import io.nats.client.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;
import java.util.Scanner;

public class StockBroker {
    private String brokerName;
    private Dispatcher dispatcher;
    private Dispatcher priceDispatcher;
    private Connection natsConnection;
    private String clientName;

    public StockBroker(String brokerName, Connection connection) {
        this.brokerName = brokerName;
        this.natsConnection = connection;

        //This takes the class instance of processOrder and uses that as message handler
        this.dispatcher = connection.createDispatcher(this::processOrder);
        this.priceDispatcher = connection.createDispatcher();

    }

    public void subscribe(String topic) {
        dispatcher.subscribe(topic);
    }

    public void unsubscribe(String topic) {
        dispatcher.unsubscribe(topic);
    }

    private void processOrder(Message message) {
        String order = new String(message.getData());

        try {
            Thread.sleep(3000); // Simulate order execution
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        double totalAmount = calculateTotalAmount(order);
        String response = constructResponse(order, totalAmount);
        publishResponse(response);
    }

    private double calculateTotalAmount(String order) {
        Pattern pattern = Pattern.compile("<(buy|sell) symbol=\"(\\w+)\" amount=\"(\\d+)\"\\s*/>");
        Matcher matcher = pattern.matcher(order);

        if (matcher.matches()) {
            String type = matcher.group(1);
            String symbol = matcher.group(2);
            double amount = Double.parseDouble(matcher.group(3));
            double stockPrice = getStockPrice(symbol);
            double fee = 0.1 * (type.equals("buy") ? stockPrice * amount : -stockPrice * amount);
            double totalAmount = type.equals("buy") ? stockPrice * amount + fee : stockPrice * amount - fee;

            return totalAmount;
        }

        return 0.0;
    }

    private double getStockPrice(String symbol) {
        String xmlData = subscribeAndGetXmlData(symbol);
        return extractStockPrice(xmlData);
    }

    private String subscribeAndGetXmlData(String symbol) {
        String topic = "NASDAQ." + symbol;
        final String[] xmlData = {""};

        MessageHandler messageHandler = msg -> {
            xmlData[0] = new String(msg.getData());
            priceDispatcher.unsubscribe(topic);
        };

        priceDispatcher.subscribe(topic, messageHandler);

        try {
            Thread.sleep(1000); // Wait for message to be received
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return xmlData[0];
    }

    private double extractStockPrice(String xmlData) {
        Pattern pattern = Pattern.compile("<price>(\\d+\\.\\d+)</price>");
        Matcher matcher = pattern.matcher(xmlData);

        if (matcher.find()) {
            String priceString = matcher.group(1);
            return Double.parseDouble(priceString);
        }

        return 0.0;
    }

    private String constructResponse(String order, double totalAmount) {
        String orderClientName = extractClientName(order);
        
        String orderPattern = "<order>(.*?)</order>";

        // Create a Pattern object
        Pattern regex = Pattern.compile(orderPattern);

        // Create a Matcher object and apply the pattern to the input XML
        Matcher matcher = regex.matcher(order);

        String newOrderContent = "";

        // Find the first occurrence of the pattern
        if (matcher.find()) {
            // Extract the content between the <order> tags
            newOrderContent = matcher.group(1);
        
        }
        return "<orderReceipt brokerId = \"" + brokerName + "\" clientId = \"" + orderClientName + "\" >" + newOrderContent + "<complete amount=\"" + totalAmount + "\" /></orderReceipt>";
    }

    private String extractClientName(String order){
        String pattern = "brokerId=\"([^\"]+)\"\\s+clientId=\"([^\"]+)\"";
        Pattern client_broker_pattern = Pattern.compile(pattern);
        Matcher matcher = client_broker_pattern.matcher(order);
        String clientId = "";
        String brokerId = "";
        if (matcher.find()) {
            brokerId = matcher.group(1);
            clientId = matcher.group(2);
            this.clientName = clientId;
            this.brokerName = brokerId;
        } else {
            clientId = "N/A";
        }
        
        return clientId;

    }

    private void publishResponse(String response) {

        String responseTopic = "response." + clientName ;
        natsConnection.publish(responseTopic, response.getBytes());
    }

    public static void main(String... args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the broker name: ");
        String brokerName = scanner.nextLine();

        try {
            Connection connection = Nats.connect("nats://localhost:4222");

            StockBroker stockBroker = new StockBroker(brokerName, connection);
            stockBroker.subscribe("broker."+ brokerName);

            connection.flush(Duration.ZERO); // Flush any buffered messages
            connection.flush(Duration.ofSeconds(100)); // Wait for 100 seconds to receive messages
            connection.close(); // Close the NATS connection
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


