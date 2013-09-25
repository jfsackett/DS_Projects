/*
 * Notes:
 * Uses user id as cookie so using the same id will affect that client's state.
 * Continue prompting for mode changes, shutdown of server until admin client quit.
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

/**
 * This application connects to a joke server admin port via a socket to perform maintenance.
 * It can change the server's mode between joke, proverb & maintenance as well as shut down the server.
 * @author Joseph Sackett
 */
public class JokeClientAdmin {
	/** Admin Port to connect to. */
	private static final int PORT = 4122;
	
	/** Joke mode protocol. */
	private static final String JOKE_MODE = "joke-mode";
	
	/** Proverb mode protocol. */
	private static final String PROVERB_MODE = "proverb-mode";

	/** Maintenance mode protocol. */
	private static final String MAINTENANCE_MODE = "maintenance-mode";

	/** Shutdown protocol. */
	private static final String SHUTDOWN = "shutdown";

	/**
	 * Main program accepts user input loop until quit, calling server with mode change requests. 
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
		System.out.println("Joe Sackett's Administration Client.");
		System.out.println("Using server: " + serverName + ':' + PORT);		
		// Show usage.
		showUsage();
		
		try {
			// Prepare to read user input.
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			
			String mode = null;
			while (mode == null || mode.length() == 0 || !"Q".equalsIgnoreCase(mode.substring(0, 1))) {
				// Prompt to continue. Default is to continue on empty input. Will exit on input not starting with 'Y' or 'y'.
				System.out.print("Select mode or quit (J/P/M/S/Q/?): ");
				System.out.flush();
				mode = stdIn.readLine();
				
				// Empty input?
				if (mode == null || mode.length() == 0) {
					continue;
				}
				
				// Help? Show Usage.
				if ("?".equalsIgnoreCase(mode.substring(0, 1))) {
					showUsage();
					continue;
				}
				
				// Quitting?
				if ("Q".equalsIgnoreCase(mode.substring(0, 1))) {
					break;
				}
				
				doAdminServerRequest(serverName, mode);				
			}
			System.out.println("Administration client exiting. Goodbye.");
		} catch (IOException ex) {
			System.out.println(ex);
		}
	}
	
	private static void showUsage() {
		System.out.println("Administraction Menu:");
		System.out.println("J - Joke Mode");
		System.out.println("P - Proverb Mode");
		System.out.println("M - Maintenance Mode");
		System.out.println("S - Shutdown Server");
		System.out.println("Q - Quit");
		System.out.println("? - Show Usage");
	}
	
	private static void doAdminServerRequest(String serverName, String mode) {
		Socket socket = null;
		BufferedReader reader = null;
		PrintStream writer = null;
		try {
			String modeRequest;
			switch (mode.charAt(0)) {
			case 'J':
			case 'j':
				modeRequest = JOKE_MODE;
				System.out.println("Putting Server in Joke mode.");
				break;
				
			case 'P':
			case 'p':
				modeRequest = PROVERB_MODE;
				System.out.println("Putting Server in Proverb mode.");
				break;
				
			case 'M':
			case 'm':
				modeRequest = MAINTENANCE_MODE;
				System.out.println("Putting Server in Maintenance mode.");
				break;
				
			case 'S':
			case 's':
				modeRequest = SHUTDOWN;
				System.out.println("Shutting Down Server.");
				break;
				
			default:
				System.out.println("Input Error: " + mode);
				return;
			}
			// Open connection to server.
			socket = new Socket(serverName, PORT);
			
			// Create reader from socket input stream.
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			// Create print stream writer from socket output stream.
			writer = new PrintStream(socket.getOutputStream());

			// Send request to server.
			writer.println(modeRequest);
			writer.flush();
			
			// Read returned response.
			while ((reader.readLine()) != null) {
				// Discard.
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
				writer.close();
			}
			if (socket != null) {
				try {socket.close();} catch (IOException ex) {}
			}
		}
	}
}
