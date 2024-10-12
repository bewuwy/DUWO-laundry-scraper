import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    static String phpSessionID;
    static HashMap<String, String> config;

    public static void main(String[] args) {

        System.out.println(Arrays.toString(args));

        try {
            readConfig(new Scanner(new File("config.txt")));
        } catch (FileNotFoundException e) {
            System.out.println("Could not load config");
            throw new RuntimeException(e);
        }

        phpSessionID = getPHPSession();

        String loginBody = String.format("UserInput=%s&PwdInput=%s\n", config.get("User"), config.get("Password"));
        loginMultiposs(loginBody);

        initMultiposs(config.get("MultipossID"), config.get("User"));

        HashMap<String, Integer> currentAvailability = getAvailability();
    }

    public static HashMap<String, Integer> getAvailability() {
        HashMap<String, Integer> availability = new HashMap<>();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://duwo.multiposs.nl/MachineAvailability.php"))
                .header("Cookie", "PHPSESSID=" + phpSessionID)
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            couldNotConnect();
            return null;
        }

        Document doc = Jsoup.parse(response.body());

        Element availabilityTable = doc.selectFirst("table.ColorTable > tbody");
        assert availabilityTable != null;

        for (int i=1; i<availabilityTable.childrenSize(); i++) {
            Element el = availabilityTable.child(i);
            String machineType = el.child(1).text();
            machineType = machineType.replace("Mach.", "Machine");
            String status = el.child(2).text();

            int available = 0;
            if (!status.startsWith("Not Available")) {
                available = Integer.parseInt(status.split(" :")[1]);
            }

            System.out.println(machineType + ": " + available);
            availability.put(machineType, available);
        }
        
        return availability;
    }

    private static void initMultiposs(String multipossID, String username) {

        String reqUrl = String.format("https://duwo.multiposs.nl/StartSite.php?ID=%s&UserID=%s", multipossID, username);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(reqUrl))
                .header("Cookie", "PHPSESSID=" + phpSessionID)
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException _) {
            couldNotConnect();
            return;
        }

        if (response.statusCode() != 302) {
            couldNotConnect();
        }
    }

    private static void loginMultiposs(String loginBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://duwo.multiposs.nl/login/submit.php"))
                .header("Cookie", "PHPSESSID=" + phpSessionID)
                .method("POST", HttpRequest.BodyPublishers.ofString(loginBody))
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException _) {
            couldNotConnect();
            return;
        }

        if (response.statusCode() != 200) {
            couldNotConnect();
            System.out.println("Could not login to multiposs");
            return;
        }

        System.out.println("Logged in to multiposs");
    }

    private static void readConfig(Scanner sc) {
        config = new HashMap<>();

        while (sc.hasNext()) {
            String l = sc.nextLine();
            String key = l.split("=", 2)[0];
            String value = l.split("=", 2)[1];

            config.put(key, value);
        }
    }

    private static String getPHPSession() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://duwo.multiposs.nl/login/index.php"))
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException | UncheckedIOException _) {
            couldNotConnect();
            return null;
        }

        Optional<String> sessionCookieString = response.headers().firstValue("set-cookie");

        if (sessionCookieString.isEmpty()) {
            couldNotConnect();
            return null;
        }

        return sessionCookieString.get().split("=")[1].split(";")[0];
    }

    private static void couldNotConnect() {
        System.out.println("Couldn't connect to multiposs");
    }
}