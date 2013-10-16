/*--------------------------------------------------------
BCClient.java 1.1
Back Channel Client
Written by Clark Elliott
with minor modifications & extensive comments by Joseph Sackett

Tests the web server back channel.
This is a standalone program to connect with MyWebServer through a
back channel maintaining a server socket at port 2570.
Requires the Xstream libraries contained in .jar files to compile, AND to run.

To compile: 
- make sure that you download Xstream libraries (jar files).
  See: http://xstream.codehaus.org
- change to directory with source files, execute:
javac -cp "[xstream lib path]\xstream-1.4.5.jar;[xstream lib path]\xpp3_min-1.1.4c.jar;[xstream lib path]\xmlpull-1.1.3.1.jar" BCClient.java

To run:
- open command prompt and change to directory containing BCClient.class
- execute these commands:
set clspath=[xstream lib path]\xstream-1.4.5.jar;[xstream lib path]\xpp3_min-1.1.4c.jar;[xstream lib path]\xmlpull-1.1.3.1.jar
java -cp %clspath% BCClient

----------------------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;  // Get the Input Output libraries
import java.net.Socket; // Get the Java networking libraries

import com.thoughtworks.xstream.XStream;

/**
 * This is responsible for serializing to XML & sending to the back channel of custom mime type conversation.
 */
public class BCClient{
	/** Back channel port number. */
	private static final int BC_PORT = 2570;
	/** File name into which the XML is echoed. */
  	private static String XMLfileName = "C:\\temp\\mimer.output";
  	/** Output file writer for XML echo. */
  	private static PrintWriter      toXmlOutputFile;
  	/** XML file into which the XML is echoed. */
  	private static File             xmlFile;

  	public static void main (String args[]) {
  		// Server name to connect back channel, localhost default
  		String serverName;
  		if (args.length < 1) {
  			serverName = "localhost";
  		}
  		else {
  			serverName = args[0];
  		}
  		
  		// XStream library object for flattening to XML & deserializing back to symbolic form.
  		XStream xstream = new XStream();
  		// Symbolic for objects for flattening & testing deserialization.
  		myDataArray da = new myDataArray();
  		myDataArray daTest = new myDataArray();
    
  		System.out.println("Clark Elliott's back channel Client.\n");
  		System.out.println("Using server: " + serverName + ", Port: 2540 / 2570");
  		// STDIN reader.
  		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
  		try {
  			String userData;
  			do {
  				// Prompt for & read some test input data.
  				System.out.print("Enter a string to send to back channel of webserver, (quit) to end: ");
  				System.out.flush();
  				userData = in.readLine ();
  				if (!"quit".equalsIgnoreCase(userData)) {
  	  				// Fill data object.
  	  				da.lines[0] = "You "; da.lines[1] = "typed "; da.lines[2] = userData;
  	  				da.num_lines = 3;
  	  				// Call XStream library method to flatten data object to XML.
  	  				String xml = xstream.toXML(da);
  					System.out.println("\n\nHere is the XML version:");
  					System.out.print(xml);
	  
  					// Send the XML through the back channel, to the server.
  					sendToBC(xml, serverName);
  					
  					// Call XStream library method to deserialize data from XML, back into symbolic form.
  					daTest = (myDataArray) xstream.fromXML(xml);
  					System.out.println("\n\nHere is the deserialized data: ");
  					for(int i=0; i < daTest.num_lines; i++) {
  						System.out.println(daTest.lines[i]);
  					}
  					System.out.println();

  					// Create File object for XML echo file.
  					xmlFile = new File(XMLfileName);
  					// If file already exists, delete it & check for delete success.
  					if (xmlFile.exists() && !xmlFile.delete()){
  						throw new IOException("XML file delete failed.");
  					}
  					xmlFile = new File(XMLfileName);
  					// Create new XML echo file & check for creation success.
  					if (!xmlFile.createNewFile()){
  						throw (IOException) new IOException("XML file creation failed.");
  					}

  					// Create writer for XML echo file.
  					toXmlOutputFile = new PrintWriter(new BufferedWriter(new FileWriter(XMLfileName)));
  					// Echo XML to file.
  					toXmlOutputFile.println("First arg to BCClient is: " + serverName + "\n");
  					toXmlOutputFile.println(xml);
  					toXmlOutputFile.close();
  				}
  			} while (userData.indexOf("quit") < 0);
  			System.out.println ("Cancelled by user request.");
  		} catch (IOException x) {
  			x.printStackTrace ();
  		}
  		finally {
			if (toXmlOutputFile != null) {
				try {toXmlOutputFile.close();} catch (Exception ex) {}
			}
  		}
  	}

  	/**
  	 * Send the XML data to the back channel.
  	 * @param sendData XML output data.
  	 * @param serverName server to connect back channel.
  	 */
  	static void sendToBC (String sendData, String serverName){
  		Socket sock = null;
  		BufferedReader fromServer = null;
  		PrintStream toServer = null;
  		String textFromServer;
  		try {
  			// Open our connection Back Channel on server:
  			sock = new Socket(serverName, BC_PORT);
  			// Open print writer to server
  			toServer   = new PrintStream(sock.getOutputStream());
  			// Open reader from server.
  			fromServer = new  BufferedReader(new InputStreamReader(sock.getInputStream()));
      
  			// Send the XML data to the server.
  			toServer.println(sendData);
  			toServer.println("end_of_xml");
  			toServer.flush(); 
  			
  			System.out.println("Blocking on acknowledgment from Server... ");
  			// Block until we read a line of response from the server.
  			textFromServer = fromServer.readLine();
  			if (textFromServer != null) {
  				System.out.println(textFromServer);
  			}
  		} catch (IOException x) {
  			System.out.println ("Socket error.");
  			x.printStackTrace ();
  		}
		finally {
			if (fromServer != null) {
				try {fromServer.close();} catch (IOException ex) {}
			}
			if (toServer != null) {
				toServer.close();
			}
			if (sock != null) {
				try {sock.close();} catch (IOException ex) {}
			}
		}
  	}
    
}
