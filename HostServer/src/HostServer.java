/*--------------------------------------------------------
HostServer.java
version 1.0

Joseph Sackett
October 27, 2013

Developed and tested with JDK 1.7.0_40.

Build Instructions:
- From a command prompt, change to directory with source files, execute:
javac *.java

Standard Execution Instructions:
HostServer hosts mock agents on different ports and provides access via the Firefox web browser.
1) From a command prompt in the same directory as the build, execute:
java HostServer
2) Open Firefox and type this into the browser line:
http://localhost:1565/
3) Expect it to show the initial agent state in the browser.
4) Change the input field so see it remember input.
5) Note that its count increments each time input is submitted.
6) Enter 'migrate' to move the agent to be hosted on new port.
7) Open a second browser to start a new agent.

A browser from a different machine can perform all of the same actions as long as the firewall rules permit port 1565.
To test this, substitute the IP address or hostname of the server running HostServer for the localhost in the above instructions.

Included Files:
- HostServer.java 
- MimeTypes.txt

Notes:
- This is a complete rewrite of the HostServer example code. It uses the MyWebServer framework and thus has some legacy code. 

----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * The server simulates hosting agents on different ports.
 * This server listens for processes connections from web browser clients to interract with agents.
 * @author Joseph Sackett
 */
public class HostServer {
	/** Default Host Server port to bind. */
	private static final int DEFAULT_HOST_SERVER_PORT = 45050;
	
	/** Default Host Server port to bind. */
	private static final int DEFAULT_NAME_SERVER_PORT = 48050;
	
	/** Initial agent port to bind. */
	private static int AG_PORT = 3000;
	
	/** Server thread timeout. */
	private static final int TIMEOUT = 2000;
	
	/** Path separator. */
	private static final char PATH_SEP = File.separator.charAt(0);
	
	/** Backslash file separator? */
	private static final char SLASH = '/';
		
	/** CRLF */
	private static final String CRLF = "\r\n";
	
	/** GET */
	private static final String GET = "GET";
	
	/** HOST_HEADER */
	private static final String HOST_HEADER = "Host: ";
	
	/** index.html */
	private static final String FAV_ICON = "favicon.ico";
	
	/** index.html */
	private static final String INDEX_HTML = "index.html";
	
	/** Name parameter. */
	private static final String NAME = "name";
	
	/** Input parameter. */
	private static final String INPUT = "input";
	
	/** Count parameter. */
	private static final String COUNT = "count";
	
	/** Migrate command. */
	private static final String MIGRATE = "migrate";
	
	/** Agent state. */
	private static final String AGENT = "agent";
	
	/** OK Response Code. */
	private static final int OK = 200;
			
	/** NOT FOUND Response Code. */
	private static final int NOT_FOUND = 404;
			
	/** NO RESPONSE Response Code. */
	private static final int NO_RESPONSE = 204;
			
	/** BAD_REQUEST Response Code. */
	private static final int BAD_REQUEST = 400;
			
	/** FORBIDDEN Response Code. */
	private static final int FORBIDDEN = 403;
			
	/** Buffer Size. */
	private static final int BUFFER_SIZE = 1000;
	
	/** Host Server Strategy Singleton. */
	private static final ServerStrategy NAME_SERVER_STRATEGY = new NameServerStrategy();
	
	/** File containing file extension to mime type mappings. */
	private static final String MIME_INPUT_FILE = "MimeTypes.txt";
	
	/** File extension to Mime type map. */
	private static Map<String,String> mimeTypes = new HashMap<String,String>();
		
	/** Code to Response string map. */
	private static Map<Integer,String> responses = new HashMap<Integer,String>();
		
	/** Global mode for the server. Thread safe. */
	private static ServerState serverState;
	
	/** Static block run when class loaded. */
	static {
		// Add HTTP responses here.
		responses.put(OK, "OK");
		responses.put(NOT_FOUND, "Not Found");
		responses.put(NO_RESPONSE, "No Response");
		responses.put(BAD_REQUEST, "Bad Request");
		responses.put(FORBIDDEN, "Forbidden");
	}
	
	/**
	 * Main HostServer Server program.
	 * - Initializes global state
	 * - Loop continually, spawning workers for each connection.
	 */
	public static void main(String[] args) {
		System.out.println("Joe Sackett's DIA Servers.");
		
		String nameServerHost = "localhost";
		int nameServerPort = DEFAULT_NAME_SERVER_PORT;
		String hostServerHost = "localhost";
		int hostServerPort = DEFAULT_HOST_SERVER_PORT;
		switch(args.length) {
		case 3:
			try{hostServerPort = Integer.getInteger(args[2]);}catch(NumberFormatException ex) {}
		case 2:
			hostServerHost = args[1];
		case 1:
			nameServerHost = args[0];
		case 0:
			break;
		default:
			System.out.println("Usage:\njava HostServer [name_server_host] [host_server_host] [host_server_port]");
			System.exit(1);
		}
		System.out.println("Name Server: " + nameServerHost + ':' + nameServerPort);
		System.out.println("Host Server: " + hostServerHost + ':' + hostServerPort);
		
		// Load Initial Mime Types.
		loadMimeTypesFile();
		
		// Initialize server state.
		serverState = new ServerState();
		
		// Start Name Server listener thread.
		new Thread(new Server(DEFAULT_NAME_SERVER_PORT, NAME_SERVER_STRATEGY)).start();
		
		// Start Host Server listener thread.
		new Thread(new Server(DEFAULT_HOST_SERVER_PORT, new HostServerStrategy(hostServerHost, hostServerPort, nameServerHost, nameServerPort))).start();
		
		// Loop until interrupted to end.
		while (serverState.isControlSwitch()) {
			try {
				Thread.sleep(TIMEOUT);
			}
			catch (InterruptedException ex) {}
		}
		
		System.out.println("Host Server exiting.");
	}	

	/**
	 * Generic server used for spawning both web server & back channel workers.
	 * Behavior parameterized with different strategies.
	 */
	private static class Server implements Runnable {
		/** Port bound to by this server. */
		private int portNum;
		
		/** Strategy executed by workers processing connections to this server (web server or back channel). */
		private ServerStrategy serverStrategy;
		
		/** Server listener socket. */
		private ServerSocket serverSocket = null;
		
		/** Control switch to shutdown this server instance. */
		private boolean controlSwitch = true;
		
		public Server(int portNum, ServerStrategy serverStrategy) {
			this.portNum = portNum;
			this.serverStrategy = serverStrategy;
		}
		
		public int getPortNum() {
			return portNum;
		}

		public boolean isControlSwitch() {
			return controlSwitch;
		}

		public void setControlSwitch(boolean controlSwitch) {
			this.controlSwitch = controlSwitch;
		}

		public void setServerStrategy(ServerStrategy serverStrategy) {
			this.serverStrategy = serverStrategy;
		}

		/** Thread run method. Terminates upon completion. */
		@Override
		public void run() {
			System.out.println("Starting " + serverStrategy.getTypeName() + " listener on port: " + getPortNum());			
			try {
				serverSocket = new ServerSocket(portNum);
				while (isControlSwitch()) {
					// Wait for the next client connection.
					Socket socket = serverSocket.accept();
					// Check for shutdown preceding client connection.
					if (isControlSwitch()) {
						// Spawn thread, along with Joke or Admin strategy.
						new Thread(new Worker(socket, serverStrategy, this)).start();
					}
				}
			}
			catch (IOException ex) {
				ex.printStackTrace();
			}
			finally {
				if (serverSocket != null) {
					try { serverSocket.close(); } catch (IOException ex) {}
				}
			}
			System.out.println(serverStrategy.getTypeName() + " at: " + getPortNum() + " listener exiting.");
		}
	}

	/**
	 * Generic worker used for both host server & agent server requests.
	 * Behavior parameterized with different strategies.
	 */
	private static class Worker implements Runnable {
		/** Socket connected to the client whom this worker will process. */
		private Socket socket;
		
		/** Strategy executed by workers processing connections to this server (host server or agent server). */
		private ServerStrategy serverStrategy;
		
		/** Spawning server. */
		private Server server;
		
		public Worker(Socket socket, ServerStrategy serverStrategy, Server server) {
			this.socket = socket;
			this.serverStrategy = serverStrategy;
			this.server = server;
		}

		/**
		 * Method to execute when thread is spawned.
		 */
		@Override
		public void run() {
			System.out.println("Spawning " + serverStrategy.getTypeName() + " worker at: " + server.getPortNum() + " to process request.");
			serverStrategy.processRequest(socket, server);
		}
	}

	/**
	 * Interface defining the method for processing client requests.
	 * Implemented by subclasses. Part of Strategy pattern.
	 */
	private static interface ServerStrategy {
		/** Echo type name. */
		public String getTypeName();
		
		/** Processes a client request. */
		public void processRequest(Socket socket, Server server);
	}
	
	/**
	 * Abstract superclass of Server Strategies.
	 * Part of Strategy pattern.
	 */
	private abstract static class AbstractServerStrategy implements ServerStrategy {
		/** Required subclass method to handle requests. */
		protected abstract void handleRequest(List<String> fullRequest, PrintStream writer, Server server) throws IOException;
		
		/** Processes a client request specific for the web server strategy. */
		@Override
		public void processRequest(Socket socket, Server server) {
			BufferedReader reader =  null;
			PrintStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintStream(socket.getOutputStream());

				// Read all input from web browser via socket.
				List<String> input = new ArrayList<String>();
				do {
					// Read line by line & save in list.
					String line = reader.readLine();
					input.add(line);
				} while (reader.ready()) ;
				
				// Process request.
				if (input.size() > 0) {
					System.out.println(input.get(0));
					handleRequest(input, writer, server);
				}
				else {
		    		writeError(BAD_REQUEST, "No Request Received.", writer);
				}
				
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			finally {
				if (reader != null) {
					try {reader.close();} catch (IOException ex) {}
				}
				if (socket != null) {
					try {socket.close();} catch (IOException ex) {}
				}
			}
		}
		
		/**
		 * Writes error code & html back to browser.
		 */
		protected static void writeError(int code, String error, PrintStream writer) throws IOException {
			System.out.println("Returning " + code + " error: " + error);
			// Build error response HTML.
			StringBuilder responseBuilder = new StringBuilder();
			responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
			responseBuilder.append("<html><head>").append(CRLF);
			responseBuilder.append("<title>").append(code).append(' ').append(responses.get(code)).append("</title>").append(CRLF);
			responseBuilder.append("</head><body>").append(CRLF);
			responseBuilder.append("<h1>").append(responses.get(code)).append("</h1>").append(CRLF);
			responseBuilder.append("<p>").append(error).append("</p>").append(CRLF);
			responseBuilder.append("</body></html>").append(CRLF);
			String response = responseBuilder.toString();
			
			writer.print("HTTP/1.1 " + code + ' ' + responses.get(code) + CRLF);
			writer.print("Content-Length: " + response.length() + CRLF);
			writer.print("Content-Type: " + mimeTypes.get("html") + CRLF);
			writer.print("Connection: close" + CRLF + CRLF);
			writer.print(response);
			writer.print(CRLF);
			writer.flush();
		}		
		
		/**
		 * Writes OK and other output headers for successful response.
		 */
		protected static void writeOkHeader(long length, String mimeType, PrintStream writer) throws IOException {
			writer.print("HTTP/1.1 200 OK" + CRLF);
			writer.print("Content-Length: " + length + CRLF);
			writer.print("Content-Type: " + mimeType + CRLF);
			writer.print("Connection: close" + CRLF + CRLF);
		}		
	}
	
	/**
	 * Implementation for processing Host Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class HostServerStrategy extends AbstractServerStrategy {
		/** Host name of this Host Server. */
		private String hostServerHost;
		
		/** Port of this Host Server. */
		private int hostServerPort;
		
		/** Host name of Name Server for registering agent servers. */
		private String nameServerHost;
		
		/** Port of Name Server for registering agent servers. */
		private int nameServerPort;
		
		public HostServerStrategy(String hostServerHost, int hostServerPort, String nameServerHost, int nameServerPort) {
			this.hostServerHost = hostServerHost;
			this.hostServerPort = hostServerPort;
			this.nameServerHost = nameServerHost;
			this.nameServerPort = nameServerPort;
			
			// Register host server.
			String registerRequest = REGISTER_HOST_SERVER + ' ' + REGISTER_HOST_SERVER + '?' + SERVER + '=' + hostServerHost + ':' + hostServerPort + CRLF;
			List<String> registerResponse = genericRequest(nameServerHost, nameServerPort, registerRequest);
		}

		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Host Server";
		}
		
//		/** Processes a client request specific for the host server strategy. */
//		@Override
//		public void processRequest(Socket socket, Server server) {
//			BufferedReader reader =  null;
//			PrintStream writer = null;
//			try {
//				// Get I/O streams from the socket.
//				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//				writer = new PrintStream(socket.getOutputStream());
//	
//				// Read all input from client via socket.
//				List<String> input = new ArrayList<String>();
//				do {
//					// Read line by line & save in list.
//					String line = reader.readLine();
//					input.add(line);
//				} while (reader.ready()) ;
//				
//				// Process request.
//				if (input.size() > 0) {
//					System.out.println(input.get(0));
//					respondToRequest(input, writer);
//				}
//				else {
//		    		writeError(BAD_REQUEST, "No Request Received.", writer);
//				}				
//	
//			} catch (IOException ex) {
//				System.out.println(ex);
//				ex.printStackTrace();
//			}
//			finally {
//				if (reader != null) {
//					try {reader.close();} catch (IOException ex) {}
//				}
//				if (writer != null) {
//					writer.close();
//				}
//				if (socket != null) {
//					try {socket.close();} catch (IOException ex) {}
//				}
//			}			
//		}

		/**
		 * Process request string & delegate to handler functions.
		 */
		@Override
		protected void handleRequest(List<String> fullRequest, PrintStream writer, Server server) throws IOException {
			try {
				// Parse & validate request
				String request = fullRequest.get(0);
		    	StringTokenizer toker = new StringTokenizer(request, " ");
		    	List<String> tokens = new ArrayList<String>();
		    	while (toker.hasMoreTokens()) {
		    		tokens.add(toker.nextToken());
		    	}
		    	String command = tokens.get(0);
		    	if (!(command.equalsIgnoreCase(GET) || command.equalsIgnoreCase(MIGRATE)) || (tokens.size() > 1 && tokens.get(1).contains(FAV_ICON))) {
		    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
		    	}
		    	
				// Parse host name from header.
		    	String host = null;
		    	for (String header : fullRequest) {
		    		if (header.contains(HOST_HEADER)) {
		    			host = header.substring(HOST_HEADER.length(), header.lastIndexOf(':'));
		    			break;
		    		}
		    	}

		    	int agentPort = ++AG_PORT;
		    	
		    	AgentState agentState = null;
				if (command.equalsIgnoreCase(GET)) {
	    			String registerRequest = REGISTER_AGENT + ' ' + AGENT + '?' + SERVER + '=' + hostServerHost + ':' + agentPort + CRLF;
					List<String> registerResponse = genericRequest(nameServerHost, nameServerPort, registerRequest);
					String agentName = registerResponse.get(0);
			    	// Initialize agent state.
					agentState = new AgentState(agentName);
				}
				else if (command.equalsIgnoreCase(MIGRATE)) {
					// Migrate request.
					Map<String,String> paramMap = parseParams(tokens.get(1));
					agentState = new AgentState(paramMap.get(NAME), paramMap.get(INPUT), Integer.parseInt(paramMap.get(COUNT)));
	    			String migrateRequest = MIGRATE + ' ' + AGENT + '?' + NAME + '=' + agentState.getName() + '&' + SERVER + '=' + hostServerHost + ':' + agentPort + CRLF;
					List<String> migrateResponse = genericRequest(nameServerHost, nameServerPort, migrateRequest);
				}
				
				// Start Agent listener thread.
				new Thread(new Server(agentPort, new AgentServerStrategy(agentState, server.getPortNum()))).start();
				System.out.println("Hosting Agent at: " + host + ':' + agentPort);
				
				// Reply to browser client or to migration request.
				if (!tokens.get(0).contains(MIGRATE)) {
					// Build HTML response for browser client.
					StringBuilder responseBuilder = new StringBuilder();
					responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
					responseBuilder.append("<html><head>").append(CRLF);
					responseBuilder.append("<meta charset=\"UTF-8\">").append(CRLF);
					responseBuilder.append("<meta http-equiv=\"refresh\" content=\"1;url=http://").append(host).append(':').append(agentPort).append("\">").append(CRLF);
					responseBuilder.append("<script type=\"text/javascript\">").append(CRLF);
					responseBuilder.append("window.location.href = \"").append("http://").append(host).append(':').append(agentPort).append('"').append(CRLF);
					responseBuilder.append("</script>").append(CRLF);
					responseBuilder.append("<title>Page Redirection</title>").append(CRLF);
					responseBuilder.append("</head><body></body></html>").append(CRLF);				
					String response = responseBuilder.toString();
					writeOkHeader(response.length(), mimeTypes.get("html"), writer);
					writer.print(response);
					writer.print(CRLF);			
				}
				else {
					// Handle migration response.
					writer.print(host + ':' + agentPort);
					writer.print(CRLF + CRLF);
				}
				writer.flush();				
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			finally {
				if (writer != null) {
					writer.close();
				}
			}			
			
			while (serverState.isControlSwitch()) {
				try {
					Thread.sleep(TIMEOUT);
				}
				catch (InterruptedException ex) {}
			}
			
			System.out.println("Host Server exiting.");
	    }

	}
		
	/**
	 * Implementation for processing Agent Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class AgentServerStrategy extends AbstractServerStrategy {
		/** Agent state. */
		private AgentState agentState;		
		
		/** Agent state. */
		private int hostServerPort;		
		
		public AgentServerStrategy(AgentState agentState, int hostServerPort) {
			this.agentState = agentState;
			this.hostServerPort = hostServerPort;
		}

		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Agent Server";
		}
		
//		/** Processes a client request specific for the agent server strategy. */
//		@Override
//		public void processRequest(Socket socket, Server server) {
//			BufferedReader reader =  null;
//			PrintStream writer = null;
//			try {
//				// Get I/O streams from the socket.
//				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//				writer = new PrintStream(socket.getOutputStream());
//
//				// Read all input from back channel browser via socket.
//				List<String> input = new ArrayList<String>();
//				do {
//					// Read line by line & save in list.
//					String line = reader.readLine();
//					input.add(line);
//				} while (reader.ready()) ;
//				
//				// Process request.
//				if (input.size() > 0) {
//					System.out.println(input.get(0));
//					respondToRequest(input, writer, server);
//				}
//				else {
//		    		writeError(BAD_REQUEST, "No Request Received.", writer);
//				}				
//
//				writer.flush();				
//			} catch (IOException ex) {
//				System.out.println(ex);
//				ex.printStackTrace();
//			}
//			finally {
//				if (reader != null) {
//					try {reader.close();} catch (IOException ex) {}
//				}
//				if (writer != null) {
//					writer.close();
//				}
//				if (socket != null) {
//					try {socket.close();} catch (IOException ex) {}
//				}
//			}			
//		}
		
		/**
		 * Process request string & delegate to handler functions.
		 */
		@Override
		protected void handleRequest(List<String> fullRequest, PrintStream writer, Server server) throws IOException {
			// Parse & validate request.
			String request = fullRequest.get(0);
	    	StringTokenizer toker = new StringTokenizer(request, " ");
	    	List<String> tokens = new ArrayList<String>(); 
	    	while (toker.hasMoreTokens()) {
	    		tokens.add(toker.nextToken());
	    	}
	    	if (tokens.size() < 3 || !tokens.get(0).equalsIgnoreCase(GET) || tokens.get(1).contains(FAV_ICON)) {
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	
	    	// Parse host name & port from headers.
	    	String host = null, port = null;
	    	for (String header : fullRequest) {
	    		if (header.contains(HOST_HEADER)) {
	    			host = header.substring(HOST_HEADER.length(), header.lastIndexOf(':'));
	    			port = header.substring(header.lastIndexOf(':')+1);
	    			break;
	    		}
	    	}
			System.out.println("Agent working at: " + host + ':' + port);

			// Parse input parameters.
			Map<String,String> paramMap = parseParams(tokens.get(1));
			String input = paramMap.get(INPUT);
	    	
    		// Check for migration request.
    		if (MIGRATE.equalsIgnoreCase(input)) {
    			// Request migration location from nameserver.
    			String migrateRequest = MIGRATE + ' ' + AGENT + '?' + NAME + '=' + agentState.getName() + '&' + 
    					INPUT + '=' + agentState.getInput() + '&' + COUNT + '=' + agentState.getCount() + CRLF;
    			migrateRequest += HOST_HEADER + host + ':' + port + CRLF + CRLF;
				List<String> migrateResponse = genericRequest(host, hostServerPort, migrateRequest);
				String response = migrateResponse.get(0);
    			host = response.substring(0, response.lastIndexOf(':'));
    			String newPort = response.substring(response.lastIndexOf(':')+1);
    			
    			// Change this server to a zombie server.
				server.setServerStrategy(new ZombieServerStrategy(host + ':' + newPort));

				// Build HTML redirection for browser client.
				StringBuilder responseBuilder = new StringBuilder();
				responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
				responseBuilder.append("<html><head>").append(CRLF);
				responseBuilder.append("<meta charset=\"UTF-8\">").append(CRLF);
				responseBuilder.append("<meta http-equiv=\"refresh\" content=\"1;url=http://").append(host).append(':').append(port).append("\">").append(CRLF);
				responseBuilder.append("<script type=\"text/javascript\">").append(CRLF);
				responseBuilder.append("window.location.href = \"").append("http://").append(host).append(':').append(port).append('"').append(CRLF);
				responseBuilder.append("</script>").append(CRLF);
				responseBuilder.append("<title>Page Redirection</title>").append(CRLF);
				responseBuilder.append("</head><body></body></html>").append(CRLF);				
				response = responseBuilder.toString();
				writeOkHeader(response.length(), mimeTypes.get("html"), writer);
				writer.print(response);
				writer.print(CRLF);
				writer.flush();
	    	}
    		else {
    			if (input != null) {
	    	    	// Set agent input state.
	       			agentState.setInput(input);
	       			// Increment usage count.
	        		agentState.incCount();
    			}
        		
				// Build HTML response for browser client.
				StringBuilder responseBuilder = new StringBuilder();
				responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
				responseBuilder.append("<html><head>").append(CRLF);
				responseBuilder.append("<title>").append("Agent at: ").append(host).append(':').append(port).append("</title>").append(CRLF);
				responseBuilder.append("</head><body>").append(CRLF);
				responseBuilder.append("<h1>").append("Agent at&nbsp;&nbsp;&nbsp;").append(host).append(':').append(port).append("</h1>").append(CRLF);
				responseBuilder.append("<h2>").append("Agent state: ").append(agentState.getCount()).append("</h2>").append(CRLF);
				responseBuilder.append("<form method=\"GET\" action=\"http://").append(host).append(':').append(port).append("\">").append(CRLF);
				responseBuilder.append("Enter text or <i>migrate</i>: <input type=\"text\" name=\"").append(INPUT).append("\" size=\"20\" value=\"").append(agentState.getInput()).append("\"/><p/>").append(CRLF);
				responseBuilder.append("<input type=\"submit\" value=\"Submit\"<p/>").append(CRLF);
				responseBuilder.append("</form></body></html>").append(CRLF);			
				String response = responseBuilder.toString();				
				writeOkHeader(response.length(), mimeTypes.get("html"), writer);
				writer.print(response);
				writer.print(CRLF);
				writer.flush();
    		}
	    }		
	}
	
	/**
	 * Implementation for processing Zombie Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class ZombieServerStrategy extends AbstractServerStrategy {
		/** Forwarding address where this agent migrated (server:port). */
		private String forwardingAddress;		
		
		public ZombieServerStrategy(String forwardingAddress) {
			this.forwardingAddress = forwardingAddress;
		}

		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Zombie Server";
		}
		
		/**
		 * Process request string & delegate to handler functions.
		 */
		@Override
		protected void handleRequest(List<String> fullRequest, PrintStream writer, Server server) throws IOException {
			// Parse & validate request.
			String request = fullRequest.get(0);
	    	StringTokenizer toker = new StringTokenizer(request, " ");
	    	List<String> tokens = new ArrayList<String>(); 
	    	while (toker.hasMoreTokens()) {
	    		tokens.add(toker.nextToken());
	    	}
	    	if (tokens.size() < 3 || !tokens.get(0).equalsIgnoreCase(GET) || tokens.get(1).contains(FAV_ICON)) {
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	
	    	// Parse host name & port from headers.
	    	String host = null, port = null;
	    	for (String header : fullRequest) {
	    		if (header.contains(HOST_HEADER)) {
	    			host = header.substring(HOST_HEADER.length(), header.lastIndexOf(':'));
	    			port = header.substring(header.lastIndexOf(':')+1);
	    			break;
	    		}
	    	}
			System.out.println("Zombie forwarding from: " + host + ':' + port + " to: " + forwardingAddress);

			// Parse input parameters.
			String params = "";
			if (request.contains("?")) {
				params = request.substring(request.indexOf('?'));
			}

//			Map<String,String> paramMap = parseParams(tokens.get(1));
//			String input = paramMap.get(INPUT);
	    	
    		// HTML redirection to forward request with name/value parameters.
			StringBuilder responseBuilder = new StringBuilder();
			responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
			responseBuilder.append("<html><head>").append(CRLF);
			responseBuilder.append("<meta charset=\"UTF-8\">").append(CRLF);
			responseBuilder.append("<meta http-equiv=\"refresh\" content=\"1;url=http://").append(forwardingAddress).append(params).append("\">").append(CRLF);
			responseBuilder.append("<script type=\"text/javascript\">").append(CRLF);
			responseBuilder.append("window.location.href = \"").append("http://").append(forwardingAddress).append(params).append('"').append(CRLF);
			responseBuilder.append("</script>").append(CRLF);
			responseBuilder.append("<title>Page Redirection</title>").append(CRLF);
			responseBuilder.append("</head><body></body></html>").append(CRLF);				
			String response = responseBuilder.toString();
			writeOkHeader(response.length(), mimeTypes.get("html"), writer);
			writer.print(response);
			writer.print(CRLF);
			writer.flush();
			
			server.setControlSwitch(false);
			genericRequest(host, Integer.parseInt(port), "NULL" + CRLF + CRLF);
	    }		
	}
	
	private static String REGISTER_HOST_SERVER = "RegisterHostServer";
	private static String REGISTER_AGENT = "RegisterAgent";
	private static String SERVER = "server";
	private static String SUCCESS = "success";

	/**
	 * Implementation for processing Name Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class NameServerStrategy extends AbstractServerStrategy {
		/** List of host servers (server:port). */
		private List<String> hostServers = new ArrayList<String>();		
		
		/** Map of agent names to agent servers (server:port). */
		private Map<String,String> agentServers = new HashMap<String,String>();		
		
		public NameServerStrategy() {
		}

		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Name Server";
		}
		
		/**
		 * Process request string & delegate to handler functions.
		 */
		@Override
		protected void handleRequest(List<String> fullRequest, PrintStream writer, Server server) throws IOException {
			// Parse & validate request.
			String request = fullRequest.get(0);
	    	StringTokenizer toker = new StringTokenizer(request, " ");
	    	List<String> tokens = new ArrayList<String>(); 
	    	while (toker.hasMoreTokens()) {
	    		tokens.add(toker.nextToken());
	    	}
	    	String command = tokens.get(0);
	    	if (!(command.equalsIgnoreCase(GET) || command.equalsIgnoreCase(REGISTER_HOST_SERVER) || command.equalsIgnoreCase(REGISTER_AGENT) 
	    			|| command.equalsIgnoreCase(MIGRATE)) || (tokens.size() > 1 && tokens.get(1).contains(FAV_ICON))) {
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	String uri = tokens.get(1);
	    	
	    	// Handle a GET by listing all host servers & agents.
	    	if (command.equalsIgnoreCase(GET)) {
	    		processGetRequest(writer);
	    		return;
	    	}
	    	
	    	// Parse host name & port from headers.
//	    	String host = null, port = null;
//	    	for (String header : fullRequest) {
//	    		if (header.contains(HOST_HEADER)) {
//	    			host = header.substring(HOST_HEADER.length(), header.lastIndexOf(':'));
//	    			port = header.substring(header.lastIndexOf(':')+1);
//	    			break;
//	    		}
//	    	}
	    	
			// Parse input parameters.
			Map<String,String> paramMap = parseParams(tokens.get(1));
			
	    	if (command.equalsIgnoreCase(REGISTER_HOST_SERVER)) {
	    		// Get host server endpoint from request parameters.
	    		String hostServer = paramMap.get(SERVER);
	    		if (hostServer == null) {
		    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
	    		}	    			
    			hostServers.add(hostServer);
    			System.out.println("Register host server: " + hostServer);
    			
    			// Success response.
    			writer.print(SUCCESS);
	    	}
	    	else if (command.equalsIgnoreCase(REGISTER_AGENT)) {
	    		// Get agent server endpoint from request parameters.
	    		String agentServer = paramMap.get(SERVER);
	    		if (agentServer == null) {
		    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
	    		}	    			
    			String agentName = getUniqueName();
    			agentServers.put(agentName, agentServer);
    			System.out.println("Register agent: " + agentName + " at server: " + agentServer);
    			
    			// Name response.
    			writer.print(agentName);
	    	}
	    	else if (command.equalsIgnoreCase(MIGRATE)) {
	    		// Get agent name from request parameters.
	    		String agentName = paramMap.get(NAME);
	    		// Get new agent server endpoint from request parameters.
	    		String agentServer = paramMap.get(SERVER);
	    		if (agentName == null || agentServer == null) {
		    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
	    		}	    			
    			agentServers.put(agentName, agentServer);
    			System.out.println("Migrate agent: " + agentName + " to server: " + agentServer);
    			
    			// Success response.
    			writer.print(SUCCESS);
	    	}
	    	
	    	// Complete response.
			writer.print(CRLF + CRLF);
			writer.flush();
			
//				// Build HTML response for browser client.
//				StringBuilder responseBuilder = new StringBuilder();
//				responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
//				responseBuilder.append("<html><head>").append(CRLF);
//				responseBuilder.append("<title>").append("Agent at: ").append(host).append(':').append(port).append("</title>").append(CRLF);
//				responseBuilder.append("</head><body>").append(CRLF);
//				responseBuilder.append("<h1>").append("Agent at&nbsp;&nbsp;&nbsp;").append(host).append(':').append(port).append("</h1>").append(CRLF);
//				responseBuilder.append("<h2>").append("Agent state: ").append(agentState.getCount()).append("</h2>").append(CRLF);
//				responseBuilder.append("<form method=\"GET\" action=\"http://").append(host).append(':').append(port).append("\">").append(CRLF);
//				responseBuilder.append("Enter text or <i>migrate</i>: <input type=\"text\" name=\"").append(INPUT).append("\" size=\"20\" value=\"").append(agentState.getInput()).append("\"/><p/>").append(CRLF);
//				responseBuilder.append("<input type=\"submit\" value=\"Submit\"<p/>").append(CRLF);
//				responseBuilder.append("</form></body></html>").append(CRLF);			
//				String response = responseBuilder.toString();				
//				writeOkHeader(response.length(), mimeTypes.get("html"), writer);
//				writer.print(response);
//				writer.print(CRLF);
//    		}
	    }
		
		private void processGetRequest(PrintStream writer) throws IOException {
			StringBuilder responseBuilder = new StringBuilder();
			responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
			responseBuilder.append("<html><head>").append(CRLF);
			responseBuilder.append("<title>").append("Name Server Manifest").append("</title>").append(CRLF);
			responseBuilder.append("</head><body>").append(CRLF);
			responseBuilder.append("<h1>").append("Name Server Manifest").append("</h1>").append(CRLF);
			responseBuilder.append("<h2>").append("Host Servers").append("</h2>").append(CRLF);
			responseBuilder.append("<pre>").append(CRLF);
			for (String hostServer : hostServers) {
				responseBuilder.append("<a href=\"http://").append(hostServer).append("/\">");
				responseBuilder.append(hostServer).append("</a>").append(CRLF);
			}
			responseBuilder.append("</pre>").append(CRLF);
			responseBuilder.append("<h2>").append("Agents").append("</h2>").append(CRLF);
			responseBuilder.append("<pre>").append(CRLF);
			for (String agentName : agentServers.keySet()) {
				responseBuilder.append("<a href=\"http://").append(agentServers.get(agentName)).append("/\">");
				responseBuilder.append(agentName).append("</a>").append(CRLF);
			}
			responseBuilder.append("</pre>").append(CRLF);
			responseBuilder.append("</body></html>").append(CRLF);			
			String response = responseBuilder.toString();
			writeOkHeader(response.length(), mimeTypes.get("html"), writer);
			writer.print(response);
			writer.print(CRLF);
			writer.flush();
		}
		
		private static String getUniqueName() {
			return "" + Math.random();
		}
	}
	
	/**
	 * Holds the Agent's state.
	 */
	private static class AgentState {
		/** Agent Name. */
		private String name;
		/** Agent Input. */
		private String input;
		/** Agent counter. */
		private int count;
		
		public AgentState(String name) {
			this.name = name;
			this.input = "Default";
			this.count = 1;
		}

		public AgentState(String name, String input, int count) {
			this.name = name;
			this.input = input;
			this.count = count;
		}

		public String getName() {
			return name;
		}

		public String getInput() {
			return input;
		}
		
		public void setInput(String input) {
			this.input = input;
		}
		
		public int getCount() {
			return count;
		}
		
		public void incCount() {
			count++;
		}
	}
	
	/**
	 * Parses request parameters and returns as map.
	 */
	private static Map<String,String> parseParams(String request) {
    	Map<String,String> paramMap = new HashMap<String,String>(); 
		String params;
		// Check whether there are parameters.
		if (!request.contains("?") || (params = request.substring(request.indexOf('?')+1)) == null || params.length() == 0) {
    		return paramMap;
		}
		
		// Tokenize the parameters out.
    	StringTokenizer toker = new StringTokenizer(params, "&");
    	while (toker.hasMoreTokens()) {
    		String token, name, value;
    		token = toker.nextToken();
			if (!token.contains("=") || (name = token.substring(0, token.indexOf('='))) == null || name.length() == 0
					|| (value = token.substring(token.indexOf('=')+1)) == null || value.length() == 0) {
	    		return paramMap;
			}
    		paramMap.put(name, value);
    	}
    	
    	return paramMap;
	}
	
	/**
	 * This loads the MimeTypes.txt file containing all of the mappings between file extension & mime type.
	 * If the input file does not exist or the file data are invalid, it uses default data so it can still proceed. 
	 */
	private static void loadMimeTypesFile() {
		// Clear old mime types.
		mimeTypes = new HashMap<String,String>();
		
 		BufferedReader reader = null;
		try {
			// Open input file.
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(MIME_INPUT_FILE)));
			// Read each line through the end of the file.
	        String input;
			while ((input = reader.readLine()) != null) {
				// Skip commented lines.
				if (input == null || input.length() == 0 || input.startsWith("#")) {
					continue;
				}
				
				// Parse file extension from mime type by [space].
		    	StringTokenizer toker = new StringTokenizer(input, " ");
		    	while (toker.countTokens() == 2) {
					// Add this mime type to global collection.
					mimeTypes.put(toker.nextToken(), toker.nextToken());
		    	}
			}
		} catch (IOException ex) {
			System.out.println(ex);
			System.out.println("Continuing with default Mime Types.");
		}
		finally {
			if (reader != null) {
				try {reader.close();} catch (Exception ex) {}
			}
		}
		
		// If load from file was unsuccessful, load default mappings.
		if (mimeTypes.isEmpty()) {
			// Add default Mime types here.
			mimeTypes.put("txt", "text/plain");
			mimeTypes.put("log", "text/plain");
			mimeTypes.put("htm", "text/html");
			mimeTypes.put("html", "text/html");
			mimeTypes.put("js", "application/javascript");
			mimeTypes.put("pdf", "application/pdf");
			mimeTypes.put("zip", "application/zip");
			mimeTypes.put("gif", "image/gif");
			mimeTypes.put("jpeg", "image/jpeg");
			mimeTypes.put("jpg", "image/jpeg");
			mimeTypes.put("png", "image/png");
			mimeTypes.put("css", "text/css");
			mimeTypes.put("ico", "image/x.icon");
			mimeTypes.put("xyz", "application/xyz");
		}
	}
	
	/**
	 * Send request to listeners at server:port and return response.
	 */
	private static List<String> genericRequest(String server, int port, String request) {
		List<String> response = new ArrayList<String>();
		Socket socket = null;
		BufferedReader reader = null;
		PrintStream writer = null;
		try {
			// Open connection to server.
			socket = new Socket(server, port);
			// Create reader from socket input stream.
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			// Create print stream writer from socket output stream.
			writer = new PrintStream(socket.getOutputStream());

			// Send request to server.
			writer.println(request);
			writer.flush();
			
			// Read returned response.
			do {
				// Read line by line & save in list.
				String line = reader.readLine();
				response.add(line);
			} while (reader.ready()) ;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		finally {
			if (reader != null) {
				try {reader.close();} catch (IOException ex) {}
			}
			if (writer != null) {
				writer.close();
			}
			if (socket != null) {
				try {socket.close();} catch (IOException ex) {}
			}
		}
		
		return response;
	}
	
	/**
	 * Encapsulates the state of the server.
	 */
	private static class ServerState {
		/** Main control switch used for shutdown. */
		private boolean controlSwitch = true;
		
		public synchronized boolean isControlSwitch() {
			return controlSwitch;
		}

		public synchronized void setControlSwitch(boolean controlSwitch) {
			this.controlSwitch = controlSwitch;
		}
	}
	
}
