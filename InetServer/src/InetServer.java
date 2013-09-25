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
From a command prompt in the same directory as the build:
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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * This program binds a listener to a port and receives an input hostname from clients, 
 * looks up remote host information and returns it to the client. 
 * @author Clark Elliott (enhanced by Joe Sackett)
 */
public class InetServer {
	/** Port to bind. */
	private static final int PORT = 4121;
	/** # requests in queue. */
	private static final int QUEUE_LENGTH = 6;
	
	/** Main control switch used for shutdown. */
	private static boolean controlSwitch = true;

	/**
	 * Server program.
	 */
	public static void main(String[] args) {
		ServerSocket serverSocket = null;
		try {
			// Bind the listener to the port.
			serverSocket = new ServerSocket(PORT, QUEUE_LENGTH);
			
			System.out.println("Joe Sackett's Inet server (borrowed from Clark Elliott) starting up and listening at port: " + PORT);
			// Loop until remotely shutdown.
			while (controlSwitch) {
				if (controlSwitch) {
					// Spawn a worker thread to do the work when connection received from a client.
					new Worker(serverSocket.accept()).start();
				}
				// Try uncommenting.
				// try { Thread.sleep(10000); } catch (InterruptedException ex) {}
			}
		} catch (IOException ex) {
			System.out.println(ex);
			System.out.println("Socket error.");
		} finally {
			try { serverSocket.close(); } catch (IOException ex) {}
		}
	}


	private static class Worker extends Thread {
		/** Socket connection to client. */
		Socket socket;
		
		public Worker(Socket socket) {
			super();
			this.socket = socket;
		}
		
		public void run() {
			try {
				// Get I/O streams from the socket.
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				PrintStream out = new PrintStream(socket.getOutputStream());
				//TODO Check warning about not executing when expected.
				if (!InetServer.controlSwitch) {
					System.out.println("Server is shutting down per client request.");
					out.println("Server is now shutting down. Goodbye.");
				}
				else {
					try {
						// Read input from client via socket.
						String input = in.readLine();
						if ("shutdown".equalsIgnoreCase(input)) {
							// Handle remote shutdown.
							InetServer.controlSwitch = false;
							System.out.println("Worker captured a shutdown request.");
							out.println("Shutdown request noted by worker.");
							out.println("Please send final shutdown request to listener.");
						}
						else {
							// Look up server information and write back to client via socket.
							System.out.println("Looking up: " + input);
							printRemoteAddress(input, out);
						}
					} catch (IOException ex) {
						System.out.println(ex);
						System.out.println("Server read error.");
					}
				}
				socket.close();
			} catch (IOException ex) {
				System.out.println(ex);
			}
		}
	}

	/**
	 * Look up and output the name and IP address of input host.
	 * @param host name or address of a host.
	 * @param out output stream for server.
	 */
	private static void printRemoteAddress(String host, PrintStream out) {
		try {
			out.println("Looking up: " + host + "...");
			// Look up host name and IP address of input host.
			InetAddress inetAddress = InetAddress.getByName(host);
			out.println("Host name: " + inetAddress.getHostName());
			out.println("Host IP address: " + ipAddressToText(inetAddress.getAddress()));
		} catch (UnknownHostException ex) {
			out.println(ex);
			out.println("Failed to lookup: " + host);
			out.println("It may not exist or be unreachable due to a firewall.");
		}
	}

	/**
	 * Converts ip address bytes to common string representation.
	 * @param ipAddress byte array containing the ip address octets.
	 * @return string representation of the ip address.
	 */
	private static String ipAddressToText(byte ipAddress[]) { 
		//TODO Make portable for 128 bit format.
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < ipAddress.length; i++) {
			if (i > 0) {
				result.append(".");
			}
			result.append(0xff & ipAddress[i]);
		}
		return result.toString();
	}
}
