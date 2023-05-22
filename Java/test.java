import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class test {
    public static void main(String[] args) {
        String message = "<orderReceipt brokerId=\"John\" clientId=\"Mary\"><sell symbol=\"MSFT\" amount=\"40\" /><complete amount=\"180000\" /></orderReceipt>";
        processOrderMessage(message);
    }

    private static void processOrderMessage(String message) {
        Pattern pattern = Pattern.compile("<orderReceipt brokerId=\"(.+?)\" clientId=\"(.+?)\"><.+ amount=\"(\\d+)\" /><complete amount=\"(\\d+)\" /></orderReceipt>");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String brokerId = matcher.group(1);
            String clientId = matcher.group(2);
            int amount = Integer.parseInt(matcher.group(4));

            LocalDateTime timestamp = LocalDateTime.now();

            // Process the extracted values
            System.out.println("Timestamp: " + timestamp);
            System.out.println("Client ID: " + clientId);
            System.out.println("Broker ID: " + brokerId);
            System.out.println("Amount: " + amount);
        }
    }
}