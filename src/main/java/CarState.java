import java.nio.ByteBuffer;

// Data Transfer Object for syncing car state
public class CarState {
    public int playerID;
    public double x, y;
    public double velocity;
    public double angle;
    public int currentLap;
    public long sequenceNumber;
    public int teamOrdinal = -1; // -1 means no team selected yet
    public boolean isRaceStarted = false;
    public int requiredPlayers = -1; // New field for dynamic lobby capacity

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(57);
        buffer.putInt(playerID);
        buffer.putDouble(x);
        buffer.putDouble(y);
        buffer.putDouble(velocity);
        buffer.putDouble(angle);
        buffer.putInt(currentLap);
        buffer.putLong(sequenceNumber);
        buffer.putInt(teamOrdinal);
        buffer.put((byte) (isRaceStarted ? 1 : 0));
        buffer.putInt(requiredPlayers);
        return buffer.array();
    }

    public static CarState deserialize(byte[] data) {
        return deserialize(data, 0, data.length);
    }

    public static CarState deserialize(byte[] data, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
        CarState state = new CarState();
        if (buffer.remaining() < 4) return state;
        
        state.playerID = buffer.getInt();
        state.x = buffer.getDouble();
        state.y = buffer.getDouble();
        state.velocity = buffer.getDouble();
        state.angle = buffer.getDouble();
        state.currentLap = buffer.getInt();
        state.sequenceNumber = buffer.getLong();
        state.teamOrdinal = buffer.getInt();
        state.isRaceStarted = buffer.get() == 1;
        
        if (buffer.remaining() >= 4) {
            state.requiredPlayers = buffer.getInt();
        }
        
        return state;
    }
}
