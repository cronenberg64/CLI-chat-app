import javax.net.ssl.*;
import java.security.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;

public class ChatClient {
    /**
     * Command-line Chat Client
     * This is the program you run to talk to your friends!
     * It handles sending messages, receiving them, transferring files, and playing
     * Battleship.
     */

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
        // constructor to initialize variables
        this.host = host;
        this.port = port;
        this.running = false;
    }

    public void start() {
        try {
            // load the keystore
            KeyStore ks = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream("chat.jks")) {
                ks.load(fis, "password".toCharArray()); // unlock the keystore
            }

            // initialize TrustManagerFactory with the KeyStore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // initialize SSLContext with our trust managers
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // create a secure SSLSocket instead of a plain Socket
            SSLSocketFactory ssf = sslContext.getSocketFactory();
            socket = ssf.createSocket(host, port);

            // set up our streams for talking to the server
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true); // 'true' for auto-flush!
            consoleReader = new BufferedReader(new InputStreamReader(System.in));

            running = true;

            System.out.println("Connected to " + host + ":" + port);

            // create a thread to receive messages from the server
            Thread receiverThread = new Thread(new MessageReceiver());
            receiverThread.setDaemon(true); // daemon means this thread dies if the main program ends.
            receiverThread.start();

            // show the help menu
            displayHelp();

            // main input loop
            String input;
            while (running && (input = consoleReader.readLine()) != null) {
                input = input.trim();
                if (!input.isEmpty()) {
                    processInput(input); // figure out what the user wants to do
                }
            }

        } catch (IOException | GeneralSecurityException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            disconnect(); // clean up when we exit
        }
    }

    // inner class to handle incoming messages from the server
    private class MessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                // keep reading lines from the server until the connection closes
                while (running && (line = reader.readLine()) != null) {
                    // check the trimmed version for logic, but keep the original for formatting
                    String trimmedCheck = line.trim();

                    // special handling for Battleship game messages
                    if (trimmedCheck.startsWith("GAME_START ") || trimmedCheck.startsWith("GAME_UPDATE ")
                            || trimmedCheck.startsWith("GAME_SETUP ")) {
                        handleGameMessage(line); // pass original line to preserve whitespace/art
                    } else if (trimmedCheck.startsWith("GAME_OVER ")) {
                        handleGameMessage(line);
                    } else {
                        // normal chat messages
                        handleServerMessage(line);
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

    // function that decides what to do with a message from the server
    private void handleServerMessage(String message) {
        if (message.isEmpty())
            return;

        // split the message into command and arguments
        String[] parts = message.split(" ", 2);
        String cmd = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        // handle different types of messages
        switch (cmd) {
            case "WELCOME":
                System.out.println("\n" + args);
                break;

            case "OK":
                // for file transfer, if we get ok then we can start sending bytes
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
                // dm received
                String[] msgParts = args.split(" ", 2);
                if (msgParts.length == 2) {
                    System.out.println("\n[DM from " + msgParts[0] + "] " + msgParts[1]);
                }
                break;

            case "CHAN":
                // channel message received
                String[] chanParts = args.split(" ", 3);
                if (chanParts.length == 3) {
                    System.out.println("\n[" + chanParts[0] + "] <" + chanParts[1] + "> " + chanParts[2]);
                }
                break;

            case "JOIN":
                // someone joined a channel
                String[] joinParts = args.split(" ", 2);
                if (joinParts.length == 2) {
                    System.out.println("\n[" + joinParts[0] + "] *** " + joinParts[1] + " joined");
                }
                break;

            case "PART":
                // someone left a channel
                String[] partParts = args.split(" ", 2);
                if (partParts.length == 2) {
                    System.out.println("\n[" + partParts[0] + "] *** " + partParts[1] + " left");
                }
                break;

            case "QUIT":
                // someone disconnected entirely
                String[] quitParts = args.split(" ", 2);
                System.out.println("\n*** " + quitParts[0] + " disconnected");
                break;

            case "USERLIST":
                // show users in a channel
                String[] userParts = args.split(" ", 2);
                if (userParts.length == 2) {
                    System.out.println("\n[Users in " + userParts[0] + "] " + userParts[1]);
                } else {
                    System.out.println("\n[Users] " + args);
                }
                break;

            case "CHANLIST":
                // show channels
                System.out.println("\n[Channels] " + args);
                break;

            case "FILEOFFER":
                // someone wants to send us a file
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
                    // automatically accept file
                    receiveFile(filename, size, hash);
                }
                break;

            default:
                // unknown message, just print it raw
                System.out.println("\n" + message);
        }
    }

    // function to receive a file from the server from another client
    private void receiveFile(String filename, int size, String expectedHash) {
        try {
            // expect a specific header first: FILEDATA
            InputStream in = socket.getInputStream();
            BufferedReader headerReader = new BufferedReader(new InputStreamReader(in));
            String header = headerReader.readLine();
            if (header == null || !header.startsWith("FILEDATA")) {
                System.out.println("[ERROR] Invalid file transfer header");
                return;
            }

            // read the exact number of bytes for the file
            byte[] fileData = new byte[size];
            int totalRead = 0;

            while (totalRead < size) {
                int bytesRead = in.read(fileData, totalRead, size - totalRead);
                if (bytesRead == -1)
                    break;
                totalRead += bytesRead;
            }

            // verify integrity using SHA-256
            if (expectedHash != null) {
                String calculatedHash = calculateChecksum(fileData);
                if (!calculatedHash.equalsIgnoreCase(expectedHash)) {
                    System.out.println("[ERROR] File integrity check failed!");
                    System.out.println("Expected: " + expectedHash);
                    System.out.println("Actual:   " + calculatedHash);
                    return;
                }
                System.out.println("[SUCCESS] File integrity verified.");
            }

            // save the file to disk with a prefix so we don't overwrite existing files
            String savePath = "received_" + filename;
            Files.write(Paths.get(savePath), fileData);

            System.out.println("[FILE] Received '" + filename + "' -> " + savePath);

        } catch (IOException e) {
            System.out.println("[ERROR] File receive failed: " + e.getMessage());
        }
    }

    // function to process commands typed by the user in the console
    private void processInput(String input) {
        if (input.startsWith("/")) {
            processCommand(input.substring(1)); // Remove the '/'
        } else {
            System.out.println("Commands must start with /. Type /help for help");
        }
    }

    // function to parse and execute client-side commands
    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            // display help message
            case "help":
                displayHelp();
                break;

            // change nickname
            case "nick":
                if (args.isEmpty()) {
                    System.out.println("Usage: /nick <nickname>");
                    return;
                }
                nickname = args.trim();
                send("NICK " + nickname);
                break;

            // join a channel
            case "join":
                if (args.isEmpty()) {
                    System.out.println("Usage: /join <#channel>");
                    return;
                }
                String joinChannel = args.trim();
                // auto-add hash if missing
                if (!joinChannel.startsWith("#")) {
                    joinChannel = "#" + joinChannel;
                }
                send("JOIN " + joinChannel);
                break;

            // leave a channel
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

            // send a private message
            case "msg":
                String[] msgParts = args.split(" ", 2);
                if (msgParts.length < 2) {
                    System.out.println("Usage: /msg <user> <message>");
                    return;
                }
                send("MSG " + msgParts[0].trim() + " " + msgParts[1]);
                break;

            // send a message to a channel
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

            // list all channels
            case "list":
                send("LIST");
                break;

            // list users in a channel
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

            // send a file to another client
            case "file":
                String[] fileParts = args.split(" ", 2);
                if (fileParts.length < 2) {
                    System.out.println("Usage: /file <user> <filepath>");
                    return;
                }
                sendFile(fileParts[0].trim(), fileParts[1].trim());
                break;

            // start a game
            case "game":
                if (args.isEmpty()) {
                    System.out.println("Usage: /game [challenge|accept|fire|quit] <args>");
                    return;
                }
                send("GAME " + args);
                break;

            // fire a coordinate
            case "fire":
                if (args.isEmpty()) {
                    System.out.println("Usage: /fire <coord> (e.g., A5)");
                    return;
                }
                send("GAME FIRE " + args);
                break;

            // place a coordinate
            case "place":
                if (args.isEmpty()) {
                    System.out.println("Usage: /place <coord> <H/V> (e.g., A1 H)");
                    return;
                }
                send("GAME PLACE " + args);
                break;

            // surrender
            case "surrender":
                send("GAME SURRENDER");
                break;

            // quit the chat
            case "quit":
                String quitMsg = args.isEmpty() ? "Goodbye" : args;
                send("QUIT " + quitMsg);
                running = false;
                break;

            default:
                System.out.println("Unknown command: " + cmd + ". Type /help for help");
        }
    }

    // function to send a file to another client
    private void sendFile(String user, String filepath) {
        File file = new File(filepath);

        if (!file.exists()) {
            System.out.println("[ERROR] File not found: " + filepath);
            return;
        }

        try {
            // read the whole file into memory
            byte[] fileData = Files.readAllBytes(file.toPath());
            String filename = file.getName();

            // calculate SHA-256 checksum so the receiver can verify it
            String hash = calculateChecksum(fileData);
            System.out.println("[FILE] Calculated SHA-256: " + hash);

            // send FILE command to server: FILE <target> <filename> <size> <hash>
            synchronized (fileTransferLock) {
                fileTransferReady = false;
                send("FILE " + user + " " + filename + " " + fileData.length + " " + hash);

                // wait for the server to say "OK"
                try {
                    fileTransferLock.wait(5000); // wait max 5 seconds
                } catch (InterruptedException e) {
                    System.out.println("[ERROR] Interrupted while waiting for server response");
                    return;
                }

                if (!fileTransferReady) {
                    System.out.println("[ERROR] Server timed out or did not accept file transfer");
                    return;
                }
            }

            // send the raw bytes
            OutputStream out = socket.getOutputStream();
            out.write(fileData);
            out.flush();

            System.out.println("[FILE] Sent '" + filename + "' to " + user);

        } catch (IOException e) {
            System.out.println("[ERROR] File send failed: " + e.getMessage());
        }
    }

    // handles visual updates for the battleship game
    private void handleGameMessage(String line) {
        // clear screen using ANSI escape codes
        System.out.print("\033[H\033[2J");
        System.out.flush();

        // only animate the cool banner once per game session
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
            // static print for backup
            System.out.println("=================================================");
            System.out.println("  ____    _  _____ _____ _     _____ ____  _   _ ___ ____  ");
            System.out.println(" | __ )  / \\|_   _|_   _| |   | ____/ ___|| | | |_ _|  _ \\ ");
            System.out.println(" |  _ \\ / _ \\ | |   | | | |   |  _| \\___ \\| |_| || || |_) |");
            System.out.println(" | |_) / ___ \\| |   | | | |___| |___ ___) |  _  || ||  __/ ");
            System.out.println(" |____/_/   \\_\\_|   |_| |_____|_____|____/|_| |_|___|_|    ");
            System.out.println("=================================================");
        }

        // remove the protocol prefix and print the rest of the board
        int firstSpace = line.indexOf(' ');
        if (firstSpace != -1) {
            System.out.println(line.substring(firstSpace + 1));
        }

        // reset banner flag on game over
        if (line.startsWith("GAME_OVER")) {
            bannerShown = false;
            System.out.println("\n[GAME] Game ended. Returning to chat...");
            System.out.println("-------------------------------------------------");
            displayHelp(); // reprint help so user knows what to do
            System.out.print("> "); // explicit prompt
            System.out.flush();
        }
    }

    // function to print text character by character for animation
    private void slowPrint(String text) {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // helper function to send a line of text to the server
    private void send(String message) {
        if (writer != null) {
            writer.println(message);
            writer.flush();
        }
    }

    // helper function to show the list of available commands
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

    // helper function to close all resources
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

    // helper function to calculate the SHA-256 hash of a byte array
    private String calculateChecksum(byte[] data) {
        try {
            // calculate the SHA-256 hash of the data
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

        // parse command line arguments
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

        // create and start the client
        ChatClient client = new ChatClient(host, port);
        client.start();
    }
}
