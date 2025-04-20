import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.*;

public class Main extends Application {
    private VBox rootLayout;
    private Stage primaryStage;
    private static TextArea terminalArea;
    private double xOffset = 0;
    private double yOffset = 0;
    private SSHManager sshManager;
    private BorderPane mainContent;
    private ToggleGroup navGroup;
    private String currentSection = "OLTs";
    private Map<String, Node> contentCache = new HashMap<>();
    private boolean isConnectedToOLT = false;
    private OLT connectedOLT;
    private ScheduledExecutorService breakageMonitor;


    /* ATENÇÃO!!!!
    - Abaixo tem +300h de código, chatgpt e muito café/energético. Tomar cuidado <3
     */


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

        mainContent = new BorderPane();
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        VBox sideNav = createSideNavigation();
        mainContent.setLeft(sideNav);

        showSection("OLTs");

        rootLayout.getChildren().add(mainContent);

        Scene scene = new Scene(rootLayout, 1280, 720);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        rootLayout.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.5)));
        setupWindowDrag(rootLayout);

        primaryStage.setTitle("Gerenciador de OLTs");
        primaryStage.setScene(scene);

        primaryStage.setOpacity(0);
        primaryStage.show();

        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(primaryStage.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(400), new KeyValue(primaryStage.opacityProperty(), 1))
        );
        fadeIn.play();

        // Monitoramento de rompimentos a cada 30 min
        startBreakageMonitoring();
    }

    private void startBreakageMonitoring() {
        breakageMonitor = Executors.newSingleThreadScheduledExecutor();
        breakageMonitor.scheduleAtFixedRate(() -> {
            for (OLT olt : OLTList.getOLTs()) {
                SSHManager tempSSH = new SSHManager();
                try {
                    tempSSH.connect(olt.ip, "suporte", "@Suporte123288", new TextArea());
                    tempSSH.scanForBreakages();
                    String report = tempSSH.getBreakagesReport();

                    if (!report.contains("0 ONTs offline")) {
                        Platform.runLater(() -> {
                            // atualiza interface com novos rompimentos
                            if (currentSection.equals("Rompimentos")) {
                                showSection("Rompimentos"); // recarrega a secao
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao monitorar OLT " + olt.name + ": " + e.getMessage());
                } finally {
                    tempSSH.disconnect();
                }
            }
        }, 0, 30, TimeUnit.MINUTES); // Verificacao a cada 30min
    }

    private VBox createSideNavigation() {
        VBox sideNav = new VBox(10);
        sideNav.getStyleClass().add("side-nav");
        sideNav.setPrefWidth(200);
        sideNav.setPadding(new Insets(20, 0, 20, 0));

        Label menuTitle = new Label("Feito por Eduardo Tomaz");
        menuTitle.getStyleClass().add("menu-title");
        menuTitle.setPadding(new Insets(0, 0, 10, 15));

        navGroup = new ToggleGroup();

        ToggleButton oltBtn = createNavButton("OLTs", false);
        ToggleButton signalBtn = createNavButton("Consulta de Sinal", false);
        ToggleButton breaksBtn = createNavButton("Rompimentos", false);

        sideNav.getChildren().addAll(menuTitle, oltBtn, signalBtn, breaksBtn);

        return sideNav;
    }

    private ToggleButton createNavButton(String text, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(navGroup);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(50);
        btn.setSelected(selected);

        btn.setOnAction(e -> {
            if (btn.isSelected()) {
                showSection(text);
            }
        });

        return btn;
    }

    private void showSection(String section) {
        if (section.equals(currentSection)) return;

        Node currentContent = mainContent.getCenter();
        Node newContent;

        if (contentCache.containsKey(section)) {
            newContent = contentCache.get(section);
        } else {
            switch (section) {
                case "OLTs":
                    newContent = createOLTSelectionScreen();
                    break;
                case "Consulta de Sinal":
                    newContent = createSignalQueryScreen();
                    break;
                case "Rompimentos":
                    newContent = createBreaksScreen();
                    break;
                default:
                    newContent = new VBox();
            }
            contentCache.put(section, newContent);
        }

        if (currentContent != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), currentContent);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), newContent);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            fadeOut.setOnFinished(e -> {
                mainContent.setCenter(newContent);
                fadeIn.play();
            });

            fadeOut.play();
        } else {
            mainContent.setCenter(newContent);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), newContent);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        }

        currentSection = section;
    }

    private Node createOLTSelectionScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));

        Label title = new Label("Selecione uma OLT");
        title.getStyleClass().add("title");

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(25);
        cardsPane.setVgap(25);
        cardsPane.setPadding(new Insets(25));
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

        content.getChildren().addAll(title, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Platform.runLater(() -> animateCardsSequentially(cardsPane.getChildren(), 50));

        return content;
    }

    private Node createSignalQueryScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));

        Label title = new Label("Consulta de Sinal Óptico");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));

        Label infoLabel = new Label("Verifique a média de Sinal da Primária");
        infoLabel.getStyleClass().add("info-label");

        HBox formRow = new HBox(15);
        formRow.setAlignment(Pos.CENTER_LEFT);

        Label oltLabel = new Label("OLT:");
        oltLabel.setPrefWidth(80);
        oltLabel.getStyleClass().add("form-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione uma OLT");
        oltComboBox.setMaxWidth(Double.MAX_VALUE);
        oltComboBox.setPrefWidth(300);
        oltComboBox.getStyleClass().add("combo-box");
        HBox.setHgrow(oltComboBox, Priority.ALWAYS);

        Label ontLabel = new Label("PON:");
        ontLabel.setPrefWidth(80);
        ontLabel.getStyleClass().add("form-label");

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("F/S/P");
        ontIdField.setPrefWidth(250);
        ontIdField.getStyleClass().add("text-field");

        formRow.getChildren().addAll(oltLabel, oltComboBox, ontLabel, ontIdField);

        Button queryBtn = new Button("Consultar Sinal");
        queryBtn.getStyleClass().add("connect-btn");
        queryBtn.setPrefWidth(200);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.getStyleClass().add("text-area");
        resultArea.setPrefHeight(350);
        resultArea.setPromptText("Os resultados da consulta serão exibidos aqui...");
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        queryBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null) {
                resultArea.setText("Por favor, selecione uma OLT para consultar.");
                return;
            }

            resultArea.setText("Conectando à " + selectedOLT.name + "...\n");

            Thread queryThread = new Thread(() -> {
                SSHManager tempSSHManager = new SSHManager();
                try {
                    String queryResult = tempSSHManager.queryOpticalSignal(ontId);
                    Platform.runLater(() -> resultArea.setText(queryResult));
                } catch (Exception ex) {
                    Platform.runLater(() -> resultArea.setText("Erro na consulta: " + ex.getMessage()));
                } finally {
                    tempSSHManager.disconnect();
                }
            });
            queryThread.setDaemon(true);
            queryThread.start();
        });

        formArea.getChildren().addAll(infoLabel, formRow, queryBtn, resultArea);
        content.getChildren().addAll(title, formArea);

        return content;
    }

    private Node createBreaksScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));

        Label title = new Label("Monitoramento de Rompimentos");
        title.getStyleClass().add("title");

        VBox dashboardArea = new VBox(20);
        dashboardArea.getStyleClass().add("form-area");
        dashboardArea.setMaxWidth(900);
        dashboardArea.setPadding(new Insets(25));

        Label infoLabel = new Label("Acompanhe rompimentos/drops. O aplicativo faz uma verificação em todas as OLTs a cada 30 minutos.");
        infoLabel.getStyleClass().add("info-label");

        HBox statusRow = new HBox(15);
        statusRow.setAlignment(Pos.CENTER);

        VBox statusBox1 = createStatusBox("ONTs Ativos", "843", "status-normal");
        VBox statusBox2 = createStatusBox("Perda de Sinal", "12", "status-warning");
        VBox statusBox3 = createStatusBox("Potenciais Rompimentos", "3", "status-critical");

        statusRow.getChildren().addAll(statusBox1, statusBox2, statusBox3);
        HBox.setHgrow(statusBox1, Priority.ALWAYS);
        HBox.setHgrow(statusBox2, Priority.ALWAYS);
        HBox.setHgrow(statusBox3, Priority.ALWAYS);

        Label breakagesTitle = new Label("Rompimentos Detectados");
        breakagesTitle.getStyleClass().add("subtitle");

        TableView<RompimentoData> tableView = new TableView<>();
        tableView.getStyleClass().add("data-table");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<RompimentoData, String> oltCol = new TableColumn<>("OLT");
        oltCol.setCellValueFactory(cellData -> cellData.getValue().oltProperty());
        oltCol.setPrefWidth(150);

        TableColumn<RompimentoData, String> ponCol = new TableColumn<>("PON");
        ponCol.setCellValueFactory(cellData -> cellData.getValue().ponProperty());
        ponCol.setPrefWidth(100);

        TableColumn<RompimentoData, String> impactedCol = new TableColumn<>("ONTs Afetados");
        impactedCol.setCellValueFactory(cellData -> cellData.getValue().impactedProperty());
        impactedCol.setPrefWidth(120);

        TableColumn<RompimentoData, String> locationCol = new TableColumn<>("Localização");
        locationCol.setCellValueFactory(cellData -> cellData.getValue().locationProperty());
        locationCol.setPrefWidth(180);

        TableColumn<RompimentoData, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(120);

        TableColumn<RompimentoData, String> timeCol = new TableColumn<>("Tempo");
        timeCol.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
        timeCol.setPrefWidth(150);

        tableView.getColumns().addAll(oltCol, ponCol, impactedCol, locationCol, statusCol, timeCol);

        // DADOS DE EXEMPLO - serao substituídos pelos dados reais do monitoramento
        ObservableList<RompimentoData> data = FXCollections.observableArrayList(
                new RompimentoData("OLT_COTIA_01", "0/1/2", "45", "Rua Exemplo, 123", "Crítico", "2h 15m"),
                new RompimentoData("OLT_TRMS_02", "0/3/4", "28", "Avenida Exemplo, 456", "Crítico", "1h 30m"),
                new RompimentoData("OLT_GRVN_01", "0/5/6", "17", "Rodovia Exemplo, km 67", "Crítico", "45m")
        );

        tableView.setItems(data);

        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Button refreshBtn = new Button("Atualizar Dados");
        refreshBtn.getStyleClass().add("connect-btn");
        refreshBtn.setOnAction(e -> {
            tableView.setOpacity(0.5);
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(event -> {
                FadeTransition fade = new FadeTransition(Duration.millis(500), tableView);
                fade.setFromValue(0.5);
                fade.setToValue(1.0);
                fade.play();
            });
            pause.play();
        });

        Button analyzeBtn = new Button("Analisar Rompimentos");
        analyzeBtn.getStyleClass().add("connect-btn");
        analyzeBtn.setStyle("-fx-background-color: linear-gradient(to right, #5a6caf, #6a7cbf);");

        actionRow.getChildren().addAll(refreshBtn, analyzeBtn);

        dashboardArea.getChildren().addAll(infoLabel, statusRow, breakagesTitle, tableView, actionRow);
        content.getChildren().addAll(title, dashboardArea);

        return content;
    }

    private VBox createStatusBox(String title, String value, String styleClass) {
        VBox box = new VBox(5);
        box.getStyleClass().addAll("status-box", styleClass);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(15));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("status-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("status-value");

        box.getChildren().addAll(valueLabel, titleLabel);

        return box;
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

    private void addEnhancedButtonHoverEffects(Button button) {
        Glow glow = new Glow();
        glow.setLevel(0.0);
        button.setEffect(glow);

        button.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), button);
            scale.setToX(1.05);
            scale.setToY(1.05);
            scale.play();

            Timeline glowAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(glow.levelProperty(), 0.0)),
                    new KeyFrame(Duration.millis(200), new KeyValue(glow.levelProperty(), 0.5))
            );
            glowAnimation.play();
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), button);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();

            Timeline glowAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(glow.levelProperty(), 0.5)),
                    new KeyFrame(Duration.millis(200), new KeyValue(glow.levelProperty(), 0.0))
            );
            glowAnimation.play();
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
            System.err.println("Erro ao carregar o ícone: " + e.getMessage());
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
                if (breakageMonitor != null) breakageMonitor.shutdown();
                primaryStage.close();
            });
            fade.play();
        });

        addEnhancedButtonHoverEffects(minimizeBtn);
        addEnhancedButtonHoverEffects(maximizeBtn);
        addEnhancedButtonHoverEffects(closeBtn);

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

    private void animateCardsSequentially(ObservableList<Node> nodes, int delay) {
        Timeline timeline = new Timeline();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setOpacity(0);
            node.setTranslateY(20);

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

        Glow glow = new Glow();
        glow.setLevel(0.0);
        connectBtn.setEffect(glow);

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

        addEnhancedButtonHoverEffects(connectBtn);

        card.setOnMouseEntered(e -> {
            Timeline glowAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(200), new KeyValue(card.opacityProperty(), 0.95))
            );
            glowAnimation.play();
        });

        card.setOnMouseExited(e -> {
            Timeline glowAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), 0.95)),
                    new KeyFrame(Duration.millis(200), new KeyValue(card.opacityProperty(), 1.0))
            );
            glowAnimation.play();
        });

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
        contentCache.remove("Terminal");
        connectedOLT = olt;
        isConnectedToOLT = true;

        Node currentContent = mainContent.getCenter();
        Node terminalContent = createTerminalScreen(olt);

        if (currentContent != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), currentContent);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                mainContent.setCenter(terminalContent);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(400), terminalContent);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            mainContent.setCenter(terminalContent);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), terminalContent);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        }

        currentSection = "Terminal";
    }

    private Node createTerminalScreen(OLT olt) {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(20));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Terminal - " + olt.name);
        title.getStyleClass().add("title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("Voltar para OLTs");
        backBtn.getStyleClass().add("back-btn");
        backBtn.setOnAction(e -> {
            if (sshManager != null) {
                sshManager.disconnect();
                sshManager = null;
            }
            isConnectedToOLT = false;
            connectedOLT = null;
            showSection("OLTs");
        });

        header.getChildren().addAll(title, spacer, backBtn);

        VBox terminalBox = new VBox(10);
        terminalBox.getStyleClass().add("terminal-box");
        terminalBox.setPadding(new Insets(10));
        VBox.setVgrow(terminalBox, Priority.ALWAYS);

        terminalArea = new TextArea();
        terminalArea.getStyleClass().add("terminal-area");
        terminalArea.setEditable(false);
        terminalArea.setPrefRowCount(20);
        VBox.setVgrow(terminalArea, Priority.ALWAYS);

        HBox commandArea = new HBox(10);
        commandArea.setAlignment(Pos.CENTER);
        commandArea.setPadding(new Insets(10, 0, 0, 0));

        Label promptLabel = new Label(">");
        promptLabel.getStyleClass().add("prompt-label");

        TextField commandField = new TextField();
        commandField.setPromptText("Digite um comando...");
        commandField.getStyleClass().add("command-field");
        HBox.setHgrow(commandField, Priority.ALWAYS);

        Button sendBtn = new Button("Enviar");
        sendBtn.getStyleClass().add("send-btn");

        commandArea.getChildren().addAll(promptLabel, commandField, sendBtn);

        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER);
        quickActions.setPadding(new Insets(10, 0, 0, 0));

        Button ctrlCBtn = new Button("Ctrl+C");
        ctrlCBtn.getStyleClass().add("action-btn");
        Button clearBtn = new Button("Limpar");
        clearBtn.getStyleClass().add("action-btn");
        Button helpBtn = new Button("Ajuda");
        helpBtn.getStyleClass().add("action-btn");

        quickActions.getChildren().addAll(ctrlCBtn, clearBtn, helpBtn);

        terminalBox.getChildren().addAll(terminalArea, commandArea, quickActions);
        content.getChildren().addAll(header, terminalBox);
        VBox.setVgrow(terminalBox, Priority.ALWAYS);

        sshManager = new SSHManager();

        Thread connectThread = new Thread(() -> {
            try {
                terminalArea.appendText("Conectando a " + olt.name + " (" + olt.ip + ")...\n");
                sshManager.connect(olt.ip, "suporte", "@Suporte123288", terminalArea);
            } catch (Exception e) {
                Platform.runLater(() -> terminalArea.appendText("\nErro ao conectar: " + e.getMessage() + "\n"));
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();

        sendBtn.setOnAction(e -> {
            String cmd = commandField.getText().trim();
            if (!cmd.isEmpty() && sshManager != null) {
                sshManager.sendCommand(cmd);
                commandField.clear();
            }
        });

        commandField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                sendBtn.fire();
            }
        });

        ctrlCBtn.setOnAction(e -> {
            if (sshManager != null) {
                sshManager.sendCtrlC();
            }
        });

        clearBtn.setOnAction(e -> {
            terminalArea.clear();
        });

        helpBtn.setOnAction(e -> {
            showHelpDialog();
        });

        return content;
    }

    private void showHelpDialog() {
        Stage helpStage = new Stage();
        helpStage.initStyle(StageStyle.UNDECORATED);
        helpStage.initOwner(primaryStage);

        VBox helpContent = new VBox(15);
        helpContent.getStyleClass().add("help-content");
        helpContent.setPadding(new Insets(20));
        helpContent.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.5)));

        Label title = new Label("Ajuda - Comandos OLT");
        title.getStyleClass().add("help-title");

        VBox commandsBox = new VBox(8);
        commandsBox.getStyleClass().add("commands-box");
        commandsBox.setPadding(new Insets(10));

        Label basicLabel = new Label("Comandos Principais:");
        basicLabel.getStyleClass().add("help-section");

        VBox basicCommands = new VBox(5);
        basicCommands.getChildren().addAll(
                new Label("• enable - Entra no modo privilegiado"),
                new Label("• config - Entra no modo de configuração"),
                new Label("• display ont info by-sn (SN) - Informações da ONT/ONU"),
                new Label("• display ont wan-info (F/S P ID) - Informações da ONT/ONU"),
                new Label("• display ont info summary (F/S/P) - Informações da Primária"),
                new Label("• display port desc (F/S/P) - Verificar Cabo e Primária"),
                new Label("• display ont autofind all - ONT/ONUs boiando")
        );

        Label oltLabel = new Label("Comandos que utilizam Interface GPON:");
        oltLabel.getStyleClass().add("help-section");

        VBox oltCommands = new VBox(5);
        oltCommands.getChildren().addAll(
                new Label("• interface gpon (F/S) - Acesso à interface PON específica "),
                new Label("• display ont register-info (P ID) - Diagnóstico de Quedas da ONT/ONU"),
                new Label("• display ont optical-info (P) all - Sinais da Primária"),
                new Label("• display ont traffic (P) all - Tráfego/Velocidade da ONT/ONU"),
                new Label("• display service-port port (F/S/P) ont (ID) - Serviço da ONT/ONU")
        );

        commandsBox.getChildren().addAll(basicLabel, basicCommands, oltLabel, oltCommands);

        Button closeBtn = new Button("Fechar");
        closeBtn.getStyleClass().add("help-close-btn");
        closeBtn.setOnAction(e -> helpStage.close());

        helpContent.getChildren().addAll(title, commandsBox, closeBtn);

        Scene helpScene = new Scene(helpContent, 550, 500);
        helpScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        helpStage.setScene(helpScene);
        helpStage.show();
    }

    public static class RompimentoData {
        private final javafx.beans.property.SimpleStringProperty olt;
        private final javafx.beans.property.SimpleStringProperty pon;
        private final javafx.beans.property.SimpleStringProperty impacted;
        private final javafx.beans.property.SimpleStringProperty location;
        private final javafx.beans.property.SimpleStringProperty status;
        private final javafx.beans.property.SimpleStringProperty time;

        public RompimentoData(String olt, String pon, String impacted, String location, String status, String time) {
            this.olt = new javafx.beans.property.SimpleStringProperty(olt);
            this.pon = new javafx.beans.property.SimpleStringProperty(pon);
            this.impacted = new javafx.beans.property.SimpleStringProperty(impacted);
            this.location = new javafx.beans.property.SimpleStringProperty(location);
            this.status = new javafx.beans.property.SimpleStringProperty(status);
            this.time = new javafx.beans.property.SimpleStringProperty(time);
        }

        public javafx.beans.property.StringProperty oltProperty() { return olt; }
        public javafx.beans.property.StringProperty ponProperty() { return pon; }
        public javafx.beans.property.StringProperty impactedProperty() { return impacted; }
        public javafx.beans.property.StringProperty locationProperty() { return location; }
        public javafx.beans.property.StringProperty statusProperty() { return status; }
        public javafx.beans.property.StringProperty timeProperty() { return time; }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        if (sshManager != null) {
            sshManager.disconnect();
        }
        if (breakageMonitor != null) {
            breakageMonitor.shutdown();
        }
    }
}