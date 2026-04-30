import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import java.util.ArrayList;
import java.util.List;

public class RaceTrack {
    private List<Shape> walls = new ArrayList<>();
    private List<Shape> checkpoints = new ArrayList<>();

    public RaceTrack() {
        // Track ellipses centered at (960, 540)
        Ellipse outerEllipse = new Ellipse(960, 540, 900, 500);
        Ellipse innerEllipse = new Ellipse(960, 540, 800, 400);
        
        // Inner Wall
        walls.add(innerEllipse); 
        
        // Outer Wall: A huge rectangle minus the outer track ellipse
        Rectangle world = new Rectangle(0, 0, 1920, 1080);
        Shape outerWall = Shape.subtract(world, outerEllipse);
        walls.add(outerWall);
        
        // Define 4 checkpoints around the track (scaled for 1080p)
        checkpoints.add(new Rectangle(960, 940, 10, 100));  // 0: Finish Line (Bottom)
        checkpoints.add(new Rectangle(1760, 490, 100, 10)); // 1: Right Side
        checkpoints.add(new Rectangle(960, 40, 10, 100));   // 2: Top Side
        checkpoints.add(new Rectangle(60, 490, 100, 10));   // 3: Left Side
    }

    public List<Shape> getWalls() {
        return walls;
    }

    public List<Shape> getCheckpoints() {
        return checkpoints;
    }
    
    public boolean isOutside(double x, double y) {
        double dx = (x - 960) / 900.0;
        double dy = (y - 540) / 500.0;
        return (dx * dx + dy * dy) > 1.0;
    }
}
