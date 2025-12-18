import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BattleshipGame {
    private static final int BOARD_SIZE = 10;
    private static final char MAST = '#';
    private static final char WATER = '.';
    private static final char MISS = '~';
    private static final char HIT = '@';
    private static final char UNKNOWN = '?';

    private String mode;
    private int port;
    private String mapFile;
    private String host;

    private char[][] myBoard;
    private char[][] enemyBoard;
    private char[][] enemyBoardDisplay;
    private char[][] myBoardWithShots;

    private boolean isMyTurn = false;
    private boolean gameEnded = false;
    private boolean iWon = false;

    private List<String> moveHistory = new ArrayList<>();
    private String lastSentMessage = null;
    private AtomicInteger retryCount = new AtomicInteger(0);
    private ScheduledExecutorService timeoutScheduler;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private Random random = new Random();

    public static void main(String[] args) {
        BattleshipGame game = new BattleshipGame();
        game.parseArgs(args);
        game.loadMap();
        game.initializeBoards();
        game.start();
    }

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-mode":
                    mode = args[++i];
                    if (!mode.equals("server") && !mode.equals("client")) {
                        System.err.println("Błędny tryb. Użyj: -mode [server|client]");
                        System.exit(1);
                    }
                    break;
                case "-port":
                    try {
                        port = Integer.parseInt(args[++i]);
                        if (port < 1 || port > 65535) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Błędny port. Użyj: -port N (1-65535)");
                        System.exit(1);
                    }
                    break;
                case "-map":
                    mapFile = args[++i];
                    break;
                case "-host":
                    host = args[++i];
                    break;
                default:
                    System.err.println("Nieznany argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (mode == null) {
            System.err.println("Brak trybu. Użyj: -mode [server|client]");
            System.exit(1);
        }

        if (port == 0) {
            System.err.println("Brak portu. Użyj: -port N");
            System.exit(1);
        }

        if (mode.equals("client") && host == null) {
            System.err.println("Tryb client wymaga parametru -host");
            System.exit(1);
        }
    }

    private void loadMap() {
        if (mapFile != null) {
            try {
                String content = Files.readString(Paths.get(mapFile));
                content = content.replaceAll("\\s+", "");

                if (content.length() != BOARD_SIZE * BOARD_SIZE) {
                    System.err.println("Nieprawidłowy rozmiar mapy w pliku. Oczekiwano " +
                            (BOARD_SIZE * BOARD_SIZE) + " znaków, otrzymano " + content.length());
                    System.exit(1);
                }

                myBoard = new char[BOARD_SIZE][BOARD_SIZE];
                for (int i = 0; i < BOARD_SIZE; i++) {
                    for (int j = 0; j < BOARD_SIZE; j++) {
                        char c = content.charAt(i * BOARD_SIZE + j);
                        if (c != MAST && c != WATER) {
                            System.err.println("Nieprawidłowy znak w mapie: " + c);
                            System.exit(1);
                        }
                        myBoard[i][j] = c;
                    }
                }

                if (!isValidBoard(myBoard)) {
                    System.err.println("Nieprawidłowa mapa: statki stykają się rogami");
                    System.exit(1);
                }

            } catch (IOException e) {
                System.err.println("Nie można wczytać mapy z pliku: " + mapFile);
                System.err.println("Błąd: " + e.getMessage());
                System.exit(1);
            }
        } else {
            BattleshipGenerator generator = BattleshipGenerator.defaultInstance();
            String map = generator.generateMap();
            myBoard = new char[BOARD_SIZE][BOARD_SIZE];
            for (int i = 0; i < BOARD_SIZE; i++) {
                for (int j = 0; j < BOARD_SIZE; j++) {
                    myBoard[i][j] = map.charAt(i * BOARD_SIZE + j);
                }
            }
        }

        int mastCount = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (myBoard[i][j] == MAST) mastCount++;
            }
        }

        if (mastCount != 20) {
            System.err.println("Nieprawidłowa liczba masztów: " + mastCount + " (powinno być 20)");
            System.exit(1);
        }
    }

    private boolean isValidBoard(char[][] board) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == MAST) {
                    for (int di = -1; di <= 1; di++) {
                        for (int dj = -1; dj <= 1; dj++) {
                            if (di == 0 && dj == 0) continue;
                            int ni = i + di;
                            int nj = j + dj;
                            if (ni >= 0 && ni < BOARD_SIZE && nj >= 0 && nj < BOARD_SIZE) {
                                if (board[ni][nj] == MAST) {
                                    if (Math.abs(di) + Math.abs(dj) == 2) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private void initializeBoards() {
        enemyBoard = new char[BOARD_SIZE][BOARD_SIZE];
        enemyBoardDisplay = new char[BOARD_SIZE][BOARD_SIZE];
        myBoardWithShots = new char[BOARD_SIZE][BOARD_SIZE];

        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                enemyBoard[i][j] = WATER;
                enemyBoardDisplay[i][j] = UNKNOWN;
                myBoardWithShots[i][j] = myBoard[i][j];
            }
        }
    }

    private void start() {
        System.out.println("=== GRA W OKRĘTY ===");
        System.out.println("Tryb: " + mode);
        System.out.println("Moja mapa:");
        printBoard(myBoard, false);
        System.out.println();

        timeoutScheduler = Executors.newScheduledThreadPool(1);

        if (mode.equals("server")) {
            runAsServer();
        } else {
            runAsClient();
        }
    }

    private void runAsServer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serwer nasłuchuje na porcie " + port + "...");
            socket = serverSocket.accept();
            System.out.println("Połączono z klientem");

            setupStreams();
            play(false);

        } catch (IOException e) {
            System.err.println("Błąd serwera: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void runAsClient() {
        try {
            System.out.println("Łączenie z " + host + ":" + port + "...");
            socket = new Socket(host, port);
            System.out.println("Połączono z serwerem");

            setupStreams();
            play(true);

        } catch (IOException e) {
            System.err.println("Nie można połączyć się z serwerem: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void setupStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void play(boolean isClient) {
        isMyTurn = isClient;

        if (isClient) {
            sendFirstMove();
        }

        startTimeoutChecker();

        Scanner scanner = new Scanner(System.in);

        while (!gameEnded) {
            if (isMyTurn) {
                System.out.println("\n=== TWÓJ RUCH ===");
                System.out.println("Mapa przeciwnika:");
                printBoard(enemyBoardDisplay, true);

                String coordinates = getShotCoordinates(scanner);
                sendShot(coordinates);
                isMyTurn = false;
            } else {
                System.out.println("\n=== RUCH PRZECIWNIKA ===");
                System.out.println("Oczekiwanie na ruch przeciwnika...");
                receiveAndProcessMove();
            }
        }

        scanner.close();
        showResults();
    }

    private String getShotCoordinates(Scanner scanner) {
        while (true) {
            System.out.print("Podaj współrzędne strzału (np. A5): ");
            String input = scanner.nextLine().trim().toUpperCase();

            if (isValidCoordinates(input)) {
                int[] coords = parseCoordinates(input);
                char current = enemyBoardDisplay[coords[0]][coords[1]];
                if (current == MISS || current == HIT) {
                    System.out.println("Już strzelano w to miejsce! Wybierz inne.");
                } else {
                    return input;
                }
            } else {
                System.out.println("Nieprawidłowe współrzędne. Przykład: A5, J10");
            }
        }
    }

    private void sendFirstMove() {
        String coordinates = getRandomCoordinates();
        String message = "start;" + coordinates;
        sendMessage(message);
    }

    private void sendShot(String coordinates) {
        String message = "strzał;" + coordinates;
        sendMessage(message);
    }

    private void sendMessage(String message) {
        out.println(message);
        lastSentMessage = message;
        moveHistory.add("Wysłano: " + message);
        System.out.println("Wysłano: " + message);
    }

    private void receiveAndProcessMove() {
        try {
            String response = in.readLine();
            if (response == null) {
                handleCommunicationError();
                return;
            }

            moveHistory.add("Otrzymano: " + response);
            System.out.println("Otrzymano: " + response);
            retryCount.set(0);

            processResponse(response);

        } catch (SocketTimeoutException e) {
            handleCommunicationError();
        } catch (IOException e) {
            System.err.println("Błąd odczytu: " + e.getMessage());
            handleCommunicationError();
        }
    }

    private void processResponse(String response) {
        String[] parts = response.split(";", 2);
        String command = parts[0];

        switch (command) {
            case "start":
                processStartCommand(parts);
                break;
            case "pudło":
                processResultCommand(parts, false, false);
                break;
            case "trafiony":
                processResultCommand(parts, true, false);
                break;
            case "trafiony zatopiony":
                processResultCommand(parts, true, true);
                break;
            case "ostatni zatopiony":
                processLastSunkCommand();
                break;
            default:
                System.err.println("Nieznana komenda: " + command);
                handleCommunicationError();
        }
    }

    private void processStartCommand(String[] parts) {
        if (parts.length < 2) {
            handleInvalidMessage("start;");
            return;
        }

        String coordinates = parts[1];
        String result = processIncomingShot(coordinates);
        sendMessage(result);

        if (result.equals("ostatni zatopiony")) {
            gameEnded = true;
            iWon = false;
        } else {
            isMyTurn = true;
        }
    }

    private void processResultCommand(String[] parts, boolean isHit, boolean isSunk) {
        if (parts.length < 2) {
            handleInvalidMessage(String.join(";", parts));
            return;
        }

        String coordinates = parts[1];
        updateEnemyBoard(coordinates, isHit, isSunk);

        if (!gameEnded) {
            isMyTurn = true;
        }
    }

    private void processLastSunkCommand() {
        gameEnded = true;
        iWon = true;
        System.out.println("Zatopiłeś ostatni statek przeciwnika!");
    }

    private String processIncomingShot(String coordinates) {
        int[] coords = parseCoordinates(coordinates);
        int row = coords[0];
        int col = coords[1];

        if (myBoardWithShots[row][col] == MAST) {
            myBoardWithShots[row][col] = HIT;

            if (isShipSunk(row, col, myBoardWithShots)) {
                markAroundSunkShip(row, col, myBoardWithShots);

                if (areAllShipsSunk(myBoardWithShots)) {
                    return "ostatni zatopiony";
                } else {
                    return "trafiony zatopiony;" + coordinates;
                }
            } else {
                return "trafiony;" + coordinates;
            }
        } else {
            myBoardWithShots[row][col] = MISS;
            return "pudło;" + coordinates;
        }
    }

    private void updateEnemyBoard(String coordinates, boolean isHit, boolean isSunk) {
        int[] coords = parseCoordinates(coordinates);
        int row = coords[0];
        int col = coords[1];

        if (isHit) {
            enemyBoardDisplay[row][col] = HIT;
            enemyBoard[row][col] = MAST;

            if (isSunk) {
                markAroundSunkShip(row, col, enemyBoardDisplay);
            }
        } else {
            enemyBoardDisplay[row][col] = MISS;
        }

        System.out.println("Mapa przeciwnika po strzale:");
        printBoard(enemyBoardDisplay, true);
    }

    private boolean isShipSunk(int startRow, int startCol, char[][] board) {
        Set<String> shipCells = new HashSet<>();
        findConnectedCells(startRow, startCol, board, shipCells, MAST, HIT);

        for (String cell : shipCells) {
            int[] coords = parseCoordinates(cell);
            if (board[coords[0]][coords[1]] == MAST) {
                return false;
            }
        }

        return true;
    }

    private void findConnectedCells(int row, int col, char[][] board, Set<String> visited, char... validCells) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) {
            return;
        }

        String key = (char)('A' + col) + "" + (row + 1);
        if (visited.contains(key)) {
            return;
        }

        char cell = board[row][col];
        boolean isValid = false;
        for (char valid : validCells) {
            if (cell == valid) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            return;
        }

        visited.add(key);

        findConnectedCells(row - 1, col, board, visited, validCells); // góra
        findConnectedCells(row + 1, col, board, visited, validCells); // dół
        findConnectedCells(row, col - 1, board, visited, validCells); // lewo
        findConnectedCells(row, col + 1, board, visited, validCells); // prawo
    }

    private void markAroundSunkShip(int row, int col, char[][] board) {
        Set<String> shipCells = new HashSet<>();
        findConnectedCells(row, col, board, shipCells, HIT);

        for (String cell : shipCells) {
            int[] coords = parseCoordinates(cell);
            int r = coords[0];
            int c = coords[1];

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = r + dr;
                    int nc = c + dc;

                    if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE) {
                        if (board[nr][nc] == UNKNOWN || board[nr][nc] == WATER) {
                            board[nr][nc] = MISS;
                        }
                    }
                }
            }
        }
    }

    private boolean areAllShipsSunk(char[][] board) {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == MAST) {
                    return false;
                }
            }
        }
        return true;
    }

    private void startTimeoutChecker() {
        timeoutScheduler.scheduleAtFixedRate(() -> {
            if (lastSentMessage != null && !isMyTurn && !gameEnded) {
                handleCommunicationError();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void handleCommunicationError() {
        int attempts = retryCount.incrementAndGet();

        if (attempts >= 3) {
            System.err.println("\nBłąd komunikacji");
            cleanup();
            System.exit(1);
        }

        System.err.println("Błąd komunikacji, ponawiam próbę " + attempts + "/3");

        if (lastSentMessage != null) {
            out.println(lastSentMessage);
            System.out.println("Ponowne wysłanie: " + lastSentMessage);
        }
    }

    private void handleInvalidMessage(String message) {
        System.err.println("Nieprawidłowa wiadomość: " + message);
        handleCommunicationError();
    }

    private String getRandomCoordinates() {
        while (true) {
            char letter = (char) ('A' + random.nextInt(BOARD_SIZE));
            int number = 1 + random.nextInt(BOARD_SIZE);
            String coordinates = "" + letter + number;

            int[] coords = parseCoordinates(coordinates);
            if (enemyBoardDisplay[coords[0]][coords[1]] == UNKNOWN) {
                return coordinates;
            }
        }
    }

    private boolean isValidCoordinates(String coordinates) {
        if (coordinates.length() < 2 || coordinates.length() > 3) {
            return false;
        }

        char letter = coordinates.charAt(0);
        if (letter < 'A' || letter > 'J') {
            return false;
        }

        try {
            String numberStr = coordinates.substring(1);
            int number = Integer.parseInt(numberStr);
            return number >= 1 && number <= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int[] parseCoordinates(String coordinates) {
        char letter = coordinates.charAt(0);
        String numberStr = coordinates.substring(1);
        int number = Integer.parseInt(numberStr);

        int row = number - 1;
        int col = letter - 'A';

        return new int[]{row, col};
    }

    private String coordinatesToString(int row, int col) {
        char letter = (char) ('A' + col);
        return "" + letter + (row + 1);
    }

    private void showResults() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println(iWon ? "WYGRANA!" : "PRZEGRANA!");
        System.out.println("=".repeat(50));

        System.out.println("\nMapa przeciwnika:");
        if (iWon) {
            printBoard(enemyBoard, false);
        } else {
            printBoard(enemyBoardDisplay, true);
        }

        System.out.println("\nMoja mapa po grze:");
        printBoard(myBoardWithShots, false);

        System.out.println("\nHistoria komunikacji:");
        for (String move : moveHistory) {
            System.out.println(move);
        }

        System.out.println("\nKoniec gry!");
    }

    private void printBoard(char[][] board, boolean showCoordinates) {
        if (showCoordinates) {
            System.out.print("  ");
            for (char c = 'A'; c < 'A' + BOARD_SIZE; c++) {
                System.out.print(c);
            }
            System.out.println();
        }

        for (int i = 0; i < BOARD_SIZE; i++) {
            if (showCoordinates) {
                System.out.printf("%2d", i + 1);
            }
            for (int j = 0; j < BOARD_SIZE; j++) {
                System.out.print(board[i][j]);
            }
            if (showCoordinates) {
                System.out.printf(" %2d", i + 1);
            }
            System.out.println();
        }

        if (showCoordinates) {
            System.out.print("  ");
            for (char c = 'A'; c < 'A' + BOARD_SIZE; c++) {
                System.out.print(c);
            }
            System.out.println();
        }
    }

    private void cleanup() {
        try {
            if (timeoutScheduler != null) {
                timeoutScheduler.shutdownNow();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }
}