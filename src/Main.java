import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
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
    private static StringBuilder commandBuffer = new StringBuilder();
    private static int lastCommandPosition = 0;
    private double xOffset = 0;
    private double yOffset = 0;

    static {
        // Carregar fontes embutidas
        Font.loadFont(Main.class.getResourceAsStream("/fonts/Inter-Regular.ttf"), 12);
        Font.loadFont(Main.class.getResourceAsStream("/fonts/Inter-Bold.ttf"), 12);
        Font.loadFont(Main.class.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf"), 12);
    }

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

        // Efeito de sombra na janela
        rootLayout.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.5)));

        // Habilitar movimento da janela com o mouse
        setupWindowDrag(rootLayout);

        primaryStage.setTitle("Gerenciador de OLTs");
        primaryStage.setScene(scene);

        // Animação de abertura da aplicação
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

        // Adicionar um ícone pequeno (opcional)
        try {
            ImageView iconView = new ImageView(new Image(getClass().getResourceAsStream("/oltapp-icon.png")));
            iconView.setFitHeight(20);
            iconView.setFitWidth(20);
            iconView.setPreserveRatio(true);
            titleBar.getChildren().add(iconView);
            HBox.setMargin(iconView, new Insets(0, 8, 0, 0));
        } catch (Exception e) {
            // Continua sem o ícone se não for possível carregá-lo
        }

        // spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // controles da janela
        HBox windowControls = new HBox(5);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        // botao minimiza o bagui
        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().addAll("window-button");
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));

        // botao maximiza o bagui
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

        // botao q fecha
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("window-button", "window-close-button");
        closeBtn.setOnAction(e -> {
            // Animação de fechamento
            FadeTransition fade = new FadeTransition(Duration.millis(200), rootLayout);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(event -> primaryStage.close());
            fade.play();
        });

        // Adicionar efeitos de hover aos botões
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
            // Fade out de conteúdo anterior
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

        // Adicionar um efeito de glow sutil ao título
        Glow glow = new Glow(0.3);
        title.setEffect(glow);

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(25);
        cardsPane.setVgap(25);
        cardsPane.setPadding(new Insets(25));
        cardsPane.setAlignment(Pos.CENTER);
        cardsPane.getStyleClass().add("scroll-content");

        // Criar cards das OLTs com animação sequencial
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

        // Adicionar conteúdo com animação
        rootLayout.getChildren().add(content);

        // Animar o título
        FadeTransition titleFade = new FadeTransition(Duration.millis(400), title);
        titleFade.setFromValue(0);
        titleFade.setToValue(1);

        TranslateTransition titleSlide = new TranslateTransition(Duration.millis(400), title);
        titleSlide.setFromY(-20);
        titleSlide.setToY(0);

        ParallelTransition titleAnimation = new ParallelTransition(titleFade, titleSlide);
        titleAnimation.play();

        // Animar os cards sequencialmente
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

        // Clipar os cantos do card
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
            if (SSHManager.testConnection(olt.ip)) {
                // Criar uma animação de "clique"
                ScaleTransition clickEffect = new ScaleTransition(Duration.millis(100), card);
                clickEffect.setToX(0.95);
                clickEffect.setToY(0.95);
                clickEffect.setAutoReverse(true);
                clickEffect.setCycleCount(2);
                clickEffect.setOnFinished(event -> showSSHTerminal(olt));
                clickEffect.play();
            } else {
                shakeNode(card);
                showErrorDialog("Erro de conexão",
                        "Não foi possível conectar à " + olt.name + " (" + olt.ip + ")\n\n" +
                                "Possíveis causas:\n" +
                                "1. Verifique se está corretamente na VPN/rede interna\n" +
                                "2. A OLT pode estar offline, ou algum bocó derrubou\n" +
                                "3. O firewall pode estar bloqueando\n");
            }
        });

        // Adicionar efeito nos botões
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

    private void shakeNode(Node node) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(50), new KeyValue(node.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(100), new KeyValue(node.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(150), new KeyValue(node.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(200), new KeyValue(node.translateXProperty(), 8)),
                new KeyFrame(Duration.millis(250), new KeyValue(node.translateXProperty(), -8)),
                new KeyFrame(Duration.millis(300), new KeyValue(node.translateXProperty(), 0))
        );
        timeline.play();
    }

    private void showSSHTerminal(OLT olt) {
        // Fade out antigo
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

        // Configuração para capturar teclas e tratar comandos
        terminalArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch(event.getCode()) {
                case ENTER:
                    event.consume();
                    String command = commandBuffer.toString().trim();
                    if (!command.isEmpty()) {
                        SSHManager.sendCommand(command);
                        commandBuffer.setLength(0);
                    }
                    break;
                case BACK_SPACE:
                    event.consume();
                    if (commandBuffer.length() > 0) {
                        commandBuffer.deleteCharAt(commandBuffer.length() - 1);
                        updateCommandDisplay();
                    }
                    break;
                case TAB:
                    event.consume();
                    String partialCommand = commandBuffer.toString().trim();
                    String completedCommand = autoCompleteCommand(partialCommand);
                    if (!completedCommand.equals(partialCommand)) {
                        commandBuffer.setLength(0);
                        commandBuffer.append(completedCommand);
                        updateCommandDisplay();
                    }
                    break;
                default:
                    if (!event.isShortcutDown() && !event.isControlDown()) {
                        event.consume();
                        String text = event.getText();
                        if (!text.isEmpty()) {
                            commandBuffer.append(text);
                            updateCommandDisplay();

                            // Animação de "digitando" - efeito sutil
                            flashCursor();
                        }
                    }
            }
        });

        // Adicionar suporte para colar com o botão direito
        terminalArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                String clipboardText = Clipboard.getSystemClipboard().getString();
                if (clipboardText != null && !clipboardText.isEmpty()) {
                    commandBuffer.append(clipboardText);
                    updateCommandDisplay();
                }
            }
        });

        Button backButton = new Button("Voltar para OLTs");
        backButton.getStyleClass().add("connect-btn");
        addButtonHoverAnimation(backButton);
        backButton.setOnAction(e -> {
// Animação do botão ao clicar
            ScaleTransition clickEffect = new ScaleTransition(Duration.millis(100), backButton);
            clickEffect.setToX(0.95);
            clickEffect.setToY(0.95);
            clickEffect.setCycleCount(2);
            clickEffect.setAutoReverse(true);
            clickEffect.setOnFinished(event -> {
                SSHManager.disconnect();
                showOLTSelectionScreen();
            });
            clickEffect.play();
        });

        VBox terminalLayout = new VBox(10, title, terminalArea, backButton);
        terminalLayout.setAlignment(Pos.TOP_CENTER);
        terminalLayout.setPadding(new Insets(20));
        terminalLayout.setOpacity(0);

        rootLayout.getChildren().add(terminalLayout);

        // Animação de fade-in para o terminal
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), terminalLayout);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        new Thread(() -> {
            SSHManager.connect(olt.ip, "suporte", "@Suporte123288", terminalArea, this::showErrorDialog);
        }).start();
    }

    // Efeito visual para o cursor enquanto digita
    private void flashCursor() {
        StackPane cursorPane = new StackPane();
        cursorPane.getStyleClass().add("typing-cursor");

        // Não precisa adicionar fisicamente ao layout, apenas uma animação visual
        Timeline blink = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(cursorPane.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(50), new KeyValue(cursorPane.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(100), new KeyValue(cursorPane.opacityProperty(), 1))
        );
        blink.setCycleCount(1);
        blink.play();
    }

    // Método para atualizar a exibição do comando atual com estilo melhorado
    private void updateCommandDisplay() {
        Platform.runLater(() -> {
            String currentText = terminalArea.getText();
            String prompt = currentText.substring(0, lastCommandPosition);
            terminalArea.setText(prompt + commandBuffer.toString());
            terminalArea.positionCaret(terminalArea.getLength());
        });
    }

    // Método para autocompletar comandos
    private String autoCompleteCommand(String partialCommand) {
        // Lista de comandos comuns OLT para autocompletar
        if (partialCommand.equals("en")) return "enable";
        if (partialCommand.equals("conf")) return "config";
        if (partialCommand.equals("config t")) return "config terminal";
        if (partialCommand.equals("sh")) return "show";
        if (partialCommand.equals("dis")) return "display";
        if (partialCommand.equals("disp")) return "display";
        if (partialCommand.equals("inter")) return "interface gpon";
        if (partialCommand.equals("display ont i")) return "display ont info";
        if (partialCommand.equals("display ont in")) return "display ont info";
        if (partialCommand.equals("display ont inf")) return "display ont info";
        if (partialCommand.equals("display ont info s")) return "display ont info summary";
        if (partialCommand.equals("display ont info sum")) return "display ont info summary";
        if (partialCommand.equals("quit")) return "quit";
        if (partialCommand.equals("ex")) return "exit";
        if (partialCommand.equals("int")) return "interface";

        // Se não houver correspondência, retornar o comando original
        return partialCommand;
    }

    public static void appendToTerminal(String text) {
        Platform.runLater(() -> {
            // Destacar comandos e respostas de maneira mais elegante
            terminalArea.appendText(text);
            lastCommandPosition = terminalArea.getLength();
            terminalArea.positionCaret(terminalArea.getLength());

            // Scroll suave para o final do terminal
            Timeline scrollTimeline = new Timeline(
                    new KeyFrame(Duration.millis(50),
                            e -> terminalArea.setScrollTop(Double.MAX_VALUE)
                    )
            );
            scrollTimeline.play();
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

            // Adicionar efeito de entrada com shake para indicar erro
            dialogPane.setOpacity(0);

            alert.setOnShown(e -> {
                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), dialogPane);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);

                TranslateTransition shake = new TranslateTransition(Duration.millis(100), dialogPane);
                shake.setFromX(-10);
                shake.setToX(0);
                shake.setAutoReverse(true);
                shake.setCycleCount(4);

                ParallelTransition pt = new ParallelTransition(fadeIn, shake);
                pt.play();
            });

            alert.showAndWait().ifPresent(response -> {
                if (response == copyButton) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(message);
                    Clipboard.getSystemClipboard().setContent(content);

                    // Efeito visual de confirmação da cópia
                    showCopyConfirmationToast();
                }
            });
        });
    }

    private void showCopyConfirmationToast() {
        Label toast = new Label("✓ Copiado para a área de transferência");
        toast.setStyle(
                "-fx-background-color: #4a6fa5;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 10px 20px;" +
                        "-fx-background-radius: 20px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 3);"
        );

        StackPane toastContainer = new StackPane(toast);
        toastContainer.setAlignment(Pos.BOTTOM_CENTER);
        toastContainer.setPadding(new Insets(0, 0, 20, 0));
        toastContainer.setMouseTransparent(true);

        rootLayout.getChildren().add(toastContainer);
        toastContainer.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toastContainer);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toastContainer);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.seconds(1.5));
        fadeOut.setOnFinished(e -> rootLayout.getChildren().remove(toastContainer));

        fadeIn.setOnFinished(e -> fadeOut.play());
        fadeIn.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}