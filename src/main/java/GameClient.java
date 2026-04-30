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
import javafx.scene.shape.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

// Main JavaFX client class
public class GameClient extends Application {
    private Car myCar = new Car();
    private InputHandler inputHandler = new InputHandler();
    private TrackRenderer trackRenderer = new TrackRenderer();
    private RaceTrack raceTrack = new RaceTrack();

    // Race State
    private int TOTAL_LAPS = 3;
    private double gameTime = 0;
    private boolean isFinished = false;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(1920, 1080);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        // Input Handling
        scene.setOnKeyPressed(e -> {
            inputHandler.handleKeyPressed(e);
            
            // Toggle Auto/Manual (M Key)
            if (e.getCode() == KeyCode.M) {
                myCar.isAutomatic = !myCar.isAutomatic;
            }

            // Handle Manual Gear Shifting (Only if in Manual mode)
            if (!myCar.isAutomatic) {
                if (e.getCode() == KeyCode.E && myCar.currentGear < myCar.MAX_GEAR) {
                    myCar.currentGear++;
                }
                if (e.getCode() == KeyCode.Q && myCar.currentGear > 1) {
                    myCar.currentGear--;
                }
            }
        });
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

                // 8. Replay Check (Must be above the 'isFinished' return!)
                if (isFinished && inputHandler.isReplayPressed()) {
                    resetGame();
                }

                if (isFinished) {
                    renderVictory(gc);
                    return;
                }

                // 0. Update Timer
                gameTime += deltaTime;

                // 1. Update Physics
                InputState input = inputHandler.getCurrentInput();

                // Save old position/angle in case of collision
                double oldX = myCar.x;
                double oldY = myCar.y;
                double oldAngle = myCar.angle;

                Vector2D newPos = PhysicsEngine.calculatePosition(myCar, input, deltaTime);
                myCar.x = newPos.x;
                myCar.y = newPos.y;

                // 1.5 Update Auto Gears
                myCar.updateAutomaticGears();

                // 2. Collision Check
                int hitIndex = PhysicsEngine.isColliding(myCar.getBounds(), raceTrack.getWalls());
                if (hitIndex != -1) {
                    // Revert to last safe position and angle
                    myCar.x = oldX;
                    myCar.y = oldY;
                    myCar.angle = oldAngle;
                    
                    // Precise Push:
                    // Index 0 = Inner Wall -> Push AWAY from center
                    // Index 1 = Outer Wall -> Push TOWARDS center
                    double vecX = myCar.x - 960; // New Center X (1920/2)
                    double vecY = myCar.y - 540; // New Center Y (1080/2)
                    double dist = Math.sqrt(vecX * vecX + vecY * vecY);
                    
                    if (dist > 0) {
                        double pushDir = (hitIndex == 0) ? 1.0 : -1.0;
                        myCar.x += (vecX / dist) * pushDir * 5;
                        myCar.y += (vecY / dist) * pushDir * 5;
                    }

                    myCar.velocity = 0; 
                }

                // 3. Off-Track Check
                myCar.isOffTrack = raceTrack.isOutside(myCar.x, myCar.y); // Simplified for now

                // 4. Checkpoint Progress
                Shape nextCP = raceTrack.getCheckpoints().get(myCar.nextCheckpoint);
                if (Shape.intersect(myCar.getBounds(), nextCP).getBoundsInLocal().getWidth() != -1) {
                    myCar.nextCheckpoint++;
                    if (myCar.nextCheckpoint >= raceTrack.getCheckpoints().size()) {
                        myCar.nextCheckpoint = 0;
                        myCar.lapCount++;
                        
                        // Check for Victory
                        if (myCar.lapCount >= TOTAL_LAPS) {
                            isFinished = true;
                        }
                    }
                }

                // 5. Render
                gc.setFill(Color.DARKGREEN); // Background
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                trackRenderer.drawTrack(gc, raceTrack);
                trackRenderer.drawCars(gc, List.of(myCar));

                // 6. Draw UI
                gc.setFill(Color.WHITE);
                gc.setFont(new javafx.scene.text.Font("Arial", 24));
                gc.fillText(String.format("Lap: %d/%d", myCar.lapCount + 1, TOTAL_LAPS), 20, 40);
                gc.fillText("Checkpoint: " + myCar.nextCheckpoint + "/4", 20, 70);
                gc.fillText(String.format("Time: %.2fs", gameTime), 20, 100);
                
                // Speedometer & Gear
                gc.setFill(Color.CYAN);
                gc.setFont(new javafx.scene.text.Font("Arial Bold", 36));
                gc.fillText(String.format("GEAR: %d (%s)", 
                            myCar.currentGear, 
                            myCar.isAutomatic ? "AUTO" : "MANUAL"), 20, 160);
                gc.fillText(String.format("%.0f KPH", myCar.getKPH()), 20, 200);
                gc.setFont(new javafx.scene.text.Font("Arial", 18));
                gc.fillText("Press M to Toggle Auto/Manual", 20, 230);

                // 7. Debug Terminal Output
                System.out.printf("X: %.2f | Y: %.2f | Vel: %.2f | Gear: %d | KPH: %.1f\n", 
                                  myCar.x, myCar.y, myCar.velocity, myCar.currentGear, myCar.getKPH());
            }
        }.start();

        stage.setTitle("GridRush - Local Test");
        stage.setScene(scene);
        stage.show();

        // Initial car position (Bottom track center)
        myCar.x = 960;
        myCar.y = 990;
    }

    private void renderVictory(GraphicsContext gc) {
        // Draw a dark overlay
        gc.setFill(new Color(0, 0, 0, 0.5)); 
        gc.fillRect(0, 0, 1920, 1080);
        
        gc.setFill(Color.GOLD);
        gc.setFont(new javafx.scene.text.Font("Arial Bold", 100));
        gc.fillText("FINISH!", 750, 450);
        
        gc.setFill(Color.WHITE);
        gc.setFont(new javafx.scene.text.Font("Arial", 50));
        gc.fillText(String.format("Final Time: %.2f seconds", gameTime), 680, 550);
        gc.fillText("Press R to Replay", 780, 650);
    }

    private void resetGame() {
        myCar.x = 960;
        myCar.y = 990;
        myCar.angle = 0;
        myCar.velocity = 0;
        myCar.lapCount = 0;
        myCar.nextCheckpoint = 0;
        gameTime = 0;
        isFinished = false;
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
        state.accelerating = pressedKeys.contains(KeyCode.ENTER);
        state.braking = pressedKeys.contains(KeyCode.S); // S for Reverse/Brake
        state.turningLeft = pressedKeys.contains(KeyCode.A);
        state.turningRight = pressedKeys.contains(KeyCode.D);
        return state;
    }

    public boolean isReplayPressed() {
        return pressedKeys.contains(KeyCode.R);
    }
}

// Renders the track and cars
class TrackRenderer {
    public void drawTrack(GraphicsContext gc, RaceTrack track) {
        // 1. Draw Asphalt (Outer Boundary)
        gc.setFill(Color.web("#333333")); // Dark grey asphalt
        gc.fillOval(960 - 900, 540 - 500, 1800, 1000);

        // 2. Draw Grass Island (Inner Boundary)
        gc.setFill(Color.DARKGREEN);
        gc.fillOval(960 - 800, 540 - 400, 1600, 800);

        // 3. Draw Track Lines (White edges)
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(960 - 900, 540 - 500, 1800, 1000); // Outer edge
        gc.strokeOval(960 - 800, 540 - 400, 1600, 800); // Inner edge

        // 4. Draw Start/Finish Line
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(10);
        // Drawing a line across the bottom part of the track (Y=940 to 1040 at X=960)
        gc.strokeLine(960, 940, 960, 1040);
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
