import java.io.*;
import java.net.*;
import java.io.File;
import io.nats.client.*;
import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.w3c.dom.Node;
import javax.xml.transform.TransformerFactory;
import org.xml.sax.InputSource;

public class StockMonitor {

    public static void main(String[] args) {
        try {
            //start the connection with the nats server
            Connection nc = Nats.connect("nats://localhost:4222");
            //clear pricelogs files folder -- our choice

            //this listens for things being sent to the server and finds them on a thread
            Dispatcher dispatch = nc.createDispatcher((msg) -> {
                //add msg.getData to a txt file with a name of what the subject is
                //this turns the xml into a string, then need to parse it out by node type
                try {
                    String response = new String(msg.getData());

                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();  
                    DocumentBuilder builder = factory.newDocumentBuilder(); 
                    Document document = builder.parse(new InputSource(new StringReader(response)));

                    Element root = document.getDocumentElement();
                    String symbol = root.getElementsByTagName("name").item(0).getTextContent();

                    //create the text file (don't override if exists, add to it) for each symbol it is tracking
                    //name of file is symbol it is tracking
                    File priceLogFile = new File("./PriceLogs/" + symbol + ".txt");
                    priceLogFile.getParentFile().mkdirs(); 
                    if(!priceLogFile.isFile()) {
                        //make the file
                        priceLogFile.createNewFile();
                    } 

                    //get text already in file to add to it
                    StringBuilder fileContent = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new FileReader("./PriceLogs/" + symbol + ".txt"));
                    String emptyString = "";
                    while ((emptyString = reader.readLine()) != null) {
                        fileContent.append(emptyString);
                        fileContent.append("\r\n");
                    }
                    reader.close();

                    //now that the files have been made, add to them
                    //each message is it's own line: add the timestamp, adjustment for message, and current price of stock after 
                    //adjustment
                    String timestamp = root.getAttributes().getNamedItem("sent").getNodeValue();
                    String adjustment = root.getElementsByTagName("adjustment").item(0).getTextContent();
                    String adjustedPrice = root.getElementsByTagName("adjustedPrice").item(0).getTextContent();

                    FileWriter writer = new FileWriter("./PriceLogs/" + symbol + ".txt");
                    writer.write(fileContent.toString() + "timestamp: " + timestamp + " adjustment: " + "$" + (Integer.valueOf(adjustment) / 100.f) + " adjusted price: " + "$" + (Integer.valueOf(adjustedPrice) / 100.f));
                    writer.close();
                }
                catch (Exception err) {
                    err.printStackTrace();
                }
                

            });

            //what you want dispatch to be listening for
            if(args.length != 0) {
                for(int i = 0; i < args.length; i++) {
                    //dispatch for whatever was sent through the console
                    dispatch.subscribe("NASDAQ." + args[i]);
                }
            } else {
                //dispatch if no symbols were specified go to this.>
                dispatch.subscribe("NASDAQ.*");
            }

            // //shut the system down
            //System.exit(0);
        }
        catch (Exception err) {
            err.printStackTrace();
        }
    }
}