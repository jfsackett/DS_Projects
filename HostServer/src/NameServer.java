/*--------------------------------------------------------
NameServer.java
version 2.0

Joseph Sackett
November 15, 2013

Developed and tested with JDK 1.7.0_40.

Build Instructions:
- From a command prompt, change to directory with source files, execute:
javac *.java

Standard Execution Instructions:
NameServer coordinates multiple host servers and provides access via the Firefox web browser.
1) From a command prompt in the same directory as the build, execute:
   java NameServer
2) Open Firefox locally and type this into the browser line:
   http://localhost:48050/
3) Expect it to show the name server status page.
4) Start Host Server and Agents.

A browser from a different machine can perform all of the same actions as long as the firewall rules permit it.
To test this, substitute the IP address or hostname of the server running HostServer for the localhost in the above instructions.

Included Files:
- checklist-agent.html
- gradeagent.bat
- HostServer.java 
- NameServer.java
- MimeTypes.txt
- console-log.txt
- DIADiscussion.html

Notes:
- This has much of the important functionality from the assignment complete, but not all. The DIA discussion file covers these gaps. 
- The best way to execute the system is to run: gradeagent.bat

----------------------------------------------------------*/
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class NameServer {
	/** Global mode for the server. Thread safe. */
	static HostServer.ServerState serverState;
	
	/**
	 * Main NameServer Server program.
	 * - Initializes global state
	 * - Loop continually, spawning workers for each connection.
	 */
	public static void main(String[] args) {
		System.out.println("Joe Sackett's DIA Name Server.");
		
		String nameServerHost = "localhost";
		int nameServerPort = HostServer.DEFAULT_NAME_SERVER_PORT;		
		System.out.println("Name Server: " + nameServerHost + ':' + nameServerPort);
		
		// Initialize server state.
		serverState = new HostServer.ServerState();
		
		// Start Name Server listener thread.
		new Thread(new HostServer.Server(nameServerPort, new NameServerStrategy())).start();
		
		// Loop until interrupted to end.
		while (serverState.isControlSwitch()) {
			try {
				Thread.sleep(HostServer.TIMEOUT);
			}
			catch (InterruptedException ex) {}
		}
		
		System.out.println("Name Server exiting.");
	}	

	/**
	 * Implementation for processing Name Server client requests.
	 * Part of Strategy pattern.
	 */
	private static class NameServerStrategy extends HostServer.AbstractServerStrategy {
		/** List of host servers (server:port). */
		private List<String> hostServers = new ArrayList<String>();		
		
		/** Map of agent names to agent servers (server:port). */
		private Map<String,String> agentServers = new HashMap<String,String>();		
		
		public NameServerStrategy() {
		}

		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Name Server";
		}
		
		/**
		 * Process request string & delegate to handler functions.
		 */
		@Override
		protected void handleRequest(List<String> fullRequest, PrintStream writer, HostServer.Server server) throws IOException {
			// Parse & validate request.
			String request = fullRequest.get(0);
	    	StringTokenizer toker = new StringTokenizer(request, " ");
	    	List<String> tokens = new ArrayList<String>(); 
	    	while (toker.hasMoreTokens()) {
	    		tokens.add(toker.nextToken());
	    	}
	    	String command = tokens.get(0);
	    	if (!(command.equalsIgnoreCase(HostServer.GET) || command.startsWith(HostServer.REGISTER_HOST_SERVER) || command.startsWith(HostServer.QUERY_HOST_SERVERS) 
	    			|| command.startsWith(HostServer.REGISTER_NEW_AGENT) || command.startsWith(HostServer.MIGRATE)) || (tokens.size() > 1 && tokens.get(1).contains(HostServer.FAV_ICON))) {
	    		writeError(HostServer.BAD_REQUEST, "Invalid request for this server: " + request, writer);
	    		return;
	    	}
	    	
	    	// Handle a HostServer.GET by listing all host servers & agents.
	    	if (command.equalsIgnoreCase(HostServer.GET)) {
	    		handleGetRequest(writer);
	    	}
	    	else {
	    		handleAdminRequest(command, request, writer);
	    	}	    	
	    }
		
		private void handleGetRequest(PrintStream writer) throws IOException {
			StringBuilder responseBuilder = new StringBuilder();
			responseBuilder.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">").append(HostServer.CRLF);
			responseBuilder.append("<html><head>").append(HostServer.CRLF);
			responseBuilder.append("<title>").append("Name Server Manifest").append("</title>").append(HostServer.CRLF);
			responseBuilder.append("</head><body>").append(HostServer.CRLF);
			responseBuilder.append("<h1>").append("Name Server Manifest").append("</h1>").append(HostServer.CRLF);
			responseBuilder.append("<h2>").append("Host Servers").append("</h2>").append(HostServer.CRLF);
			responseBuilder.append("<pre>").append(HostServer.CRLF);
			for (String hostServer : hostServers) {
				responseBuilder.append("<a href=\"http://").append(hostServer).append("/\">");
				responseBuilder.append(hostServer).append("</a>").append(HostServer.CRLF);
			}
			responseBuilder.append("</pre>").append(HostServer.CRLF);
			responseBuilder.append("<h2>").append("Agents").append("</h2>").append(HostServer.CRLF);
			responseBuilder.append("<pre>").append(HostServer.CRLF);
			for (String agentName : agentServers.keySet()) {
				responseBuilder.append("<a href=\"http://").append(agentServers.get(agentName)).append("/\">");
				responseBuilder.append(agentName).append("</a>").append(HostServer.CRLF);
			}
			responseBuilder.append("</pre>").append(HostServer.CRLF);
			responseBuilder.append("</body></html>").append(HostServer.CRLF);			
			String response = responseBuilder.toString();
			writeOkHeader(response.length(), HostServer.mimeTypes.get("html"), writer);
			writer.print(response);
			writer.print(HostServer.CRLF);
			writer.flush();
		}
		
		private void handleAdminRequest(String command, String request, PrintStream writer) throws IOException {
			// Parse input parameters.
			Map<String,String> paramMap = HostServer.parseParams(command);
			
	    	if (command.startsWith(HostServer.REGISTER_HOST_SERVER)) {
	    		// Get host server endpoint from request parameters.
	    		String hostServer = paramMap.get(HostServer.SERVER);
	    		if (hostServer == null) {
		    		writeError(HostServer.BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
	    		}	    			
    			hostServers.add(hostServer);
    			System.out.println("Register host server: " + hostServer);
    			
    			// Success response.
    			writer.print(HostServer.SUCCESS);
	    	}
	    	else if (command.startsWith(HostServer.QUERY_HOST_SERVERS)) {
	    		// Respond with &-delimited list of host servers.
	    		String hostServersResponse = new String();
				for (String hostServer : hostServers) {
					if (hostServersResponse.length() != 0) {
						hostServersResponse += '&';
					}
					hostServersResponse += hostServer;
				}
				
    			// Host Servers response.
    			writer.print(hostServersResponse);
	    	}
	    	else if (command.startsWith(HostServer.REGISTER_NEW_AGENT)) {
	    		// Get agent server endpoint from request parameters.
	    		String agentServer = paramMap.get(HostServer.SERVER);
	    		// Find a peer if one exists, else use itself.
	    		String peerAgentServer = (agentServers.isEmpty()) ? agentServer : agentServers.values().iterator().next();
	    		if (agentServer == null) {
		    		writeError(HostServer.BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
	    		}
    			String agentName = getUniqueName();
    			agentServers.put(agentName, agentServer);
    			System.out.println("Register agent: " + agentName + " at server: " + agentServer);
    			
    			// Name response.
    			writer.print(agentName + '&' + peerAgentServer);
	    	}
	    	else if (command.startsWith(HostServer.MIGRATE)) {
	    		// Get agent name from request parameters.
	    		String agentName = paramMap.get(HostServer.NAME);
	    		// Get new agent server endpoint from request parameters.
	    		String agentServer = paramMap.get(HostServer.SERVER);
	    		if (agentName == null || agentServer == null) {
		    		writeError(HostServer.BAD_REQUEST, "Invalid request for this server: " + request, writer);
		    		return;
	    		}	    			
    			agentServers.put(agentName, agentServer);
    			System.out.println("Migrate agent: " + agentName + " to server: " + agentServer);
    			
    			// Success response.
    			writer.print(HostServer.SUCCESS);
	    	}
	    	
	    	// Complete response.
			writer.print(HostServer.CRLF);
			writer.flush();
		}
		
		private static String getUniqueName() {
			return "" + Math.random();
		}
	}
	
}