import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameBoard {
    private static final int SIZE = 10;
    private char[][] board;
    private Set<String> shotsFired;
    private Set<String> hits;
    private Set<String> misses;
    private Set<String> enemyShots;
    private Set<String> enemyHits;
    private List<Ship> ships;

    public GameBoard(String mapFile) throws IOException {
        board = new char[SIZE][SIZE];
        shotsFired = new HashSet<>();
        hits = new HashSet<>();
        misses = new HashSet<>();
        enemyShots = new HashSet<>();
        enemyHits = new HashSet<>();
        ships = new ArrayList<>();
        loadMap(mapFile);
    }


    private void loadMap(String mapFile) throws IOException {
        String mapString;
        if (mapFile != null) {
            BufferedReader reader = new BufferedReader(new FileReader(mapFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            mapString = sb.toString();
        } else {
            mapString = BattleshipGenerator.defaultInstance().generateMap();
        }

        if (mapString.length() != SIZE * SIZE) {
            throw new IllegalArgumentException("Nieprawidłowy rozmiar mapy");
        }

        int index = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                board[r][c] = mapString.charAt(index++);
            }
        }
        detectShips();
    }

    private void detectShips() {
        boolean[][] visited = new boolean[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == '#' && !visited[r][c]) {
                    Ship ship = new Ship();
                    exploreShip(r, c, visited, ship);
                    ships.add(ship);
                }
            }
        }
    }

    private void exploreShip(int r, int c, boolean[][] visited, Ship ship) {
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE || visited[r][c] || board[r][c] != '#') {
            return;
        }
        visited[r][c] = true;
        ship.addCoordinate(r, c);

        exploreShip(r + 1, c, visited, ship);
        exploreShip(r - 1, c, visited, ship);
        exploreShip(r, c + 1, visited, ship);
        exploreShip(r, c - 1, visited, ship);
    }

    public String processShot(String coord) {
        int[] rc = parseCoordinate(coord);
        int r = rc[0];
        int c = rc[1];

        boolean alreadyShot = enemyShots.contains(coord);
        enemyShots.add(coord);

        Ship targetShip = null;
        for (Ship ship : ships) {
            if (ship.contains(coord)) {
                targetShip = ship;
                break;
            }
        }

        if (targetShip != null) {
            if (targetShip.hasBeenHitAt(coord)) {
                if (targetShip.isSunk()) {
                    return "trafiony zatopiony";
                } else {
                    return "trafiony";
                }
            }

            targetShip.hit(coord);
            enemyHits.add(coord);

            if (targetShip.isSunk()) {
                if (allShipsSunk()) {
                    return "ostatni zatopiony";
                } else {
                    return "trafiony zatopiony";
                }
            } else {
                return "trafiony";
            }
        } else {
            if (alreadyShot) {
                return "pudło";
            } else {
                return "pudło";
            }
        }
    }

    public void recordOurShot(String coord, String result) {
        shotsFired.add(coord);
        if (result.contains("trafiony")) {
            hits.add(coord);
        } else {
            misses.add(coord);
        }
    }

    private boolean alreadyHitByEnemy(String coord) {
        return enemyShots.contains(coord);
    }

    private Ship getShipAt(String coord) {
        for (Ship ship : ships) {
            if (ship.contains(coord)) {
                return ship;
            }
        }
        return null;
    }

    public boolean allShipsSunk() {
        for (Ship ship : ships) {
            if (!ship.isSunk()) {
                return false;
            }
        }
        return true;
    }

    public String getOwnBoardDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                String coord = toCoordinate(r, c);
                if (enemyHits.contains(coord)) {
                    sb.append('@');
                } else if (enemyShots.contains(coord)) {
                    sb.append('~');
                } else {
                    sb.append(board[r][c]);
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public String getEnemyBoardDisplay(boolean allVisible) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                String coord = toCoordinate(r, c);
                if (hits.contains(coord)) {
                    sb.append('#');
                } else if (misses.contains(coord)) {
                    sb.append('.');
                } else if (allVisible) {
                    if (shotsFired.contains(coord)) {
                        sb.append('.');
                    } else {
                        sb.append('?');
                    }
                } else {
                    sb.append('?');
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public void displayOwnBoard() {
        System.out.println("Moja mapa:");
        System.out.println(getOwnBoardDisplay());
    }

    public void displayEnemyBoard(boolean allVisible) {
        System.out.println("Mapa przeciwnika:");
        System.out.println(getEnemyBoardDisplay(allVisible));
    }

    public void displayInitialBoard() {
        System.out.println("Moja początkowa mapa:");
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                System.out.print(board[r][c]);
            }
            System.out.println();
        }
    }

    public boolean isValidCoordinate(String coord) {
        if (coord == null || coord.length() < 2 || coord.length() > 3) {
            return false;
        }

        char colChar = Character.toUpperCase(coord.charAt(0));
        if (colChar < 'A' || colChar > 'J') {
            return false;
        }

        try {
            int row = Integer.parseInt(coord.substring(1));
            return row >= 1 && row <= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int[] parseCoordinate(String coord) {
        char colChar = Character.toUpperCase(coord.charAt(0));
        int row = Integer.parseInt(coord.substring(1)) - 1;
        int col = colChar - 'A';
        return new int[]{row, col};
    }

    private String toCoordinate(int r, int c) {
        return "" + (char)('A' + c) + (r + 1);
    }

    public boolean alreadyShotAt(String coord) {
        return shotsFired.contains(coord);
    }

    public boolean alreadyHitByEnemyAt(String coord) {
        return enemyShots.contains(coord);
    }

    private class Ship {
        private Set<String> coordinates;
        private Set<String> hits;

        public Ship() {
            coordinates = new HashSet<>();
            hits = new HashSet<>();
        }

        public void addCoordinate(int r, int c) {
            coordinates.add(toCoordinate(r, c));
        }

        public boolean contains(String coord) {
            return coordinates.contains(coord);
        }

        public void hit(String coord) {
            hits.add(coord);
        }

        public boolean isSunk() {
            return hits.size() == coordinates.size();
        }

        public Set<String> getCoordinates() {
            return new HashSet<>(coordinates);
        }

        public boolean hasBeenHitAt(String coord) {
            return hits.contains(coord);
        }

        public int getSize() {
            return coordinates.size();
        }
    }
}