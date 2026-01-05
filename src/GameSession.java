import java.util.*;

public class GameSession {
    /**
     * This is a class that manages a single game of battleship between two players.
     * It keeps track of the boards, whose turn it is, and who's winning.
     */

    private final ClientHandler player1; // The challenger
    private final ClientHandler player2; // The challenged

    // The boards: 10x10 grids of characters
    // '~' = Water
    // 'S' = Ship (hidden from opponent)
    // 'X' = Hit
    // 'O' = Miss
    private final char[][] board1; // Player 1's actual board (where their ships are)
    private final char[][] board2; // Player 2's actual board (where their ships are)

    // The views: What each player sees of the opponent's board
    // They only see hits ('X') and misses ('O'), not the ships ('S')
    private final char[][] view1; // What P1 sees of P2's board
    private final char[][] view2; // What P2 sees of P1's board

    private boolean p1Turn; // True if it's Player 1's turn
    private GameState state; // Current phase of the game

    // An integer to track setup progress
    private int p1ShipsPlaced = 0;
    private int p2ShipsPlaced = 0;

    // A list to track the ships themselves to know when one is sunk
    private List<Ship> p1Ships = new ArrayList<>();
    private List<Ship> p2Ships = new ArrayList<>();

    // Game Constants
    private static final int[] SHIP_LENGTHS = { 5, 4, 3, 3, 2 }; // Sizes of the 5 ships
    private static final String[] SHIP_NAMES = { "Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer" };

    // Inner class to represent a single ship
    private class Ship {
        List<int[]> coords = new ArrayList<>(); // a list of coords where the ship is located

        void addCoord(int r, int c) {
            // function to add the coord to the list
            coords.add(new int[] { r, c });
        }

        // function to check if this ship is completely destroyed, return t or f
        boolean isSunk(char[][] board) {
            for (int[] p : coords) {
                // If any part of the ship is still 'S' (not 'X'), it's afloat
                if (board[p[0]][p[1]] == 'S')
                    return false; // not sunk
            }
            return true; // sunk
        }
    }

    // The phases of the game
    public enum GameState {
        SETUP, // preparation phase for placing ships
        PLAYING, // firing phase
        FINISHED // game over
    }

    // a constructor that starts a new session
    public GameSession(ClientHandler p1, ClientHandler p2) {
        // initialize variables
        this.player1 = p1;
        this.player2 = p2;
        this.board1 = new char[10][10];
        this.board2 = new char[10][10];
        this.view1 = new char[10][10];
        this.view2 = new char[10][10];
        this.p1Turn = true; // player 1 turn starts
        this.state = GameState.SETUP;

        // initialize boards and views
        initializeBoard(board1);
        initializeBoard(board2);
        initializeBoard(view1);
        initializeBoard(view2);
    }

    // function to fill a board with '~'
    private void initializeBoard(char[][] board) {
        for (int i = 0; i < 10; i++) {
            Arrays.fill(board[i], '~');
        }
    }

    // function to handle a player trying to place a ship
    public String placeShip(ClientHandler player, String coord, String orientation) {
        if (state != GameState.SETUP)
            // if the game is not in setup phase, return error
            return "ERROR Game is not in setup phase";

        boolean isP1 = (player == player1);
        // check if the player has placed all 5 ships
        int shipsPlaced = isP1 ? p1ShipsPlaced : p2ShipsPlaced;

        if (shipsPlaced >= 5)
            return "ERROR All ships placed. Waiting for opponent.";

        // which ship are we placing
        int len = SHIP_LENGTHS[shipsPlaced];
        char[][] board = isP1 ? board1 : board2;

        // parse the coordinate (e.g., "A1" -> 0, 0)
        int[] parsed = parseCoord(coord);
        if (parsed == null)
            return "ERROR Invalid coordinate";
        int r = parsed[0];
        int c = parsed[1];
        boolean horizontal = orientation.equalsIgnoreCase("H");

        // check if it fits
        if (!canPlace(board, r, c, len, horizontal)) {
            return "ERROR Invalid placement (overlap or out of bounds)";
        }

        // place the ship on the board
        Ship newShip = new Ship();
        for (int i = 0; i < len; i++) {
            int rPos, cPos;
            if (horizontal) {
                rPos = r;
                cPos = c + i;
            } else {
                rPos = r + i;
                cPos = c;
            }
            board[rPos][cPos] = 'S'; // mark as Ship
            newShip.addCoord(rPos, cPos);
        }

        // add to list of ships
        if (isP1)
            p1Ships.add(newShip);
        else
            p2Ships.add(newShip);

        // increment counter
        if (isP1)
            p1ShipsPlaced++;
        else
            p2ShipsPlaced++;

        // check if both players are done placing
        if (p1ShipsPlaced == 5 && p2ShipsPlaced == 5) {
            state = GameState.PLAYING;
            return "READY"; // let the battle begin!
        }

        return "PLACED " + (isP1 ? p1ShipsPlaced : p2ShipsPlaced); // success, but waiting for more
    }

    // tells the player which ship they need to place next
    public String getNextShipName(ClientHandler player) {
        int idx = (player == player1) ? p1ShipsPlaced : p2ShipsPlaced;
        if (idx >= 5)
            return "WAITING";
        return SHIP_NAMES[idx] + " (" + SHIP_LENGTHS[idx] + ")";
    }

    // checks if a ship fits at the given location
    private boolean canPlace(char[][] board, int r, int c, int len, boolean horizontal) {
        if (horizontal) {
            if (c + len > 10)
                return false; // off the right edge
            for (int i = 0; i < len; i++)
                if (board[r][c + i] != '~')
                    return false; // overlaps another ship
        } else {
            if (r + len > 10)
                return false; // off the bottom edge
            for (int i = 0; i < len; i++)
                if (board[r + i][c] != '~')
                    return false; // overlaps another ship
        }
        return true;
    }

    // handles a player firing a shot
    public String processMove(ClientHandler player, String coord) {
        if (state != GameState.PLAYING)
            return "ERROR Game is not active";

        // check if it's their turn
        if ((player == player1 && !p1Turn) || (player == player2 && p1Turn)) {
            return "ERROR It is not your turn!";
        }

        int[] parsed = parseCoord(coord);
        if (parsed == null)
            return "ERROR Invalid coordinate (e.g., A5)";
        int r = parsed[0];
        int c = parsed[1];

        // determine target board and view
        char[][] targetBoard = (player == player1) ? board2 : board1;
        char[][] myView = (player == player1) ? view1 : view2;

        // check if already fired here
        if (myView[r][c] != '~')
            return "ERROR You already fired there!";

        // check for hit
        boolean hit = targetBoard[r][c] == 'S';

        // update the view and the actual board
        myView[r][c] = hit ? 'X' : 'O';
        targetBoard[r][c] = hit ? 'X' : 'O'; // update actual board too so opponent sees hits

        p1Turn = !p1Turn; // switch turn

        // check for victory
        if (checkWin((player == player1) ? board2 : board1)) {
            state = GameState.FINISHED;
            return "WIN";
        }

        return hit ? "HIT" : "MISS";
    }

    // check if all ships on a board are sunk
    private boolean checkWin(char[][] board) {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (board[i][j] == 'S')
                    return false; // found a ship part still standing
            }
        }
        return true; // no ships left
    }

    // helper to convert "A1" to [0, 0]
    private int[] parseCoord(String coord) {
        if (coord.length() < 2 || coord.length() > 3)
            return null;
        coord = coord.toUpperCase();
        char rowChar = coord.charAt(0);
        if (rowChar < 'A' || rowChar > 'J')
            return null;
        int row = rowChar - 'A';

        try {
            // 1-based to 0-based
            int col = Integer.parseInt(coord.substring(1)) - 1;
            if (col < 0 || col > 9)
                return null;
            return new int[] { row, col };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // generates the text-based UI for the game board
    public String getRenderedBoard(ClientHandler player) {
        StringBuilder sb = new StringBuilder();
        char[][] myShips = (player == player1) ? board1 : board2;
        char[][] myShots = (player == player1) ? view1 : view2;

        // header
        sb.append(
                "\n            YOUR SHIPS                           ENEMY WATERS                  GAME INFO\n");
        sb.append(
                "      1  2  3  4  5  6  7  8  9  10      1  2  3  4  5  6  7  8  9  10   --------------------------\n");

        for (int i = 0; i < 10; i++) {
            char rowLabel = (char) ('A' + i);

            // left board (my ships)
            sb.append("   ").append(rowLabel).append(" ");
            for (int j = 0; j < 10; j++) {
                sb.append(" ").append(myShips[i][j]).append(" ");
            }

            // spacer or formatting
            sb.append("   ");

            // right board (shots at the enemy)
            sb.append(rowLabel).append(" ");
            for (int j = 0; j < 10; j++) {
                sb.append(" ").append(myShots[i][j]).append(" ");
            }

            // side panel content (status, commands, etc.)
            sb.append("   ");
            sb.append(getSidePanelLine(i, player));

            sb.append("\n");
        }

        return sb.toString();
    }

    // helper to fill the side panel with quick info for convenience
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
                    return "ALIVE:    You " + countShips(isP1 ? p1Ships : p2Ships, isP1 ? board1 : board2) + " | Enemy "
                            + countShips(isP1 ? p2Ships : p1Ships, isP1 ? board2 : board1);
                }
            default:
                return "";
        }
    }

    // counts how many ships are still alive
    private int countShips(List<Ship> ships, char[][] board) {
        int count = 0;
        for (Ship s : ships) {
            if (!s.isSunk(board))
                count++;
        }
        return count;
    }

    // variable to check if the game is still going
    public boolean isActive() {
        return state != GameState.FINISHED;
    }

    public ClientHandler getPlayer1() {
        return player1;
    }

    public ClientHandler getPlayer2() {
        return player2;
    }

    // returns the opponent of a player
    public ClientHandler getOpponent(ClientHandler p) {
        return p == player1 ? player2 : player1;
    }
}
