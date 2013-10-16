/*--------------------------------------------------------
BCHandler.java 1.1
Back Channel Handler
Originally by Clark Elliott
Significant modifications & extensive comments by Joseph Sackett

Provides web server back channel accessed via a custom mime type.
This is a standalone program to connect with MyWebServer through a
back channel maintaining a server socket at port 2570.
It is intended to be launched by the browser from a batch file associated with a custom mime type.
Requires the Xstream libraries contained in .jar files to compile, AND to run.

To configure:
- From Windows Explorer, browse to a .xyz file and open it (double click).
- Explorer will prompt you for the type of application to associate with this file extension.
- From the file chooser, browse to shim.bat and check permanently select this application.

To compile: 
- make sure that you download Xstream libraries (jar files).
  See: http://xstream.codehaus.org
- change to directory with source files, execute:
javac -cp "[xstream lib path]\xstream-1.4.5.jar;[xstream lib path]\xpp3_min-1.1.4c.jar;[xstream lib path]\xmlpull-1.1.3.1.jar" BCHandler.java

To run:
- associate a file shim.bat with the custom file extension to be handled.
- shim.bat need only contain these commands:
set clspath=[path to BCHandler.class];[xstream lib path]\xstream-1.4.5.jar;[xstream lib path]\xpp3_min-1.1.4c.jar;[xstream lib path]\xmlpull-1.1.3.1.jar
java -cp %clspath% -Dfirstarg=%1 BCHandler

Included Files:
- mimer-discussion.html 
- Handler.java 
- BCClient.java 
- MyWebServer.java 
- BCHandler.java
- serverlog.txt 
- checklist-mimer.html

----------------------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;  // Get the Input Output libraries
import java.net.Socket; // Get the Java networking libraries
import java.util.Properties;

import com.thoughtworks.xstream.XStream;

/**
 * This is responsible for serializing to XML & sending to the back channel of custom mime type conversation.
 */
public class BCHandler{
	/** Back channel port number. */
	private static final int BC_PORT = 2570;
	/** Line Separator. */
	private static final String LINE_SEP = System.getProperty("line.separator");
	/** File name into which the XML is echoed. */
  	private static String XMLfileName = "C:\\temp\\mimer.output";
  	/** Output file writer for XML echo. */
  	private static PrintWriter toXmlOutputFile;
  	/** XML file into which the XML is echoed. */
  	private static File xmlFile;
  	/** Reader for temporary file containing downloaded MIME data. */
  	private static BufferedReader fromMimeDataFile;

  	public static void main (String args[]) {
  		// Server name to connect back channel, localhost default
  		String serverName;
  		if (args.length < 1) {
  			serverName = "localhost";
  		}
  		else {
  			serverName = args[0];
  		}
  		
		System.out.println("Executing the BCHandler application.");
  		System.out.println("Server name: " + serverName + ", Port: 2540 / 2570");
		
		// Get environment variables.
		Properties props = new Properties(System.getProperties());
		// Get the environment variable containing the temporary filename.
		String fileName = props.getProperty("firstarg");
		System.out.println("File name: " + fileName);
  
  		try {
  			// Create reader for temporary file containing mime data from server.
  			fromMimeDataFile = new BufferedReader(new FileReader(fileName));
  			
  			// Read mime date from temporary file into myDataArray object.
  			myDataArray input = new myDataArray();
  			int i = 0;
  			// Reads the lines from the file (seven at most).
  			while(((input.lines[i++] = fromMimeDataFile.readLine())!= null) && i < 8){
  				System.out.println("Data is: " + input.lines[i-1]);
  			}
  			// Set num_lines to length of buffer.
  			input.num_lines = i - 1;
  			System.out.println("num_lines is: " + input.num_lines);

  			// XStream library object for flattening to XML & deserializing back to symbolic form.
			XStream xstream = new XStream();
			// Call XStream library method to flatten data object to XML.
			String xml = xstream.toXML(input);
			System.out.println("XML output:");
			System.out.println(xml);
  
			// Send the XML through the back channel, to the server.
			sendToBC(xml, serverName);
				
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
			toXmlOutputFile.println(xml);
  		} catch (IOException x) {
  			x.printStackTrace ();
  		}
  		finally {
			if (fromMimeDataFile != null) {
				try {fromMimeDataFile.close();} catch (Exception ex) {}
			}
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
  			toServer.print(sendData);
  			toServer.println(LINE_SEP + "end_of_xml");
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
