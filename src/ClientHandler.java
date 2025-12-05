import java.io.*;
import java.net.*;

/**
 * Handles individual client connections
 * Each client runs in its own thread
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private String nickname;
    private boolean authenticated;
    private boolean running;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.nickname = null;
        this.authenticated = false;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            send("WELCOME Welcome to the chat server! Please set your nickname with /nick <name>\n");

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    processCommand(line);
                }
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("[CLIENT " + getIdentifier() + "] Error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String args = parts.length > 1 ? parts[1] : "";

        System.out.println("[CLIENT " + getIdentifier() + "] Command: " + cmd + " " +
                (args.length() > 50 ? args.substring(0, 50) + "..." : args));

        // NICK command doesn't require authentication
        if (cmd.equals("NICK")) {
            handleNick(args);
        } else if (!authenticated) {
            send("ERROR 401 Please set your nickname first with /nick <name>\n");
        } else {
            switch (cmd) {
                case "JOIN":
                    handleJoin(args);
                    break;
                case "PART":
                    handlePart(args);
                    break;
                case "MSG":
                    handleMsg(args);
                    break;
                case "CHAN":
                    handleChan(args);
                    break;
                case "LIST":
                    handleList();
                    break;
                case "USERS":
                    handleUsers(args);
                    break;
                case "FILE":
                    handleFile(args);
                    break;
                case "QUIT":
                    handleQuit(args);
                    break;
                default:
                    send("ERROR 400 Unknown command: " + cmd + "\n");
            }
        }
    }

    private void handleNick(String nickname) {
        nickname = nickname.trim();

        // Validate nickname
        if (nickname.isEmpty() || nickname.length() > 20 ||
                !nickname.matches("[a-zA-Z0-9_]+")) {
            send("ERROR 400 Invalid nickname (1-20 alphanumeric characters)\n");
            return;
        }

        // Check if changing own nickname
        if (this.nickname != null && this.nickname.equalsIgnoreCase(nickname)) {
            send("OK NICK You are already known as " + this.nickname + "\n");
            return;
        }

        // Check if nickname is taken
        if (server.isNicknameTaken(nickname.toLowerCase())) {
            send("ERROR 409 Nickname already in use\n");
            return;
        }

        // Unregister old nickname
        if (this.nickname != null) {
            server.unregisterClient(this.nickname);
            server.removeFromAllChannels(this.nickname);
        }

        // Register new nickname
        this.nickname = nickname;
        server.registerClient(nickname, this);
        this.authenticated = true;

        send("OK NICK Welcome, " + nickname + "!\n");
    }

    private void handleJoin(String channel) {
        channel = channel.trim();

        if (!channel.startsWith("#")) {
            send("ERROR 400 Channel name must start with #\n");
            return;
        }

        server.joinChannel(channel, nickname);
        send("OK JOIN Joined " + channel + "\n");

        // Notify others
        server.broadcastToChannel(channel, "JOIN " + channel + " " + nickname + "\n", nickname);
    }

    private void handlePart(String channel) {
        channel = channel.trim();

        if (!server.isInChannel(channel, nickname)) {
            send("ERROR 404 You are not in " + channel + "\n");
            return;
        }

        server.partChannel(channel, nickname);
        send("OK PART Left " + channel + "\n");

        // Notify others
        server.broadcastToChannel(channel, "PART " + channel + " " + nickname + "\n", null);
    }

    private void handleMsg(String args) {
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            send("ERROR 400 Usage: MSG <user> <message>\n");
            return;
        }

        String target = parts[0].trim();
        String message = parts[1];

        ClientHandler targetClient = server.getClient(target);
        if (targetClient == null) {
            send("ERROR 404 User " + target + " not found\n");
            return;
        }

        targetClient.send("MSG " + nickname + " " + message + "\n");
        send("OK MSG Message sent to " + target + "\n");
    }

    private void handleChan(String args) {
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            send("ERROR 400 Usage: CHAN <channel> <message>\n");
            return;
        }

        String channel = parts[0].trim();
        String message = parts[1];

        if (!server.isInChannel(channel, nickname)) {
            send("ERROR 404 You are not in " + channel + "\n");
            return;
        }

        server.broadcastToChannel(channel, "CHAN " + channel + " " + nickname + " " + message + "\n", nickname);
        send("OK CHAN Message sent to " + channel + "\n");
    }

    private void handleList() {
        java.util.List<String> channels = server.getChannelList();
        if (channels.isEmpty()) {
            send("CHANLIST No channels available\n");
        } else {
            send("CHANLIST " + String.join(" ", channels) + "\n");
        }
    }

    private void handleUsers(String args) {
        String channel = args.trim();

        if (channel.isEmpty()) {
            java.util.List<String> users = server.getAllUsers();
            send("USERLIST all " + String.join(" ", users) + "\n");
        } else {
            java.util.List<String> users = server.getUserList(channel);
            if (users.isEmpty()) {
                send("ERROR 404 Channel " + channel + " not found\n");
            } else {
                send("USERLIST " + channel + " " + String.join(" ", users) + "\n");
            }
        }
    }

    private void handleFile(String args) {
        String[] parts = args.split(" ", 3);
        if (parts.length < 3) {
            send("ERROR 400 Usage: FILE <user> <filename> <size>\n");
            return;
        }

        String target = parts[0].trim();
        String filename = parts[1].trim();
        int size;

        try {
            size = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            send("ERROR 400 Invalid file size\n");
            return;
        }

        ClientHandler targetClient = server.getClient(target);
        if (targetClient == null) {
            send("ERROR 404 User " + target + " not found\n");
            return;
        }

        // Notify target
        targetClient.send("FILEOFFER " + nickname + " " + filename + " " + size + "\n");

        // Receive file data
        send("OK FILE Send file data now\n");

        try {
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int remaining = size;
            int bytesRead;

            while (remaining > 0) {
                int toRead = Math.min(remaining, data.length);
                bytesRead = in.read(data, 0, toRead);
                if (bytesRead == -1)
                    break;
                buffer.write(data, 0, bytesRead);
                remaining -= bytesRead;
            }

            byte[] fileData = buffer.toByteArray();

            // Forward to target
            targetClient.sendFileData(fileData);

            send("OK FILE File sent to " + target + "\n");

        } catch (IOException e) {
            send("ERROR 500 File transfer failed: " + e.getMessage() + "\n");
        }
    }

    private void handleQuit(String message) {
        String quitMsg = message.isEmpty() ? "Client disconnected" : message;
        send("OK QUIT " + quitMsg + "\n");
        running = false;
    }

    public void send(String message) {
        if (writer != null) {
            writer.print(message);
            writer.flush();
        }
    }

    public void sendFileData(byte[] fileData) throws IOException {
        OutputStream out = socket.getOutputStream();
        String header = "FILEDATA " + fileData.length + "\n";
        out.write(header.getBytes());
        out.write(fileData);
        out.flush();
    }

    public void disconnect() {
        running = false;

        if (nickname != null) {
            System.out.println("[CLIENT " + nickname + "] Disconnected");

            server.removeFromAllChannels(nickname);
            server.unregisterClient(nickname);
            server.broadcastQuit(nickname, "Disconnected");
        }

        try {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }

    public String getNickname() {
        return nickname;
    }

    private String getIdentifier() {
        return nickname != null ? nickname : socket.getRemoteSocketAddress().toString();
    }
}
