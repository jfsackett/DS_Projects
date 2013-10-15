/* Handler.java 1.0
Clark Elliott MIMER shim java example
with minor modifications & extensive comments by Joseph Sackett

Capture Environment Variables passed from .bat file through java.exe.

Assuming the first argument is a valid file name, read five lines
of data from the file, and display the data on the console.
Also create the XML echo file and write some dummy data there.

Note that NO XML is used in this preliminary program, although some
variable names refer to XML for consistency with other programs
in this assignment.

Here is the DOS .bat file to run this Java program:
rem This is shim.bat
rem Have to set classpath in batch, passing as arg does not work:
set classpath=%classpath%;c:/[your execution directory]
rem Pass the name of the first argument to java:
java -Dfirstarg="%1" Handler

To run:

> shim mimer-data.xyz

...where mimer-data.xyz has five lines of ascii data in it.

*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

public class Handler {
	/** File name into which the XML is echoed. */
  	private static String XMLfileName = "C:\\temp\\mimer.output";
  	/** Output file writer for XML echo. */
  	private static PrintWriter      toXmlOutputFile;
  	/** XML file into which the XML is echoed. */
  	private static File             xmlFile;
  	/** Reader for temporary file containing downloaded MIME data. */
  	private static BufferedReader   fromMimeDataFile;

  	public static void main(String args[]) {
  		try {
  			System.out.println("Executing the Handler application.");
  			
  			// Get environment variables.
  			Properties p = new Properties(System.getProperties());
  			// Get the environment variable containing the temporary filename.
  			String argOne = p.getProperty("firstarg");
  			System.out.println("First var is: " + argOne);
      
  			// Create reader for temporary file containing mime data from server.
  			fromMimeDataFile = new BufferedReader(new FileReader(argOne));
  			
  			// Read mime date from temporary file into myDataArray object.
  			myDataArray da = new myDataArray();
  			int i = 0;
  			// Only allows for five lines of data in input file plus safety:
  			while(((da.lines[i++] = fromMimeDataFile.readLine())!= null) && i < 8){
  				System.out.println("Data is: " + da.lines[i-1]);
  			}
  			// Set num_lines to length of buffer.
  			da.num_lines = i-1;
  			System.out.println("num_lines is: " + da.num_lines);

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
			// Echo some dummy data for now. XML will go here later.
			toXmlOutputFile.println("First arg to Handler is: " + argOne + "\n");
			toXmlOutputFile.println("<This where the persistent XML will go>");
  		}
  		catch (Throwable e) {
  			e.printStackTrace();
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
  	
//  	/** Buffer for holding symbolic form of data. */ 
//    private static class myDataArray {
//  	  	int num_lines = 0;
//  	  	String[] lines = new String[8];
//  	}

}
