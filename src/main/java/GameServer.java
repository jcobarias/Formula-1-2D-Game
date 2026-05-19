import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;

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

    private int requiredPlayers = -1; // Dynamic based on first client
    private boolean raceStarted = false;

    private void pruneDeadClients() {
        long now = System.currentTimeMillis();
        boolean removedAny = false;
        for (Map.Entry<Integer, ClientInfo> entry : clients.entrySet()) {
            if (now - entry.getValue().lastHeartbeat > 6000) { // 6-second timeout
                clients.remove(entry.getKey());
                System.out.println("🥀 Driver ID " + entry.getKey() + " timed out due to inactivity.");
                removedAny = true;
            }
        }
        if (removedAny && clients.isEmpty()) {
            System.out.println("♻️ Lobby empty. Resetting server state...");
            raceStarted = false;
            requiredPlayers = -1;
        }
    }

    private void handlePacket(DatagramPacket packet) {
        try {
            pruneDeadClients();

            ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
            if (buffer.remaining() < 4) return;

            int packetType = buffer.getInt();
            if (packetType == 0) { // Telemetry/CarState
                CarState state = CarState.deserialize(packet.getData(), packet.getOffset() + 4, packet.getLength() - 4);
                int playerID = state.playerID;

                // Register/Update client address
                if (!clients.containsKey(playerID)) {
                    clients.put(playerID, new ClientInfo(packet.getAddress(), packet.getPort()));
                    System.out.println("🏎️ New Driver Joined: ID " + playerID + " (Port: " + packet.getPort() + ")");

                    // Set server capacity based on first player's choice
                    if (requiredPlayers == -1 && state.requiredPlayers > 0) {
                        requiredPlayers = state.requiredPlayers;
                        System.out.println("🔧 Server capacity set to: " + requiredPlayers + " players.");
                    }
                } else {
                    clients.get(playerID).lastHeartbeat = System.currentTimeMillis();
                }

                // If any active client reports that they are NOT in a race, force reset server's raceStarted state
                if (!state.isRaceStarted && raceStarted) {
                    System.out.println("🔄 Active client reports lobby state. Resetting server raceStarted flag.");
                    raceStarted = false;
                }

                // Track team choice
                ClientInfo client = clients.get(playerID);
                if (client.teamOrdinal != state.teamOrdinal) {
                    client.teamOrdinal = state.teamOrdinal;
                    if (state.teamOrdinal != -1) {
                        System.out.println("✅ Driver " + playerID + " selected team ordinal: " + state.teamOrdinal);
                    }
                }

                // Check if everyone is ready (joined AND picked team)
                if (requiredPlayers != -1 && clients.size() >= requiredPlayers && !raceStarted) {
                    int readyCount = 0;
                    for (ClientInfo c : clients.values()) {
                        if (c.teamOrdinal != -1)
                            readyCount++;
                    }

                    if (readyCount >= requiredPlayers) {
                        raceStarted = true;
                        System.out.println("🏁 All " + requiredPlayers + " drivers ready! Broadcasting START signal.");

                        CarState startSignal = new CarState();
                        startSignal.playerID = -999;
                        startSignal.isRaceStarted = true;
                        startSignal.requiredPlayers = requiredPlayers;
                        byte[] startData = startSignal.serialize();

                        // Wrap start signal in Type 0 header
                        ByteBuffer startBuffer = ByteBuffer.allocate(4 + startData.length);
                        startBuffer.putInt(0);
                        startBuffer.put(startData);
                        byte[] finalStartData = startBuffer.array();

                        // Broadcast multiple times for reliability
                        for (int i = 0; i < 3; i++) {
                            broadcastState(finalStartData, finalStartData.length, -1);
                        }
                    } else {
                        // Periodic status log every 3 seconds
                        if (System.currentTimeMillis() % 3000 < 50) {
                            System.out.println(String.format("⏳ Lobby Status: [%d/%d Connected] | [%d/%d Ready]", 
                                clients.size(), requiredPlayers, readyCount, requiredPlayers));
                        }
                    }
                }

                // Relay this state to all other drivers
                broadcastState(packet.getData(), packet.getLength(), playerID);
            } else if (packetType == 1) { // ChatMessage
                if (buffer.remaining() >= 8) {
                    int senderID = buffer.getInt();
                    if (clients.containsKey(senderID)) {
                        clients.get(senderID).lastHeartbeat = System.currentTimeMillis();
                    }
                    // Relay chat packet to all other connected clients
                    broadcastState(packet.getData(), packet.getLength(), senderID);
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Packet Error from " + packet.getAddress() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastState(byte[] data, int length, int senderID) {
        for (Map.Entry<Integer, ClientInfo> entry : clients.entrySet()) {
            if (!entry.getKey().equals(senderID)) {
                ClientInfo info = entry.getValue();
                try {
                    DatagramPacket relay = new DatagramPacket(data, length, info.address, info.port);
                    socket.send(relay);
                } catch (Exception e) {
                    clients.remove(entry.getKey());
                    if (clients.isEmpty()) {
                        System.out.println("♻️ Lobby empty. Resetting server state...");
                        raceStarted = false;
                        requiredPlayers = -1;
                    }
                }
            }
        }
    }

    private static class ClientInfo {
        InetAddress address;
        int port;
        int teamOrdinal = -1;
        long lastHeartbeat;

        ClientInfo(InetAddress address, int port) {
            this.address = address;
            this.port = port;
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) {
        new GameServer().start();
    }
}
