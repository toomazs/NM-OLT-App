import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import javafx.stage.StageStyle;

public class Main extends Application {
    private VBox rootLayout;
    private Stage primaryStage;
    private static TextArea terminalArea;
    private static StringBuilder commandBuffer = new StringBuilder();
    private static int lastCommandPosition = 0;
    private double xOffset = 0;
    private double yOffset = 0;

    static {
        Font.loadFont(Main.class.getResourceAsStream("/fonts/Inter-Regular.ttf"), 12);
        Font.loadFont(Main.class.getResourceAsStream("/fonts/Inter-Bold.ttf"), 12);
    }


    // Abaixo possui + 400 horas de puro odio e codigo ao som de phonk!
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

        rootLayout.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        rootLayout.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });

        primaryStage.setTitle("Gerenciador de OLTs");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER);
        titleBar.setPadding(new Insets(5, 10, 5, 15));

        Label titleLabel = new Label("Gerenciador de OLTs");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");

        // spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // controles da janela
        HBox windowControls = new HBox(5);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        // botao minimiza o bagui
        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().add("window-button");
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));

        // botao maximiza o bagui
        Button maximizeBtn = new Button("□");
        maximizeBtn.getStyleClass().add("window-button");
        maximizeBtn.setOnAction(e -> {
            if (primaryStage.isMaximized()) {
                primaryStage.setMaximized(false);
                maximizeBtn.setText("□");
            } else {
                primaryStage.setMaximized(true);
                maximizeBtn.setText("❐");
            }
        });

        // botao q fecha
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("window-button");
        closeBtn.getStyleClass().add("window-close-button");
        closeBtn.setOnAction(e -> primaryStage.close());

        windowControls.getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn);
        titleBar.getChildren().addAll(titleLabel, spacer, windowControls);

        return titleBar;
    }

    private void showOLTSelectionScreen() {
        if (rootLayout.getChildren().size() > 1) {
            rootLayout.getChildren().remove(1, rootLayout.getChildren().size());
        }

        Label title = new Label("Gerenciador de OLTs");
        title.getStyleClass().add("title");

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(20);
        cardsPane.setVgap(20);
        cardsPane.setPadding(new Insets(20));
        cardsPane.setAlignment(Pos.CENTER);
        cardsPane.getStyleClass().add("scroll-content");

        for (OLT olt : OLTList.getOLTs()) {
            VBox card = createOLTCard(olt);
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

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        rootLayout.getChildren().addAll(title, scrollPane);
    }

    private VBox createOLTCard(OLT olt) {
        VBox card = new VBox(10);
        card.getStyleClass().add("olt-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(200, 120);

        Label nameLabel = new Label(olt.name.replace("_", " "));
        nameLabel.getStyleClass().add("olt-name");

        Label ipLabel = new Label(olt.ip);
        ipLabel.getStyleClass().add("olt-ip");

        Button connectBtn = new Button("Conectar");
        connectBtn.getStyleClass().add("connect-btn");
        connectBtn.setOnAction(e -> {
            if (SSHManager.testConnection(olt.ip)) {
                showSSHTerminal(olt);
            } else {
                showErrorDialog("Erro de conexão",
                        "Não foi possível conectar à " + olt.name + " (" + olt.ip + ")\n\n" +
                                "Possíveis causas:\n" +
                                "1. Verifique se está corretamente na VPN/rede interna\n" +
                                "2. A OLT pode estar offline, ou algum bocó derrubou\n" +
                                "3. O firewall pode estar bloqueando\n");
            }
        });

        card.getChildren().addAll(nameLabel, ipLabel, connectBtn);
        return card;
    }

    private void showSSHTerminal(OLT olt) {
        // Remove todos os filhos exceto a barra de título
        if (rootLayout.getChildren().size() > 1) {
            rootLayout.getChildren().remove(1, rootLayout.getChildren().size());
        }

        Label title = new Label("Terminal - " + olt.name.replace("_", " "));
        title.getStyleClass().add("title");

        terminalArea = new TextArea();
        terminalArea.setEditable(true);
        terminalArea.getStyleClass().add("text-area");
        terminalArea.setPrefHeight(500);

        terminalArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch(event.getCode()) {
                case ENTER:
                    event.consume();
                    String command = commandBuffer.toString().trim();
                    if (!command.isEmpty()) {
                        // envia o comando com espaço certo
                        SSHManager.sendCommand(command);
                        commandBuffer.setLength(0);
                    }
                    break;
                case BACK_SPACE:
                    if (terminalArea.getCaretPosition() <= lastCommandPosition) {
                        event.consume();
                    } else if (commandBuffer.length() > 0) {
                        commandBuffer.deleteCharAt(commandBuffer.length() - 1);
                    }
                    break;
                default:
                    if (!event.isShortcutDown() && !event.isControlDown()) {
                        String text = event.getText();
                        if (!text.isEmpty()) {
                            commandBuffer.append(text);
                        }
                    }
            }
        });

        Button backButton = new Button("Voltar para OLTs");
        backButton.getStyleClass().add("connect-btn");
        backButton.setOnAction(e -> {
            SSHManager.disconnect();
            showOLTSelectionScreen();
        });

        VBox terminalLayout = new VBox(10, title, terminalArea, backButton);
        terminalLayout.setAlignment(Pos.TOP_CENTER);
        terminalLayout.setPadding(new Insets(20));

        rootLayout.getChildren().add(terminalLayout);

        new Thread(() -> {
            SSHManager.connect(olt.ip, "suporte", "@Suporte123288", terminalArea, this::showErrorDialog);
        }).start();
    }

    public static void appendToTerminal(String text) {
        Platform.runLater(() -> {
            terminalArea.appendText(text);
            lastCommandPosition = terminalArea.getLength();
            terminalArea.positionCaret(terminalArea.getLength());
        });
    }

    public void showErrorDialog(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText("Erro de conexão");
            alert.setContentText(message);

            ButtonType copyButton = new ButtonType("Copiar Detalhes");
            alert.getButtonTypes().add(copyButton);

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");

            alert.showAndWait().ifPresent(response -> {
                if (response == copyButton) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(message);
                    Clipboard.getSystemClipboard().setContent(content);
                }
            });
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}