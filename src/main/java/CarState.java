import java.nio.ByteBuffer;

// Data Transfer Object for syncing car state
public class CarState {
    public int playerID;
    public double x, y;
    public double velocity;
    public double angle;
    public int currentLap;

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(40);
        buffer.putInt(playerID);
        buffer.putDouble(x);
        buffer.putDouble(y);
        buffer.putDouble(velocity);
        buffer.putDouble(angle);
        buffer.putInt(currentLap);
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
        return state;
    }
}
