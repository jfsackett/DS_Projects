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
import java.net.Socket;

/**
 * This application connects to a joke server admin port via a socket to perform maintenance.
 * It can change the server's mode between joke, proverb & maintenance as well as shut down the server.
 * @author Joseph Sackett
 */
public class MyTelnet {
	/** Admin Port to connect to. */
	private static final int PORT = 80;

	private static final String CRLF = "\r\n";

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
		System.out.println("Joe Sackett's MyTelnet.");
		System.out.println("Using server: " + serverName + ':' + PORT);		
		// Show usage.
//		showUsage();
		
		try {
			// Prepare to read user input.
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			
			String mode = null;
			while (mode == null || mode.length() == 0 || !"Q".equalsIgnoreCase(mode.substring(0, 1))) {
				// Prompt to continue. Default is to continue on empty input. Will exit on input not starting with 'Y' or 'y'.
				System.out.print("Enter HTTP string: ");
				System.out.flush();
				mode = stdIn.readLine();
				
				// Empty input?
				if (mode == null || mode.length() == 0) {
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
	
	private static void doAdminServerRequest(String serverName, String mode) {
		Socket socket = null;
		BufferedReader reader = null;
		PrintStream writer = null;
		try {
			// Open connection to server.
			socket = new Socket(serverName, PORT);
			
			// Create reader from socket input stream.
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			// Create print stream writer from socket output stream.
			writer = new PrintStream(socket.getOutputStream());

			String request = "GET /elliott/cat.html HTTP/1.1" + CRLF;
			request += "Host: condor.depaul.edu" + CRLF + CRLF;
//			request += "User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0" + CRLF;
//			request += "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" + CRLF;
//			request += "Accept-Language: en-US,en;q=0.5" + CRLF;
//			request += "Accept-Encoding: gzip, deflate" + CRLF;
//			request += "Connection: close" + CRLF;
//			request += CRLF;
			
			// Send request to server.
			writer.println(request);
			writer.flush();
			
			// Read returned response.
			String response;
			while ((response = reader.readLine()) != null) {
				System.out.println(response);
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
