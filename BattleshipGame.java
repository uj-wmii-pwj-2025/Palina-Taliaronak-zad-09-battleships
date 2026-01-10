import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BattleshipGame {
    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);

        if (!params.containsKey("mode") || !params.containsKey("port")) {
            System.err.println("Brak -mode i -port");
            System.exit(1);
        }

        String mode = params.get("mode");
        int port = Integer.parseInt(params.get("port"));
        String mapFile = params.get("map");
        String host = params.get("host");

        try {
            if ("server".equals(mode)) {
                BattleshipServer server = new BattleshipServer(port, mapFile);
                server.start();
            } else if ("client".equals(mode)) {
                if (host == null) {
                    System.err.println("Tryb client wymaga -host");
                    System.exit(1);
                }
                BattleshipClient client = new BattleshipClient(host, port, mapFile);
                client.start();
            } else {
                System.err.println("Nieznany tryb: " + mode);
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Błąd: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                String key = args[i].substring(1);
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    params.put(key, args[i + 1]);
                    i++;
                } else {
                    params.put(key, "");
                }
            }
        }
        return params;
    }
}