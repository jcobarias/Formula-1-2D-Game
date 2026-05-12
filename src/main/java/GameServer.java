import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private static final int PORT = 12345;
    // Map to store all player data. ConcurrentHashMap is vital for multiplayer.
    private Map<Integer, CarState> playerStates = new ConcurrentHashMap<>();
    private List<ObjectOutputStream> clientStreams = new ArrayList<>();
    private int nextPlayerId = 1;

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("F1 Server started on port " + PORT);
            
            // 1. Start the BROADCAST loop in a separate thread
            new Thread(this::startBroadcastLoop).start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                int id = nextPlayerId++;
                System.out.println("Player " + id + " connected!");
                
                // 2. Start a thread for this specific client
                new ClientHandler(clientSocket, id).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // This pushes the state to ALL clients ~60 times per second
    private void startBroadcastLoop() {
        while (true) {
            try {
                broadcastState();
                Thread.sleep(16); // Approx 60 FPS
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    public synchronized void broadcastState() {
        // We send the current map of ALL cars to every connected client
        for (ObjectOutputStream out : new ArrayList<>(clientStreams)) {
            try {
                out.reset(); // Clear cache to ensure fresh data
                out.writeObject(new ArrayList<>(playerStates.values()));
                out.flush();
            } catch (IOException e) { /* Handle disconnects here */ }
        }
    }

    // --- INNER CLASS TO HANDLE INDIVIDUAL PLAYERS ---
    private class ClientHandler extends Thread {
        private Socket socket;
        private int playerId;

        public ClientHandler(Socket socket, int id) {
            this.socket = socket;
            this.playerId = id;
        }

        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                
                synchronized(GameServer.this) { clientStreams.add(out); }
                
                // Tell the client what their ID is
                out.writeInt(playerId);
                out.flush();

                while (true) {
                    // Receive car position from the client
                    CarState state = (CarState) in.readObject();
                    playerStates.put(playerId, state);
                }
            } catch (Exception e) {
                System.out.println("Player " + playerId + " disconnected.");
                playerStates.remove(playerId);
            }
        }
    }

    public static void main(String[] args) {
        new GameServer().listen();
    }
}
