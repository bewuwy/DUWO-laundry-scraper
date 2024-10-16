import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Notifier {

    static String prefix = "DL: ";

    String botToken;
    int chatID;

    public Notifier(String botToken, int chatID) {
        this.botToken = botToken;
        this.chatID = chatID;
    }

    void sendTelegramMessage(String text) {

        String textWithPrefix = prefix + text;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("https://api.telegram.org/bot%s/sendMessage", this.botToken)))
                .header("Content-Type", "application/json")
                .header("User-Agent", "insomnia/10.0.0")
                .method("POST", HttpRequest.BodyPublishers.ofString(String.format("{\n\t\"chat_id\": %s,\n\t\"text\": \"%s\"\n}", chatID, textWithPrefix)))
                .build();
        try {
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
