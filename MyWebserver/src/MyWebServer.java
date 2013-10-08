/*--------------------------------------------------------

Joseph Sackett
October 7, 2013

Developed and tested with JDK 1.7.0_40.

Build Instructions:
Unzip all source files to same directory.
From a command prompt in that directory, execute:
javac *.java

Execution Instructions:
MyWebServer emulates a real web server, except it runs on port 2540 and supports the Firefox web browser.
1) From a command prompt in the same directory as the build, execute:
java MyWebServer
2) Open Firefox and type this into the browser line:
http://localhost:2540/
3) Expect it to return a listing of the directory where the web server program was executed.
4) Browse through directories or click on a file to have it display in browser, specific to its mime type.
5) Try many different types of files and directories to test it fully.

A browser from a different machine can perform all of the same actions as long as the firewall rules permit port 2540.
To test this, substitute the IP address or hostname of the server running MyWebServer for the localhost in the above instructions.

Included Files:
 a. checklist-mywebserver.html
 b. MyWebServer.java
 c. http-streams.txt
 d. serverlog.txt

Notes:
Dynamic mime-type mapping.
Handles binary data.

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
 * It also processes a form submit by emulating a CGI call.  
 * @author Joseph Sackett
 */
public class MyWebServer {
	/** Web Server port to bind. */
	private static final int PORT = 2540;
	
	/** Relative URL for CGI emulation. */
	private static final String CGI_CALL = "/cgi/addnums.fake-cgi";
		
	/** Path separator. */
	private static final char PATH_SEP = File.separator.charAt(0);
	
	/** Person parameter name. */
	private static final String PERSON = "person";
	
	/** Number 1 parameter name. */
	private static final String NUM1 = "num1";
	
	/** Number 2 parameter name. */
	private static final String NUM2 = "num2";
	
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
	
	/** File containing file extension to mime type mappings. */
	private static final String MIME_INPUT_FILE = "MimeTypes.txt";
	
	/** File extension to Mime type map. */
	private static Map<String,String> mimeTypes = new HashMap<String,String>();
		
	/** Code to Response string map. */
	private static Map<Integer,String> responses = new HashMap<Integer,String>();
		
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
	 * Main Web Server Server program.
	 * - Initializes global state
	 * - Loop continually, spawning workers for each connection.
	 */
	public static void main(String[] args) {
		System.out.println("Joe Sackett's Web Server.");
		System.out.println("Web Server Port: " + PORT);
		
		// Load Initial Mime Types.
		loadMimeTypesFile();
		
		ServerSocket serverSocket = null;
		try {
			serverSocket = new ServerSocket(PORT);
			while (true) {
				// Wait for the next browser connection.
				Socket socket = serverSocket.accept();

				// Spawn thread to process request.
				new Thread(new Worker(socket)).start();
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
			// /cgi/addnums.fake-cgi?person=YourName&num1=4&num2=5
			String params;
			if (!request.contains("?") || (params = request.substring(request.indexOf('?')+1)) == null || params.length() == 0) {
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
			}
			
	    	StringTokenizer toker = new StringTokenizer(params, "&");
	    	Map<String,String> paramMap = new HashMap<String,String>(); 
	    	while (toker.hasMoreTokens()) {
	    		String token, name, value;
	    		token = toker.nextToken();
				if (!token.contains("=") || (name = token.substring(0, token.indexOf('='))) == null || name.length() == 0
						|| (value = token.substring(token.indexOf('=')+1)) == null || value.length() == 0) {
		    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
				}
	    		paramMap.put(name, value);
	    		System.out.println(name + '=' + value);
	    	}
	    	if (paramMap.size() < 3) {
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	
	    	String person;
	    	int n1, n2, result;
	    	try {
	    		String num1, num2;
	    		if ((person = paramMap.get(PERSON)) == null || (num1 = paramMap.get(NUM1)) == null || (num2 = paramMap.get(NUM2)) == null) {
		    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
		    	}
	    		n1 = Integer.parseInt(num1);
	    		n2 = Integer.parseInt(num2);
	    		result = n1 + n2;
	    	}
	    	catch (NumberFormatException ex) {
	    		System.out.println(ex);
	    		writeError(BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	
			StringBuilder responseBuilder = new StringBuilder();
			responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
			responseBuilder.append("<html><head>").append(CRLF);
			responseBuilder.append("<title>").append("Addnums Mock CGI").append("</title>").append(CRLF);
			responseBuilder.append("</head><body>").append(CRLF);
			responseBuilder.append("<h1>").append("Addnums Mock CGI").append("</h1>").append(CRLF);
			responseBuilder.append("<p>").append("Dear ").append(person).append(", the sum of ").append(n1).append(" and ").append(n2).append(" is ").append(result).append(".</p>").append(CRLF);
			responseBuilder.append("</body></html>").append(CRLF);
			String response = responseBuilder.toString();
			
			writeOkHeader(response.length(), mimeTypes.get("html"), writer);
			writer.writeBytes(response);
			writer.flush();
		}
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
		}
	}
	
}
