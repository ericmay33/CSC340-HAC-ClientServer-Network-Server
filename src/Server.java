import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
    private int serverPort; // From config
    private Map<String, Node> knownClients;
    private Map<String, Message> clientStatus;
    private ScheduledExecutorService scheduler;

    public Server() {
        try {
            this.serverIP = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            throw new RuntimeException("Could not determine server IP: " + e.getMessage());
        }
        this.knownClients = new HashMap<>();
        this.clientStatus = new HashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        loadKnownClients();
    }

    private void loadKnownClients() {
        String configFile = ".config";
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split(":");
                    String ip = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    if (firstLine) {
                        // First line is server's own address
                        if (ip.equals(serverIP)) {
                            this.serverPort = port;
                        } else {
                            throw new IOException("First line must be server's IP: " + serverIP);
                        }
                        firstLine = false;
                    } else {
                        // Remaining lines are clients
                        knownClients.put(ip, new Node(ip, port));
                        System.out.println("Added client: " + ip + ":" + port);
                    }
                }
            }
            if (serverPort == 0) {
                throw new IOException("Server port not found in config for " + serverIP);
            }
            System.out.println("Server configured on " + serverIP + ":" + serverPort);
        } catch (IOException e) {
            throw new RuntimeException("Error reading server config: " + e.getMessage());
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
                    System.out.println("Received heartbeat from " + receivedMessage.getNodeIP() +
                                      " - Version: " + receivedMessage.getVersion() +
                                      ", Timestamp: " + receivedMessage.getTimestamp() +
                                      ", Files: " + receivedMessage.getFileListing());
                    processClientHeartbeat(receivedMessage);
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
            StringBuilder combinedData = new StringBuilder();
            long currentTime = System.currentTimeMillis() / 1000;
            for (String clientIP : knownClients.keySet()) {
                Message lastMessage = clientStatus.get(clientIP);
                String status = (lastMessage != null && (currentTime - lastMessage.getTimestamp()) <= 30) 
                                ? "up" : "down";
                String files = (lastMessage != null) ? lastMessage.getFileListing() : "none";
                String nodeEntry = clientIP + ":" + status + ":" + files;
                combinedData.append(nodeEntry).append(";");
            }
            if (combinedData.length() > 0) {
                combinedData.setLength(combinedData.length() - 1);
            }
            String updateData = combinedData.toString();

            Message update = new Message((byte) 0, serverIP, (int) currentTime, updateData);
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
        clientStatus.put(message.getNodeIP(), message);
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.startListening();
        server.startUpdateTimer();
    }
}