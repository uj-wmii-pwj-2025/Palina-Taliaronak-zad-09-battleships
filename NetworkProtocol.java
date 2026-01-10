import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NetworkProtocol {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String lastSentMessage;

    public NetworkProtocol(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public String sendAndReceive(String message) throws IOException {
        return sendAndReceive(message, 0);
    }

    private String sendAndReceive(String message, int retryCount) throws IOException {
        if (retryCount >= 3) {
            throw new IOException("Błąd komunikacji");
        }

        if (message != null) {
            sendMessage(message);
        }

        try {
            socket.setSoTimeout(60000);
            String response = in.readLine();
            if (response == null) {
                throw new IOException("Połączenie zamknięte");
            }
            System.out.println("Otrzymano: " + response);
            return response;
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("Timeout, ponawiam... (próba " + (retryCount + 1) + "/3)");
            if (lastSentMessage != null) {
                return sendAndReceive(lastSentMessage, retryCount + 1);
            } else {
                throw new IOException("Brak wiadomości do ponowienia");
            }
        } catch (IOException e) {
            if (retryCount < 2 && lastSentMessage != null) {
                System.out.println("Błąd IO, ponawiam... (próba " + (retryCount + 1) + "/3)");
                return sendAndReceive(lastSentMessage, retryCount + 1);
            } else {
                throw new IOException("Błąd komunikacji: " + e.getMessage());
            }
        }
    }

    public void sendMessage(String message) {
        out.println(message);
        lastSentMessage = message;
        System.out.println("Wysłano: " + message);
    }

    public void close() throws IOException {
        socket.close();
    }
}