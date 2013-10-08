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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * This server listens for processes connections from web browser clients.
 * It processes HTTP GET requests for specific files and directory listings.
 * It also processes a form submit by emulates a CGI call.  
 * @author Joseph Sackett
 */
public class MyListener {
	/** Web Server port to bind. */
	private static final int PORT = 80;
	
	/** CRLF */
	private static final String CRLF = "\r\n";

	/** Global mode for the server. Thread safe. */
	private static ServerState serverState = new ServerState();
	
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
				writer = new DataOutputStream(socket.getOutputStream());

				// Read all input from web browser via socket.
				List<String> input = new ArrayList<String>();
				while (reader.ready()) {
					// Read line by line & save in list.
					String line = reader.readLine();
					input.add(line);
					System.out.println(line);
				}
				
				StringBuilder responseBuilder = new StringBuilder();
				responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(CRLF);
				responseBuilder.append("<html><head>").append(CRLF);
				responseBuilder.append("<title>").append("Echo").append("</title>").append(CRLF);
				responseBuilder.append("</head><body>").append(CRLF);
				responseBuilder.append("<h1>").append("Echo").append("</h1>").append(CRLF);
				responseBuilder.append("<pre>").append(CRLF);
				for (String in : input) {
					responseBuilder.append(in).append(CRLF);
				}
				responseBuilder.append("</pre>").append(CRLF);
				responseBuilder.append("</body></html>").append(CRLF);			
				
				String response = responseBuilder.toString();
				writeOkHeader(response.length(), "text/html", writer);
				writer.writeBytes(response);
				writer.writeBytes(CRLF);
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
					try{writer.close();} catch (IOException ex) {}
				}
				if (socket != null) {
					try {socket.close();} catch (IOException ex) {}
				}
			}
		}
		
		private static void writeOkHeader(long length, String mimeType, DataOutputStream writer) throws IOException {
			writer.writeBytes("HTTP/1.1 200 OK" + CRLF);
			writer.writeBytes("Content-Length: " + length + CRLF);
			writer.writeBytes("Content-Type: " + mimeType + CRLF);
			writer.writeBytes("Connection: close" + CRLF + CRLF);
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
