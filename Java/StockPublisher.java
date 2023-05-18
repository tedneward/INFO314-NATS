import io.nats.client.*;
import java.sql.Timestamp;

public class StockPublisher {

    private static Connection nc = null;

    public static void main(String... args) throws Exception {
        String natsURL = "nats://127.0.0.1:4222";
        if (args.length > 0) {
            natsURL = args[0];
        }

        nc = Nats.connect(natsURL);
        System.console().writer().println("Connected to Nats server....");
        System.console().writer().println("Starting stock markets....");
        StockMarket sm1 = new StockMarket(StockPublisher::publishMessage, "AMZN", "MSFT", "GOOG", "AAPL", "TSLA", "JNJ",
                "NFLX");
        new Thread(sm1).start();
        StockMarket sm2 = new StockMarket(StockPublisher::publishMessage, "ACN", "BA", "SNAP", "GME", "AMC", "NKE",
                "DIS");
        new Thread(sm2).start();
        StockMarket sm3 = new StockMarket(StockPublisher::publishMessage, "COST", "ABNB", "ADBE", "SBUX", "META",
                "PYPL", "ZM");
        new Thread(sm3).start();
    }

    public synchronized static void publishMessage(String symbol, int adjustment, int price) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        String xml = "<message sent=\"" + timestamp + "\">" +
                "<stock><name>" + symbol + "</name>" +
                "<adjustment>" + adjustment + "</adjustment>" +
                "<adjustedPrice>" + price + "</adjustedPrice></stock></message>";

        String stockExchange = "";
        if (symbol.equals("AMZN") || symbol.equals("MSFT") || symbol.equals("GOOG") || symbol.equals("AAPL") ||
                symbol.equals("TSLA") || symbol.equals("JNJ") || symbol.equals("SBUX") || symbol.equals("ZM")
                || symbol.equals("NFLX") || symbol.equals("META") || symbol.equals("COST") || symbol.equals("ABNB")
                || symbol.equals("ADBE") || symbol.equals("PYPL")) {
            stockExchange = "NASDAQ.";
        } else if (symbol.equals("GME") || symbol.equals("DIS") || symbol.equals("SNAP") || symbol.equals("AMC")
                || symbol.equals("ACN") || symbol.equals("BA") || symbol.equals("NKE")) {
            stockExchange = "NYSE.";
        } else {
            stockExchange = "Stock."; 
        }
        nc.publish(stockExchange + symbol, xml.getBytes());
    }
}