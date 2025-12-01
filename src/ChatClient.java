import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Command-line Chat Client
 * Supports direct messages, channels, and file transfers
 */
public class ChatClient {
<<<<<<< HEAD
    private static final int DISCOVERY_PORT = 6666;
    private static final int DISCOVERY_TIMEOUT = 5000; // 5 seconds

=======
>>>>>>> 4de9990 (Add files via upload)
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private BufferedReader consoleReader;
    private boolean running;
    private String nickname;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.running = false;
    }

<<<<<<< HEAD
    private boolean discoverServer() {
        System.out.println("[DISCOVERY] Searching for chat servers on local network...");

        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
            socket.setSoTimeout(DISCOVERY_TIMEOUT);

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                if (message.startsWith("CHAT_SERVER:")) {
                    String[] parts = message.split(":");
                    if (parts.length >= 3) {
                        this.host = parts[1];
                        this.port = Integer.parseInt(parts[2]);
                        System.out.println("[DISCOVERY] Found server at " + host + ":" + port);
                        return true;
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("[DISCOVERY] No server found on local network");
                return false;
            }
        } catch (IOException e) {
            System.err.println("[DISCOVERY] Error: " + e.getMessage());
        }

        return false;
    }

=======
>>>>>>> 4de9990 (Add files via upload)
    public void start() {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            consoleReader = new BufferedReader(new InputStreamReader(System.in));
            running = true;

            System.out.println("Connected to " + host + ":" + port);

            // Start receiver thread
            Thread receiverThread = new Thread(new MessageReceiver());
            receiverThread.setDaemon(true);
            receiverThread.start();

            // Display help
            displayHelp();

            // Main input loop
            String input;
            while (running && (input = consoleReader.readLine()) != null) {
                input = input.trim();
                if (!input.isEmpty()) {
                    processInput(input);
                }
            }

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private class MessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleServerMessage(line.trim());
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("\nConnection lost: " + e.getMessage());
                    running = false;
                }
            }
        }
    }

    private void handleServerMessage(String message) {
        if (message.isEmpty()) return;

        String[] parts = message.split(" ", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "WELCOME":
                System.out.println("\n" + args);
                break;

            case "OK":
                System.out.println("[OK] " + args);
                break;

            case "ERROR":
                System.out.println("[ERROR] " + args);
                break;

            case "MSG":
                // Direct message received
                String[] msgParts = args.split(" ", 2);
                if (msgParts.length == 2) {
                    System.out.println("\n[DM from " + msgParts[0] + "] " + msgParts[1]);
                }
                break;

            case "CHAN":
                // Channel message received
                String[] chanParts = args.split(" ", 3);
                if (chanParts.length == 3) {
                    System.out.println("\n[" + chanParts[0] + "] <" + chanParts[1] + "> " + chanParts[2]);
                }
                break;

            case "JOIN":
                // User joined channel
                String[] joinParts = args.split(" ", 2);
                if (joinParts.length == 2) {
                    System.out.println("\n[" + joinParts[0] + "] *** " + joinParts[1] + " joined");
                }
                break;

            case "PART":
                // User left channel
                String[] partParts = args.split(" ", 2);
                if (partParts.length == 2) {
                    System.out.println("\n[" + partParts[0] + "] *** " + partParts[1] + " left");
                }
                break;

            case "QUIT":
                // User disconnected
                String[] quitParts = args.split(" ", 2);
                System.out.println("\n*** " + quitParts[0] + " disconnected");
                break;

            case "USERLIST":
                // List of users
                String[] userParts = args.split(" ", 2);
                if (userParts.length == 2) {
                    System.out.println("\n[Users in " + userParts[0] + "] " + userParts[1]);
                } else {
                    System.out.println("\n[Users] " + args);
                }
                break;

            case "CHANLIST":
                // List of channels
                System.out.println("\n[Channels] " + args);
                break;

            case "FILEOFFER":
                // File transfer offer
                String[] fileParts = args.split(" ", 3);
                if (fileParts.length == 3) {
                    String sender = fileParts[0];
                    String filename = fileParts[1];
                    int size = Integer.parseInt(fileParts[2]);
                    System.out.println("\n[FILE] " + sender + " wants to send you '" + filename +
                                     "' (" + size + " bytes)");
                    System.out.println("[FILE] Accepting file transfer...");
                    receiveFile(filename, size);
                }
                break;

            default:
                // Unknown message
                System.out.println("\n" + message);
        }
    }

    private void receiveFile(String filename, int size) {
        try {
            // Read FILEDATA header
            InputStream in = socket.getInputStream();
            BufferedReader headerReader = new BufferedReader(new InputStreamReader(in));

            String header = headerReader.readLine();
            if (header == null || !header.startsWith("FILEDATA")) {
                System.out.println("[ERROR] Invalid file transfer");
                return;
            }

            // Read file data
            byte[] fileData = new byte[size];
            int totalRead = 0;

            while (totalRead < size) {
                int bytesRead = in.read(fileData, totalRead, size - totalRead);
                if (bytesRead == -1) break;
                totalRead += bytesRead;
            }

            // Save file
            String savePath = "received_" + filename;
            Files.write(Paths.get(savePath), fileData);

            System.out.println("[FILE] Received '" + filename + "' -> " + savePath);

        } catch (IOException e) {
            System.out.println("[ERROR] File receive failed: " + e.getMessage());
        }
    }

    private void processInput(String input) {
        if (input.startsWith("/")) {
            processCommand(input.substring(1));
        } else {
            System.out.println("Commands must start with /. Type /help for help");
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "help":
                displayHelp();
                break;

            case "nick":
                if (args.isEmpty()) {
                    System.out.println("Usage: /nick <nickname>");
                    return;
                }
                nickname = args.trim();
                send("NICK " + nickname);
                break;

            case "join":
                if (args.isEmpty()) {
                    System.out.println("Usage: /join <#channel>");
                    return;
                }
                String joinChannel = args.trim();
                if (!joinChannel.startsWith("#")) {
                    joinChannel = "#" + joinChannel;
                }
                send("JOIN " + joinChannel);
                break;

            case "part":
                if (args.isEmpty()) {
                    System.out.println("Usage: /part <#channel>");
                    return;
                }
                String partChannel = args.trim();
                if (!partChannel.startsWith("#")) {
                    partChannel = "#" + partChannel;
                }
                send("PART " + partChannel);
                break;

            case "msg":
                String[] msgParts = args.split(" ", 2);
                if (msgParts.length < 2) {
                    System.out.println("Usage: /msg <user> <message>");
                    return;
                }
                send("MSG " + msgParts[0].trim() + " " + msgParts[1]);
                break;

            case "chan":
                String[] chanParts = args.split(" ", 2);
                if (chanParts.length < 2) {
                    System.out.println("Usage: /chan <#channel> <message>");
                    return;
                }
                String channel = chanParts[0].trim();
                if (!channel.startsWith("#")) {
                    channel = "#" + channel;
                }
                send("CHAN " + channel + " " + chanParts[1]);
                break;

            case "list":
                send("LIST");
                break;

            case "users":
                if (!args.isEmpty()) {
                    String usersChannel = args.trim();
                    if (!usersChannel.startsWith("#")) {
                        usersChannel = "#" + usersChannel;
                    }
                    send("USERS " + usersChannel);
                } else {
                    send("USERS");
                }
                break;

            case "file":
                String[] fileParts = args.split(" ", 2);
                if (fileParts.length < 2) {
                    System.out.println("Usage: /file <user> <filepath>");
                    return;
                }
                sendFile(fileParts[0].trim(), fileParts[1].trim());
                break;

            case "quit":
                String quitMsg = args.isEmpty() ? "Goodbye" : args;
                send("QUIT " + quitMsg);
                running = false;
                break;

            default:
                System.out.println("Unknown command: " + cmd + ". Type /help for help");
        }
    }

    private void sendFile(String user, String filepath) {
        File file = new File(filepath);

        if (!file.exists()) {
            System.out.println("[ERROR] File not found: " + filepath);
            return;
        }

        try {
            byte[] fileData = Files.readAllBytes(file.toPath());
            String filename = file.getName();

            // Send FILE command
            send("FILE " + user + " " + filename + " " + fileData.length);

            // Wait briefly for OK response
            Thread.sleep(100);

            // Send file data
            OutputStream out = socket.getOutputStream();
            out.write(fileData);
            out.flush();

            System.out.println("[FILE] Sent '" + filename + "' to " + user);

        } catch (IOException | InterruptedException e) {
            System.out.println("[ERROR] File send failed: " + e.getMessage());
        }
    }

    private void send(String message) {
        if (writer != null) {
            writer.println(message);
            writer.flush();
        }
    }

    private void displayHelp() {
        System.out.println("\n=== Chat Client Commands ===");
        System.out.println("/nick <nickname>           - Set your nickname");
        System.out.println("/join <#channel>           - Join a channel");
        System.out.println("/part <#channel>           - Leave a channel");
        System.out.println("/msg <user> <message>      - Send direct message to user");
        System.out.println("/chan <#channel> <message> - Send message to channel");
        System.out.println("/list                      - List all channels");
        System.out.println("/users [#channel]          - List all users or users in channel");
        System.out.println("/file <user> <filepath>    - Send file to user");
        System.out.println("/quit [message]            - Disconnect from server");
        System.out.println("/help                      - Show this help message");
        System.out.println("===========================\n");
    }

    private void disconnect() {
        running = false;

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (consoleReader != null) consoleReader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }

        System.out.println("Disconnected");
    }

    public static void main(String[] args) {
<<<<<<< HEAD
        String host = null;
        int port = 6667;
        boolean autoDiscover = true;

        if (args.length > 0) {
            host = args[0];
            autoDiscover = false;
=======
        String host = "localhost";
        int port = 6667;

        if (args.length > 0) {
            host = args[0];
>>>>>>> 4de9990 (Add files via upload)
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[1]);
                System.exit(1);
            }
        }

<<<<<<< HEAD
        ChatClient client = new ChatClient(host != null ? host : "localhost", port);

        // Try auto-discovery if no host specified
        if (autoDiscover) {
            System.out.println("=== Auto-Discovery Mode ===");
            if (!client.discoverServer()) {
                System.out.println("Falling back to localhost:6667");
                client = new ChatClient("localhost", 6667);
            }
        }

=======
        ChatClient client = new ChatClient(host, port);
>>>>>>> 4de9990 (Add files via upload)
        client.start();
    }
}
