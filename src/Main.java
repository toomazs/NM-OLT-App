import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import models.OLT;
import models.OLTList;
import models.Ticket;
import models.Usuario;
import database.DatabaseManager;
import org.fxmisc.flowless.VirtualizedScrollPane;
import screens.LoginScreen;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import utils.ConfigManager;
import utils.ThemeManager;
import utils.WindowsUtils;
import java.awt.Desktop;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.scene.shape.Circle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javafx.scene.text.Font;


public class Main extends Application {
    private static final String APP_ID = "NMultiFibra.OLTApp";
    private double xOffset = 0;
    private double yOffset = 0;
    private Usuario usuario;
    private Usuario usuarioLogado;
    private VBox rootLayout;
    private Stage primaryStage;
    private BorderPane mainContent;
    private ToggleGroup navGroup;
    private String currentSection = null;
    private final Map<String, Node> contentCache = new HashMap<>();
    private final ConfigManager configManager = ConfigManager.getInstance();
    private TabPane terminalTabs;
    private final Map<Tab, SSHManager> terminalConnections = new HashMap<>();
    private ImageView titleBarIconView;
    private TrayIcon trayIcon;
    private String iconFileName;
    private final AtomicBoolean isQueryInProgress = new AtomicBoolean(false);
    private Label currentWaitingToast = null;
    private final Map<OLT, VBox> oltCardNodes = new HashMap<>();
    private final Map<OLT, Circle> oltStatusIndicators = new HashMap<>();
    private final Map<OLT, Label> oltStatusLabels = new HashMap<>();
    private StackPane mainContentPlaceholder;
    private FlowPane oltCardsPane;
    private TextField oltSearchField;
    private ComboBox<String> oltStatusFilter;
    private ComboBox<String> oltSortBy;
    private HBox statsBar;
    private Label onlineCountLabel;
    private Label offlineCountLabel;
    private Tab draggingTab;


    // 60FPS Configs
    public static void optimizeJavaFXFor60FPS() {
        System.setProperty("prism.vsync", "true");
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");
        System.setProperty("prism.text", "t2k");
        System.setProperty("javafx.animation.fullspeed", "true");
        System.setProperty("javafx.animation.pulse", "60");
        System.setProperty("prism.forceGPU", "true");
    }


    private void loadLocalFonts() {
        try {
            // DM Sans
//            Font.loadFont(getClass().getResourceAsStream("/fonts/DMSans-Regular.ttf"), 10);
//            Font.loadFont(getClass().getResourceAsStream("/fonts/DMSans-Bold.ttf"), 10);
//            Font.loadFont(getClass().getResourceAsStream("/fonts/DMSans-Italic.ttf"), 10);
//            Font.loadFont(getClass().getResourceAsStream("/fonts/DMSans-BoldItalic.ttf"), 10);

            Font.loadFont(getClass().getResourceAsStream("/fonts/Rubik-Regular.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/fonts/Rubik-Bold.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/fonts/Rubik-Italic.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/fonts/Rubik-BoldItalic.ttf"), 10);

            // Jetbrains
            Font.loadFont(getClass().getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/fonts/JetBrainsMono-Bold.ttf"), 10);
            Font.loadFont(getClass().getResourceAsStream("/fonts/JetBrainsMono-Italic.ttf"), 10);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ---------------------- Start ---------------------- //
    @Override
    public void start(Stage primaryStage) {
            Platform.runLater(() -> {
                loadLocalFonts();
                WindowsUtils.setAppUserModelId(APP_ID);

                LoginScreen loginScreen = new LoginScreen();
                Usuario loggedInUser = loginScreen.showLogin(new Stage());

                if (loggedInUser != null) {
                    this.usuario = loggedInUser;
                    setupMainApplicationWindow(primaryStage);
                } else {
                    Platform.exit();
                    System.exit(0);
                }
            });
    }

    private void setupMainApplicationWindow(Stage primaryStage) {
        try {
            if (this.usuario == null) {
                LoginScreen loginScreen = new LoginScreen();
                this.usuario = loginScreen.showLogin(new Stage());
                if (this.usuario == null) {
                    Platform.exit();
                    return;
                }
            }
            this.usuarioLogado = this.usuario;
            String usernameToSave = this.usuarioLogado.getUsuario();
            configManager.setLastUser(usernameToSave);

            this.primaryStage = primaryStage;
            primaryStage.initStyle(StageStyle.TRANSPARENT);

            String initialTheme = configManager.getTheme();
            this.iconFileName = ThemeManager.getIconFileNameForTheme(initialTheme);

            try {
                InputStream iconStream = getClass().getResourceAsStream(this.iconFileName);
                if (iconStream == null) {
                    iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
                }
                if (iconStream != null) {
                    primaryStage.getIcons().add(new Image(iconStream));
                }
            } catch (Exception e) {
            }

            rootLayout = new VBox();
            rootLayout.setAlignment(Pos.TOP_CENTER);
            rootLayout.getStyleClass().add("root");

            Rectangle clip = new Rectangle();
            clip.setArcWidth(30);
            clip.setArcHeight(30);
            clip.widthProperty().bind(rootLayout.widthProperty());
            clip.heightProperty().bind(rootLayout.heightProperty());
            rootLayout.setClip(clip);

            HBox titleBar = createTitleBar();
            rootLayout.getChildren().add(titleBar);

            mainContent = new BorderPane();
            VBox.setVgrow(mainContent, Priority.ALWAYS);
            mainContent.setLeft(createSideNavigation());

            rootLayout.getChildren().add(mainContent);

            Button criarTicketBtn = new Button("!");
            criarTicketBtn.getStyleClass().add("floating-btn");
            criarTicketBtn.setPrefSize(42, 42);

            criarTicketBtn.setOnMouseEntered(e -> {
                Timeline timeline = new Timeline();
                FadeTransition fadeOut = new FadeTransition(Duration.millis(150), criarTicketBtn);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(event -> {
                    criarTicketBtn.setText("Abrir Ticket");

                    FadeTransition fadeIn = new FadeTransition(Duration.millis(150), criarTicketBtn);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });
                fadeOut.play();

                KeyValue widthValue = new KeyValue(criarTicketBtn.prefWidthProperty(), 150, Interpolator.EASE_BOTH);
                KeyFrame keyFrame = new KeyFrame(Duration.millis(300), widthValue);
                timeline.getKeyFrames().add(keyFrame);
                timeline.play();
            });

            criarTicketBtn.setOnMouseExited(e -> {
                Timeline timeline = new Timeline();
                FadeTransition fadeOut = new FadeTransition(Duration.millis(150), criarTicketBtn);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(event -> {

                    criarTicketBtn.setText("!");
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(150), criarTicketBtn);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();

                });
                fadeOut.play();

                KeyValue widthValue = new KeyValue(criarTicketBtn.prefWidthProperty(), 42, Interpolator.EASE_BOTH);
                KeyFrame keyFrame = new KeyFrame(Duration.millis(300), widthValue);
                timeline.getKeyFrames().add(keyFrame);
                timeline.play();
            });

            criarTicketBtn.setOnAction(e -> {
                Stage stage = new Stage();
                stage.initStyle(StageStyle.TRANSPARENT);
                stage.initOwner(primaryStage);
                stage.initModality(Modality.APPLICATION_MODAL);

                VBox content = new VBox(15);
                content.getStyleClass().add("glass-pane");
                content.setPadding(new Insets(15));
                content.setPrefSize(500, 450);
                content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

                content.setCache(true);
                content.setCacheHint(CacheHint.SPEED);

                HBox ticketTitleBar = new HBox();
                ticketTitleBar.setAlignment(Pos.CENTER_LEFT);
                ticketTitleBar.setPadding(new Insets(5, 10, 5, 15));

                Label title = new Label("Novo Ticket Interno");
                title.getStyleClass().add("olt-name");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button closeBtn = new Button("✕");
                closeBtn.getStyleClass().addAll("close-btn", "window-btn");
                closeBtn.setPadding(new Insets(12, 12, 12, 12));
                closeBtn.setOnAction(ev -> {
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), content);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);

                    ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), content);
                    scaleOut.setFromX(1.0);
                    scaleOut.setFromY(1.0);
                    scaleOut.setToX(0.9);
                    scaleOut.setToY(0.9);

                    ParallelTransition parallelOut = new ParallelTransition(fadeOut, scaleOut);
                    parallelOut.setOnFinished(event -> stage.close());
                    parallelOut.play();
                });
                addEnhancedButtonHoverEffects(closeBtn);

                ticketTitleBar.getChildren().addAll(title, spacer, closeBtn);

                Label descLabel = new Label("Descrição do Problema:");
                descLabel.getStyleClass().add("form-label");

                CodeArea descricaoArea = new CodeArea();
                descricaoArea.getStyleClass().add("code-area");

                Label prioridadeLabel = new Label("Prioridade:");
                prioridadeLabel.getStyleClass().add("form-label");

                ComboBox<String> prioridadeBox = new ComboBox<>();
                prioridadeBox.getItems().addAll("Baixa", "Média", "Alta", "Crítica");
                prioridadeBox.setPromptText("Selecione");
                prioridadeBox.getStyleClass().add("combo-box");
                addComboBoxFocusEffects(prioridadeBox);

                Label infoLabel = new Label("Esse ticket vai direto ao Desenvolvedor.");
                infoLabel.getStyleClass().add("info-label");

                HBox btnRow = new HBox(10);
                btnRow.setAlignment(Pos.CENTER_RIGHT);
                btnRow.setPadding(new Insets(10, 0, 0, 0));

                Button meusChamadosBtn = new Button("Ver Meus Chamados");
                meusChamadosBtn.getStyleClass().add("cancel-btn");
                addEnhancedButtonHoverEffects(meusChamadosBtn);
                meusChamadosBtn.setOnAction(event -> showMeusChamadosModal());

                Button okBtn = new Button("Criar");
                okBtn.getStyleClass().add("connect-btn");
                addEnhancedButtonHoverEffects(okBtn);

                okBtn.setOnAction(ev -> {
                    if (descricaoArea.getText().isEmpty() || prioridadeBox.getValue() == null) return;

                    DatabaseManager.criarTicket(
                            usuarioLogado.getNome(),
                            usuarioLogado.getCargo(),
                            descricaoArea.getText(),
                            prioridadeBox.getValue()
                    );

                    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), content);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);

                    ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), content);
                    scaleOut.setFromX(1.0);
                    scaleOut.setFromY(1.0);
                    scaleOut.setToX(0.9);
                    scaleOut.setToY(0.9);

                    ParallelTransition parallelOut = new ParallelTransition(fadeOut, scaleOut);
                    parallelOut.setOnFinished(event -> {
                        stage.close();
                        animateModalClose(stage, content, () -> showToast("✅ Ticket criado com sucesso!"));
                    });
                    parallelOut.play();
                });

                Region btnSpacer = new Region();
                HBox.setHgrow(btnSpacer, Priority.ALWAYS);

                btnRow.getChildren().addAll(meusChamadosBtn, btnSpacer, okBtn);
                content.getChildren().addAll(ticketTitleBar, descLabel, descricaoArea, prioridadeLabel, prioridadeBox, infoLabel, btnRow);

                Scene scene = new Scene(content);
                scene.setFill(Color.TRANSPARENT);
                ThemeManager.applyThemeToNewScene(scene);

                stage.setScene(scene);
                stage.setOpacity(0);
                content.setScaleX(0.9);
                content.setScaleY(0.9);

                stage.show();
                stage.centerOnScreen();

                animateModalOpen(stage, content);

                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), content);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);

                ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), content);
                scaleIn.setFromX(0.9);
                scaleIn.setFromY(0.9);
                scaleIn.setToX(1.0);
                scaleIn.setToY(1.0);

                Timeline stageOpacity = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(stage.opacityProperty(), 0)),
                        new KeyFrame(Duration.millis(300), new KeyValue(stage.opacityProperty(), 1))
                );

                ParallelTransition parallelIn = new ParallelTransition(fadeIn, scaleIn, stageOpacity);
                parallelIn.setInterpolator(Interpolator.EASE_OUT);
                parallelIn.play();
            });

            StackPane rootStack = new StackPane();
            rootStack.getChildren().addAll(rootLayout, criarTicketBtn);
            StackPane.setAlignment(criarTicketBtn, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(criarTicketBtn, new Insets(0, 20, 20, 0));

            Scene scene = new Scene(rootStack, 1300, 760);
            scene.setFill(Color.TRANSPARENT);
            ThemeManager.applyTheme(scene, initialTheme);

            primaryStage.setScene(scene);
            primaryStage.setTitle("NM OLT App");
            primaryStage.setOnCloseRequest(event -> {
                event.consume();
            });

            primaryStage.setMinWidth(760);
            primaryStage.setMinHeight(670);
            new StageResizer(primaryStage);

            primaryStage.setOpacity(0);
            primaryStage.show();

            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                java.awt.Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/oltapp-icon-taskbar.png"));

                trayIcon = new TrayIcon(image, "NM OLT App");
                trayIcon.setImageAutoSize(true);
                try {
                    tray.add(trayIcon);
                } catch (Exception e) {
                }
            }


            Timeline fadeIn = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(primaryStage.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(400), new KeyValue(primaryStage.opacityProperty(), 1))
            );
            fadeIn.setOnFinished(e -> {
                Platform.runLater(this::loadInitialMainContent);
            });
            fadeIn.play();

            setupWindowDrag(rootLayout.getChildren().get(0));

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Erro ao iniciar o aplicativo: " + e.getMessage());
            Platform.exit();
        }
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    private void loadInitialMainContent() {
        terminalTabs = new TabPane();
        terminalTabs.setStyle("-fx-background-color: transparent;");
        terminalTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        terminalTabs.setTabMinWidth(150);

        Tab oltsTab = new Tab("Lista de OLTs");
        standardizeTabText(oltsTab);
        oltsTab.setClosable(false);

        Node oltsContent = createOLTScreen();
        oltsTab.setContent(oltsContent);

        terminalTabs.getTabs().add(oltsTab);

        if (mainContent != null) {
            mainContent.setCenter(terminalTabs);
        }
        currentSection = "OLTs";

    }
    // ---------------------- Start ---------------------- //


    // ---------------------- Barra Vertical (Abas) ---------------------- //
    private VBox createSideNavigation() {
        VBox sideNav = new VBox(10);
        sideNav.getStyleClass().add("side-nav");
        sideNav.setPrefWidth(200);
        sideNav.setMaxWidth(250);
        sideNav.setPadding(new Insets(20, 0, 20, 0));

        HBox versionBox = new HBox();
        versionBox.setAlignment(Pos.CENTER_LEFT);
        versionBox.setPadding(new Insets(0, 0, 10, 15));

        Label versionLabel = new Label("v1.6.0.0 • ");
        versionLabel.getStyleClass().add("version-text");

        Label creditsLink = new Label("créditos");
        creditsLink.getStyleClass().add("credits-link");

        Glow glowEffect = new Glow(0.0);
        creditsLink.setEffect(glowEffect);

        creditsLink.setOnMouseEntered(e -> glowEffect.setLevel(0.8));
        creditsLink.setOnMouseExited(e -> glowEffect.setLevel(0.0));
        creditsLink.setOnMouseClicked(e -> showCreditsSection());
        versionBox.getChildren().addAll(versionLabel, creditsLink);
        navGroup = new ToggleGroup();

        ToggleButton oltBtn = createNavButton("OLTs", true);
        ToggleButton signalBtn = createNavButton("Consulta de Sinal", false);
        ToggleButton ponSummaryBtn = createNavButton("Summary", false);
        ToggleButton onuBySNBtn = createNavButton("By-SN", false);
        ToggleButton diagnosisBtn = createNavButton("Quedas", false);
        ToggleButton trafficBtn = createNavButton("Tráfego", false);
        ToggleButton serviceBtn = createNavButton("Serviços", false);

        sideNav.getChildren().addAll(oltBtn, signalBtn, ponSummaryBtn, onuBySNBtn, diagnosisBtn, trafficBtn, serviceBtn);

        if (usuario.isAdmin()) {
            VBox adminSection = createAdminSection();
            sideNav.getChildren().add(adminSection);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox footerBox = new VBox(5);
        footerBox.setAlignment(Pos.CENTER_LEFT);
        footerBox.setPadding(new Insets(10, 15, 10, 15));

        VBox userInfoBox = new VBox(2);
        userInfoBox.getStyleClass().add("user-info-box");
        userInfoBox.setPadding(new Insets(5, 0, 5, 0));

        Label usernameLabel = new Label("👤 " + usuario.getNome());
        usernameLabel.getStyleClass().add("user-name");

        HBox userRoleBox = new HBox(5);
        userRoleBox.setAlignment(Pos.CENTER_LEFT);

        Label roleLabel = new Label("ㅤ " + usuario.getCargo());
        roleLabel.getStyleClass().add("user-role");

        Button dropdownBtn = new Button("▾");
        dropdownBtn.getStyleClass().add("dropdown-arrow");
        addEnhancedButtonHoverEffects(dropdownBtn);

        userRoleBox.getChildren().addAll(roleLabel, dropdownBtn);
        userInfoBox.getChildren().addAll(usernameLabel, userRoleBox);

        VBox logoutContainer = new VBox(5);
        logoutContainer.setPadding(new Insets(5, 0, 0, 0));
        logoutContainer.setVisible(false);
        logoutContainer.setManaged(false);

        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll(
                "Roxo", "All Black", "All White", "Dracula", "GitHub Dark", "Shades", "Night Owl", "Light Owl", "Creme", "Terminal", "Azul", "Verde", "Vermelho", "Rosa"
        );
        themeCombo.setPromptText("Temas");
        themeCombo.getStyleClass().add("theme-combobox");
        themeCombo.setPrefWidth(120);
        themeCombo.setMaxWidth(120);
        addComboBoxFocusEffects(themeCombo);

        Button logoutBtn = new Button("🚪");
        logoutBtn.getStyleClass().add("logout-btn");
        logoutBtn.setPrefWidth(40);
        logoutBtn.setMaxWidth(40);
        logoutBtn.setPrefHeight(43);
        logoutBtn.setTooltip(new Tooltip("Deslogar"));
        addEnhancedButtonHoverEffects(logoutBtn);

        HBox themeLogoutBox = new HBox(5);
        themeLogoutBox.setAlignment(Pos.CENTER);
        themeLogoutBox.getChildren().addAll(themeCombo, logoutBtn);

        logoutContainer.getChildren().add(themeLogoutBox);

        dropdownBtn.setOnAction(e -> {
            boolean isLogoutContainerCurrentlyVisible = logoutContainer.isVisible();

            if (!isLogoutContainerCurrentlyVisible) {
                dropdownBtn.setText("▴");
                dropdownBtn.getStyleClass().add("dropdown-arrow-active");

                logoutContainer.setOpacity(0.0);
                logoutContainer.setTranslateY(-10);

                logoutContainer.setManaged(true);
                logoutContainer.setVisible(true);

                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), logoutContainer);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);

                TranslateTransition slideDown = new TranslateTransition(Duration.millis(200), logoutContainer);
                slideDown.setFromY(-10);
                slideDown.setToY(0);

                ParallelTransition showAnimation = new ParallelTransition(fadeIn, slideDown);
                showAnimation.play();

            } else {
                dropdownBtn.setText("▾");
                dropdownBtn.getStyleClass().remove("dropdown-arrow-active");

                FadeTransition fadeOut = new FadeTransition(Duration.millis(200), logoutContainer);
                fadeOut.setFromValue(logoutContainer.getOpacity());
                fadeOut.setToValue(0.0);

                TranslateTransition slideUp = new TranslateTransition(Duration.millis(200), logoutContainer);
                slideUp.setFromY(logoutContainer.getTranslateY());
                slideUp.setToY(-10);

                ParallelTransition hideAnimation = new ParallelTransition(fadeOut, slideUp);
                hideAnimation.setOnFinished(event -> {
                    logoutContainer.setVisible(false);
                    logoutContainer.setManaged(false);

                    logoutContainer.setOpacity(1.0);
                    logoutContainer.setTranslateY(0);
                });
                hideAnimation.play();
            }
        });

        themeCombo.setOnAction(e -> {
            String selected = themeCombo.getValue();
            if (selected != null) {
                String themeFile;
                String selectedIconFileName;

                switch (selected) {
                    case "Roxo" -> {
                        themeFile = "style.css";
                        selectedIconFileName = "oltapp-icon.png";
                    }
                    case "All Black" -> {
                        themeFile = "style-allblack.css";
                        selectedIconFileName = "/oltapp-icon-black.png";
                    }
                    case "All White" -> {
                        themeFile = "style-allwhite.css";
                        selectedIconFileName = "/oltapp-icon-white.png";
                    }
                    case "Dracula" -> {
                        themeFile = "style-dracula.css";
                        selectedIconFileName = "/oltapp-icon-dracula.png";
                    }
                    case "GitHub Dark" -> {
                        themeFile = "style-gdark.css";
                        selectedIconFileName = "/oltapp-icon-gdark.png";
                    }
                    case "Shades" -> {
                        themeFile = "style-sop.css";
                        selectedIconFileName = "/oltapp-icon-sop.png";
                    }
                    case "Night Owl" -> {
                        themeFile = "style-nightowl.css";
                        selectedIconFileName = "/oltapp-icon-nightowl.png";
                    }
                    case "Light Owl" -> {
                        themeFile = "style-lightowl.css";
                        selectedIconFileName = "/oltapp-icon-lightowl.png";
                    }
                    case "Creme" -> {
                        themeFile = "style-creme.css";
                        selectedIconFileName = "/oltapp-icon-creme.png";
                    }
                    case "Terminal" -> {
                        themeFile = "style-terminal.css";
                        selectedIconFileName = "/oltapp-icon-terminal.png";
                    }
                    case "Azul" -> {
                        themeFile = "style-blue.css";
                        selectedIconFileName = "/oltapp-icon-blue.png";
                    }
                    case "Verde" -> {
                        themeFile = "style-green.css";
                        selectedIconFileName = "/oltapp-icon-green.png";
                    }
                    case "Vermelho" -> {
                        themeFile = "style-red.css";
                        selectedIconFileName = "/oltapp-icon-red.png";
                    }
                    case "Rosa" -> {
                        themeFile = "style-pink.css";
                        selectedIconFileName = "/oltapp-icon-pink.png";
                    }
                    default -> {
                        themeFile = "style.css";
                        selectedIconFileName = "/oltapp-icon.png";
                    }
                }

                ThemeManager.applyTheme(primaryStage.getScene(), themeFile);
                configManager.setTheme(themeFile);
                this.iconFileName = selectedIconFileName;

                updateApplicationIcons(selectedIconFileName);
            }
        });

        logoutBtn.setOnAction(e -> {
            DatabaseManager.logUsuario(usuario.getNome(), "Deslogou do sistema");

            primaryStage.close();

            Platform.runLater(() -> {
                try {
                    Main novaApp = new Main();
                    novaApp.start(new Stage());
                } catch (Exception ex) {
                    System.err.println("Erro ao reiniciar o aplicativo após o logout.");
                    ex.printStackTrace();
                }
            });
        });

        footerBox.getChildren().addAll(versionBox, userInfoBox, logoutContainer);
        sideNav.getChildren().addAll(spacer, footerBox);
        return sideNav;
    }

    private VBox createAdminSection() {
        VBox adminSection = new VBox(5);
        adminSection.setPadding(new Insets(15, 0, 0, 0));

        Region separator = new Region();
        separator.getStyleClass().add("admin-separator");
        separator.setPrefWidth(180);
        separator.setPrefHeight(1);

        Label adminLabel = new Label("🔧 ADMIN & DEVS");
        adminLabel.getStyleClass().add("admin-section-label");
        adminLabel.setPadding(new Insets(10, 0, 5, 15));

        ToggleButton pendenciasBtn = createNavButton("Chamados", false);

        adminSection.getChildren().addAll(separator, adminLabel, pendenciasBtn);
        return adminSection;
    }

    private void updateApplicationIcons(String iconResourcePath) {
        try {
            InputStream iconStream = getClass().getResourceAsStream(iconResourcePath);
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                Image windowIcon = new Image(iconStream);
                primaryStage.getIcons().clear();
                primaryStage.getIcons().add(windowIcon);

                InputStream titleBarIconStream = getClass().getResourceAsStream(iconResourcePath);
                if (titleBarIconStream == null) {
                    titleBarIconStream = getClass().getResourceAsStream("/oltapp-icon.png");
                }
                if (titleBarIconStream != null && titleBarIconView != null) {
                    titleBarIconView.setImage(new Image(titleBarIconStream));
                }
            }
        } catch (Exception ex) {
        }
    }

    private ToggleButton createNavButton(String text, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(navGroup);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(50);
        btn.setSelected(selected);

        setupButtonAnimations(btn);

        btn.setOnAction(e -> {
            if (btn.isSelected()) {
                showSection(text);
            }
        });

        return btn;
    }

    private void setupButtonAnimations(ToggleButton btn) {
        final double originalScaleX = 1.0;
        final double originalScaleY = 1.0;
        final double originalOpacity = 1.0;

        final double hoverScaleX = 1.02;
        final double hoverScaleY = 1.02;
        final double hoverOpacity = 0.95;

        final double pressScaleX = 0.98;
        final double pressScaleY = 0.98;

        DropShadow glowEffect = new DropShadow();
        glowEffect.setColor(Color.web("#7d4cbd", 0.3));
        glowEffect.setRadius(8);
        glowEffect.setSpread(0.3);

        Timeline hoverIn = new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(btn.scaleXProperty(), hoverScaleX, Interpolator.EASE_OUT),
                        new KeyValue(btn.scaleYProperty(), hoverScaleY, Interpolator.EASE_OUT),
                        new KeyValue(btn.opacityProperty(), hoverOpacity, Interpolator.EASE_OUT)
                )
        );

        Timeline hoverOut = new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(btn.scaleXProperty(), originalScaleX, Interpolator.EASE_OUT),
                        new KeyValue(btn.scaleYProperty(), originalScaleY, Interpolator.EASE_OUT),
                        new KeyValue(btn.opacityProperty(), originalOpacity, Interpolator.EASE_OUT)
                )
        );

        Timeline pressIn = new Timeline(
                new KeyFrame(Duration.millis(80),
                        new KeyValue(btn.scaleXProperty(), pressScaleX, Interpolator.EASE_IN),
                        new KeyValue(btn.scaleYProperty(), pressScaleY, Interpolator.EASE_IN)
                )
        );

        Timeline pressOut = new Timeline(
                new KeyFrame(Duration.millis(120),
                        new KeyValue(btn.scaleXProperty(), hoverScaleX, Interpolator.EASE_OUT),
                        new KeyValue(btn.scaleYProperty(), hoverScaleY, Interpolator.EASE_OUT)
                )
        );

        Timeline glowIn = new Timeline(
                new KeyFrame(Duration.millis(250),
                        new KeyValue(glowEffect.radiusProperty(), 12, Interpolator.EASE_OUT),
                        new KeyValue(glowEffect.colorProperty(), Color.web("#7d4cbd", 0.4), Interpolator.EASE_OUT)
                )
        );

        Timeline glowOut = new Timeline(
                new KeyFrame(Duration.millis(300),
                        new KeyValue(glowEffect.radiusProperty(), 0, Interpolator.EASE_OUT),
                        new KeyValue(glowEffect.colorProperty(), Color.web("#7d4cbd", 0.0), Interpolator.EASE_OUT)
                )
        );

        btn.setOnMouseEntered(e -> {
            if (!btn.isSelected()) {
                hoverOut.stop();
                glowOut.stop();
                hoverIn.play();
                glowIn.play();
                btn.setEffect(glowEffect);
            }
        });

        btn.setOnMouseExited(e -> {
            if (!btn.isSelected()) {
                hoverIn.stop();
                glowIn.stop();
                hoverOut.play();

                glowOut.setOnFinished(ev -> {
                    if (!btn.isHover() && !btn.isSelected()) {
                        btn.setEffect(null);
                    }
                });
                glowOut.play();
            }
        });

        btn.setOnMousePressed(e -> {
            if (!btn.isSelected()) {
                pressIn.play();

                Timeline pressGlow = new Timeline(
                        new KeyFrame(Duration.millis(80),
                                new KeyValue(glowEffect.radiusProperty(), 15, Interpolator.EASE_IN),
                                new KeyValue(glowEffect.colorProperty(), Color.web("#7d4cbd", 0.6), Interpolator.EASE_IN)
                        )
                );
                pressGlow.play();
            }
        });

        btn.setOnMouseReleased(e -> {
            if (!btn.isSelected() && btn.isHover()) {
                pressOut.play();

                Timeline releaseGlow = new Timeline(
                        new KeyFrame(Duration.millis(120),
                                new KeyValue(glowEffect.radiusProperty(), 12, Interpolator.EASE_OUT),
                                new KeyValue(glowEffect.colorProperty(), Color.web("#7d4cbd", 0.4), Interpolator.EASE_OUT)
                        )
                );
                releaseGlow.play();
            }
        });

        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                hoverIn.stop();
                hoverOut.stop();

                DropShadow selectedGlow = new DropShadow();
                selectedGlow.setColor(Color.web("#b387e2", 0.5));
                selectedGlow.setRadius(10);
                selectedGlow.setSpread(0.0);
                btn.setEffect(selectedGlow);

                Timeline selectedPulse = new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(selectedGlow.radiusProperty(), 10),
                                new KeyValue(selectedGlow.colorProperty(), Color.web("#b387e2", 0.5))
                        ),
                        new KeyFrame(Duration.millis(1500),
                                new KeyValue(selectedGlow.radiusProperty(), 12, Interpolator.EASE_BOTH),
                                new KeyValue(selectedGlow.colorProperty(), Color.web("#b387e2", 0.3), Interpolator.EASE_BOTH)
                        ),
                        new KeyFrame(Duration.millis(3000),
                                new KeyValue(selectedGlow.radiusProperty(), 10, Interpolator.EASE_BOTH),
                                new KeyValue(selectedGlow.colorProperty(), Color.web("#b387e2", 0.5), Interpolator.EASE_BOTH)
                        )
                );
                selectedPulse.setCycleCount(Timeline.INDEFINITE);
                selectedPulse.play();

                btn.getProperties().put("selectedPulse", selectedPulse);

            } else {
                Timeline selectedPulse = (Timeline) btn.getProperties().get("selectedPulse");
                if (selectedPulse != null) {
                    selectedPulse.stop();
                }
                btn.setEffect(null);

                btn.setScaleX(originalScaleX);
                btn.setScaleY(originalScaleY);
                btn.setOpacity(originalOpacity);
            }
        });
    }

    private void showSection(String section) {
        if ("OLTs".equals(section) && terminalTabs != null) {
            refreshAllOLTStatuses();
            if (mainContent.getCenter() == terminalTabs) {
                for (Tab tab : terminalTabs.getTabs()) {
                    if ("Lista de OLTs".equals(tab.getText())) {
                        terminalTabs.getSelectionModel().select(tab);
                        break;
                    }
                }
                if (!section.equals(currentSection)) {
                    currentSection = section;
                }
                return;
            } else {
                animateContentSwitch(terminalTabs);
                if (!section.equals(currentSection)) {
                    currentSection = section;
                }
                for (Tab tab : terminalTabs.getTabs()) {
                    if ("Lista de OLTs".equals(tab.getText())) {
                        terminalTabs.getSelectionModel().select(tab);
                        break;
                    }
                }
                return;
            }
        }

        Node newContent = contentCache.computeIfAbsent(section, s -> {
            switch (s) {
                case "Consulta de Sinal": return createSignalQueryScreen();
                case "Summary": return createPONSummaryScreen();
                case "By-SN": return createONUBySNScreen();
                case "Quedas": return createDropDiagnosisScreen();
                case "Tráfego": return createONUTrafficScreen();
                case "Serviços": return createONUServiceScreen();
                case "Chamados": return createTechnicalTicketsScreen();
                default: return new VBox(new Label("Conteúdo para " + s));
            }
        });

        if (newContent != null && newContent != mainContent.getCenter()) {
            animateContentSwitch(newContent);
            currentSection = section;
        }
    }

    private void animateContentSwitch(Node newContent) {
        Node currentCenterContent = mainContent.getCenter();
        newContent.setOpacity(0);

        if (currentCenterContent != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), currentCenterContent);
            fadeOut.setFromValue(currentCenterContent.getOpacity());
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                mainContent.setCenter(newContent);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), newContent);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            mainContent.setCenter(newContent);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), newContent);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        }
    }
    // ---------------------- Barra Vertical (Abas) ---------------------- //


    // ---------------------- OLTs ---------------------- //
    private Node createOLTScreen() {
        VBox content = new VBox(5);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(15));
        VBox.setVgrow(content, Priority.ALWAYS);

        HBox combinedHeader = createOltHeaderControls();

        oltCardsPane = new FlowPane();
        oltCardsPane.setHgap(15);
        oltCardsPane.setVgap(15);
        oltCardsPane.setPadding(new Insets(10, 20, 20, 20));
        oltCardsPane.setAlignment(Pos.CENTER);

        setupResponsiveLayout(content, oltCardsPane);

        VBox scrollContent = new VBox(oltCardsPane);
        scrollContent.setAlignment(Pos.TOP_CENTER);
        scrollContent.setFillWidth(true);
        VBox.setVgrow(scrollContent, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setMaxHeight(Double.MAX_VALUE);

        content.getChildren().addAll(combinedHeader, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Platform.runLater(() -> {
            refreshOLTScreen();
            animateCardsSequentially(oltCardsPane.getChildren(), 50);
        });

        return content;
    }

    private HBox createOltHeaderControls() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 20, 10, 20));

        HBox leftControls = new HBox(10);
        leftControls.setAlignment(Pos.CENTER_LEFT);

        oltSearchField = new TextField();
        oltSearchField.setPromptText("🔍 Buscar OLTs");
        oltSearchField.setPrefWidth(200);
        oltSearchField.setPrefHeight(43);
        oltSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyOLTFiltersAndSorting());
        addFieldFocusEffects(oltSearchField);

        oltStatusFilter = new ComboBox<>();
        oltStatusFilter.getItems().addAll("Status", "Online", "Offline");
        oltStatusFilter.setValue("Status");
        oltStatusFilter.setPrefWidth(120);
        oltStatusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyOLTFiltersAndSorting());
        addComboBoxFocusEffects(oltStatusFilter);

        oltSortBy = new ComboBox<>();
        oltSortBy.getItems().addAll("Nome A-Z", "Nome Z-A", "Status", "IP");
        oltSortBy.setValue("Nome A-Z");
        oltSortBy.setPrefWidth(120);
        oltSortBy.valueProperty().addListener((obs, oldVal, newVal) -> applyOLTFiltersAndSorting());
        addComboBoxFocusEffects(oltSortBy);

        leftControls.getChildren().addAll(oltSearchField, oltStatusFilter, oltSortBy);

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        statsBar = new HBox(15);
        statsBar.setAlignment(Pos.CENTER);
        onlineCountLabel = new Label("\uD83D\uDD34 Online: 0");
        onlineCountLabel.getStyleClass().add("success-label");
        onlineCountLabel.setStyle("-fx-font-weight: bold;");
        offlineCountLabel = new Label("\uD83D\uDD34 Offline: 0");
        offlineCountLabel.getStyleClass().add("error-label");
        offlineCountLabel.setStyle("-fx-font-weight: bold;");
        statsBar.getChildren().addAll(onlineCountLabel, offlineCountLabel);

        HBox rightControls = new HBox(10);
        rightControls.setAlignment(Pos.CENTER_RIGHT);

        Button addOLTBtn = new Button("➕");
        addOLTBtn.getStyleClass().add("floating-btn");
        addOLTBtn.setPrefSize(30, 30);
        addOLTBtn.setTooltip(new Tooltip("Adicionar OLT"));
        addOLTBtn.setOnAction(e -> showAddOLTModal());
        addEnhancedButtonHoverEffects(addOLTBtn);

        Button refreshStatusesBtn = new Button("🔄");
        refreshStatusesBtn.getStyleClass().add("floating-btn");
        refreshStatusesBtn.setPrefSize(30, 30);
        refreshStatusesBtn.setTooltip(new Tooltip("Atualizar Status"));
        addEnhancedButtonHoverEffects(refreshStatusesBtn);
        refreshStatusesBtn.setOnAction(e -> {
            showToast("🔄 Verificando status de todas as OLTs...");
            refreshAllOLTStatuses();
        });

        rightControls.getChildren().addAll(addOLTBtn, refreshStatusesBtn);

        header.getChildren().addAll(leftControls, spacer1, statsBar, spacer2, rightControls);
        return header;
    }

    private void updateStatsBar() {
        long onlineCount = oltStatusLabels.values().stream().filter(label -> "Online".equals(label.getText())).count();
        long offlineCount = oltStatusLabels.values().stream().filter(label -> "Offline".equals(label.getText())).count();

        Platform.runLater(() -> {
            if (onlineCountLabel != null) {
                onlineCountLabel.setText("\uD83D\uDD34 Online: " + onlineCount);
            }
            if (offlineCountLabel != null) {
                offlineCountLabel.setText("\uD83D\uDD34 Offline: " + offlineCount);
            }
        });
    }

    private VBox createEnhancedOLTCard(OLT olt, boolean isMaximized) {
        double cardWidth = isMaximized ? 185 : 165;
        double cardHeight = isMaximized ? 175 : 155;

        VBox card = new VBox(5);
        card.getStyleClass().add("olt-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(cardWidth, cardHeight);
        card.setMaxSize(cardWidth, cardHeight);
        card.setUserData(olt);

        Rectangle clip = new Rectangle(cardWidth, cardHeight);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        card.setClip(clip);

        HBox cardHeader = new HBox(5);
        cardHeader.setAlignment(Pos.CENTER);
        cardHeader.setPadding(new Insets(8, 10, 0, 10));

        Label nameLabel = new Label(olt.name.replace("_", " "));
        nameLabel.getStyleClass().add("olt-name");
        nameLabel.setStyle(isMaximized ? "-fx-font-size: 14px; -fx-font-weight: bold;" : "-fx-font-size: 13px; -fx-font-weight: bold;");
        nameLabel.setMaxWidth(cardWidth - 20);
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);

        cardHeader.getChildren().add(nameLabel);

        VBox infoSection = new VBox(3);
        infoSection.setAlignment(Pos.CENTER);
        infoSection.setPadding(new Insets(2, 10, 0, 10));

        Label ipLabel = new Label(olt.ip);
        ipLabel.getStyleClass().add("olt-ip");
        ipLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        infoSection.getChildren().addAll(ipLabel);

        HBox statusBox = new HBox(6);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(5, 0, 5, 0));

        Circle statusIndicator = new Circle(6, Color.GRAY);
        statusIndicator.setStroke(Color.TRANSPARENT);
        statusIndicator.setStrokeWidth(0);

        Label statusLabel = new Label("N/A");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        statusBox.getChildren().addAll(statusIndicator, statusLabel);

        oltStatusIndicators.put(olt, statusIndicator);
        oltStatusLabels.put(olt, statusLabel);

        HBox actionButtons = new HBox(6);
        actionButtons.setAlignment(Pos.CENTER);
        actionButtons.setPadding(new Insets(0, 10, 8, 10));

        Button connectBtn = new Button("Conectar");
        connectBtn.getStyleClass().add("connect-btn");
        connectBtn.setStyle(isMaximized ? "-fx-font-size: 11px; -fx-pref-width: 85px; -fx-pref-height: 28px;" : "-fx-font-size: 10px; -fx-pref-width: 75px; -fx-pref-height: 26px;");

        Runnable connectAction = () -> {
            showSSHTerminal(olt);
            DatabaseManager.logUsuario(usuario.getNome(), "Conectou na " + olt.name);
        };

        connectBtn.setOnAction(e -> {
            ScaleTransition clickEffect = new ScaleTransition(Duration.millis(120), connectBtn);
            clickEffect.setToX(0.95);
            clickEffect.setToY(0.95);
            clickEffect.setAutoReverse(true);
            clickEffect.setCycleCount(2);
            clickEffect.setOnFinished(event -> connectAction.run());
            clickEffect.play();
        });
        addEnhancedButtonHoverEffects(connectBtn);
        actionButtons.getChildren().add(connectBtn);

        if (OLTList.canRemove(olt)) {
            Button removeBtn = new Button("x");
            removeBtn.getStyleClass().add("logout-btn");
            removeBtn.setPrefSize(28, 28);
            removeBtn.setStyle("-fx-font-size: 10px;");
            removeBtn.setTooltip(new Tooltip("Remover OLT"));
            removeBtn.setOnAction(e -> showRemoveOLTConfirmation(olt));
            addEnhancedButtonHoverEffects(removeBtn);
            actionButtons.getChildren().add(removeBtn);
        }

        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button ||
                    (e.getTarget() instanceof Node && ((Node) e.getTarget()).getParent() instanceof Button)) {
                return;
            }

            ScaleTransition clickEffect = new ScaleTransition(Duration.millis(120), card);
            clickEffect.setToX(0.98);
            clickEffect.setToY(0.98);
            clickEffect.setAutoReverse(true);
            clickEffect.setCycleCount(2);
            clickEffect.setOnFinished(event -> connectAction.run());
            clickEffect.play();
        });

        card.setOnMouseEntered(e -> {
            TranslateTransition lift = new TranslateTransition(Duration.millis(200), card);
            lift.setToY(-6);
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1.04);
            scale.setToY(1.04);
            new ParallelTransition(lift, scale).play();
        });

        card.setOnMouseExited(e -> {
            TranslateTransition drop = new TranslateTransition(Duration.millis(200), card);
            drop.setToY(0);
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1.0);
            scale.setToY(1.0);
            new ParallelTransition(drop, scale).play();
        });

        Region spacer2 = new Region();
        VBox.setVgrow(spacer2, Priority.SOMETIMES);

        card.getChildren().addAll(cardHeader, infoSection, statusBox, spacer2, actionButtons);
        return card;
    }

    private void showAddOLTModal() {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(15);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(15));
        content.setPrefSize(450, 480);
        content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(5, 10, 5, 15));

        Label title = new Label("Adicionar Nova OLT");
        title.getStyleClass().add("olt-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(12, 12, 12, 12));
        closeBtn.setOnAction(ev -> animateModalClose(stage, content, () -> {
            stage.close();
        }));
        addEnhancedButtonHoverEffects(closeBtn);

        titleBar.getChildren().addAll(title, spacer, closeBtn);

        Label nameLabel = new Label("Nome da OLT:"); nameLabel.getStyleClass().add("form-label");
        TextField nameField = new TextField(); nameField.setPromptText("OLT_XXXX_XX"); nameField.getStyleClass().add("text-field");
        addFieldFocusEffects(nameField);

        Label ipLabel = new Label("Endereço IP:"); ipLabel.getStyleClass().add("form-label");
        TextField ipField = new TextField(); ipField.setPromptText("10.0.X.XX"); ipField.getStyleClass().add("text-field");
        addFieldFocusEffects(ipField);

        Label portLabel = new Label("Porta SSH (opcional):"); portLabel.getStyleClass().add("form-label");
        TextField portField = new TextField(); portField.setPromptText("Deixe em branco para usar a Porta 22 (padrão)"); portField.getStyleClass().add("text-field");
        addFieldFocusEffects(portField);

        Label userLabel = new Label("Usuário SSH (opcional):");
        userLabel.getStyleClass().add("form-label");
        TextField userField = new TextField();
        userField.setPromptText("Deixe em branco para usar o Usuário do Suporte (padrão)");
        userField.getStyleClass().add("text-field");
        addFieldFocusEffects(userField);

        Label passLabel = new Label("Senha SSH (opcional):");
        passLabel.getStyleClass().add("form-label");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Deixe em branco para usar a Senha do Suporte (padrão)");
        passField.getStyleClass().add("modern-text-field");
        passField.setPrefWidth(370);
        addFieldFocusEffects(passField);

        TextField passFieldVisible = new TextField();
        passFieldVisible.setPromptText("Deixe em branco para usar a Senha do Suporte (padrão)");
        passFieldVisible.getStyleClass().add("modern-text-field");
        passFieldVisible.setPrefWidth(370);
        passFieldVisible.setVisible(false);
        passFieldVisible.setManaged(false);
        addFieldFocusEffects(passFieldVisible);

        passField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                if (!passField.getStyleClass().contains("has-text")) {
                    passField.getStyleClass().add("has-text");
                }
            } else {
                passField.getStyleClass().remove("has-text");
            }
        });

        passFieldVisible.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                if (!passFieldVisible.getStyleClass().contains("has-text")) {
                    passFieldVisible.getStyleClass().add("has-text");
                }
            } else {
                passFieldVisible.getStyleClass().remove("has-text");
            }
        });

        Button togglePassBtn = new Button("👁");
        togglePassBtn.getStyleClass().add("floating-btn");
        togglePassBtn.setOnAction(e -> {
            if (passField.isVisible()) {
                passFieldVisible.setText(passField.getText());
                passField.setVisible(false);
                passField.setManaged(false);
                passFieldVisible.setVisible(true);
                passFieldVisible.setManaged(true);
                passFieldVisible.requestFocus();
                passFieldVisible.positionCaret(passFieldVisible.getText().length());
            } else {
                passField.setText(passFieldVisible.getText());
                passFieldVisible.setVisible(false);
                passFieldVisible.setManaged(false);
                passField.setVisible(true);
                passField.setManaged(true);
                passField.requestFocus();
                passField.positionCaret(passField.getText().length());
            }
        });

        StackPane passPane = new StackPane(passField, passFieldVisible);

        HBox passLayout = new HBox(5, passPane, togglePassBtn);
        passLayout.setAlignment(Pos.CENTER_LEFT);
        passLayout.setMaxWidth(470);

        Label infoLabel = new Label("A OLT será adicionada à Lista de OLTs, e ficará salva."); infoLabel.getStyleClass().add("info-label");

        HBox btnRow = new HBox(10); btnRow.setAlignment(Pos.CENTER_RIGHT); btnRow.setPadding(new Insets(10, 0, 0, 0));
        Button cancelBtn = new Button("Cancelar"); cancelBtn.getStyleClass().add("logout-btn"); addEnhancedButtonHoverEffects(cancelBtn);
        Button addBtn = new Button("Adicionar"); addBtn.getStyleClass().add("connect-btn"); addEnhancedButtonHoverEffects(addBtn);

        cancelBtn.setOnAction(ev -> closeBtn.fire());

        addBtn.setOnAction(ev -> {
            String name = nameField.getText().trim();
            String ip = ipField.getText().trim();
            String port = portField.getText().trim();
            String user = userField.getText().trim();
            String password = passField.getText().trim();

            if (name.isEmpty() || ip.isEmpty()) {
                showToast("❌ Nome e IP são obrigatórios!");
                return;
            }
            if (!isValidIP(ip)) {
                showToast("❌ Formato de IP inválido!");
                return;
            }

            OLT newOlt = new OLT(name, ip, port, user, password);

            boolean isDuplicate = OLTList.getOLTs().stream()
                    .anyMatch(olt -> olt.getName().equalsIgnoreCase(newOlt.getName()) || olt.equals(newOlt));

            if (isDuplicate) {
                showToast("❌ Já existe uma OLT com esse nome ou IP/Porta!");
                return;
            }

            OLTList.addOLT(newOlt);

            Platform.runLater(this::refreshOLTScreen);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), content);
            fadeOut.setFromValue(1.0); fadeOut.setToValue(0.0);
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), content);
            scaleOut.setFromX(1.0); scaleOut.setFromY(1.0); scaleOut.setToX(0.9); scaleOut.setToY(0.9);
            ParallelTransition parallelOut = new ParallelTransition(fadeOut, scaleOut);
            parallelOut.setOnFinished(e -> animateModalClose(stage, content, () -> {
                stage.close();
                showToast("✅ OLT adicionada com sucesso!");
            }));
            parallelOut.play();
        });

        nameField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addBtn.fire();
            }
        });

        ipField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addBtn.fire();
            }
        });

        portField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addBtn.fire();
            }
        });

        userField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addBtn.fire();
            }
        });

        passField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addBtn.fire();
            }
        });

        passFieldVisible.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addBtn.fire();
            }
        });

        btnRow.getChildren().addAll(cancelBtn, addBtn);
        content.getChildren().addAll(titleBar, nameLabel, nameField, ipLabel, ipField, portLabel, portField, userLabel, userField, passLabel, passLayout, infoLabel, btnRow);
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyThemeToNewScene(scene);
        stage.setScene(scene);

        stage.setOpacity(0);
        content.setScaleX(0.9);
        content.setScaleY(0.9);
        stage.centerOnScreen();
        stage.show();
        animateModalOpen(stage, content);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), content); fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(300), content); scaleIn.setFromX(0.9); scaleIn.setFromY(0.9); scaleIn.setToX(1.0); scaleIn.setToY(1.0);
        Timeline stageOpacity = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(stage.opacityProperty(), 0)), new KeyFrame(Duration.millis(300), new KeyValue(stage.opacityProperty(), 1)));
        ParallelTransition parallelIn = new ParallelTransition(fadeIn, scaleIn, stageOpacity); parallelIn.setInterpolator(Interpolator.EASE_OUT);
        parallelIn.play();
    }

    private void showRemoveOLTConfirmation(OLT olt) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(20);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(25));
        content.setPrefSize(400, 250);
        content.setAlignment(Pos.CENTER);
        content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(10, 12, 10, 12));
        closeBtn.setOnAction(ev -> animateModalClose(stage, content, null));
        addEnhancedButtonHoverEffects(closeBtn);
        titleBar.getChildren().add(closeBtn);

        VBox confirmContent = new VBox(15);
        confirmContent.setAlignment(Pos.CENTER);
        confirmContent.setPadding(new Insets(0, 10, 10, 10));

        Label confirmLabel = new Label("Tem certeza que deseja remover a OLT?");
        confirmLabel.getStyleClass().add("form-label");

        Label oltNameLabel = new Label(olt.name + " (" + olt.ip + ")");
        oltNameLabel.getStyleClass().add("olt-name");
        oltNameLabel.setStyle("-fx-text-fill: #ff4444; -fx-font-weight: bold;");

        HBox btnRow = new HBox(15);
        btnRow.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("cancel-btn");
        addEnhancedButtonHoverEffects(cancelBtn);

        Button removeBtn = new Button("Remover");
        removeBtn.getStyleClass().add("logout-btn");
        addEnhancedButtonHoverEffects(removeBtn);

        cancelBtn.setOnAction(ev -> animateModalClose(stage, content, null));

        removeBtn.setOnAction(ev -> {
            OLTList.removeOLT(olt);
            Platform.runLater(this::refreshOLTScreen);
            animateModalClose(stage, content, () -> showToast("❌ OLT removida com sucesso!"));
        });

        btnRow.getChildren().addAll(cancelBtn, removeBtn);
        confirmContent.getChildren().addAll(confirmLabel, oltNameLabel, btnRow);
        content.getChildren().addAll(titleBar, confirmContent);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyThemeToNewScene(scene);

        stage.setScene(scene);

        stage.setOpacity(0);
        content.setScaleX(0.8);
        content.setScaleY(0.8);

        stage.show();
        stage.centerOnScreen();

        animateModalOpen(stage, content);
    }

    private boolean isValidIP(String ip) {
        return ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    }

    private void refreshOLTScreen() {
        refreshOltCardsList();
        applyOLTFiltersAndSorting();
        refreshAllOLTStatuses();
        updateStatsBar();
    }

    private void refreshOltCardsList() {
        List<OLT> allOLTs = OLTList.getOLTs();
        boolean isMaximized = primaryStage != null && primaryStage.isMaximized();

        oltCardNodes.keySet().removeIf(olt -> !allOLTs.contains(olt));
        oltStatusIndicators.keySet().removeIf(olt -> !allOLTs.contains(olt));
        oltStatusLabels.keySet().removeIf(olt -> !allOLTs.contains(olt));

        for (OLT olt : allOLTs) {
            oltCardNodes.computeIfAbsent(olt, o -> createEnhancedOLTCard(o, isMaximized));
        }
    }

    private void applyOLTFiltersAndSorting() {
        if (oltCardsPane == null) return;

        String searchText = oltSearchField.getText().toLowerCase().trim();
        String statusFilter = oltStatusFilter.getValue();
        String sortBy = oltSortBy.getValue();

        List<OLT> filteredOLTs = OLTList.getOLTs().stream()
                .filter(olt -> {
                    boolean matchesSearch = searchText.isEmpty() ||
                            olt.getName().toLowerCase().contains(searchText) ||
                            olt.getIp().contains(searchText);
                    if (!matchesSearch) return false;

                    if (statusFilter != null && !"Status".equals(statusFilter)) {
                        Label statusLabel = oltStatusLabels.get(olt);
                        if (statusLabel == null || !statusFilter.equals(statusLabel.getText())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        filteredOLTs.sort((olt1, olt2) -> {
            switch (sortBy) {
                case "Nome Z-A":
                    return olt2.getName().compareToIgnoreCase(olt1.getName());
                case "Status":
                    int status1Value = getStatusValue(oltStatusLabels.getOrDefault(olt1, new Label("N/A")).getText());
                    int status2Value = getStatusValue(oltStatusLabels.getOrDefault(olt2, new Label("N/A")).getText());
                    return Integer.compare(status1Value, status2Value);
                case "IP":
                    return olt1.getIp().compareTo(olt2.getIp());
                case "Nome A-Z":
                default:
                    return olt1.getName().compareToIgnoreCase(olt2.getName());
            }
        });

        List<Node> cardNodes = filteredOLTs.stream()
                .map(oltCardNodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Platform.runLater(() -> {
            oltCardsPane.getChildren().setAll(cardNodes);
        });
    }

    private int getStatusValue(String status) {
        switch (status) {
            case "Online": return 1;
            case "Verificando...": return 2;
            case "Offline": return 3;
            default: return 4;
        }
    }

    private void setupResponsiveLayout(VBox content, FlowPane cardsPane) {
        final boolean[] isMaximized = {false};

        ChangeListener<Number> widthChangeListener = (obs, oldVal, newVal) -> {
            if (newVal == null || cardsPane.getScene() == null) return;

            double availableWidth = cardsPane.getScene().getWidth() - mainContent.getLeft().getLayoutBounds().getWidth() - 40;
            double cardWidth = isMaximized[0] ? 180 : 160;
            int gap = 15;
            int numColumns = Math.max(1, (int) (availableWidth / (cardWidth + gap)));
            double newPaneWidth = numColumns * (cardWidth + gap) - gap;
            cardsPane.setPrefWrapLength(newPaneWidth);
        };

        cardsPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Stage stage = (Stage) newScene.getWindow();
                isMaximized[0] = stage.isMaximized();

                newScene.widthProperty().addListener(widthChangeListener);

                stage.maximizedProperty().addListener((obs2, oldMax, newMax) -> {
                    isMaximized[0] = newMax;
                    refreshOltCardsList();
                    applyOLTFiltersAndSorting();
                    Platform.runLater(() -> widthChangeListener.changed(null, null, newScene.getWidth()));
                });

                Platform.runLater(() -> widthChangeListener.changed(null, null, newScene.getWidth()));
            }
        });
    }

    private void refreshAllOLTStatuses() {
        oltCardNodes.keySet().forEach(this::refreshSingleOLTStatus);
    }

    private void refreshSingleOLTStatus(OLT olt) {
        Circle statusIndicator = oltStatusIndicators.get(olt);
        Label statusLabel = oltStatusLabels.get(olt);

        if (statusIndicator == null || statusLabel == null) {
            return;
        }

        Platform.runLater(() -> {
            statusIndicator.setFill(Color.KHAKI);
            statusLabel.setText("Verificando...");
            updateStatsBar();
        });

        SSHManager checker = new SSHManager();
        checker.checkOLTStatus(olt.ip).thenAcceptAsync(isOnline -> {
            Platform.runLater(() -> {
                Circle indicator = oltStatusIndicators.get(olt);
                Label label = oltStatusLabels.get(olt);
                if (indicator != null && label != null) {
                    if (isOnline) {
                        indicator.setFill(Color.LIGHTGREEN);
                        label.setText("Online");
                    } else {
                        indicator.setFill(Color.INDIANRED);
                        label.setText("Offline");
                    }
                    updateStatsBar();
                    applyOLTFiltersAndSorting();
                }
            });
        }, Platform::runLater);
    }
    // ---------------------- OLTs ---------------------- //


    // ---------------------- Consulta de Sinal ---------------------- //
    private Node createSignalQueryScreen() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 20, 0));
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Consulta de Sinal Óptico");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);

        Label infoLabel = new Label("Verifique o Sinal Óptico da Primária.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240);
        addComboBoxFocusEffects(oltComboBox);

        TextField fsField = new TextField();
        fsField.setPromptText("Digite o F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115);
        addFieldFocusEffects(fsField);

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115);
        addFieldFocusEffects(pField);

        HBox formRow = new HBox(10);
        formRow.setAlignment(Pos.CENTER);
        formRow.getChildren().addAll(fsField, pField);
        formRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fsField, Priority.ALWAYS);
        HBox.setHgrow(pField, Priority.ALWAYS);

        TextFormatter<String> pFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,3}")) {
                return change;
            }
            return null;
        });
        pField.setTextFormatter(pFormatter);

        TextFormatter<String> fsFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9/]{0,4}")) {
                return change;
            }
            return null;
        });
        fsField.setTextFormatter(fsFormatter);

        Button queryBtn = new Button("Consultar");
        queryBtn.getStyleClass().add("connect-btn");
        queryBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(queryBtn);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("connect-btn");
        cancelBtn.setMaxWidth(140);
        cancelBtn.setDisable(true);
        addEnhancedButtonHoverEffects(cancelBtn);

        CodeArea resultArea = new CodeArea();
        resultArea.setEditable(false);
        resultArea.getStyleClass().add("code-area");
        resultArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(resultArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> consultaThread = new AtomicReference<>();

        Runnable resetConsultaControls = () -> {
            Thread thread = consultaThread.getAndSet(null);
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }

            SSHManager ssh = currentSSHManager.getAndSet(null);
            if (ssh != null) {
                ssh.disconnect();
            }

            Platform.runLater(() -> {
                queryBtn.setDisable(false);
                cancelBtn.setDisable(true);
                oltComboBox.setDisable(false);
                fsField.setDisable(false);
                pField.setDisable(false);
                isQueryInProgress.set(false);
                showWaitingToast(false);
            });
        };

        Runnable queryAction = () -> {
            if (isQueryInProgress.get()) {
                showWaitingToast(true);
                return;
            }

            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty()) {
                showToast("❌ Por favor, preencha todos os campos corretamente.");
                return;
            }

            queryBtn.setDisable(true);
            cancelBtn.setDisable(false);
            oltComboBox.setDisable(true);
            fsField.setDisable(true);
            pField.setDisable(true);
            isQueryInProgress.set(true);

            resultArea.clear();
            String initialMessage = "Consultando os Sinais da PON " + fs + "/" + p + " na " + selectedOLT.name + " (" + selectedOLT.ip + "), aguarde...\n";
            appendStyledTextWithIPHighlight(resultArea, initialMessage, selectedOLT.ip, "ip-address");

            SSHManager sshManager = new SSHManager();
            sshManager.setConnectTimeout(10000);
            currentSSHManager.set(sshManager);

            Thread queryThread = new Thread(() -> {
                try {
                    boolean connected = sshManager.connect(selectedOLT.getIp(), selectedOLT.getUser(), selectedOLT.getPassword(), resultArea, false);
                    if (!connected || Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    String resultado = sshManager.queryOpticalSignal(fs, p);

                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultArea.replaceText(resultado);
                            destacarIPs(resultArea);
                            showToast("🔎 Consulta de Sinal Óptico finalizada!");
                        });
                    }

                } catch (Exception ex) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultArea.replaceText("Erro na consulta: " + ex.getMessage());
                            destacarIPs(resultArea);
                            showToast("⚠️ Erro ao consultar Sinal Óptico.");
                        });
                    }
                } finally {
                    sshManager.disconnect();
                    Platform.runLater(() -> resetConsultaControls.run());
                }
            });

            consultaThread.set(queryThread);
            queryThread.setDaemon(true);
            queryThread.start();
        };

        queryBtn.setOnAction(e -> queryAction.run());
        fsField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !queryBtn.isDisabled()) queryAction.run(); });
        pField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !queryBtn.isDisabled()) queryAction.run(); });

        cancelBtn.setOnAction(e -> {
            Platform.runLater(() -> {
                resultArea.replaceText("Consulta cancelada pelo usuário.");
                showToast("❌ Consulta cancelada!");
            });
            resetConsultaControls.run();
        });

        HBox consultaButtonContainer = new HBox(10);
        consultaButtonContainer.setAlignment(Pos.CENTER);
        consultaButtonContainer.getChildren().addAll(queryBtn, cancelBtn);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(exportBtn);
        exportBtn.setOnAction(e -> exportarResultado(resultArea, "Consulta_Sinal"));

        Button limparBtn = new Button("Limpar");
        limparBtn.getStyleClass().add("connect-btn");
        limparBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(limparBtn);
        limparBtn.setOnAction(e -> {
            if (resultArea.getText().trim().isEmpty()) {
                showToast("❌ Terminal já está vazio!");
            } else {
                resultArea.clear();
                showToast("✅ Terminal limpo!");
            }
        });

        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(exportBtn, limparBtn);

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                formRow,
                consultaButtonContainer,
                scrollPane,
                buttonContainer
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- Consulta de Sinal ---------------------- //


    // ---------------------- Summary ---------------------- //
    private Node createPONSummaryScreen() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 20, 0));
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Resumo da PON");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);

        Label infoLabel = new Label("Verifique todas as informações da Primária.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240);
        addComboBoxFocusEffects(oltComboBox);

        TextField ponField = new TextField();
        ponField.setPromptText("Digite o F/S/P");
        ponField.getStyleClass().add("text-field");
        ponField.setMaxWidth(240);
        addFieldFocusEffects(ponField);

        TextFormatter<String> ponFormatter = new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (change.isContentChange() && !newText.matches("[0-9/]{0,7}")) {
                return null;
            }
            return change;
        });
        ponField.setTextFormatter(ponFormatter);

        Button consultarBtn = new Button("Consultar");
        consultarBtn.getStyleClass().add("connect-btn");
        consultarBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(consultarBtn);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("connect-btn");
        cancelBtn.setMaxWidth(140);
        cancelBtn.setDisable(true);
        addEnhancedButtonHoverEffects(cancelBtn);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(resultadoArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> consultaThread = new AtomicReference<>();

        Runnable resetConsultaControls = () -> {
            Thread thread = consultaThread.getAndSet(null);
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }

            SSHManager ssh = currentSSHManager.getAndSet(null);
            if (ssh != null) {
                ssh.disconnect();
            }

            Platform.runLater(() -> {
                consultarBtn.setDisable(false);
                cancelBtn.setDisable(true);
                oltComboBox.setDisable(false);
                ponField.setDisable(false);
                isQueryInProgress.set(false);
                showWaitingToast(false);
            });
        };

        Runnable queryAction = () -> {
            if (isQueryInProgress.get()) {
                showWaitingToast(true);
                return;
            }

            OLT selectedOLT = oltComboBox.getValue();
            String pon = ponField.getText().trim();

            if (selectedOLT == null || pon.isEmpty()) {
                showToast("❌ Por favor, preencha todos os campos corretamente.");
                return;
            }

            consultarBtn.setDisable(true);
            cancelBtn.setDisable(false);
            oltComboBox.setDisable(true);
            ponField.setDisable(true);
            isQueryInProgress.set(true);

            resultadoArea.clear();
            String initialMessage = "Consultando o Summary da PON " + pon + " na " + selectedOLT.name + " (" + selectedOLT.ip + "), aguarde...\n";
            appendStyledTextWithIPHighlight(resultadoArea, initialMessage, selectedOLT.ip, "ip-address");

            SSHManager sshManager = new SSHManager();
            sshManager.setConnectTimeout(15000);
            currentSSHManager.set(sshManager);

            Thread thread = new Thread(() -> {
                try {
                    boolean connected = sshManager.connect(selectedOLT.getIp(), selectedOLT.getUser(), selectedOLT.getPassword(), resultadoArea, false);
                    if (!connected || Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    String result = sshManager.queryPonSummary(selectedOLT.name, pon);

                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(result);
                            destacarIPs(resultadoArea);
                            showToast("🔎 Consulta de Summary finalizada!");
                        });
                        DatabaseManager.logUsuario(usuario.getNome(), "Consultou a PON " + pon + " na " + selectedOLT.name);
                    }

                } catch (Exception ex) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText("Erro na consulta PON Summary: " + ex.getMessage());
                            destacarIPs(resultadoArea);
                            showToast("⚠️ Erro ao consultar PON Summary.");
                        });
                    }
                } finally {
                    sshManager.disconnect();
                    Platform.runLater(() -> resetConsultaControls.run());
                }
            });

            consultaThread.set(thread);
            thread.setDaemon(true);
            thread.start();
        };

        consultarBtn.setOnAction(e -> queryAction.run());
        ponField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !consultarBtn.isDisabled()) queryAction.run(); });

        cancelBtn.setOnAction(e -> {
            Platform.runLater(() -> {
                resultadoArea.replaceText("Consulta cancelada pelo usuário.");
                showToast("❌ Consulta cancelada!");
            });
            resetConsultaControls.run();
        });

        HBox consultaButtonContainer = new HBox(10);
        consultaButtonContainer.setAlignment(Pos.CENTER);
        consultaButtonContainer.getChildren().addAll(consultarBtn, cancelBtn);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(exportBtn);
        exportBtn.setOnAction(e -> exportarResultado(resultadoArea, "Resumo_PON"));

        Button limparBtn = new Button("Limpar");
        limparBtn.getStyleClass().add("connect-btn");
        limparBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(limparBtn);
        limparBtn.setOnAction(e -> {
            if (resultadoArea.getText().trim().isEmpty()) {
                showToast("❌ Terminal já está vazio!");
            } else {
                resultadoArea.clear();
                showToast("✅ Terminal limpo!");
            }
        });

        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(exportBtn, limparBtn);

        formArea.getChildren().addAll(
                infoLabel, oltComboBox, ponField, consultaButtonContainer, scrollPane, buttonContainer
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- Summary ---------------------- //


    // ---------------------- By-SN ---------------------- //
    private Node createONUBySNScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Consulta ONT/ONU por SN");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);

        Label infoLabel = new Label("Verifique todas as informações da ONT/ONU.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240);
        addComboBoxFocusEffects(oltComboBox);

        TextField snField = new TextField();
        snField.setPromptText("Digite o SN da ONT/ONU");
        snField.getStyleClass().add("text-field");
        snField.setMaxWidth(240);
        addFieldFocusEffects(snField);

        TextFormatter<String> snFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[A-Za-z0-9]{0,20}")) {
                return change;
            }
            return null;
        });
        snField.setTextFormatter(snFormatter);

        Button consultarBtn = new Button("Consultar");
        consultarBtn.getStyleClass().add("connect-btn");
        consultarBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(consultarBtn);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("connect-btn");
        cancelBtn.setMaxWidth(140);
        cancelBtn.setDisable(true);
        addEnhancedButtonHoverEffects(cancelBtn);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(resultadoArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> consultaThread = new AtomicReference<>();

        Runnable resetConsultaControls = () -> {
            Thread thread = consultaThread.getAndSet(null);
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }

            SSHManager ssh = currentSSHManager.getAndSet(null);
            if (ssh != null) {
                ssh.disconnect();
            }

            Platform.runLater(() -> {
                consultarBtn.setDisable(false);
                cancelBtn.setDisable(true);
                oltComboBox.setDisable(false);
                snField.setDisable(false);
                isQueryInProgress.set(false);
                showWaitingToast(false);
            });
        };

        Runnable queryAction = () -> {
            if (isQueryInProgress.get()) {
                showWaitingToast(true);
                return;
            }
            OLT selectedOLT = oltComboBox.getValue();
            String sn = snField.getText().trim();

            if (selectedOLT == null || sn.isEmpty()) {
                showToast("❌ Por favor, preencha todos os campos corretamente.");
                return;
            }

            consultarBtn.setDisable(true);
            cancelBtn.setDisable(false);
            oltComboBox.setDisable(true);
            snField.setDisable(true);
            isQueryInProgress.set(true);

            resultadoArea.clear();
            String initialMessage = "Consultando Informações do SN " + sn + " na " + selectedOLT.name + " (" + selectedOLT.ip + "), aguarde...\n";
            appendStyledTextWithIPHighlight(resultadoArea, initialMessage, selectedOLT.ip, "ip-address");

            SSHManager sshManager = new SSHManager();
            sshManager.setConnectTimeout(18000);
            currentSSHManager.set(sshManager);

            Thread thread = new Thread(() -> {
                try {
                    boolean connected = sshManager.connect(selectedOLT.getIp(), selectedOLT.getUser(), selectedOLT.getPassword(), resultadoArea, false);
                    if (!connected || Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    String result = sshManager.queryOntInfoBySn(sn);

                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(result);
                            destacarIPs(resultadoArea);
                            showToast("🔎 Consulta By-SN finalizada!");
                        });
                        DatabaseManager.logUsuario(usuario.getNome(), "Consultou ONT/ONU pelo SN " + sn + " na " + selectedOLT.name);
                    }
                } catch (Exception ex) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText("Erro na consulta By-SN: " + ex.getMessage());
                            destacarIPs(resultadoArea);
                            showToast("⚠️ Erro ao consultar By-SN.");
                        });
                    }
                } finally {
                    sshManager.disconnect();
                    Platform.runLater(() -> resetConsultaControls.run());
                }
            });

            consultaThread.set(thread);
            thread.setDaemon(true);
            thread.start();
        };

        consultarBtn.setOnAction(e -> queryAction.run());
        snField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !consultarBtn.isDisabled()) queryAction.run(); });

        cancelBtn.setOnAction(e -> {
            Platform.runLater(() -> {
                resultadoArea.replaceText("Consulta cancelada pelo usuário.");
                showToast("❌ Consulta cancelada!");
            });
            resetConsultaControls.run();
        });

        HBox consultaButtonContainer = new HBox(10);
        consultaButtonContainer.setAlignment(Pos.CENTER);
        consultaButtonContainer.getChildren().addAll(consultarBtn, cancelBtn);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(exportBtn);
        exportBtn.setOnAction(e -> exportarResultado(resultadoArea, "Consulta_SN"));

        Button limparBtn = new Button("Limpar");
        limparBtn.getStyleClass().add("connect-btn");
        limparBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(limparBtn);
        limparBtn.setOnAction(e -> {
            if (resultadoArea.getText().trim().isEmpty()) {
                showToast("❌ Terminal já está vazio!");
            } else {
                resultadoArea.clear();
                showToast("✅ Terminal limpo!");
            }
        });

        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(exportBtn, limparBtn);

        formArea.getChildren().addAll(
                infoLabel, oltComboBox, snField, consultaButtonContainer, scrollPane, buttonContainer
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- By-SN ---------------------- //


    // ---------------------- Quedas ---------------------- //
    private Node createDropDiagnosisScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Diagnóstico de Quedas da ONT/ONU");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);

        Label infoLabel = new Label("Verifique o Diagnóstico de Quedas da ONT/ONU.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(363.5);
        addComboBoxFocusEffects(oltComboBox);

        TextField fsField = new TextField();
        fsField.setPromptText("Digite F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115);
        fsField.setTextFormatter(new TextFormatter<>(c -> c.getControlNewText().matches("[0-9/]{0,4}") ? c : null));
        addFieldFocusEffects(fsField);

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115);
        pField.setTextFormatter(new TextFormatter<>(c -> c.getControlNewText().matches("[0-9]{0,3}") ? c : null));
        addFieldFocusEffects(pField);

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("ID da ONT");
        ontIdField.getStyleClass().add("text-field");
        ontIdField.setMaxWidth(115);
        ontIdField.setTextFormatter(new TextFormatter<>(c -> c.getControlNewText().matches("[0-9]{0,4}") ? c : null));
        addFieldFocusEffects(ontIdField);

        HBox formRow2 = new HBox(10, fsField, pField, ontIdField);
        formRow2.setAlignment(Pos.CENTER);
        HBox.setHgrow(fsField, Priority.ALWAYS);
        HBox.setHgrow(pField, Priority.ALWAYS);
        HBox.setHgrow(ontIdField, Priority.ALWAYS);

        Button diagnosticarBtn = new Button("Consultar");
        diagnosticarBtn.getStyleClass().add("connect-btn");
        diagnosticarBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(diagnosticarBtn);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("connect-btn");
        cancelBtn.setMaxWidth(140);
        cancelBtn.setDisable(true);
        addEnhancedButtonHoverEffects(cancelBtn);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(resultadoArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> consultaThread = new AtomicReference<>();

        Runnable resetConsultaControls = () -> {
            Thread thread = consultaThread.getAndSet(null);
            if (thread != null && thread.isAlive()) thread.interrupt();

            SSHManager ssh = currentSSHManager.getAndSet(null);
            if (ssh != null) ssh.disconnect();

            Platform.runLater(() -> {
                diagnosticarBtn.setDisable(false);
                cancelBtn.setDisable(true);
                oltComboBox.setDisable(false);
                fsField.setDisable(false);
                pField.setDisable(false);
                ontIdField.setDisable(false);
                isQueryInProgress.set(false);
                showWaitingToast(false);
            });
        };

        Runnable queryAction = () -> {
            if (isQueryInProgress.get()) {
                showWaitingToast(true);
                return;
            }

            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty() || ontId.isEmpty()) {
                showToast("❌ Por favor, preencha todos os campos corretamente.");
                return;
            }

            diagnosticarBtn.setDisable(true);
            cancelBtn.setDisable(false);
            oltComboBox.setDisable(true);
            fsField.setDisable(true);
            pField.setDisable(true);
            ontIdField.setDisable(true);
            isQueryInProgress.set(true);

            resultadoArea.clear();
            String initialMessage = "Iniciando Diagnóstico de Quedas para ID " + ontId + " na PON " + fs + "/" + p + " da " + selectedOLT.name + " (" + selectedOLT.ip + "), aguarde...\n";
            appendStyledTextWithIPHighlight(resultadoArea, initialMessage, selectedOLT.ip, "ip-address");

            SSHManager sshManager = new SSHManager();
            sshManager.setConnectTimeout(10000);
            currentSSHManager.set(sshManager);

            Thread thread = new Thread(() -> {
                try {
                    boolean connected = sshManager.connect(selectedOLT.getIp(), selectedOLT.getUser(), selectedOLT.getPassword(), resultadoArea, false);
                    if (!connected || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    String result = sshManager.queryOntRegisterInfo(fs, p, ontId);

                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(result);
                            destacarIPs(resultadoArea);
                            showToast("🔎 Consulta de Quedas finalizada!");
                        });
                        DatabaseManager.logUsuario(usuario.getNome(), "Consultou diagnóstico de quedas da ONT/ONU " + fs + "/" + p + "/" + ontId + " na " + selectedOLT.name);
                    }
                } catch (Exception ex) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText("Erro no diagnóstico de quedas: " + ex.getMessage());
                            destacarIPs(resultadoArea);
                            showToast("⚠️ Erro ao consultar Quedas.");
                        });
                    }
                } finally {
                    sshManager.disconnect();
                    Platform.runLater(resetConsultaControls);
                }
            });

            consultaThread.set(thread);
            thread.setDaemon(true);
            thread.start();
        };

        diagnosticarBtn.setOnAction(e -> queryAction.run());
        cancelBtn.setOnAction(e -> {
            resultadoArea.replaceText("Consulta cancelada pelo usuário.");
            showToast("❌ Consulta cancelada!");
            resetConsultaControls.run();
        });

        fsField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && !diagnosticarBtn.isDisabled()) queryAction.run(); });
        pField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && !diagnosticarBtn.isDisabled()) queryAction.run(); });
        ontIdField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && !diagnosticarBtn.isDisabled()) queryAction.run(); });

        HBox consultaButtons = new HBox(10, diagnosticarBtn, cancelBtn);
        consultaButtons.setAlignment(Pos.CENTER);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(exportBtn);
        exportBtn.setOnAction(e -> exportarResultado(resultadoArea, "Diagnostico_Quedas"));

        Button limparBtn = new Button("Limpar");
        limparBtn.getStyleClass().add("connect-btn");
        limparBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(limparBtn);
        limparBtn.setOnAction(e -> {
            if (resultadoArea.getText().trim().isEmpty()) {
                showToast("❌ Terminal já está vazio!");
            } else {
                resultadoArea.clear();
                showToast("✅ Terminal limpo!");
            }
        });

        HBox extraButtons = new HBox(10, exportBtn, limparBtn);
        extraButtons.setAlignment(Pos.CENTER);

        formArea.getChildren().addAll(
                infoLabel, oltComboBox, formRow2, consultaButtons, scrollPane, extraButtons
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- Quedas ---------------------- //


    // ---------------------- Tráfego ---------------------- //
    private Node createONUTrafficScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Monitoramento de Tráfego ONT/ONU");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);

        Label infoLabel = new Label("Monitore o Tráfego e Velocidade da ONT/ONU.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(363.5);
        addComboBoxFocusEffects(oltComboBox);

        TextField fsField = new TextField();
        fsField.setPromptText("Digite F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115);
        addFieldFocusEffects(fsField);

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115);
        addFieldFocusEffects(pField);

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("ID da ONT");
        ontIdField.getStyleClass().add("text-field");
        ontIdField.setMaxWidth(115);
        addFieldFocusEffects(ontIdField);

        TextFormatter<String> fsFormatter = new TextFormatter<>(change -> change.getControlNewText().matches("[0-9/]{0,4}") ? change : null);
        fsField.setTextFormatter(fsFormatter);
        TextFormatter<String> pFormatter = new TextFormatter<>(change -> change.getControlNewText().matches("[0-9]{0,3}") ? change : null);
        pField.setTextFormatter(pFormatter);
        TextFormatter<String> ontIdFormatter = new TextFormatter<>(change -> change.getControlNewText().matches("[0-9]{0,4}") ? change : null);
        ontIdField.setTextFormatter(ontIdFormatter);

        HBox formRow1 = new HBox(10);
        formRow1.setAlignment(Pos.CENTER);
        formRow1.getChildren().addAll(fsField, pField, ontIdField);
        HBox.setHgrow(fsField, Priority.ALWAYS);
        HBox.setHgrow(pField, Priority.ALWAYS);
        HBox.setHgrow(ontIdField, Priority.ALWAYS);

        Button monitorBtn = new Button("Monitorar");
        monitorBtn.getStyleClass().add("connect-btn");
        monitorBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(monitorBtn);

        Button stopBtn = new Button("Parar");
        stopBtn.getStyleClass().add("stop-btn");
        stopBtn.setMaxWidth(140);
        stopBtn.setDisable(true);
        addEnhancedButtonHoverEffects(stopBtn);

        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.getChildren().addAll(monitorBtn, stopBtn);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(resultadoArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        AtomicBoolean monitoring = new AtomicBoolean(false);
        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> monitoringThread = new AtomicReference<>();
        AtomicReference<Timer> timeoutTimerRef = new AtomicReference<>();


        Runnable resetMonitoringControls = () -> {
            monitoring.set(false);

            Timer currentTimer = timeoutTimerRef.getAndSet(null);
            if (currentTimer != null) {
                currentTimer.cancel();
            }

            Thread monThread = monitoringThread.getAndSet(null);
            if (monThread != null && monThread.isAlive()) {
                monThread.interrupt();
            }

            SSHManager ssh = currentSSHManager.getAndSet(null);
            if (ssh != null) {
                ssh.disconnect();
            }

            Platform.runLater(() -> {
                monitorBtn.setDisable(false);
                stopBtn.setDisable(true);
                oltComboBox.setDisable(false);
                fsField.setDisable(false);
                pField.setDisable(false);
                ontIdField.setDisable(false);
                isQueryInProgress.set(false);
                showWaitingToast(false);
            });
        };

        Runnable startMonitoringAction = () -> {
            if (isQueryInProgress.get()) {
                showWaitingToast(true);
                return;
            }
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty() || ontId.isEmpty()) {
                showToast("❌ Por favor, preencha todos os campos corretamente.");
                return;
            }

            if (monitoring.get()) return;

            isQueryInProgress.set(true);
            monitoring.set(true);

            resultadoArea.clear();
            String initialMessage = "Iniciando Monitoramento de Tráfego para ID " + ontId + " na PON " + fs + "/" + p + " da " + selectedOLT.name + " (" + selectedOLT.ip + "), aguarde...\n" +
                    "O Monitoramento será executado por 2 minutos ou até que seja interrompido manualmente.\n";
            appendStyledTextWithIPHighlight(resultadoArea, initialMessage, selectedOLT.ip, "ip-address");

            monitorBtn.setDisable(true);
            stopBtn.setDisable(false);
            oltComboBox.setDisable(true);
            fsField.setDisable(true);
            pField.setDisable(true);
            ontIdField.setDisable(true);

            SSHManager sshManager = new SSHManager();
            currentSSHManager.set(sshManager);

            Timer timeoutTimer = new Timer(true);
            timeoutTimerRef.set(timeoutTimer);
            timeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (monitoring.get()) {
                        Platform.runLater(() -> {
                            resultadoArea.appendText("\nMonitoramento finalizado automaticamente após 2 minutos.\n");
                            resetMonitoringControls.run();
                        });
                    }
                }
            }, 120000);

            Thread thread = new Thread(() -> {
                try {
                boolean connected = sshManager.connect(selectedOLT.getIp(), selectedOLT.getUser(), selectedOLT.getPassword(), resultadoArea, false);
                    if (!connected) {
                        Platform.runLater(resetMonitoringControls::run);
                        return;
                    }

                    long startTime = System.currentTimeMillis();
                    long endTime = startTime + 120000;

                    while (monitoring.get() && System.currentTimeMillis() < endTime) {
                        try {
                            String trafficData = sshManager.queryOntTraffic(fs, p, ontId);
                            Platform.runLater(() -> {
                                if(monitoring.get()){
                                    resultadoArea.appendText("\n" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n");
                                    resultadoArea.appendText(trafficData);
                                    resultadoArea.moveTo(resultadoArea.getLength());
                                    resultadoArea.requestFollowCaret();
                                    destacarIPs(resultadoArea);
                                }
                            });
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                } catch (Exception ex) {
                    Platform.runLater(() -> resultadoArea.appendText("\nErro durante o monitoramento: " + ex.getMessage() + "\n"));
                } finally {
                    if (monitoring.get()) {
                        Platform.runLater(resetMonitoringControls::run);
                    }
                }
            });

            monitoringThread.set(thread);
            thread.setDaemon(true);
            thread.start();
            DatabaseManager.logUsuario(usuario.getNome(), "Iniciou monitoramento de tráfego para ONT " + ontId + " na PON " + fs + "/" + p + " na " + selectedOLT.name);
        };

        monitorBtn.setOnAction(e -> startMonitoringAction.run());

        fsField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) startMonitoringAction.run(); });
        pField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) startMonitoringAction.run(); });
        ontIdField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) startMonitoringAction.run(); });

        stopBtn.setOnAction(e -> {
            if(monitoring.get()){
                resultadoArea.appendText("\nMonitoramento interrompido manualmente.\n");
            }
            resetMonitoringControls.run();
        });
        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(exportBtn);
        exportBtn.setOnAction(e -> exportarResultado(resultadoArea, "Monitoramento_Trafego"));

        Button limparBtn = new Button("Limpar");
        limparBtn.getStyleClass().add("connect-btn");
        limparBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(limparBtn);
        limparBtn.setOnAction(e -> {
            if (resultadoArea.getText().trim().isEmpty()) {
                showToast("❌ Terminal já está vazio!");
            } else {
                resultadoArea.clear();
                showToast("✅ Terminal limpo!");
            }
        });

        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(exportBtn, limparBtn);

        formArea.getChildren().addAll(
                infoLabel, oltComboBox, formRow1, buttonRow, scrollPane, buttonContainer
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- Tráfego ---------------------- //


    // ---------------------- Serviços ---------------------- //
    private Node createONUServiceScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Consulta de Serviços da ONT/ONU");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);

        Label infoLabel = new Label("Monitore os serviços da ONT/ONU.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240);
        addComboBoxFocusEffects(oltComboBox);

        TextField fspField = new TextField();
        fspField.setPromptText("Digite F/S/P");
        fspField.getStyleClass().add("text-field");
        fspField.setMaxWidth(115);
        addFieldFocusEffects(fspField);

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("ID da ONT");
        ontIdField.getStyleClass().add("text-field");
        ontIdField.setMaxWidth(115);
        addFieldFocusEffects(ontIdField);

        TextFormatter<String> ponFormatter = new TextFormatter<>(change -> change.getControlNewText().matches("[0-9/]{0,7}") ? change : null);
        fspField.setTextFormatter(ponFormatter);
        TextFormatter<String> ontIdFormatter = new TextFormatter<>(change -> change.getControlNewText().matches("[0-9]{0,4}") ? change : null);
        ontIdField.setTextFormatter(ontIdFormatter);

        HBox formRow3 = new HBox(10);
        formRow3.setAlignment(Pos.CENTER);
        formRow3.getChildren().addAll(fspField, ontIdField);
        HBox.setHgrow(fspField, Priority.ALWAYS);
        HBox.setHgrow(ontIdField, Priority.ALWAYS);

        Button consultBtn = new Button("Consultar");
        consultBtn.getStyleClass().add("connect-btn");
        consultBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(consultBtn);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("connect-btn");
        cancelBtn.setMaxWidth(140);
        cancelBtn.setDisable(true);
        addEnhancedButtonHoverEffects(cancelBtn);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setWrapText(false);
        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(resultadoArea);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> consultaThread = new AtomicReference<>();

        Runnable resetConsultaControls = () -> {
            Thread thread = consultaThread.getAndSet(null);
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
            }

            SSHManager ssh = currentSSHManager.getAndSet(null);
            if (ssh != null) {
                ssh.disconnect();
            }

            Platform.runLater(() -> {
                consultBtn.setDisable(false);
                cancelBtn.setDisable(true);
                oltComboBox.setDisable(false);
                fspField.setDisable(false);
                ontIdField.setDisable(false);
                isQueryInProgress.set(false);
                showWaitingToast(false);
            });
        };

        Runnable queryAction = () -> {
            if (isQueryInProgress.get()) {
                showWaitingToast(true);
                return;
            }
            OLT selectedOLT = oltComboBox.getValue();
            String fsp = fspField.getText().trim();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null || fsp.isEmpty() || ontId.isEmpty()) {
                showToast("❌ Por favor, preencha todos os campos corretamente.");
                return;
            }

            consultBtn.setDisable(true);
            cancelBtn.setDisable(false);
            oltComboBox.setDisable(true);
            fspField.setDisable(true);
            ontIdField.setDisable(true);
            isQueryInProgress.set(true);

            resultadoArea.clear();
            String initialMessage = "Consultando Serviços para ID " + ontId + " na PON " + fsp + " da " + selectedOLT.name + " (" + selectedOLT.ip + "), aguarde...\n";
            appendStyledTextWithIPHighlight(resultadoArea, initialMessage, selectedOLT.ip, "ip-address");

            SSHManager sshManager = new SSHManager();
            sshManager.setConnectTimeout(15000);
            currentSSHManager.set(sshManager);

            Thread thread = new Thread(() -> {
                try {
                    boolean connected = sshManager.connect(selectedOLT.getIp(), selectedOLT.getUser(), selectedOLT.getPassword(), resultadoArea, false);
                    if (!connected || Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    String result = sshManager.queryServicePortInfo(fsp, ontId);

                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(result);
                            destacarIPs(resultadoArea);
                            showToast("🔎 Consulta de Serviços finalizada!");
                        });
                        DatabaseManager.logUsuario(usuario.getNome(), "Consultou os serviços da ONT/ONU ID" + ontId + " da PON " + fsp + " na " + selectedOLT.name);
                    }
                } catch (Exception ex) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText("Erro na consulta de serviços: " + ex.getMessage());
                            destacarIPs(resultadoArea);
                            showToast("⚠️ Erro ao consultar Serviços.");
                        });
                    }
                } finally {
                    sshManager.disconnect();
                    Platform.runLater(() -> resetConsultaControls.run());
                }
            });

            consultaThread.set(thread);
            thread.setDaemon(true);
            thread.start();
        };

        consultBtn.setOnAction(e -> queryAction.run());
        fspField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !consultBtn.isDisabled()) queryAction.run(); });
        ontIdField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER && !consultBtn.isDisabled()) queryAction.run(); });

        cancelBtn.setOnAction(e -> {
            Platform.runLater(() -> {
                resultadoArea.replaceText("Consulta cancelada pelo usuário.");
                showToast("❌ Consulta cancelada!");
            });
            resetConsultaControls.run();
        });

        HBox consultaButtonContainer = new HBox(10);
        consultaButtonContainer.setAlignment(Pos.CENTER);
        consultaButtonContainer.getChildren().addAll(consultBtn, cancelBtn);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(exportBtn);
        exportBtn.setOnAction(e -> exportarResultado(resultadoArea, "Consulta_Servicos"));

        Button limparBtn = new Button("Limpar");
        limparBtn.getStyleClass().add("connect-btn");
        limparBtn.setMaxWidth(140);
        addEnhancedButtonHoverEffects(limparBtn);
        limparBtn.setOnAction(e -> {
            if (resultadoArea.getText().trim().isEmpty()) {
                showToast("❌ Terminal já está vazio!");
            } else {
                resultadoArea.clear();
                showToast("✅ Terminal limpo!");
            }
        });

        HBox buttonContainer = new HBox(10);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(exportBtn, limparBtn);

        formArea.getChildren().addAll(
                infoLabel, oltComboBox, formRow3, consultaButtonContainer, scrollPane, buttonContainer
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- Serviços ---------------------- //


    // ---------------------- Chamados (ADMIN)  ---------------------- //
    private Node createTechnicalTicketsScreen() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("content-area");
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Gerenciamento de Chamados");
        title.getStyleClass().add("title");

        TableView<Ticket> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setItems(FXCollections.observableArrayList(DatabaseManager.getAllTickets()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().addAll(
                createColumn("Criado por", "criadoPor", 150),
                createColumn("Descrição", "descricao", 350),
                createColumn("Prioridade", "previsao", 100),
                createColumn("Data/Hora", "dataHora", 150),
                createColumn("Status", "status", 120),
                createColumn("Resposta", "resposta", 250)
        );

        if (usuario.isAdmin()) {
            TableColumn<Ticket, Void> actionCol = new TableColumn<>("Ações");
            actionCol.setPrefWidth(180);
            actionCol.setResizable(false);
            actionCol.setStyle("-fx-alignment: CENTER;");

            actionCol.setCellFactory(col -> new TableCell<>() {
                private final Button responderBtn = new Button("Responder");
                private final Button excluirBtn = new Button("Excluir");
                private final HBox pane = new HBox(5, responderBtn, excluirBtn);

                {
                    pane.setAlignment(Pos.CENTER);
                    responderBtn.getStyleClass().add("button");
                    excluirBtn.getStyleClass().add("logout-btn");

                    responderBtn.setOnAction(e -> {
                        Ticket selected = getTableView().getItems().get(getIndex());
                        if (selected != null) {
                            showResponderTicketModal(selected, () -> {
                                table.setItems(FXCollections.observableArrayList(DatabaseManager.getAllTickets()));
                                table.refresh();
                            });
                        }
                    });

                    excluirBtn.setOnAction(e -> {
                        Ticket selected = getTableView().getItems().get(getIndex());
                        if (selected != null) {
                            boolean confirm = showConfirmation("Deseja excluir o chamado #" + selected.getId() + "?");
                            if (confirm) {
                                DatabaseManager.excluirTicket(selected);
                                getTableView().getItems().remove(selected);
                                showToast("Chamado removido com sucesso!");
                            }
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(pane);
                    }
                }
            });
            table.getColumns().add(actionCol);
        }

        content.getChildren().addAll(title, table);
        return content;
    }

    private TableColumn<Ticket, String> createColumn(String title, String prop, double prefWidth) {
        TableColumn<Ticket, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        col.setPrefWidth(prefWidth);
        return col;
    }

    private boolean showConfirmation(String msg) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(primaryStage);
        alert.setTitle("Confirmação");
        alert.setHeaderText(null);
        alert.setContentText(msg);

        ButtonType yes = new ButtonType("Sim", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("Não", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(yes, no);

        DialogPane dialogPane = alert.getDialogPane();
        ThemeManager.applyThemeToDialog(dialogPane, configManager.getTheme());

        return alert.showAndWait().orElse(no) == yes;
    }

    private void showMeusChamadosModal() {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.APPLICATION_MODAL);

        StackPane root = new StackPane();
        VBox content = new VBox(15);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(15));
        content.setPrefSize(950, 600);

        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        root.getChildren().add(content);
        root.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Meus Chamados");
        title.getStyleClass().add("olt-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setOnAction(ev -> animateModalClose(stage, content, () -> {
            stage.close();
        }));
        addEnhancedButtonHoverEffects(closeBtn);
        titleBar.getChildren().addAll(title, spacer, closeBtn);

        TableView<Ticket> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setItems(FXCollections.observableArrayList(DatabaseManager.getTicketsByUsuario(usuarioLogado.getNome())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().addAll(
                createColumn("Descrição", "descricao", 300),
                createColumn("Prioridade", "previsao", 100),
                createColumn("Data/Hora", "dataHora", 150),
                createColumn("Status", "status", 110),
                createColumn("Resposta do Desenvolvedor", "resposta", 290)
        );

        Button refreshBtn = new Button("🔄 Atualizar");
        refreshBtn.getStyleClass().add("connect-btn");
        addEnhancedButtonHoverEffects(refreshBtn);
        refreshBtn.setOnAction(e -> {
            table.setItems(FXCollections.observableArrayList(DatabaseManager.getTicketsByUsuario(usuarioLogado.getNome())));
            showLocalToast(root, "✅ Chamados atualizados!");
        });

        HBox bottomBar = new HBox(refreshBtn);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(titleBar, table, bottomBar);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyThemeToNewScene(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        animateModalOpen(stage, content);
    }

    private void showLocalToast(Parent parent, String message) {
        Label toast = new Label(message);
        toast.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 20px;");
        toast.setOpacity(0);

        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(10, 10, 10, 10));

        if (parent instanceof StackPane) {
            ((StackPane) parent).getChildren().add(toast);
        }

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setToValue(1.0);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.millis(2000));

        fadeIn.setOnFinished(ev -> fadeOut.play());
        fadeOut.setOnFinished(ev -> ((StackPane) parent).getChildren().remove(toast));

        fadeIn.play();
    }

    private void showResponderTicketModal(Ticket ticket, Runnable onUpdate) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(15);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(15));
        content.setPrefSize(500, 550);
        content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);


        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Responder Chamado #" + ticket.getId());
        title.getStyleClass().add("olt-name");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setOnAction(ev -> animateModalClose(stage, content, () -> {
            stage.close();
        }));
        addEnhancedButtonHoverEffects(closeBtn);
        titleBar.getChildren().addAll(title, spacer, closeBtn);

        Label descLabel = new Label("Descrição do Problema:");
        descLabel.getStyleClass().add("form-label");
        CodeArea descArea = new CodeArea(ticket.getDescricao());
        descArea.setEditable(false);
        descArea.setWrapText(true);
        descArea.getStyleClass().add("code-area");
        descArea.setPrefHeight(100);

        Label respostaLabel = new Label("Sua Resposta:");
        respostaLabel.getStyleClass().add("form-label");
        CodeArea respostaArea = new CodeArea(ticket.getResposta());
        respostaArea.setWrapText(true);
        respostaArea.getStyleClass().add("code-area");
        respostaArea.setPrefHeight(150);

        Label statusLabel = new Label("Status do Chamado:");
        statusLabel.getStyleClass().add("form-label");
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("Pendente", "Em andamento", "Resolvido", "Fechado");
        statusBox.setValue(ticket.getStatus());
        statusBox.getStyleClass().add("combo-box");
        addComboBoxFocusEffects(statusBox);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button salvarBtn = new Button("Salvar Alterações");
        salvarBtn.getStyleClass().add("connect-btn");
        addEnhancedButtonHoverEffects(salvarBtn);
        salvarBtn.setOnAction(e -> {
            String novaResposta = respostaArea.getText();
            String novoStatus = statusBox.getValue();
            DatabaseManager.updateTicket(ticket.getId(), novaResposta, novoStatus);
            showToast("✅ Chamado #" + ticket.getId() + " atualizado.");
            onUpdate.run();
            animateModalClose(stage, content, () -> {
                stage.close();
            });
        });
        btnRow.getChildren().add(salvarBtn);

        content.getChildren().addAll(titleBar, descLabel, descArea, respostaLabel, respostaArea, statusLabel, statusBox, btnRow);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyThemeToNewScene(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        animateModalOpen(stage, content);
    }
    // ---------------------- Chamados (ADMIN)  ---------------------- //


    // ---------------------- JavaFX Anims & UI ---------------------- //
    private void animateModalOpen(Stage stage, Node content) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(stage.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                        new KeyValue(content.scaleXProperty(), 0.8, Interpolator.EASE_OUT),
                        new KeyValue(content.scaleYProperty(), 0.8, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(250),
                        new KeyValue(stage.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(content.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(content.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                )
        );

        timeline.setOnFinished(e -> content.setCache(false));
        timeline.play();
    }

    private void animateModalClose(Stage stage, Node content, Runnable callback) {
        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(stage.opacityProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(content.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(content.scaleYProperty(), 1.0, Interpolator.EASE_IN)
                ),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(stage.opacityProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(content.scaleXProperty(), 0.8, Interpolator.EASE_IN),
                        new KeyValue(content.scaleYProperty(), 0.8, Interpolator.EASE_IN)
                )
        );

        timeline.setOnFinished(e -> {
            stage.close();
            if (callback != null) callback.run();
        });
        timeline.play();
    }

    private void setupWindowDrag(Node node) {
        node.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        node.setOnMouseDragged(event -> {
            if (primaryStage != null && !primaryStage.isMaximized()) {
                primaryStage.setX(event.getScreenX() - xOffset);
                primaryStage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    private void addEnhancedButtonHoverEffects(Button button) {
        Glow glow = new Glow();
        glow.setLevel(0.0);
        button.setEffect(glow);

        ScaleTransition scaleEnter = new ScaleTransition(Duration.millis(150), button);
        scaleEnter.setToX(1.05);
        scaleEnter.setToY(1.05);

        Timeline glowEnter = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.levelProperty(), 0.0)),
                new KeyFrame(Duration.millis(150), new KeyValue(glow.levelProperty(), 0.3))
        );

        ScaleTransition scaleExit = new ScaleTransition(Duration.millis(150), button);
        scaleExit.setToX(1.0);
        scaleExit.setToY(1.0);

        Timeline glowExit = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.levelProperty(), glow.getLevel())),
                new KeyFrame(Duration.millis(150), new KeyValue(glow.levelProperty(), 0.0))
        );

        button.setOnMouseEntered(e -> {
            scaleEnter.playFromStart();
            glowEnter.playFromStart();
        });

        button.setOnMouseExited(e -> {
            scaleExit.playFromStart();
            glowExit.playFromStart();
        });
    }

    private void addFieldFocusEffects(Control field) {
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), field);
                scaleIn.setFromX(1.0);
                scaleIn.setFromY(1.0);
                scaleIn.setToX(1.02);
                scaleIn.setToY(1.02);
                scaleIn.setInterpolator(Interpolator.EASE_OUT);
                scaleIn.play();

                Glow glow = new Glow(0.3);
                field.setEffect(glow);
            } else {
                ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), field);
                scaleOut.setFromX(1.02);
                scaleOut.setFromY(1.02);
                scaleOut.setToX(1.0);
                scaleOut.setToY(1.0);
                scaleOut.setInterpolator(Interpolator.EASE_OUT);
                scaleOut.play();

                field.setEffect(null);
            }
        });
    }

    private void addComboBoxFocusEffects(ComboBox<?> comboBox) {
        comboBox.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), comboBox);
                scaleIn.setFromX(1.0);
                scaleIn.setFromY(1.0);
                scaleIn.setToX(1.02);
                scaleIn.setToY(1.02);
                scaleIn.setInterpolator(Interpolator.EASE_OUT);
                scaleIn.play();

                Glow glow = new Glow(0.3);
                comboBox.setEffect(glow);
            } else {
                ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), comboBox);
                scaleOut.setFromX(1.02);
                scaleOut.setFromY(1.02);
                scaleOut.setToX(1.0);
                scaleOut.setToY(1.0);
                scaleOut.setInterpolator(Interpolator.EASE_OUT);
                scaleOut.play();

                comboBox.setEffect(null);
            }
        });
    }


    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(5, 10, 5, 15));
        HBox.setHgrow(titleBar, Priority.ALWAYS);

        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximize();
            }
        });

        titleBarIconView = new ImageView();
        titleBarIconView.setFitHeight(20);
        titleBarIconView.setFitWidth(20);
        titleBarIconView.setPreserveRatio(true);
        updateApplicationIcons(this.iconFileName);


        Label titleLabel = new Label("NM OLT App");
        titleLabel.getStyleClass().add("olt-name");
        HBox.setMargin(titleLabel, new Insets(0, 0, 0, 8));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = new Button();
        minimizeBtn.getStyleClass().addAll("window-btn", "minimize-btn");
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));
        minimizeBtn.setTooltip(new Tooltip("Minimizar"));
        addEnhancedButtonHoverEffects(minimizeBtn);

        Button maximizeBtn = new Button();
        maximizeBtn.getStyleClass().addAll("window-btn", "maximize-btn");
        maximizeBtn.setOnAction(e -> toggleMaximize());
        maximizeBtn.setTooltip(new Tooltip("Maximizar/Restaurar"));
        addEnhancedButtonHoverEffects(maximizeBtn);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(10, 12, 10, 12));
        addEnhancedButtonHoverEffects(closeBtn);
        closeBtn.setOnAction(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(200), rootLayout);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(event -> Platform.exit());
            fade.play();
        });
        closeBtn.setTooltip(new Tooltip("Fechar"));

        HBox windowControls = new HBox(5);
        windowControls.setAlignment(Pos.CENTER);
        windowControls.getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn);

        titleBar.getChildren().addAll(titleBarIconView, titleLabel, spacer, windowControls);
        return titleBar;
    }

    private void toggleMaximize() {
        if (primaryStage == null) return;
        primaryStage.setMaximized(!primaryStage.isMaximized());
    }

    private void animateCardsSequentially(ObservableList<Node> nodes, int delayMillis) {
        Timeline timeline = new Timeline();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            node.setOpacity(0);
            node.setTranslateY(20);

            KeyFrame kfShow = new KeyFrame(Duration.millis(i * delayMillis + 300),
                    new KeyValue(node.opacityProperty(), 1, Interpolator.EASE_OUT),
                    new KeyValue(node.translateYProperty(), 0, Interpolator.EASE_OUT)
            );
            timeline.getKeyFrames().add(kfShow);
        }
        timeline.play();
    }

    private void showWaitingToast(boolean show) {
        Platform.runLater(() -> {
            if (primaryStage == null || primaryStage.getScene() == null || primaryStage.getScene().getRoot() == null) return;
            StackPane root = (StackPane) primaryStage.getScene().getRoot();

            if (show) {
                if (currentWaitingToast != null) return;

                currentWaitingToast = new Label("⏳ Aguarde a consulta atual ser concluída...");
                currentWaitingToast.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 20px;");
                currentWaitingToast.setOpacity(0);
                root.getChildren().add(currentWaitingToast);
                StackPane.setAlignment(currentWaitingToast, Pos.BOTTOM_CENTER);
                StackPane.setMargin(currentWaitingToast, new Insets(0, 0, 95, 180));

                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), currentWaitingToast);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);

                TranslateTransition shake = new TranslateTransition(Duration.millis(50), currentWaitingToast);
                shake.setFromX(0);
                shake.setToX(2);
                shake.setCycleCount(6);
                shake.setAutoReverse(true);

                fadeIn.setOnFinished(e -> shake.play());
                fadeIn.play();

            } else {
                if (currentWaitingToast != null) {
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(300), currentWaitingToast);
                    fadeOut.setFromValue(1);
                    fadeOut.setToValue(0);
                    Label toastToRemove = currentWaitingToast;
                    fadeOut.setOnFinished(e -> root.getChildren().remove(toastToRemove));
                    fadeOut.play();
                    currentWaitingToast = null;
                }
            }
        });
    }

    private void showTrayNotification(String title, String message, TrayIcon.MessageType messageType) {
        if (trayIcon != null && SystemTray.isSupported()) {

            boolean iconInTray = false;
            for(TrayIcon ti : SystemTray.getSystemTray().getTrayIcons()){
                if(ti == trayIcon){
                    iconInTray = true;
                    break;
                }
            }
            if(iconInTray){
                trayIcon.displayMessage(title, message, messageType);
            } else {
                System.out.println("Tray Notification (icon not in tray): " + title + " - " + message);
            }

        } else {
            System.out.println("Tray Notification (not supported or icon null): " + title + " - " + message);
        }
    }
    private void showToast(String message) {
        if (primaryStage != null && primaryStage.isIconified()) {
            String title = "Gerenciador OLTs";
            TrayIcon.MessageType type = TrayIcon.MessageType.INFO;

            if (message.contains("✅")) title = "Sucesso";

            else if (message.contains("❌") || message.contains("⚠️")) {
                title = "Aviso/Erro";
                type = message.contains("❌") ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.WARNING;

            } else if (message.contains("🔎")) title = "Consulta";

            else if (message.contains("🔄")) title = "Atualização";

            showTrayNotification(title, message, type);

            return;
        }

        Platform.runLater(() -> {
            if (primaryStage == null || primaryStage.getScene() == null || primaryStage.getScene().getRoot() == null) return;
            StackPane root = (StackPane) primaryStage.getScene().getRoot();
            if (root == null) return;

            Label toastLabel = new Label(message);
            toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 20px;");
            toastLabel.setOpacity(0);

            root.getChildren().add(toastLabel);
            StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
            StackPane.setMargin(toastLabel, new Insets(0, 0, 95, 180));

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toastLabel);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);
            PauseTransition stay = new PauseTransition(Duration.seconds(2.5));
            FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toastLabel);
            fadeOut.setFromValue(1); fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> root.getChildren().remove(toastLabel));
            new SequentialTransition(fadeIn, stay, fadeOut).play();
        });
    }

    private void appendStyledTextWithIPHighlight(CodeArea codeArea, String text, String ipToHighlight, String styleClass) {
        if (codeArea == null) {
            System.err.println("Erro: CodeArea não inicializado para estilização de IP.");
            return;
        }

        Platform.runLater(() -> {
            int currentLength = codeArea.getLength();
            codeArea.appendText(text);

            if (ipToHighlight != null && !ipToHighlight.isEmpty()) {
                Pattern ipPattern = Pattern.compile("\\b" + Pattern.quote(ipToHighlight) + "\\b");
                Matcher matcher = ipPattern.matcher(text);
                if (matcher.find()) {
                    int start = currentLength + matcher.start();
                    int end = currentLength + matcher.end();
                    codeArea.setStyle(start, end, Collections.singleton(styleClass));
                }
            }
        });
    }
    // ---------------------- JavaFX Anims & UI ---------------------- //


    // ---------------------- Inside - Terminal ---------------------- //
    private void showSSHTerminal(OLT olt) {
        if (terminalTabs == null) {
            terminalTabs = new TabPane();
            terminalTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            Tab oltsListTab = new Tab("Lista de OLTs");
            oltsListTab.setContent(createOLTScreen());
            oltsListTab.setClosable(false);
            terminalTabs.getTabs().add(oltsListTab);
        }

        for (Tab existingTab : terminalTabs.getTabs()) {
            if (olt.name.equals(existingTab.getText())) {
                terminalTabs.getSelectionModel().select(existingTab);
                animateContentSwitch(terminalTabs);
                if (existingTab.getContent() instanceof VBox) {
                    VBox termContent = (VBox) existingTab.getContent();
                    Node commandAreaNode = termContent.getChildren().stream()
                            .filter(n -> n instanceof VBox && ((VBox) n).getChildren().stream()
                                    .anyMatch(childNode -> childNode instanceof HBox && ((HBox) childNode).getChildren().stream()
                                            .anyMatch(grandChild -> grandChild instanceof TextField)))
                            .findFirst().orElse(null);
                    if (commandAreaNode instanceof VBox) {
                        HBox commandInputBox = (HBox) ((VBox) commandAreaNode).getChildren().stream().filter(n -> n instanceof HBox).reduce((first, second) -> second).orElse(null);
                        if(commandInputBox != null){
                            TextField cmdField = (TextField) commandInputBox.getChildren().stream().filter(n -> n instanceof TextField).findFirst().orElse(null);
                            if(cmdField != null) Platform.runLater(cmdField::requestFocus);
                        }
                    }
                }
                return;
            }
        }

        VBox content = new VBox(10);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(15));
        VBox.setVgrow(content, Priority.ALWAYS);

        CodeArea newTerminalArea = new CodeArea();
        newTerminalArea.getStyleClass().add("terminal-area");
        newTerminalArea.setEditable(false);
        newTerminalArea.setWrapText(true);
        VBox.setVgrow(newTerminalArea, Priority.ALWAYS);

        TextField commandField = new TextField();
        commandField.setPromptText("Digite um comando...");
        commandField.getStyleClass().add("command-field");
        HBox.setHgrow(commandField, Priority.ALWAYS);

        Button sendBtn = new Button("Enviar");
        sendBtn.getStyleClass().add("send-btn");
        addEnhancedButtonHoverEffects(sendBtn);

        HBox commandInputBox = new HBox(5, new Label(">"), commandField, sendBtn);
        commandInputBox.setAlignment(Pos.CENTER_LEFT);
        commandInputBox.setPadding(new Insets(5, 0, 0, 0));

        Button clearBtn = new Button("Limpar");
        clearBtn.getStyleClass().add("action-btn");
        addEnhancedButtonHoverEffects(clearBtn);
        Button helpBtn = new Button("Ajuda");
        helpBtn.getStyleClass().add("action-btn");
        addEnhancedButtonHoverEffects(helpBtn);
        HBox quickActions = new HBox(10, clearBtn, helpBtn);
        quickActions.setAlignment(Pos.CENTER_LEFT);
        quickActions.setPadding(new Insets(5,0,0,0));

        VBox terminalBox = new VBox(5, newTerminalArea, commandInputBox, quickActions);
        VBox.setVgrow(terminalBox, Priority.ALWAYS);
        content.getChildren().add(terminalBox);

        Tab terminalTab = new Tab(olt.name.replace("_", " "));
        terminalTab.setContent(content);
        terminalTab.setClosable(true);
        makeTabDraggable(terminalTab);

        SSHManager newSSHManager = new SSHManager();
        terminalConnections.put(terminalTab, newSSHManager);

        Runnable onSshDisconnect = () -> {
            if (terminalTabs.getTabs().contains(terminalTab)) {
                showToast("🔌 Conexão com " + olt.name + " foi encerrada.");

                Node tabContent = terminalTab.getContent();
                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), tabContent);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(ev -> {
                    terminalConnections.remove(terminalTab);
                    terminalTabs.getTabs().remove(terminalTab);
                    if (terminalTabs.getTabs().size() == 1 && terminalTabs.getTabs().get(0).getText().equals("Lista de OLTs")) {
                        terminalTabs.getSelectionModel().select(0);
                    }
                });
                fadeOut.play();
            }
        };

        newSSHManager.setOnDisconnectCallback(onSshDisconnect);

        terminalTab.setOnCloseRequest(e -> {
            e.consume();

            Node tabContent = terminalTab.getContent();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), tabContent);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeOut.setOnFinished(ev -> {
                terminalConnections.remove(terminalTab);
                terminalTabs.getTabs().remove(terminalTab);

                if (terminalTabs.getTabs().size() == 1 && terminalTabs.getTabs().get(0).getText().equals("Lista de OLTs")) {
                    terminalTabs.getSelectionModel().select(0);
                }
            });

            SSHManager sshToDisconnect = terminalConnections.get(terminalTab);
            if (sshToDisconnect != null) {
                new Thread(sshToDisconnect::disconnect).start();
            }

            fadeOut.play();
        });

        terminalTabs.getTabs().add(terminalTab);
        terminalTabs.getSelectionModel().select(terminalTab);
        animateContentSwitch(terminalTabs);
        Platform.runLater(commandField::requestFocus);

        currentSection = olt.name;

        Thread connectThread = new Thread(() -> {
            try {
                newSSHManager.connect(olt.getIp(), olt.getUser(), olt.getPassword(), newTerminalArea, true);
                Platform.runLater(commandField::requestFocus);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (newTerminalArea != null) {
                        newTerminalArea.appendText("\n❌ Erro ao conectar ao terminal: " + ex.getMessage() + "\n");
                    }
                    commandField.requestFocus();
                });
            }
        });

        connectThread.setDaemon(true);
        connectThread.start();

        List<String> commandHistory = new ArrayList<>();
        final int[] commandHistoryIndex = {-1};

        List<String> huaweiOltCommands = HuaweiOltCommands.getAllCommands();

        sendBtn.setOnAction(ev -> {
            String cmd = commandField.getText().trim();
            if (!cmd.isEmpty()) {
                newSSHManager.sendCommand(cmd);
                DatabaseManager.logUsuario(usuario.getNome(), "Executou comando no terminal: " + cmd);
                if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(cmd)) {
                    commandHistory.add(cmd);
                }
                commandHistoryIndex[0] = commandHistory.size();
            } else if (newSSHManager.isWaitingForMorePromptActive()) {
                newSSHManager.sendRawInput(" ");
            }
            commandField.clear();
            newTerminalArea.requestFollowCaret();
            commandField.requestFocus();
        });

        commandField.setOnKeyPressed(ev -> {

            if (ev.getCode() == KeyCode.BACK_SPACE) {
                String selectedText = commandField.getSelectedText();
                if (selectedText != null && !selectedText.isEmpty()) {

                    IndexRange selectionRange = commandField.getSelection();
                    if (selectionRange.getLength() > 0) {
                        if (selectionRange.getStart() == 0 && selectionRange.getEnd() == commandField.getText().length()) {
                            commandField.clear();
                        } else {
                            commandField.deleteText(selectionRange);
                        }
                        ev.consume();
                        return;
                    }
                }
            }

            if (ev.getCode() == KeyCode.DELETE) {
                String selectedText = commandField.getSelectedText();
                if (selectedText != null && !selectedText.isEmpty()) {
                    IndexRange selectionRange = commandField.getSelection();
                    if (selectionRange.getLength() > 0) {
                        if (selectionRange.getStart() == 0 && selectionRange.getEnd() == commandField.getText().length()) {
                            commandField.clear();
                        } else {
                            commandField.deleteText(selectionRange);
                        }
                        ev.consume();
                        return;
                    }
                }
            }


            switch (ev.getCode()) {
                case ENTER:
                    if (commandField.getText().isEmpty()) {
                        if (newSSHManager.isWaitingForMorePromptActive()) {
                            newSSHManager.sendRawInput("\r");
                        } else {
                            String terminalText = newTerminalArea.getText();
                            if (terminalText.toLowerCase().contains("more")) {
                                newSSHManager.forceAdvancePagination();
                            } else {
                                newSSHManager.sendCommand("");
                            }
                        }
                    } else {
                        sendBtn.fire();
                    }
                    ev.consume();
                    break;
                case SPACE:
                    if (commandField.getText().isEmpty()) {
                        if (newSSHManager.isWaitingForMorePromptActive()) {
                            newSSHManager.sendRawInput(" ");
                        } else {
                            String terminalText = newTerminalArea.getText();
                            if (terminalText.toLowerCase().contains("more")) {
                                newSSHManager.forceAdvancePagination();
                            }
                        }
                        commandField.clear();
                        ev.consume();
                    }
                    break;
                case Q:
                    if (commandField.getText().isEmpty() && newSSHManager.isWaitingForMorePromptActive()) {
                        newSSHManager.sendRawInput("q");
                        ev.consume();
                    }
                    break;
                case UP:
                    if (!commandHistory.isEmpty()) {
                        if (commandHistoryIndex[0] > 0) commandHistoryIndex[0]--;
                        else commandHistoryIndex[0] = 0;

                        if(commandHistoryIndex[0] < commandHistory.size()){
                            commandField.setText(commandHistory.get(commandHistoryIndex[0]));
                            commandField.positionCaret(commandField.getText().length());
                        }
                    }
                    ev.consume(); break;
                case DOWN:
                    if (!commandHistory.isEmpty()) {
                        if (commandHistoryIndex[0] < commandHistory.size() - 1) {
                            commandHistoryIndex[0]++;
                            commandField.setText(commandHistory.get(commandHistoryIndex[0]));
                            commandField.positionCaret(commandField.getText().length());
                        } else if (commandHistoryIndex[0] == commandHistory.size() -1 ) {
                            commandHistoryIndex[0] = commandHistory.size();
                            commandField.clear();
                        }
                    }
                    ev.consume(); break;
                case TAB:
                    ev.consume();
                    String currentInputForTab = commandField.getText();

                    boolean endsWithSpace = currentInputForTab.matches(".*\\s$");
                    String[] words = currentInputForTab.trim().split("\\s+");

                    if (currentInputForTab.trim().isEmpty()) {
                        words = new String[]{};
                    }

                    String currentPrefix;
                    String partialWord;

                    if (endsWithSpace || currentInputForTab.isEmpty()) {
                        currentPrefix = currentInputForTab.trim();
                        partialWord = "";
                    } else {
                        if (words.length == 0) {
                            currentPrefix = "";
                            partialWord = currentInputForTab;
                        } else if (words.length == 1) {
                            currentPrefix = "";
                            partialWord = words[0];
                        } else {
                            partialWord = words[words.length - 1];
                            currentPrefix = String.join(" ", Arrays.copyOfRange(words, 0, words.length - 1));
                        }
                    }

                    List<String> finalSuggestions = new ArrayList<>();
                    String searchBase = currentPrefix.isEmpty() ? "" : currentPrefix + " ";

                    for (String cmd : huaweiOltCommands) {
                        if (cmd.toLowerCase().startsWith(searchBase.toLowerCase())) {
                            String remainder = cmd.substring(searchBase.length());
                            if (!remainder.isEmpty()) {
                                String nextToken = remainder.split("\\s+")[0];
                                if (nextToken.toLowerCase().startsWith(partialWord.toLowerCase())) {
                                    if (!finalSuggestions.contains(nextToken)) {
                                        finalSuggestions.add(nextToken);
                                    }
                                }
                            }
                        }
                    }
                    Collections.sort(finalSuggestions);

                    if (finalSuggestions.size() == 1) {
                        String completion = finalSuggestions.get(0);
                        final String newText = searchBase + completion + " ";
                        commandField.setText(newText);
                        Platform.runLater(() -> {
                            commandField.requestFocus();
                            commandField.positionCaret(commandField.getText().length());
                        });

                    } else if (finalSuggestions.size() > 1) {
                        String common = findCommonPrefix(finalSuggestions);
                        if (common != null && !common.isEmpty() && !common.equals(partialWord)) {
                            final String newText = searchBase + common;
                            commandField.setText(newText);
                            Platform.runLater(() -> {
                                commandField.requestFocus();
                                commandField.positionCaret(commandField.getText().length());
                            });

                        } else {
                            newTerminalArea.appendText("\n");
                            finalSuggestions.forEach(s -> newTerminalArea.appendText(s + "  "));
                            newTerminalArea.appendText("\n" + olt.name + getPromptSuffix(newTerminalArea.getText()) + currentInputForTab);
                            newTerminalArea.requestFollowCaret();
                            commandField.requestFocus();
                        }
                    }
                    break;
                default:
                    break;
            }
        });

        clearBtn.setOnAction(e -> { newTerminalArea.clear(); commandField.requestFocus(); });
        helpBtn.setOnAction(e -> showHelpDialog());
    }

    private void makeTabDraggable(Tab tab) {
        Label tabLabel = new Label(tab.getText());
        tabLabel.getStyleClass().add("tab-label");

        tabLabel.setOnDragDetected(event -> {
            Dragboard db = tabLabel.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();

            content.putString(tabLabel.getText());

            db.setContent(content);
            draggingTab = tab;
            event.consume();
        });

        tabLabel.setOnDragOver(event -> {
            if (event.getGestureSource() != tabLabel && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        tabLabel.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                TabPane tabPane = tab.getTabPane();
                int targetIndex = tabPane.getTabs().indexOf(tab);

                tabPane.getTabs().remove(draggingTab);

                if (targetIndex == 0) {
                    targetIndex = 1;
                }

                tabPane.getTabs().add(targetIndex, draggingTab);

                tabPane.getSelectionModel().select(draggingTab);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        tabLabel.setOnDragDone(event -> {
            draggingTab = null;
        });

        tab.setText(null);
        tab.setGraphic(tabLabel);
    }

    private void standardizeTabText(Tab tab) {
        Label tabLabel = new Label(tab.getText());
        tabLabel.getStyleClass().add("tab-label");
        tab.setText(null);
        tab.setGraphic(tabLabel);
    }

    private String getPromptSuffix(String terminalContent) {
        if (terminalContent.contains("(config-if-gpon-")) return "(config-if-gpon)#";
        if (terminalContent.contains("(config-")) return "(config)#";
        if (terminalContent.contains("#")) return "#";
        return ">";
    }


    private String findCommonPrefix(List<String> strings) {
        if (strings == null || strings.isEmpty()) return "";
        String first = strings.get(0);
        for (int i = 0; i < first.length(); i++) {
            char c = first.charAt(i);
            for (int j = 1; j < strings.size(); j++) {
                if (i >= strings.get(j).length() || strings.get(j).charAt(i) != c) {
                    return first.substring(0, i);
                }
            }
        }
        return first;
    }

    public void showHelpDialog() {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        if (primaryStage != null) {
            stage.initOwner(primaryStage);
        }
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox helpContent = new VBox(15);
        helpContent.getStyleClass().add("help-content");
        helpContent.setPadding(new Insets(20));
        helpContent.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        helpContent.setCache(true);
        helpContent.setCacheHint(CacheHint.SPEED);

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 0, 10, 0));

        Label title = new Label("Ajuda - Comandos Comuns");
        title.getStyleClass().add("title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(8, 10, 8, 10));
        closeBtn.setOnAction(ev -> animateModalClose(stage, helpContent, () -> {
            stage.close();
        }));
        addEnhancedButtonHoverEffects(closeBtn);

        titleBar.getChildren().addAll(title, spacer, closeBtn);

        VBox commandsBox = new VBox(8);
        commandsBox.getStyleClass().add("commands-box");
        commandsBox.setPadding(new Insets(10));

        Label basicLabel = new Label("Comandos Gerais:");
        basicLabel.getStyleClass().add("help-section");
        VBox basicCommands = new VBox(5);
        basicCommands.getChildren().addAll(
                new Label("• enable - Entra no modo privilegiado"),
                new Label("• config - Entra no modo de configuração global"),
                new Label("• display ont info by-sn <SN> - Info da ONT por Serial Number"),
                new Label("• display ont wan-info <F/S> <P> <ID> - Info WAN da ONT"),
                new Label("• display ont info summary <F/S/P> - Resumo das ONTs na PON"),
                new Label("• display port desc <F/S/P> - Descrição da porta PON"),
                new Label("• display service-port port <F/S/P> ont <ID> - Serviços configurados na ONT"),
                new Label("• display ont autofind all - Lista ONTs boiando"),
                new Label("• quit - Sai do modo atual / Desconecta")
        );

        Label gponLabel = new Label("Comandos (após 'interface gpon <F/S>'):");
        gponLabel.getStyleClass().add("help-section");
        VBox gponCommands = new VBox(5);
        gponCommands.getChildren().addAll(
                new Label("• display ont register-info <P> <ID> - Histórico de registro/quedas"),
                new Label("• display ont optical-info <P> all - Sinais ópticos das ONTs na porta P"),
                new Label("• display ont traffic <P> <ID> - Tráfego da ONT (tempo real)")
        );

        Region sectionSpacer = new Region();
        sectionSpacer.setPrefHeight(10);

        commandsBox.getChildren().addAll(basicLabel, basicCommands, sectionSpacer, gponLabel, gponCommands);

        helpContent.getChildren().addAll(titleBar, commandsBox);

        Scene helpScene = new Scene(helpContent);
        helpScene.setFill(Color.TRANSPARENT);
        ThemeManager.applyThemeToNewScene(helpScene);

        stage.setScene(helpScene);

        stage.setOpacity(0);
        helpContent.setScaleX(0.9);
        helpContent.setScaleY(0.9);

        stage.show();
        stage.centerOnScreen();

        animateModalOpen(stage, helpContent);
    }
    // ---------------------- Inside - Terminal ---------------------- //


    // ---------------------- Exports, Tratamento & Creditos ---------------------- //
    private void exportarResultado(CodeArea resultadoArea, String nomeBase) {
        String resultado = resultadoArea.getText();
        if (resultado == null || resultado.trim().isEmpty()) {
            showToast("❌ Nada para exportar. Faça uma consulta primeiro.");
            return;
        }

        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(15);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(15));
        content.setPrefSize(450, 350);
        content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        HBox exportTitleBar = new HBox();
        exportTitleBar.setAlignment(Pos.CENTER_LEFT);
        exportTitleBar.setPadding(new Insets(5, 10, 5, 15));

        Label title = new Label("Exportar Resultado");
        title.getStyleClass().add("olt-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(12, 12, 12, 12));
        closeBtn.setOnAction(ev -> animateModalClose(stage, content, () -> {
            stage.close();
        }));
        addEnhancedButtonHoverEffects(closeBtn);

        exportTitleBar.getChildren().addAll(title, spacer, closeBtn);

        Label infoLabel = new Label("O arquivo será salvo na pasta 'exports'.\nEscolha o formato de exportação:");
        infoLabel.getStyleClass().add("info-label");

        Label formatoLabel = new Label("Formato:");
        formatoLabel.getStyleClass().add("form-label");

        VBox formatosContainer = new VBox(10);

        HBox primeiraLinha = new HBox(10);
        primeiraLinha.setAlignment(Pos.CENTER);

        Button pdfBtn = new Button("📄 PDF");
        pdfBtn.getStyleClass().add("floating-btn");
        pdfBtn.setPrefWidth(100);
        addEnhancedButtonHoverEffects(pdfBtn);

        Button xlsxBtn = new Button("📊 XLSX");
        xlsxBtn.getStyleClass().add("floating-btn");
        xlsxBtn.setPrefWidth(100);
        addEnhancedButtonHoverEffects(xlsxBtn);

        primeiraLinha.getChildren().addAll(pdfBtn, xlsxBtn);

        HBox segundaLinha = new HBox(10);
        segundaLinha.setAlignment(Pos.CENTER);

        Button csvBtn = new Button("📋 CSV");
        csvBtn.getStyleClass().add("floating-btn");
        csvBtn.setPrefWidth(100);
        addEnhancedButtonHoverEffects(csvBtn);

        Button txtBtn = new Button("📝 TXT");
        txtBtn.getStyleClass().add("floating-btn");
        txtBtn.setPrefWidth(100);
        addEnhancedButtonHoverEffects(txtBtn);

        segundaLinha.getChildren().addAll(csvBtn, txtBtn);

        formatosContainer.getChildren().addAll(primeiraLinha, segundaLinha);

        content.getChildren().addAll(exportTitleBar, infoLabel, formatoLabel, formatosContainer);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().clear();
        ThemeManager.applyThemeToNewScene(scene);

        stage.setScene(scene);

        stage.setOpacity(0);
        content.setScaleX(0.9);
        content.setScaleY(0.9);

        stage.centerOnScreen();
        stage.show();

        animateModalOpen(stage, content);

        pdfBtn.setOnAction(e -> processarExportacao("PDF", resultado, nomeBase, stage, content));
        xlsxBtn.setOnAction(e -> processarExportacao("XLSX", resultado, nomeBase, stage, content));
        csvBtn.setOnAction(e -> processarExportacao("CSV", resultado, nomeBase, stage, content));
        txtBtn.setOnAction(e -> processarExportacao("TXT", resultado, nomeBase, stage, content));
    }

    private void processarExportacao(String formato, String resultado, String nomeBase, Stage stage, VBox content) {
        File dir = new File("exports");
        if (!dir.exists()) dir.mkdirs();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String nomeArquivoBase = "exports/" + nomeBase + "_" + timestamp;

        try {
            switch (formato) {
                case "PDF":
                    try (FileOutputStream fos = new FileOutputStream(nomeArquivoBase + ".pdf")) {
                        com.lowagie.text.Document document = new com.lowagie.text.Document();
                        com.lowagie.text.pdf.PdfWriter.getInstance(document, fos);
                        document.open();
                        com.lowagie.text.Font monoFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.COURIER, 10);
                        document.add(new com.lowagie.text.Paragraph(resultado, monoFont));
                        document.close();
                        animateModalExportClose(stage, content, "📂 Arquivo PDF exportado: " + nomeArquivoBase + ".pdf");
                    }
                    break;
                case "XLSX":
                    try (Workbook workbook = new XSSFWorkbook();
                         FileOutputStream fos = new FileOutputStream(nomeArquivoBase + ".xlsx")) {

                        Sheet sheet = workbook.createSheet(nomeBase.length() > 30 ? nomeBase.substring(0,30) : nomeBase);

                        String[] lines = resultado.split("\\r?\\n");
                        Pattern tableLikePattern = Pattern.compile("(\\S+(\\s+\\S+)*?)(\\s{2,}|\\t)");

                        for (int i = 0; i < lines.length; i++) {
                            Row row = sheet.createRow(i);
                            String line = lines[i];

                            Matcher matcher = tableLikePattern.matcher(line);
                            List<String> cells = new ArrayList<>();
                            int lastEnd = 0;
                            while (matcher.find()) {
                                cells.add(matcher.group(1).trim());
                                lastEnd = matcher.end();
                            }

                            if (lastEnd < line.length()) {
                                cells.add(line.substring(lastEnd).trim());
                            }

                            if (cells.isEmpty() && !line.trim().isEmpty()){
                                cells.add(line.trim());
                            } else if (cells.stream().allMatch(String::isEmpty) && !line.trim().isEmpty()) {
                                cells.clear();
                                cells.add(line.trim());
                            }

                            if (cells.size() == 1 && cells.get(0).isEmpty() && line.trim().isEmpty()) {
                            } else if (cells.size() == 1 && !cells.get(0).isEmpty()){
                                row.createCell(0).setCellValue(cells.get(0));
                            }
                            else {
                                for (int j = 0; j < cells.size(); j++) {
                                    if(!cells.get(j).isEmpty()){
                                        row.createCell(j).setCellValue(cells.get(j));
                                    }
                                }
                            }
                        }
                        if (sheet.getRow(0) != null) {
                            int firstRowCellCount = sheet.getRow(0).getPhysicalNumberOfCells();
                            for (int k = 0; k < firstRowCellCount; k++) {
                                sheet.autoSizeColumn(k);
                            }
                        }

                        workbook.write(fos);
                        animateModalExportClose(stage, content, "📂 Arquivo XLSX exportado: " + nomeArquivoBase + ".xlsx");
                    }
                    break;
                case "CSV":
                    try (FileWriter writer = new FileWriter(nomeArquivoBase + ".csv")) {
                        String csvData = resultado.replaceAll("\\s{2,}", ",").replaceAll("\\s+(?=[^-\n])", ",");
                        writer.write(csvData);
                        animateModalExportClose(stage, content, "📂 Arquivo CSV exportado: " + nomeArquivoBase + ".csv");
                    }
                    break;

                case "TXT":
                    try (FileWriter writer = new FileWriter(nomeArquivoBase + ".txt")) {
                        writer.write(resultado);
                        animateModalExportClose(stage, content, "📂 Arquivo TXT exportado: " + nomeArquivoBase + ".txt");
                    }
                    break;
            }
        } catch (IOException ex) {
            showToast("❌ Erro ao exportar arquivo: " + ex.getMessage());
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), content);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), content);
            scaleOut.setFromX(1.0);
            scaleOut.setFromY(1.0);
            scaleOut.setToX(0.9);
            scaleOut.setToY(0.9);

            ParallelTransition parallelOut = new ParallelTransition(fadeOut, scaleOut);
            parallelOut.setOnFinished(e -> animateModalClose(stage, content, () -> {
                stage.close();
            }));
            parallelOut.play();
        }
    }

    private void animateModalExportClose(Stage stage, VBox content, String mensagemSucesso) {
        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(stage.opacityProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(content.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                        new KeyValue(content.scaleYProperty(), 1.0, Interpolator.EASE_IN)
                ),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(stage.opacityProperty(), 0.0, Interpolator.EASE_IN),
                        new KeyValue(content.scaleXProperty(), 0.8, Interpolator.EASE_IN),
                        new KeyValue(content.scaleYProperty(), 0.8, Interpolator.EASE_IN)
                )
        );
        timeline.play();
        showToast(mensagemSucesso);
    }

    private void destacarIPs(CodeArea codeArea) {
        String texto = codeArea.getText();
        codeArea.setStyleSpans(0, computeHighlighting(texto));
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton("ip-address"), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void showCreditsSection() {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(20);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(25));
        content.setPrefSize(500, 400);
        content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        content.setCache(true);
        content.setCacheHint(CacheHint.SPEED);

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(10, 12, 10, 12));
        closeBtn.setOnAction(ev -> animateModalClose(stage, content, () -> {
            stage.close();
        }));
        addEnhancedButtonHoverEffects(closeBtn);
        titleBar.getChildren().add(closeBtn);

        VBox creditsContent = new VBox(15);
        creditsContent.setAlignment(Pos.TOP_LEFT);
        creditsContent.setPadding(new Insets(0, 10, 10, 10));

        Label appName = new Label("NM OLT App - v1.6.0.0");
        appName.getStyleClass().add("credits-title");

        Label developer = new Label("Desenvolvido por Eduardo Tomaz");
        developer.getStyleClass().add("credits-text");

        VBox socialLinks = new VBox(5);
        socialLinks.setAlignment(Pos.TOP_LEFT);
        Hyperlink linkedInLink = new Hyperlink("LinkedIn: /in/eduardotoomazs");
        linkedInLink.getStyleClass().add("credits-link");
        linkedInLink.setOnAction(e -> openWebpage("https://www.linkedin.com/in/eduardotoomazs/"));
        Hyperlink instagramLink = new Hyperlink("Instagram: @tomazdudux");
        instagramLink.getStyleClass().add("credits-link");
        instagramLink.setOnAction(e -> openWebpage("https://www.instagram.com/tomazdudux/"));
        socialLinks.getChildren().addAll(linkedInLink, instagramLink);

        VBox githubLinks = new VBox(5);
        githubLinks.setAlignment(Pos.TOP_LEFT);
        Hyperlink githubWinLink = new Hyperlink("GitHub (Windows): toomazs/NM-OLT-App");
        githubWinLink.getStyleClass().add("credits-link");
        githubWinLink.setOnAction(e -> openWebpage("https://github.com/toomazs/NM-OLT-App"));
        Hyperlink githubLinuxLink = new Hyperlink("GitHub (Linux): toomazs/NM-OLT-App-Linux");
        githubLinuxLink.getStyleClass().add("credits-link");
        githubLinuxLink.setOnAction(e -> openWebpage("https://github.com/toomazs/NM-OLT-App-Linux"));
        githubLinks.getChildren().addAll(githubWinLink, githubLinuxLink);

        creditsContent.getChildren().addAll(appName, developer, new Region(){{setPrefHeight(10);}}, socialLinks, new Region(){{setPrefHeight(10);}}, githubLinks);
        content.getChildren().addAll(titleBar, creditsContent);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.applyThemeToNewScene(scene);

        stage.setScene(scene);
        stage.setOpacity(0);
        content.setScaleX(0.8);
        content.setScaleY(0.8);

        stage.show();
        stage.centerOnScreen();

        animateModalOpen(stage, content);
    }

    private void openWebpage(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            showToast("❌ Não foi possível abrir o link.");
        }
    }
    // ---------------------- Exports, Tratamento & Creditos ---------------------- //


    // ---------------------- Lifecycle ---------------------- //
    public static void main(String[] args) {
        optimizeJavaFXFor60FPS();
        launch(args);
    }

    @Override
    public void stop() {
        if (currentWaitingToast != null && primaryStage != null && primaryStage.getScene() != null && primaryStage.getScene().getRoot() instanceof StackPane) {
            StackPane root = (StackPane) primaryStage.getScene().getRoot();
            root.getChildren().remove(currentWaitingToast);
            currentWaitingToast = null;
        }

        if (terminalConnections != null && !terminalConnections.isEmpty()) {
            terminalConnections.values().forEach(sshManager -> {
                if (sshManager != null) sshManager.disconnect();
            });
            terminalConnections.clear();
        }

        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }
    // ---------------------- Lifecycle ---------------------- //

}