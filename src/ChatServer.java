import javax.net.ssl.*;
import java.security.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-threaded TCP Chat Server
 * Supports direct messages, channels, and file transfers
 */
public class ChatServer {
    private int port;
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Set<String>> channels;
    private Map<ClientHandler, GameSession> activeGames;
    private boolean running;
    private String serverPassword;

    public ChatServer(int port, String serverPassword) {
        this.port = port;
        this.serverPassword = serverPassword;
        this.clients = new ConcurrentHashMap<>();
        this.channels = new ConcurrentHashMap<>();
        this.activeGames = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start() {
        try {
            // Load KeyStore
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream("chat.jks")) {
                ks.load(fis, "password".toCharArray());
            }

            // Initialize KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "password".toCharArray());

            // Initialize SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Create SSLServerSocket
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            serverSocket = ssf.createServerSocket(port);

            running = true;
            System.out.println("[SERVER] Started on port " + port + " (SSL/TLS Enabled)");
            System.out.println("[SERVER] Waiting for connections...");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] New connection from " + clientSocket.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    new Thread(handler).start();

                } catch (IOException e) {
                    if (running) {
                        System.err.println("[SERVER] Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException | GeneralSecurityException e) {
            System.err.println("[SERVER] Failed to start: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        running = false;
        System.out.println("[SERVER] Shutting down...");

        for (ClientHandler client : clients.values()) {
            client.disconnect();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Error closing server socket: " + e.getMessage());
        }

        System.out.println("[SERVER] Shutdown complete");
    }

    public synchronized boolean registerClient(String nickname, ClientHandler handler) {
        if (clients.containsKey(nickname.toLowerCase())) {
            return false;
        }
        clients.put(nickname, handler);
        return true;
    }

    public synchronized void unregisterClient(String nickname) {
        clients.remove(nickname);
    }

    public synchronized boolean isNicknameTaken(String nickname) {
        return clients.containsKey(nickname.toLowerCase());
    }

    public ClientHandler getClient(String nickname) {
        return clients.get(nickname);
    }

    public synchronized void joinChannel(String channel, String nickname) {
        channels.putIfAbsent(channel, ConcurrentHashMap.newKeySet());
        channels.get(channel).add(nickname);
    }

    public synchronized void partChannel(String channel, String nickname) {
        Set<String> members = channels.get(channel);
        if (members != null) {
            members.remove(nickname);
            if (members.isEmpty()) {
                channels.remove(channel);
            }
        }
    }

    public synchronized boolean isInChannel(String channel, String nickname) {
        Set<String> members = channels.get(channel);
        return members != null && members.contains(nickname);
    }

    public void broadcastToChannel(String channel, String message, String exclude) {
        Set<String> members = channels.get(channel);
        if (members != null) {
            for (String nickname : members) {
                if (!nickname.equals(exclude)) {
                    ClientHandler client = clients.get(nickname);
                    if (client != null) {
                        client.send(message);
                    }
                }
            }
        }
    }

    public synchronized void removeFromAllChannels(String nickname) {
        for (Map.Entry<String, Set<String>> entry : channels.entrySet()) {
            String channel = entry.getKey();
            Set<String> members = entry.getValue();
            if (members.contains(nickname)) {
                members.remove(nickname);
                broadcastToChannel(channel, "PART " + channel + " " + nickname + "\n", nickname);
                if (members.isEmpty()) {
                    channels.remove(channel);
                }
            }
        }
    }

    public synchronized List<String> getChannelList() {
        return new ArrayList<>(channels.keySet());
    }

    public synchronized List<String> getUserList(String channel) {
        Set<String> members = channels.get(channel);
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }

    public synchronized List<String> getAllUsers() {
        return new ArrayList<>(clients.keySet());
    }

    public void broadcastQuit(String nickname, String message) {
        String msg = "QUIT " + nickname + " " + message + "\n";
        for (ClientHandler client : clients.values()) {
            if (!client.getNickname().equals(nickname)) {
                client.send(msg);
            }
        }
    }

    public boolean checkPassword(String password) {
        return serverPassword == null || serverPassword.equals(password);
    }

    // --- Game Logic ---
    public void startGame(ClientHandler p1, ClientHandler p2) {
        GameSession game = new GameSession(p1, p2);
        activeGames.put(p1, game);
        activeGames.put(p2, game);

        String p1Ship = game.getNextShipName(p1);
        String p2Ship = game.getNextShipName(p2);

        p1.send("GAME_SETUP You are Player 1. Place your " + p1Ship
                + ". Format: /game place <coord> <H/V> (e.g., A1 H)\n" + game.getRenderedBoard(p1));
        p2.send("GAME_SETUP You are Player 2. Place your " + p2Ship
                + ". Format: /game place <coord> <H/V> (e.g., A1 H)\n" + game.getRenderedBoard(p2));
    }

    public GameSession getGame(ClientHandler player) {
        return activeGames.get(player);
    }

    public void endGame(GameSession game) {
        activeGames.remove(game.getPlayer1());
        activeGames.remove(game.getPlayer2());
    }

    public static void main(String[] args) {
        int port = 6667;
        String password = null;

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

        ChatServer server = new ChatServer(port, password);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVER] Interrupted by user");
            server.shutdown();
        }));

        server.start();
    }
}
