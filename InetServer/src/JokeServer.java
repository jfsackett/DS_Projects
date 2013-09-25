import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This multithreaded server listens for processes connections from clients.
 * It handles two types of clients, joke clients and admin clients (which connect on different ports).
 * It sends jokes & proverbs to joke clients and allows admin clients to adjust its state.  
 * @author Joseph Sackett
 */
public class JokeServer {
	/** Joke Port to bind. */
	private static final int JOKE_PORT = 4121;
	
	/** Admin Port to bind. */
	private static final int ADMIN_PORT = 4122;
	
	/** Server thread timeout. */
	private static final int TIMEOUT = 2000;
	
	/** Joke input file. */
	private static final String JOKE_INPUT_FILE = "JokeInput.txt";
	
	/** Joke input file. */
	private static final String USERS_STATE_FILE = "UsersState.ser";
	
	/** Joke request protocol. */
	private static final String JOKE_REQUEST = "Give me something";
	
	/** Joke mode protocol. */
	private static final String JOKE_MODE = "joke-mode";
	
	/** Proverb mode protocol. */
	private static final String PROVERB_MODE = "proverb-mode";

	/** Maintenance mode protocol. */
	private static final String MAINTENANCE_MODE = "maintenance-mode";

	/** Shutdown protocol. */
	private static final String SHUTDOWN = "shutdown";

	/** Joke Server Strategy Singleton. */
	private static final ServerStrategy JOKE_SERVER_STRATEGY = new JokeServerStrategy();
	
	/** Administration Server Strategy Singleton. */
	private static final ServerStrategy ADMIN_SERVER_STRATEGY = new AdminServerStrategy();
	
	/** Name replacement token in jokes & proverbs. */
	private static final String NAME_TOKEN = "Xname";
	
	/** Server modes. */
	private static enum ServerMode {
		JOKE, PROVERB, MAINTENANCE;
	}
	
	/** Main shared repository of jokes & proverbs. Thread safe; read-only. */
	private static OutputRepository outputRepository;
	
	/** Global mode for the server. Thread safe. */
	private static ServerState serverState;
	
	/** Persistent record of users' joke/proverb states. Thread safe. */
	private static PersistentUsersState usersState;
	
	// Transient record of user joke/proverb states. Thread safe; uses Hashtable implementation. */
	///private static Map<String,Joker> userStates;
	
	/**
	 * Main Joke Server program.
	 * - Initializes global state
	 * - Spawn Joke listener.
	 * - Spawn Admin listener.
	 * - Loop until shutdown (sleeping often). 
	 */
	public static void main(String[] args) {
		System.out.println("Joe Sackett's Joke Server.");
		System.out.println("Joke port: " + JOKE_PORT);
		System.out.println("Admin port: " + ADMIN_PORT);
		
		// Load global jokes & proverbs.
		loadJokeInputFile();
		
		// Initialize server state.
		serverState = new ServerState();
		
		// Initialize user state map.
//		userStates = new Hashtable<String,Joker>();
		
		usersState = new PersistentUsersState(USERS_STATE_FILE);
		
		// Start Joke Server listener thread.
		new Thread(new Server(JOKE_PORT, JOKE_SERVER_STRATEGY)).start();
		
		// Start Admin Server listener thread.
		new Thread(new Server(ADMIN_PORT, ADMIN_SERVER_STRATEGY)).start();
		
		while (serverState.isControlSwitch()) {
			try {
				Thread.sleep(TIMEOUT);
			}
			catch (InterruptedException ex) {}
		}
		
		shutdownListeners();
	}
	
	/**
	 * Send dummy requests to listeners to unblock them for shutdown.
	 */
	private static void shutdownListeners() {
		Socket socket = null;
		ObjectOutputStream oWriter = null;
		PrintStream pWriter = null;
		try {
			socket = new Socket("localhost", JOKE_PORT);
			oWriter = new ObjectOutputStream(socket.getOutputStream());  
			// Send dummy input to Joke listener to unblock it for shutdown.
			oWriter.writeObject(new String[]{"","",""});
			oWriter.flush();
			oWriter.close();
			socket.close();
			oWriter = null;
			socket = null;
			
			socket = new Socket("localhost", ADMIN_PORT);
			pWriter = new PrintStream(socket.getOutputStream());
			// Send dummy input to Admin listener to unblock it for shutdown.
			pWriter.println("");
			pWriter.flush();
		} catch (IOException ex) {
			System.out.println("Socket error.");
			System.out.println(ex);
		}
		finally {
			if (oWriter != null) {
				try {oWriter.close();} catch (IOException ex) {}
			}
			if (pWriter != null) {
				pWriter.close();
			}
			if (socket != null) {
				try {socket.close();} catch (IOException ex) {}
			}
		}
	}
	
	/**
	 * Generic server used for spawning both jokes & admin workers.
	 * Behavior parameterized with different strategies.
	 */
	private static class Server implements Runnable {
		/** Port bound to by this server. */
		int portNum;
		
		/** Strategy executed by workers processing connections to this server (joke or admin). */
		ServerStrategy serverStrategy;
		
		public Server(int portNum, ServerStrategy serverStrategy) {
			this.portNum = portNum;
			this.serverStrategy = serverStrategy;
		}

		@Override
		public void run() {
			System.out.println("Starting " + serverStrategy.getTypeName() + " listener on port: " + portNum);
			ServerSocket serverSocket = null;
			try {
				serverSocket = new ServerSocket(portNum);
				while (serverState.isControlSwitch()) {
					// Wait for the next client connection.
					Socket socket = serverSocket.accept();
					// Check for shutdown preceding client connection.
					if (serverState.isControlSwitch()) {
						// Spawn thread, along with Joke or Admin strategy.
						new Thread(new Worker(socket, serverStrategy)).start();
					}
				}
			}
			catch (IOException ex) {
				System.out.println(ex);
			}
			finally {
				if (serverSocket != null) {
					try { serverSocket.close(); } catch (IOException ex) {}
				}
			}
			System.out.println(serverStrategy.getTypeName() + " listener exiting.");
		}
	}

	/**
	 * Generic worker used for both jokes & admin.
	 * Behavior parameterized with different strategies.
	 */
	private static class Worker implements Runnable {
		/** Socket connected to the client whom this worker will process. */
		Socket socket;
		
		/** Strategy executed by workers processing connections to this server (joke or admin). */
		ServerStrategy serverStrategy;
		
		public Worker(Socket socket, ServerStrategy serverStrategy) {
			this.socket = socket;
			this.serverStrategy = serverStrategy;
		}

		/**
		 * Method to execute when thread is spawned.
		 */
		@Override
		public void run() {
			System.out.println("Spawning " + serverStrategy.getTypeName() + " worker to process request.");
			serverStrategy.processRequest(socket);
		}
	}

	/**
	 * Interface defining the method for processing client requests.
	 * Implemented by subclasses. Part of Strategy pattern.
	 */
	private static interface ServerStrategy {
		/** Echo type name. */
		public String getTypeName();
		
		/** Processes a client request. */
		public void processRequest(Socket socket);
	}
	
	/**
	 * Implementation for processing JokeServer client requests.
	 * Part of Strategy pattern.
	 */
	private static class JokeServerStrategy implements ServerStrategy {
		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Joke Server";
		}
		
		/** Processes a client request specific for the joke strategy. */
		@Override
		public void processRequest(Socket socket) {
			PrintStream writer = null;
			ObjectInputStream reader = null;
			String loginId = null;
			Joker joker = null;
			try {
				// Get I/O streams from the socket.
				writer = new PrintStream(socket.getOutputStream());
				reader = new ObjectInputStream(socket.getInputStream());
				
				// Read input from client via socket.
				String[] input = (String[])reader.readObject();  
				if (input == null || input.length != 3 || !JOKE_REQUEST.equalsIgnoreCase(input[0])) {
					System.out.println("Input Error.");
					writer.println("Input Error.");
					return;
				}  
				System.out.println(input[0] + " : " + input[1] + " : " + input[2]);

				loginId = input[1];
				String userName = input[2];
				
				// Check whether server in maintenance mode.
				if (serverState.getServerMode() == ServerMode.MAINTENANCE) {
					// Write warning back to client.
					writer.println(outputRepository.getWarning());
				}
				else {
					// Retrieve persistent state for this user.
					joker = usersState.loadJoker(loginId);
					
					// Which mode are we in?
					if (serverState.getServerMode() == ServerMode.JOKE) {
						// Write joke back to user.
						writer.println(joker.getJoke(outputRepository, userName));
					}
					else {
						// Write proverb back to user.
						writer.println(joker.getProverb(outputRepository, userName));
					}
				}
			} 
			catch (IOException ex) {
				System.out.println(ex);
			}
			catch (ClassNotFoundException ex) {
				System.out.println(ex);
			}
			finally {
				// Persist joker state.
				if (loginId != null && joker != null) {
					usersState.saveJoker(loginId, joker);
				}
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
	
	/**
	 * Implementation for processing AdminServer client requests.
	 * Part of Strategy pattern.
	 */
	private static class AdminServerStrategy implements ServerStrategy {
		/** Echo type name specific for this strategy. */
		@Override
		public String getTypeName() {
			return "Admin Server";
		}
		
		/** Processes a client request specific for the admin strategy. */
		@Override
		public void processRequest(Socket socket) {
			BufferedReader reader =  null;
			PrintStream writer = null;
			try {
				// Get I/O streams from the socket.
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintStream(socket.getOutputStream());

				// Read input from client via socket.
				String input = reader.readLine();
				if (JOKE_MODE.equalsIgnoreCase(input)) {
					System.out.println("Switching to Joke mode.");
					writer.println("Server switching to Joke mode.");
					serverState.setServerMode(ServerMode.JOKE);
				}
				else if (PROVERB_MODE.equalsIgnoreCase(input)) {
					System.out.println("Switching to Proverb mode.");
					writer.println("Server switching to Proverb mode.");
					serverState.setServerMode(ServerMode.PROVERB);
				}
				else if (MAINTENANCE_MODE.equalsIgnoreCase(input)) {
					System.out.println("Switching to Maintenance mode.");
					writer.println("Server switching to Maintenance mode.");
					serverState.setServerMode(ServerMode.MAINTENANCE);
				}
				else if (SHUTDOWN.equalsIgnoreCase(input)) {
					serverState.setControlSwitch(false);
					System.out.println("Shutting Down....");
					writer.println("Server Shutdown in progress.");
				}
			} catch (IOException ex) {
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
	
	/**
	 * This loads the JokeInput.txt file containing all of the jokes & proverbs; allowing configuration of the application.
	 * If the input file does not exist or the file data are invalid, it uses default data so it can still proceed. 
	 */
	private static void loadJokeInputFile() {
		String warning = null;
		Integer numBoth = null;
		int numJokes = Integer.MAX_VALUE;
		String[] jokes = null;
		int numProverbs = Integer.MAX_VALUE;
		String[] proverbs = null;
        String input = null;
 		BufferedReader reader = null;
		try {
			// Open input file.
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(JOKE_INPUT_FILE)));
			// Read each line through the end of the file.
			while ((input = reader.readLine()) != null) {
				// Skip commented lines.
				if (input == null || input.length() == 0 || input.startsWith("#")) {
					continue;
				}
				
				// Load the warning message first.
				if (warning == null) {
					warning = input;
					continue;
				}
			
				// Next load the number of jokes & proverbs.
				if (numBoth == null) {
					numBoth = Integer.parseInt(input);
					jokes = new String[numBoth];
					proverbs = new String[numBoth];
					// Load jokes next.
					numJokes = 0;
					continue;
				}
				
				// Next load jokes until full.
				if (numBoth != null && numJokes < numBoth) {
					jokes[numJokes] = input;
					if (++numJokes == numBoth) {
						// Done loading jokes, load proverbs.
						numProverbs = 0;
					}
					continue;
				}
				
				// Finally load proverbs until full.
				if (numBoth != null && numProverbs < numBoth) {
					proverbs[numProverbs++] = input;
					continue;
				}				
			}	
		} catch (IOException ex) {
			System.out.println(ex);
		}
		finally {
			if (reader != null) {
				try {reader.close();} catch (Exception ex) {}
			}
		}

		// Make sure input file was loaded and contained valid input.
		if (warning != null && warning.length() > 0 && jokes != null && proverbs != null 
				&& jokes.length >= 5 && proverbs.length == jokes.length) {
			outputRepository = new OutputRepository(warning, jokes, proverbs);
		}
		else {
			// Use default data.
			System.out.println("Error loading joke input, using default data.");
			outputRepository = new OutputRepository();
		}
		
		return;
	}
	
	/**
	 * This is the main repository of jokes & proverbs for output to the clients.
	 */
	private static class OutputRepository {
		/** Warning message. */
		private String warning;
		
		/** Joke output. */
		private String[] jokes;
		
		/** Proverb Output. */
		private String[] proverbs;

		/** Provides default for use when jokes & proverbs not received as input. */
		public OutputRepository() {
			warning = "Default Warning";
			jokes = new String[]{"A. Default Joke 1", "B. Default Joke 2", "C. Default Joke 3", "D. Default Joke 4", "E. Default Joke 5"};
			proverbs = new String[]{"A. Default Proverb 1", "B. Default Proverb 2", "C. Default Proverb 3", "D. Default Proverb 4", "E. Default Proverb 5"};
		}

		public OutputRepository(String warning, String[] jokes, String[] proverbs) {
			this.warning = warning;
			this.jokes = jokes;
			this.proverbs = proverbs;
		}

		public synchronized String getWarning() {
			return warning;
		}

		public synchronized String[] getJokes() {
			return jokes;
		}

		public synchronized String[] getProverbs() {
			return proverbs;
		}
	}
	
	private static class ServerState {
		/** Main control switch used for shutdown. */
		private boolean controlSwitch = true;
		
		/** Global mode for the server. */
		private ServerMode serverMode = ServerMode.JOKE;

		public synchronized boolean isControlSwitch() {
			return controlSwitch;
		}

		public synchronized void setControlSwitch(boolean controlSwitch) {
			this.controlSwitch = controlSwitch;
		}

		public synchronized ServerMode getServerMode() {
			return serverMode;
		}

		public synchronized void setServerMode(ServerMode serverMode) {
			this.serverMode = serverMode;
		}
	}
	
	/** Provides persistent record of users' joke/proverb states using serialized Hashtable. Thread safe. */
	private static class PersistentUsersState {
		/** Users' state filename. */
		private String usersStateFilename;
		
		public PersistentUsersState(String usersStateFilename) {
			this.usersStateFilename = usersStateFilename;
			// Check whether serialized Hashtable exists.
			File usersStateFile = new File(usersStateFilename);
			if(!usersStateFile.exists()) {
				// If not, create empty one.
				writeSerializedHashtable(usersStateFilename, new Hashtable<String,Joker>());
			}
		}
		
		public synchronized Joker loadJoker(String loginId) {
			Hashtable<String,Joker> userStates = readSerializedHashtable(usersStateFilename);
			Joker joker = userStates.get(loginId);
			if (joker == null) {
				// No, create new state & store it.
				joker = new Joker(outputRepository);
				userStates.put(loginId, joker);
			}
			
			return joker;
		}
		
		public synchronized void saveJoker(String loginId, Joker joker) {
			Hashtable<String,Joker> userStates = readSerializedHashtable(usersStateFilename);
			userStates.put(loginId, joker);
			writeSerializedHashtable(usersStateFilename, userStates);
		}
		
		private Hashtable<String,Joker> readSerializedHashtable(String fileName) {
			Hashtable<String,Joker> userStates = null;
			ObjectInput reader = null;
			boolean corruptSerializedFile = false;
			try {
				reader = new ObjectInputStream(new BufferedInputStream(new FileInputStream(fileName)));
				userStates = (Hashtable<String,Joker>)reader.readObject();
		    }  
		    catch(IOException ex) {
				System.out.println(ex);
				corruptSerializedFile = true;
		    }
		    catch(ClassNotFoundException ex) {
				System.out.println(ex);
				corruptSerializedFile = true;
		    }
			finally {
				if (reader != null) {
					try {reader.close();} catch (Exception ex) {}
				}
			}
			
			// Is serialized file corrept?
			if (corruptSerializedFile) {
				// Reset to empty copy.
				userStates = new Hashtable<String,Joker>();
				// Rewrite empty copy.
				writeSerializedHashtable(fileName, userStates);
			}
			
			return userStates;
		}
		
		private void writeSerializedHashtable(String fileName, Hashtable<String,Joker> userStates) {
			ObjectOutput writer = null;
			try {
				writer = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
				writer.writeObject(userStates);
		    }  
		    catch(IOException ex) {
				System.out.println(ex);
		    }
			finally {
				if (writer != null) {
					try {writer.close();} catch (Exception ex) {}
				}
			}
		}		
	}
	
	/**
	 * This provides an unseen joke or proverb for a given user.
	 * It records the state of the jokes for a user.
	 */
	private static class Joker implements Serializable {
		/** Serialization UID. */
		private static final long serialVersionUID = 4932276364995617348L;

		/** Jokes seen. */
		private boolean[] jokesSeen;
		
		/** Proverbs seen. */
		private boolean[] proverbsSeen;
		
		public Joker(OutputRepository outputRepository) {
			// Initialize the flags for retaining joke state.
			jokesSeen = new boolean[outputRepository.getJokes().length];
			proverbsSeen = new boolean[outputRepository.getProverbs().length];
		}

		/** Returns a random unseen joke. Note this must be synchronized for thread safety. */
		public synchronized String getJoke(OutputRepository outputRepository, String userName) {
			return replaceNameTokens(getEither(jokesSeen, outputRepository.getJokes()), userName);
		}
		
		/** Returns a random unseen proverb. Note this must be synchronized for thread safety. */
		public synchronized String getProverb(OutputRepository outputRepository, String userName) {
			return replaceNameTokens(getEither(proverbsSeen, outputRepository.getProverbs()), userName);
		}
		
		/** Shared utility for finding & returning random joke or proverb. */
		private static String getEither(boolean[] seenFlags, String[] content) {
			// Count the # unseen jokes.
			int count = 0;
			for (int ix = 0; ix < seenFlags.length; ix++) {
				if (!seenFlags[ix]) {
					count++;
				}
			}
			
			// Reset flags & count if all set.
			if (count == 0) {
				for (int ix = 0; ix < seenFlags.length; ix++) {
					seenFlags[ix] = false;
				}
				count = seenFlags.length;
			}
			
			// Get a random number between 0 & # unseen jokes.
			int randomNum = (int)(Math.random() * count);
			// Determine the index of chosen unseen joke.
			for (int ix = 0; ix < seenFlags.length; ix++) {
				if (!seenFlags[ix]) {
					if (randomNum-- == 0) {
						// Mark as seen.
						seenFlags[ix] = true;
						// Return content.
						return content[ix];
					}
				}
			}
			
			return "Houston, we have a problem.";
		}
		
		/**
		 * Replaces name in input text.
		 * Regular expression syntax inspired by:
		 * http://stackoverflow.com/questions/959731/how-to-replace-a-set-of-tokens-in-a-java-string
		 * @param text text to have token replaced.
		 * @param userName name to be placed into text.
		 * @return text with name substituted for token.
		 */
		private static final String replaceNameTokens(String text, String userName) {
			Pattern pattern = Pattern.compile(NAME_TOKEN);
			Matcher matcher = pattern.matcher(text);
			StringBuffer buffer = new StringBuffer();
			while (matcher.find()) {
				matcher.appendReplacement(buffer, "");
				buffer.append(userName);
			}
			matcher.appendTail(buffer);
			return buffer.toString();
		}
	}
}
