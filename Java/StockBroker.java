import java.io.*;
import io.nats.client.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern; 
import java.time.*;
import java.util.Scanner;

public class StockBroker {

    private String brokerName;
    private Dispatcher d;
    private Connection nc;


    //This logic is specific to the instance of a specific StockBroker. Allowing each client to be processed by the broker and only that broker. 
    public StockBroker(String brokerName, Connection connection) {
        this.brokerName = brokerName;
        this.nc = connection;
        this.d = connection.createDispatcher((msg) -> {
            // Handle the received message
            String order = new String(msg.getData());
            processOrder(order);
        });
    }

    public void subscribe(String topic) {
        d.subscribe(topic);
    }

    public void unsubscribe(String topic) {
        d.unsubscribe(topic);
    }

    public void processOrder(String order) {
        // Perform necessary calculations and order execution logic
        // Sleep for a few seconds to simulate order execution
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Calculate total order amount with 10% fee
        double totalAmount = calculateTotalAmount(order);

        // Construct the response message
        String response = constructResponse(order, totalAmount);

        // Publish the response message back to the client
        publishResponse(response);
    }

    private static double calculateTotalAmount(String order) {
        // Define the regular expression pattern to match the order format
        Pattern pattern = Pattern.compile("<(buy|sell) symbol=\"(\\w+)\" amount=\"(\\d+)\"\\s*/>");
        Matcher matcher = pattern.matcher(order);
    
        // Check if the order matches the expected format
        if (matcher.matches()) {
            String type = matcher.group(1);
            String symbol = matcher.group(2);
            double amount = Double.parseDouble(matcher.group(3));
            

            //Get this somehow from the the stock market class
            double stockPrice = getStockPrice(symbol); // Assuming you have a method to retrieve the stock price
            double fee = 0.1 * (type.equals("buy") ? stockPrice * amount : -stockPrice * amount);
            double totalAmount = type.equals("buy") ? stockPrice * amount + fee : stockPrice * amount - fee;
    
            return totalAmount;
        }
    
        // Return a default value or throw an exception if the order format is invalid
        return 0.0;
    }

    private static double getStockPrice(String symbol){
        // going to be getting this from Matt's code 
    }

    private String constructResponse(String order, double totalAmount) {
        // Construct the response message to be sent back to the client
        String response = "<orderReceipt>" + order + "<complete amount=\"" + totalAmount + "\" /></orderReceipt>";
        return "";
    }

    private void publishResponse(String response) {
        // Publish the response message to the client
        String responseTopic = "response." + brokerName;
        nc.publish(responseTopic, response.getBytes());
    }



    public static void main(String... args) throws IOException, InterruptedException{
        //Need to connect to the NATS Server
        

        // Get the broker name from command-line arguments, but not sure if it will be set up like that
        // Prompt the user to enter the broker name
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the broker name: ");
        String brokerName = scanner.nextLine();


        try{
            Connection connection = Nats.connect("nats://localhost:4222");

          

            // Create an instance of the StockBroker
            StockBroker stockBroker = new StockBroker(brokerName,connection);
           
            // Subscribe to the broker's topic
            stockBroker.subscribe(brokerName);


            //Might not need this, look into the duration class. 
            // Run the NATS event loop to receive messages
            nc.flush(Duration.ZERO); // Flush any buffered messages
            nc.flush(Duration.ofSeconds(100)); // Wait for a 100 second to receive messages
            nc.close(); // Close the NATS connection

        }catch(Exception e){
            e.printStackTrace();
        }



    }
    
}
