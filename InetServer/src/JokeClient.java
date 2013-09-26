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
This saves state persistently using Java serialization. Expect JokeServer to create a file named UsersState.ser
Significant effort was put into writing thread-safe code but I'm not certain I got it all correct.

----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * This application connects to a joke server via a socket and asks makes a request.
 * It will echo the joke, proverb, or whatever returned by the server.  
 * @author Joseph Sackett
 */
public class JokeClient {
	/** Port to connect to. */
	private static final int PORT = 4121;
	
	/** Joke request protocol. */
	private static final String JOKE_REQUEST = "Give me something";

	/**
	 * Main program accepts user input loop until quit, calling server for joke requests. 
	 * @param args optional specification of server name.
	 */
	public static void main(String[] args) {
		// Retrieve the server from the command line, optional.
		String serverName;
		if (args.length == 0) {
			// default to localhost.
			serverName = "localhost";
		}
		else {
			serverName = args[0];
		}
		
		// Display application header.
		System.out.println("Joe Sackett's Joke Client.");
		System.out.println("Using server: " + serverName + ':' + PORT);
		
		try {
			// Prepare to read user input.
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			
			// Prompt for user's login id; non-empty required.
			String loginId;
			do {
				System.out.print("Enter your unique client Login Id (hint: your email adddress): ");
				System.out.flush();
				loginId = stdIn.readLine();
			} while (loginId == null || loginId.length() == 0);
		
			// Prompt for user's name; non-empty required.
			String userName;
			do {
				System.out.print("Enter your Name: ");
				System.out.flush();
				userName = stdIn.readLine();
			} while (userName == null || userName.length() == 0);
		
			String another;
			do {
				String result = doServerRequest(serverName, loginId, userName);
				if (result != null && result.length() > 0) {
					// Display result.
					System.out.println(result);
				}
				
				// Prompt to continue. Default is to continue on empty input. Will exit on input not starting with 'Y' or 'y'.
				System.out.print("Would you like another ([Y]/N): ");
				System.out.flush();
				another = stdIn.readLine();
			} while (another == null || another.length() == 0 || "Y".equalsIgnoreCase(another.substring(0, 1)));
			System.out.println("I hope you got some laughs and wisdom. Goodbye.");
		} catch (IOException ex) {
			System.out.println(ex);
		}
	}
	
	private static String doServerRequest(String serverName, String loginId, String userName) {
		StringBuffer fullResponse = new StringBuffer();
		Socket socket = null;
		BufferedReader reader = null;
		ObjectOutputStream writer = null;
		try {
			// Open connection to server.
			socket = new Socket(serverName, PORT);
			
			// Create reader from socket input stream.
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			// Create object stream writer from socket output stream.
			writer = new ObjectOutputStream(socket.getOutputStream());  

			// Send request to server.
			writer.writeObject(new String[]{JOKE_REQUEST, loginId, userName});
			writer.flush();
			
			// Read returned response.
			String response;
			while ((response = reader.readLine()) != null) {
				fullResponse.append(response);
			}
		} catch (IOException ex) {
			System.out.println("Socket error.");
			System.out.println(ex);
		}
		finally {
			if (reader != null) {
				try {reader.close();} catch (IOException ex) {}
			}
			if (writer != null) {
				try {writer.close();} catch (IOException ex) {}
			}
			if (socket != null) {
				try {socket.close();} catch (IOException ex) {}
			}
		}
		
		return fullResponse.toString();
	}
}
