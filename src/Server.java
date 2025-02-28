import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Server {

    private String serverIP;
    private final int serverPort = 5000;
    private Map<String, Node> knownClients;
    private ScheduledExecutorService scheduler;

    public Server(int serverPort) {
        try {
            this.serverIP = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            this.serverIP = "127.0.0.1";
            System.out.println("Could not determine server IP: " + e.getMessage());
        }
        this.knownClients = new HashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        loadKnownClients();
    }

    private void loadKnownClients() {
        String configFile = ".config";
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split(":");
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    knownClients.put(ip, new Node(ip, port));
                    System.out.println("Added client: " + ip + ":" + port);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading server config: " + e.getMessage());
        }
    }

    public void startListening() {
        Thread receiveThread = new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(serverPort);
                byte[] incomingData = new byte[5120];

                System.out.println("Server listening on " + serverIP + ":" + serverPort + "...");
                while (true) {
                    DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                    socket.receive(incomingPacket);
                    Message receivedMessage = Message.decode(incomingPacket.getData());
                    System.out.println("Received heartbeat from " + receivedMessage.getNodeIP() + " - Version: " + receivedMessage.getVersion() + ", Timestamp: " + receivedMessage.getTimestamp() + ", Files: " + receivedMessage.getFileListing());
                    processClientHeartbeat(receivedMessage); // Placeholder for processing
                }
            } catch (Exception e) {
                System.err.println("Error in listening thread: " + e.getMessage());
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void startUpdateTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            sendClientUpdates();
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void sendClientUpdates() {
        try {
            DatagramSocket socket = new DatagramSocket();
            // Placeholder data for now; will be processed later
            String combinedData = "Placeholder for client availability and file listings";
            Message update = new Message((byte) 0, serverIP, (int) (System.currentTimeMillis() / 1000), combinedData);
            byte[] byteMessage = update.getMessageBytes();

            for (Node client : knownClients.values()) {
                InetAddress clientAddress = InetAddress.getByName(client.getIP());
                DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, 
                                                          clientAddress, client.getPort());
                socket.send(packet);
                System.out.println("Sent update to " + client.getIP() + ":" + client.getPort());
            }
            socket.close();
        } catch (Exception e) {
            System.err.println("Error sending client updates: " + e.getMessage());
        }
    }

    public void processClientHeartbeat(Message message) {
        
    }

    public static void main(String[] args) {
        Server server = new Server(0);
        server.startListening();
        server.startUpdateTimer();
    }
}