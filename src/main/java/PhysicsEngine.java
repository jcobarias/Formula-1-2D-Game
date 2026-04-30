import java.util.List;
import javafx.scene.shape.Shape;

// Utility class for physics calculations
public class PhysicsEngine {
    public static Vector2D calculatePosition(Car car, InputState input, double deltaTime) {
        // 1. Handle Acceleration/Braking
        if (input.accelerating) {
            car.velocity += car.acceleration * deltaTime;
        } else if (input.braking) {
            car.velocity -= car.brakingForce * deltaTime;
        }

        // 2. Handle Steering (only if moving)
        if (Math.abs(car.velocity) > 0.1) {
            double steeringDirection = 0;
            if (input.turningLeft) steeringDirection = -1;
            if (input.turningRight) steeringDirection = 1;

            // Faster = wider turns logic (optional, but requested in specs)
            double turnSpeed = car.rotationSpeed * (1.0 - Math.min(Math.abs(car.velocity) / 500.0, 0.5));
            car.angle += steeringDirection * turnSpeed * deltaTime;
        }

        // 3. Apply Friction
        car.velocity = handleFriction(car.velocity, car.isOffTrack);

        // 4. Calculate New Position
        double newX = car.x + Math.cos(Math.toRadians(car.angle)) * car.velocity * deltaTime;
        double newY = car.y + Math.sin(Math.toRadians(car.angle)) * car.velocity * deltaTime;

        return new Vector2D(newX, newY);
    }

    public static double handleFriction(double speed, boolean isOffTrack) {
        double friction = isOffTrack ? 0.95 : 0.99; // Simple decay
        if (isOffTrack) {
            // Apply heavy multiplier as per specs
            speed *= 0.3; // This might be too aggressive every frame, usually it's a cap or a higher decay
        }
        return speed * friction;
    }

    public static boolean isColliding(Shape carBounds, List<Shape> obstacles) {
        // TODO: Implement collision detection
        return false;
    }
}

// Simple 2D vector class
class Vector2D {
    public double x, y;
    public Vector2D(double x, double y) { this.x = x; this.y = y; }
}

// Placeholder for Car and InputState classes
class Car {
    public double x, y, angle, velocity;
    public double acceleration = 200.0;
    public double brakingForce = 300.0;
    public double rotationSpeed = 150.0;
    public boolean isOffTrack = false;
}

class InputState {
    public boolean accelerating;
    public boolean braking;
    public boolean turningLeft;
    public boolean turningRight;
}
