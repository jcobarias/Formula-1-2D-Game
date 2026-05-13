import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GridRush F1 - Game Server
 * Handles real-time movement synchronization using UDP.
 */
public class GameServer {
    private static final int PORT = 9876;
    private DatagramSocket socket;
    
    // Maps PlayerID to their network address for broadcasting
    private ConcurrentHashMap<Integer, ClientInfo> clients = new ConcurrentHashMap<>();

    public void start() {
        try {
            socket = new DatagramSocket(PORT);
            System.out.println("🏁 GridRush Server started on port " + PORT);
            System.out.println("Listening for incoming drivers...");

            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                socket.receive(incoming);

                handlePacket(incoming);
            }
        } catch (Exception e) {
            System.err.println("Server Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private static final int REQUIRED_PLAYERS = 2;
    private boolean raceStarted = false;

    private void handlePacket(DatagramPacket packet) {
        try {
            // Deserialize directly from the packet buffer
            CarState state = CarState.deserialize(packet.getData());
            int playerID = state.playerID;

            // Register/Update client address
            if (!clients.containsKey(playerID)) {
                clients.put(playerID, new ClientInfo(packet.getAddress(), packet.getPort()));
                System.out.println("🏎️ New Driver Joined: ID " + playerID);
            }

            // Track team choice
            ClientInfo client = clients.get(playerID);
            client.teamOrdinal = state.teamOrdinal;

            // Check if everyone is ready
            int readyCount = 0;
            for (ClientInfo c : clients.values()) {
                if (c.teamOrdinal != -1) readyCount++;
            }

            if (readyCount >= REQUIRED_PLAYERS && !raceStarted) {
                raceStarted = true;
                System.out.println("🏁 All drivers ready! Broadcasting START signal.");
                
                CarState startSignal = new CarState();
                startSignal.playerID = -999;
                startSignal.isRaceStarted = true;
                byte[] startData = startSignal.serialize();
                broadcastState(startData, startData.length, -1); // Broadcast to everyone
            }

            // Relay this state to all other drivers
            broadcastState(packet.getData(), packet.getLength(), playerID);
            
        } catch (Exception e) {
            // Ignore malformed
        }
    }

    private void broadcastState(byte[] data, int length, int senderID) {
        for (Integer id : clients.keySet()) {
            if (id != senderID) {
                ClientInfo info = clients.get(id);
                try {
                    DatagramPacket relay = new DatagramPacket(data, length, info.address, info.port);
                    socket.send(relay);
                } catch (Exception e) {
                    clients.remove(id);
                }
            }
        }
    }

    private static class ClientInfo {
        InetAddress address;
        int port;
        int teamOrdinal = -1;

        ClientInfo(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }

    public static void main(String[] args) {
        new GameServer().start();
    }
}
