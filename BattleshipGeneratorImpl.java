import java.util.Random;

public class BattleshipGeneratorImpl implements BattleshipGenerator {
    private static final int BOARD_SIZE = 10;
    private static final int TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE;
    private static final int[] SHIP_SIZES = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
    private static final char MAST = '#';
    private static final char WATER = '.';

    private final Random random;

    public BattleshipGeneratorImpl() {
        this.random = new Random();
    }

    @Override
    public String generateMap() {
        char[][] board;
        boolean valid;
        int attempts = 0;

        do {
            board = new char[BOARD_SIZE][BOARD_SIZE];
            initializeBoard(board);
            valid = placeAllShips(board);
            attempts++;
        } while (!valid && attempts < 1000);

        if (!valid) {
            return createFallbackBoard();
        }

        return convertToString(board);
    }

    private void initializeBoard(char[][] board) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = WATER;
            }
        }
    }

    private boolean placeAllShips(char[][] board) {
        int[] sizes = SHIP_SIZES.clone();
        shuffleArray(sizes);

        for (int shipSize : sizes) {
            if (!placeShip(board, shipSize)) {
                return false;
            }
        }
        return true;
    }

    private boolean placeShip(char[][] board, int size) {
        int attempts = 0;
        final int MAX_ATTEMPTS = 1000;

        while (attempts < MAX_ATTEMPTS) {
            int row = random.nextInt(BOARD_SIZE);
            int col = random.nextInt(BOARD_SIZE);
            boolean horizontal = random.nextBoolean();

            if (canPlaceShip(board, row, col, size, horizontal)) {
                placeShipOnBoard(board, row, col, size, horizontal);
                return true;
            }
            attempts++;
        }
        return false;
    }

    private boolean canPlaceShip(char[][] board, int row, int col, int size, boolean horizontal) {
        // Sprawdź czy statek mieści się na planszy
        if (horizontal) {
            if (col + size > BOARD_SIZE) return false;
        } else {
            if (row + size > BOARD_SIZE) return false;
        }

        // Sprawdź czy pola są wolne i nie stykają się z innymi statkami
        for (int i = 0; i < size; i++) {
            int currentRow = horizontal ? row : row + i;
            int currentCol = horizontal ? col + i : col;

            // Sprawdź samo pole
            if (board[currentRow][currentCol] != WATER) {
                return false;
            }

            // Sprawdź wszystkich sąsiadów (włącznie z rogami)
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = currentRow + dr;
                    int nc = currentCol + dc;

                    if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE) {
                        if (board[nr][nc] == MAST) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private void placeShipOnBoard(char[][] board, int row, int col, int size, boolean horizontal) {
        for (int i = 0; i < size; i++) {
            int currentRow = horizontal ? row : row + i;
            int currentCol = horizontal ? col + i : col;
            board[currentRow][currentCol] = MAST;
        }
    }

    private String convertToString(char[][] board) {
        StringBuilder result = new StringBuilder(TOTAL_CELLS);
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                result.append(board[i][j]);
            }
        }
        return result.toString();
    }

    private void shuffleArray(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            int temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    private String createFallbackBoard() {
        // Standardowa układanka z generatora
        char[][] board = new char[BOARD_SIZE][BOARD_SIZE];
        initializeBoard(board);

        // Statek 4-masztowy
        placeShipOnBoard(board, 0, 1, 4, true);

        // Statki 3-masztowe
        placeShipOnBoard(board, 2, 0, 3, true);
        placeShipOnBoard(board, 4, 3, 3, false);

        // Statki 2-masztowe
        placeShipOnBoard(board, 6, 1, 2, true);
        placeShipOnBoard(board, 8, 6, 2, false);
        placeShipOnBoard(board, 3, 8, 2, false);

        // Statki 1-masztowe
        board[0][7] = MAST;
        board[2][5] = MAST;
        board[5][0] = MAST;
        board[9][9] = MAST;

        return convertToString(board);
    }
}