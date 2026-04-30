import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.Scene;
import javafx.animation.AnimationTimer;
import javafx.scene.paint.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

// Main JavaFX client class
public class GameClient extends Application {
    private Car myCar = new Car();
    private InputHandler inputHandler = new InputHandler();
    private TrackRenderer trackRenderer = new TrackRenderer();

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(800, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        // Input Handling
        scene.setOnKeyPressed(inputHandler::handleKeyPressed);
        scene.setOnKeyReleased(inputHandler::handleKeyReleased);

        // Game Loop (approx 60 FPS)
        new AnimationTimer() {
            private long lastTime = 0;

            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double deltaTime = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                // 1. Update Physics
                InputState input = inputHandler.getCurrentInput();
                Vector2D newPos = PhysicsEngine.calculatePosition(myCar, input, deltaTime);
                myCar.x = newPos.x;
                myCar.y = newPos.y;

                // 2. Render
                gc.setFill(Color.DARKGREEN); // Background
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                trackRenderer.drawTrack(gc, null);
                trackRenderer.drawCars(gc, List.of(myCar));
            }
        }.start();

        stage.setTitle("GridRush - Local Test");
        stage.setScene(scene);
        stage.show();

        // Initial car position
        myCar.x = 400;
        myCar.y = 300;
    }

    public void connectToServer(String ip, int port) {
        // TODO: Initialize socket connection
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// Handles user input
class InputHandler {
    private Set<KeyCode> pressedKeys = new HashSet<>();

    public void handleKeyPressed(KeyEvent e) {
        pressedKeys.add(e.getCode());
    }

    public void handleKeyReleased(KeyEvent e) {
        pressedKeys.remove(e.getCode());
    }

    public InputState getCurrentInput() {
        InputState state = new InputState();
        state.accelerating = pressedKeys.contains(KeyCode.UP) || pressedKeys.contains(KeyCode.W);
        state.braking = pressedKeys.contains(KeyCode.DOWN) || pressedKeys.contains(KeyCode.S);
        state.turningLeft = pressedKeys.contains(KeyCode.LEFT) || pressedKeys.contains(KeyCode.A);
        state.turningRight = pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D);
        return state;
    }
}

// Renders the track and cars
class TrackRenderer {
    public void drawTrack(GraphicsContext gc, RaceTrack track) {
        // Basic track placeholder: a grey oval
        gc.setStroke(Color.GRAY);
        gc.setLineWidth(50);
        gc.strokeOval(100, 100, 600, 400);
    }

    public void drawCars(GraphicsContext gc, List<Car> allCars) {
        for (Car car : allCars) {
            gc.save();
            gc.translate(car.x, car.y);
            gc.rotate(car.angle);

            // Draw a simple car shape (rectangle with a "front" indicator)
            gc.setFill(Color.RED);
            gc.fillRect(-15, -10, 30, 20);
            gc.setFill(Color.BLACK);
            gc.fillRect(10, -5, 5, 10); // Front lights/nose

            gc.restore();
        }
    }
}

// Placeholder for RaceTrack class
class RaceTrack {}
