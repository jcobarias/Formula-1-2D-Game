import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.animation.AnimationTimer;
import javafx.scene.text.*;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.geometry.*;
import javafx.beans.binding.Bindings;
import javafx.scene.transform.Scale;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.util.HashMap;
import javafx.scene.effect.DropShadow;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Map;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

enum GameState {
    MODE_SELECTION, STAGING, START_SCREEN, READY_WAIT, RACING, VICTORY, PAUSED, EXIT_CONFIRM, COUNTDOWN, PODIUM
}

class Confetti {
    double x, y, vx, vy, life;
    Color color;

    public Confetti(double x, double y) {
        this.x = x;
        this.y = y;
        this.vx = Math.random() * 4 - 2;
        this.vy = Math.random() * -5 - 2;
        this.life = 1.0;
        this.color = Color.color(Math.random(), Math.random(), Math.random());
    }
}

enum F1Team {
    MERCEDES("Mercedes-AMG", "#A6A6A6", "#00A19B", "/assets/mercedes.png"),
    FERRARI("Scuderia Ferrari", "#DC0000", "#FEF200", "/assets/ferrari.png"),
    RED_BULL("Red Bull Racing", "#0600EF", "#DC0000", "/assets/redbull.png"),
    MCLAREN("McLaren F1", "#FF8700", "#47C7FC", "/assets/mclaren.png"),
    ALPINE("Alpine F1", "#0090FF", "#E10600", "/assets/alpine.png");

    public final String fullName;
    public final String primary;
    public final String accent;
    public final String spritePath;

    F1Team(String fullName, String primary, String accent, String spritePath) {
        this.fullName = fullName;
        this.primary = primary;
        this.accent = accent;
        this.spritePath = spritePath;
    }
}

public class GameClient extends Application {
    private Car myCar = new Car();
    private InputHandler inputHandler = new InputHandler();
    private TrackRenderer trackRenderer = new TrackRenderer();
    private RaceTrack raceTrack = new RaceTrack();

    // Networking
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private int playerID = (int) (Math.random() * 10000); // Random ID for now
    private Map<Integer, Car> otherCars = new ConcurrentHashMap<>();
    private double lastSendTime = 0;
    private static final double SEND_INTERVAL = 1.0 / 30.0; // Send 30 times per second
    private long currentSequenceNumber = 0;
    private int lastKnownPlayerCount = -1;

    // Game States
    private GameState currentState = GameState.MODE_SELECTION; // Start here now
    private F1Team selectedTeam = F1Team.MERCEDES;
    private int TOTAL_LAPS = 3;
    private boolean isMultiplayer = false;
    private int REQUIRED_PLAYERS = 2;

    private double gameTime = 0;
    private double countdownTime = 0;
    private double currentLapTime = 0;
    private double lastLapTime = 0;
    private double bestLapTime = 0;
    private GameState previousState = GameState.MODE_SELECTION;

    // Chatbox System variables
    private ScrollPane chatScrollPane;
    private VBox chatMessageList;
    private TextField chatInput;
    private VBox chatContainer;
    private long lastChatActivityTime = 0;
    private boolean chatActive = false;

    private StackPane rootPane;
    private StackPane modeSelectionUI;
    private VBox stagingUI;
    private VBox stagingPlayerList;
    private Text stagingStatusText;
    private List<HBox> stagingPlayerSlots = new ArrayList<>();
    private VBox startScreenUI;
    private VBox readyWaitUI;
    private Text readyWaitStatusText;
    private VBox pauseMenuUI;
    private VBox exitConfirmUI;
    private VBox victoryScreenUI; // Victory Overlay

    // Camera & Cinematic
    private double cameraX = 0;
    private double cameraY = 0;
    private double orbitAngle = 0;
    private List<Confetti> confetti = new ArrayList<>();

    // Menu Navigation
    private int menuIndex = 0;
    private List<VBox> carCards = new ArrayList<>();
    private List<Button> pauseButtons = new ArrayList<>();
    private List<Button> victoryButtons = new ArrayList<>();
    private Button startScreenExitBtn;
    private Button startScreenBackBtn;
    private List<Button> exitModalButtons = new ArrayList<>();
    private int modalIndex = 0;

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane();
        this.rootPane = root;
        Canvas canvas = new Canvas(1920, 1080);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // UI Menus
        this.modeSelectionUI = createModeSelectionUI();
        this.stagingUI = createStagingUI();
        this.startScreenUI = createStartScreenUI();
        this.readyWaitUI = createReadyWaitUI();
        this.pauseMenuUI = createPauseMenuUI();
        this.exitConfirmUI = createExitConfirmUI();
        this.victoryScreenUI = createVictoryScreenUI();
        this.chatContainer = createChatUI();

        root.getChildren().addAll(canvas, modeSelectionUI, stagingUI, readyWaitUI, startScreenUI, pauseMenuUI,
                victoryScreenUI, exitConfirmUI, chatContainer);

        updateChatUIForCurrentState();

        // Wrap root in a scalable Group to fit any screen resolution dynamically
        Group scalableGroup = new Group(root);
        StackPane outerRoot = new StackPane(scalableGroup);
        outerRoot.setStyle("-fx-background-color: black;"); // Adds letterboxing

        Scene scene = new Scene(outerRoot, 1280, 720); // Starts at a comfortable 720p window

        // Bind the scale to the window's dimensions (maintaining 16:9 aspect ratio)
        Scale scale = new Scale();
        scale.xProperty().bind(Bindings.min(
                scene.widthProperty().divide(1920.0),
                scene.heightProperty().divide(1080.0)));
        scale.yProperty().bind(scale.xProperty());
        root.getTransforms().add(scale);

        scene.setOnKeyPressed(e -> {
            if (chatActive) {
                if (e.getCode() == KeyCode.ESCAPE) {
                    closeChat();
                }
                return;
            }

            if (e.getCode() == KeyCode.T && (currentState == GameState.RACING || currentState == GameState.COUNTDOWN)) {
                openChat();
                e.consume();
                return;
            }

            if (currentState == GameState.EXIT_CONFIRM) {
                handleExitConfirmKey(e.getCode());
                return;
            }

            if (e.getCode() == KeyCode.ESCAPE && currentState != GameState.START_SCREEN) {
                togglePause();
                return;
            }

            if (currentState == GameState.MODE_SELECTION) {
                handleModeSelectionKey(e.getCode());
                return;
            }

            if (currentState == GameState.START_SCREEN) {
                handleStartMenuKey(e.getCode());
                return;
            }

            if (currentState == GameState.PAUSED) {
                handlePauseMenuKey(e.getCode());
                return;
            }

            if (currentState == GameState.VICTORY) {
                handleVictoryMenuKey(e.getCode());
                return;
            }

            // Capture racing input
            inputHandler.handleKeyPressed(e);
            if (currentState != GameState.RACING)
                return;

            if (e.getCode() == KeyCode.M)
                myCar.isAutomatic = !myCar.isAutomatic;
            if (!myCar.isAutomatic) {
                if (e.getCode() == KeyCode.E && myCar.currentGear < myCar.MAX_GEAR)
                    myCar.currentGear++;
                if (e.getCode() == KeyCode.Q && myCar.currentGear > 1)
                    myCar.currentGear--;
            }
        });
        scene.setOnKeyReleased(e -> {
            if (chatActive)
                return;
            inputHandler.handleKeyReleased(e);
        });

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

                // Chat fade-out logic during race
                if (chatContainer != null && chatContainer.isVisible() && !chatActive
                        && (currentState == GameState.RACING || currentState == GameState.COUNTDOWN)) {
                    long timeSinceLastActivity = System.currentTimeMillis() - lastChatActivityTime;
                    if (timeSinceLastActivity > 6000) {
                        double currentOpacity = chatScrollPane.getOpacity();
                        if (currentOpacity > 0.1) {
                            chatScrollPane.setOpacity(Math.max(0.1, currentOpacity - deltaTime * 2.0));
                        }
                    }
                }

                // Handle States
                if (currentState == GameState.MODE_SELECTION) {
                    drawMenuBackground(gc);
                    return;
                }

                if (currentState == GameState.STAGING) {
                    drawMenuBackground(gc);

                    // Only update UI if count changed or every 0.5s
                    int currentCount = otherCars.size() + 1;
                    if (currentCount != lastKnownPlayerCount || System.currentTimeMillis() % 500 < 20) {
                        updateStagingUI();
                        lastKnownPlayerCount = currentCount;
                    }

                    // Send lobby heartbeat
                    lastSendTime += deltaTime;
                    if (lastSendTime >= SEND_INTERVAL) {
                        sendCarState();
                        lastSendTime = 0;
                    }

                    if (otherCars.size() + 1 >= REQUIRED_PLAYERS) {
                        stagingUI.setVisible(false);
                        startScreenUI.setVisible(true);
                        currentState = GameState.START_SCREEN;
                        updateMenuHighlighting();
                    }
                    return;
                }

                if (currentState == GameState.START_SCREEN) {
                    drawMenuBackground(gc);

                    // Send choice heartbeat
                    lastSendTime += deltaTime;
                    if (lastSendTime >= SEND_INTERVAL) {
                        sendCarState();
                        lastSendTime = 0;
                    }
                    return;
                }

                if (currentState == GameState.READY_WAIT) {
                    drawMenuBackground(gc);

                    int readyCount = 1; // Includes me
                    for (Car c : otherCars.values()) {
                        if (c.teamOrdinal != -1)
                            readyCount++;
                    }

                    readyWaitStatusText.setText(String.format("WAITING FOR DRIVERS TO SELECT TEAM (%d / %d)...",
                            readyCount, REQUIRED_PLAYERS));

                    // Keep broadcasting my selection so the server knows
                    lastSendTime += deltaTime;
                    if (lastSendTime >= SEND_INTERVAL) {
                        sendCarState();
                        lastSendTime = 0;
                    }

                    // Transition is now handled by the server's broadcast in receiveOtherCars
                    return;
                }

                if (currentState == GameState.COUNTDOWN) {
                    countdownTime += deltaTime;
                    runRaceLogic(0, gc); // 0 deltaTime for physics (frozen)
                    drawStartingLights(gc);
                    if (countdownTime > 4.0) {
                        currentState = GameState.RACING;
                        countdownTime = 0;
                    }
                    return;
                }

                if (currentState == GameState.PAUSED || currentState == GameState.EXIT_CONFIRM) {
                    runRaceLogic(0, gc); // Draw but don't update physics
                    return;
                }

                if (currentState == GameState.VICTORY) {
                    gc.getCanvas().setEffect(new javafx.scene.effect.BoxBlur(10, 10, 3));
                    updateVictoryUI(gc);
                    runRaceLogic(0, gc);
                    return;
                } else {
                    gc.getCanvas().setEffect(null);
                }

                if (currentState == GameState.PODIUM) {
                    countdownTime += deltaTime;
                    orbitAngle += deltaTime * 0.5;
                    updateConfetti(deltaTime);
                    cameraX = myCar.x - 1920 / 2.0 + Math.cos(orbitAngle) * 200;
                    cameraY = myCar.y - 1080 / 2.0 + Math.sin(orbitAngle) * 200;
                    runRaceLogic(0, gc);
                    drawConfetti(gc);
                    if (countdownTime > 5.0) {
                        currentState = GameState.VICTORY;
                        countdownTime = 0;
                    }
                    return;
                }

                // --- RACING STATE ---
                gameTime += deltaTime;
                currentLapTime += deltaTime;
                cameraX = 0;
                cameraY = 0; // Keep camera fixed during race
                runRaceLogic(deltaTime, gc);

                // Send heartbeat during race
                lastSendTime += deltaTime;
                if (lastSendTime >= SEND_INTERVAL) {
                    sendCarState();
                    lastSendTime = 0;
                }
            }
        }.start();

        stage.setTitle("GridRush F1 - Tournament Mode");
        stage.setScene(scene);
        stage.show();

        // Grab focus so keys work immediately
        root.requestFocus();
        updateMenuHighlighting();

        // Connect to server (Default to localhost for local testing)
        // We'll call this only if Multiplayer is selected
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        styleMenuButton(btn);
        return btn;
    }

    private HBox createPlayerSlot(String name) {
        HBox slot = new HBox(20);
        slot.setAlignment(Pos.CENTER_LEFT);
        slot.setPadding(new Insets(15, 30, 15, 30));
        slot.setStyle("-fx-background-color: #222; -fx-border-color: #444; -fx-border-width: 1;");
        slot.setPrefWidth(600);

        Circle statusCircle = new Circle(10, Color.GRAY);
        Text driverName = new Text(name);
        driverName.setFont(Font.font("Arial", 20));
        driverName.setFill(Color.GRAY);

        slot.getChildren().addAll(statusCircle, driverName);
        return slot;
    }

    private StackPane createModeSelectionUI() {
        StackPane rootContainer = new StackPane();
        InputStream fontStream = getClass().getResourceAsStream("/fonts/Formula1-Bold_web_0.ttf");
        Font f1Font = Font.loadFont(fontStream, 20);

        // 1. Checkerboard Background (Using your 80px tile setting)
        Pane checkerBackground = new Pane();
        int tileSize = 80;
        javafx.scene.image.WritableImage checkerImage = new javafx.scene.image.WritableImage(tileSize * 2,
                tileSize * 2);
        javafx.scene.image.PixelWriter writer = checkerImage.getPixelWriter();

        Color color1 = Color.web("#161616");
        Color color2 = Color.color(1.0, 1.0, 1.0);

        for (int y = 0; y < tileSize * 2; y++) {
            for (int x = 0; x < tileSize * 2; x++) {
                boolean isTile1 = (x < tileSize && y < tileSize) || (x >= tileSize && y >= tileSize);
                writer.setColor(x, y, isTile1 ? color1 : color2);
            }
        }

        checkerBackground.setBackground(new Background(new BackgroundImage(
                checkerImage,
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT,
                BackgroundSize.DEFAULT)));

        Pane overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(5, 5, 5, 0.70);"); // Slightly darkened for better button contrast

        // 2. Main layout container
        VBox menu = new VBox(40); // Adjusted spacing dynamically
        menu.setAlignment(Pos.CENTER);
        menu.setStyle("-fx-background-color: transparent;");

        // 3. EYE-CATCHING ARCADE TITLE (Double-Layered Neon & Fire Effect)
        StackPane titleStack = new StackPane();
        titleStack.setAlignment(Pos.CENTER);

        // Neon blue outline behind the main text
        Text titleShadow = new Text("GRIDRUSH F1");
        titleShadow.setFont(Font.font("Formula1 Display Bold", 175)); // Swapped to aggressive arcade Impact font
        // titleShadow.setRotate(-5); // Slanted like a real racing logo
        titleShadow.setFill(Color.TRANSPARENT);
        titleShadow.setStroke(Color.web("#00ffff")); // Cyan neon stroke
        titleShadow.setStrokeWidth(5);
        titleShadow.setTranslateY(6);
        titleShadow.setTranslateX(-6);
        titleShadow.setEffect(new javafx.scene.effect.Glow(0.8));

        // Main text with an orange-to-yellow fire gradient
        Text titleFront = new Text("GRIDRUSH F1");
        titleFront.setFont(Font.font("Formula1 Display Bold", 175));
        // titleFront.setRotate(-5);

        LinearGradient fireGradient = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#fff200")), // Bright Yellow
                new Stop(0.6, Color.web("#ff6600")), // Hot Orange
                new Stop(1.0, Color.web("#cc0000")) // Deep Red
        );
        titleFront.setFill(fireGradient);

        // Give the front text a fiery outer aura glow
        javafx.scene.effect.DropShadow fireGlow = new javafx.scene.effect.DropShadow();
        fireGlow.setColor(Color.web("#ff3300"));
        fireGlow.setRadius(20);
        fireGlow.setSpread(0.3);
        titleFront.setEffect(fireGlow);

        titleStack.getChildren().addAll(titleShadow, titleFront);

        Text subtitle = new Text("SELECT GAME MODE");
        subtitle.setFont(Font.font("Formula1 Display Bold", 26)); // Weightier subhead font
        subtitle.setFill(Color.WHITE);
        subtitle.setEffect(new javafx.scene.effect.DropShadow(5, Color.BLACK));

        Button singlePlayerBtn = createMenuButton("SINGLE PLAYER");
        Button multiPlayer2Btn = createMenuButton("MULTIPLAYER (2 PLAYERS)");
        Button multiPlayer4Btn = createMenuButton("MULTIPLAYER (4 PLAYERS)");
        Button exitBtn = createMenuButton("EXIT GAME");

        // Ensure buttons are wide enough so they never clip text (...)
        for (Button btn : new java.util.ArrayList<Button>(
                java.util.Arrays.asList(singlePlayerBtn, multiPlayer2Btn, multiPlayer4Btn, exitBtn))) {
            btn.setMinWidth(460);
            btn.setMinHeight(55);
        }

        // 4. CAR SHOWROOM WITH DYNAMIC HOVER EFFECTS
        HBox carsBox = new HBox(35);
        carsBox.setAlignment(Pos.CENTER);
        carsBox.setPadding(new Insets(30, 10, 20, 10));
        carsBox.setStyle("-fx-background-color: transparent;");

        for (F1Team team : F1Team.values()) {
            Image img = new Image(getClass().getResourceAsStream(team.spritePath));
            ImageView iv = new ImageView(img);
            iv.setFitWidth(260); // Sized slightly down to leave room for the scaling hover effect
            iv.setFitHeight(130);
            iv.setPreserveRatio(true);
            DropShadow outline = new DropShadow();
            outline.setColor(Color.WHITE);
            outline.setRadius(20);
            iv.setEffect(outline);

            // Wrap the image view in a StackPane container so the scaling/effects remain
            // smooth
            StackPane carContainer = new StackPane(iv);
            carContainer.setPadding(new Insets(10));
            carContainer.setUserData(team);

            // HOVER ENTER: Pop up (+15% scale) and cast a glowing shadow matching the
            // specific team color!
            carContainer.setOnMouseEntered(e -> {
                Color teamColor = Color.web(team.accent); // Pulls the accent hex color dynamically from your enum

                // Neon shadow glow effect
                javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
                glow.setColor(teamColor);
                glow.setRadius(35);
                glow.setSpread(0.45);
                carContainer.setEffect(glow);

                // Hardware accelerated scale up transition
                javafx.animation.ScaleTransition scaleUp = new javafx.animation.ScaleTransition(
                        javafx.util.Duration.millis(120), carContainer);
                scaleUp.setToX(1.15);
                scaleUp.setToY(1.15);
                scaleUp.play();
            });

            // HOVER EXIT: Cleanly shrink back down and remove the colorful aura shadow
            carContainer.setOnMouseExited(e -> {
                carContainer.setEffect(null);

                javafx.animation.ScaleTransition scaleDown = new javafx.animation.ScaleTransition(
                        javafx.util.Duration.millis(120), carContainer);
                scaleDown.setToX(1.0);
                scaleDown.setToY(1.0);
                scaleDown.play();
            });

            carsBox.getChildren().add(carContainer);
        }

        singlePlayerBtn.setOnAction(e -> startSinglePlayer());
        multiPlayer2Btn.setOnAction(e -> startMultiplayer(2));
        multiPlayer4Btn.setOnAction(e -> startMultiplayer(4));
        exitBtn.setOnAction(e -> showExitConfirm());

        menu.getChildren().addAll(titleStack, subtitle, singlePlayerBtn, multiPlayer2Btn, multiPlayer4Btn, exitBtn,
                carsBox);

        // 5. Group layers together safely
        rootContainer.getChildren().addAll(checkerBackground, overlay, menu);

        return rootContainer;
    }

    private void startSinglePlayer() {
        isMultiplayer = false;
        currentState = GameState.START_SCREEN;
        modeSelectionUI.setVisible(false);
        startScreenUI.setVisible(true);
        updateMenuHighlighting();
    }

    private VBox createStagingUI() {
        VBox menu = new VBox(40);
        menu.setAlignment(Pos.CENTER);
        menu.setStyle(
                "-fx-background-color: rgba(10, 10, 10, 0.95); -fx-border-color: cyan; -fx-border-width: 3; -fx-padding: 50;");
        menu.setMaxSize(1000, 750);
        menu.setVisible(false);

        Text title = new Text("F1 MULTIPLAYER LOBBY");
        title.setFont(Font.font("Arial Black", 60));
        title.setFill(Color.CYAN);
        title.setEffect(new javafx.scene.effect.Glow(0.5));

        stagingStatusText = new Text("WAITING FOR DRIVERS...");
        stagingStatusText.setFont(Font.font("Arial Bold", 24));
        stagingStatusText.setFill(Color.WHITE);

        stagingPlayerList = new VBox(15);
        stagingPlayerList.setAlignment(Pos.CENTER);

        Button leaveBtn = new Button("LEAVE LOBBY");
        styleMenuButton(leaveBtn);
        leaveBtn.setOnAction(e -> leaveStagingLobby());

        menu.getChildren().addAll(title, stagingStatusText, stagingPlayerList, leaveBtn);
        return menu;
    }

    private void updateStagingUI() {
        if (stagingUI == null || !stagingUI.isVisible())
            return;

        int currentCount = otherCars.size() + 1;
        stagingStatusText
                .setText(String.format("READY STATUS: %d / %d DRIVERS CONNECTED", currentCount, REQUIRED_PLAYERS));

        // Update Slot 1 (Local Player)
        if (!stagingPlayerSlots.isEmpty()) {
            HBox mySlot = stagingPlayerSlots.get(0);
            ((Circle) mySlot.getChildren().get(0)).setFill(Color.LIME);
            ((Text) mySlot.getChildren().get(1)).setText("DRIVER 1: YOU (ID: " + playerID + ")");
            ((Text) mySlot.getChildren().get(1)).setFill(Color.WHITE);
            mySlot.setStyle("-fx-background-color: #333; -fx-border-color: cyan; -fx-border-width: 2;");
        }

        // Update Other Slots
        List<Integer> ids = new ArrayList<>(otherCars.keySet());
        Collections.sort(ids);

        for (int i = 1; i < REQUIRED_PLAYERS; i++) {
            if (i >= stagingPlayerSlots.size())
                break;
            HBox slot = stagingPlayerSlots.get(i);
            Circle circle = (Circle) slot.getChildren().get(0);
            Text text = (Text) slot.getChildren().get(1);

            if (i - 1 < ids.size()) {
                circle.setFill(Color.LIME);
                text.setText("DRIVER " + (i + 1) + ": PLAYER (ID: " + ids.get(i - 1) + ")");
                text.setFill(Color.WHITE);
                slot.setStyle("-fx-background-color: #282828; -fx-border-color: #666; -fx-border-width: 1;");
            } else {
                circle.setFill(Color.GRAY);
                text.setText("DRIVER " + (i + 1) + ": WAITING...");
                text.setFill(Color.GRAY);
                slot.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333; -fx-border-width: 1;");
            }
        }
    }

    private VBox createStartScreenUI() {
        VBox menu = new VBox(40);
        menu.setAlignment(Pos.CENTER);
        menu.setStyle("-fx-background-color: rgba(0,0,0,0.8);");
        menu.setVisible(false); // Changed to false: hide until mode is selected

        // Text title = new Text("GRIDRUSH F1");
        // title.setFont(Font.font("Arial Black", 120));
        // title.setFill(Color.WHITE);
        // title.setStroke(Color.CYAN);
        // title.setStrokeWidth(2);

        Text sub = new Text("SELECT YOUR MACHINE");
        sub.setFont(Font.font("Formula1 Display Bold", 30));
        sub.setFill(Color.LIGHTGRAY);

        HBox carBox = new HBox(20);
        carBox.setAlignment(Pos.CENTER);

        for (F1Team team : F1Team.values()) {
            VBox card = new VBox(15);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(20));
            card.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #222, #111);" +
                            "-fx-border-color: " + team.accent + ";" +
                            "-fx-border-width: 3;" +
                            "-fx-background-radius: 18;" +
                            "-fx-border-radius: 18;");
            card.setPrefWidth(280);

            ImageView preview = new ImageView(
                    new Image(getClass().getResourceAsStream(team.spritePath)));

            preview.setFitWidth(220);
            preview.setFitHeight(110);
            preview.setPreserveRatio(true);

            DropShadow glow = new DropShadow();
            glow.setRadius(25);
            glow.setColor(Color.web(team.accent));

            preview.setEffect(glow);

            Text name = new Text(team.fullName);
            name.setFont(Font.font("Arial Bold", 18));
            name.setFill(Color.WHITE);

            Button joinBtn = new Button("DRIVE");
            joinBtn.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-weight: bold;");
            joinBtn.setOnAction(e -> {
                if (!isTeamTaken(team.ordinal())) {
                    myCar.teamOrdinal = team.ordinal();
                    startRace(team, menu);
                }
            });

            card.getChildren().addAll(preview, name, joinBtn);
            carBox.getChildren().add(card);
            carCards.add(card);

            // Mouse interaction for the card
            final int index = carCards.size() - 1;
            card.setOnMouseEntered(e -> {
                menuIndex = index;
                updateMenuHighlighting();
            });
            card.setOnMouseClicked(e -> {
                if (!isTeamTaken(team.ordinal())) {
                    myCar.teamOrdinal = team.ordinal();
                    startRace(team, menu);
                }
            });
        }
        updateMenuHighlighting();

        menu.getChildren().addAll(sub, carBox);

        // Add dedicated Back to Menu and Exit Buttons in an HBox at the bottom
        HBox bottomButtons = new HBox(30);
        bottomButtons.setAlignment(Pos.CENTER);

        this.startScreenBackBtn = new Button("BACK TO MENU");
        styleMenuButton(startScreenBackBtn);
        startScreenBackBtn.setOnAction(e -> returnToMainMenu());

        this.startScreenExitBtn = new Button("EXIT GAME");
        styleMenuButton(startScreenExitBtn);
        startScreenExitBtn.setOnAction(e -> showExitConfirm());

        bottomButtons.getChildren().addAll(startScreenBackBtn, startScreenExitBtn);
        menu.getChildren().add(bottomButtons);

        return menu;
    }

    private VBox createReadyWaitUI() {
        VBox menu = new VBox(30);
        menu.setAlignment(Pos.CENTER);
        menu.setStyle(
                "-fx-background-color: rgba(10, 10, 10, 0.95); -fx-border-color: cyan; -fx-border-width: 3; -fx-padding: 30;");
        menu.setMaxSize(800, 450);
        menu.setVisible(false);

        Text title = new Text("WAITING FOR OTHER DRIVERS");
        title.setFont(Font.font("Arial Black", 40));
        title.setFill(Color.CYAN);

        readyWaitStatusText = new Text("WAITING FOR DRIVERS TO SELECT TEAM...");
        readyWaitStatusText.setFont(Font.font("Arial", 24));
        readyWaitStatusText.setFill(Color.WHITE);

        javafx.scene.control.ProgressIndicator progress = new javafx.scene.control.ProgressIndicator();
        progress.setPrefSize(60, 60);
        progress.setStyle("-fx-progress-color: cyan;");

        Button leaveBtn = new Button("LEAVE LOBBY");
        styleMenuButton(leaveBtn);
        leaveBtn.setOnAction(e -> leaveStagingLobby());

        menu.getChildren().addAll(title, readyWaitStatusText, progress, leaveBtn);
        return menu;
    }

    private void leaveStagingLobby() {
        // Disconnect cleanly from the server
        if (socket != null) {
            socket.close();
            socket = null;
        }
        otherCars.clear();
        lastKnownPlayerCount = -1;

        // Toggle UI visibility
        if (stagingUI != null) {
            stagingUI.setVisible(false);
        }
        if (readyWaitUI != null) {
            readyWaitUI.setVisible(false);
        }
        if (startScreenUI != null) {
            startScreenUI.setVisible(false);
        }
        if (modeSelectionUI != null) {
            modeSelectionUI.setVisible(true);
        }

        // Reset game state back to Mode Selection
        currentState = GameState.MODE_SELECTION;
        menuIndex = 0;
        updateMenuHighlighting();
        updateChatUIForCurrentState();

        System.out.println("🚪 Left multiplayer lobby. Returned to Main Menu.");
    }

    private void returnToMainMenu() {
        if (startScreenUI != null) {
            startScreenUI.setVisible(false);
        }
        if (modeSelectionUI != null) {
            modeSelectionUI.setVisible(true);
        }
        currentState = GameState.MODE_SELECTION;
        menuIndex = 0;
        myCar.teamOrdinal = -1;
        updateMenuHighlighting();
        updateChatUIForCurrentState();
        System.out.println("🚪 Returned to Main Menu from car selection.");
    }

    private VBox createExitConfirmUI() {
        VBox modal = new VBox(30);
        modal.setAlignment(Pos.CENTER);
        modal.setStyle("-fx-background-color: rgba(0,0,0,0.9); -fx-border-color: cyan; -fx-border-width: 3;");
        modal.setMaxSize(600, 300);
        modal.setVisible(false);

        Text msg = new Text("ARE YOU SURE YOU WANT TO QUIT?");
        msg.setFont(Font.font("Arial Black", 24));
        msg.setFill(Color.WHITE);

        HBox btnBox = new HBox(40);
        btnBox.setAlignment(Pos.CENTER);

        Button yesBtn = new Button("YES, QUIT");
        yesBtn.setStyle("-fx-background-color: #900; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 20;");
        yesBtn.setOnAction(e -> System.exit(0));
        exitModalButtons.add(yesBtn);

        Button noBtn = new Button("NO, STAY");
        noBtn.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 20;");
        noBtn.setOnAction(e -> hideExitConfirm());
        exitModalButtons.add(noBtn);

        btnBox.getChildren().addAll(yesBtn, noBtn);
        modal.getChildren().addAll(msg, btnBox);
        return modal;
    }

    private VBox createChatUI() {
        chatContainer = new VBox(10);
        chatContainer.setAlignment(Pos.BOTTOM_LEFT);
        chatContainer.setMaxSize(350, 250);
        chatContainer.setPadding(new Insets(15));

        chatContainer.setStyle("-fx-background-color: rgba(10, 10, 10, 0.4); " +
                "-fx-background-radius: 12; -fx-border-radius: 12; " +
                "-fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-width: 1;");
        chatContainer.setVisible(false);

        chatMessageList = new VBox(6);
        chatMessageList.setStyle("-fx-background-color: transparent;");

        chatScrollPane = new ScrollPane(chatMessageList);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setPrefHeight(200);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScrollPane.setStyle(
                "-fx-background: transparent; -fx-background-color: transparent; -fx-viewport-background-color: transparent; -fx-border-color: transparent;");

        chatInput = new TextField();
        chatInput.setPromptText("[T] to talk...");
        chatInput.setStyle("-fx-background-color: rgba(10, 10, 10, 0.4); " +
                "-fx-text-fill: transparent; -fx-font-size: 16; " +
                "-fx-border-color: transparent; -fx-background-radius: 6; -fx-border-radius: 6;");
        chatInput.setDisable(true);
        chatInput.setEditable(false);

        // Capture ENTER to send message
        chatInput.setOnAction(e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                sendChatMessage(text);
                displayChatMessage(playerID, text);
            }
            closeChat();
        });

        // Focus listener for chat safety
        chatInput.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                chatActive = true;
                if (inputHandler != null) {
                    inputHandler.clear();
                }
            } else if (chatActive) {
                closeChat();
            }
        });

        chatContainer.getChildren().addAll(chatScrollPane, chatInput);
        return chatContainer;
    }

    private void displayChatMessage(int senderID, String message) {
        javafx.application.Platform.runLater(() -> {
            Text flowText = new Text();
            flowText.setFont(Font.font("Arial", FontWeight.BOLD, 15));

            String senderTag;
            if (senderID == playerID) {
                senderTag = "YOU: ";
                flowText.setFill(Color.CYAN);
            } else if (senderID == -999) {
                senderTag = "[SYSTEM]: ";
                flowText.setFill(Color.GOLD);
            } else {
                senderTag = "DRIVER <" + senderID + ">: ";
                flowText.setFill(Color.web("#ff007f"));
            }

            flowText.setText(senderTag + message);

            HBox msgBox = new HBox(flowText);
            msgBox.setPadding(new Insets(4, 8, 4, 8));
            msgBox.setStyle("-fx-background-color: rgba(20, 20, 20, 0.6); -fx-background-radius: 6;");

            chatMessageList.getChildren().add(msgBox);

            // Limit message list size to prevent memory leak / overflow
            if (chatMessageList.getChildren().size() > 50) {
                chatMessageList.getChildren().remove(0);
            }

            chatScrollPane.setVvalue(1.0);
            chatContainer.setOpacity(1.0);
            chatScrollPane.setOpacity(1.0);
            lastChatActivityTime = System.currentTimeMillis();
        });
    }

    private void sendChatMessage(String message) {
        if (socket == null)
            return;
        try {
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(12 + msgBytes.length);
            buffer.putInt(1); // Type 1: CHAT_MESSAGE
            buffer.putInt(playerID);
            buffer.putInt(msgBytes.length);
            buffer.put(msgBytes);

            byte[] data = buffer.array();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("Error sending chat message: " + e.getMessage());
        }
    }

    private void openChat() {
        chatActive = true;
        inputHandler.clear(); // Clear key presses to stop driving
        chatInput.setEditable(true);
        chatInput.setDisable(false);
        chatInput.requestFocus();
        chatInput.setStyle(
                "-fx-background-color: rgba(20, 20, 20, 0.85); -fx-text-fill: white; -fx-font-size: 16; -fx-border-color: cyan; -fx-border-width: 2;");
        chatContainer.setOpacity(1.0);
        chatScrollPane.setOpacity(1.0);
        lastChatActivityTime = System.currentTimeMillis();
    }

    private void closeChat() {
        chatActive = false;
        chatInput.setText("");
        if (rootPane != null) {
            rootPane.requestFocus();
        }

        if (currentState == GameState.RACING || currentState == GameState.COUNTDOWN) {
            chatInput.setEditable(false);
            chatInput.setDisable(true);
            chatInput.setStyle(
                    "-fx-background-color: rgba(10, 10, 10, 0.4); -fx-text-fill: transparent; -fx-font-size: 16; -fx-border-color: transparent;");
        } else {
            // Keep active and clickable in lobby states
            chatInput.setEditable(true);
            chatInput.setDisable(false);
            chatInput.setStyle(
                    "-fx-background-color: #222; -fx-text-fill: white; -fx-font-size: 16; -fx-border-color: #555;");
        }
        lastChatActivityTime = System.currentTimeMillis();
    }

    private void updateChatUIForCurrentState() {
        if (chatContainer == null)
            return;

        if (currentState == GameState.STAGING || currentState == GameState.READY_WAIT) {
            chatContainer.setVisible(true);
            chatContainer.setOpacity(1.0);
            chatScrollPane.setOpacity(1.0);
            StackPane.setAlignment(chatContainer, Pos.BOTTOM_LEFT);
            chatContainer.setAlignment(Pos.BOTTOM_LEFT);
            chatContainer.setMaxSize(400, 500);
            chatContainer.setTranslateX(30);
            chatContainer.setTranslateY(-30);
            chatContainer.setStyle("-fx-background-color: rgba(10, 10, 10, 0.85); " +
                    "-fx-background-radius: 18; -fx-border-radius: 18; " +
                    "-fx-border-color: cyan; -fx-border-width: 2;");
            chatInput.setPromptText("Type in lobby and press ENTER...");
            chatInput.setEditable(true);
            chatInput.setDisable(false);
            chatInput.setStyle(
                    "-fx-background-color: #222; -fx-text-fill: white; -fx-font-size: 16; -fx-border-color: #555;");
        } else if (currentState == GameState.COUNTDOWN || currentState == GameState.RACING) {
            chatContainer.setVisible(true);
            chatContainer.setOpacity(1.0);
            chatScrollPane.setOpacity(1.0);
            StackPane.setAlignment(chatContainer, Pos.BOTTOM_LEFT);
            chatContainer.setAlignment(Pos.BOTTOM_LEFT);
            chatContainer.setMaxSize(350, 250);
            chatContainer.setTranslateX(30);
            chatContainer.setTranslateY(-30);
            chatContainer.setStyle("-fx-background-color: transparent; " +
                    "-fx-background-radius: 12; -fx-border-radius: 12; " +
                    "-fx-border-color: transparent; -fx-border-width: 0;");
            chatInput.setPromptText("[T] to talk...");
            closeChat();
        } else {
            chatContainer.setVisible(false);
            closeChat();
        }
    }

    private void showExitConfirm() {
        this.previousState = currentState;
        currentState = GameState.EXIT_CONFIRM;
        exitConfirmUI.setVisible(true);
        modalIndex = 1; // Default to 'NO, STAY' for safety
        updateMenuHighlighting();
    }

    private void hideExitConfirm() {
        currentState = previousState;
        exitConfirmUI.setVisible(false);
        updateMenuHighlighting();
    }

    private VBox createPauseMenuUI() {
        VBox menu = new VBox(30);
        menu.setAlignment(Pos.CENTER);
        menu.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        menu.setVisible(false); // Hidden initially

        Text title = new Text("PAUSED");
        title.setFont(Font.font("Arial Black", 80));
        title.setFill(Color.WHITE);

        Button resumeBtn = new Button("RESUME");
        styleMenuButton(resumeBtn);
        resumeBtn.setOnAction(e -> togglePause());
        pauseButtons.add(resumeBtn);

        Button homeBtn = new Button("HOME");
        styleMenuButton(homeBtn);
        homeBtn.setOnAction(e -> {
            togglePause();
            resetGame();
        });
        pauseButtons.add(homeBtn);

        Button exitBtn = new Button("EXIT");
        styleMenuButton(exitBtn);
        exitBtn.setOnAction(e -> showExitConfirm());
        pauseButtons.add(exitBtn);

        menu.getChildren().addAll(title, resumeBtn, homeBtn, exitBtn);
        return menu;
    }

    private void handleModeSelectionKey(KeyCode code) {
        if (code == KeyCode.UP) {
            menuIndex = (menuIndex - 1 + 4) % 4;
        } else if (code == KeyCode.DOWN) {
            menuIndex = (menuIndex + 1) % 4;
        } else if (code == KeyCode.ENTER) {
            if (menuIndex == 0) { // Single Player
                startSinglePlayer();
            } else if (menuIndex == 1) { // Multiplayer (2 Players)
                startMultiplayer(2);
            } else if (menuIndex == 2) { // Multiplayer (4 Players)
                startMultiplayer(4);
            } else if (menuIndex == 3) { // Exit Game
                showExitConfirm();
            }
        }
        updateMenuHighlighting();
    }

    private void startMultiplayer(int players) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("localhost");
        dialog.setTitle("Connect to GridRush F1 Server");
        dialog.setHeaderText("Join Multiplayer Lobby");
        dialog.setContentText("Enter Server IP Address:");
        
        javafx.scene.control.DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #0d0d0d; " +
                            "-fx-border-color: cyan; " +
                            "-fx-border-width: 2px; " +
                            "-fx-text-fill: white;");
        
        // Custom styling for standard dialog components to fit neon theme
        dialogPane.lookup(".label").setStyle("-fx-text-fill: white; -fx-font-family: 'Arial'; -fx-font-size: 14px; -fx-font-weight: bold;");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: cyan; -fx-font-family: 'Arial'; -fx-font-size: 14px;");
        
        java.util.Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) {
            return; // User closed dialog, go back to menu
        }
        
        String ip = result.get().trim();
        if (ip.isEmpty()) {
            ip = "localhost";
        }

        int port = 9876;
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            ip = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number specified, defaulting to 9876.");
            }
        }

        this.REQUIRED_PLAYERS = players;
        this.isMultiplayer = true;
        this.otherCars.clear();

        // Rebuild dynamic lobby slots
        stagingPlayerSlots.clear();
        stagingPlayerList.getChildren().clear();
        for (int i = 0; i < REQUIRED_PLAYERS; i++) {
            HBox slot = createPlayerSlot(i == 0 ? "YOU" : "WAITING...");
            if (i == 0)
                slot.setStyle("-fx-background-color: #222; -fx-border-color: #0f0; -fx-border-width: 2;");
            stagingPlayerSlots.add(slot);
            stagingPlayerList.getChildren().add(slot);
        }

        currentState = GameState.STAGING;
        modeSelectionUI.setVisible(false);
        stagingUI.setVisible(true);
        updateChatUIForCurrentState();
        connectToServer(ip, port); // Connect to lobby at specified ip and port
    }

    private void handleStartMenuKey(KeyCode code) {
        if (code == KeyCode.LEFT) {
            if (menuIndex > carCards.size()) {
                menuIndex--; // Move from Exit to Back
            } else if (menuIndex == carCards.size()) {
                menuIndex = carCards.size() - 1; // Move from Back to last car card
            } else {
                menuIndex = (menuIndex - 1 + carCards.size()) % carCards.size();
            }
        } else if (code == KeyCode.RIGHT) {
            if (menuIndex == carCards.size() - 1) {
                menuIndex = carCards.size(); // Move from last car card to Back
            } else if (menuIndex == carCards.size()) {
                menuIndex = carCards.size() + 1; // Move from Back to Exit
            } else if (menuIndex == carCards.size() + 1) {
                menuIndex = 0; // Wrap from Exit to first car card
            } else {
                menuIndex = (menuIndex + 1) % carCards.size();
            }
        } else if (code == KeyCode.DOWN && menuIndex < carCards.size()) {
            menuIndex = carCards.size(); // Focus Back Button
        } else if (code == KeyCode.UP) {
            if (menuIndex >= carCards.size()) {
                menuIndex = 0; // Focus first car card
            } else {
                menuIndex = carCards.size(); // Focus Back Button
            }
        } else if (code == KeyCode.ENTER) {
            if (menuIndex < carCards.size()) {
                F1Team team = F1Team.values()[menuIndex];
                if (!isTeamTaken(team.ordinal())) {
                    myCar.teamOrdinal = team.ordinal();
                    startRace(team, startScreenUI);
                }
            } else if (menuIndex == carCards.size()) {
                returnToMainMenu();
            } else {
                showExitConfirm();
            }
        }
        updateMenuHighlighting();
    }

    private VBox createVictoryScreenUI() {
        VBox overlay = new VBox(30);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);"); // Dark Sleek Background
        overlay.setVisible(false);

        Text title = new Text("PODIUM FINISH!");
        title.setFont(Font.font("Arial Black", 100));
        title.setFill(Color.GOLD);
        title.setEffect(new javafx.scene.effect.DropShadow(20, Color.GOLD));

        Button replayBtn = new Button("REPLAY");
        replayBtn.setText(String.format("REPLAY (PB: %.2fs)", bestLapTime));
        styleMenuButton(replayBtn);
        replayBtn.setOnAction(e -> {
            victoryScreenUI.setVisible(false);
            resetGame();
            startRace(selectedTeam, startScreenUI); // Quick restart
        });
        victoryButtons.add(replayBtn);

        Button menuBtn = new Button("MAIN MENU");
        styleMenuButton(menuBtn);
        menuBtn.setOnAction(e -> {
            victoryScreenUI.setVisible(false);
            resetGame();
        });
        victoryButtons.add(menuBtn);

        Button exitBtn = new Button("EXIT");
        styleMenuButton(exitBtn);
        exitBtn.setOnAction(e -> showExitConfirm());
        victoryButtons.add(exitBtn);

        overlay.getChildren().addAll(title, replayBtn, menuBtn, exitBtn);
        return overlay;
    }

    private void updateVictoryUI(GraphicsContext gc) {
        if (!victoryScreenUI.isVisible()) {
            victoryScreenUI.setVisible(true);
            menuIndex = 0;
            // Update the Replay button text with the latest record
            victoryButtons.get(0).setText(String.format("REPLAY (BEST: %.2fs)", bestLapTime));
            updateMenuHighlighting();
        }
        // Background is now handled by VBox styling and Canvas effect
    }

    private void handleVictoryMenuKey(KeyCode code) {
        if (code == KeyCode.UP) {
            menuIndex = (menuIndex - 1 + victoryButtons.size()) % victoryButtons.size();
        } else if (code == KeyCode.DOWN) {
            menuIndex = (menuIndex + 1) % victoryButtons.size();
        } else if (code == KeyCode.ENTER) {
            victoryButtons.get(menuIndex).fire();
        }
        updateMenuHighlighting();
    }

    private void handleExitConfirmKey(KeyCode code) {
        if (code == KeyCode.LEFT || code == KeyCode.RIGHT) {
            modalIndex = 1 - modalIndex; // Toggle between 0 and 1
        } else if (code == KeyCode.ENTER) {
            exitModalButtons.get(modalIndex).fire();
        } else if (code == KeyCode.ESCAPE) {
            hideExitConfirm();
        }
        updateMenuHighlighting();
    }

    private void handlePauseMenuKey(KeyCode code) {
        if (code == KeyCode.UP) {
            menuIndex = (menuIndex - 1 + pauseButtons.size()) % pauseButtons.size();
        } else if (code == KeyCode.DOWN) {
            menuIndex = (menuIndex + 1) % pauseButtons.size();
        } else if (code == KeyCode.ENTER) {
            pauseButtons.get(menuIndex).fire();
        }
        updateMenuHighlighting();
    }

    private void updateMenuHighlighting() {
        // Mode Selection Highlight
        if (currentState == GameState.MODE_SELECTION && modeSelectionUI != null) {
            if (modeSelectionUI.getChildren().size() > 2 && modeSelectionUI.getChildren().get(2) instanceof VBox) {
                VBox menuVBox = (VBox) modeSelectionUI.getChildren().get(2);
                int buttonIdx = 0;
                for (int i = 0; i < menuVBox.getChildren().size(); i++) {
                    javafx.scene.Node node = menuVBox.getChildren().get(i);
                    if (node instanceof Button) {
                        Button b = (Button) node;
                        if (buttonIdx == menuIndex) {
                            b.setStyle(
                                    "-fx-background-color: cyan; -fx-text-fill: black; -fx-font-size: 24; -fx-font-weight: bold;");
                        } else {
                            b.setStyle(
                                    "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;");
                        }
                        buttonIdx++;
                    }
                }
            }
        }

        Set<Integer> takenOrdinals = getTakenTeamOrdinals();

        // Start Screen Highlight
        for (int i = 0; i < carCards.size(); i++) {
            VBox card = carCards.get(i);
            Button btn = (Button) card.getChildren().get(2);
            boolean taken = takenOrdinals.contains(i);

            if (i == menuIndex && currentState == GameState.START_SCREEN) {
                if (taken) {
                    card.setStyle(
                            "-fx-background-color: #111;" +
                                    "-fx-border-color: red;" +
                                    "-fx-border-width: 3;" +
                                    "-fx-opacity: 0.45;" +
                                    "-fx-background-radius: 18;" +
                                    "-fx-border-radius: 18;");
                    btn.setText("TAKEN");
                    btn.setDisable(true);
                } else {
                    card.setStyle("-fx-border-color: cyan; -fx-border-width: 5; -fx-background-color: #333;");
                    btn.setText("DRIVE");
                    btn.setDisable(false);
                }
            } else {
                if (taken) {
                    card.setStyle(
                            "-fx-border-color: #500; -fx-border-width: 2; -fx-background-color: #1a1a1a; -fx-opacity: 0.5;");
                    btn.setText("TAKEN");
                    btn.setDisable(true);
                } else {
                    card.setStyle("-fx-border-color: white; -fx-border-width: 2; -fx-background-color: #222;");
                    btn.setText("DRIVE");
                    btn.setDisable(false);
                }
            }
        }
        // Exit / Back Buttons Highlight
        if (startScreenBackBtn != null) {
            if (menuIndex == carCards.size() && currentState == GameState.START_SCREEN) {
                startScreenBackBtn.setStyle(
                        "-fx-background-color: cyan; -fx-text-fill: black; -fx-font-size: 24; -fx-font-weight: bold;");
            } else {
                startScreenBackBtn.setStyle(
                        "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;");
            }
        }
        if (startScreenExitBtn != null) {
            if (menuIndex == carCards.size() + 1 && currentState == GameState.START_SCREEN) {
                startScreenExitBtn.setStyle(
                        "-fx-background-color: cyan; -fx-text-fill: black; -fx-font-size: 24; -fx-font-weight: bold;");
            } else {
                startScreenExitBtn.setStyle(
                        "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;");
            }
        }
        // Modal Highlight
        for (int i = 0; i < exitModalButtons.size(); i++) {
            Button b = exitModalButtons.get(i);
            if (i == modalIndex && currentState == GameState.EXIT_CONFIRM) {
                b.setStyle(
                        "-fx-background-color: cyan; -fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 20; -fx-background-radius: 6; -fx-border-radius: 6;");
                b.setScaleX(1.15);
                b.setScaleY(1.15);
            } else {
                if (i == 0) { // YES button
                    b.setStyle(
                            "-fx-background-color: #900; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 20; -fx-background-radius: 6; -fx-border-radius: 6;");
                } else { // NO button
                    b.setStyle(
                            "-fx-background-color: #444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 20; -fx-background-radius: 6; -fx-border-radius: 6;");
                }
                b.setScaleX(1.0);
                b.setScaleY(1.0);
            }
        }
        // Pause Menu Highlight
        for (int i = 0; i < pauseButtons.size(); i++) {
            if (i == menuIndex && currentState == GameState.PAUSED) {
                pauseButtons.get(i).setStyle(
                        "-fx-background-color: cyan; -fx-text-fill: black; -fx-font-size: 24; -fx-font-weight: bold;");
            } else {
                pauseButtons.get(i).setStyle(
                        "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;");
            }
        }
        // Victory Menu Highlight
        for (int i = 0; i < victoryButtons.size(); i++) {
            if (i == menuIndex && currentState == GameState.VICTORY) {
                victoryButtons.get(i).setStyle(
                        "-fx-background-color: gold; -fx-text-fill: black; -fx-font-size: 24; -fx-font-weight: bold;");
            } else {
                victoryButtons.get(i).setStyle(
                        "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;");
            }
        }
    }

    private void styleMenuButton(Button btn) {
        btn.setPrefWidth(300);
        btn.setStyle(
                "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: cyan; -fx-text-fill: black; -fx-font-size: 24; -fx-font-weight: bold;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold; -fx-border-color: #555;"));
    }

    private void togglePause() {
        if (currentState == GameState.RACING) {
            currentState = GameState.PAUSED;
            menuIndex = 0; // Reset index for menu
            pauseMenuUI.setVisible(true);
            updateMenuHighlighting();
        } else if (currentState == GameState.PAUSED) {
            currentState = GameState.RACING;
            pauseMenuUI.setVisible(false);
        }
    }

    private void startRace(F1Team team, VBox menu) {
        this.selectedTeam = team;
        menu.setVisible(false);

        // F1 Two-Column Staggered Grid Positions (X, Y pairs)
        double[][] f1Grid = {
                { 960, 970 }, // P1
                { 910, 1010 }, // P2
                { 860, 970 }, // P3
                { 810, 1010 }, // P4
                { 760, 970 } // P5
        };

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < f1Grid.length; i++)
            indices.add(i);
        Collections.shuffle(indices);

        int chosenIdx = indices.get(0);
        myCar.x = f1Grid[chosenIdx][0];
        myCar.y = f1Grid[chosenIdx][1];
        myCar.angle = 0;
        myCar.velocity = 0;
        myCar.color = Color.web(team.primary);
        myCar.accentColor = Color.web(team.accent); // Assuming we add this field to Car
        myCar.lapCount = 0;
        myCar.nextCheckpoint = 1;

        if (isMultiplayer) {
            currentState = GameState.READY_WAIT;
            readyWaitUI.setVisible(true);
        } else {
            countdownTime = 0;
            currentState = GameState.COUNTDOWN;
        }
        updateChatUIForCurrentState();
    }

    private void drawStartingLights(GraphicsContext gc) {
        double startX = 1920 / 2 - 150;
        double startY = 150;

        // Background gantry
        gc.setFill(Color.web("#222"));
        gc.fillRoundRect(startX - 20, startY - 20, 340, 100, 20, 20);

        for (int i = 0; i < 5; i++) {
            // Light housing
            gc.setFill(Color.BLACK);
            gc.fillOval(startX + i * 60, startY, 50, 50);

            // Light logic (F1 style: 1 red light every 0.7s)
            if (countdownTime > (i + 1) * 0.7 && countdownTime < 3.5) {
                gc.setFill(Color.RED);
                gc.setEffect(new javafx.scene.effect.DropShadow(20, Color.RED));
                gc.fillOval(startX + i * 60 + 5, startY + 5, 40, 40);
                gc.setEffect(null);
            } else if (countdownTime >= 3.5) {
                // Lights out! (Wait 0.5s before state change)
                gc.setFill(Color.BLACK);
                gc.fillOval(startX + i * 60 + 5, startY + 5, 40, 40);
            }
        }
    }

    private void drawMenuBackground(GraphicsContext gc) {
        gc.setFill(Color.web("#0a0a0a"));
        gc.fillRect(0, 0, 1920, 1080);
        // Add some "vibe" lines or particles here if desired
    }

    private void drawUI(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 24));
        gc.fillText(String.format("Lap: %d/%d", myCar.lapCount + 1, TOTAL_LAPS), 20, 40);
        gc.fillText(String.format("Total Time: %.2fs", gameTime), 20, 70);

        // Lap Timers (Top Right)
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(String.format("Current Lap: %.2fs", currentLapTime), 1900, 40);
        if (lastLapTime > 0)
            gc.fillText(String.format("Last Lap: %.2fs", lastLapTime), 1900, 70);
        if (bestLapTime > 0) {
            gc.setFill(Color.GOLD);
            gc.fillText(String.format("BEST: %.2fs", bestLapTime), 1900, 100);
            gc.setFill(Color.WHITE);
        }

        // DRS Status
        String drsStatus = myCar.drsActive ? "OPEN"
                : (raceTrack.isInDrsZone(myCar.x, myCar.y) ? "AVAILABLE" : "LOCKED");
        gc.setFill(myCar.drsActive ? Color.LIME : (drsStatus.equals("AVAILABLE") ? Color.WHITE : Color.GRAY));
        gc.fillText("DRS: " + drsStatus, 1900, 130);

        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Arial Bold", 36));
        gc.fillText(String.format("GEAR: %d (%s)", myCar.currentGear, myCar.isAutomatic ? "AUTO" : "MANUAL"), 20, 130);
        gc.fillText(String.format("%.0f KPH", myCar.getKPH()), 20, 170);

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
        currentLapTime = 0;
        lastLapTime = 0;
        // bestLapTime = 0; // KEEP this for the session
        menuIndex = 0;

        if (startScreenUI != null)
            startScreenUI.setVisible(true);
        if (victoryScreenUI != null)
            victoryScreenUI.setVisible(false);
        currentState = GameState.START_SCREEN;
        updateMenuHighlighting();
    }

    public void connectToServer(String ip, int port) {
        try {
            otherCars.clear(); // Clear ghosts from previous sessions
            this.socket = new DatagramSocket();
            this.serverAddress = InetAddress.getByName(ip);
            this.serverPort = port;

            // Start receiver thread
            Thread receiverThread = new Thread(this::receiveOtherCars);
            receiverThread.setDaemon(true);
            receiverThread.start();

            System.out.println("Connected to server at " + ip + ":" + port + " with ID " + playerID);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveOtherCars() {
        byte[] buffer = new byte[1024];
        while (socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteBuffer byteBuf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
                if (byteBuf.remaining() < 4)
                    continue;

                int packetType = byteBuf.getInt();
                if (packetType == 0) { // Telemetry / CarState
                    CarState state = CarState.deserialize(packet.getData(), packet.getOffset() + 4,
                            packet.getLength() - 4);

                    System.out.println("DEBUG: Received state packet from ID " + state.playerID + " (Type: "
                            + (state.playerID == -999 ? "SERVER" : "PLAYER") + ")");

                    // Check for Server Authoritative Start Signal
                    if (state.playerID == -999 && state.isRaceStarted) {
                        if (currentState == GameState.READY_WAIT || currentState == GameState.STAGING
                                || currentState == GameState.START_SCREEN) {
                            javafx.application.Platform.runLater(() -> {
                                stagingUI.setVisible(false);
                                readyWaitUI.setVisible(false);
                                startScreenUI.setVisible(false);
                                countdownTime = 0;
                                currentState = GameState.COUNTDOWN;
                                updateChatUIForCurrentState(); // Move chat box to bottom-left overlay
                            });
                        }
                        continue;
                    }

                    if (state.playerID != this.playerID) {
                        // Sync lobby size with other players/server
                        if (state.requiredPlayers > 0 && this.REQUIRED_PLAYERS != state.requiredPlayers) {
                            this.REQUIRED_PLAYERS = state.requiredPlayers;
                        }

                        Car remoteCar = otherCars.computeIfAbsent(state.playerID, id -> {
                            Car c = new Car();
                            c.color = Color.GRAY;
                            c.x = state.x;
                            c.y = state.y;
                            c.targetX = state.x;
                            c.targetY = state.y;
                            c.targetAngle = state.angle;
                            return c;
                        });

                        // Ignore old packets
                        if (state.sequenceNumber > remoteCar.lastSequenceNumber) {
                            remoteCar.lastSequenceNumber = state.sequenceNumber;
                            remoteCar.targetX = state.x;
                            remoteCar.targetY = state.y;
                            remoteCar.targetAngle = state.angle;
                            remoteCar.velocity = state.velocity;

                            if (remoteCar.teamOrdinal != state.teamOrdinal) {
                                remoteCar.teamOrdinal = state.teamOrdinal; // Sync team choice
                                // Force UI update if someone picks a team
                                javafx.application.Platform.runLater(() -> updateMenuHighlighting());
                            }
                        }
                    }
                } else if (packetType == 1) { // ChatMessage
                    if (byteBuf.remaining() >= 8) {
                        int senderID = byteBuf.getInt();
                        int msgLen = byteBuf.getInt();
                        if (byteBuf.remaining() >= msgLen) {
                            byte[] msgBytes = new byte[msgLen];
                            byteBuf.get(msgBytes);
                            String message = new String(msgBytes, StandardCharsets.UTF_8);
                            displayChatMessage(senderID, message);
                        }
                    }
                }
            } catch (Exception e) {
                if (socket == null || socket.isClosed()) {
                    System.out.println("🔌 Disconnected from server (Socket closed gracefully).");
                    break;
                }
                System.err.println("CRITICAL: Error in receiver thread:");
                e.printStackTrace();
                break;
            }
        }
    }

    private boolean isTeamTaken(int ordinal) {
        for (Car c : otherCars.values()) {
            if (c.teamOrdinal == ordinal)
                return true;
        }
        return false;
    }

    private Set<Integer> getTakenTeamOrdinals() {
        Set<Integer> taken = new HashSet<>();
        for (Car c : otherCars.values()) {
            if (c.teamOrdinal != -1)
                taken.add(c.teamOrdinal);
        }
        return taken;
    }

    private void sendCarState() {
        if (socket == null)
            return;
        try {
            CarState state = new CarState();
            state.playerID = this.playerID;
            state.x = myCar.x;
            state.y = myCar.y;
            state.velocity = myCar.velocity;
            state.angle = myCar.angle;
            state.currentLap = myCar.lapCount;
            state.sequenceNumber = ++currentSequenceNumber;
            state.teamOrdinal = myCar.teamOrdinal;
            state.isRaceStarted = (currentState == GameState.RACING);
            state.requiredPlayers = REQUIRED_PLAYERS;

            byte[] serialized = state.serialize();
            ByteBuffer buffer = ByteBuffer.allocate(4 + serialized.length);
            buffer.putInt(0); // Type 0: CAR_STATE
            buffer.put(serialized);

            byte[] data = buffer.array();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (Exception e) {
            // Silently fail to keep game running
        }
    }

    private void spawnConfetti() {
        confetti.clear();
        for (int i = 0; i < 200; i++) {
            confetti.add(new Confetti(myCar.x, myCar.y));
        }
    }

    private void updateConfetti(double dt) {
        for (Confetti c : confetti) {
            c.x += c.vx;
            c.y += c.vy;
            c.vy += 0.1; // Gravity
            c.life -= dt * 0.2;
        }
    }

    private void drawConfetti(GraphicsContext gc) {
        gc.save();
        gc.translate(-cameraX, -cameraY);
        for (Confetti c : confetti) {
            gc.setFill(c.color.deriveColor(0, 1, 1, Math.max(0, c.life)));
            gc.fillRect(c.x, c.y, 8, 8);
        }
        gc.restore();
    }

    private void renderRaceScene(GraphicsContext gc) {
        runRaceLogic(0, gc); // Render-only call
    }

    private void runRaceLogic(double dt, GraphicsContext gc) {
        InputState input = (dt > 0) ? inputHandler.getCurrentInput() : new InputState();
        double oldX = myCar.x, oldY = myCar.y, oldAngle = myCar.angle;

        Vector2D newPos = PhysicsEngine.calculatePosition(myCar, input, dt);
        myCar.x = newPos.x;
        myCar.y = newPos.y;
        myCar.updateAutomaticGears();

        // DRS Zone Logic: Only allow DRS if in the zone
        boolean inZone = raceTrack.isInDrsZone(myCar.x, myCar.y);
        myCar.drsActive = input.drsActive && inZone;

        // Collision
        int hitIndex = PhysicsEngine.isColliding(myCar.getBounds(), raceTrack.getWalls());
        if (hitIndex != -1 && dt > 0) {
            myCar.x = oldX;
            myCar.y = oldY;
            myCar.angle = oldAngle;

            // Repulsion Logic: Nudge car away from walls to prevent sticking
            double vecX = myCar.x - 960, vecY = myCar.y - 540;
            double dist = Math.sqrt(vecX * vecX + vecY * vecY);
            if (dist > 0) {
                // Corrected: Wall 0 (Inner) pushes OUT, Wall 1 (Outer) pushes IN
                // Reduced force for a smoother feel
                double pushDir = (hitIndex == 0) ? 1.0 : -1.0;
                myCar.x += (vecX / dist) * pushDir * 5;
                myCar.y += (vecY / dist) * pushDir * 5;
            }
            myCar.velocity = 0;
        }

        myCar.isOffTrack = raceTrack.isOutside(myCar.x, myCar.y);

        // Checkpoints
        if (dt > 0) {
            Shape nextCP = raceTrack.getCheckpoints().get(myCar.nextCheckpoint);
            if (Shape.intersect(myCar.getBounds(), nextCP).getBoundsInLocal().getWidth() != -1) {
                if (myCar.nextCheckpoint == 0 && myCar.velocity > 50) {
                    myCar.lapCount++;
                    lastLapTime = currentLapTime;
                    if (bestLapTime == 0 || lastLapTime < bestLapTime)
                        bestLapTime = lastLapTime;
                    currentLapTime = 0;
                    if (myCar.lapCount >= TOTAL_LAPS) {
                        currentState = GameState.PODIUM;
                        countdownTime = 0;
                        spawnConfetti();
                    }
                }
                myCar.nextCheckpoint++;
                if (myCar.nextCheckpoint >= raceTrack.getCheckpoints().size())
                    myCar.nextCheckpoint = 0;
            }
        }

        // Smoothly interpolate other cars
        for (Car remoteCar : otherCars.values()) {
            // Linear Interpolation (Lerp)
            // Move 20% of the distance to target every frame (~12% per 1/60s)
            double lerpFactor = 0.2;
            remoteCar.x += (remoteCar.targetX - remoteCar.x) * lerpFactor;
            remoteCar.y += (remoteCar.targetY - remoteCar.y) * lerpFactor;

            // Handle angle wrap-around for smoother rotation
            double diff = remoteCar.targetAngle - remoteCar.angle;
            while (diff < -180)
                diff += 360;
            while (diff > 180)
                diff -= 360;
            remoteCar.angle += diff * lerpFactor;
        }

        // Render
        gc.save();
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillRect(0, 0, 1920, 1080);
        gc.translate(-cameraX, -cameraY);
        trackRenderer.drawTrack(gc, raceTrack);

        // Draw all cars (Me + Others)
        List<Car> allCars = new ArrayList<>();
        allCars.add(myCar);
        allCars.addAll(otherCars.values());
        trackRenderer.drawCars(gc, allCars);

        gc.restore();

        // Throttle state sending (e.g. 30Hz)
        if (dt > 0) {
            lastSendTime += dt;
            if (lastSendTime >= SEND_INTERVAL) {
                sendCarState();
                lastSendTime = 0;
            }
        }

        drawUI(gc);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// Handles user input
class InputHandler {
    private Set<KeyCode> pressedKeys = new HashSet<>();

    public void clear() {
        pressedKeys.clear();
    }

    public void handleKeyPressed(KeyEvent e) {
        pressedKeys.add(e.getCode());
    }

    public void handleKeyReleased(KeyEvent e) {
        pressedKeys.remove(e.getCode());
    }

    public InputState getCurrentInput() {
        InputState state = new InputState();
        // Support both Enter/W and Arrows for driving
        state.accelerating = pressedKeys.contains(KeyCode.ENTER) || pressedKeys.contains(KeyCode.UP);
        state.braking = pressedKeys.contains(KeyCode.S) || pressedKeys.contains(KeyCode.DOWN);
        state.turningLeft = pressedKeys.contains(KeyCode.A) || pressedKeys.contains(KeyCode.LEFT);
        state.turningRight = pressedKeys.contains(KeyCode.D) || pressedKeys.contains(KeyCode.RIGHT);
        state.drsActive = pressedKeys.contains(KeyCode.SPACE);
        return state;
    }

    public boolean isReplayPressed() {
        return pressedKeys.contains(KeyCode.R);
    }
}

// Renders the track and cars
class TrackRenderer {

    private final Map<Integer, Image> carSprites = new HashMap<>(); // added this code for sprite storage

    public TrackRenderer() {

        for (F1Team team : F1Team.values()) {

            try {

                Image img = new Image(
                        getClass().getResourceAsStream(team.spritePath));

                carSprites.put(team.ordinal(), img);

                System.out.println(
                        "Loaded sprite: " + team.spritePath);

            } catch (Exception e) {

                System.out.println(
                        "Failed to load sprite: " + team.spritePath);

                e.printStackTrace();
            }
        }
    }

    public void drawTrack(GraphicsContext gc, RaceTrack track) {
        // 1. Draw Asphalt (Outer Stadium)
        gc.setFill(Color.web("#333333"));
        gc.fillRoundRect(960 - 900, 540 - 500, 1800, 1000, 1000, 1000);

        // 2. Draw Grass Island (Inner Stadium)
        gc.setFill(Color.DARKGREEN);
        gc.fillRoundRect(960 - 800, 540 - 400, 1600, 800, 800, 800);

        // 3. Draw Track Lines (White boundaries)
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(5);
        gc.strokeRoundRect(960 - 900, 540 - 500, 1800, 1000, 1000, 1000);
        gc.strokeRoundRect(960 - 800, 540 - 400, 1600, 800, 800, 800);

        // 4. Draw Dashed Center Line
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(2);
        gc.setLineDashes(20.0, 20.0);
        gc.strokeRoundRect(960 - 850, 540 - 450, 1700, 900, 900, 900);
        gc.setLineDashes(null);

        // 5. Draw DRS Zone
        Rectangle drs = track.getDrsZone();
        gc.setStroke(Color.web("#00ff00", 0.5)); // Translucent neon green
        gc.setLineWidth(10);
        gc.strokeRect(drs.getX(), drs.getY(), drs.getWidth(), drs.getHeight());
        gc.setFill(Color.web("#00ff00", 0.5));
        gc.setFont(Font.font("Formula1 Display Bold", 40));
        gc.fillText("DRS ZONE", drs.getX() + 250, drs.getY() + 65);

        // 6. Draw Checkered Start/Finish Line (Bottom)
        double startX = 960;
        double topY = 940;
        double bottomY = 1040;
        double stripeWidth = 20;

        for (double y = topY; y < bottomY; y += stripeWidth) {
            gc.setFill(((int) ((y - topY) / stripeWidth) % 2 == 0) ? Color.WHITE : Color.BLACK);
            gc.fillRect(startX - 10, y, 10, stripeWidth);
            gc.setFill(((int) ((y - topY) / stripeWidth) % 2 == 0) ? Color.BLACK : Color.WHITE);
            gc.fillRect(startX, y, 10, stripeWidth);
        }

        // 6. Draw F1 Staggered Grid Slots
        gc.setStroke(Color.color(1, 1, 1, 0.4));
        gc.setLineWidth(2);
        double[][] f1Grid = {
                { 960, 970 }, { 910, 1010 }, { 860, 970 }, { 810, 1010 }, { 760, 970 }
        };
        for (double[] pos : f1Grid) {
            gc.strokeRect(pos[0] - 20, pos[1] - 15, 40, 30);
        }
    }

    public void drawCars(GraphicsContext gc, List<Car> allCars) { // added this code

        for (Car car : allCars) {

            gc.save();

            gc.translate(car.x, car.y);

            gc.rotate(car.angle);

            // SHADOW

            gc.setGlobalAlpha(0.25);

            gc.setFill(Color.BLACK);

            gc.fillOval(-22, -10, 44, 20);

            gc.setGlobalAlpha(1.0);

            // SPRITE RENDERING

            Image sprite = null;

            if (car.teamOrdinal >= 0 &&
                    car.teamOrdinal < F1Team.values().length) {

                sprite = carSprites.get(car.teamOrdinal);
            }

            if (sprite != null) {

                double width = 60;
                double height = 30;

                gc.drawImage(
                        sprite,
                        -width / 2,
                        -height / 2,
                        width,
                        height);

            } else {

                // FALLBACK RECTANGLE

                gc.setFill(car.color);
                gc.fillRoundRect(-20, -10, 40, 20, 8, 8);

                gc.setFill(car.accentColor);
                gc.fillRect(-5, -10, 10, 20);
            }

            // DRIVER NUMBER DOT

            gc.setFill(Color.WHITE);

            gc.fillOval(-4, -4, 8, 8);

            gc.restore();
        }
    }
}
