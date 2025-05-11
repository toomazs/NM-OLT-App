import java.io.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
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
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.control.Button;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.FadeTransition;
import models.Ticket;
import models.Usuario;
import models.Mensagem;
import database.DatabaseManager;
import screens.LoginScreen;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import utils.ConfigManager;
import java.awt.Desktop;
import java.net.URI;
import utils.ThemeManager;
import javafx.scene.shape.Circle;
import java.sql.Connection;
import javafx.stage.FileChooser;
import java.nio.file.Files;
import javafx.scene.Cursor;
import java.util.Locale;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.sql.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.Graphics2D;
import java.awt.MediaTracker;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.Optional;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import utils.WindowsUtils;
import java.util.Set;
import java.util.HashSet;


public class Main extends Application {
    private static final String APP_ID = "YourCompanyName.GerenciadorOLTs";
    private double xOffset = 0;
    private double yOffset = 0;
    private Usuario usuario;
    private Usuario usuarioLogado;
    private VBox rootLayout;
    private Stage primaryStage;
    private static CodeArea terminalArea;
    private SSHManager sshManager;
    private ScheduledExecutorService breakageMonitor;
    private BorderPane mainContent;
    private ToggleGroup navGroup;
    private String currentSection = null;
    private Map<String, Node> contentCache = new HashMap<>();
    private boolean isConnectedToOLT = false;
    private OLT connectedOLT;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ConfigManager configManager = ConfigManager.getInstance();
    private TabPane terminalTabs;
    private Map<Tab, SSHManager> terminalConnections = new HashMap<>();
    private ImageView titleBarIconView;
    private Node statusSidebar;
    private ScrollPane userProfileDrawer;
    private VBox conversationsScreenRoot = null;
    private final Map<String, Node> userNodeMap = new HashMap<>();
    private final Map<String, String> userStatusMap = new HashMap<>();
    private final Map<String, String> userLocationMap = new HashMap<>();
    private VBox onlineUserBox;
    private VBox offlineUserBox;
    private TitledPane onlinePane;
    private TitledPane offlinePane;
    private TrayIcon trayIcon;
    private ImageView profileAvatarView;
    private String iconFileName;
    private PrintStream fileLogger;
    private static final String DEBUG_LOG_FILE = "debug.log";
    private static final String LOG_DIRECTORY = "logs";
    private final int MESSAGES_PER_PAGE = 50;
    private int currentMessageOffset = 0;
    private int totalMessages = 0;
    private boolean isLoadingMessages = false;
    private Set<Integer> notifiedMessageIds = new HashSet<>(); // Adicionado para rastrear mensagens notificadas
    private Usuario currentChatPartner = null;
    private ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread t = Executors.defaultThreadFactory().newThread(runnable);
        t.setDaemon(true);
        return t;
    });




    // ---------------------- START ---------------------- //
    @Override
    public void start(Stage primaryStage) {

        WindowsUtils.setAppUserModelId(APP_ID);

        setupFileLogging();

        if (configManager != null) {
            configManager.setLogger(fileLogger);
            fileLogger.println("DEBUG (Main): Logger passado para ConfigManager.");
        } else {
            fileLogger.println("ERRO (Main): configManager é nulo ao tentar setar o logger.");
        }

        try {
            if (this.usuario == null) {
                LoginScreen loginScreen = new LoginScreen();
                this.usuario = loginScreen.showLogin(new Stage());
                if (this.usuario == null) {
                    System.out.println("Login cancelled or failed. Exiting.");
                    Platform.exit();
                    return;
                }
            }

            this.usuarioLogado = this.usuario;

            String usernameToSave = this.usuarioLogado.getUsuario();
            fileLogger.println("DEBUG: Tentando salvar o usuário: '" + usernameToSave + "'");
            configManager.setLastUser(usernameToSave);

            String initialTheme = configManager.getTheme();
            fileLogger.println("DEBUG (Main): Tema inicial configurado: " + initialTheme);

            this.iconFileName = ThemeManager.getIconFileNameForTheme(initialTheme);
            fileLogger.println("DEBUG (Main): Nome do arquivo do ícone inicial: " + this.iconFileName);

            this.primaryStage = primaryStage;
            primaryStage.initStyle(StageStyle.UNDECORATED);

            try {
                InputStream iconStream = getClass().getResourceAsStream(this.iconFileName);
                if (iconStream == null) {
                    System.err.println("Stream nulo para ícone da janela: " + this.iconFileName + ". Usando fallback.");
                    iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
                }
                if (iconStream != null) {
                    primaryStage.getIcons().add(new Image(iconStream));
                    fileLogger.println("DEBUG (Main): Ícone da janela definido: " + this.iconFileName);
                } else {
                    fileLogger.println("ERRO (Main): Ícone da janela (incluindo fallback) não encontrado.");
                }
            } catch (Exception e) {
                fileLogger.println("ERRO (Main): Erro ao carregar o ícone inicial da janela: " + e.getMessage());
                e.printStackTrace(fileLogger);
            }

            rootLayout = new VBox();
            rootLayout.setAlignment(Pos.TOP_CENTER);
            rootLayout.getStyleClass().add("root");

            // rootLayout.getChildren().add(createTitleBar()); // Add title bar here
            HBox titleBar = createTitleBar(); // Create title bar
            rootLayout.getChildren().add(titleBar); // Add title bar to rootLayout

            mainContent = new BorderPane();

            VBox.setVgrow(mainContent, Priority.ALWAYS);
            mainContent.setLeft(createSideNavigation());

            statusSidebar = createStatusSidebar();
            mainContent.setRight(statusSidebar);

            scheduler.scheduleAtFixedRate(() -> {
                String sql = "UPDATE usuarios SET status = 'ausente' WHERE status = 'online' AND ultima_atividade < ?";
                Timestamp cutoffTime = Timestamp.from(Instant.now().minus(20, java.time.temporal.ChronoUnit.MINUTES));

                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setTimestamp(1, cutoffTime);
                    int updatedRows = stmt.executeUpdate();
                    if (updatedRows > 0) {
                        System.out.println("Idle check: Marked " + updatedRows + " user(s) as 'ausente'.");
                        performEfficientSidebarUpdate();
                    }
                } catch (Exception e) {
                    System.err.println("Error updating idle users to 'ausente': " + e.getMessage());
                }
            }, 1, 5, TimeUnit.MINUTES);

            scheduler.scheduleAtFixedRate(() -> {
                if (usuarioLogado != null && currentSection != null) {
                    DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), currentSection);
                }
            }, 30, 30, TimeUnit.SECONDS);

            scheduler.scheduleAtFixedRate(this::performEfficientSidebarUpdate, 5, 10, TimeUnit.SECONDS);

            scheduler.scheduleAtFixedRate(() -> {
                if (usuarioLogado == null || "não_perturbe".equalsIgnoreCase(usuarioLogado.getStatus())) return;

                List<DatabaseManager.ConversationInfo> conversas = DatabaseManager.getConversas(usuarioLogado.getUsuario());

                for (DatabaseManager.ConversationInfo conversa : conversas) {
                    Mensagem ultimaMsg = conversa.getUltimaMensagem();

                    if (ultimaMsg != null && !ultimaMsg.isLida() &&
                            !ultimaMsg.getRemetente().equals(usuarioLogado.getUsuario())) {

                        if (!notifiedMessageIds.contains(ultimaMsg.getId())) {
                            Platform.runLater(() -> {
                                if (trayIcon != null) {
                                    Usuario remetente = conversa.getOutroUsuario();
                                    updateTrayIconWithAvatar(remetente);

                                    trayIcon.displayMessage(
                                            "Nova mensagem de " + remetente.getNome(),
                                            ultimaMsg.getConteudo(),
                                            TrayIcon.MessageType.INFO
                                    );
                                }
                            });
                            // Adiciona a ID da mensagem ao conjunto de notificadas
                            notifiedMessageIds.add(ultimaMsg.getId());
                        }

                        break; // Breaks after finding the first unread message in the list
                    }
                }
            }, 5, 8, TimeUnit.SECONDS);

            terminalTabs = new TabPane();
            terminalTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            terminalTabs.setTabMinWidth(150);

            Tab oltsTab = new Tab("Lista de OLTs");
            oltsTab.setClosable(false);
            Node oltsContent = createOLTScreen();
            oltsTab.setContent(oltsContent);

            terminalTabs.getTabs().add(oltsTab);
            mainContent.setCenter(terminalTabs);
            currentSection = "OLTs";
            rootLayout.getChildren().add(mainContent);

            Button criarTicketBtn = new Button("!");
            criarTicketBtn.getStyleClass().add("floating-btn");
            criarTicketBtn.setPrefSize(48, 48);

            criarTicketBtn.setOnAction(e -> {
                Stage ticketStage = new Stage();
                ticketStage.initStyle(StageStyle.UNDECORATED);
                ticketStage.initOwner(primaryStage);

                VBox content = new VBox(15);
                content.getStyleClass().add("glass-pane");
                content.setPadding(new Insets(15));
                content.setPrefSize(500, 450);
                content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

                HBox ticketTitleBar = new HBox(); // Use a different name for ticket title bar
                ticketTitleBar.setAlignment(Pos.CENTER_LEFT);
                ticketTitleBar.setPadding(new Insets(5, 10, 5, 15));

                Label title = new Label("Novo Ticket Interno");
                title.getStyleClass().add("olt-name");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button closeBtn = new Button("✕");
                closeBtn.getStyleClass().addAll("close-btn", "window-btn");
                closeBtn.setOnAction(ev -> ticketStage.close());
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

                Label infoLabel = new Label("Esse ticket vai direto ao Eduardo Tomaz.");
                infoLabel.getStyleClass().add("info-label");

                HBox btnRow = new HBox(10);
                btnRow.setAlignment(Pos.CENTER_RIGHT);
                btnRow.setPadding(new Insets(10, 0, 0, 0));

                Button okBtn = new Button("Criar");
                okBtn.getStyleClass().add("connect-btn");

                okBtn.setOnAction(ev -> {
                    if (descricaoArea.getText().isEmpty() || prioridadeBox.getValue() == null) return;

                    DatabaseManager.criarTicket(
                            usuarioLogado.getNome(),
                            usuarioLogado.getCargo(),
                            descricaoArea.getText(),
                            prioridadeBox.getValue()
                    );
                    ticketStage.close();
                    showToast("✅ Ticket criado com sucesso!");
                });

                btnRow.getChildren().addAll(okBtn);
                content.getChildren().addAll(ticketTitleBar, descLabel, descricaoArea, prioridadeLabel, prioridadeBox, infoLabel, btnRow); // Added ticketTitleBar

                Scene scene = new Scene(content);
                ThemeManager.applyThemeToNewScene(scene);

                ticketStage.setScene(scene);
                ticketStage.show();

            });

            StackPane rootStack = new StackPane();
            rootStack.getChildren().addAll(rootLayout, criarTicketBtn);
            StackPane.setAlignment(criarTicketBtn, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(criarTicketBtn, new Insets(0, 20, 20, 0));

            Scene scene = new Scene(rootStack, 1360, 720);
            ThemeManager.applyTheme(scene, configManager.getTheme());

            primaryStage.setScene(scene);
            primaryStage.setTitle("Gerenciador de OLTs");
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("Window close requested.");
                event.consume();

                backgroundExecutor.submit(() -> {
                    if (usuarioLogado != null) {
                        System.out.println("Updating status to offline for: " + usuarioLogado.getUsuario());
                        DatabaseManager.atualizarStatus(usuarioLogado.getUsuario(), "offline");
                    } else {
                        System.err.println("Close request: usuarioLogado is null.");
                    }

                    shutdownApplicationResources();

                    Platform.runLater(Platform::exit);
                    System.exit(0);
                });
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown hook triggered.");
                if (usuarioLogado != null) {
                    DatabaseManager.atualizarStatus(usuarioLogado.getUsuario(), "offline");
                    System.out.println("ShutdownHook: Attempted to update status to offline for " + usuarioLogado.getUsuario());
                }
                shutdownApplicationResources();
            }));

            primaryStage.show();

            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                java.awt.Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/oltapp-icon-taskbar.png"));

                trayIcon = new TrayIcon(image, "Gerenciador de OLTs");
                trayIcon.setImageAutoSize(true);
                try {
                    tray.add(trayIcon);
                } catch (Exception e) {
                    System.err.println("Erro ao adicionar TrayIcon: " + e.getMessage());
                }
            }

            primaryStage.setOpacity(0);
            Timeline fadeIn = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(primaryStage.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(400), new KeyValue(primaryStage.opacityProperty(), 1))
            );
            fadeIn.play();

            setupWindowDrag(rootLayout.getChildren().get(0));

            scene.setOnKeyPressed(event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.F) {
                    showSection("ONT/ONU By-SN");

                    Platform.runLater(() -> {
                        Node contentNode = contentCache.get("ONT/ONU By-SN");
                        if (contentNode != null) {
                            TextField snField = (TextField) contentNode.lookup(".text-field");
                            if (snField != null && "Digite o SN da ONT/ONU".equals(snField.getPromptText())) {
                                snField.requestFocus();
                            } else {
                                System.err.println("SN Field not found or not unique in ONT/ONU By-SN screen for Ctrl+F focus.");
                            }
                        }
                    });

                    event.consume();
                }
            });


            performEfficientSidebarUpdate();


        } catch (Exception e) {
            fileLogger.println("ERRO (Main): Exceção geral no método start: " + e.getMessage());
            e.printStackTrace(fileLogger);

            Platform.exit();
        }
    }

    private void setupFileLogging() {
        try {
            Path logDir = Paths.get(LOG_DIRECTORY);
            Files.createDirectories(logDir);

            Path logFile = logDir.resolve(DEBUG_LOG_FILE);
            fileLogger = new PrintStream(new FileOutputStream(logFile.toFile(), true));

            System.setOut(fileLogger);
            System.setErr(fileLogger);

            fileLogger.println("--- Aplicativo Iniciado: " + LocalDateTime.now() + " ---");
        } catch (IOException e) {
            System.err.println("Erro ao configurar o log em arquivo: " + e.getMessage());
            e.printStackTrace();
            fileLogger = new PrintStream(System.out);
        }
    }

    private void closeFileLogging() {
        if (fileLogger != null && fileLogger != System.out && fileLogger != System.err) {
            fileLogger.println("--- Aplicativo Encerrado: " + LocalDateTime.now() + " ---");
            fileLogger.close();
        }
    }
    // ---------------------- START ---------------------- //





    // ---------------------- BARRAS VERTICAIS ---------------------- //
    // ------- Barra lateral esquerda (Abas)
    private VBox createSideNavigation() {
        VBox sideNav = new VBox(10);
        sideNav.getStyleClass().add("side-nav");
        sideNav.setPrefWidth(200);
        sideNav.setMaxWidth(250);
        sideNav.setPadding(new Insets(20, 0, 20, 0));

        HBox versionBox = new HBox();
        versionBox.setAlignment(Pos.CENTER_LEFT);
        versionBox.setPadding(new Insets(0, 0, 10, 15));

        Label versionLabel = new Label("v1.5.4.0 • ");
        versionLabel.getStyleClass().add("version-text");

        Label creditsLink = new Label("créditos");
        creditsLink.getStyleClass().add("credits-link");

        Glow glowEffect = new Glow(0.0);
        creditsLink.setEffect(glowEffect);

        creditsLink.setOnMouseEntered(e -> {
            glowEffect.setLevel(0.8);
        });

        creditsLink.setOnMouseExited(e -> {
            glowEffect.setLevel(0.0);
        });

        creditsLink.setOnMouseClicked(e -> showCreditsSection());
        versionBox.getChildren().addAll(versionLabel, creditsLink);
        navGroup = new ToggleGroup();

        ToggleButton oltBtn = createNavButton("OLTs", false);
        ToggleButton signalBtn = createNavButton("Consulta de Sinal", false);
        ToggleButton ponSummaryBtn = createNavButton("PON Summary", false);
        ToggleButton onuBySNBtn = createNavButton("ONT/ONU By-SN", false);
        ToggleButton diagnosisBtn = createNavButton("ONT/ONU Quedas", false);
        ToggleButton conversasBtn = createNavButton("Conversas", false);

        sideNav.getChildren().addAll(oltBtn, signalBtn, ponSummaryBtn, onuBySNBtn, diagnosisBtn, conversasBtn);

        if (usuario.getUsuario().equalsIgnoreCase("Eduardo")) {
            ToggleButton pendenciasBtn = createNavButton("Chamados", false);
            sideNav.getChildren().add(pendenciasBtn);
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

        Label roleLabel = new Label("ㅤ(" + usuario.getCargo() + ")");
        roleLabel.getStyleClass().add("user-role");

        Button dropdownBtn = new Button("▾");
        dropdownBtn.getStyleClass().add("dropdown-arrow");

        userRoleBox.getChildren().addAll(roleLabel, dropdownBtn);
        userInfoBox.getChildren().addAll(usernameLabel, userRoleBox);

        VBox logoutContainer = new VBox(5);
        logoutContainer.setPadding(new Insets(5, 0, 0, 0));
        logoutContainer.setVisible(false);
        logoutContainer.setManaged(false);

        Button dropdownPerfilBtn = new Button("👤 Perfil");
        dropdownPerfilBtn.getStyleClass().add("logout-button");
        dropdownPerfilBtn.setMaxWidth(Double.MAX_VALUE);
        dropdownPerfilBtn.setOnAction(e -> {
            showSection("Perfil");
            logoutContainer.setVisible(false);
            logoutContainer.setManaged(false);
            dropdownBtn.setText("▾");
            dropdownBtn.getStyleClass().remove("dropdown-arrow-active");
        });


        Button themeBtn = new Button("🎨 Temas");
        themeBtn.getStyleClass().add("logout-button");
        themeBtn.setMaxWidth(Double.MAX_VALUE);
        logoutContainer.getChildren().add(dropdownPerfilBtn);
        logoutContainer.getChildren().add(themeBtn);

        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll(
                "Roxo (Padrão)", "All Black", "All White", "Dracula", "Night Owl", "Light Owl", "Creme", "Azul", "Verde", "Vermelho", "Rosa"
        );
        themeCombo.setPromptText("Escolha o tema");
        themeCombo.setVisible(false);
        themeCombo.setManaged(false);
        themeCombo.getStyleClass().add("theme-combobox");
        logoutContainer.getChildren().add(themeCombo);

        dropdownBtn.setOnAction(e -> {
            boolean isVisible = !logoutContainer.isVisible();
            logoutContainer.setVisible(isVisible);
            logoutContainer.setManaged(isVisible);
            dropdownBtn.setText(isVisible ? "▴" : "▾");

            dropdownBtn.getStyleClass().remove("dropdown-arrow-active");
            if (isVisible) {
                dropdownBtn.getStyleClass().add("dropdown-arrow-active");
            }
        });

        themeBtn.setOnAction(e -> {
            boolean visible = !themeCombo.isVisible();
            themeCombo.setVisible(visible);
            themeCombo.setManaged(visible);
        });

        themeCombo.setOnAction(e -> {
            String selected = themeCombo.getValue();
            if (selected != null) {
                String themeFile = "style.css";
                String selectedIconFileName;

                switch (selected) {
                    case "Roxo (Padrão)" -> {
                        themeFile = "style.css";
                        selectedIconFileName = "/oltapp-icon.png";
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

                utils.ThemeManager.applyTheme(primaryStage.getScene(), themeFile);

                configManager.setTheme(themeFile);

                try {
                    InputStream iconStream = getClass().getResourceAsStream(selectedIconFileName);
                    if (iconStream == null) {
                        System.err.println("Stream nulo para ícone da janela (seleção): " + selectedIconFileName + ". Usando fallback.");
                        iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
                    }
                    if (iconStream != null) {
                        Image windowIcon = new Image(iconStream);
                        primaryStage.getIcons().clear();
                        primaryStage.getIcons().add(windowIcon);
                        titleBarIconView.setFitHeight(20);
                        titleBarIconView.setFitWidth(20);
                    } else {
                        System.err.println("Ícone da janela (seleção/fallback) não encontrado.");
                    }
                } catch (Exception ex) {
                    System.err.println("Erro ao carregar o ícone da janela para o tema " + selected + ": " + ex.getMessage());
                }

                if (titleBarIconView != null) {
                    try {
                        InputStream iconStream = getClass().getResourceAsStream(selectedIconFileName);
                        if (iconStream == null) {
                            System.err.println("Stream nulo para ícone da barra de título (seleção): " + selectedIconFileName + ". Usando fallback.");
                            iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
                        }
                        if (iconStream != null) {
                            Image titleBarImage = new Image(iconStream);
                            titleBarIconView.setImage(titleBarImage);
                            titleBarIconView.setFitHeight(20);
                            titleBarIconView.setFitWidth(20);
                        } else {
                            System.err.println("Ícone da barra de título (seleção/fallback) não encontrado.");
                        }
                    } catch (Exception ex) {
                        System.err.println("Erro ao carregar a imagem do ícone da barra de título para o tema " + selected + ": " + ex.getMessage());
                    }
                } else {
                    System.err.println("Erro: Referência titleBarIconView é nula ao tentar atualizar ícone do tema.");
                }
            }
            themeCombo.setVisible(false);
            themeCombo.setManaged(false);
        });

        Button logoutBtn = new Button("❌ Deslogar");
        logoutBtn.getStyleClass().add("logout-button");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutContainer.getChildren().add(logoutBtn);

        logoutBtn.setOnAction(e -> {
            DatabaseManager.atualizarStatus(usuario.getUsuario(), "offline");
            primaryStage.close();

            Stage loginStage = new Stage();
            Usuario novoLogin = new LoginScreen().showLogin(loginStage);

            System.out.println("Usuário retornado: " + (novoLogin != null ? novoLogin.getNome() : "null"));

            if (novoLogin != null) {
                Platform.runLater(() -> {
                    Main novoMain = new Main();
                    novoMain.setUsuario(novoLogin);
                    try {
                        novoMain.start(new Stage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }
        });

        footerBox.getChildren().addAll(versionBox, userInfoBox, logoutContainer);
        sideNav.getChildren().addAll(spacer, footerBox);
        return sideNav;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
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

        if ("OLTs".equals(section) && terminalTabs != null) {
            if (mainContent.getCenter() == terminalTabs) {
                for (Tab tab : terminalTabs.getTabs()) {
                    if ("Lista de OLTs".equals(tab.getText())) {
                        terminalTabs.getSelectionModel().select(tab);
                        break;
                    }
                }
                if (!section.equals(currentSection)) {
                    currentSection = section;
                    DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), currentSection);
                }
                return;
            } else {
                Node currentContent = mainContent.getCenter();
                mainContent.setCenter(terminalTabs);
                if (!section.equals(currentSection)) {
                    currentSection = section;
                    DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), currentSection);
                }

                if (currentContent != null) {
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(150), currentContent);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(e -> {
                        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), terminalTabs);
                        fadeIn.setFromValue(0.0);
                        fadeIn.setToValue(1.0);
                        fadeIn.play();
                    });
                    fadeOut.play();
                } else {
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), terminalTabs);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
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

        if (section.equals(currentSection)) {
            if ("Conversas".equals(section)) {
                atualizarTelaConversas();
            }
        }

        Node currentContent = mainContent.getCenter();
        Node newContent = null;

        if (section.equals("Chamados")) {
            newContent = createTechnicalTicketsScreen();
        } else if (contentCache.containsKey(section)) {
            newContent = contentCache.get(section);
        } else {
            switch (section) {
                case "Consulta de Sinal":
                    newContent = createSignalQueryScreen();
                    break;
                case "PON Summary":
                    newContent = createPONSummaryScreen();
                    break;
                case "ONT/ONU By-SN":
                    newContent = createONUBySNScreen();
                    break;
                case "ONT/ONU Quedas":
                    newContent = createDropDiagnosisScreen();
                    break;
                case "Conversas":
                    newContent = createConversationsScreen();
                    break;
                case "Perfil":
                    newContent = createPerfilScreen();
                    break;
                case "Chamados":
                    newContent = createTechnicalTicketsScreen();
                    break;
                default:
                    newContent = new VBox();
                    break;
            }
            if (newContent != null && !section.startsWith("DM-") && !section.equals("Chamados")) {
                contentCache.put(section, newContent);
                DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), section);
            } else if (section.equals("Chamados")) {
                DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), "Chamados");
            }

        }

        final Node finalNewContent = newContent;

        if (finalNewContent != null && finalNewContent != mainContent.getCenter()) {
            if (currentContent != null) {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(150), currentContent);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);

                fadeOut.setOnFinished(e -> {
                    mainContent.setCenter(finalNewContent);
                    atualizarTelaConversas();

                    if ("Conversas".equals(section)) {
                        atualizarTelaConversas();
                    }
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), finalNewContent);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });

                fadeOut.play();
            } else {
                mainContent.setCenter(finalNewContent);
                if ("Conversas".equals(section)) {
                    atualizarTelaConversas();
                }

                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), finalNewContent);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            }
        }

        if (!section.equals(currentSection)) {
            currentSection = section;
            if (!section.startsWith("DM-")) {
                DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), currentSection);
            } else {
                DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), "Em Conversa");
            }
        }
    }
    // ------- Barra lateral esquerda (Abas)

    // ------- Barra lateral direita (Usuarios)
    private void atualizarStatusSidebar() {
        Platform.runLater(() -> {
            Node novaSidebar = createStatusSidebar();
            if (novaSidebar instanceof Region) {
                ((Region) novaSidebar).setMaxHeight(Double.MAX_VALUE);
                VBox.setVgrow((ScrollPane)novaSidebar, Priority.ALWAYS);
            }
            mainContent.setRight(novaSidebar);
            statusSidebar = novaSidebar;
        });
    }

    private Node createPerfilScreen() {
        VBox perfilBox = new VBox(15);
        perfilBox.setPadding(new Insets(20));
        perfilBox.getStyleClass().add("content-area");
        perfilBox.setAlignment(Pos.CENTER);

        Label nomeLabel = new Label(usuarioLogado.getNome());
        nomeLabel.getStyleClass().add("user-name-profile");
        Label usuarioLabel = new Label("@" + usuarioLogado.getUsuario());
        usuarioLabel.getStyleClass().add("user-tag-profile");
        Label cargoLabel = new Label(usuarioLogado.getCargo());
        cargoLabel.getStyleClass().add("user-role-profile");

        Label statusLabel = new Label("Status:");
        statusLabel.getStyleClass().add("form-label");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("online", "ausente", "offline", "não_perturbe");
        statusCombo.setValue(usuarioLogado.getStatus());
        statusCombo.getStyleClass().add("combo-box");

        statusCombo.setOnAction(e -> {
            String novoStatus = statusCombo.getValue();
            DatabaseManager.atualizarStatus(usuarioLogado.getUsuario(), novoStatus);
            usuarioLogado = new Usuario(
                    usuarioLogado.getNome(),
                    usuarioLogado.getUsuario(),
                    usuarioLogado.getCargo(),
                    novoStatus,
                    usuarioLogado.getFotoPerfil(),
                    usuarioLogado.getAbaAtual()
            );
            performEfficientSidebarUpdate();
        });

        profileAvatarView = carregarAvatar(usuarioLogado.getFotoPerfil(), 200, 200);
        profileAvatarView.setClip(new Circle(100, 100, 100));

        Button trocarFotoBtn = new Button("Trocar Foto");
        trocarFotoBtn.getStyleClass().add("action-button-profile");
        trocarFotoBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Selecione sua nova foto de perfil");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg")
            );

            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                try {
                    byte[] imageBytes = Files.readAllBytes(file.toPath());
                    DatabaseManager.atualizarFotoPerfil(usuarioLogado.getUsuario(), imageBytes);

                    usuarioLogado = new Usuario(
                            usuarioLogado.getNome(),
                            usuarioLogado.getUsuario(),
                            usuarioLogado.getCargo(),
                            usuarioLogado.getStatus(),
                            imageBytes,
                            usuarioLogado.getAbaAtual()
                    );

                    try {
                        Image newProfileImage = new Image(new ByteArrayInputStream(imageBytes));
                        profileAvatarView.setImage(newProfileImage);
                        Circle clip = new Circle(profileAvatarView.getFitWidth() / 2.0, profileAvatarView.getFitHeight() / 2.0, Math.min(profileAvatarView.getFitWidth(), profileAvatarView.getFitHeight()) / 2.0);
                        if (!(profileAvatarView.getClip() instanceof Circle) || ((Circle) profileAvatarView.getClip()).getRadius() != clip.getRadius()) {
                            profileAvatarView.setClip(clip);
                        }
                    } catch (Exception imgEx) {
                        System.err.println("Erro ao atualizar ImageView do perfil: " + imgEx.getMessage());
                        try {
                            InputStream stream = getClass().getResourceAsStream("/imagens/error-avatar.png");
                            if (stream != null) {
                                Image errorAvatarImage = new Image(stream);
                                profileAvatarView.setImage(errorAvatarImage);
                            }
                        } catch (Exception ex) {
                            System.err.println("Could not load error avatar for profile screen: " + ex.getMessage());
                        }
                    }


                    performEfficientSidebarUpdate();


                    Alert alerta = new Alert(Alert.AlertType.INFORMATION, "Foto atualizada com sucesso!");
                    alerta.showAndWait();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Alert erro = new Alert(Alert.AlertType.ERROR, "Erro ao enviar a imagem: " + ex.getMessage());
                    erro.showAndWait();
                }
            }
        });

        perfilBox.getChildren().addAll(
                profileAvatarView,
                nomeLabel,
                usuarioLabel,
                cargoLabel,
                trocarFotoBtn,
                new Separator(javafx.geometry.Orientation.HORIZONTAL),
                statusLabel,
                statusCombo);
        return perfilBox;
    }

    private Node createStatusSidebar() {
        VBox sidebarVBox = new VBox(10);
        sidebarVBox.setPadding(new Insets(10));
        sidebarVBox.setPrefWidth(220);
        sidebarVBox.setMaxWidth(250);
        sidebarVBox.getStyleClass().add("side-status");
        VBox.setVgrow(sidebarVBox, Priority.ALWAYS);

        onlineUserBox = new VBox(5);
        onlineUserBox.setPadding(new Insets(5, 0, 5, 0));
        VBox.setVgrow(onlineUserBox, Priority.ALWAYS);

        offlineUserBox = new VBox(5);
        offlineUserBox.setPadding(new Insets(5, 0, 5, 0));

        ScrollPane offlineScrollPane = new ScrollPane(offlineUserBox);
        offlineScrollPane.setFitToWidth(true);
        offlineScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        offlineScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        offlineScrollPane.getStyleClass().add("transparent-scroll");
        offlineScrollPane.setMaxHeight(Double.MAX_VALUE); // Allow scrollpane to take max height

        onlinePane = new TitledPane("Online (0)", onlineUserBox);
        onlinePane.setExpanded(true);
        onlinePane.getStyleClass().add("user-pane");
        onlinePane.setAnimated(false);
        VBox.setVgrow(onlinePane, Priority.ALWAYS); // Allow onlinePane to grow

        offlinePane = new TitledPane("Offline (0)", offlineScrollPane);
        offlinePane.setExpanded(false);
        offlinePane.getStyleClass().add("user-pane");
        offlinePane.setAnimated(false);
        // Don't set Vgrow for offlinePane

        sidebarVBox.getChildren().addAll(onlinePane, offlinePane);

        ScrollPane mainScrollPane = new ScrollPane(sidebarVBox);
        mainScrollPane.setFitToWidth(true);
        mainScrollPane.setFitToHeight(true); // Fit to height of its content
        mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScrollPane.getStyleClass().add("transparent-scroll");
        mainScrollPane.setMaxHeight(Double.MAX_VALUE); // Allow main scrollpane to take max height
        VBox.setVgrow(mainScrollPane, Priority.ALWAYS); // Make sure the main scrollpane grows within the parent

        return mainScrollPane;
    }


    private HBox criarUserBox(Usuario u) {
        HBox userBox = new HBox(8);
        userBox.setAlignment(Pos.CENTER_LEFT);
        userBox.setCursor(Cursor.HAND);
        userBox.getStyleClass().addAll("user-info-box", "user-hbox");
        userBox.setUserData(u.getUsuario());
        userBox.setOnMouseClicked(event -> abrirPerfilUsuario(u));
        userBox.setMaxWidth(Double.MAX_VALUE); // Allow HBox to take max width

        ImageView avatar = carregarAvatar(u.getFotoPerfil(), 32, 32);
        Circle clip = new Circle(16, 16, 16);
        avatar.setClip(clip);

        Circle statusCircle = new Circle(5);
        statusCircle.setStroke(Color.web("#1e1e2e"));
        statusCircle.setStrokeWidth(1.5);
        statusCircle.setFill(getStatusColor(u.getStatus()));
        statusCircle.getStyleClass().add("status-circle");

        StackPane avatarStack = new StackPane(avatar, statusCircle);
        StackPane.setAlignment(statusCircle, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(statusCircle, new Insets(0, 1, 1, 0));

        Circle stackClip = new Circle(16, 16, 16);
        avatarStack.setClip(stackClip);
        avatarStack.setStyle("-fx-background-color: transparent;");
        VBox nameBox = new VBox(2);
        Label nomeLabel = new Label(u.getNome());
        nomeLabel.getStyleClass().add("user-name");

        String location = u.getAbaAtual();
        boolean isOnlineOrAusente = "online".equalsIgnoreCase(u.getStatus()) || "ausente".equalsIgnoreCase(u.getStatus()) || "não_perturbe".equalsIgnoreCase(u.getStatus()); // Include não_perturbe
        String displayStatus = (isOnlineOrAusente && location != null && !location.isBlank()) ? location : "";
        Label statusLabel = new Label(displayStatus);

        statusLabel.getStyleClass().add("user-role");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setVisible(!displayStatus.isEmpty());
        statusLabel.setManaged(!displayStatus.isEmpty());

        nameBox.getChildren().addAll(nomeLabel, statusLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS); // Allow nameBox to grow

        userBox.getChildren().addAll(avatarStack, nameBox);

        return userBox;
    }

    private void abrirPerfilUsuario(Usuario u) {
        // Busca os dados mais recentes do usuário para garantir que a foto e status estão atualizados
        Usuario usuarioMaisRecente = DatabaseManager.getUsuarioByUsername(u.getUsuario()).orElse(u);
        final Usuario usuarioParaExibir = usuarioMaisRecente; // Usa final para lambda

        // Lógica para fechar ou substituir o drawer existente
        if (userProfileDrawer != null && mainContent.getRight() == userProfileDrawer) {
            String userInDrawer = (userProfileDrawer.getContent() instanceof VBox) ? (String) ((VBox) userProfileDrawer.getContent()).getUserData() : null;
            if (userInDrawer != null && userInDrawer.equals(usuarioParaExibir.getUsuario())) {
                // Se o drawer já está aberto para este usuário, fecha ele
                mainContent.setRight(statusSidebar);
                userProfileDrawer = null;
                return; // Sai do método
            } else {
                // Se é de outro usuário, fecha o drawer atual antes de abrir o novo
                mainContent.setRight(statusSidebar);
                userProfileDrawer = null;
            }
        }

        // Cria o painel do perfil
        VBox profilePane = new VBox(15);
        profilePane.setPadding(new Insets(20));
        profilePane.setPrefWidth(220); // Largura do drawer
        profilePane.getStyleClass().add("user-profile-drawer");
        profilePane.setUserData(usuarioParaExibir.getUsuario()); // Guarda o usuário exibido
        profilePane.setAlignment(Pos.TOP_CENTER); // Align content to top-center
        VBox.setVgrow(profilePane, Priority.ALWAYS); // Allow profilePane to grow

        // Botão de fechar
        Button closeButton = new Button("✕");
        closeButton.getStyleClass().addAll("window-btn", "close-btn", "profile-close-button"); // Adiciona classe específica
        closeButton.setOnAction(e -> {
            mainContent.setRight(statusSidebar); // Volta para a sidebar de status
            userProfileDrawer = null;
        });
        HBox topBar = new HBox(closeButton);
        topBar.setAlignment(Pos.TOP_RIGHT);

        // Avatar do usuário
        ImageView avatar = carregarAvatar(usuarioParaExibir.getFotoPerfil(), 120, 120);
        avatar.setClip(new Circle(60, 60, 60)); // Clip circular

        // Círculo de status sobreposto ao avatar
        Circle statusCircle = new Circle(10);
        statusCircle.setStroke(Color.web("#1e1e2e")); // Cor da borda baseada no tema?
        statusCircle.setStrokeWidth(2);
        statusCircle.setFill(getStatusColor(usuarioParaExibir.getStatus()));
        StackPane avatarStack = new StackPane(avatar, statusCircle);
        StackPane.setAlignment(statusCircle, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(statusCircle, new Insets(0, 5, 5, 0));
        StackPane.setAlignment(avatarStack, Pos.CENTER); // Center the avatar stack

        if (usuarioLogado != null && usuarioLogado.getUsuario().equals(usuarioParaExibir.getUsuario())) {
            avatar.setCursor(Cursor.HAND);
            Tooltip.install(avatar, new Tooltip("Clique para alterar sua foto"));

            avatar.setOnMouseClicked(event -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Selecione sua nova foto de perfil");
                fileChooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif")
                );

                File file = fileChooser.showOpenDialog(primaryStage); // Usa primaryStage
                if (file != null) {
                    try {
                        // Opcional: Verificação de tamanho
                        if (file.length() > 5 * 1024 * 1024) { // Limite de 5MB
                            Alert sizeError = new Alert(Alert.AlertType.ERROR, "Arquivo muito grande (limite: 5MB).");
                            sizeError.initOwner(primaryStage);
                            sizeError.showAndWait();
                            return;
                        }

                        byte[] imageBytes = Files.readAllBytes(file.toPath());
                        DatabaseManager.atualizarFotoPerfil(usuarioLogado.getUsuario(), imageBytes);

                        // Atualiza o objeto local 'usuarioLogado' com a nova foto
                        usuarioLogado = new Usuario(
                                usuarioLogado.getNome(),
                                usuarioLogado.getUsuario(),
                                usuarioLogado.getCargo(),
                                usuarioLogado.getStatus(),
                                imageBytes, // Nova foto
                                usuarioLogado.getAbaAtual()
                        );

                        // Atualiza a ImageView no drawer imediatamente
                        try {
                            Image newProfileImage = new Image(new ByteArrayInputStream(imageBytes));
                            if (newProfileImage.isError()) throw new IOException("Erro ao carregar bytes da imagem.");
                            avatar.setImage(newProfileImage);
                            // Reaplicar clip pode ser necessário se o tamanho da ImageView mudar
                            avatar.setClip(new Circle(avatar.getFitWidth() / 2.0, avatar.getFitHeight() / 2.0, Math.min(avatar.getFitWidth(), avatar.getFitHeight()) / 2.0));
                        } catch (Exception imgEx) {
                            System.err.println("Erro ao atualizar ImageView no drawer: " + imgEx.getMessage());
                            fileLogger.println("ERRO (abrirPerfilUsuario): Falha ao atualizar ImageView: " + imgEx.getMessage());
                            // Mostrar imagem de erro?
                        }


                        performEfficientSidebarUpdate(); // Atualiza a lista principal de usuários
                        showToast("✅ Foto atualizada com sucesso!");

                    } catch (IOException ex) {
                        ex.printStackTrace(fileLogger);
                        Alert erro = new Alert(Alert.AlertType.ERROR, "Erro ao ler ou enviar a imagem: " + ex.getMessage());
                        erro.initOwner(primaryStage);
                        erro.showAndWait();
                    }
                }
            });
        } else {
            avatar.setCursor(Cursor.DEFAULT);
        }


        // Informações do usuário
        Label nomeLabel = new Label(usuarioParaExibir.getNome()); // Removido \n
        nomeLabel.getStyleClass().add("profile-user-name");
        Label usuarioLabel = new Label("@" + usuarioParaExibir.getUsuario());
        usuarioLabel.getStyleClass().add("profile-user-tag");
        Label cargoLabel = new Label(usuarioParaExibir.getCargo());
        cargoLabel.getStyleClass().add("profile-user-role");

        String statusTextToDisplay;
        switch (usuarioParaExibir.getStatus().toLowerCase()) {
            case "online":
                statusTextToDisplay = "Online";
                break;
            case "ausente":
                statusTextToDisplay = "Ausente";
                break;
            case "não_perturbe":
                statusTextToDisplay = "Não Perturbe";
                break;
            default:
                statusTextToDisplay = "Offline";
                break;
        }
        Label statusLabel = new Label("Status: " + statusTextToDisplay);
        statusLabel.getStyleClass().add("profile-user-status");

        Separator separator = new Separator();
        separator.getStyleClass().add("profile-separator");

        Label msgLabel = new Label("Enviar mensagem");
        msgLabel.getStyleClass().add("profile-section-title");

        TextField campoMensagem = new TextField();
        campoMensagem.setPromptText("Escreva uma mensagem...");
        campoMensagem.getStyleClass().add("profile-message-field");

        Button btnEnviar = new Button("Enviar");
        btnEnviar.getStyleClass().add("button");
        btnEnviar.setOnAction(e -> {
            String texto = campoMensagem.getText();
            if (!texto.isBlank()) {
                DatabaseManager.enviarMensagem(usuarioLogado.getUsuario(), usuarioParaExibir.getUsuario(), texto);
                mainContent.setRight(statusSidebar);
                userProfileDrawer = null;
                showSection("Conversas");
                Platform.runLater(() -> mostrarDMNaMesmaAba(usuarioParaExibir));
            }
        });

        profilePane.getChildren().addAll(
                topBar,
                avatarStack,
                nomeLabel,
                usuarioLabel,
                cargoLabel,
                statusLabel,
                separator,
                msgLabel,
                campoMensagem,
                btnEnviar
        );

        // Configura o ScrollPane
        ScrollPane scrollPane = new ScrollPane(profilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("transparent-scroll"); // Usa estilo transparente
        VBox.setVgrow(scrollPane, Priority.ALWAYS); // Allow the scrollpane to grow within the right side

        // Atualiza o drawer e mostra
        userProfileDrawer = scrollPane;
        mainContent.setRight(userProfileDrawer);
    }


    private void atualizarTelaConversas() {

        Node centerContent = mainContent.getCenter();
        boolean isActive = centerContent != null && centerContent.getId() != null && centerContent.getId().equals("conversationsScreen");

        if (conversationsScreenRoot != null && isActive) {

            VBox targetContentBox = null;

            if (conversationsScreenRoot.getChildren().size() > 1) {
                Node secondChild = conversationsScreenRoot.getChildren().get(1);
                if (secondChild instanceof ScrollPane) {
                    ScrollPane scrollPane = (ScrollPane) secondChild;
                    Node contentNode = scrollPane.getContent();
                    if (contentNode instanceof VBox && contentNode.getId() != null && contentNode.getId().equals("contentListBox")) {
                        targetContentBox = (VBox) contentNode;
                    }
                }
            }

            final VBox finalTargetContentBox = targetContentBox;

            if (finalTargetContentBox == null) {
                return;
            }

            Platform.runLater(() -> {
                finalTargetContentBox.getChildren().clear();
                Label loadingLabel = new Label("Carregando...");
                loadingLabel.getStyleClass().add("empty-state");
                finalTargetContentBox.getChildren().add(loadingLabel);
            });

            backgroundExecutor.submit(() -> {
                List<DatabaseManager.ConversationInfo> conversas = DatabaseManager.getConversas(usuarioLogado.getUsuario());

                List<Node> itemsToAdd = new ArrayList<>();

                if (conversas.isEmpty()) {
                    Label recommendedTitle = new Label("Não há conversas ativas. Comece uma agora mesmo:");
                    recommendedTitle.getStyleClass().add("section-title");
                    recommendedTitle.setPadding(new Insets(10, 0, 0, 0));
                    itemsToAdd.add(recommendedTitle);

                    List<Usuario> allUsers = DatabaseManager.getTodosUsuarios();
                    List<Usuario> recommendedUsers = new ArrayList<>();

                    for (Usuario user : allUsers) {
                        if (!user.getUsuario().equals(usuarioLogado.getUsuario())) {
                            recommendedUsers.add(user);
                        }
                    }

                    if (recommendedUsers.isEmpty()) {
                        Label noUsersLabel = new Label("Nenhum outro usuário encontrado.");
                        noUsersLabel.getStyleClass().add("empty-state");
                        itemsToAdd.add(noUsersLabel);
                    } else {
                        for (Usuario recommendedUser : recommendedUsers) {
                            HBox userItem = createRecommendedUserItem(recommendedUser);
                            itemsToAdd.add(userItem);
                        }
                    }
                } else {
                    for (DatabaseManager.ConversationInfo conversa : conversas) {

                        if (conversa.getMensagensNaoLidas() > 0) {
                            Mensagem ultimaMsg = conversa.getUltimaMensagem();

                            if (!usuarioLogado.getStatus().equalsIgnoreCase("não_perturbe") && !ultimaMsg.isLida() && trayIcon != null) {
                            }
                        }
                        HBox conversationItem = createConversationItem(conversa);
                        itemsToAdd.add(conversationItem);

                        if (conversas.indexOf(conversa) < conversas.size() - 1) {
                            Separator separator = new Separator();
                            separator.getStyleClass().add("conversation-separator");
                            itemsToAdd.add(separator);
                        }
                    }
                }

                Platform.runLater(() -> {
                    finalTargetContentBox.getChildren().clear();
                    finalTargetContentBox.getChildren().addAll(itemsToAdd);
                });
            });

        } else if (conversationsScreenRoot == null) {
        } else {
        }
    }


    private void updateTrayIconWithAvatar(Usuario sender) {
        if (trayIcon != null && sender != null) {
            backgroundExecutor.submit(() -> {
                java.awt.Image combinedImage = createCombinedTrayIcon(sender.getFotoPerfil());
                if (combinedImage != null) {
                    Platform.runLater(() -> {
                        trayIcon.setImage(combinedImage);
                    });
                }
            });
        }
    }

    private java.awt.Image createCombinedTrayIcon(byte[] userAvatarBytes) {
        try {
            java.awt.Image baseIcon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/oltapp-icon-taskbar.png"));
            MediaTracker tracker = new MediaTracker(new java.awt.Component() {
            });
            tracker.addImage(baseIcon, 0);
            tracker.waitForID(0);

            int baseWidth = baseIcon.getWidth(null);
            int baseHeight = baseIcon.getHeight(null);

            java.awt.Image combinedImage = new java.awt.image.BufferedImage(baseWidth, baseHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) combinedImage.getGraphics();

            g.drawImage(baseIcon, 0, 0, null);

            if (userAvatarBytes != null && userAvatarBytes.length > 0) {
                java.awt.Image userAvatar = Toolkit.getDefaultToolkit().createImage(userAvatarBytes);
                tracker.addImage(userAvatar, 1);
                tracker.waitForID(1);

                int avatarSize = Math.min(baseWidth, baseHeight) / 3;
                int avatarX = baseWidth - avatarSize;
                int avatarY = baseHeight - avatarSize;

                g.drawImage(userAvatar, avatarX, avatarY, avatarSize, avatarSize, null);

            }

            g.dispose();
            return combinedImage;

        } catch (Exception e) {
            System.err.println("Erro ao criar ícone combinado para TrayIcon: " + e.getMessage());
            e.printStackTrace();
            try {
                java.awt.Image baseIcon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/oltapp-icon-taskbar.png"));
                MediaTracker tracker = new MediaTracker(new java.awt.Component() {
                });
                tracker.addImage(baseIcon, 0);
                tracker.waitForID(0);
                return baseIcon;
            } catch (Exception ex) {
                System.err.println("Erro ao carregar ícone base em caso de erro: " + ex.getMessage());
                return null;
            }
        }
    }

    private void removeSystemTrayIcon() {
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            if (trayIcon != null) {
                tray.remove(trayIcon);
                System.out.println("System Tray Icon removed.");
                trayIcon = null;
            }
        }
    }


    private Node createConversationsScreen() {
        if (conversationsScreenRoot == null) {
            conversationsScreenRoot = new VBox(15);
            conversationsScreenRoot.setPadding(new Insets(15));
            conversationsScreenRoot.getStyleClass().add("content-area");
            conversationsScreenRoot.setId("conversationsScreen");
            VBox.setVgrow(conversationsScreenRoot, Priority.ALWAYS); // Allow conversationsScreenRoot to grow

            Label titleLabel = new Label("Minhas Conversas");
            titleLabel.getStyleClass().add("screen-title");

            VBox contentListBox = new VBox(5);
            contentListBox.setId("contentListBox");
            contentListBox.setPadding(new Insets(5, 0, 5, 0));
            VBox.setVgrow(contentListBox, Priority.ALWAYS); // Allow contentListBox to grow

            ScrollPane scrollPane = new ScrollPane(contentListBox);
            scrollPane.setFitToWidth(true);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.getStyleClass().add("transparent-scroll");
            scrollPane.setMaxHeight(Double.MAX_VALUE); // Allow scrollpane to take max height
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            conversationsScreenRoot.getChildren().addAll(titleLabel, scrollPane);
        }
        return conversationsScreenRoot;
    }


    private HBox createRecommendedUserItem(Usuario u) {
        HBox userBox = new HBox(8);
        userBox.setAlignment(Pos.CENTER_LEFT);
        userBox.setCursor(Cursor.HAND);
        userBox.getStyleClass().addAll("user-info-box", "user-hbox", "recommended-user-item");
        userBox.setUserData(u.getUsuario());
        userBox.setMaxWidth(Double.MAX_VALUE); // Allow HBox to take max width

        ImageView avatar = carregarAvatar(u.getFotoPerfil(), 32, 32);
        Circle clip = new Circle(16, 16, 16);
        avatar.setClip(clip);

        Circle statusCircle = new Circle(5);
        statusCircle.setStroke(Color.web("#1e1e2e"));
        statusCircle.setStrokeWidth(1.5);
        statusCircle.setFill(getStatusColor(u.getStatus()));
        statusCircle.getStyleClass().add("status-circle");

        StackPane avatarStack = new StackPane(avatar, statusCircle);
        StackPane.setAlignment(statusCircle, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(statusCircle, new Insets(0, 1, 1, 0));

        VBox nameBox = new VBox(2);
        Label nomeLabel = new Label(u.getNome());
        nomeLabel.getStyleClass().add("user-name");

        Label cargoLabel = new Label(u.getCargo());
        cargoLabel.getStyleClass().add("user-role");

        nameBox.getChildren().addAll(nomeLabel, cargoLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS); // Allow nameBox to grow

        userBox.getChildren().addAll(avatarStack, nameBox);

        userBox.setOnMouseClicked(event -> {
            mostrarDMNaMesmaAba(u);
        });

        return userBox;
    }

    private HBox createConversationItem(DatabaseManager.ConversationInfo conversa) {
        HBox item = new HBox(10);
        item.setPadding(new Insets(8));
        item.getStyleClass().add("conversation-item");
        item.setCursor(Cursor.HAND);
        item.setMaxWidth(Double.MAX_VALUE); // Allow HBox to take max width

        ImageView avatar = carregarAvatar(conversa.getOutroUsuario().getFotoPerfil(), 48, 48);

        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(conversa.getOutroUsuario().getNome() + " • ");
        nameLabel.getStyleClass().add("conversation-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        String displayTime = "??:??";
        Mensagem lastMsg = conversa.getUltimaMensagem();
        if (lastMsg != null && lastMsg.getTimestampObject() != null) {

            Timestamp dbTimestamp = lastMsg.getTimestampObject();
            Instant instant = dbTimestamp.toInstant();

            LocalDateTime messageLDT = lastMsg.getTimestampObject().toLocalDateTime();
            ZonedDateTime messageZDT = messageLDT.atZone(ZoneId.of("UTC-3"));
            ZonedDateTime localZDT = messageZDT.withZoneSameInstant(ZoneId.systemDefault());
            LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());


            if (localZDT.toLocalDate().equals(now.toLocalDate())) {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                displayTime = timeFormatter.format(localZDT);
            } else if (localZDT.toLocalDate().equals(now.minusDays(1).toLocalDate())) {
                displayTime = "Ontem";
            } else if (localZDT.toLocalDate().isAfter(now.minusDays(7).toLocalDate())) {
                DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEEE", new Locale("pt", "BR"));
                displayTime = dayFormatter.format(localZDT);
                displayTime = displayTime.substring(0, 1).toUpperCase() + displayTime.substring(1);
            } else {
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy");
                displayTime = dateFormatter.format(localZDT);
            }
        }

        Label timeLabel = new Label(displayTime);
        timeLabel.getStyleClass().add("conversation-time");
        timeLabel.setMinWidth(Region.USE_PREF_SIZE);

        headerBox.getChildren().addAll(nameLabel, timeLabel);
        HBox.setHgrow(headerBox, Priority.ALWAYS); // Allow headerBox to grow

        HBox previewBox = new HBox();
        previewBox.setAlignment(Pos.CENTER_LEFT);

        Label messageLabel = new Label();
        messageLabel.getStyleClass().add("conversation-preview");
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(messageLabel, Priority.ALWAYS);

        if (lastMsg != null && lastMsg.getConteudo() != null) {
            String previewText = lastMsg.getConteudo();
            int maxLength = 40;
            if (previewText.length() > maxLength) {
                previewText = previewText.substring(0, maxLength) + "...";
            }
            previewText = previewText.replace('\n', ' ');
            messageLabel.setText(previewText);
        } else {
            messageLabel.setText("...");
        }

        previewBox.getChildren().add(messageLabel);

        if (conversa.getMensagensNaoLidas() > 0) {
            StackPane badge = new StackPane();
            badge.getStyleClass().add("unread-badge");

            Circle badgeCircle = new Circle(10);
            badgeCircle.getStyleClass().add("unread-badge-circle");

            Label countLabel = new Label(String.valueOf(conversa.getMensagensNaoLidas()));
            countLabel.getStyleClass().add("unread-badge-text");

            badge.getChildren().addAll(badgeCircle, countLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            previewBox.getChildren().addAll(spacer, badge);
            HBox.setMargin(badge, new Insets(0, 0, 0, 5));
        }
        HBox.setHgrow(previewBox, Priority.ALWAYS); // Allow previewBox to grow

        infoBox.getChildren().addAll(headerBox, previewBox);

        item.getChildren().addAll(avatar, infoBox);

        item.setOnMouseClicked(event -> {
            List<Integer> readMessageIds = DatabaseManager.marcarMensagensComoLidas(
                    usuarioLogado.getUsuario(),
                    conversa.getOutroUsuario().getUsuario()
            );
            mostrarDMNaMesmaAba(conversa.getOutroUsuario());
        });

        return item;
    }
    private void mostrarDMNaMesmaAba(Usuario destinatario) {
        currentChatPartner = destinatario;
        currentMessageOffset = 0;
        totalMessages = 0;
        isLoadingMessages = false;

        BorderPane chatLayout = new BorderPane();
        chatLayout.getStyleClass().add("content-area-dms");
        chatLayout.setId("dmChatLayout-" + destinatario.getUsuario());
        VBox.setVgrow(chatLayout, Priority.ALWAYS); // Allow chatLayout to grow

        HBox header = new HBox(10);
        Button backBtn = new Button("←");
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> showSection("Conversas"));

        Label titulo = new Label("Mensagem Privada com " + destinatario.getNome());
        titulo.getStyleClass().add("screen-title");

        header.getChildren().addAll(backBtn, titulo);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        chatLayout.setTop(header);

        VBox mensagensBox = new VBox(8);
        mensagensBox.setPadding(new Insets(10));
        mensagensBox.getStyleClass().add("dm-messages-container");
        VBox.setVgrow(mensagensBox, Priority.ALWAYS); // Allow mensagensBox to grow

        ScrollPane scrollPane = new ScrollPane(mensagensBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("transparent-scroll");
        scrollPane.setMaxHeight(Double.MAX_VALUE); // Allow scrollpane to take max height
        chatLayout.setCenter(scrollPane);

        Map<Integer, Mensagem> mensagensMap = new HashMap<>();
        AtomicReference<Mensagem> respondendoA = new AtomicReference<>(null);
        VBox bottomContainer = new VBox();
        bottomContainer.getProperties().put("respondendoA", respondendoA);
        bottomContainer.getProperties().put("mensagensMap", mensagensMap);

        VBox replyArea = new VBox(5);
        replyArea.getStyleClass().add("reply-area");
        replyArea.setManaged(false);
        replyArea.setVisible(false);

        HBox replyHeader = new HBox(5);
        replyHeader.setAlignment(Pos.CENTER_LEFT);

        Label replyingToLabel = new Label();
        replyingToLabel.getStyleClass().add("replying-to-label");

        Button cancelReplyBtn = new Button("×");
        cancelReplyBtn.getStyleClass().addAll("cancel-reply-button"); // Use all styles from CSS
        cancelReplyBtn.setOnAction(e -> {
            respondendoA.set(null);
            replyArea.setManaged(false);
            replyArea.setVisible(false);
        });


        if (classExists("org.kordamp.ikonli.javafx.FontIcon")) {
            // Ensure FontIcon is correctly used
            replyHeader.getChildren().addAll(new FontIcon("fas-reply"), replyingToLabel, new Region(), cancelReplyBtn);
        } else {
            Label iconPlaceholder = new Label("[Reply Icon]");
            replyHeader.getChildren().addAll(iconPlaceholder, replyingToLabel, new Region(), cancelReplyBtn);
            System.err.println("FontIcon class not found. Please ensure Ikonli is in your project dependencies.");
        }

        HBox.setHgrow(replyHeader.getChildren().get(replyHeader.getChildren().size() - 2), Priority.ALWAYS);

        Label replyPreviewLabel = new Label();
        replyPreviewLabel.getStyleClass().add("reply-preview-label");
        replyPreviewLabel.setWrapText(true);

        replyArea.getChildren().addAll(replyHeader, replyPreviewLabel);

        TextArea input = new TextArea();
        input.setPromptText("Digite sua mensagem...");
        input.getStyleClass().add("dm-input-field");
        input.setPrefRowCount(1);
        input.setWrapText(true);
        HBox.setHgrow(input, Priority.ALWAYS);

        Button enviar = new Button("→");
        enviar.getStyleClass().add("dm-send-button");
        enviar.setDisable(true);

        input.textProperty().addListener((obs, oldText, newText) -> {
            enviar.setDisable(newText.trim().isEmpty());
            int newLineCount = newText.split("\n").length;
            input.setPrefRowCount(Math.min(5, Math.max(1, newLineCount)));
        });

        enviar.setOnAction(e -> enviarMensagem(input, mensagensBox, scrollPane, destinatario, respondendoA, replyArea, mensagensMap));

        input.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                } else {
                    event.consume();
                    if (!input.getText().trim().isEmpty()) {
                        enviar.fire();
                    }
                }
            }
        });

        HBox inputArea = new HBox(10, input, enviar);
        inputArea.setPadding(new Insets(10));
        inputArea.getStyleClass().add("dm-input-container");
        inputArea.setAlignment(Pos.CENTER);

        bottomContainer.getChildren().addAll(replyArea, inputArea);
        chatLayout.setBottom(bottomContainer);
        VBox.setVgrow(bottomContainer, Priority.NEVER); // Don't allow bottomContainer to grow

        scrollPane.vvalueProperty().addListener((obs, oldVvalue, newVvalue) -> {
            if (newVvalue.doubleValue() < 0.1 && !isLoadingMessages && (currentMessageOffset < totalMessages)) {
                loadMoreMessages(mensagensBox, scrollPane, destinatario, mensagensMap, input, replyArea, respondendoA);
            }
        });

        loadInitialMessages(mensagensBox, scrollPane, destinatario, mensagensMap, input, replyArea, respondendoA);

        Platform.runLater(() -> {
            mainContent.setCenter(chatLayout);
            currentSection = "DM-" + destinatario.getUsuario();
            DatabaseManager.atualizarUltimaAtividade(usuarioLogado.getUsuario(), "Em Conversa");
            input.requestFocus();
        });
    }

    private void loadInitialMessages(VBox mensagensBox, ScrollPane scrollPane, Usuario destinatario,
                                     Map<Integer, Mensagem> mensagensMap, TextArea input, VBox replyArea, AtomicReference<Mensagem> respondendoA) {
        Label loadingLabel = new Label("Carregando mensagens...");
        loadingLabel.getStyleClass().add("empty-state");
        Platform.runLater(() -> {
            mensagensBox.getChildren().setAll(loadingLabel);
        });

        backgroundExecutor.submit(() -> {
            try {
                totalMessages = DatabaseManager.getTotalMensagensPrivadas(usuarioLogado.getUsuario(), destinatario.getUsuario());

                List<Mensagem> mensagens = DatabaseManager.getMensagensPrivadasPaginado(
                        usuarioLogado.getUsuario(), destinatario.getUsuario(), MESSAGES_PER_PAGE, 0
                );

                Platform.runLater(() -> {
                    mensagensBox.getChildren().clear();
                    mensagensMap.clear();

                    for (Mensagem m : mensagens) {
                        mensagensBox.getChildren().add(createMessageNode(m, mensagensMap, mensagensBox, scrollPane, input, replyArea, respondendoA));
                        mensagensMap.put(m.getId(), m);
                    }

                    List<Integer> initialMessageIds = new ArrayList<>();
                    for(Mensagem m : mensagens) {
                        if (!m.getRemetente().equals(usuarioLogado.getUsuario())) {
                            initialMessageIds.add(m.getId());
                        }
                    }
                    notifiedMessageIds.removeAll(initialMessageIds);

                    List<Integer> readMessageIds = DatabaseManager.marcarMensagensComoLidas( // <--- E AQUI RECEBE A LISTA E ATUALIZA O SET LOCAL
                            usuarioLogado.getUsuario(),
                            destinatario.getUsuario()
                    );
                    notifiedMessageIds.removeAll(readMessageIds);

                    Node currentSidebarContent = mainContent.getCenter(); // Verifica o centro, não a direita
                    if (currentSidebarContent != null && currentSidebarContent.getId() != null && currentSidebarContent.getId().equals("conversationsScreen")) {
                        Platform.runLater(this::atualizarTelaConversas);
                    }


                    Platform.runLater(() -> {
                        scrollPane.setVvalue(1.0);
                        mensagensBox.getProperties().put("chatRecipient", destinatario.getUsuario());
                    });


                    currentMessageOffset = mensagens.size();
                });
            } catch (Exception e) {
                System.err.println("Erro durante o carregamento inicial de mensagens: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    mensagensBox.getChildren().setAll(new Label("Erro ao carregar mensagens."));
                });
            } finally {
                isLoadingMessages = false;
            }
        });
    }

    private void loadMoreMessages(VBox mensagensBox, ScrollPane scrollPane, Usuario destinatario,
                                  Map<Integer, Mensagem> mensagensMap, TextArea input, VBox replyArea, AtomicReference<Mensagem> respondendoA) {
        if (currentMessageOffset >= totalMessages || isLoadingMessages) {
            return;
        }

        isLoadingMessages = true;
        Label loadingMoreLabel = new Label("Carregando mensagens antigas...");
        loadingMoreLabel.getStyleClass().add("empty-state");
        Platform.runLater(() -> mensagensBox.getChildren().add(0, loadingMoreLabel));

        backgroundExecutor.submit(() -> {
            try {
                List<Mensagem> oldMessages = DatabaseManager.getMensagensPrivadasPaginado(
                        usuarioLogado.getUsuario(), destinatario.getUsuario(), MESSAGES_PER_PAGE, currentMessageOffset
                );

                Platform.runLater(() -> {
                    mensagensBox.getChildren().remove(0);
                    double currentScrollY = scrollPane.getBoundsInLocal().getMinY() + scrollPane.getVvalue() * (mensagensBox.getHeight() - scrollPane.getViewportBounds().getHeight());
                    double contentHeightBefore = mensagensBox.getHeight();

                    for (int i = oldMessages.size() - 1; i >= 0; i--) {
                        Mensagem m = oldMessages.get(i);
                        mensagensBox.getChildren().add(0, createMessageNode(m, mensagensMap, mensagensBox, scrollPane, input, replyArea, respondendoA));
                        mensagensMap.put(m.getId(), m);
                    }

                    currentMessageOffset += oldMessages.size();
                    mensagensBox.requestLayout();
                    mensagensBox.layout();

                    double contentHeightAfter = mensagensBox.getHeight();
                    double heightDifference = contentHeightAfter - contentHeightBefore;
                    double newScrollY = currentScrollY + heightDifference;
                    double newVvalue = newScrollY / (mensagensBox.getHeight() > 0 ? mensagensBox.getHeight() : 1.0);

                    Platform.runLater(() -> {
                        scrollPane.setVvalue(Math.max(0.0, Math.min(1.0, newVvalue)));
                    });

                    isLoadingMessages = false;
                });
            } catch (Exception e) {
                System.err.println("Erro durante o carregamento de mais mensagens: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    mensagensBox.getChildren().remove(0);
                });
            } finally {
                isLoadingMessages = false;
            }
        });
    }

    private HBox createMessageNode(Mensagem m, Map<Integer, Mensagem> mensagensMap, VBox mensagensBox, ScrollPane scrollPane,
                                   TextArea input, VBox replyArea, AtomicReference<Mensagem> respondendoA) {
        HBox messageContainer = new HBox(8);
        messageContainer.setId("msg-" + m.getId());
        messageContainer.setMaxWidth(Double.MAX_VALUE); // Allow messageContainer to take max width
        boolean isSenderMe = m.getRemetente().equals(usuarioLogado.getUsuario());
        Usuario remetente = DatabaseManager.getUsuarioByUsername(m.getRemetente())
                .orElse(new Usuario("?", "?", "?", "offline", null, "?"));

        ImageView avatar = null;
        if (remetente.getFotoPerfil() != null && remetente.getFotoPerfil().length > 0) {
            avatar = carregarAvatar(remetente.getFotoPerfil(), 32, 32);
        } else {
            try (InputStream is = getClass().getResourceAsStream("/imagens/default-avatar.png")) {
                if (is != null) {
                    byte[] defaultAvatarBytes = is.readAllBytes();
                    avatar = carregarAvatar(defaultAvatarBytes, 32, 32);
                } else {
                    System.err.println("❌ Imagem padrão não encontrada em /imagens/default-avatar.png");
                    avatar = new ImageView();
                    avatar.setFitWidth(32);
                    avatar.setFitHeight(32);
                }
            } catch (IOException e) {
                System.err.println("Erro ao carregar imagem padrão: " + e.getMessage());
                avatar = new ImageView();
                avatar.setFitWidth(32);
                avatar.setFitHeight(32);
            }
        }

        VBox messageBox = new VBox(2);
        Label nameLabel = new Label(remetente.getNome());
        nameLabel.getStyleClass().add("message-sender-name");

        Integer refMsgId = DatabaseManager.getMensagemRespondidaId(m.getId());
        if (refMsgId != null) {
            Mensagem refMsg = mensagensMap.get(refMsgId);
            if (refMsg == null) {
                Optional<Mensagem> optionalRefMsg = DatabaseManager.getMensagemPorId(refMsgId);
                if (optionalRefMsg.isPresent()) {
                    refMsg = optionalRefMsg.get();
                }
            }

            if (refMsg != null) {
                Usuario refRemetente = DatabaseManager.getUsuarioByUsername(refMsg.getRemetente())
                        .orElse(new Usuario("?", "?", "?", "offline", null, "?"));

                VBox replyContainer = new VBox(2);
                replyContainer.getStyleClass().add("reply-reference");

                Label replyName = new Label(refRemetente.getNome());
                replyName.getStyleClass().add("reply-name");

                String previewContent = refMsg.getConteudo();
                if (previewContent.length() > 50) {
                    previewContent = previewContent.substring(0, 50) + "...";
                }
                Label replyPreview = new Label(previewContent);
                replyPreview.getStyleClass().add("reply-preview");

                replyContainer.getChildren().addAll(replyName, replyPreview);
                replyContainer.setOnMouseClicked(e -> {
                    Mensagem originalMsgToScroll = mensagensMap.get(refMsgId);
                    if (originalMsgToScroll == null) {
                        DatabaseManager.getMensagemPorId(refMsgId).ifPresent(msg -> {
                            scrollToMessage(msg.getId(), mensagensBox, scrollPane);
                        });
                    } else {
                        scrollToMessage(originalMsgToScroll.getId(), mensagensBox, scrollPane);
                    }
                });

                messageBox.getChildren().add(replyContainer);
            } else {
                VBox replyContainer = new VBox(2);
                replyContainer.getStyleClass().add("reply-reference-unavailable");
                Label unavailableLabel = new Label("Mensagem original não disponível");
                unavailableLabel.getStyleClass().add("reply-preview-unavailable");
                replyContainer.getChildren().add(unavailableLabel);
                messageBox.getChildren().add(replyContainer);
            }
        }

        TextFlow textFlow = new TextFlow();
        textFlow.setMaxWidth(400);
        textFlow.getStyleClass().add(isSenderMe ? "bolha-enviada" : "bolha-recebida");

        String[] lines = m.getConteudo().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Text textPart = new Text(lines[i]);
            textFlow.getChildren().add(textPart);

            if (i < lines.length - 1) {
                textFlow.getChildren().add(new Text("\n"));
            }
        }
        if (!textFlow.getChildren().isEmpty() && !m.getConteudo().endsWith("\n")) {
            Node lastNode = textFlow.getChildren().get(textFlow.getChildren().size() - 1);
            if (lastNode instanceof Text && ((Text)lastNode).getText().equals("\n")) {
                textFlow.getChildren().remove(lastNode);
            }
        }

        StackPane messageBubbleContainer = new StackPane();
        messageBubbleContainer.getStyleClass().add("message-bubble-container");
        messageBubbleContainer.getChildren().add(textFlow);

        HBox actionButtons = new HBox(5);
        actionButtons.getStyleClass().add("message-action-buttons");
        actionButtons.setOpacity(0);

        if (classExists("org.kordamp.ikonli.javafx.FontIcon")) {
            Button copyBtn = new Button();
            copyBtn.setGraphic(new FontIcon("fas-copy"));
            copyBtn.getStyleClass().add("action-button");
            copyBtn.setTooltip(new Tooltip("Copiar mensagem"));
            copyBtn.setOnAction(e -> {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(m.getConteudo());
                clipboard.setContent(content);
                showToast("✓ Mensagem copiada!");
            });

            Button replyBtn = new Button();
            replyBtn.setGraphic(new FontIcon("fas-reply"));
            replyBtn.getStyleClass().add("action-button");
            replyBtn.setTooltip(new Tooltip("Responder"));
            replyBtn.setOnAction(e -> prepararResposta(m, input, replyArea, respondendoA));

            actionButtons.getChildren().addAll(copyBtn, replyBtn);
        } else {
            Button copyBtn = new Button("Copiar");
            copyBtn.getStyleClass().add("action-button");
            copyBtn.setOnAction(e -> {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(m.getConteudo());
                clipboard.setContent(content);
                showToast("✓ Mensagem copiada!");
            });

            Button replyBtn = new Button("Responder");
            replyBtn.getStyleClass().add("action-button");
            replyBtn.setOnAction(e -> prepararResposta(m, input, replyArea, respondendoA));

            actionButtons.getChildren().addAll(copyBtn, replyBtn);
            System.err.println("FontIcon class not found for message action buttons. Using text buttons.");
        }

        HBox bubbleWithActions = new HBox(5);
        if (isSenderMe) {
            bubbleWithActions.getChildren().addAll(actionButtons, messageBubbleContainer);
            bubbleWithActions.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubbleWithActions.getChildren().addAll(messageBubbleContainer, actionButtons);
            bubbleWithActions.setAlignment(Pos.CENTER_LEFT);
        }
        HBox.setHgrow(bubbleWithActions, Priority.ALWAYS); // Allow bubbleWithActions to grow

        bubbleWithActions.setOnMouseEntered(e -> actionButtons.setOpacity(1));
        bubbleWithActions.setOnMouseExited(e -> actionButtons.setOpacity(0));

        Label timeLabel = new Label(m.getFormattedTimestamp("HH:mm", ZoneId.systemDefault()));
        timeLabel.getStyleClass().add("message-timestamp");

        messageBox.getChildren().addAll(bubbleWithActions, timeLabel);
        VBox.setVgrow(messageBox, Priority.NEVER); // Prevent messageBox from growing vertically

        if (isSenderMe) {
            messageContainer.setAlignment(Pos.CENTER_RIGHT);
            if (avatar != null) messageContainer.getChildren().addAll(messageBox, avatar); else messageContainer.getChildren().add(messageBox);
        } else {
            messageContainer.setAlignment(Pos.CENTER_LEFT);
            if (avatar != null) messageContainer.getChildren().addAll(avatar, messageBox); else messageContainer.getChildren().add(messageBox);
        }

        return messageContainer;
    }

    private void scrollToMessage(int messageId, VBox mensagensBox, ScrollPane scrollPane) {
        Node targetNode = mensagensBox.lookup("#msg-" + messageId);
        if (targetNode != null) {
            double scrollY = targetNode.getBoundsInParent().getMinY();
            double contentHeight = mensagensBox.getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double targetVvalue = scrollY / contentHeight;

            Platform.runLater(() -> {
                scrollPane.setVvalue(Math.max(0.0, Math.min(1.0, targetVvalue)));
                targetNode.getStyleClass().add("highlighted-message");
                PauseTransition pause = new PauseTransition(Duration.seconds(2));
                pause.setOnFinished(event -> targetNode.getStyleClass().remove("highlighted-message"));
                pause.play();
            });
        } else {
            System.err.println("Node for message ID " + messageId + " not found in UI.");
            showToast("Mensagem referenciada não visível ou não carregada.");
        }
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void enviarMensagem(TextArea input, VBox mensagensBox, ScrollPane scrollPane,
                                Usuario destinatario, AtomicReference<Mensagem> respondendoA, VBox replyArea, Map<Integer, Mensagem> mensagensMap) {
        String msg = input.getText().trim();
        if (msg.isEmpty()) return;

        Integer refMsgId = (respondendoA.get() != null) ? respondendoA.get().getId() : null;

        boolean sent = DatabaseManager.enviarMensagemComReferencia(
                usuarioLogado.getUsuario(), destinatario.getUsuario(), msg, refMsgId);

        if (!sent) {
            showToast("❌ Erro ao enviar mensagem.");
            return;
        }

        input.clear();
        respondendoA.set(null);
        replyArea.setManaged(false);
        replyArea.setVisible(false); // Oculta a área de resposta

        backgroundExecutor.submit(() -> {
            // Marca a mensagem enviada como lida para o remetente (você mesmo)
            // Isso pode não ser estritamente necessário para notificações, mas é boa prática.
            // E garante que se houver alguma lógica baseada em 'lida' para mensagens enviadas, funcione.
            // Dependendo da implementação de marcarMensagensComoLidas, pode ser necessário adaptar.
            DatabaseManager.marcarMensagensComoLidas(
                    usuarioLogado.getUsuario(), // O remetente
                    usuarioLogado.getUsuario()  // O destinatário (você mesmo, na sua caixa de saída conceitual)
            );

            DatabaseManager.getUltimaMensagem(usuarioLogado.getUsuario(), destinatario.getUsuario())
                    .ifPresent(ultimaMensagem -> {
                        Platform.runLater(() -> {
                            HBox newMessageNode = createMessageNode(
                                    ultimaMensagem,
                                    mensagensMap,
                                    mensagensBox,
                                    scrollPane,
                                    input,
                                    replyArea,
                                    respondendoA
                            );

                            mensagensBox.getChildren().add(newMessageNode);
                            mensagensMap.put(ultimaMensagem.getId(), ultimaMensagem);
                            totalMessages++;
                            scrollPane.setVvalue(1.0);

                            // Não remove do notifiedMessageIds aqui, pois é uma mensagem que você enviou.
                            // A notificação só é para mensagens recebidas.

                            // Atualiza a tela de conversas na barra lateral se necessário,
                            // para mostrar a última mensagem enviada.
                            // Verificar se a tela de conversas lateral está visível antes de atualizar
                            Node currentSidebarContent = mainContent.getCenter(); // Verifica o centro, não a direita
                            if (currentSidebarContent != null && currentSidebarContent.getId() != null && currentSidebarContent.getId().equals("conversationsScreen")) {
                                // A tela de conversas lateral está visível, atualize-a.
                                Platform.runLater(this::atualizarTelaConversas);
                            }

                        });
                    });
        });
    }


    private void prepararResposta(Mensagem mensagemOriginal, TextArea inputArea, VBox replyArea, AtomicReference<Mensagem> respondendoA) {
        Label replyingToLabel = null;
        Label replyPreviewLabel = null;

        if (!replyArea.getChildren().isEmpty() && replyArea.getChildren().get(0) instanceof HBox) {
            HBox replyHeader = (HBox) replyArea.getChildren().get(0);
            // Check for FontIcon or placeholder label at index 0, and the target label at index 1
            int labelIndex = 1; // Assuming the label is the second child after the icon/placeholder
            if (replyHeader.getChildren().size() > labelIndex && replyHeader.getChildren().get(labelIndex) instanceof Label) {
                replyingToLabel = (Label) replyHeader.getChildren().get(labelIndex);
            }
        }
        if (replyArea.getChildren().size() > 1 && replyArea.getChildren().get(1) instanceof Label) {
            replyPreviewLabel = (Label) replyArea.getChildren().get(1);
        }

        if (replyingToLabel == null || replyPreviewLabel == null) {
            System.err.println("Erro: Não foi possível encontrar os Labels dentro da replyArea na prepararResposta.");
            // Attempt to find by style class as a fallback
            if(replyingToLabel == null) replyingToLabel = (Label) replyArea.lookup(".replying-to-label");
            if(replyPreviewLabel == null) replyPreviewLabel = (Label) replyArea.lookup(".reply-preview-label");

            if(replyingToLabel == null || replyPreviewLabel == null) {
                System.err.println("Erro: Falha ao encontrar Labels mesmo por style class.");
                return; // Cannot proceed without required labels
            }
        }


        Usuario remetente = DatabaseManager.getUsuarioByUsername(mensagemOriginal.getRemetente())
                .orElse(new Usuario("?", "?", "?", "offline", null, "?"));

        replyingToLabel.setText("Respondendo a " + remetente.getNome());

        String previewContent = mensagemOriginal.getConteudo();
        if (previewContent.length() > 50) {
            previewContent = previewContent.substring(0, 50) + "...";
        }
        replyPreviewLabel.setText(previewContent);

        replyArea.setManaged(true);
        replyArea.setVisible(true);

        if (respondendoA != null) {
            respondendoA.set(mensagemOriginal);
        } else {
            System.err.println("AtomicReference 'respondendoA' é nulo na prepararResposta.");
        }

        if (inputArea != null) {
            inputArea.requestFocus();
        } else {
            System.err.println("TextArea 'inputArea' é nulo na prepararResposta.");
        }
    }
    private void performEfficientSidebarUpdate() {
        if (onlineUserBox == null || offlineUserBox == null) {
            System.err.println("Sidebar update skipped: UI components not initialized.");
            return;
        }
        if (onlinePane == null || offlinePane == null) {
            System.err.println("Sidebar update skipped: TitledPanes not initialized.");
            return;
        }

        backgroundExecutor.submit(() -> {
            List<Usuario> latestUsers = DatabaseManager.getTodosUsuarios();
            List<Runnable> uiUpdates = new ArrayList<>();
            List<String> usersToRemove = new ArrayList<>(userNodeMap.keySet());

            List<Node> onlineNodes = new ArrayList<>();
            List<Node> offlineNodes = new ArrayList<>();


            for (Usuario user : latestUsers) {
                String username = user.getUsuario();
                usersToRemove.remove(username);

                Node userNode = userNodeMap.get(username);

                if (userNode == null) {
                    userNode = criarUserBox(user);
                    userNodeMap.put(username, userNode);
                } else {
                    final Node finalUserNode = userNode;
                    final Usuario latestUser = user;

                    uiUpdates.add(() -> {
                        updateUserNodeUI(finalUserNode, latestUser);
                        userStatusMap.put(username, latestUser.getStatus());
                        userLocationMap.put(username, latestUser.getAbaAtual());
                    });
                }

                boolean isCurrentlyConsideredOnline = "online".equalsIgnoreCase(user.getStatus()) || "ausente".equalsIgnoreCase(user.getStatus()) || "não_perturbe".equalsIgnoreCase(user.getStatus());
                if (isCurrentlyConsideredOnline) {
                    onlineNodes.add(userNode);
                } else {
                    offlineNodes.add(userNode);
                }
            }

            for (String userToRemove : usersToRemove) {
                Node nodeToRemove = userNodeMap.remove(userToRemove);
                userStatusMap.remove(userToRemove);
                userLocationMap.remove(userToRemove);
                if (nodeToRemove != null) {
                    uiUpdates.add(() -> {
                        onlineUserBox.getChildren().remove(nodeToRemove);
                        offlineUserBox.getChildren().remove(nodeToRemove);
                        System.out.println("Removed user node: " + userToRemove);
                    });
                }
            }

            Platform.runLater(() -> {
                for (Runnable update : uiUpdates) {
                    update.run();
                }

                onlineUserBox.getChildren().setAll(onlineNodes);
                offlineUserBox.getChildren().setAll(offlineNodes);

                onlinePane.setText("Online (" + onlineNodes.size() + ")");
                offlinePane.setText("Offline (" + offlineNodes.size() + ")");

            });
        });
    }

    private void updateUserNodeUI(Node userNode, Usuario user) {
        if (userNode == null || user == null) return;

        Circle statusCircle = (Circle) userNode.lookup(".status-circle");
        Label statusLabel = (Label) userNode.lookup(".status-label");
        ImageView avatarView = null;

        if (userNode instanceof HBox) {
            HBox userHBox = (HBox) userNode;
            if (!userHBox.getChildren().isEmpty() && userHBox.getChildren().get(0) instanceof StackPane) {
                StackPane avatarStack = (StackPane) userHBox.getChildren().get(0);
                for (Node stackChild : avatarStack.getChildren()) {
                    if (stackChild instanceof ImageView) {
                        avatarView = (ImageView) stackChild;
                        break;
                    }
                }
            }
        }

        if (statusCircle != null) {
            statusCircle.setFill(getStatusColor(user.getStatus()));
        }

        if (statusLabel != null) {
            boolean isOnlineOrAusente = "online".equalsIgnoreCase(user.getStatus()) || "ausente".equalsIgnoreCase(user.getStatus()) || "não_perturbe".equalsIgnoreCase(user.getStatus());

            if (isOnlineOrAusente) {
                String displayStatus;
                if (user.getUsuario().equals(usuarioLogado.getUsuario()) && user.getAbaAtual() != null && user.getAbaAtual().startsWith("DM-")) {
                    displayStatus = "Em Conversa";
                } else {
                    displayStatus = (user.getAbaAtual() != null && !user.getAbaAtual().isBlank()) ? user.getAbaAtual() : "Indisponível";
                }

                statusLabel.setText(displayStatus);
                statusLabel.setVisible(true);
                statusLabel.setManaged(true);

            } else {
                statusLabel.setText("");
                statusLabel.setVisible(false);
                statusLabel.setManaged(false);
            }
        }

        if (avatarView != null) {
            try {

                if (user.getFotoPerfil() != null && user.getFotoPerfil().length > 0) {
                    Image newAvatarImage = new Image(new ByteArrayInputStream(user.getFotoPerfil()));
                    avatarView.setImage(newAvatarImage);

                } else {
                    InputStream stream = getClass().getResourceAsStream("/imagens/default-avatar.png");
                    if (stream != null) {
                        Image defaultAvatarImage = new Image(stream);
                        avatarView.setImage(defaultAvatarImage);

                    } else {
                        System.err.println("❌ Imagem padrão não encontrada ao tentar atualizar avatar para " + user.getUsuario() + ".");
                    }
                }

                Circle clip = new Circle(avatarView.getFitWidth() / 2.0, avatarView.getFitHeight() / 2.0, Math.min(avatarView.getFitWidth(), avatarView.getFitHeight()) / 2.0);

                if (!(avatarView.getClip() instanceof Circle) || ((Circle) avatarView.getClip()).getRadius() != clip.getRadius()) {
                    avatarView.setClip(clip);
                }

            } catch (Exception e) {
                System.err.println("Erro ao carregar/atualizar imagem do avatar para " + user.getUsuario() + ": " + e.getMessage());

                try {
                    InputStream stream = getClass().getResourceAsStream("/imagens/error-avatar.png");
                    if (stream != null) {
                        Image errorAvatarImage = new Image(stream);
                        avatarView.setImage(errorAvatarImage);
                    }
                } catch (Exception ex) {
                    System.err.println("Não foi possível carregar o ícone de erro do avatar: " + ex.getMessage());
                }
            }
        }
    }

    private Color getStatusColor(String status) {
        if (status == null) return Color.GRAY;
        return switch (status.toLowerCase()) {
            case "online" -> Color.LIMEGREEN;
            case "ausente" -> Color.GOLD;
            case "não_perturbe" -> Color.web("#f23f42");
            default -> Color.GRAY;
        };
    }

    private void shutdownApplicationResources() {
        System.out.println("Shutting down application resources...");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                System.out.println("Scheduler shutdown complete.");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
            try {
                if (!backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    backgroundExecutor.shutdownNow();
                }
                System.out.println("Background Executor shutdown complete.");
            } catch (InterruptedException e) {
                backgroundExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (!terminalConnections.isEmpty()) {
            System.out.println("Disconnecting active SSH sessions...");
            for (SSHManager ssh : terminalConnections.values()) {
                if (ssh != null) {
                    ssh.disconnect();
                }
            }
            terminalConnections.clear();
            System.out.println("SSH sessions disconnected.");
        }
        System.out.println("Resource shutdown process finished.");
    }


    private ImageView carregarAvatar(byte[] fotoBytes, int largura, int altura) {
        ImageView avatar;
        try {
            if (fotoBytes != null && fotoBytes.length > 0) {
                avatar = new ImageView(new Image(new ByteArrayInputStream(fotoBytes)));
            } else {
                InputStream stream = getClass().getResourceAsStream("/imagens/default-avatar.png");
                if (stream != null) {
                    avatar = new ImageView(new Image(stream));
                } else {
                    System.err.println("❌ Imagem padrão não encontrada em /imagens/default-avatar.png");
                    avatar = new ImageView();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao carregar avatar: " + e.getMessage());
            avatar = new ImageView();
        }

        avatar.setFitWidth(largura);
        avatar.setFitHeight(altura);
        avatar.setClip(new Circle(largura / 2.0, altura / 2.0, Math.min(largura, altura) / 2.0));
        return avatar;
    }
    // ------- Barra lateral direita (Usuarios)
    // ---------------------- BARRAS VERTICAIS ---------------------- //


    // ---------------------- OLTS ---------------------- //
    private Node createOLTScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(10);
        cardsPane.setVgap(10);
        cardsPane.setPadding(new Insets(20));
        cardsPane.setAlignment(Pos.TOP_CENTER);

        cardsPane.setPrefWrapLength(1000); // Initial value, will be adjusted
        cardsPane.setMaxWidth(Double.MAX_VALUE); // Allow flow pane to take max width
        cardsPane.getStyleClass().add("scroll-content");

        // Adjust prefWrapLength based on main content width for responsiveness
        mainContent.widthProperty().addListener((obs, oldVal, newVal) -> {
            double availableWidth = newVal.doubleValue() - (mainContent.getLeft() != null ? mainContent.getLeft().getBoundsInLocal().getWidth() : 0) - (mainContent.getRight() != null ? mainContent.getRight().getBoundsInLocal().getWidth() : 0) - 40; // Adjust for padding and sidebars
            if(availableWidth > 0) {
                // Calculate optimal columns based on available width and card width (approx 165 including gaps)
                int optimalColumns = Math.max(1, (int) (availableWidth / 165));
                cardsPane.setPrefWrapLength(optimalColumns * 165);
            }
        });


        for (OLT olt : OLTList.getOLTs()) {
            VBox card = createOLTCard(olt);
            cardsPane.getChildren().add(card);
        }

        VBox scrollContent = new VBox(cardsPane);
        scrollContent.setAlignment(Pos.TOP_CENTER);
        scrollContent.setPadding(new Insets(20, 40, 20, 40));
        scrollContent.setFillWidth(true);
        VBox.setVgrow(scrollContent, Priority.ALWAYS); // Allow scrollContent VBox to grow

        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true); // Fit to height of its content
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportHeight(400); // This might need adjustment
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setMaxHeight(Double.MAX_VALUE); // Allow scrollpane to take max height

        content.getChildren().addAll(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Platform.runLater(() -> {
            animateCardsSequentially(cardsPane.getChildren(), 50);
            cardsPane.toFront();
        });

        return content;
    }
    // ---------------------- OLTS ---------------------- //


    // ---------------------- CONSULTA DE SINAL ---------------------- //
    private Node createSignalQueryScreen() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 20, 0));
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow

        Label title = new Label("Consulta de Sinal Óptico");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER); // Center form content
        VBox.setVgrow(formArea, Priority.ALWAYS); // Allow formArea to grow

        Label infoLabel = new Label("Verifique o Sinal Óptico da Primária.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240); // Allow combo box to take max width

        TextField fsField = new TextField();
        fsField.setPromptText("Digite o F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115); // Allow text field to take max width

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115); // Allow text field to take max width

        HBox formRow = new HBox(10);
        formRow.setAlignment(Pos.CENTER);
        formRow.getChildren().addAll(fsField, pField);
        formRow.setMaxWidth(Double.MAX_VALUE); // Allow form row to take max width
        HBox.setHgrow(fsField, Priority.ALWAYS); // Allow fsField to grow
        HBox.setHgrow(pField, Priority.ALWAYS); // Allow pField to grow

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

        fsField.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    fsField.copy();
                } else if (event.getCode() == KeyCode.V) {
                    fsField.paste();
                }
            }
            if (event.getCode() == KeyCode.ENTER) {
                queryBtn.fire();
            }
        });

        pField.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    pField.copy();
                } else if (event.getCode() == KeyCode.V) {
                    pField.paste();
                }
            }
            if (event.getCode() == KeyCode.ENTER) {
                queryBtn.fire();
            }
        });

        CodeArea resultArea = new CodeArea();
        resultArea.setEditable(false);
        resultArea.getStyleClass().add("code-area");
        resultArea.setPrefHeight(350); // Initial pref height, but allow growth
        VBox.setVgrow(resultArea, Priority.ALWAYS); // Allow result area to grow

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140); // Allow button to take max width


        exportBtn.setOnAction(e -> {
            exportarResultado(resultArea, "Consulta_Sinal");
        });

        queryBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty()) {
                resultArea.replaceText(0, resultArea.getLength(),
                        "Por favor, preencha todos os campos corretamente.");
                return;
            }

            resultArea.replaceText(0, resultArea.getLength(), "Consultando, aguarde...");
            SSHManager sshManager = new SSHManager();
            sshManager.setConnectTimeout(10000);
            Thread queryThread = new Thread(() -> {
                try {
                    boolean connected = sshManager.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, null);
                    if (!connected) {
                        Platform.runLater(() -> {
                            resultArea.replaceText(0, resultArea.getLength(), "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.");
                        });
                        return;
                    }

                    String resultado = sshManager.queryOpticalSignal(fs, p);

                    Platform.runLater(() -> {
                        resultArea.replaceText(0, resultArea.getLength(), resultado);
                        destacarIPs(resultArea);
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultArea.replaceText(0, resultArea.getLength(), "Erro: " + ex.getMessage());
                        destacarIPs(resultArea);
                    });
                } finally {
                    sshManager.disconnect();
                    Platform.runLater(() -> {
                        showToast("🔎 Consulta de Sinal Óptico finalizada!");
                    });
                }
            });
            queryThread.setDaemon(true);
            queryThread.start();
        });

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                formRow,
                queryBtn,
                resultArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }

    private void exportarResultado(CodeArea resultadoArea, String nomeBase) {
        String resultado = resultadoArea.getText();
        if (resultado.isEmpty()) {
            resultadoArea.replaceText("Nada para exportar. Faça uma consulta primeiro.");
            return;
        }

        Dialog<String> exportDialog = new Dialog<>();
        exportDialog.initOwner(primaryStage);
        exportDialog.setTitle("Exportar");

        DialogPane dialogPane = exportDialog.getDialogPane();
        dialogPane.getStyleClass().add("dialog-pane");

        ButtonType csvButton = new ButtonType("CSV", ButtonBar.ButtonData.OK_DONE);
        ButtonType pdfButton = new ButtonType("PDF", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialogPane.getButtonTypes().addAll(csvButton, pdfButton, cancelButton);

        VBox dialogContent = new VBox(10);
        dialogContent.setStyle("-fx-padding: 10;");
        Label label = new Label("Será salvo na pasta /exports\nFormato de exportação:");
        label.getStyleClass().add("info-label");
        dialogContent.getChildren().add(label);
        dialogPane.setContent(dialogContent);

        exportDialog.setResultConverter(dialogBtn -> {
            if (dialogBtn == csvButton) return "CSV";
            if (dialogBtn == pdfButton) return "PDF";
            return null;
        });

        exportDialog.showAndWait().ifPresent(formato -> {
            switch (formato) {
                case "CSV":
                    exportarCSV(resultado, nomeBase);
                    break;
                case "PDF":
                    exportarPDF(resultado, nomeBase);
                    break;
            }
        });
    }

    // CSV
    private void exportarCSV(String texto, String nomeBase) {
        try {
            File dir = new File("exports");
            if (!dir.exists()) dir.mkdir();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String nomeArquivo = "exports/" + nomeBase + "_" + timestamp + ".csv";
            FileWriter writer = new FileWriter(nomeArquivo);

            for (String line : texto.split("\n")) {
                writer.append(line.replaceAll("\\s{2,}", ",").replaceAll("\\s", ",")).append("\n");
            }
            showToast("📂 Arquivo CSV exportado com sucesso!");
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // PDF
    private void exportarPDF(String texto, String nomeBase) {
        try {
            File dir = new File("exports");
            if (!dir.exists()) dir.mkdir();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String nomeArquivo = "exports/" + nomeBase + "_" + timestamp + ".pdf";

            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, new FileOutputStream(nomeArquivo));
            document.open();
            document.add(new com.lowagie.text.Paragraph(texto));
            document.close();
            showToast("📂 Arquivo PDF exportado com sucesso!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // ---------------------- CONSULTA DE SINAL ---------------------- //


    // ---------------------- PON SUMMARY ---------------------- //
    private Node createPONSummaryScreen() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 0, 20, 0));
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow

        Label title = new Label("Resumo da PON");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER); // Center form content
        VBox.setVgrow(formArea, Priority.ALWAYS); // Allow formArea to grow

        Label infoLabel = new Label("Verifique todas as informações da Primária.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240);

        TextField ponField = new TextField();
        ponField.setPromptText("Digite o F/S/P");
        ponField.getStyleClass().add("text-field");
        ponField.setMaxWidth(240);

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

        ponField.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    ponField.copy();
                } else if (event.getCode() == KeyCode.V) {
                    ponField.paste();
                }
            }
            if (event.getCode() == KeyCode.ENTER) {
                consultarBtn.fire();
            }
        });

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setPrefHeight(350); // Initial pref height, but allow growth
        VBox.setVgrow(resultadoArea, Priority.ALWAYS); // Allow result area to grow

        consultarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String pon = ponField.getText().trim();

            if (selectedOLT == null || pon.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, selecione a OLT e informe a PON.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Consultando, aguarde...");

            Thread thread = new Thread(() -> {
                SSHManager tempSSHManager = new SSHManager();
                CodeArea hiddenArea = new CodeArea();
                try {
                    boolean connected = tempSSHManager.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, hiddenArea);
                    if (!connected) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(0, resultadoArea.getLength(), "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.");
                        });
                    }

                    Platform.runLater(() -> hiddenArea.clear());

                    tempSSHManager.sendCommand("enable");
                    Thread.sleep(1000);
                    tempSSHManager.sendCommand("config");
                    Thread.sleep(1000);

                    final int[] startPos = {0};
                    Platform.runLater(() -> {
                        startPos[0] = hiddenArea.getLength();
                    });
                    Thread.sleep(100);

                    tempSSHManager.sendCommand("display ont info summary " + pon);

                    Thread.sleep(5000);

                    final String[] resultado = {""};
                    Platform.runLater(() -> {
                        String fullOutput = hiddenArea.getText();

                        String searchString = "display ont info summary " + pon;
                        int cmdIndex = fullOutput.indexOf(searchString, startPos[0]);

                        if (cmdIndex >= 0) {
                            int resultStartIndex = fullOutput.indexOf("\n", cmdIndex);
                            if (resultStartIndex >= 0) {
                                resultado[0] = fullOutput.substring(resultStartIndex + 1);
                            }
                        } else {
                            resultado[0] = "Erro: Não foi possível identificar a saída do comando.";
                        }

                        resultadoArea.replaceText(0, resultadoArea.getLength(), resultado[0]);
                        destacarIPs(resultadoArea);
                        showToast("🔎 Consulta de PON Summary finalizada!");
                    });

                    DatabaseManager.logUsuario(usuario.getNome(),
                            "Consultou resumo da PON " + pon + " na " + selectedOLT.name);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultadoArea.replaceText(0, resultadoArea.getLength(), "Erro: " + ex.getMessage());
                        destacarIPs(resultadoArea);
                        showToast("⚠️ Erro ao consultar PON Summary.");
                    });
                } finally {
                    tempSSHManager.disconnect();
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140); // Allow button to take max width


        exportBtn.setOnAction(e -> {
            exportarResultado(resultadoArea, "Resumo_PON");
        });

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                ponField,
                consultarBtn,
                resultadoArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- PON SUMMARY ---------------------- //


    // ---------------------- ONT/ONU BY-SN ---------------------- //
    private Node createONUBySNScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow

        Label title = new Label("Consulta ONT/ONU por SN");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER); // Center form content
        VBox.setVgrow(formArea, Priority.ALWAYS); // Allow formArea to grow


        Label infoLabel = new Label("Verifique todas as informações do SN.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240); // Allow combo box to take max width

        TextField snField = new TextField();
        snField.setPromptText("Digite o SN da ONT/ONU");
        snField.getStyleClass().add("text-field");
        snField.setMaxWidth(240); // Allow text field to take max width

        TextFormatter<String> snFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[A-Za-z0-9]{0,20}")) {
                return change;
            }
            return null;
        });
        snField.setTextFormatter(snFormatter);

        Button consultarBtn = new Button("Consultar");
        consultarBtn.getStyleClass().add("connect-btn");
        consultarBtn.setMaxWidth(140); // Allow button to take max width

        snField.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    snField.copy();
                } else if (event.getCode() == KeyCode.V) {
                    snField.paste();
                }
            }
            if (event.getCode() == KeyCode.ENTER) {
                consultarBtn.fire();
            }
        });

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setPrefHeight(350); // Initial pref height, but allow growth
        VBox.setVgrow(resultadoArea, Priority.ALWAYS); // Allow result area to grow


        consultarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String sn = snField.getText().trim();

            if (selectedOLT == null || sn.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, selecione a OLT e informe o SN do cliente.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Consultando, aguarde...");

            Thread thread = new Thread(() -> {
                SSHManager tempSSHManager = new SSHManager();
                CodeArea hiddenArea = new CodeArea();
                try {
                    boolean connected = tempSSHManager.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, hiddenArea);
                    if (!connected) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(0, resultadoArea.getLength(), "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.");
                        });
                        return;
                    }

                    Platform.runLater(() -> hiddenArea.clear());

                    tempSSHManager.sendCommand("enable");
                    Thread.sleep(1000);
                    tempSSHManager.sendCommand("config");
                    Thread.sleep(1000);

                    final int[] startPos = {0};
                    Platform.runLater(() -> {
                        startPos[0] = hiddenArea.getLength();
                    });
                    Thread.sleep(100);

                    tempSSHManager.sendCommand("display ont info by-sn " + sn);

                    Thread.sleep(12000);

                    final String[] resultado = {""};
                    Platform.runLater(() -> {
                        String fullOutput = hiddenArea.getText();

                        String searchString = "display ont info by-sn " + sn;
                        int cmdIndex = fullOutput.indexOf(searchString, startPos[0]);

                        if (cmdIndex >= 0) {
                            int resultStartIndex = fullOutput.indexOf("\n", cmdIndex);
                            if (resultStartIndex >= 0) {
                                resultado[0] = fullOutput.substring(resultStartIndex + 1);
                            }
                        } else {
                            resultado[0] = "Erro: Não foi possível identificar a saída do comando.";
                        }

                        resultadoArea.replaceText(0, resultadoArea.getLength(), resultado[0]);
                        destacarIPs(resultadoArea);
                        showToast("🔎 Consulta BY-SN finalizada!");
                    });

                    DatabaseManager.logUsuario(usuario.getNome(),
                            "Consultou ONT/ONU pelo SN " + sn + " na " + selectedOLT.name);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultadoArea.replaceText(0, resultadoArea.getLength(), "Erro: " + ex.getMessage());
                        destacarIPs(resultadoArea);
                        showToast("⚠️ Erro ao consultar BY-SN.");
                    });
                } finally {
                    tempSSHManager.disconnect();
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140); // Allow button to take max width


        exportBtn.setOnAction(e -> {
            exportarResultado(resultadoArea, "Consulta_SN");
        });

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                snField,
                consultarBtn,
                resultadoArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- ONT/ONU BY-SN ---------------------- //


    // ---------------------- ONT/ONU QUEDAS ---------------------- //
    private Node createDropDiagnosisScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow

        Label title = new Label("Diagnóstico de Quedas da ONT/ONU");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER); // Center form content
        VBox.setVgrow(formArea, Priority.ALWAYS); // Allow formArea to grow

        Label infoLabel = new Label("Verifique o diagnóstico de quedas da ONT/ONU.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240); // Allow combo box to take max width

        TextField fsField = new TextField();
        fsField.setPromptText("Digite F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115); // Allow text field to take max width

        TextField pidField = new TextField();
        pidField.setPromptText("Digite o P ID");
        pidField.getStyleClass().add("text-field");
        pidField.setMaxWidth(115); // Allow text field to take max width

        TextFormatter<String> fsFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9/]{0,4}")) {
                return change;
            }
            return null;
        });
        fsField.setTextFormatter(fsFormatter);

        TextFormatter<String> pidFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9 ]{0,6}")) {
                return change;
            }
            return null;
        });
        pidField.setTextFormatter(pidFormatter);

        HBox formRow = new HBox(10);
        formRow.setAlignment(Pos.CENTER);
        formRow.getChildren().addAll(fsField, pidField);
        formRow.setMaxWidth(Double.MAX_VALUE); // Allow form row to take max width
        HBox.setHgrow(fsField, Priority.ALWAYS); // Allow fsField to grow
        HBox.setHgrow(pidField, Priority.ALWAYS); // Allow pidField to grow


        Button diagnosticarBtn = new Button("Consultar");
        diagnosticarBtn.getStyleClass().add("connect-btn");
        diagnosticarBtn.setMaxWidth(140); // Allow button to take max width

        fsField.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    fsField.copy();
                } else if (event.getCode() == KeyCode.V) {
                    fsField.paste();
                }
            }
            if (event.getCode() == KeyCode.ENTER) {
                diagnosticarBtn.fire();
            }
        });

        pidField.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    pidField.copy();
                } else if (event.getCode() == KeyCode.V) {
                    pidField.paste();
                }
            }
                if (event.getCode() == KeyCode.ENTER) {
                    diagnosticarBtn.fire();
                }
        });



        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setPrefHeight(350); // Initial pref height, but allow growth
        VBox.setVgrow(resultadoArea, Priority.ALWAYS); // Allow result area to grow

        diagnosticarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String pid = pidField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || pid.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Preencha todos os campos corretamente.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Consultando, aguarde...");

            Thread thread = new Thread(() -> {
                SSHManager ssh = new SSHManager();
                CodeArea hiddenArea = new CodeArea();
                try {
                    boolean connected = ssh.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, hiddenArea);
                    if (!connected) {
                        Platform.runLater(() -> {
                            resultadoArea.replaceText(0, resultadoArea.getLength(), "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.");
                        });
                        return;
                    }

                    Platform.runLater(() -> hiddenArea.clear());

                    ssh.sendCommand("enable");
                    Thread.sleep(1000);
                    ssh.sendCommand("config");
                    Thread.sleep(1000);
                    ssh.sendCommand("interface gpon " + fs);
                    Thread.sleep(1000);

                    final int[] startPos = {0};
                    Platform.runLater(() -> {
                        startPos[0] = hiddenArea.getLength();
                    });
                    Thread.sleep(100);

                    ssh.sendCommand("display ont register-info " + pid);

                    Thread.sleep(5000);

                    final String[] resultado = {""};
                    Platform.runLater(() -> {
                        String fullOutput = hiddenArea.getText();

                        String searchString = "display ont register-info " + pid;
                        int cmdIndex = fullOutput.indexOf(searchString, startPos[0]);

                        if (cmdIndex >= 0) {
                            int resultStartIndex = fullOutput.indexOf("\n", cmdIndex);
                            if (resultStartIndex >= 0) {
                                resultado[0] = fullOutput.substring(resultStartIndex + 1);
                            }
                        } else {
                            resultado[0] = "Erro: Não foi possível identificar a saída do comando.";
                        }

                        resultadoArea.replaceText(0, resultadoArea.getLength(), resultado[0]);
                        destacarIPs(resultadoArea);
                        showToast("🔎 Consulta de Quedas finalizada!");
                    });

                    DatabaseManager.logUsuario(usuario.getNome(),
                            "Consultou diagnóstico de quedas da ONT/ONU " + fs + "/" + pid + " na " + selectedOLT.name);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultadoArea.replaceText(0, resultadoArea.getLength(), "Erro: " + ex.getMessage());
                        destacarIPs(resultadoArea);
                        showToast("⚠️ Erro ao consultar Quedas.");
                    });
                } finally {
                    ssh.disconnect();
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140); // Allow button to take max width


        exportBtn.setOnAction(e -> {
            exportarResultado(resultadoArea, "Diagnostico_Quedas");
        });

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                formRow,
                diagnosticarBtn,
                resultadoArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- ONT/ONU QUEDAS ---------------------- //


    // ---------------------- CHAMADOS  ---------------------- //
    private Node createTechnicalTicketsScreen() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("content-area");
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow

        Label title = new Label("Chamados");
        title.getStyleClass().add("title");

        TableView<Ticket> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setItems(FXCollections.observableArrayList(DatabaseManager.getAllTickets()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS); // Allow table to grow


        table.getColumns().addAll(
                createColumn("Criado por", "criadoPor"),
                createColumn("Cargo", "cargo"),
                createColumn("Descrição do Problema", "descricao"),
                createColumn("Prioridade", "previsao"),
                createColumn("Data/Hora", "dataHora"),
                createColumn("Status", "status")
        );

        if (usuario.getUsuario().equalsIgnoreCase("Eduardo")) {
            TableColumn<Ticket, Void> actionCol = new TableColumn<>("Ação");

            actionCol.setCellFactory(col -> new TableCell<>() {
                private final Button deleteBtn = new Button("Excluir");

                {
                    deleteBtn.getStyleClass().addAll("window-close-button", "small-delete-btn");
                    deleteBtn.setOnAction(e -> {
                        Ticket selected = getTableView().getItems().get(getIndex());
                        if (selected != null) {
                            boolean confirm = showConfirmation("Deseja excluir o chamado?");
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
                        setGraphic(deleteBtn);
                    }
                }
            });

            table.getColumns().add(actionCol);
        }

        content.getChildren().addAll(title, table);
        return content;
    }

    private <T> TableColumn<Ticket, String> createColumn(String title, String prop) {
        TableColumn<Ticket, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        return col;
    }

    private boolean showConfirmation(String msg) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmação");
        alert.setHeaderText(null);
        alert.setContentText(msg);

        ButtonType yes = new ButtonType("Sim", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("Não", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(yes, no);

        return alert.showAndWait().orElse(no) == yes;
    }
    // ---------------------- CHAMADOS ---------------------- //


    // ---------------------- ANIMAÇÕES JAVAFX ---------------------- //
    private void setupWindowDrag(Node node) {
        node.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        node.setOnMouseDragged(event -> {
            if (!primaryStage.isMaximized()) {
                primaryStage.setX(event.getScreenX() - xOffset);
                primaryStage.setY(event.getScreenY() - yOffset);
            }
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
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(5, 10, 5, 15));
        HBox.setHgrow(titleBar, Priority.ALWAYS); // Allow titleBar to grow


        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            if (!primaryStage.isMaximized()) {
                primaryStage.setX(event.getScreenX() - xOffset);
                primaryStage.setY(event.getScreenY() - yOffset);
            }
        });

        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximize();
            }
        });

        titleBarIconView = new ImageView();
        titleBarIconView.setFitHeight(20);
        titleBarIconView.setFitWidth(20);
        titleBarIconView.setPreserveRatio(true);

        titleBarIconView.imageProperty().addListener((obs, oldImage, newImage) -> {
            if (newImage != null) {
                if (newImage.isError()) {
                    System.err.println("ImageView: Erro ao carregar a imagem definida: " + newImage.getException());
                } else {
                    System.out.println("ImageView: Nova imagem definida com sucesso.");
                }
            } else {
                System.out.println("ImageView: Imagem removida.");
            }
        });


        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone da barra de título: " + iconFileName + ". Usando fallback.");
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }

            if (iconStream != null) {
                Image titleBarImage = new Image(iconStream);

                titleBarImage.exceptionProperty().addListener((obs, oldEx, newEx) -> {
                    if (newEx != null) {
                        System.err.println("Image Load: Exceção durante o carregamento assíncrono: " + newEx);
                    }
                });
                titleBarImage.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                    if (newProgress.doubleValue() == 1.0) {
                        if (titleBarImage.isError()) {
                            System.err.println("Image Load: Erro final no carregamento da imagem. Exception: " + titleBarImage.getException());
                        } else {
                            System.out.println("Image Load: Carregamento concluído com sucesso.");
                            Platform.runLater(() -> titleBarIconView.setImage(titleBarImage));
                        }
                    }
                });

                if (titleBarImage.isError()) {
                    System.err.println("Image Load: Erro imediato após a criação da imagem. Exception: " + titleBarImage.getException());
                } else if (titleBarImage.getProgress() == 1.0) {
                    System.out.println("Image Load: Carregada síncronamente com sucesso.");
                    titleBarIconView.setImage(titleBarImage);
                }

            } else {
                System.err.println("Ícone da barra de título (incluindo fallback) não encontrado.");

            }
        } catch (Exception e) {
            System.err.println("Erro geral ao carregar o ícone da barra de título: " + e.getMessage());

        }

        Label titleLabel = new Label("Gerenciador de OLTs");
        titleLabel.getStyleClass().add("olt-name");
        HBox.setHgrow(titleLabel, Priority.ALWAYS); // Allow titleLabel to grow

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox windowControls = new HBox(5);
        windowControls.getStyleClass().add("window-controls");

        Button minimizeBtn = new Button();
        minimizeBtn.getStyleClass().addAll("window-btn", "minimize-btn");
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));
        minimizeBtn.setTooltip(new Tooltip("Minimizar"));

        // Add Maximize Button
        Button maximizeBtn = new Button();
        maximizeBtn.getStyleClass().addAll("window-btn", "maximize-btn");
        maximizeBtn.setOnAction(e -> toggleMaximize());
        maximizeBtn.setTooltip(new Tooltip("Maximizar/Restaurar"));


        Button closeBtn = new Button();
        closeBtn.getStyleClass().addAll("window-btn", "close-btn");
        closeBtn.setOnAction(e -> {
            Platform.runLater(() -> {
                FadeTransition fade = new FadeTransition(Duration.millis(200), rootLayout);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(event -> {
                    System.out.println("FadeTransition concluído, chamando Platform.exit()...");
                    Platform.exit();
                });
                fade.play();
            });
        });
        closeBtn.setTooltip(new Tooltip("Fechar"));

        windowControls.getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn); // Added maximizeBtn

        titleBar.getChildren().addAll(titleBarIconView, titleLabel, spacer, windowControls);

        HBox.setMargin(titleBarIconView, new Insets(0, 8, 0, 0));

        return titleBar;
    }

    private void toggleMaximize() {
        if (primaryStage.isMaximized()) {
            primaryStage.setMaximized(false);
        } else {
            primaryStage.setMaximized(true);
        }
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
        VBox card = new VBox(6);
        card.getStyleClass().add("olt-card");
        card.setAlignment(Pos.CENTER);

        double cardWidth = 155;
        double cardHeight = 110;
        card.setPrefSize(cardWidth, cardHeight);
        card.setMaxWidth(cardWidth); // Keep max width fixed for cards in FlowPane
        card.setMaxHeight(cardHeight); // Keep max height fixed for cards


        Rectangle clip = new Rectangle(cardWidth, cardHeight);
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
            DatabaseManager.logUsuario(usuario.getNome(), "Conectou na " + olt.name);
            clickEffect.play();
        });

        addEnhancedButtonHoverEffects(connectBtn);

        card.setOnMouseEntered(e -> {
            TranslateTransition lift = new TranslateTransition(Duration.millis(200), card);
            lift.setToY(-5);
            lift.play();

            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1.03);
            scale.setToY(1.03);
            scale.play();
        });

        card.setOnMouseExited(e -> {
            TranslateTransition drop = new TranslateTransition(Duration.millis(200), card);
            drop.setToY(0);
            drop.play();

            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
        });

        card.getChildren().addAll(nameLabel, ipLabel, connectBtn);
        return card;
    }
    // ---------------------- ANIMAÇÕES JAVAFX ---------------------- //



    // ---------------------- INSIDE-TERMINAL ---------------------- //
    private void showSSHTerminal(OLT olt) {

        if (terminalTabs == null) {
            terminalTabs = new TabPane();
            terminalTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            terminalTabs.setTabMinWidth(150);
            Tab oltsTab = new Tab("Lista de OLTs");
            oltsTab.setClosable(false);

            Node oltsContent = createOLTScreen();
            oltsTab.setContent(oltsContent);

            terminalTabs.getTabs().add(oltsTab);
        }

        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(20));
        VBox.setVgrow(content, Priority.ALWAYS); // Allow content VBox to grow


        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(header, Priority.ALWAYS); // Allow header HBox to grow

        Label title = new Label("Terminal - " + olt.name);
        title.getStyleClass().add("title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("Voltar para OLTs");
        backBtn.getStyleClass().add("back-btn");
        backBtn.setOnAction(e -> {
            Tab currentTab = terminalTabs.getSelectionModel().getSelectedItem();
            terminalTabs.getSelectionModel().select(0);

            if (currentTab != null && !currentTab.getText().equals("Lista de OLTs")) {
                SSHManager ssh = terminalConnections.remove(currentTab);
                if (ssh != null) {
                    ssh.disconnect();
                }
                terminalTabs.getTabs().remove(currentTab);
            }

            if (terminalTabs.getTabs().size() <= 1 && terminalTabs.getTabs().get(0).getText().equals("Lista de OLTs")) {
                if(mainContent.getCenter() != terminalTabs) {
                    showSection("OLTs");
                }
            }
        });


        header.getChildren().addAll(title, spacer, backBtn);

        VBox terminalBox = new VBox(10);
        terminalBox.getStyleClass().add("terminal-box");
        terminalBox.setPadding(new Insets(10));
        VBox.setVgrow(terminalBox, Priority.ALWAYS);

        CodeArea newTerminalArea = new CodeArea();
        newTerminalArea.getStyleClass().add("terminal-area");
        newTerminalArea.setEditable(false);
        newTerminalArea.setWrapText(true);
        newTerminalArea.setPrefHeight(300); // Initial pref height, but allow growth
        VBox.setVgrow(newTerminalArea, Priority.ALWAYS); // Allow terminal area to grow

        HBox commandArea = new HBox(10);
        commandArea.setAlignment(Pos.CENTER);
        commandArea.setPadding(new Insets(10, 0, 0, 0));
        HBox.setHgrow(commandArea, Priority.ALWAYS); // Allow commandArea to grow


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
        HBox.setHgrow(quickActions, Priority.ALWAYS); // Allow quickActions to grow


        Button clearBtn = new Button("Limpar");
        clearBtn.getStyleClass().add("action-btn");
        Button helpBtn = new Button("Ajuda");
        helpBtn.getStyleClass().add("action-btn");


        quickActions.getChildren().addAll(clearBtn, helpBtn);

        terminalBox.getChildren().addAll(newTerminalArea, commandArea, quickActions);
        content.getChildren().addAll(header, terminalBox);
        VBox.setVgrow(terminalBox, Priority.ALWAYS); // Allow terminalBox to grow


        Tab terminalTab = new Tab(olt.name, content);
        terminalTab.setClosable(true);

        SSHManager newSSHManager = new SSHManager();
        terminalConnections.put(terminalTab, newSSHManager);

        terminalTab.setOnClosed(e -> {
            SSHManager ssh = terminalConnections.remove(terminalTab);
            if (ssh != null) {
                ssh.disconnect();
            }
            if (terminalTabs != null && terminalTabs.getTabs().size() == 1 && terminalTabs.getTabs().get(0).getText().equals("Lista de OLTs")) {
                terminalTabs.getSelectionModel().select(0);
            }
        });

        terminalTabs.getTabs().add(terminalTab);
        terminalTabs.getSelectionModel().select(terminalTab);

        Node currentContent = mainContent.getCenter();
        if (!(currentContent instanceof TabPane) || currentContent != terminalTabs) {
            if (currentContent != null) {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(200), currentContent);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(ev -> {
                    mainContent.setCenter(terminalTabs);
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(400), terminalTabs);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                    Platform.runLater(commandField::requestFocus);
                });
                fadeOut.play();
            } else {
                mainContent.setCenter(terminalTabs);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(400), terminalTabs);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
                Platform.runLater(commandField::requestFocus);
            }
        } else {
            Platform.runLater(commandField::requestFocus);
        }
        currentSection = (olt.name);

        Thread connectThread = new Thread(() -> {
            try {
                final CodeArea terminalAreaForThread = newTerminalArea;
                Platform.runLater(() -> terminalAreaForThread.appendText("Conectando a " + olt.name + " (" + olt.ip + ")...\n"));
                newSSHManager.connect(olt.ip, Secrets.SSH_USER, Secrets.SSH_PASS, terminalAreaForThread);
                Platform.runLater(commandField::requestFocus);
            } catch (Exception e) {
                final CodeArea terminalAreaForThread = newTerminalArea;
                final String errorMsg = e.getMessage();
                Platform.runLater(() -> terminalAreaForThread.appendText("\nErro ao conectar: " + errorMsg + "\n"));
                Platform.runLater(commandField::requestFocus);
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();

        List<String> commandHistory = new ArrayList<>();
        int[] commandHistoryIndex = {-1};

        List<String> huaweiOltCommands = new ArrayList<>(Arrays.asList(
                // COMANDOS OLT Huawei MA5800/MA6800
                "enable", "config", "display", "interface", "quit", "save", "reboot",
                "undo", "commit", "compare configuration", "copy", "delete", "dir",
                "ping", "tracert", "ssh", "telnet", "scroll", "language", "idle-timeout",
                "system", "super", "patch activate", "patch load", "patch delete",
                "backup configuration", "backup data", "restore configuration", "restore data",
                "display version", "display board", "display device", "display time",
                "display users", "display history-command", "display current-configuration",
                "display saved-configuration", "display startup", "display log all",
                "display log buffer", "display alarm active", "display alarm history",
                "display cpu-usage", "display memory-usage", "display temperature",
                "display fan", "display power", "display environment", "display ip interface brief",
                "display ip routing-table", "display arp", "display arp all",
                "display mac-address", "display mac-address dynamic", "display mac-address static",
                "display vlan", "display vlan all", "display vlan summary", "display vlan port",
                "display port state", "display port statistics", "display port desc",
                "display traffic table ip", "display traffic table ip all", "display qos profile",
                "display qos profile all", "display ont-srvprofile gpon",
                "display ont-lineprofile gpon", "display dba-profile", "display dba-profile all",
                "display ont info summary", "display ont info by-sn", "display ont info by-loid",
                "display ont info by-ip", "display ont info by-mac", "display ont info",
                "display ont version", "display ont capability", "display ont status",
                "display ont wan-info", "display ont iptv-info", "display ont voip-info",
                "display ont alarm-info", "display ont alarm-profile", "display ont autofind all",
                "display ont optical-info", "display ont register-info", "display ont traffic",
                "display ont port state", "display ont port statistics", "display ont video-service-info",
                "display service-port all", "display service-port", "display service-port port",
                "display service-port vlan", "display service-port index", "display service-port statistics",
                "display snmp-agent sys-info", "display ntp-service status", "display ssh server status",
                "display telnet server status", "display load balancing", "display patch information",
                "display elabel",

                "interface", "vlan", "port vlan", "traffic table ip", "undo traffic table",
                "qos profile", "undo qos profile", "dba-profile", "undo dba-profile",
                "ont-srvprofile gpon", "undo ont-srvprofile", "ont-lineprofile gpon",
                "undo ont-lineprofile", "ont alarm-profile", "undo ont alarm-profile",
                "snmp-agent", "ntp-service", "ssh server", "telnet server", "user-interface vty",
                "aaa", "terminal user", "systemname", "time-zone", "syslog-server",
                "header login", "mac-address static", "arp static", "ip route-static",
                "security", "load balancing",

                "display this", "port", "description", "shutdown", "undo shutdown", "speed",
                "duplex", "port vlan", "port default vlan", "port hybrid vlan", "port link-type",
                "mac-limit maximum", "broadcast-suppression", "multicast-suppression",
                "unicast-suppression", "qos apply", "trust", "stp", "loop-detect",

                "port <port_id> ont add", "port <port_id> ont delete", "port <port_id> ont modify",
                "port <port_id> ont confirm", "ont add", "ont delete", "ont modify", "ont confirm",
                "ont port attribute", "ont ipconfig", "ont wan-config", "ont internet-config",
                "ont voice-config", "ont video-config", "ont multicast-forward",
                "service-port", "undo service-port", "display ont info", "display ont optical-info",
                "display ont register-info", "display ont traffic", "ont auto-learn",
                "ont-interconnection enable", "laser", "optical-alarm-profile", "power-saving",

                "flow-control", "jumboframe", "auto-neg",

                "by-sn", "summary", "all", "port", "vlan", "ont", "ip", "index", "profile",
                "gpon", "ethernet", "eth", "pots", "veip", "gemport",
                "sn-auth", "loid-auth", "password-auth", "omci", "up-stream", "down-stream",
                "cir", "pir", "cbs", "pbs", "priority", "weight", "queue",
                "profile-id", "profile-index", "profile-name",
                "smart", "standard", "mux", "qinq", "stacking",
                "access", "trunk", "hybrid", "tagged", "untagged",
                "enable", "disable", "on", "off",
                "add", "delete", "modify", "confirm",
                "dhcp", "static", "pppoe",
                "active", "history", "brief", "dynamic", "static", "configuration", "state",
                "statistics", "version", "capability", "wan-info", "optical-info", "register-info",
                "autofind"
                // COMANDOS OLT Huawei MA5800/MA6800
        ));

        sendBtn.setOnAction(e -> {
            String cmd = commandField.getText().trim();
            if (!cmd.isEmpty()) {
                newSSHManager.sendCommand(cmd);
                DatabaseManager.logUsuario(usuario.getNome(), "Executou comando no terminal: " + cmd);
                commandHistory.add(cmd);
                commandHistoryIndex[0] = commandHistory.size();
                commandField.clear();

                newTerminalArea.moveTo(newTerminalArea.getLength());
                newTerminalArea.requestFollowCaret();

                commandField.requestFocus();
            }
        });

        commandField.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER:
                    sendBtn.fire();
                    break;
                case UP:
                    if (!commandHistory.isEmpty()) {
                        if (commandHistoryIndex[0] == commandHistory.size()) {
                            commandHistoryIndex[0] = commandHistory.size() - 1;
                        } else if (commandHistoryIndex[0] > 0) {
                            commandHistoryIndex[0]--;
                        }
                        if (commandHistoryIndex[0] >= 0 && commandHistoryIndex[0] < commandHistory.size()) {
                            commandField.setText(commandHistory.get(commandHistoryIndex[0]));
                            commandField.positionCaret(commandField.getText().length());
                        }
                    }
                    e.consume();
                    break;
                case DOWN:
                    if (!commandHistory.isEmpty()) {
                        if (commandHistoryIndex[0] >= -1 && commandHistoryIndex[0] < commandHistory.size() - 1) {
                            commandHistoryIndex[0]++;
                            commandField.setText(commandHistory.get(commandHistoryIndex[0]));
                            commandField.positionCaret(commandField.getText().length());
                        } else {
                            commandHistoryIndex[0] = commandHistory.size();
                            commandField.clear();
                        }
                    }
                    e.consume();
                    break;
                case TAB:
                    e.consume();

                    String currentText = commandField.getText();
                    if (currentText == null || currentText.trim().isEmpty()) {
                        break;
                    }

                    String trimmedText = currentText.trim();
                    List<String> matches = huaweiOltCommands.stream()
                            .filter(cmd -> cmd.toLowerCase().startsWith(trimmedText.toLowerCase()))
                            .collect(Collectors.toList());

                    String textToSet = null;

                    if (matches.size() == 1) {
                        String completion = matches.get(0);
                        if (!currentText.equals(completion)) {
                            textToSet = completion;
                        } else {
                            Platform.runLater(() -> {
                                commandField.deselect();
                                commandField.requestFocus();
                            });
                        }
                    } else if (matches.size() > 1) {
                        String commonPrefix = findCommonPrefix(matches);
                        if (commonPrefix != null && !commonPrefix.isEmpty() && commonPrefix.length() > currentText.length()) {
                            textToSet = commonPrefix;
                        } else {
                            final CodeArea terminalAreaForTab = newTerminalArea;
                            final List<String> finalMatches = matches;
                            final String currentInputText = commandField.getText();
                            Platform.runLater(() -> {
                                terminalAreaForTab.appendText("\n");
                                for(String match : finalMatches) {
                                    terminalAreaForTab.appendText(match + "  ");
                                }
                                terminalAreaForTab.appendText("\n> " + currentInputText);
                                terminalAreaForTab.moveTo(terminalAreaForTab.getLength());
                                terminalAreaForTab.requestFollowCaret();
                                commandField.requestFocus();
                                commandField.positionCaret(currentInputText.length());
                                commandField.deselect();
                            });
                        }
                    } else {
                        commandField.requestFocus();
                    }

                    if (textToSet != null) {
                        final String finalTextToSet = textToSet;
                        commandField.setText(finalTextToSet);

                        Platform.runLater(() -> {
                            System.out.println("runLater: Attempting to position caret, deselect, and focus for: " + finalTextToSet);
                            commandField.positionCaret(finalTextToSet.length());
                            commandField.deselect();
                            commandField.requestFocus();
                            System.out.println("runLater: Caret: " + commandField.getCaretPosition() + ", Selected: '" + commandField.getSelectedText() + "', Focused: " + commandField.isFocused());
                        });
                    }
                    break;
            }
        });


        clearBtn.setOnAction(e -> {
            newTerminalArea.clear();
            commandField.requestFocus();
        });

        helpBtn.setOnAction(e -> {
            showHelpDialog();
        });
    }

    private String findCommonPrefix(List<String> strings) {
        if (strings == null || strings.isEmpty()) return "";
        if (strings.size() == 1) return strings.get(0);

        String firstStr = strings.get(0).toLowerCase();
        int prefixLength = firstStr.length();

        for (int i = 1; i < strings.size(); i++) {
            String currentStr = strings.get(i).toLowerCase();
            int currentMatchLength = 0;
            while (currentMatchLength < prefixLength && currentMatchLength < currentStr.length() &&
                    firstStr.charAt(currentMatchLength) == currentStr.charAt(currentMatchLength)) {
                currentMatchLength++;
            }
            prefixLength = currentMatchLength;
            if (prefixLength == 0) break;
        }


        return strings.get(0).substring(0, prefixLength);
    }
// ---------------------- INSIDE-TERMINAL ---------------------- //




    // ---------------------- TRATAMENTO IP, TOAST e CRÉDITOS ---------------------- //
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
        Stage creditsStage = new Stage();
        creditsStage.initStyle(StageStyle.UNDECORATED);
        creditsStage.initOwner(primaryStage);

        VBox content = new VBox(20);
        content.getStyleClass().add("glass-pane");
        content.setPadding(new Insets(25));
        content.setPrefSize(500, 400);
        content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(5, 10, 5, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setOnAction(ev -> creditsStage.close());
        addEnhancedButtonHoverEffects(closeBtn);

        titleBar.getChildren().addAll(spacer, closeBtn);

        VBox creditsContent = new VBox(15);
        creditsContent.setAlignment(Pos.TOP_LEFT);
        creditsContent.setPadding(new Insets(10));

        Label appName = new Label("Gerenciador de OLTs - 1.5.4.0");
        appName.getStyleClass().add("credits-title");

        Label developer = new Label("Desenvolvido por Eduardo Tomaz");
        developer.getStyleClass().add("credits-text");


        VBox socialLinks = new VBox(5);
        socialLinks.setAlignment(Pos.TOP_LEFT);

        Hyperlink socialLink1 = new Hyperlink("LinkedIn");
        socialLink1.getStyleClass().add("credits-link");
        socialLink1.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.linkedin.com/in/eduardotoomazs/"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        Hyperlink socialLink2 = new Hyperlink("Instagram");
        socialLink2.getStyleClass().add("credits-link");
        socialLink2.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.instagram.com/tomazdudux/"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


        VBox githubLinks = new VBox(5);
        githubLinks.setAlignment(Pos.TOP_LEFT);

        Hyperlink githubLink1 = new Hyperlink("Repositório Github Windows");
        githubLink1.getStyleClass().add("credits-link");
        githubLink1.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/toomazs/NM-OLT-App"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        Hyperlink githubLink2 = new Hyperlink("Repositório Github Linux");
        githubLink2.getStyleClass().add("credits-link");
        githubLink2.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/toomazs/NM-OLT-App-Linux"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        githubLinks.getChildren().addAll(githubLink1, githubLink2);
        socialLinks.getChildren().addAll(socialLink1, socialLink2);

        creditsContent.getChildren().addAll(appName, developer, socialLinks, githubLinks);

        content.getChildren().addAll(titleBar, creditsContent);

        Scene scene = new Scene(content);
        ThemeManager.applyThemeToNewScene(scene);

        creditsStage.setScene(scene);
        creditsStage.show();

        creditsStage.centerOnScreen();
    }

    private void showToast(String message) {
        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-text-fill: white; -fx-padding: 10px 20px; -fx-background-radius: 20px;");
        toastLabel.setOpacity(0);

        StackPane root = (StackPane) primaryStage.getScene().getRoot();
        root.getChildren().add(toastLabel);

        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toastLabel, new Insets(0, 0, 80, 0));

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toastLabel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.seconds(2.5));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toastLabel);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        fadeOut.setOnFinished(e -> root.getChildren().remove(toastLabel));

        SequentialTransition seq = new SequentialTransition(fadeIn, stay, fadeOut);
        seq.play();
    }
    // ---------------------- TRATAMENTO IP, TOAST e CRÉDITOS ---------------------- //


    // ---------------------- AJUDA INSIDE-TERMINAL ---------------------- //
    private void showHelpDialog() {
        Stage helpStage = new Stage();
        helpStage.initStyle(StageStyle.UNDECORATED);
        helpStage.initOwner(primaryStage);

        VBox helpContent = new VBox(15);
        helpContent.getStyleClass().add("help-content");
        helpContent.setPadding(new Insets(10));
        helpContent.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.5)));

        VBox commandsBox = new VBox(8);
        commandsBox.getStyleClass().add("commands-box");
        commandsBox.setPadding(new Insets(10));
        commandsBox.setMaxWidth(Double.MAX_VALUE); // Allow commandsBox to take max width


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
        basicCommands.setMaxWidth(Double.MAX_VALUE); // Allow basicCommands to take max width


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
        oltCommands.setMaxWidth(Double.MAX_VALUE); // Allow oltCommands to take max width


        commandsBox.getChildren().addAll(basicLabel, basicCommands, oltLabel, oltCommands);

        Button closeBtn = new Button("Fechar");
        closeBtn.getStyleClass().add("help-close-btn");
        closeBtn.setOnAction(e -> helpStage.close());
        closeBtn.setMaxWidth(Double.MAX_VALUE); // Allow closeBtn to take max width


        helpContent.getChildren().addAll(commandsBox, closeBtn);

        Scene helpScene = new Scene(helpContent, 550, 500);
        ThemeManager.applyThemeToNewScene(helpScene);

        helpStage.setScene(helpScene);
        helpStage.show();
    }

    // ---------------------- AJUDA INSIDE-TERMINAL ---------------------- //



    public static void main(String[] args) {
        launch(args);
    }



    // ---------------------- STOPS ---------------------- //
    @Override
    public void stop() {
        System.out.println("Aplicação parando (método stop())...");

        if (usuarioLogado != null) {
            System.out.println("Atualizando status do usuário para offline em stop()...");
            try {
                Thread dbUpdateThread = new Thread(() -> {
                    try {
                        DatabaseManager.atualizarStatus(usuarioLogado.getUsuario(), "offline");
                        System.out.println("Status do usuário atualizado em stop().");
                    } catch (Exception dbEx) {
                        System.err.println("Erro na thread de atualização de status do DB em stop(): " + dbEx.getMessage());
                        dbEx.printStackTrace();
                    }
                });
                dbUpdateThread.setDaemon(true);
                dbUpdateThread.start();
            } catch (Exception e) {
                System.err.println("Erro ao criar thread para atualização de status em stop(): " + e.getMessage());
                e.printStackTrace();
            }
        }

        removeSystemTrayIcon();
        System.out.println("Ícone da bandeja do sistema solicitado para remoção.");


        shutdownApplicationResources();


        if (breakageMonitor != null) {
            breakageMonitor.shutdownNow();
            try {
                if (!breakageMonitor.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("breakageMonitor não encerrou em tempo.");
                    breakageMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("breakageMonitor interrompido durante o encerramento.");
                Thread.currentThread().interrupt();
            }
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("scheduler não encerrou em tempo.");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("scheduler interrompido durante o encerramento.");
                Thread.currentThread().interrupt();
            }
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
            try {
                if (!backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("backgroundExecutor não encerrou em tempo.");
                    backgroundExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                backgroundExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Método stop concluído.");
        closeFileLogging();
    }
}
// ---------------------- STOPS ---------------------- //