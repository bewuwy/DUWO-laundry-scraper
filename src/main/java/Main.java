import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {

    static String phpSessionID;
    static HashMap<String, String> config;

    static Notifier notifier;

    public static void main(String[] args) {

        HashMap<String, Integer> targetAvailability = getTargetAvailability(args);

        if (targetAvailability.isEmpty()) {
           System.out.println("No targets set! You can use CLI arguments to set them: \"wm 3\" for 3 washing machines or \"d\" for 1 dryer (you can also specify multiple targets)");
        }
        else {
            System.out.println("Waiting for targets: " + targetAvailability);
        }

        try {
            readConfig(new Scanner(new File("config.txt")));
        } catch (FileNotFoundException e) {
            System.out.println("Could not load config");
            throw new RuntimeException(e);
        }

        if (!targetAvailability.isEmpty()) {
            try {
                notifier = new Notifier(config.get("tgBotToken"), Integer.parseInt(config.get("tgChatID")));
                notifier.sendTelegramMessage("Waiting for laundry: " + targetAvailability);
            } catch (NumberFormatException _) {
                System.out.println("No Telegram bot set up. Configure it in the config.txt file.");
            }
        }

        refreshPHPSession();

        boolean allTargetsReached;
        int iterationCounter = 0;

        HashMap<String, Integer> currentAvailability;
        do {
            iterationCounter ++;
            if (iterationCounter % 10 == 0)
                refreshPHPSession();  // refresh PHP session cookie every 10 iterations (minutes)

            System.out.println();

            currentAvailability = getAvailability();

            if (currentAvailability.isEmpty()) {
                couldNotConnect("No current availability loaded", "E-mail address in config may be wrong");
                return;
            }

            allTargetsReached = true;

            for (String m: targetAvailability.keySet()) {
                int target = targetAvailability.get(m);
                int current;
                try {
                    current = currentAvailability.get(m);
                } catch (RuntimeException e) {
                    System.out.println("\nERROR: The target \"" + m + "\" does not exist in your building");
                    return;
                }

                if (current < target) {
                    allTargetsReached = false;
                }
            }

            if (allTargetsReached) {
                if (!targetAvailability.isEmpty()) {
                    String msg = "Reached your target of: " + targetAvailability + "!";
                    System.out.println(msg);
                    if (notifier != null)
                        notifier.sendTelegramMessage(msg);
                }
                return;
            }

            try {
                TimeUnit.MINUTES.sleep(1);
            } catch (InterruptedException _) {
                return;
            }
        } while (true);
    }

    private static void refreshPHPSession() {
        phpSessionID = getPHPSession();

        // multiposs availability page works even with incorrect password (only email needs to be correct)
        String loginBody = String.format("UserInput=%s&PwdInput=%s\n", config.get("user"), "INCORRECT_PASS");
        loginMultiposs(loginBody);

        initMultiposs(config.get("user"));
    }

    private static HashMap<String, Integer> getTargetAvailability(String[] args) {
        HashMap<String, Integer> targetAvailability = new HashMap<>();
        String currentMachine = "";
        int currentAmount = 1;

        for (String arg : args) {
            try {
                currentAmount = Integer.parseInt(arg);
            } catch (NumberFormatException e) {

                if (!currentMachine.isEmpty()) {
                    targetAvailability.put(currentMachine, currentAmount);
                }

                currentMachine = switch (arg) {
                    case "wm" -> "Washing Machine";
                    case "d" -> "Dryer";
                    default -> arg;
                };
                currentAmount = 1;
            }
        }

        if (!currentMachine.isEmpty()) {
            targetAvailability.put(currentMachine, currentAmount);
        }

        return targetAvailability;
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
            return availability;
        }

        Document doc = Jsoup.parse(response.body());

        Element availabilityTable = doc.selectFirst("table.ColorTable > tbody");
        if (availabilityTable == null) {
            return availability;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("H:mm");
        System.out.print(sdf.format(new Date()) + " ");

        for (int i=1; i<availabilityTable.childrenSize(); i++) {
            Element el = availabilityTable.child(i);

            String machineType = el.child(1).text();
            String location = el.child(0).text();
            if (i == 1) {
                System.out.println(location);
            }
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

    private static void initMultiposs(String username) {

        // the multiposs id does not matter, as the building is determined by your account
        String reqUrl = String.format("https://duwo.multiposs.nl/StartSite.php?ID=%s&UserID=%s", "SOME_RANDOM_ID", username);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(reqUrl))
                .header("Cookie", "PHPSESSID=" + phpSessionID)
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            couldNotConnect("initMultiposs: " + e);
            return;
        }

        if (response.statusCode() != 302) {
            couldNotConnect("initMultiposs: wrong response code " + response.statusCode());
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
        } catch (IOException | InterruptedException e) {
            couldNotConnect("loginMultiposs: " + e);
            return;
        }

        if (response.statusCode() != 200) {
            couldNotConnect("loginMultiposs: wrong response code " + response.statusCode());
        }
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
        } catch (IOException | InterruptedException | UncheckedIOException e) {
            couldNotConnect("getPHPSession: " + e);
            return null;
        }

        Optional<String> sessionCookieString = response.headers().firstValue("set-cookie");

        if (sessionCookieString.isEmpty()) {
            couldNotConnect("getPHPSession: did not get PHP session id cookie");
            return null;
        }

        return sessionCookieString.get().split("=")[1].split(";")[0];
    }

    private static void couldNotConnect(String e) {
        String msg = String.format("ERROR: Couldn't connect to multiposs (%s)%n", e);
        System.out.printf(msg);
        if (notifier != null)
            notifier.sendTelegramMessage(msg);
    }

    @SuppressWarnings("SameParameterValue")
    private static void couldNotConnect(String e, String d) {
        String msg = String.format("ERROR: Couldn't connect to multiposs (%s) - %s %n", e, d);
        System.out.printf(msg);
        if (notifier != null)
            notifier.sendTelegramMessage(msg);
    }
}
