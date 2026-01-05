import javax.net.ssl.*;
import java.security.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    /**
     * Multi-threaded TCP Chat Server
     * This is the heart of our application! It listens for connections,
     * manages clients, handles channels, and even runs the Battleship game.
     */

    private int port;
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Set<String>> channels;
    private Map<ClientHandler, GameSession> activeGames;
    private boolean running;
    private String serverPassword;

    public ChatServer(int port, String serverPassword) {
        // constructor to initialize variables
        this.port = port;
        this.serverPassword = serverPassword;
        this.clients = new ConcurrentHashMap<>();
        this.channels = new ConcurrentHashMap<>();
        this.activeGames = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start() {
        // start method to start the server
        try {
            // setup the SSL context
            // load the keystore file
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream("chat.jks")) {
                ks.load(fis, "password".toCharArray());
            }

            // initialize the KeyManagerFactory.
            // this factory manages our keys and certificates.
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "password".toCharArray());

            // initialize the SSLContext.
            // this is the environment where the secure connection happens.
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // create the SSLServerSocket.
            // instead of a regular ServerSocket, we use an SSL one to encrypt traffic.
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            serverSocket = ssf.createServerSocket(port);

            running = true; // set the server to running
            System.out.println("[SERVER] Started on port " + port + " (SSL/TLS Enabled)");
            System.out.println("[SERVER] Waiting for connections...");

            // the main loop, keep accepting new connections until told to stop
            while (running) {
                try {
                    // accept() blocks (waits) until a client connects.
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] New connection from " + clientSocket.getRemoteSocketAddress());

                    // create a new handler for this specific client.
                    // this handler will run in its own thread so it doesn't block other clients.
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    new Thread(handler).start();

                } catch (IOException e) {
                    if (running) {
                        // only print error if we didn't intentionally stop the server
                        System.err.println("[SERVER] Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException | GeneralSecurityException e) {
            // if something goes wrong during startup (like missing keystore), we crash
            // gracefully.
            System.err.println("[SERVER] Failed to start: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    // shutdown method to stop the server
    public void shutdown() {
        running = false; // stop the loop
        System.out.println("[SERVER] Shutting down...");

        // disconnect all currently connected clients politely
        for (ClientHandler client : clients.values()) {
            client.disconnect();
        }

        // close the main server socket so no new connections can come in
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Error closing server socket: " + e.getMessage());
        }

        System.out.println("[SERVER] Shutdown complete");
    }

    // register a new client with a nickname
    public synchronized boolean registerClient(String nickname, ClientHandler handler) {
        // check if the name is already taken (case-insensitive)
        if (clients.containsKey(nickname.toLowerCase())) {
            return false; // nickname is already taken
        }
        clients.put(nickname, handler); // nickname is available
        return true;
    }

    // unregister client
    public synchronized void unregisterClient(String nickname) {
        clients.remove(nickname);
    }

    // check if a nickname is already in use
    public synchronized boolean isNicknameTaken(String nickname) {
        return clients.containsKey(nickname.toLowerCase());
    }

    // get the handler for a specific user, used for dms
    public ClientHandler getClient(String nickname) {
        return clients.get(nickname);
    }

    // add a user to a channel
    public synchronized void joinChannel(String channel, String nickname) {
        // if the channel doesn't exist, create it
        channels.putIfAbsent(channel, ConcurrentHashMap.newKeySet());
        channels.get(channel).add(nickname);
    }

    // remove a user from a channel
    public synchronized void partChannel(String channel, String nickname) {
        Set<String> members = channels.get(channel);
        if (members != null) {
            members.remove(nickname);
            // if channel is empty, we delete it to save memory.
            if (members.isEmpty()) {
                channels.remove(channel);
            }
        }
    }

    // check if a user is in a specific channel.
    public synchronized boolean isInChannel(String channel, String nickname) {
        Set<String> members = channels.get(channel);
        return members != null && members.contains(nickname);
    }

    // send a message to everyone in a channel
    public void broadcastToChannel(String channel, String message, String exclude) {
        Set<String> members = channels.get(channel);
        if (members != null) {
            for (String nickname : members) {
                // don't send the message back to the person who sent it
                if (!nickname.equals(exclude)) {
                    ClientHandler client = clients.get(nickname);
                    if (client != null) {
                        client.send(message);
                    }
                }
            }
        }
    }

    // when a user disconnects, remove them from all channels they were in
    public synchronized void removeFromAllChannels(String nickname) {
        for (Map.Entry<String, Set<String>> entry : channels.entrySet()) {
            String channel = entry.getKey();
            Set<String> members = entry.getValue();
            if (members.contains(nickname)) {
                members.remove(nickname);
                // broadcast user left
                broadcastToChannel(channel, "PART " + channel + " " + nickname + "\n", nickname);
                // clean up empty channels
                if (members.isEmpty()) {
                    channels.remove(channel);
                }
            }
        }
    }

    // function to get a list of all active channels
    public synchronized List<String> getChannelList() {
        return new ArrayList<>(channels.keySet());
    }

    // function to get a list of users in a specific channel
    public synchronized List<String> getUserList(String channel) {
        Set<String> members = channels.get(channel);
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }

    // function to get a list of all users on the server.
    public synchronized List<String> getAllUsers() {
        return new ArrayList<>(clients.keySet());
    }

    // function to broadcast quit message to everyone on the server
    public void broadcastQuit(String nickname, String message) {
        String msg = "QUIT " + nickname + " " + message + "\n";
        for (ClientHandler client : clients.values()) {
            // don't send to the person who is quitting since they are already leaving
            if (!client.getNickname().equals(nickname)) {
                client.send(msg);
            }
        }
    }

    // function to check if the provided server password is correct
    public boolean checkPassword(String password) {
        // if no password is set on the server, anyone can join!
        return serverPassword == null || serverPassword.equals(password);
    }

    // function to start a new game between two players
    public void startGame(ClientHandler p1, ClientHandler p2) {
        GameSession game = new GameSession(p1, p2);
        activeGames.put(p1, game);
        activeGames.put(p2, game);

        // get the first ship they need to place
        String p1Ship = game.getNextShipName(p1);
        String p2Ship = game.getNextShipName(p2);

        // send instructions to both players
        p1.send("GAME_SETUP You are Player 1. Place your " + p1Ship
                + ". Format: /game place <coord> <H/V> (e.g., A1 H)\n" + game.getRenderedBoard(p1));
        p2.send("GAME_SETUP You are Player 2. Place your " + p2Ship
                + ". Format: /game place <coord> <H/V> (e.g., A1 H)\n" + game.getRenderedBoard(p2));
    }

    // function to get game session for a specific player
    public GameSession getGame(ClientHandler player) {
        return activeGames.get(player);
    }

    // function to end game and clean up
    public void endGame(GameSession game) {
        activeGames.remove(game.getPlayer1());
        activeGames.remove(game.getPlayer2());
    }

    public static void main(String[] args) {
        int port = 6667; // default IRC port
        String password = null;

        // parse command line arguments
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0]);
                System.exit(1);
            }
        }

        if (args.length > 1) {
            password = args[1];
        }

        // create the server instance
        ChatServer server = new ChatServer(port, password);

        // add a shutdown hook to handle Ctrl+C gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVER] Interrupted by user");
            server.shutdown();
        }));

        server.start();
    }
}
