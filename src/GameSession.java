import java.util.*;

public class GameSession {
    private final ClientHandler player1;
    private final ClientHandler player2;
    private final char[][] board1; // Player 1's board (ships + P2's shots)
    private final char[][] board2; // Player 2's board (ships + P1's shots)
    private final char[][] view1; // What P1 sees of P2's board (hits/misses)
    private final char[][] view2; // What P2 sees of P1's board (hits/misses)
    private boolean p1Turn;
    private GameState state;
    private int p1ShipsPlaced = 0;
    private int p2ShipsPlaced = 0;
    private static final int[] SHIP_LENGTHS = { 5, 4, 3, 3, 2 };
    private static final String[] SHIP_NAMES = { "Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer" };

    public enum GameState {
        SETUP, PLAYING, FINISHED
    }

    public GameSession(ClientHandler p1, ClientHandler p2) {
        this.player1 = p1;
        this.player2 = p2;
        this.board1 = new char[10][10];
        this.board2 = new char[10][10];
        this.view1 = new char[10][10];
        this.view2 = new char[10][10];
        this.p1Turn = true; // Player 1 starts
        this.state = GameState.SETUP;

        initializeBoard(board1);
        initializeBoard(board2);
        initializeBoard(view1);
        initializeBoard(view2);
    }

    private void initializeBoard(char[][] board) {
        for (int i = 0; i < 10; i++) {
            Arrays.fill(board[i], '~'); // Water
        }
    }

    public String placeShip(ClientHandler player, String coord, String orientation) {
        if (state != GameState.SETUP)
            return "ERROR Game is not in setup phase";

        boolean isP1 = (player == player1);
        int shipsPlaced = isP1 ? p1ShipsPlaced : p2ShipsPlaced;

        if (shipsPlaced >= 5)
            return "ERROR All ships placed. Waiting for opponent.";

        int len = SHIP_LENGTHS[shipsPlaced];
        char[][] board = isP1 ? board1 : board2;

        int[] parsed = parseCoord(coord);
        if (parsed == null)
            return "ERROR Invalid coordinate";
        int r = parsed[0];
        int c = parsed[1];
        boolean horizontal = orientation.equalsIgnoreCase("H");

        if (!canPlace(board, r, c, len, horizontal)) {
            return "ERROR Invalid placement (overlap or out of bounds)";
        }

        // Place ship
        for (int i = 0; i < len; i++) {
            if (horizontal)
                board[r][c + i] = 'S';
            else
                board[r + i][c] = 'S';
        }

        if (isP1)
            p1ShipsPlaced++;
        else
            p2ShipsPlaced++;

        // Check if both ready
        if (p1ShipsPlaced == 5 && p2ShipsPlaced == 5) {
            state = GameState.PLAYING;
            return "READY";
        }

        return "PLACED " + (isP1 ? p1ShipsPlaced : p2ShipsPlaced); // Return next ship index
    }

    public String getNextShipName(ClientHandler player) {
        int idx = (player == player1) ? p1ShipsPlaced : p2ShipsPlaced;
        if (idx >= 5)
            return "WAITING";
        return SHIP_NAMES[idx] + " (" + SHIP_LENGTHS[idx] + ")";
    }

    private boolean canPlace(char[][] board, int r, int c, int len, boolean horizontal) {
        if (horizontal) {
            if (c + len > 10)
                return false;
            for (int i = 0; i < len; i++)
                if (board[r][c + i] != '~')
                    return false;
        } else {
            if (r + len > 10)
                return false;
            for (int i = 0; i < len; i++)
                if (board[r + i][c] != '~')
                    return false;
        }
        return true;
    }

    public String processMove(ClientHandler player, String coord) {
        if (state != GameState.PLAYING)
            return "ERROR Game is not active";
        if ((player == player1 && !p1Turn) || (player == player2 && p1Turn)) {
            return "ERROR It is not your turn!";
        }

        int[] parsed = parseCoord(coord);
        if (parsed == null)
            return "ERROR Invalid coordinate (e.g., A5)";
        int r = parsed[0];
        int c = parsed[1];

        char[][] targetBoard = (player == player1) ? board2 : board1;
        char[][] myView = (player == player1) ? view1 : view2;

        if (myView[r][c] != '~')
            return "ERROR You already fired there!";

        boolean hit = targetBoard[r][c] == 'S';
        myView[r][c] = hit ? 'X' : 'O';
        targetBoard[r][c] = hit ? 'X' : 'O'; // Update actual board too so opponent sees damage

        p1Turn = !p1Turn; // Switch turn

        if (checkWin((player == player1) ? board2 : board1)) {
            state = GameState.FINISHED;
            return "WIN";
        }

        return hit ? "HIT" : "MISS";
    }

    private boolean checkWin(char[][] board) {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (board[i][j] == 'S')
                    return false; // Ship remaining
            }
        }
        return true;
    }

    private int[] parseCoord(String coord) {
        if (coord.length() < 2 || coord.length() > 3)
            return null;
        coord = coord.toUpperCase();
        char rowChar = coord.charAt(0);
        if (rowChar < 'A' || rowChar > 'J')
            return null;
        int row = rowChar - 'A';

        try {
            int col = Integer.parseInt(coord.substring(1)) - 1;
            if (col < 0 || col > 9)
                return null;
            return new int[] { row, col };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getRenderedBoard(ClientHandler player) {
        StringBuilder sb = new StringBuilder();
        char[][] myShips = (player == player1) ? board1 : board2;
        char[][] myShots = (player == player1) ? view1 : view2;
        boolean isP1 = (player == player1);

        // Header
        sb.append("\n   YOUR SHIPS                         ENEMY WATERS                     GAME INFO\n");
        sb.append("   1  2  3  4  5  6  7  8  9  10      1  2  3  4  5  6  7  8  9  10   --------------------------\n");

        for (int i = 0; i < 10; i++) {
            char rowLabel = (char) ('A' + i);

            // Left Board (Ships)
            sb.append(rowLabel).append("  ");
            for (int j = 0; j < 10; j++) {
                sb.append(myShips[i][j]).append("  ");
            }

            // Spacer
            sb.append("   ");

            // Right Board (Shots)
            sb.append(rowLabel).append("  ");
            for (int j = 0; j < 10; j++) {
                sb.append(myShots[i][j]).append("  ");
            }

            // Side Panel Content
            sb.append("   ");
            sb.append(getSidePanelLine(i, player));

            sb.append("\n");
        }

        return sb.toString();
    }

    private String getSidePanelLine(int row, ClientHandler player) {
        boolean isP1 = (player == player1);
        boolean myTurn = (isP1 && p1Turn) || (!isP1 && !p1Turn);
        int myPlaced = isP1 ? p1ShipsPlaced : p2ShipsPlaced;
        int oppPlaced = isP1 ? p2ShipsPlaced : p1ShipsPlaced;

        switch (row) {
            case 0:
                return "Status: "
                        + (state == GameState.SETUP ? "SETUP PHASE" : (myTurn ? "YOUR TURN" : "OPPONENT'S TURN"));
            case 1:
                return "";
            case 2:
                return "COMMANDS:";
            case 3:
                return "/fire <coord>   - Attack (e.g. A5)";
            case 4:
                return "/place <coord> <H/V>";
            case 5:
                return "  (e.g. A1 H)   - Top-Left Edge";
            case 6:
                return "/surrender      - Give up";
            case 7:
                return "";
            case 8:
                if (state == GameState.SETUP) {
                    return "TO PLACE: You " + (5 - myPlaced) + " | Enemy " + (5 - oppPlaced);
                } else {
                    return "ALIVE:    You " + countShips((isP1 ? board1 : board2)) + " | Enemy "
                            + countShips((isP1 ? board2 : board1));
                }
            default:
                return "";
        }
    }

    private int countShips(char[][] board) {
        int count = 0;
        // This is a rough count of segments, ideally we track actual ships alive
        // For now, just counting segments is fine or we can omit this if too complex
        for (int i = 0; i < 10; i++)
            for (int j = 0; j < 10; j++)
                if (board[i][j] == 'S')
                    count++;
        return count;
    }

    public boolean isActive() {
        return state != GameState.FINISHED;
    }

    public ClientHandler getPlayer1() {
        return player1;
    }

    public ClientHandler getPlayer2() {
        return player2;
    }

    public ClientHandler getOpponent(ClientHandler p) {
        return p == player1 ? player2 : player1;
    }
}
