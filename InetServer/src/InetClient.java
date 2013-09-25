/*--------------------------------------------------------
Joseph Sackett
September 17, 2013

Developed and tested with JDK 1.7.0_06.

Build Instructions:
Unzip all source files to same directory.
From a command prompt in that directory:
javac *.java

Execution Instructions:
InetServer works with InetClient and should be executed first.
1) From a command prompt in the same directory as the build:
java InetServer
2) Open another command prompt in the same directory:
java InetClient

Follow the prompts on InetClient, asking you to enter a host name.
Expect the server to perform a DNS lookup of that host and return the results to the client for output.
The output in the InetServer and InetClient programs in their command prompts should show their activity.

InetClient can connect to an InetServer running on a different machine by specifying the hostname or ip address
of the server as the command line parameter (e.g. for machine my_host_name at ip address 192.168.1.42):
java InetClient my_host_name
or
java InetClient 192.168.1.42

Included Files:
 a. checklist.html
 b. InetServer.java
 c. InetClient.java

Notes:
THE SALIENT PORTIONS OF THIS CODE WERE COPIED ALMOST VERBATIM FROM THE ASSIGNMENT NOTES, per instructions.
I made changes to it along some formatting, naming and general practices. I also added a lot of comments.

----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

/**
 * This program prompts the user for a host name and connects with the server to look up ip address. 
 * @author Clark Elliott (enhanced by Joe Sackett)
 */
public class InetClient {
	/** Port to connect to. */
	private static final int PORT = 4121;

	/**
	 * Main program accepts user input loop until quit, calling server for ip lookup. 
	 * @param args optional specification of server name.
	 */
	public static void main(String[] args) {
		// retrieve the server from the command line
		String serverName;
		if (args.length == 0) {
			// default to localhost.
			serverName = "localhost";
		}
		else {
			serverName = args[0];
		}
		
		System.out.println("Joe Sackett's Inet Client (borrowed from Clark Elliott), 1.8");
		System.out.println("Using: " + serverName + ':' + PORT);
		// Prepare to read user input.
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		try {
			String host;
			do {
				// Prompt user.
				System.out.print("Enter a hostname or an IP address, (quit) to end: ");
				System.out.flush();
				// Read input host.
				host = in.readLine();
				if (host != null && host.length() > 0 && !"quit".equalsIgnoreCase(host)) {
					// Connect to server for ip address lookup.
					getRemoteAddress(host, serverName);
				}
			} while (!"quit".equalsIgnoreCase(host));
			System.out.println("Cancelled by user request.");
		} catch (IOException ex) {
			System.out.println(ex);
		}
	}
	
	/**
	 * Connect to server, pass input hostname, retrieve & output response.
	 * @param host host for lookup by server.
	 * @param serverName server to connect to.
	 */
	private static void getRemoteAddress(String host, String serverName) {
		try {
			// Open connection to server.
			Socket socket = new Socket(serverName, PORT);
			
			// Create reader from socket input stream.
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			// Create print stream writer from socket output stream.
			PrintStream out = new PrintStream(socket.getOutputStream());
			
			// Send request to server.
			out.println(host);
			out.flush();
			
			// Read returned response.
			for (int ix = 0; ix <= 3; ix++) {
				String response = in.readLine();
				if (response != null) {
					System.out.println(response);
				}
			}
			
			socket.close();
		} catch (IOException ex) {
			System.out.println("Socket error.");
			System.out.println(ex);
		}
	}

}
