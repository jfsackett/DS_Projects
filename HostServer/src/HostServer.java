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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
	/** Host Server port to bind. */
	private static final int HS_PORT = 1565;
	
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
	
	/** Web Server Strategy Singleton. */
//	private static final ServerStrategy WEB_SERVER_STRATEGY = new WebServerStrategy();
	
	/** Back Channel Server Strategy Singleton. */
	private static final ServerStrategy HOST_SERVER_STRATEGY = new HostServerStrategy();
	
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
		System.out.println("Joe Sackett's Host Server.");
		System.out.println("Host Server Port: " + HS_PORT);
		
		// Load Initial Mime Types.
		loadMimeTypesFile();
		
		// Initialize server state.
		serverState = new ServerState();
		
		// Start Host Server listener thread.
		new Thread(new Server(HS_PORT, HOST_SERVER_STRATEGY)).start();
		
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
		int portNum;
		
		/** Strategy executed by workers processing connections to this server (web server or back channel). */
		ServerStrategy serverStrategy;
		
		/** Server listener socket. */
		private ServerSocket serverSocket = null;
		
		/** Control switch to shutdown this server instance. */
		boolean controlSwitch = true;
		
		public Server(int portNum, ServerStrategy serverStrategy) {
			this.portNum = portNum;
			this.serverStrategy = serverStrategy;
		}
		
		public boolean isControlSwitch() {
			return controlSwitch;
		}

		public void setControlSwitch(boolean controlSwitch) {
			this.controlSwitch = controlSwitch;
		}

		/** Thread run method. Terminates upon completion. */
		@Override
		public void run() {
			System.out.println("Starting " + serverStrategy.getTypeName() + " listener on port: " + portNum);			
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
				System.out.println(ex);
			}
			finally {
				if (serverSocket != null) {
					try { serverSocket.close(); } catch (IOException ex) {}
				}
			}
			System.out.println(serverStrategy.getTypeName() + " listener exiting.");
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
			System.out.println("Spawning " + serverStrategy.getTypeName() + " worker to process request.");
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
	 * Implementation for processing Web Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class WebServerStrategy implements ServerStrategy {
		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Web Server";
		}
		
		/** Processes a client request specific for the web server strategy. */
		@Override
		public void processRequest(Socket socket, Server server) {
			BufferedReader reader =  null;
			DataOutputStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new DataOutputStream(socket.getOutputStream());

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
					respondToRequest(input.get(0), writer);
				}
				else {
		    		writeError(BAD_REQUEST, "No Request Received.", writer);
				}
				
			} catch (IOException ex) {
				System.out.println(ex);
				ex.printStackTrace();
			}
			finally {
				if (reader != null) {
					try {reader.close();} catch (IOException ex) {}
				}
				if (writer != null) {
					try{writer.close();} catch (IOException ex) {}
				}
				if (socket != null) {
					try {socket.close();} catch (IOException ex) {}
				}
			}
		}
		
		/**
		 * Process request string & delegate to handler functions.
		 */
		private static void respondToRequest(String request, DataOutputStream writer) throws IOException {
			// Validate request
	    	StringTokenizer toker = new StringTokenizer(request, " ");
	    	List<String> tokens = new ArrayList<String>(); 
	    	while (toker.hasMoreTokens()) {
	    		tokens.add(toker.nextToken());
	    	}
	    	if (tokens.size() < 3 || !tokens.get(0).equalsIgnoreCase(GET)) {
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	
	    	// Tie URL to local directory & check for shenanigans.
	    	String url = "." + tokens.get(1);
	    	if (url.contains("..")) {
	    		writeError(FORBIDDEN, "You don't have permission to access " + tokens.get(1) + " on this server.", writer);
	    		return;
	    	}
	    	
	    	// Does requested file exist?
			File file = new File(url);
			if (!file.exists()) {
	    		writeError(NOT_FOUND, "The requested URL " + tokens.get(1) + " was not found on this server.", writer);
	    		return;
			}
			
			// Dispatch based on file, directory or CGI request.
			if (file.isFile()) {
	    		processFileRequest(file, writer);
			}
			else if (file.isDirectory()) {
	    		processDirRequest(file, writer);
			}
			else {
	    		writeError(NOT_FOUND, "The requested URL " + tokens.get(1) + " was not found on this server.", writer);
	    		return;
			}
		}
		
		/**
		 * Writes error code & html back to browser.
		 */
		private static void writeError(int code, String error, DataOutputStream writer) throws IOException {
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
			
			writer.writeBytes("HTTP/1.1 " + code + ' ' + responses.get(code) + CRLF);
			writer.writeBytes("Content-Length: " + response.length() + CRLF);
			writer.writeBytes("Content-Type: " + mimeTypes.get("html") + CRLF);
			writer.writeBytes("Connection: close" + CRLF + CRLF);
			writer.writeBytes(response);
			writer.writeBytes(CRLF);
			writer.flush();
		}
		
		/**
		 * Writes OK and other output headers for successful response.
		 */
		private static void writeOkHeader(long length, String mimeType, DataOutputStream writer) throws IOException {
			writer.writeBytes("HTTP/1.1 200 OK" + CRLF);
			writer.writeBytes("Content-Length: " + length + CRLF);
			writer.writeBytes("Content-Type: " + mimeType + CRLF);
			writer.writeBytes("Connection: close" + CRLF + CRLF);
		}
		
		/**
		 * Return the contents of a file to the browser.
		 */
		private static void processFileRequest(File file, DataOutputStream writer) throws IOException {
			// Parse file name for extension.
			String fileName = file.getName();
			String fileExtension = fileName.substring(fileName.lastIndexOf('.')+1).toLowerCase();
			String mimeType = mimeTypes.get(fileExtension);
			// If no mime type for this extension, reload mime type file in case it was added.
			if (mimeType == null) {
				loadMimeTypesFile();
				mimeType = mimeTypes.get(fileExtension);
				// If still missing mime type, return error.
				if (mimeType == null) {
		    		writeError(BAD_REQUEST, "No Mime Type for this request: " + fileName, writer);
		    		return;
				}
			}
			
			System.out.println("Returning file: " + file.getName());
			writeOkHeader(file.length(), mimeTypes.get(fileExtension), writer);
			InputStream fileReader = null;
			try {
				// Open input file & read into 
				fileReader = new DataInputStream(new FileInputStream(file));
				// Read and immediately output bytes until done.
				byte[] buffer = new byte[BUFFER_SIZE];
				while (fileReader.read(buffer) > 0) {
					writer.write(buffer);
				}
			} catch (IOException ex) {
				System.out.println(ex);
			}
			finally {
				if (fileReader != null) {
					try {fileReader.close();} catch (Exception ex) {}
				}
				writer.flush();
			}
		}
		
		/**
		 * Returns a directory listing to the browser.
		 */
		private static void processDirRequest(File dir, DataOutputStream writer) throws IOException {
			String currDir = dir.getPath().substring(1).replace(PATH_SEP, SLASH);
			currDir = (currDir.length() == 0) ? "/" : currDir;
			String parentDir = (dir.getParent() == null) ? "" : dir.getParent().substring(1).replace(PATH_SEP, SLASH);
			parentDir = (parentDir.length() == 0 && currDir.length() > 1) ? "/" : parentDir;
			StringBuilder responseBuilder = new StringBuilder();
			responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
			responseBuilder.append("<html><head>").append(CRLF);
			responseBuilder.append("<title>").append("Index of ").append(currDir).append("</title>").append(CRLF);
			responseBuilder.append("</head><body>").append(CRLF);
			responseBuilder.append("<h1>").append("Index of ").append(currDir).append("</h1>").append(CRLF);
			responseBuilder.append("<pre>").append(CRLF);
			if (parentDir.length() > 0) {
				responseBuilder.append("[D]  ");
				responseBuilder.append("<a href=\"").append(parentDir).append("\">");
				responseBuilder.append("Parent Directory</a>").append(CRLF);
			}
			File[] files = dir.listFiles(); 
			for (int i = 0; i < files.length; i++) {
				if (files[i].isFile()) {
					// Check for index.html to mask directory listing.
					if (files[i].getName().equals(INDEX_HTML)) {
						// Short-circuit directory listing & display index.html.
						processFileRequest(files[i], writer);
						return;
					}
					responseBuilder.append("[F]  ");
					responseBuilder.append("<a href=\"").append((dir.getParent() == null) ? "" : dir.getParent().substring(dir.getParent().lastIndexOf(".")+1)).append(dir.getName()).append('/').append(files[i].getName()).append("\">");
					responseBuilder.append(files[i].getName()).append("</a>").append(CRLF);
				}
				else if (files[i].isDirectory()) {
					responseBuilder.append("[D]  ");
					responseBuilder.append("<a href=\"").append((dir.getParent() == null) ? "" : dir.getParent().substring(dir.getParent().lastIndexOf(".")+1)).append(dir.getName()).append('/').append(files[i].getName()).append("\">");
					responseBuilder.append(files[i].getName()).append("</a>").append(CRLF);
				}
			}
			responseBuilder.append("</pre>").append(CRLF);
			responseBuilder.append("</body></html>").append(CRLF);			
			
			System.out.println("Returning directory listing: " + currDir);
			String response = responseBuilder.toString();
			writeOkHeader(response.length(), mimeTypes.get("html"), writer);
			writer.writeBytes(response);
			writer.writeBytes(CRLF);
			writer.flush();
		}
	}
	
	/**
	 * Implementation for processing Host Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class HostServerStrategy implements ServerStrategy {
		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Host Server";
		}
		
		/** Processes a client request specific for the host server strategy. */
		@Override
		public void processRequest(Socket socket, Server server) {
			BufferedReader reader =  null;
			PrintStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintStream(socket.getOutputStream());
	
				// Read all input from client via socket.
				List<String> input = new ArrayList<String>();
				do {
					// Read line by line & save in list.
					String line = reader.readLine();
					input.add(line);
				} while (reader.ready()) ;
				
				// Process request.
				if (input.size() > 0) {
					System.out.println(input.get(0));
					respondToRequest(input, writer);
				}
				else {
		    		writeError(BAD_REQUEST, "No Request Received.", writer);
				}				
	
			} catch (IOException ex) {
				System.out.println(ex);
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
		}

		/**
		 * Process request string & delegate to handler functions.
		 */
		private static void respondToRequest(List<String> fullRequest, PrintStream writer) throws IOException {
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
		    	
		    	// Initialize agent state.
		    	AgentState agentState = new AgentState();
		    	
		    	// Migrate request?
				if (tokens.get(0).contains(MIGRATE)) {
					Map<String,String> paramMap = parseParams(tokens.get(1));
					agentState = new AgentState(paramMap.get(INPUT), Integer.parseInt(paramMap.get(COUNT)));
				}
				
				// Parse host name from header.
		    	String host = null;
		    	for (String header : fullRequest) {
		    		if (header.contains(HOST_HEADER)) {
		    			host = header.substring(HOST_HEADER.length(), header.lastIndexOf(':'));
		    			break;
		    		}
		    	}
		    	
				// Start Agent listener thread.
		    	int port = ++AG_PORT;
				new Thread(new Server(port, new AgentServerStrategy(agentState))).start();
				System.out.println("Hosting Agent at: " + host + ':' + port);
				
				// Reply to browser client or to migration request.
				if (!tokens.get(0).contains(MIGRATE)) {
					// Build HTML response for browser client.
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
					String response = responseBuilder.toString();
					writeOkHeader(response.length(), mimeTypes.get("html"), writer);
					writer.print(response);
					writer.print(CRLF);			
				}
				else {
					// Handle migration response.
					writer.print(host + ':' + port);
					writer.print(CRLF + CRLF);
				}
				writer.flush();				
			} catch (IOException ex) {
				System.out.println(ex);
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
		
		/**
		 * Writes OK and other output headers for successful response.
		 */
		private static void writeOkHeader(long length, String mimeType, PrintStream writer) throws IOException {
			writer.print("HTTP/1.1 200 OK" + CRLF);
			writer.print("Content-Length: " + length + CRLF);
			writer.print("Content-Type: " + mimeType + CRLF);
			writer.print("Connection: close" + CRLF + CRLF);
		}
		
		/**
		 * Writes error code & html back to browser.
		 */
		private static void writeError(int code, String error, PrintStream writer) throws IOException {
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
	}
		
	/**
	 * Implementation for processing Agent Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class AgentServerStrategy implements ServerStrategy {
		/** Agent state. */
		private AgentState agentState;		
		
		public AgentServerStrategy(AgentState agentState) {
			this.agentState = agentState;
		}

		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Agent Server";
		}
		
		/** Processes a client request specific for the agent server strategy. */
		@Override
		public void processRequest(Socket socket, Server server) {
			BufferedReader reader =  null;
			PrintStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintStream(socket.getOutputStream());

				// Read all input from back channel browser via socket.
				List<String> input = new ArrayList<String>();
				do {
					// Read line by line & save in list.
					String line = reader.readLine();
					input.add(line);
				} while (reader.ready()) ;
				
				// Process request.
				if (input.size() > 0) {
					System.out.println(input.get(0));
					respondToRequest(input, writer, server);
				}
				else {
		    		writeError(BAD_REQUEST, "No Request Received.", writer);
				}				

				writer.flush();				
			} catch (IOException ex) {
				System.out.println(ex);
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
		}
		
		/**
		 * Process request string & delegate to handler functions.
		 */
		private void respondToRequest(List<String> fullRequest, PrintStream writer, Server server) throws IOException {
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
    			String migrateRequest = MIGRATE + ' ' + AGENT + '?' + INPUT + '=' + agentState.getInput() + '&' + COUNT + '=' + agentState.getCount() + CRLF;
    			migrateRequest += HOST_HEADER + host + ':' + port + CRLF + CRLF;
				String response = genericRequest(host, HS_PORT, migrateRequest);
    			host = response.substring(0, response.lastIndexOf(':'));
    			String newPort = response.substring(response.lastIndexOf(':')+1);
				// Build HTML redirection for browser client.
				StringBuilder responseBuilder = new StringBuilder();
				responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
				responseBuilder.append("<html><head>").append(CRLF);
				responseBuilder.append("<meta charset=\"UTF-8\">").append(CRLF);
				responseBuilder.append("<meta http-equiv=\"refresh\" content=\"1;url=http://").append(host).append(':').append(newPort).append("\">").append(CRLF);
				responseBuilder.append("<script type=\"text/javascript\">").append(CRLF);
				responseBuilder.append("window.location.href = \"").append("http://").append(host).append(':').append(newPort).append('"').append(CRLF);
				responseBuilder.append("</script>").append(CRLF);
				responseBuilder.append("<title>Page Redirection</title>").append(CRLF);
				responseBuilder.append("</head><body></body></html>").append(CRLF);				
				response = responseBuilder.toString();
				writeOkHeader(response.length(), mimeTypes.get("html"), writer);
				writer.print(response);
				writer.print(CRLF);
				server.setControlSwitch(false);
				genericRequest(host, Integer.parseInt(port), "NULL" + CRLF + CRLF);
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
    		}
	    }
		
		/**
		 * Writes OK and other output headers for successful response.
		 */
		private static void writeOkHeader(long length, String mimeType, PrintStream writer) throws IOException {
			writer.print("HTTP/1.1 200 OK" + CRLF);
			writer.print("Content-Length: " + length + CRLF);
			writer.print("Content-Type: " + mimeType + CRLF);
			writer.print("Connection: close" + CRLF + CRLF);
		}
		
		/**
		 * Writes error code & html back to browser.
		 */
		private static void writeError(int code, String error, PrintStream writer) throws IOException {
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
	}
	
	/**
	 * Holds the Agent's state.
	 */
	private static class AgentState {
		/** Agent Input. */
		private String input;
		/** Agent counter. */
		private int count;
		
		public AgentState() {
			this.input = "Default";
			this.count = 1;
		}

		public AgentState(String input, int count) {
			this.input = input;
			this.count = count;
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
	 * Parses parameters from URL and returns as map.
	 */
	private static Map<String,String> parseParams(String request) {
    	Map<String,String> paramMap = new HashMap<String,String>(); 
		String params;
		if (!request.contains("?") || (params = request.substring(request.indexOf('?')+1)) == null || params.length() == 0) {
    		return paramMap;
		}
		
    	StringTokenizer toker = new StringTokenizer(params, "&");
    	while (toker.hasMoreTokens()) {
    		String token, name, value;
    		token = toker.nextToken();
			if (!token.contains("=") || (name = token.substring(0, token.indexOf('='))) == null || name.length() == 0
					|| (value = token.substring(token.indexOf('=')+1)) == null || value.length() == 0) {
	    		return paramMap;
			}
    		paramMap.put(name, value);
    		System.out.println(name + '=' + value);
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
	private static String genericRequest(String server, int port, String request) {
		String response = null;
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
			response = reader.readLine();
		} catch (IOException ex) {
			System.out.println(ex);
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
