import io.nats.client.*;
import java.io.*;
import java.util.*;
import java.nio.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

public class StockMarketClient{
    public static void main(String...args) throws Exception {
        Connection nc = Nats.connect("nats://localhost:4222");
        String broker="BestBroker";

        // uncomment when StockPublisher connection made and pass response into "testing"
        // Subscription sub = nc.subscribe("stockPublisher");
        // Message stocks = sub.nextMessage(Duration.ofMillis(500));
        // String response = new String(stocks.getData(), StandardCharsets.UTF_8);

        // this is a sample of what a message from 
        String testing = "<message sent=\"" + "2023-05-17 13:37:45" + "\"><stock><name>AMZN</name><adjustment>40</adjustment><adjustedPrice>6000</adjustedPrice></stock></message>";;
        
        Object[] output=checkingStrategy(testing);

        if (output != null){
            String action= (String) output[0];
            String stockName = (String) output[1];
            int numberOfShares = (int) output[2];
            String xmlRequest = xmlRequestBuilder(action, stockName, numberOfShares);
            System.out.println(xmlRequest);

            // nc.publish(broker, xmlRequest.getBytes());

            // uncomment once broker connection established
            // Future<Message> incoming = nc.request(broker, xmlRequest.getBytes()); // awaits response from broker
            // Message msg = incoming.get(500, TimeUnit.MILLISECONDS);
            // String brokerResponse = new String(msg.getData(), StandardCharsets.UTF_8);

            // Testing broker Response parser
            String brokerResponse = "<orderReceipt><sell symbol=\"AMZN\" amount=\"50\" /><complete amount=\"180000\" /></orderReceipt>";
            System.out.println("Response from broker");
            System.out.println(brokerResponse);

            updatePortfolio(stockName, numberOfShares, action);
        }
    }

    public static Object[] checkingStrategy(String message){
        Object[] params = new Object[3];
        try{
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(message.getBytes()));
            
            doc.getDocumentElement().normalize();
            
            // Extract the value of the name and adjustment element
            Element nameElement = (Element) doc.getElementsByTagName("name").item(0);
            String name = nameElement.getTextContent();

            Element adjustedPriceElement = (Element) doc.getElementsByTagName("adjustedPrice").item(0);
            int adjustment = Integer.parseInt(adjustedPriceElement.getTextContent());

            // Load the strategy XML file
            File strategyXmlFile = new File("../Clients/strategy-1.xml");
            DocumentBuilderFactory dbFactory2 = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder2 = dbFactory2.newDocumentBuilder();
            Document doc2 = dBuilder2.parse(strategyXmlFile);

            doc2.getDocumentElement().normalize();
        
            // when elements in the strategy XML
            NodeList whenList = doc2.getElementsByTagName("when");
            for (int i = 0; i < whenList.getLength(); i++) {
                Node node = whenList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element symbolElement = (Element) node;
                    String stockSymbol = symbolElement.getElementsByTagName("stock").item(0).getTextContent();

                    // Check if the symbol matches
                    if (name.equals(stockSymbol)) {
                        boolean hasBelow = symbolElement.getElementsByTagName("below").getLength() > 0;
                        boolean hasAbove = symbolElement.getElementsByTagName("above").getLength() > 0;

                        Node belowNode = symbolElement.getElementsByTagName("below").item(0);
                        Node aboveNode = symbolElement.getElementsByTagName("above").item(0);

                        if (hasBelow && belowNode != null) {
                            int belowThreshold = Integer.parseInt(belowNode.getTextContent());
                            if (adjustment < belowThreshold) {
                                int buyAmount = Integer.parseInt(symbolElement.getElementsByTagName("buy").item(0).getTextContent());
                                params[0] = "buy";
                                params[1] = stockSymbol;
                                params[2] = buyAmount;
                                return params;
                            }
                        }

                        if (hasAbove && aboveNode != null) {
                            int aboveThreshold = Integer.parseInt(aboveNode.getTextContent());
                            if (adjustment > aboveThreshold) {
                                params[0] = "sell";
                                params[1] = stockSymbol;
                                params[2] = 50;
                                return params;
                            }
                        }   
                    }
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String xmlRequestBuilder(String action, String stockName, int numberOfShares){
        try{
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // Create the root element <order>
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("order");
        doc.appendChild(rootElement);

        Element buyElement = doc.createElement(action);
        buyElement.setAttribute("symbol", stockName);
        buyElement.setAttribute("amount", String.valueOf(numberOfShares));
        rootElement.appendChild(buyElement);

        // Convert the Document to a String
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
        } catch (ParserConfigurationException | TransformerException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void updatePortfolio(String symbol, int amount, String action){
        try{
            File xmlFile = new File("../Clients/portfolio-1.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
        
            // Normalize the XML structure
            doc.getDocumentElement().normalize();

            // Find the stock element with the specified symbol
            Element stockElement = null;
            NodeList stockList = doc.getElementsByTagName("stock");
            for (int i = 0; i < stockList.getLength(); i++) {
                Node node = stockList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String stockSymbol = element.getAttribute("symbol");
                    if (stockSymbol.equals(symbol)) {
                        stockElement = element;
                        break;
                    }
                }
            }

            // Update the stock amount based on the action
            if (stockElement != null) {
                int currentAmount = Integer.parseInt(stockElement.getTextContent());
                int updatedAmount;
            
                if (action.equals("buy")) {
                    updatedAmount = currentAmount + amount;
                } else if (action.equals("sell")) {
                    updatedAmount = currentAmount - amount;
                } else {
                    return;
                }
            
                // Set the updated amount in the stock element
                stockElement.setTextContent(String.valueOf(updatedAmount));
            
                // Save the changes back to the XML file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(new FileWriter(xmlFile));
                transformer.transform(source, result);
            
                System.out.println("Portfolio updated successfully.");
            } else {
                System.out.println("Stock symbol not found in the portfolio.");
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}