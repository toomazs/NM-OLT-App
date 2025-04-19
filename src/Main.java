import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;
import javafx.collections.ObservableList;

public class Main extends Application {
    private VBox rootLayout;
    private Stage primaryStage;
    private static TextArea terminalArea;
    private double xOffset = 0;
    private double yOffset = 0;
    private SSHManager sshManager;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.initStyle(StageStyle.UNDECORATED);

        try {
            Image icon = new Image(getClass().getResourceAsStream("/oltapp-icon.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Erro ao carregar o ícone: " + e.getMessage());
        }

        rootLayout = new VBox();
        rootLayout.setAlignment(Pos.TOP_CENTER);
        rootLayout.getStyleClass().add("root");

        rootLayout.getChildren().add(createTitleBar());
        showOLTSelectionScreen();

        Scene scene = new Scene(rootLayout, 900, 650);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        // Sombra na janela
        rootLayout.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.5)));

        // Movimento da janela
        setupWindowDrag(rootLayout);

        primaryStage.setTitle("Gerenciador de OLTs");
        primaryStage.setScene(scene);

        // Animação de fade-in
        primaryStage.setOpacity(0);
        primaryStage.show();

        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(primaryStage.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(400), new KeyValue(primaryStage.opacityProperty(), 1))
        );
        fadeIn.play();
    }

    private void setupWindowDrag(Node node) {
        node.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        node.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER);
        titleBar.setPadding(new Insets(5, 10, 5, 15));

        Label titleLabel = new Label("Gerenciador de OLTs");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");

        try {
            ImageView iconView = new ImageView(new Image(getClass().getResourceAsStream("/oltapp-icon.png")));
            iconView.setFitHeight(20);
            iconView.setFitWidth(20);
            iconView.setPreserveRatio(true);
            titleBar.getChildren().add(iconView);
            HBox.setMargin(iconView, new Insets(0, 8, 0, 0));
        } catch (Exception e) {
            // Ícone é opcional
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox windowControls = new HBox(5);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().addAll("window-button");
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));

        Button maximizeBtn = new Button("□");
        maximizeBtn.getStyleClass().addAll("window-button");
        maximizeBtn.setOnAction(e -> {
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(false);
                maximizeBtn.setText("□");
            } else {
                primaryStage.setMaximized(true);
                maximizeBtn.setText("❐");
            }
        });

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("window-button", "window-close-button");
        closeBtn.setOnAction(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(200), rootLayout);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(event -> {
                if (sshManager != null) sshManager.disconnect();
                primaryStage.close();
            });
            fade.play();
        });

        addButtonHoverEffects(minimizeBtn);
        addButtonHoverEffects(maximizeBtn);
        addButtonHoverEffects(closeBtn);

        windowControls.getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn);
        titleBar.getChildren().addAll(titleLabel, spacer, windowControls);

        return titleBar;
    }

    private void addButtonHoverEffects(Button button) {
        button.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(250), button);
            scale.setToX(1.1);
            scale.setToY(1.1);
            scale.play();
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(250), button);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
        });
    }

    private void showOLTSelectionScreen() {
        if (rootLayout.getChildren().size() > 1) {
            Node oldContent = rootLayout.getChildren().get(1);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), oldContent);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                rootLayout.getChildren().remove(oldContent);
                createAndShowOLTContent();
            });
            fadeOut.play();
        } else {
            createAndShowOLTContent();
        }
    }

    private void createAndShowOLTContent() {
        Label title = new Label("Gerenciador de OLTs");
        title.getStyleClass().add("title");

        Glow glow = new Glow(0.3);
        title.setEffect(glow);

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(25);
        cardsPane.setVgap(25);
        cardsPane.setPadding(new Insets(25));
        cardsPane.setAlignment(Pos.CENTER);
        cardsPane.getStyleClass().add("scroll-content");

        for (OLT olt : OLTList.getOLTs()) {
            VBox card = createOLTCard(olt);
            card.setOpacity(0);
            card.setTranslateY(20);
            cardsPane.getChildren().add(card);
        }

        ScrollPane scrollPane = new ScrollPane(cardsPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportHeight(550);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(15, title, scrollPane);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        rootLayout.getChildren().add(content);

        FadeTransition titleFade = new FadeTransition(Duration.millis(400), title);
        titleFade.setFromValue(0);
        titleFade.setToValue(1);

        TranslateTransition titleSlide = new TranslateTransition(Duration.millis(400), title);
        titleSlide.setFromY(-20);
        titleSlide.setToY(0);

        ParallelTransition titleAnimation = new ParallelTransition(titleFade, titleSlide);
        titleAnimation.play();

        animateCardsSequentially(cardsPane.getChildren(), 50);
    }

    private void animateCardsSequentially(ObservableList<Node> nodes, int delay) {
        Timeline timeline = new Timeline();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            KeyFrame kf1 = new KeyFrame(Duration.millis(i * delay),
                    new KeyValue(node.opacityProperty(), 0),
                    new KeyValue(node.translateYProperty(), 20)
            );

            KeyFrame kf2 = new KeyFrame(Duration.millis(i * delay + 300),
                    new KeyValue(node.opacityProperty(), 1),
                    new KeyValue(node.translateYProperty(), 0)
            );

            timeline.getKeyFrames().addAll(kf1, kf2);
        }

        timeline.play();
    }

    private VBox createOLTCard(OLT olt) {
        VBox card = new VBox(10);
        card.getStyleClass().add("olt-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(200, 130);

        Rectangle clip = new Rectangle(200, 130);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        card.setClip(clip);

        Label nameLabel = new Label(olt.name.replace("_", " "));
        nameLabel.getStyleClass().add("olt-name");

        Label ipLabel = new Label(olt.ip);
        ipLabel.getStyleClass().add("olt-ip");

        Button connectBtn = new Button("Conectar");
        connectBtn.getStyleClass().add("connect-btn");
        connectBtn.setOnAction(e -> {
            if (sshManager != null) sshManager.disconnect();
            ScaleTransition clickEffect = new ScaleTransition(Duration.millis(100), card);
            clickEffect.setToX(0.95);
            clickEffect.setToY(0.95);
            clickEffect.setAutoReverse(true);
            clickEffect.setCycleCount(2);
            clickEffect.setOnFinished(event -> showSSHTerminal(olt));
            clickEffect.play();
        });

        addButtonHoverAnimation(connectBtn);

        card.getChildren().addAll(nameLabel, ipLabel, connectBtn);
        return card;
    }

    private void addButtonHoverAnimation(Button button) {
        button.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(300), button);
            scale.setToX(1.05);
            scale.setToY(1.05);
            scale.play();
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(300), button);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
        });
    }

    private void showSSHTerminal(OLT olt) {
        if (rootLayout.getChildren().size() > 1) {
            Node oldContent = rootLayout.getChildren().get(1);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), oldContent);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                rootLayout.getChildren().remove(oldContent);
                createAndShowTerminal(olt);
            });
            fadeOut.play();
        } else {
            createAndShowTerminal(olt);
        }
    }

    private void createAndShowTerminal(OLT olt) {
        Label title = new Label("Terminal - " + olt.name.replace("_", " "));
        title.getStyleClass().add("title");

        terminalArea = new TextArea();
        terminalArea.setEditable(false);
        terminalArea.getStyleClass().add("text-area");
        terminalArea.setPrefHeight(500);

        StringBuilder commandBuffer = new StringBuilder();

        terminalArea.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    String command = commandBuffer.toString().trim();
                    if (!command.isEmpty() && sshManager != null) {
                        sshManager.sendCommand(command);
                    }
                    commandBuffer.setLength(0);
                    appendToTerminal("\n");
                    event.consume();
                    break;

                case BACK_SPACE:
                    if (commandBuffer.length() > 0) {
                        commandBuffer.setLength(commandBuffer.length() - 1);
                        String currentText = terminalArea.getText();
                        terminalArea.setText(currentText.substring(0, currentText.length() - 1));
                        terminalArea.positionCaret(terminalArea.getLength());
                    }
                    event.consume();
                    break;

                case TAB:
                    if (sshManager != null) {
                        sshManager.sendCommand("\t");
                    }
                    event.consume();
                    break;

                default:
                    String input = event.getText();
                    if (input.length() == 1 && !event.isControlDown()) {
                        commandBuffer.append(input);
                        appendToTerminal(input);
                    }
                    event.consume();
            }
        });

        Button backButton = new Button("Voltar para OLTs");
        backButton.getStyleClass().add("connect-btn");
        addButtonHoverAnimation(backButton);
        backButton.setOnAction(e -> {
            if (sshManager != null) {
                sshManager.disconnect();
                sshManager = null;
            }
            showOLTSelectionScreen();
        });

        Button interruptButton = new Button("Interromper Conexão");
        interruptButton.getStyleClass().add("connect-btn");
        interruptButton.setStyle("-fx-background-color: #ff6b6b;");
        addButtonHoverAnimation(interruptButton);
        interruptButton.setOnAction(event -> {
            appendToTerminal("\nInterrompendo conexão atual...\n");
            if (sshManager != null) {
                sshManager.disconnect();
                sshManager = null;
            }
            appendToTerminal("Terminal interrompido.\n");
        });

        HBox buttonBar = new HBox(15, backButton, interruptButton);
        buttonBar.setAlignment(Pos.CENTER);

        VBox terminalLayout = new VBox(10, title, terminalArea, buttonBar);
        terminalLayout.setAlignment(Pos.TOP_CENTER);
        terminalLayout.setPadding(new Insets(20));
        terminalLayout.setOpacity(0);

        rootLayout.getChildren().add(terminalLayout);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), terminalLayout);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        sshManager = new SSHManager(outputChunk -> {
            Platform.runLater(() -> {
                appendToTerminal(outputChunk);
                String lower = outputChunk.toLowerCase();
                if (lower.contains("---- more") || lower.contains("<cr>") || lower.contains("<k>")) {
                    sshManager.sendCommand(" ");
                }
            });
        });

        sshManager.connect(olt.ip, "suporte", "@Suporte123288", terminalArea);
    }

    public static void appendToTerminal(String text) {
        Platform.runLater(() -> {
            terminalArea.appendText(text);
            terminalArea.positionCaret(terminalArea.getLength());
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
