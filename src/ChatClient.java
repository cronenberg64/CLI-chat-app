import javax.net.ssl.*;
import java.security.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Command-line Chat Client
 * Supports direct messages, channels, and file transfers
 */
public class ChatClient {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private BufferedReader consoleReader;
    private boolean running;
    private String nickname;
    private final Object fileTransferLock = new Object();
    private volatile boolean fileTransferReady = false;
    private boolean bannerShown = false;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.running = false;
    }

    public void start() {
        try {
            // Load TrustStore (using the same keystore file for simplicity)
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream("chat.jks")) {
                ks.load(fis, "password".toCharArray());
            }

            // Initialize TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // Initialize SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // Create SSLSocket
            SSLSocketFactory ssf = sslContext.getSocketFactory();
            socket = ssf.createSocket(host, port);
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

        } catch (IOException | GeneralSecurityException e) {
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
                    // Do NOT trim globally, or we lose board indentation!
                    String trimmedCheck = line.trim();

                    if (trimmedCheck.startsWith("GAME_START ") || trimmedCheck.startsWith("GAME_UPDATE ")
                            || trimmedCheck.startsWith("GAME_SETUP ")) {
                        handleGameMessage(line); // Pass original line to preserve whitespace
                    } else if (trimmedCheck.startsWith("GAME_OVER ")) {
                        handleGameMessage(line);
                        // "Returning to chat" is now handled inside handleGameMessage to ensure order
                    } else {
                        handleServerMessage(line); // Pass original line to preserve whitespace for board rows
                    }
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
        if (message.isEmpty())
            return;

        String[] parts = message.split(" ", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "WELCOME":
                System.out.println("\n" + args);
                break;

            case "OK":
                if (args.startsWith("FILE")) {
                    synchronized (fileTransferLock) {
                        fileTransferReady = true;
                        fileTransferLock.notifyAll();
                    }
                }
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
                // Expected: FILEOFFER <sender> <filename> <size> <hash>
                String[] fileParts = args.split(" ", 4);
                if (fileParts.length >= 3) {
                    String sender = fileParts[0];
                    String filename = fileParts[1];
                    int size = Integer.parseInt(fileParts[2]);
                    String hash = fileParts.length > 3 ? fileParts[3] : null;

                    System.out.println("\n[FILE] " + sender + " wants to send you '" + filename +
                            "' (" + size + " bytes)");
                    if (hash != null) {
                        System.out.println("[FILE] Checksum (SHA-256): " + hash);
                    }
                    System.out.println("[FILE] Accepting file transfer...");
                    receiveFile(filename, size, hash);
                }
                break;

            default:
                // Unknown message
                System.out.println("\n" + message);
        }
    }

    private void receiveFile(String filename, int size, String expectedHash) {
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
                if (bytesRead == -1)
                    break;
                totalRead += bytesRead;
            }

            // Verify Checksum
            if (expectedHash != null) {
                String calculatedHash = calculateChecksum(fileData);
                if (!calculatedHash.equalsIgnoreCase(expectedHash)) {
                    System.out.println("[ERROR] File integrity check failed!");
                    System.out.println("Expected: " + expectedHash);
                    System.out.println("Actual:   " + calculatedHash);
                    return; // Do not save corrupt file
                }
                System.out.println("[SUCCESS] File integrity verified.");
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

            case "game":
                if (args.isEmpty()) {
                    System.out.println("Usage: /game [challenge|accept|fire|quit] <args>");
                    return;
                }
                send("GAME " + args);
                break;

            case "fire":
                if (args.isEmpty()) {
                    System.out.println("Usage: /fire <coord> (e.g., A5)");
                    return;
                }
                send("GAME FIRE " + args);
                break;

            case "place":
                if (args.isEmpty()) {
                    System.out.println("Usage: /place <coord> <H/V> (e.g., A1 H)");
                    return;
                }
                send("GAME PLACE " + args);
                break;

            case "surrender":
                send("GAME SURRENDER");
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

            // Calculate Checksum
            String hash = calculateChecksum(fileData);
            System.out.println("[FILE] Calculated SHA-256: " + hash);

            // Send FILE command
            synchronized (fileTransferLock) {
                fileTransferReady = false;
                send("FILE " + user + " " + filename + " " + fileData.length + " " + hash);

                // Wait for OK response (max 5 seconds)
                try {
                    fileTransferLock.wait(5000);
                } catch (InterruptedException e) {
                    System.out.println("[ERROR] Interrupted while waiting for server response");
                    return;
                }

                if (!fileTransferReady) {
                    System.out.println("[ERROR] Server timed out or did not accept file transfer");
                    return;
                }
            }

            // Send file data
            OutputStream out = socket.getOutputStream();
            out.write(fileData);
            out.flush();

            System.out.println("[FILE] Sent '" + filename + "' to " + user);

        } catch (IOException e) {
            System.out.println("[ERROR] File send failed: " + e.getMessage());
        }
    }

    private void handleGameMessage(String line) {
        // Clear screen (ANSI escape codes)
        System.out.print("\033[H\033[2J");
        System.out.flush();

        // Only animate the banner once per game session
        if (!bannerShown && (line.startsWith("GAME_START") || line.startsWith("GAME_SETUP"))) {
            slowPrint("=================================================\n");
            slowPrint("  ____    _  _____ _____ _     _____ ____  _   _ ___ ____  \n");
            slowPrint(" | __ )  / \\|_   _|_   _| |   | ____/ ___|| | | |_ _|  _ \\ \n");
            slowPrint(" |  _ \\ / _ \\ | |   | | | |   |  _| \\___ \\| |_| || || |_) |\n");
            slowPrint(" | |_) / ___ \\| |   | | | |___| |___ ___) |  _  || ||  __/ \n");
            slowPrint(" |____/_/   \\_\\_|   |_| |_____|_____|____/|_| |_|___|_|    \n");
            slowPrint("=================================================\n");
            bannerShown = true;
        } else {
            // Static print for updates to prevent flickering/re-animation
            System.out.println("=================================================");
            System.out.println("  ____    _  _____ _____ _     _____ ____  _   _ ___ ____  ");
            System.out.println(" | __ )  / \\|_   _|_   _| |   | ____/ ___|| | | |_ _|  _ \\ ");
            System.out.println(" |  _ \\ / _ \\ | |   | | | |   |  _| \\___ \\| |_| || || |_) |");
            System.out.println(" | |_) / ___ \\| |   | | | |___| |___ ___) |  _  || ||  __/ ");
            System.out.println(" |____/_/   \\_\\_|   |_| |_____|_____|____/|_| |_|___|_|    ");
            System.out.println("=================================================");
        }
        // For updates or if banner already shown, we skip the banner to reduce
        // clutter/flicker

        // Remove the protocol prefix (GAME_START, etc) and print the rest
        int firstSpace = line.indexOf(' ');
        if (firstSpace != -1) {
            System.out.println(line.substring(firstSpace + 1));
        }

        // Reset banner flag on Game Over
        if (line.startsWith("GAME_OVER")) {
            bannerShown = false;
            System.out.println("\n[GAME] Game ended. Returning to chat...");
            System.out.println("-------------------------------------------------");
            displayHelp(); // Reprint help so user knows what to do
            System.out.print("> "); // Explicit prompt
            System.out.flush();
        }
    }

    private void slowPrint(String text) {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            try {
                Thread.sleep(2); // Fast typing effect
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
        System.out.println("/game challenge <user>     - Challenge a user to Battleship");
        System.out.println("/game accept <user>        - Accept a Battleship challenge");
        System.out.println("/quit [message]            - Disconnect from server");
        System.out.println("/help                      - Show this help message");
        System.out.println("===========================\n");
    }

    private void disconnect() {
        running = false;

        try {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
            if (consoleReader != null)
                consoleReader.close();
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }

        System.out.println("Disconnected");
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[ERROR] SHA-256 algorithm not found: " + e.getMessage());
            return "UNKNOWN";
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 6667;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port: " + args[1]);
                System.exit(1);
            }
        }

        ChatClient client = new ChatClient(host, port);
        client.start();
    }
}
