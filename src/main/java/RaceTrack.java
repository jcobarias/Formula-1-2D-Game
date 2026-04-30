import javafx.scene.shape.*;
import java.util.ArrayList;
import java.util.List;

public class RaceTrack {
    private List<Shape> walls = new ArrayList<>();
    private List<Shape> checkpoints = new ArrayList<>();
    private Path outerPath;
    private Path innerPath;

    public RaceTrack() {
        // Track Stadium Shape (Rounded Rectangles)
        // Outer dimensions: 1800x1000, Inner: 1600x800
        Rectangle outerStadium = new Rectangle(960 - 900, 540 - 500, 1800, 1000);
        outerStadium.setArcWidth(1000);
        outerStadium.setArcHeight(1000);

        Rectangle innerStadium = new Rectangle(960 - 800, 540 - 400, 1600, 800);
        innerStadium.setArcWidth(800);
        innerStadium.setArcHeight(800);

        // Inner Wall
        walls.add(innerStadium);

        // Outer Wall: A huge rectangle minus the outer track stadium
        Rectangle world = new Rectangle(0, 0, 1920, 1080);
        Shape outerWall = Shape.subtract(world, outerStadium);
        walls.add(outerWall);

        // Define 4 checkpoints around the stadium track
        checkpoints.add(new Rectangle(960, 940, 10, 100)); // 0: Start/Finish Line (Bottom)
        checkpoints.add(new Rectangle(1760, 490, 100, 10)); // 1: Right Side
        checkpoints.add(new Rectangle(960, 40, 10, 100)); // 2: Top Side
        checkpoints.add(new Rectangle(60, 490, 100, 10)); // 3: Left Side
    }

    public List<Shape> getWalls() {
        return walls;
    }

    public List<Shape> getCheckpoints() {
        return checkpoints;
    }

    public boolean isOutside(double x, double y) {
        Rectangle outer = new Rectangle(960 - 900, 540 - 500, 1800, 1000);
        outer.setArcWidth(1000);
        outer.setArcHeight(1000);
        return !outer.contains(x, y);
    }
}
