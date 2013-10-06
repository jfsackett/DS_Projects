/*--------------------------------------------------------

Joseph Sackett
September 25, 2013

Developed and tested with JDK 1.7.0_40.

Build Instructions:
Unzip all source files to same directory.
From a command prompt in that directory, execute:
javac *.java

Execution Instructions:
JokeServer works with JokeClient and JokeClientAdmin but JokeServer should be executed first.
1) From a command prompt in the same directory as the build, execute:
java JokeServer
2) Open another command prompt in the same directory as the build, execute:
java JokeClient
3) Enter your email address at the prompt.
4) Enter your name at the prompt.
5) Expect it to return a joke containing your name.
6) Decide whether to have it give you another joke or proverb.
7) Open another command prompt in the same directory as the build, execute:
java JokeClientAdmin
8) Read the usage options to see what the Admin can do.
9) Type: P and hit return to put the JokeServer in proverb mode.
10) Return to JokeClient and make another request.
11) Expect it to return a proverb containing your name.
12) Try different scenarios to test it fully.

JokeClient and JokeClientAdmin can connect to a JokeServer running on a different machine by specifying the hostname or ip address
of the server as the command line parameter at client startup (e.g. for machine my_host_name at ip address 192.168.1.42):
java JokeClient my_host_name
or
java JokeClient 192.168.1.42
The JokeClientAdmin can connect from a remote server using the same mechanism.

Included Files:
 a. checklist-joke.html
 b. InetServer.java
 c. InetClient.java
 d. JokeServer.java
 e. JokeClient.java
 f. JokeClientAdmin.java
 g. JokeInput.txt
 h. JokeOutput.txt

Notes:
JokeServer saves users' state persistently using Java serialization. Expect JokeServer to create a file named UsersState.ser
Because users' state is persistently maintained, the best way to start a fresh test is to use a brand new Login Id
 or by deleting the UsersState.ser file before starting JokeServer.
Significant effort was put into writing thread-safe code but I'm not certain I got it all correct.

----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * This server listens for processes connections from web browser clients.
 * It processes HTTP GET requests for specific files and directory listings.
 * It also processes a form submit by emulates a CGI call.  
 * @author Joseph Sackett
 */
public class MyWebServer {
	/** Web Server port to bind. */
	private static final int PORT = 2540;
	
	/** Relative URL for CGI emulation. */
	private static final String CGI_CALL = "/cgi/addnums.fake-cgi";
		
	/** Relative URL for server shutdown. */
	private static final String SHUTDOWN = "/tear_down_the_wall";
	
	/** Path separator. */
	private static final char PATH_SEP = File.separator.charAt(0);
	
	/** Backslash file separator? */
	private static final char SLASH = '/';
		
	/** CRLF */
	private static final String CRLF = "\r\n";
	
	/** GET */
	private static final String GET = "GET";
	
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
	
	/** File extension to Mime type map. */
	private static Map<String,String> mimeTypes = new HashMap<String,String>();
		
	/** Code to Response string map. */
	private static Map<Integer,String> responses = new HashMap<Integer,String>();
		
	/** Global mode for the server. Thread safe. */
	private static ServerState serverState = new ServerState();
	
	/** Static block run when class loaded. */
	static {
		// Add supported Mime types here.
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

		// Add HTTP responses here.
		responses.put(OK, "OK");
		responses.put(NOT_FOUND, "Not Found");
		responses.put(NO_RESPONSE, "No Response");
		responses.put(BAD_REQUEST, "Bad Request");
		responses.put(FORBIDDEN, "Forbidden");
	}
	
	/**
	 * Main Web Server Server program.
	 * - Initializes global state
	 * - Loop until shutdown, spawning workers for each connection. 
	 */
	public static void main(String[] args) {
		System.out.println("Joe Sackett's Web Server.");
		System.out.println("Web Server Port: " + PORT);
		
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(PORT);
			while (serverState.isControlSwitch()) {
				// Wait for the next browser connection.
				Socket socket = serverSocket.accept();
				// Check for shutdown preceding client connection.
				if (serverState.isControlSwitch()) {
					// Spawn thread, along with Joke or Admin strategy.
					new Thread(new Worker(socket)).start();
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
		System.out.println("My Web Server exiting.");
	}	

	/**
	 * Worker processes input requests from browser.
	 */
	private static class Worker implements Runnable {
		/** Socket connected to the client whom this worker will process. */
		Socket socket;
		
		public Worker(Socket socket) {
			this.socket = socket;
		}

		/**
		 * Method to execute when thread is spawned.
		 * Processes incoming HTTP request.
		 */
		@Override
		public void run() {
			System.out.println("Spawning worker to process HTTP request.");
			BufferedReader reader =  null;
			DataOutputStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//				writer = new PrintStream(socket.getOutputStream());
				writer = new DataOutputStream(socket.getOutputStream());

				// Read all input from web browser via socket.
				List<String> input = new ArrayList<String>();
				while (reader.ready()) {
					// Read line by line & save in list.
					String line = reader.readLine();
					input.add(line);
					System.out.println(line);
				}
				
				// Process request.
				if (input.size() > 0) {
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
	    	
	    	// Check for dummy CGI request.
	    	if (tokens.get(1).startsWith(CGI_CALL)) {
//	    		writeError(OK, "ok.", writer);
	    		processCgiRequest(tokens.get(1), writer);
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
		
		private static void writeError(int code, String error, DataOutputStream writer) throws IOException {
			// Build response HTML.
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
		
		private static void writeOkHeader(long length, String mimeType, DataOutputStream writer) throws IOException {
			writer.writeBytes("HTTP/1.1 200 OK" + CRLF);
			writer.writeBytes("Content-Length: " + length + CRLF);
			writer.writeBytes("Content-Type: " + mimeType + CRLF);
			writer.writeBytes("Connection: close" + CRLF + CRLF);
		}
		
		private static void processFileRequest(File file, DataOutputStream writer) throws IOException {
			writeOkHeader(file.length(), mimeTypes.get(file.getName().substring(file.getName().lastIndexOf(".")+1).toLowerCase()), writer);
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
//				writer.writeBytes(CRLF);
				writer.flush();
			}
		}
		
		private static void processDirRequest(File dir, DataOutputStream writer) throws IOException {
			String currDir = dir.getPath().substring(1).replace(PATH_SEP, SLASH);
			currDir = (currDir.length() == 0) ? "/" : currDir;
			String parentDir = (dir.getParent() == null) ? "" : dir.getParent().substring(1).replace(PATH_SEP, SLASH);
			parentDir = (parentDir.length() == 0 && currDir.length() > 1) ? "/" : parentDir;
			System.out.println("Path2: " + currDir);
			System.out.println("Parent2: " + parentDir);
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
			
			String response = responseBuilder.toString();
			writeOkHeader(response.length(), mimeTypes.get("html"), writer);
			writer.writeBytes(response);
			writer.writeBytes(CRLF);
			writer.flush();
		}
		
		private static void processCgiRequest(String request, DataOutputStream writer) throws IOException {
			System.out.println(request);
    		writeError(OK, "ok.", writer);
		}
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
