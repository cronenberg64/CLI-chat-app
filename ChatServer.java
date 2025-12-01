import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-threaded TCP Chat Server
 * Supports direct messages, channels, and file transfers
 */
public class ChatServer {
    private static final int DISCOVERY_PORT = 6666;
    private static final String DISCOVERY_MESSAGE = "CHAT_SERVER";

    private int port;
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> clients;
    private Map<String, Set<String>> channels;
    private boolean running;
    private Thread discoveryThread;

    public ChatServer(int port) {
        this.port = port;
        this.clients = new ConcurrentHashMap<>();
        this.channels = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("[SERVER] Started on port " + port);

            // Start discovery broadcast thread
            startDiscoveryBroadcast();
            System.out.println("[SERVER] Discovery broadcast enabled on port " + DISCOVERY_PORT);
            System.out.println("[SERVER] Waiting for connections...");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] New connection from " + clientSocket.getRemoteSocketAddress());

                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    Thread thread = new Thread(handler);
                    thread.start();

                } catch (IOException e) {
                    if (running) {
                        System.err.println("[SERVER] Error accepting connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[SERVER] Failed to start: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void startDiscoveryBroadcast() {
        discoveryThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                // Get local IP address
                String localIP = InetAddress.getLocalHost().getHostAddress();
                String message = DISCOVERY_MESSAGE + ":" + localIP + ":" + port;
                byte[] buffer = message.getBytes();

                // Broadcast address for local network
                InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                                                          broadcastAddress, DISCOVERY_PORT);

                System.out.println("[DISCOVERY] Broadcasting on " + localIP + ":" + port);

                while (running) {
                    try {
                        socket.send(packet);
                        Thread.sleep(3000); // Broadcast every 3 seconds
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("[DISCOVERY] Error: " + e.getMessage());
            }
        });
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    public void shutdown() {
        running = false;
        System.out.println("[SERVER] Shutting down...");

        // Stop discovery broadcast
        if (discoveryThread != null) {
            discoveryThread.interrupt();
        }

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

    public static void main(String[] args) {
        int port = 6667;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[0]);
                System.exit(1);
            }
        }

        ChatServer server = new ChatServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVER] Interrupted by user");
            server.shutdown();
        }));

        server.start();
    }
}
