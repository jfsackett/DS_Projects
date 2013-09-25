import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This program looks up local and remote host information via the java.net package. 
 * @author Clark Elliott (enhanced by Joe Sackett)
 */
public class InetAddresser {

	/**
	 * Main program prints the hostname and IP address of the local machine and then 
	 * prompts to perform similar lookup for remote machines.
	 */
	public static void main(String[] args) {
		System.out.println("Clark Elliott's INET addresser program (enhanced by Joe Sackett), 1.8");
		// Print network information for local name or address.
		printLocalAddress();
		
		// Prepare to read user input.
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		try {
			String input;
			do {
				// Prompt user.
				System.out.print("Enter a hostname or an IP address, (quit) to end: ");
				System.out.flush();
				// Read input.
				input = in.readLine();
				if (input != null && input.length() > 0 && !"quit".equalsIgnoreCase(input)) {
					// Print network information for input name or address.
					printRemoteAddress(input);
				}
			} while (!"quit".equalsIgnoreCase(input));
			System.out.println("Exiting.");
		} catch (IOException ex) {
			System.out.println(ex);
		}
	}

	/**
	 * Look up and output the name and IP address of local host.
	 */
	private static void printLocalAddress() {
		try {
			// Look up host name and IP address of localhost.
			InetAddress localhost = InetAddress.getLocalHost();
			System.out.println("Local name: " + localhost.getHostName());
			System.out.println("Local IP address: " + ipAddressToText(localhost.getAddress()));
		} catch (UnknownHostException ex) {
			System.out.println(ex);
			System.out.println("Failed to lookup localhost; check firewall.");
		}
	}
	
	/**
	 * Look up and output the name and IP address of input host.
	 * @param host name or address of a host.
	 */
	private static void printRemoteAddress(String host) {
		try {
			System.out.println("Looking up: " + host + "...");
			// Look up host name and IP address of input host.
			InetAddress inetAddress = InetAddress.getByName(host);
			System.out.println("Host name: " + inetAddress.getHostName());
			System.out.println("Host IP address: " + ipAddressToText(inetAddress.getAddress()));
		} catch (UnknownHostException ex) {
			System.out.println(ex);
			System.out.println("Failed to lookup: " + host);
			System.out.println("It may not exist or be unreachable due to a firewall.");
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
