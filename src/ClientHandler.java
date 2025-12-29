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
        // If server has no password, we are authenticated by default regarding
        // password,
        // but still need NICK. However, let's keep 'authenticated' as "fully ready".
        // Actually, let's split it: passwordAuth and nickSet.
        // For simplicity, let's say 'authenticated' means "passed password check".
        this.authenticated = server.checkPassword(null); // True if no password set
        this.running = true;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            send("WELCOME Welcome to the Secure Chat Server!\n");
            if (!authenticated) {
                send("INFO Please authenticate with /auth <password>\n");
            } else {
                send("INFO Please set your nickname with /nick <name>\n");
            }

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

        // Handle IRC-style trailing parameters (prefixed with :)
        if (args.startsWith(":")) {
            args = args.substring(1);
        } else if (args.contains(" :")) {
            // Split into non-trailing and trailing parts
            String[] splitArgs = args.split(" :", 2);
            args = splitArgs[0] + " " + splitArgs[1];
        }

        System.out.println("[CLIENT " + getIdentifier() + "] Command: " + cmd + " " +
                (args.length() > 50 ? args.substring(0, 50) + "..." : args));

        // NICK command doesn't require authentication
        if (cmd.equals("AUTH")) {
            handleAuth(args);
        } else if (cmd.equals("NICK")) {
            if (!authenticated) {
                send("ERROR 401 You must authenticate first with /auth <password>\n");
            } else {
                handleNick(args);
            }
        } else if (!authenticated) {
            send("ERROR 401 You must authenticate first with /auth <password>\n");
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
                    handleList(args);
                    break;
                case "USERS":
                    handleUsers(args);
                    break;
                case "FILE":
                    handleFile(args);
                    break;
                case "GAME":
                    handleGame(args);
                    break;
                case "USER":
                    // IRC USER command: USER <username> <mode> <unused> :<realname>
                    // We just accept it to allow connection
                    break;
                case "PING":
                    // IRC PING command: PING :<server>
                    send("PONG " + args + "\n");
                    break;
                case "PRIVMSG":
                    handlePrivMsg(args);
                    break;
                case "QUIT":
                    running = false;
                    send("QUIT Goodbye!\n");
                    break;
                default:
                    send("ERROR 400 Unknown command\n");
            }
        }
    }

    private void handleAuth(String password) {
        if (authenticated) {
            send("ERROR 400 Already authenticated\n");
            return;
        }

        if (server.checkPassword(password.trim())) {
            authenticated = true;
            send("OK AUTH Password accepted. Now set your nickname with /nick <name>\n");
        } else {
            send("ERROR 401 Incorrect password\n");
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
        // this.authenticated = true; // Removed this line as authentication is now
        // password-based

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

    private void handleGame(String args) {
        String[] parts = args.split(" ", 2);
        String subCmd = parts[0].toUpperCase();
        String param = parts.length > 1 ? parts[1] : "";

        switch (subCmd) {
            case "CHALLENGE":
                ClientHandler opponent = server.getClient(param);
                if (opponent == null) {
                    send("ERROR User not found\n");
                } else if (opponent == this) {
                    send("ERROR You cannot challenge yourself\n");
                } else {
                    opponent.send("GAME_REQ " + nickname + " has challenged you to Battleship! Type '/game accept "
                            + nickname + "' to play.\n");
                    send("OK GAME Challenge sent to " + param + "\n");
                }
                break;

            case "ACCEPT":
                ClientHandler challenger = server.getClient(param);
                if (challenger == null) {
                    send("ERROR User not found\n");
                } else {
                    server.startGame(challenger, this);
                }
                break;

            case "PLACE":
                GameSession setupGame = server.getGame(this);
                if (setupGame == null) {
                    send("ERROR You are not in a game\n");
                    return;
                }
                // param expected: "A1 H"
                String[] placeParts = param.split(" ");
                if (placeParts.length < 2) {
                    send("ERROR Usage: /game place <coord> <H/V>\n");
                    return;
                }
                String result = setupGame.placeShip(this, placeParts[0], placeParts[1]);
                if (result.startsWith("ERROR")) {
                    send(result + "\n");
                } else if (result.equals("READY")) {
                    // Game starts!
                    ClientHandler p1 = setupGame.getPlayer1();
                    ClientHandler p2 = setupGame.getPlayer2();
                    p1.send("GAME_START Game Started! Your turn.\n" + setupGame.getRenderedBoard(p1));
                    p2.send("GAME_START Game Started! Opponent's turn.\n" + setupGame.getRenderedBoard(p2));
                } else if (result.startsWith("PLACED")) {
                    // Next ship
                    String nextShip = setupGame.getNextShipName(this);
                    if (nextShip.equals("WAITING")) {
                        send("GAME_UPDATE All ships placed. Waiting for opponent...\n"
                                + setupGame.getRenderedBoard(this));
                    } else {
                        send("GAME_SETUP Placed! Next: " + nextShip + "\n" + setupGame.getRenderedBoard(this));
                    }
                }
                break;

            case "FIRE":
                GameSession game = server.getGame(this);
                if (game == null) {
                    send("ERROR You are not in a game\n");
                    return;
                }
                String fireResult = game.processMove(this, param);
                if (fireResult.startsWith("ERROR")) {
                    send(fireResult + "\n");
                } else {
                    // Valid move
                    ClientHandler opp = game.getOpponent(this);
                    if (fireResult.equals("WIN")) {
                        send("GAME_OVER YOU WON!\n");
                        opp.send("GAME_OVER YOU LOST!\n");
                        server.endGame(game);
                    } else {
                        String msg = "You fired at " + param + ": " + fireResult + "!\n";
                        send("GAME_UPDATE " + msg + game.getRenderedBoard(this));
                        opp.send("GAME_UPDATE Opponent fired at " + param + ": " + fireResult + "!\n"
                                + game.getRenderedBoard(opp));
                    }
                }
                break;

            case "SURRENDER":
            case "QUIT":
                GameSession activeGame = server.getGame(this);
                if (activeGame != null) {
                    ClientHandler opp = activeGame.getOpponent(this);
                    opp.send("GAME_OVER Opponent surrendered! You win!\n");
                    send("GAME_OVER You surrendered.\n");
                    server.endGame(activeGame);
                } else {
                    send("ERROR No active game to surrender\n");
                }
                break;

            default:
                send("ERROR Unknown game command. Usage: /game [challenge|accept|fire|quit]\n");
        }
    }

    private void handleList(String args) {
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
        // Expected: FILE <user> <filename> <size> [checksum]
        String[] parts = args.split(" ", 4);
        if (parts.length < 3) {
            send("ERROR 400 Usage: FILE <user> <filename> <size> [checksum]\n");
            return;
        }

        String target = parts[0].trim();
        String filename = parts[1].trim();
        int size;
        String checksum = parts.length > 3 ? parts[3].trim() : null;

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
        String offerMsg = "FILEOFFER " + nickname + " " + filename + " " + size;
        if (checksum != null) {
            offerMsg += " " + checksum;
        }
        targetClient.send(offerMsg + "\n");

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

    private void handlePrivMsg(String args) {
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            send("ERROR 400 Usage: PRIVMSG <target> <message>\n");
            return;
        }

        String target = parts[0].trim();
        String message = parts[1];

        if (target.startsWith("#")) {
            // It's a channel message
            if (!server.isInChannel(target, nickname)) {
                send("ERROR 404 You are not in " + target + "\n");
                return;
            }
            server.broadcastToChannel(target, "CHAN " + target + " " + nickname + " " + message + "\n", nickname);
        } else {
            // It's a direct message
            ClientHandler targetClient = server.getClient(target);
            if (targetClient == null) {
                send("ERROR 404 User " + target + " not found\n");
                return;
            }
            targetClient.send("MSG " + nickname + " " + message + "\n");
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
            server.removeFromAllChannels(nickname);
            server.unregisterClient(nickname);
            server.broadcastQuit(nickname, "Disconnected");

            // Check if in active game and surrender
            GameSession activeGame = server.getGame(this);
            if (activeGame != null) {
                ClientHandler opp = activeGame.getOpponent(this);
                opp.send("GAME_OVER Opponent disconnected! You win!\n");
                server.endGame(activeGame);
            }
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
