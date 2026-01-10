import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class BattleshipClient {
    private String host;
    private int port;
    private String mapFile;
    private BufferedReader consoleReader;

    public BattleshipClient(String host, int port, String mapFile) {
        this.host = host;
        this.port = port;
        this.mapFile = mapFile;
        this.consoleReader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void start() throws IOException {
        Socket socket = new Socket(host, port);
        System.out.println("Połączono z serwerem " + host + ":" + port);

        GameBoard board = new GameBoard(mapFile);
        board.displayInitialBoard();

        NetworkProtocol protocol = new NetworkProtocol(socket);
        playGame(protocol, board);
    }

    private void playGame(NetworkProtocol protocol, GameBoard board) throws IOException {
        boolean gameOver = false;
        boolean myTurn = true;
        String lastResult = "";

        System.out.println("\n=== ROZPOCZĘCIE GRY ===\n");

        while (!gameOver) {
            if (myTurn) {
                System.out.println("\n--- TURA KLIENTA ---");
                board.displayOwnBoard();
                board.displayEnemyBoard(false);

                String shotCoord = getValidShot(board);
                System.out.println("Strzelam w: " + shotCoord);

                String message = "start;" + shotCoord;
                String response = protocol.sendAndReceive(message);

                if (response == null) {
                    System.out.println("Błąd komunikacji");
                    break;
                }

                String[] responseParts = response.split(";");
                String result = responseParts[0];
                String responseCoord = responseParts.length > 1 ? responseParts[1] : null;

                board.recordOurShot(shotCoord, result);

                System.out.println("Rezultat: " + result);

                if ("ostatni zatopiony".equals(result)) {
                    System.out.println("\n=== WYGRANA ===");
                    System.out.println("Przeciwnik zatopił twój ostatni statek");
                    displayResults(board, true);
                    gameOver = true;
                    break;
                } else if ("trafiony zatopiony".equals(result)) {
                    System.out.println("Trafiony i zatopiony");
                    myTurn = true;
                } else if ("trafiony".equals(result)) {
                    System.out.println("Trafiony");
                    myTurn = true;
                } else {
                    System.out.println("Pudło");
                    myTurn = false;
                }

                lastResult = result;
            } else {
                System.out.println("\n--- TURA SERWERA ---");
                board.displayOwnBoard();

                String message = protocol.sendAndReceive(null);
                if (message == null) {
                    System.out.println("Błąd komunikacji");
                    break;
                }

                String[] parts = message.split(";");
                String command = parts[0];
                String coord = parts.length > 1 ? parts[1] : null;

                if ("start".equals(command) && coord != null) {
                    String result = board.processShot(coord);
                    protocol.sendMessage(result + ";" + coord);

                    System.out.println("Przeciwnik strzela w: " + coord);
                    System.out.println("Rezultat: " + result);

                    if ("ostatni zatopiony".equals(result)) {
                        System.out.println("\n=== PRZEGRANA ===");
                        System.out.println("Zatopiono twój ostatni statek");
                        displayResults(board, false);
                        gameOver = true;
                        break;
                    } else if ("trafiony zatopiony".equals(result)) {
                        System.out.println("Trafiony i zatopiony");
                        myTurn = false;
                    } else if ("trafiony".equals(result)) {
                        System.out.println("Trafiony");
                        myTurn = false;
                    } else {
                        System.out.println("Pudło");
                        myTurn = true;
                    }

                    lastResult = result;
                } else {
                    System.out.println("Nieznana komenda: " + command);
                }
            }

            if (board.allShipsSunk() && "trafiony zatopiony".equals(lastResult)) {
                System.out.println("\n=== PRZEGRANA ===");
                displayResults(board, false);
                gameOver = true;
            }
        }

        System.out.println("\n=== KONIEC GRY ===");
        protocol.close();
    }

    private String getValidShot(GameBoard board) throws IOException {
        while (true) {
            System.out.print("Podaj współrzędne strzału: ");
            String input = consoleReader.readLine().trim().toUpperCase();

            if (!board.isValidCoordinate(input)) {
                System.out.println("Nieprawidłowe współrzędne. Użyj formatu A1-J10.");
                continue;
            }

            if (board.alreadyShotAt(input)) {
                System.out.println("Już strzelałeś w to miejsce. Wybierz inne.");
                continue;
            }

            return input;
        }
    }

    private void displayResults(GameBoard board, boolean won) {
        System.out.println("\n=== KONIEC GRY ===");
        if (won) {
            System.out.println("WYGRANA");
            System.out.println("\nMapa przeciwnika:");
        } else {
            System.out.println("PRZEGRANA");
            System.out.println("\nMapa przeciwnika:");
        }
        System.out.println(board.getEnemyBoardDisplay(won));
        System.out.println("\nMoja mapa po grze:");
        System.out.println(board.getOwnBoardDisplay());
    }
}