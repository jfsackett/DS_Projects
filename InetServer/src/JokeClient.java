/*
 * Notes:
 * Uses user id as cookie so using the same id will affect that client's state.
 * Continue prompt will give another joke for any string starting with 'Y' or 'y' as well as empty string
 * and quit for any other input.
 */
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
				System.out.print("Enter your unique client login id (not your name): ");
				System.out.flush();
				loginId = stdIn.readLine();
			} while (loginId == null || loginId.length() == 0);
		
			// Prompt for user's name; non-empty required.
			String userName;
			do {
				System.out.print("Enter your name: ");
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
			System.out.println("I hope you got some laughs or wisdom. Goodbye.");
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
