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

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(53); // Increased size
        buffer.putInt(playerID);
        buffer.putDouble(x);
        buffer.putDouble(y);
        buffer.putDouble(velocity);
        buffer.putDouble(angle);
        buffer.putInt(currentLap);
        buffer.putLong(sequenceNumber);
        buffer.putInt(teamOrdinal);
        buffer.put((byte) (isRaceStarted ? 1 : 0));
        return buffer.array();
    }

    public static CarState deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        CarState state = new CarState();
        state.playerID = buffer.getInt();
        state.x = buffer.getDouble();
        state.y = buffer.getDouble();
        state.velocity = buffer.getDouble();
        state.angle = buffer.getDouble();
        state.currentLap = buffer.getInt();
        if (buffer.remaining() >= 8) {
            state.sequenceNumber = buffer.getLong();
        }
        if (buffer.remaining() >= 4) {
            state.teamOrdinal = buffer.getInt();
        }
        if (buffer.remaining() >= 1) {
            state.isRaceStarted = buffer.get() == 1;
        }
        return state;
    }
}
