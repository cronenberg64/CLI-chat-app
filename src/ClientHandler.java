import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    /**
     * Handles individual client connections.
     * Think of this as a personal butler for each user connected to the server.
     * It runs in its own thread so it can focus entirely on one client without
     * distraction.
     */

    private Socket socket;
    private ChatServer server;
    private BufferedReader reader;
    private PrintWriter writer;
    private String nickname;
    private boolean authenticated;
    private boolean running;

    public ClientHandler(Socket socket, ChatServer server) {
        // constructor to initialize variables
        this.socket = socket;
        this.server = server;
        this.nickname = null;
        this.authenticated = server.checkPassword(null);
        this.running = true;
    }

    // the main loop for this client's thread.
    @Override
    public void run() {
        try {
            // set up input and output streams
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            // instructions and texts
            send("WELCOME Welcome to CLI chat app!\n");

            if (!authenticated) {
                send("INFO Please authenticate with /auth <password>\n");
            } else {
                send("INFO Please set your nickname with /nick <name>\n");
            }

            // keep listening for commands until user leaves
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    processCommand(line); // execute command
                }
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("[CLIENT " + getIdentifier() + "] Error: " + e.getMessage());
            }
        } finally {
            disconnect(); // clean up
        }
    }

    // function to process commands
    private void processCommand(String command) {
        // split the command from its arguments (e.g., "JOIN #general" -> "JOIN",
        // "#general")
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String args = parts.length > 1 ? parts[1] : "";

        // log it to the server console so we can see what's happening
        System.out.println("[CLIENT " + getIdentifier() + "] Command: " + cmd + " " +
                (args.length() > 50 ? args.substring(0, 50) + "..." : args));

        // AUTH command (if password is required)
        if (cmd.equals("AUTH")) {
            handleAuth(args);
        }
        // NICK command (setting their name)
        else if (cmd.equals("NICK")) {
            if (!authenticated) {
                send("ERROR 401 You must authenticate first with /auth <password>\n");
            } else {
                handleNick(args);
            }
        }
        // For everything else, they must be authenticated first
        else if (!authenticated) {
            send("ERROR 401 You must authenticate first with /auth <password>\n");
        } else {
            // switch statement for all other commands
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
                case "QUIT":
                    handleQuit(args);
                    break;
                default:
                    send("ERROR 400 Unknown command\n");
            }
        }
    }

    // function to check the password
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

    // function to set the user's nickname
    private void handleNick(String nickname) {
        nickname = nickname.trim();

        // validate nickname format (alphanumeric, 1-20 chars)
        if (nickname.isEmpty() || nickname.length() > 20 ||
                !nickname.matches("[a-zA-Z0-9_]+")) {
            send("ERROR 400 Invalid nickname (1-20 alphanumeric characters)\n");
            return;
        }

        // check if they are just re-sending their own name
        if (this.nickname != null && this.nickname.equalsIgnoreCase(nickname)) {
            send("OK NICK You are already known as " + this.nickname + "\n");
            return;
        }

        // check if someone else already has this name
        if (server.isNicknameTaken(nickname.toLowerCase())) {
            send("ERROR 409 Nickname already in use\n");
            return;
        }

        // if they had an old name, remove it from the registry
        if (this.nickname != null) {
            server.unregisterClient(this.nickname);
            server.removeFromAllChannels(this.nickname);
        }

        // register the new name
        this.nickname = nickname;
        server.registerClient(nickname, this);

        send("OK NICK Welcome, " + nickname + "!\n");
    }

    // function to join a channel
    private void handleJoin(String channel) {
        channel = channel.trim();

        // channels must start with #
        if (!channel.startsWith("#")) {
            send("ERROR 400 Channel name must start with #\n");
            return;
        }

        server.joinChannel(channel, nickname);
        send("OK JOIN You joined " + channel + "\n");

        // broadcast to everyone in the channel
        server.broadcastToChannel(channel, "JOIN " + channel + " " + nickname + "\n", nickname);
    }

    // function to leave a channel
    private void handlePart(String channel) {
        channel = channel.trim();

        if (!server.isInChannel(channel, nickname)) {
            send("ERROR 404 You are not in " + channel + "\n");
            return;
        }

        server.partChannel(channel, nickname);
        send("OK PART You left " + channel + "\n");

        // broadcast to everyone in the channel
        server.broadcastToChannel(channel, "PART " + channel + " " + nickname + "\n", null);
    }

    // function to send a private message to another user
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

    // function to send a message to a channel
    private void handleChan(String args) {
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            send("ERROR 400 Usage: CHAN <channel> <message>\n");
            return;
        }

        String channel = parts[0].trim();
        String message = parts[1];

        // error handling for channel, check if the user is in the channel
        if (!server.isInChannel(channel, nickname)) {
            send("ERROR 404 You are not in " + channel + "\n");
            return;
        }

        // broadcast to everyone else in the channel
        server.broadcastToChannel(channel, "CHAN " + channel + " " + nickname + " " + message + "\n", nickname);
        send("OK CHAN Message sent to " + channel + "\n");
    }

    // function to handle battleship game commands
    private void handleGame(String args) {
        String[] parts = args.split(" ", 2);
        String subCmd = parts[0].toUpperCase();
        String param = parts.length > 1 ? parts[1] : "";

        switch (subCmd) {
            case "CHALLENGE":
                // challenge another user to a game
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
                // accept a challenge
                ClientHandler challenger = server.getClient(param);
                if (challenger == null) {
                    send("ERROR User not found\n");
                } else {
                    server.startGame(challenger, this);
                }
                break;

            case "PLACE":
                // place a ship on the board
                GameSession setupGame = server.getGame(this);
                if (setupGame == null) {
                    send("ERROR You are not in a game\n");
                    return;
                }
                // need correct parameters
                String[] placeParts = param.split(" ");
                if (placeParts.length < 2) {
                    send("ERROR Usage: /game place <coord> <H/V>\n");
                    return;
                }
                String result = setupGame.placeShip(this, placeParts[0], placeParts[1]);
                if (result.startsWith("ERROR")) {
                    send(result + "\n");
                } else if (result.equals("READY")) {
                    // both player setups are done
                    ClientHandler p1 = setupGame.getPlayer1();
                    ClientHandler p2 = setupGame.getPlayer2();
                    p1.send("GAME_START Game Started! Your turn.\n" + setupGame.getRenderedBoard(p1));
                    p2.send("GAME_START Game Started! Opponent's turn.\n" + setupGame.getRenderedBoard(p2));
                } else if (result.startsWith("PLACED")) {
                    // ship placed, ask for the next one
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
                // fire a shot
                GameSession game = server.getGame(this);
                if (game == null) {
                    send("ERROR You are not in a game\n");
                    return;
                }
                String fireResult = game.processMove(this, param);
                if (fireResult.startsWith("ERROR")) {
                    send(fireResult + "\n");
                } else {
                    // valid move
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
                // give up
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

    // list available channels
    private void handleList(String args) {
        java.util.List<String> channels = server.getChannelList();
        if (channels.isEmpty()) {
            send("CHANLIST No channels available\n");
        } else {
            send("CHANLIST " + String.join(" ", channels) + "\n");
        }
    }

    // function that list users in a channel or all users
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

    // function to handle file transfer requests
    private void handleFile(String args) {
        // expected: FILE <user> <filename> <size> [checksum]
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

        // notify target that a file is coming
        String offerMsg = "FILEOFFER " + nickname + " " + filename + " " + size;
        if (checksum != null) {
            offerMsg += " " + checksum;
        }
        targetClient.send(offerMsg + "\n");

        // tell sender to start sending data
        send("OK FILE Send file data now\n");

        try {
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int remaining = size;
            int bytesRead;

            // read exactly 'size' bytes from the stream
            while (remaining > 0) {
                int toRead = Math.min(remaining, data.length);
                bytesRead = in.read(data, 0, toRead);
                if (bytesRead == -1)
                    break;
                buffer.write(data, 0, bytesRead);
                remaining -= bytesRead;
            }

            byte[] fileData = buffer.toByteArray();

            // forward the data to the target client
            targetClient.sendFileData(fileData);

            send("OK FILE File sent to " + target + "\n");

        } catch (IOException e) {
            send("ERROR 500 File transfer failed: " + e.getMessage() + "\n");
        }
    }

    // function to handle user quitting
    private void handleQuit(String message) {
        String quitMsg = message.isEmpty() ? "Client disconnected" : message;
        send("OK QUIT " + quitMsg + "\n");
        running = false;
    }

    // helper to send a message to this client
    public void send(String message) {
        if (writer != null) {
            writer.print(message);
            writer.flush();
        }
    }

    // helper to send binary file data to this client
    public void sendFileData(byte[] fileData) throws IOException {
        OutputStream out = socket.getOutputStream();
        // send a header first so the client knows what's coming
        String header = "FILEDATA " + fileData.length + "\n";
        out.write(header.getBytes());
        out.write(fileData);
        out.flush();
    }

    // clean up resources when the client disconnects
    public void disconnect() {
        running = false;

        if (nickname != null) {
            System.out.println("[CLIENT " + nickname + "] Disconnected");

            // remove them from everything
            server.removeFromAllChannels(nickname);
            server.unregisterClient(nickname);
            server.broadcastQuit(nickname, "Disconnected");

            // if they were in a game, forfeit
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
