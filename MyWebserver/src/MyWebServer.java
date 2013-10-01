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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

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
		 */
		@Override
		public void run() {
			System.out.println("Spawning worker to process HTTP request.");
			BufferedReader reader =  null;
			PrintStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintStream(socket.getOutputStream());

				// Read input from client via socket.
				String input;
				while ((input = reader.readLine()) != null) {
				
				System.out.println(input);
				}
				writer.println("Receiving 5 over 5.");
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
