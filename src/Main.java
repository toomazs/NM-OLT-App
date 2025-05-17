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
import java.nio.file.Files;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.FileOutputStream;
import java.io.PrintStream;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import utils.WindowsUtils;
import javafx.beans.value.ChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.TimerTask;
import java.util.Timer;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;


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
    private BorderPane mainContent;
    private ToggleGroup navGroup;
    private String currentSection = null;
    private Map<String, Node> contentCache = new HashMap<>();
    private boolean isConnectedToOLT = false;
    private OLT connectedOLT;
    private ConfigManager configManager = ConfigManager.getInstance();
    private TabPane terminalTabs;
    private Map<Tab, SSHManager> terminalConnections = new HashMap<>();
    private ImageView titleBarIconView;
    private Node statusSidebar;
    private TrayIcon trayIcon;
    private String iconFileName;
    private PrintStream fileLogger;
    private static final String DEBUG_LOG_FILE = "debug.log";
    private static final String LOG_DIRECTORY = "logs";


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

            this.primaryStage = primaryStage;
            primaryStage.initStyle(StageStyle.UNDECORATED);

            String initialTheme = configManager.getTheme();
            fileLogger.println("DEBUG (Main): Tema inicial configurado: " + initialTheme);

            this.iconFileName = ThemeManager.getIconFileNameForTheme(initialTheme);
            fileLogger.println("DEBUG (Main): Nome do arquivo do ícone inicial: " + this.iconFileName);

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

            HBox titleBar = createTitleBar();
            rootLayout.getChildren().add(titleBar);

            mainContent = new BorderPane();

            VBox.setVgrow(mainContent, Priority.ALWAYS);
            mainContent.setLeft(createSideNavigation());
            mainContent.setRight(statusSidebar);

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

                KeyValue widthValue = new KeyValue(criarTicketBtn.prefWidthProperty(), 48, Interpolator.EASE_BOTH);
                KeyFrame keyFrame = new KeyFrame(Duration.millis(300), widthValue);
                timeline.getKeyFrames().add(keyFrame);
                timeline.play();
            });

            criarTicketBtn.setOnAction(e -> {
                Stage ticketStage = new Stage();
                ticketStage.initStyle(StageStyle.UNDECORATED);
                ticketStage.initOwner(primaryStage);

                VBox content = new VBox(15);
                content.getStyleClass().add("glass-pane");
                content.setPadding(new Insets(15));
                content.setPrefSize(500, 450);
                content.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3)));

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
                content.getChildren().addAll(ticketTitleBar, descLabel, descricaoArea, prioridadeLabel, prioridadeBox, infoLabel, btnRow);

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
            });
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

        } catch (Exception e) {
            fileLogger.println("ERRO (Main): Exceção geral no método start: " + e.getMessage());
            e.printStackTrace(fileLogger);

            Platform.exit();
        }
    }

    // Debug log
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


    // ---------------------- BARRA VERTICAL ---------------------- //
    private VBox createSideNavigation() {
        VBox sideNav = new VBox(10);
        sideNav.getStyleClass().add("side-nav");
        sideNav.setPrefWidth(200);
        sideNav.setMaxWidth(250);
        sideNav.setPadding(new Insets(20, 0, 20, 0));

        HBox versionBox = new HBox();
        versionBox.setAlignment(Pos.CENTER_LEFT);
        versionBox.setPadding(new Insets(0, 0, 10, 15));

        Label versionLabel = new Label("v1.5.5.0 • ");
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
        ToggleButton signalBtn = createNavButton("PON Consulta de Sinal", false);
        ToggleButton ponSummaryBtn = createNavButton("PON Summary", false);
        ToggleButton onuBySNBtn = createNavButton("ONT/ONU By-SN", false);
        ToggleButton diagnosisBtn = createNavButton("ONT/ONU Quedas", false);
        ToggleButton trafficBtn = createNavButton("ONT/ONU Velocidade", false);
        ToggleButton serviceBtn = createNavButton("ONT/ONU Serviços", false);

        sideNav.getChildren().addAll(oltBtn, signalBtn, ponSummaryBtn, onuBySNBtn, diagnosisBtn, trafficBtn, serviceBtn);

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


        Button themeBtn = new Button("🎨 Temas");
        themeBtn.getStyleClass().add("logout-button");
        themeBtn.setMaxWidth(Double.MAX_VALUE);
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
                }
                return;
            } else {
                Node currentContent = mainContent.getCenter();
                mainContent.setCenter(terminalTabs);
                if (!section.equals(currentSection)) {
                    currentSection = section;
                }

                if (currentContent != null) {
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(200), currentContent);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(e -> {
                        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), terminalTabs);
                        fadeIn.setFromValue(0.0);
                        fadeIn.setToValue(1.0);
                        fadeIn.play();
                    });
                    fadeOut.play();
                } else {
                    terminalTabs.setOpacity(0);
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), terminalTabs);
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

        Node currentContent = mainContent.getCenter();
        Node newContent = contentCache.get(section);

        if (newContent == null) {
            switch (section) {
                case "PON Consulta de Sinal":
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
                case "ONT/ONU Velocidade":
                    newContent = createONUTrafficScreen();
                    break;
                case "ONT/ONU Serviços":
                    newContent = createONUServiceScreen();
                    break;
                case "Chamados":
                    newContent = createTechnicalTicketsScreen();
                    break;
                default:
                    newContent = new VBox();
                    break;
            }
        }

        if (newContent != null && newContent != currentContent) {
            final Node finalNewContent = newContent;
            finalNewContent.setOpacity(0);
            mainContent.setCenter(finalNewContent);

            if (currentContent != null) {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(200), currentContent);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(e -> {
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(200), finalNewContent);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });
                fadeOut.play();
            } else {
                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), finalNewContent);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            }

            currentSection = section;
        }
    }
    // ---------------------- BARRA VERTICAl ---------------------- //


    // ---------------------- OLTS ---------------------- //
    private Node createOLTScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));
        VBox.setVgrow(content, Priority.ALWAYS);

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(15);
        cardsPane.setVgap(15);
        cardsPane.setPadding(new Insets(20));
        cardsPane.setAlignment(Pos.CENTER);

        List<VBox> oltCards = new ArrayList<>();
        final boolean[] isMaximized = {false};

        for (OLT olt : OLTList.getOLTs()) {
            VBox card = createOLTCard(olt, isMaximized[0]);
            oltCards.add(card);
            cardsPane.getChildren().add(card);
        }

        ChangeListener<Number> widthChangeListener = (obs, oldVal, newVal) -> {
            if (newVal == null) return;
            double availableWidth = newVal.doubleValue();
            double cardWidth = isMaximized[0] ? 185 : 155;
            int gap = 15;
            int numColumns = Math.max(1, (int) ((availableWidth - 40) / (cardWidth + gap)));
            double newPaneWidth = numColumns * (cardWidth + gap);
            cardsPane.setPrefWidth(newPaneWidth);
            int itemsPerRow = Math.max(1, numColumns);
            int totalRows = (int) Math.ceil((double) OLTList.getOLTs().size() / itemsPerRow);
            int itemsInLastRow = OLTList.getOLTs().size() % itemsPerRow;
            if (itemsInLastRow == 1 && totalRows > 1 && itemsPerRow > 1) {
                cardsPane.setPrefWrapLength((itemsPerRow - 1) * (cardWidth + gap));
            } else {
                cardsPane.setPrefWrapLength(newPaneWidth);
            }
        };

        content.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Stage stage = (Stage) newScene.getWindow();
                newScene.widthProperty().addListener(widthChangeListener);
                stage.maximizedProperty().addListener((obs2, oldMax, newMax) -> {
                    isMaximized[0] = newMax;
                    widthChangeListener.changed(null, null, newScene.getWidth());
                    for (VBox card : oltCards) {
                        double newWidth = newMax ? 185 : 155;
                        double newHeight = newMax ? 141.5 : 123;
                        card.setPrefSize(newWidth, newHeight);
                        card.setMaxWidth(newWidth);
                        card.setMaxHeight(newHeight);
                        Rectangle clip = (Rectangle) card.getClip();
                        clip.setWidth(newWidth);
                        clip.setHeight(newHeight);
                        for (Node child : card.getChildren()) {
                            if (child instanceof Label && ((Label) child).getStyleClass().contains("olt-name")) {
                                ((Label) child).setStyle(newMax ? "-fx-font-size: 16px;" : "-fx-font-size: 14px;");
                            }
                            if (child instanceof Button && ((Button) child).getText().equals("Conectar")) {
                                ((Button) child).setStyle(newMax ? "-fx-font-size: 14px; -fx-pref-width: 120px; -fx-pref-height: 35px;" :
                                        "-fx-font-size: 12px; -fx-pref-width: 100px; -fx-pref-height: 30px;");
                            }
                        }
                    }
                });
                Platform.runLater(() -> widthChangeListener.changed(null, null, newScene.getWidth()));
            }
        });

        VBox scrollContent = new VBox(cardsPane);
        scrollContent.setAlignment(Pos.TOP_CENTER);
        scrollContent.setPadding(new Insets(20, 20, 20, 20));
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

        content.getChildren().addAll(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Platform.runLater(() -> {
            animateCardsSequentially(cardsPane.getChildren(), 50);
            cardsPane.toFront();
        });

        return content;
    }

    private VBox createOLTCard(OLT olt, boolean isMaximized) {
        double cardWidth = isMaximized ? 185 : 155;
        double cardHeight = isMaximized ? 141.5 : 123;

        VBox card = new VBox(8);
        card.getStyleClass().add("olt-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(cardWidth, cardHeight);
        card.setMaxWidth(cardWidth);
        card.setMaxHeight(cardHeight);

        Rectangle clip = new Rectangle(cardWidth, cardHeight);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        card.setClip(clip);

        Label nameLabel = new Label(olt.name.replace("_", " "));
        nameLabel.getStyleClass().add("olt-name");
        nameLabel.setStyle(isMaximized ? "-fx-font-size: 16px;" : "-fx-font-size: 14px;");

        Label ipLabel = new Label(olt.ip);
        ipLabel.getStyleClass().add("olt-ip");

        Button connectBtn = new Button("Conectar");
        connectBtn.getStyleClass().add("connect-btn");
        connectBtn.setStyle(isMaximized ? "-fx-font-size: 14px; -fx-pref-width: 120px; -fx-pref-height: 55px;" :
                "-fx-font-size: 12px; -fx-pref-width: 100px; -fx-pref-height: 30px;");

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

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.SOMETIMES);
        spacer.setPrefHeight(10);

        card.getChildren().addAll(nameLabel, ipLabel, spacer, connectBtn);
        VBox.setMargin(connectBtn, new Insets(0, 0, 5, 0));
        return card;
    }
    // ---------------------- OLTS ---------------------- //


    // ---------------------- CONSULTA DE SINAL ---------------------- //
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

        TextField fsField = new TextField();
        fsField.setPromptText("Digite o F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115);

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115);

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
        resultArea.setPrefHeight(350);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);


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

            resultArea.replaceText(0, resultArea.getLength(), "Consultando os sinais da PON " + fs + "/" + p + "...\n");
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
        resultadoArea.setPrefHeight(350);
        VBox.setVgrow(resultadoArea, Priority.ALWAYS);

        consultarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String pon = ponField.getText().trim();

            if (selectedOLT == null || pon.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, preencha todos os campos corretamente.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Consultando informações da PON " + pon + "...\n");

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
                    Platform.runLater(() -> startPos[0] = hiddenArea.getLength());
                    Thread.sleep(100);

                    tempSSHManager.sendCommand("display port desc " + pon);
                    Thread.sleep(2500);

                    tempSSHManager.sendCommand("display ont info summary " + pon);
                    Thread.sleep(8000);

                    final String[] resultadoFinal = {""};
                    Platform.runLater(() -> {
                        String fullOutput = hiddenArea.getText();
                        StringBuilder resultado = new StringBuilder();

                        String portDescCmd = "display port desc " + pon;
                        int descIndex = fullOutput.indexOf(portDescCmd, startPos[0]);
                        if (descIndex >= 0) {
                            int descStart = fullOutput.indexOf("\n", descIndex);
                            int nextBlock = fullOutput.indexOf("\n\n", descStart);
                            if (descStart > 0 && nextBlock > descStart) {
                                String descResult = fullOutput.substring(descStart + 1, nextBlock).trim();
                                descResult = "  " + descResult.replaceFirst("^", "");
                                resultado.append(descResult).append("\n");
                            }
                        }

                        String summaryCmd = "display ont info summary " + pon;
                        int sumIndex = fullOutput.indexOf(summaryCmd, startPos[0]);
                        if (sumIndex >= 0) {
                            int sumStart = fullOutput.indexOf("\n", sumIndex);
                            if (sumStart > 0) {
                                String sumResult = fullOutput.substring(sumStart + 1).trim();
                                sumResult = sumResult.replace("Command is being executed. Please wait", "");
                                resultado.append(sumResult);
                            }
                        } else {
                            resultado.append("❌ Não foi possível obter o resumo da PON.\n");
                        }

                        resultadoArea.replaceText(0, resultadoArea.getLength(), resultado.toString());
                        destacarIPs(resultadoArea);
                        showToast("🔎 Consulta de PON Summary finalizada!");
                    });

                    DatabaseManager.logUsuario(usuario.getNome(),
                            "Consultou resumo e descrição da PON " + pon + " na " + selectedOLT.name);

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
        exportBtn.setMaxWidth(140);

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
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Consulta ONT/ONU por SN");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));
        formArea.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(formArea, Priority.ALWAYS);


        Label infoLabel = new Label("Verifique todas as informações do SN.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        oltComboBox.setMaxWidth(240);

        TextField snField = new TextField();
        snField.setPromptText("Digite o SN da ONT/ONU");
        snField.getStyleClass().add("text-field");
        snField.setMaxWidth(240);

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
        resultadoArea.setPrefHeight(350);
        VBox.setVgrow(resultadoArea, Priority.ALWAYS);


        consultarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String sn = snField.getText().trim();

            if (selectedOLT == null || sn.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, preencha todos os campos corretamente.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Consultando informações do SN " + sn + "...\n");

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
                            resultado[0] = "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.";
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
        exportBtn.setMaxWidth(140);


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

        TextField fsField = new TextField();
        fsField.setPromptText("Digite F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115);

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115);

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("ID da ONT");
        ontIdField.getStyleClass().add("text-field");
        ontIdField.setMaxWidth(115);

        TextFormatter<String> fsFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9/]{0,4}")) {
                return change;
            }
            return null;
        });
        fsField.setTextFormatter(fsFormatter);

        TextFormatter<String> pFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,3}")) {
                return change;
            }
            return null;
        });
        pField.setTextFormatter(pFormatter);

        TextFormatter<String> ontIdFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,4}")) {
                return change;
            }
            return null;
        });
        ontIdField.setTextFormatter(ontIdFormatter);

        HBox formRow2 = new HBox(10);
        formRow2.setAlignment(Pos.CENTER);
        formRow2.getChildren().addAll(fsField, pField, ontIdField);
        HBox.setHgrow(fsField, Priority.ALWAYS);
        HBox.setHgrow(pField, Priority.ALWAYS);
        HBox.setHgrow(ontIdField, Priority.ALWAYS);

        Button diagnosticarBtn = new Button("Consultar");
        diagnosticarBtn.getStyleClass().add("connect-btn");
        diagnosticarBtn.setMaxWidth(140);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setPrefHeight(350);
        VBox.setVgrow(resultadoArea, Priority.ALWAYS);

        diagnosticarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty() || ontId.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, preencha todos os campos corretamente.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Iniciando diagnóstico de quedas para ID " + ontId + " na PON " + fs + "/" + p + "...\n");

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

                    ssh.sendCommand("display ont register-info " + p + " " + ontId);

                    Thread.sleep(5000);

                    final String[] resultado = {""};
                    Platform.runLater(() -> {
                        String fullOutput = hiddenArea.getText();

                        String searchString = "display ont register-info " + p + " " + ontId;
                        int cmdIndex = fullOutput.indexOf(searchString, startPos[0]);

                        if (cmdIndex >= 0) {
                            int resultStartIndex = fullOutput.indexOf("\n", cmdIndex);
                            if (resultStartIndex >= 0) {
                                resultado[0] = fullOutput.substring(resultStartIndex + 1);
                            }
                        } else {
                            resultado[0] = "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.";
                        }

                        resultadoArea.replaceText(0, resultadoArea.getLength(), resultado[0]);
                        destacarIPs(resultadoArea);
                        showToast("🔎 Consulta de Quedas finalizada!");
                    });

                    DatabaseManager.logUsuario(usuario.getNome(),
                            "Consultou diagnóstico de quedas da ONT/ONU " + fs + "/" + p + ontId + " na " + selectedOLT.name);
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

        fsField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                diagnosticarBtn.fire();
            }
        });

        pField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                diagnosticarBtn.fire();
            }
        });

        ontIdField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                diagnosticarBtn.fire();
            }
        });

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);


        exportBtn.setOnAction(e -> {
            exportarResultado(resultadoArea, "Diagnostico_Quedas");
        });

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                formRow2,
                diagnosticarBtn,
                resultadoArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- ONT/ONU QUEDAS ---------------------- //


    // ---------------------- VELOCIDADE ONT/ONU ---------------------- //
    private Node createONUTrafficScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20, 0, 20, 0));
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Monitoramento de Velocidade ONT/ONU");
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

        TextField fsField = new TextField();
        fsField.setPromptText("Digite F/S");
        fsField.getStyleClass().add("text-field");
        fsField.setMaxWidth(115);

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.getStyleClass().add("text-field");
        pField.setMaxWidth(115);

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("ID da ONT");
        ontIdField.getStyleClass().add("text-field");
        ontIdField.setMaxWidth(115);

        TextFormatter<String> fsFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9/]{0,4}")) {
                return change;
            }
            return null;
        });
        fsField.setTextFormatter(fsFormatter);

        TextFormatter<String> pFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,3}")) {
                return change;
            }
            return null;
        });
        pField.setTextFormatter(pFormatter);

        TextFormatter<String> ontIdFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,4}")) {
                return change;
            }
            return null;
        });
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

        Button stopBtn = new Button("Parar");
        stopBtn.getStyleClass().add("stop-btn");
        stopBtn.setMaxWidth(140);
        stopBtn.setDisable(true);

        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.getChildren().addAll(monitorBtn, stopBtn);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setPrefHeight(350);
        VBox.setVgrow(resultadoArea, Priority.ALWAYS);

        AtomicBoolean monitoring = new AtomicBoolean(false);
        AtomicReference<SSHManager> currentSSHManager = new AtomicReference<>();
        AtomicReference<Thread> monitoringThread = new AtomicReference<>();

        Runnable resetMonitoringControls = () -> {
            monitoring.set(false);
            Platform.runLater(() -> {
                monitorBtn.setDisable(false);
                stopBtn.setDisable(true);
                oltComboBox.setDisable(false);
                fsField.setDisable(false);
                pField.setDisable(false);
                ontIdField.setDisable(false);
            });
        };

        monitorBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty() || ontId.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, preencha todos os campos corretamente.");
                return;
            }

            if (monitoring.get()) {
                return;
            }

            resultadoArea.clear();
            resultadoArea.appendText("Iniciando monitoramento de Velocidade para ID " + ontId + " na PON " + fs + "/" + p + "...\n");
            resultadoArea.appendText("O monitoramento será executado por 2 minutos ou até que seja interrompido manualmente.\n");

            monitoring.set(true);
            monitorBtn.setDisable(true);
            stopBtn.setDisable(false);
            oltComboBox.setDisable(true);
            fsField.setDisable(true);
            pField.setDisable(true);
            ontIdField.setDisable(true);

            SSHManager sshManager = new SSHManager();
            currentSSHManager.set(sshManager);

            // Timer 2min
            Timer timeoutTimer = new Timer();
            timeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (monitoring.get()) {
                        monitoring.set(false);
                        Platform.runLater(() -> {
                            resultadoArea.appendText("\nMonitoramento finalizado automaticamente após 2 minutos.\n");
                            resetMonitoringControls.run();
                        });
                    }
                }
            }, 120000);

            Thread thread = new Thread(() -> {
                CodeArea hiddenArea = new CodeArea();
                try {
                    boolean connected = sshManager.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, hiddenArea);
                    if (!connected) {
                        Platform.runLater(() -> {
                            resultadoArea.appendText("\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.");
                            resetMonitoringControls.run();
                        });
                        timeoutTimer.cancel();
                        return;
                    }

                    sshManager.sendCommand("enable");
                    Thread.sleep(1000);
                    sshManager.sendCommand("config");
                    Thread.sleep(1000);
                    sshManager.sendCommand("interface gpon " + fs);
                    Thread.sleep(1000);

                    final String trafficCommand = "display ont traffic " + p + " " + ontId;

                    while (monitoring.get()) {
                        Platform.runLater(() -> {
                            hiddenArea.clear();
                        });
                        Thread.sleep(100);

                        sshManager.sendCommand(trafficCommand);

                        Thread.sleep(1500);

                        Platform.runLater(() -> {
                            String currentOutput = hiddenArea.getText();
                            if (currentOutput.contains("{ <cr>|ontportid<U>") ||
                                    currentOutput.contains("<1,24>||<K> }:")) {
                                try {
                                    sshManager.sendEnterKey();
                                } catch (Exception ex) {
                                    System.err.println("Erro ao enviar Enter: " + ex.getMessage());
                                }
                            }
                        });

                        Thread.sleep(3500);

                        Platform.runLater(() -> {
                            String fullOutput = hiddenArea.getText();

                            if (fullOutput.contains("Traffic Information") ||
                                    fullOutput.contains("Up traffic") ||
                                    fullOutput.contains("Down traffic")) {

                                String result = extractTrafficInfo(fullOutput);

                                resultadoArea.appendText("\n" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n");
                                resultadoArea.appendText(result);
                                resultadoArea.moveTo(resultadoArea.getLength());
                                resultadoArea.requestFollowCaret();
                            } else {
                                resultadoArea.appendText("\nAguardando dados de tráfego...\n");
                                System.out.println("Debug - Saída completa: " + fullOutput);
                            }
                        });

                        Thread.sleep(2000);
                    }

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultadoArea.appendText("\nErro durante o monitoramento: " + ex.getMessage() + "\n");
                        resetMonitoringControls.run();
                    });
                    timeoutTimer.cancel();
                } finally {
                    sshManager.disconnect();
                }
            });

            thread.setDaemon(true);
            thread.start();
            monitoringThread.set(thread);

            DatabaseManager.logUsuario(usuario.getNome(),
                    "Iniciou monitoramento de velocidade para ONT " + ontId + " na PON " + fs + "/" + p + " na " + selectedOLT.name);
        });

        stopBtn.setOnAction(e -> {
            monitoring.set(false);
            resetMonitoringControls.run();

            if (monitoringThread.get() != null) {
                monitoringThread.get().interrupt();
            }

            if (currentSSHManager.get() != null) {
                currentSSHManager.get().disconnect();
            }

            resultadoArea.appendText("\nMonitoramento interrompido manualmente.\n");
        });

        fsField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                monitorBtn.fire();
            }
        });

        pField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                monitorBtn.fire();
            }
        });

        ontIdField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                monitorBtn.fire();
            }
        });

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);
        exportBtn.setOnAction(e -> exportarResultado(resultadoArea, "Monitoramento_Velocidade"));

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                formRow1,
                buttonRow,
                resultadoArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }

    private String extractTrafficInfo(String output) {
        StringBuilder result = new StringBuilder();

        Pattern upPattern = Pattern.compile("Up traffic\\s+\\(kbps\\)\\s+:\\s+(\\d+)");
        Pattern downPattern = Pattern.compile("Down traffic\\s+\\(kbps\\)\\s+:\\s+(\\d+)");

        Pattern upPattern2 = Pattern.compile("Upstream rate\\s*:\\s*(\\d+)\\s*kbps");
        Pattern downPattern2 = Pattern.compile("Downstream rate\\s*:\\s*(\\d+)\\s*kbps");

        Matcher upMatcher = upPattern.matcher(output);
        Matcher downMatcher = downPattern.matcher(output);

        if (!upMatcher.find()) {
            upMatcher = upPattern2.matcher(output);
        }

        if (!downMatcher.find()) {
            downMatcher = downPattern2.matcher(output);
        }

        upMatcher.reset();
        downMatcher.reset();

        if (upMatcher.find()) {
            int upSpeed = Integer.parseInt(upMatcher.group(1));
            result.append(String.format("Upload: %d kbps (%.2f Mbps)\n", upSpeed, upSpeed / 1000.0));
        } else {
            result.append("Upload: Não disponível\n");
        }

        if (downMatcher.find()) {
            int downSpeed = Integer.parseInt(downMatcher.group(1));
            result.append(String.format("Download: %d kbps (%.2f Mbps)\n", downSpeed, downSpeed / 1000.0));
        } else {
            result.append("Download: Não disponível\n");
        }

        result.append("----------------------------------------------------\n");

        return result.toString();
    }
    // ---------------------- VELOCIDADE ONT/ONU ---------------------- //


    // ---------------------- ONT/ONU SERVIÇOS  ---------------------- //
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

        TextField fspField = new TextField();
        fspField.setPromptText("Digite F/S/P");
        fspField.getStyleClass().add("text-field");
        fspField.setMaxWidth(115);

        TextField ontIdField = new TextField();
        ontIdField.setPromptText("ID da ONT");
        ontIdField.getStyleClass().add("text-field");
        ontIdField.setMaxWidth(115);

        TextFormatter<String> ponFormatter = new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (change.isContentChange() && !newText.matches("[0-9/]{0,7}")) {
                return null;
            }
            return change;
        });
        fspField.setTextFormatter(ponFormatter);


        TextFormatter<String> ontIdFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,4}")) {
                return change;
            }
            return null;
        });
        ontIdField.setTextFormatter(ontIdFormatter);


        HBox formRow3 = new HBox(10);
        formRow3.setAlignment(Pos.CENTER);
        formRow3.getChildren().addAll(fspField, ontIdField);
        HBox.setHgrow(fspField, Priority.ALWAYS);
        HBox.setHgrow(ontIdField, Priority.ALWAYS);

        Button consultBtn = new Button("Consultar");
        consultBtn.getStyleClass().add("connect-btn");
        consultBtn.setMaxWidth(140);

        CodeArea resultadoArea = new CodeArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("code-area");
        resultadoArea.setPrefHeight(350);
        VBox.setVgrow(resultadoArea, Priority.ALWAYS);

        consultBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fsp = fspField.getText().trim();
            String ontId = ontIdField.getText().trim();

            if (selectedOLT == null || fsp.isEmpty() || ontId.isEmpty()) {
                resultadoArea.replaceText(0, resultadoArea.getLength(),
                        "Por favor, preencha todos os campos corretamente.");
                return;
            }

            resultadoArea.replaceText(0, resultadoArea.getLength(), "Consultando serviços para ID " + ontId + " na PON " + fsp + "...\n");

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

                    final int[] startPos = {0};
                    Platform.runLater(() -> {
                        startPos[0] = hiddenArea.getLength();
                    });
                    Thread.sleep(100);

                    ssh.sendCommand("display service-port port " + fsp + " ont " + ontId);

                    Thread.sleep(7000);

                    Platform.runLater(() -> {
                        String currentOutput = hiddenArea.getText();
                        if (currentOutput.contains("{ <cr>|e2e<K>|gemport<K>") ||
                                currentOutput.contains("|sort-by<K>||<K> }:")) {
                            try {
                                ssh.sendEnterKey();
                            } catch (Exception ex) {
                                System.err.println("Erro ao enviar Enter: " + ex.getMessage());
                            }
                        }
                    });

                    Thread.sleep(3500);

                    final String[] resultado = {""};
                    Platform.runLater(() -> {
                        String fullOutput = hiddenArea.getText();

                        String searchString = "display service-port port " + fsp + " ont " + ontId;
                        int cmdIndex = fullOutput.indexOf(searchString, startPos[0]);

                        if (cmdIndex >= 0) {
                            int resultStartIndex = fullOutput.indexOf("\n", cmdIndex);
                            if (resultStartIndex >= 0) {
                                resultado[0] = fullOutput.substring(resultStartIndex + 1);
                            }
                        } else {
                            resultado[0] = "\n❌ Não foi possível conectar à OLT.\n\n" +
                                    "Verifique se:\n" +
                                    "1 - Você está na rede interna da empresa\n" +
                                    "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                    "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                    "Caso esteja tudo correto, contate o Eduardo.";
                        }

                        resultadoArea.replaceText(0, resultadoArea.getLength(), resultado[0]);
                        destacarIPs(resultadoArea);
                        showToast("🔎 Consulta de Serviços finalizada!");
                    });

                    DatabaseManager.logUsuario(usuario.getNome(),
                            "Consultou os serviços da ONT/ONU ID" + ontId + " da PON " + fsp + " na " + selectedOLT.name);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultadoArea.replaceText(0, resultadoArea.getLength(), "Erro: " + ex.getMessage());
                        destacarIPs(resultadoArea);
                        showToast("⚠️ Erro ao consultar Serviços.");
                    });
                } finally {
                    ssh.disconnect();
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        fspField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                consultBtn.fire();
            }
        });

        ontIdField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                consultBtn.fire();
            }
        });

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setMaxWidth(140);


        exportBtn.setOnAction(e -> {
            exportarResultado(resultadoArea, "Consulta_Servicos");
        });

        formArea.getChildren().addAll(
                infoLabel,
                oltComboBox,
                formRow3,
                consultBtn,
                resultadoArea,
                exportBtn
        );

        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ---------------------- ONT/ONU SERVIÇOS  ---------------------- //


    // ---------------------- CHAMADOS  ---------------------- //
    private Node createTechnicalTicketsScreen() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("content-area");
        VBox.setVgrow(content, Priority.ALWAYS);

        Label title = new Label("Chamados");
        title.getStyleClass().add("title");

        TableView<Ticket> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setItems(FXCollections.observableArrayList(DatabaseManager.getAllTickets()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);


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
        HBox.setHgrow(titleBar, Priority.ALWAYS);


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
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox windowControls = new HBox(5);
        windowControls.getStyleClass().add("window-controls");

        Button minimizeBtn = new Button();
        minimizeBtn.getStyleClass().addAll("window-btn", "minimize-btn");
        minimizeBtn.setOnAction(e -> primaryStage.setIconified(true));
        minimizeBtn.setTooltip(new Tooltip("Minimizar"));

        Button maximizeBtn = new Button();
        maximizeBtn.getStyleClass().addAll("window-btn", "maximize-btn");
        maximizeBtn.setOnAction(e -> toggleMaximize());
        maximizeBtn.setTooltip(new Tooltip("Maximizar/Restaurar"));

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(12, 12, 12, 12));
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
        Stage stage = (Stage) mainContent.getScene().getWindow();

        if (stage.isMaximized()) {
            stage.setMaximized(false);
        } else {
            stage.setMaximized(true);
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
        VBox.setVgrow(content, Priority.ALWAYS);

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(header, Priority.ALWAYS);

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
                if (mainContent.getCenter() != terminalTabs) {
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
        newTerminalArea.setPrefHeight(300);
        VBox.setVgrow(newTerminalArea, Priority.ALWAYS);

        HBox commandArea = new HBox(10);
        commandArea.setAlignment(Pos.CENTER);
        commandArea.setPadding(new Insets(10, 0, 0, 0));
        HBox.setHgrow(commandArea, Priority.ALWAYS);

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
        HBox.setHgrow(quickActions, Priority.ALWAYS);

        Button clearBtn = new Button("Limpar");
        clearBtn.getStyleClass().add("action-btn");
        Button helpBtn = new Button("Ajuda");
        helpBtn.getStyleClass().add("action-btn");

        quickActions.getChildren().addAll(clearBtn, helpBtn);

        terminalBox.getChildren().addAll(newTerminalArea, commandArea, quickActions);
        content.getChildren().addAll(header, terminalBox);
        VBox.setVgrow(terminalBox, Priority.ALWAYS);

        Tab terminalTab = new Tab(olt.name, content);
        terminalTab.setClosable(true);

        SSHManager newSSHManager = new SSHManager();
        terminalConnections.put(terminalTab, newSSHManager);

        terminalTab.setOnCloseRequest(e -> {
            e.consume();

            Node tabContent = terminalTab.getContent();
            Platform.runLater(() -> {
                tabContent.setOpacity(1.0);
                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), tabContent);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(ev -> {
                    SSHManager ssh = terminalConnections.remove(terminalTab);
                    if (ssh != null) {
                        ssh.disconnect();
                    }
                    terminalTabs.getTabs().remove(terminalTab);

                    if (terminalTabs != null && terminalTabs.getTabs().size() == 1 && terminalTabs.getTabs().get(0).getText().equals("Lista de OLTs")) {
                        terminalTabs.getSelectionModel().select(0);
                    }
                });
                fadeOut.play();
            });
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
                // Comandos OLT Huawei MA6800
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
                // Comandos OLT Huawei MA6800
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
                                for (String match : finalMatches) {
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
        commandsBox.setMaxWidth(Double.MAX_VALUE);


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
        basicCommands.setMaxWidth(Double.MAX_VALUE);


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
        oltCommands.setMaxWidth(Double.MAX_VALUE);


        commandsBox.getChildren().addAll(basicLabel, basicCommands, oltLabel, oltCommands);

        Button closeBtn = new Button("Fechar");
        closeBtn.getStyleClass().add("help-close-btn");
        closeBtn.setOnAction(e -> helpStage.close());
        closeBtn.setMaxWidth(Double.MAX_VALUE);


        helpContent.getChildren().addAll(commandsBox, closeBtn);

        Scene helpScene = new Scene(helpContent, 550, 500);
        ThemeManager.applyThemeToNewScene(helpScene);

        helpStage.setScene(helpScene);
        helpStage.show();
    }

    // ---------------------- AJUDA INSIDE-TERMINAL ---------------------- //


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
        closeBtn.setPadding(new Insets(12, 12, 12, 12));
        closeBtn.setOnAction(ev -> creditsStage.close());
        addEnhancedButtonHoverEffects(closeBtn);

        titleBar.getChildren().addAll(spacer, closeBtn);

        VBox creditsContent = new VBox(15);
        creditsContent.setAlignment(Pos.TOP_LEFT);
        creditsContent.setPadding(new Insets(10));

        Label appName = new Label("Gerenciador de OLTs - 1.5.5.0");
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



    public static void main(String[] args) {
        launch(args);
    }


    // ---------------------- STOPS ---------------------- //
    @Override
    public void stop() {
        System.out.println("Aplicação parando (método stop())...");
        fileLogger.println("DEBUG (Main - stop): Método stop() iniciado.");

        if (this.sshManager != null) {
            System.out.println("DEBUG (Main - stop): Desconectando SSHManager principal...");
            fileLogger.println("DEBUG (Main - stop): Desconectando SSHManager principal...");
            this.sshManager.disconnect();
            System.out.println("DEBUG (Main - stop): SSHManager principal desconectado.");
            fileLogger.println("DEBUG (Main - stop): SSHManager principal desconectado.");
        }

        if (terminalConnections != null && !terminalConnections.isEmpty()) {
            System.out.println("DEBUG (Main - stop): Desconectando conexões SSH ativas dos terminais...");
            fileLogger.println("DEBUG (Main - stop): Desconectando conexões SSH ativas dos terminais...");
            for (Map.Entry<Tab, SSHManager> entry : terminalConnections.entrySet()) {
                SSHManager ssh = entry.getValue();
                if (ssh != null) {
                    ssh.disconnect();
                    System.out.println("DEBUG (Main - stop): Desconectada SSH da aba: " + entry.getKey().getText());
                    fileLogger.println("DEBUG (Main - stop): Desconectada SSH da aba: " + entry.getKey().getText());
                }
            }
            terminalConnections.clear();
            System.out.println("DEBUG (Main - stop): Todas as conexões SSH dos terminais foram desconectadas e limpas.");
            fileLogger.println("DEBUG (Main - stop): Todas as conexões SSH dos terminais foram desconectadas e limpas.");
        }

        if (trayIcon != null && SystemTray.isSupported()) {
            System.out.println("DEBUG (Main - stop): Removendo ícone da bandeja do sistema...");
            fileLogger.println("DEBUG (Main - stop): Removendo ícone da bandeja do sistema...");
            SystemTray.getSystemTray().remove(trayIcon);
            System.out.println("DEBUG (Main - stop): Ícone da bandeja do sistema removido.");
            fileLogger.println("DEBUG (Main - stop): Ícone da bandeja do sistema removido.");
        }

        if (usuarioLogado != null) {
            System.out.println("Atualizando status do usuário para offline em stop()...");
            fileLogger.println("DEBUG (Main - stop): Atualizando status do usuário para offline...");

            try {
                Thread dbUpdateThread = new Thread(() -> {
                    try {
                        System.out.println("Status do usuário atualizado em stop().");
                        fileLogger.println("DEBUG (Main - stop): Status do usuário (simulado) atualizado para offline.");
                    } catch (Exception dbEx) {
                        System.err.println("Erro na thread de atualização de status do DB em stop(): " + dbEx.getMessage());
                        fileLogger.println("ERRO (Main - stop): Erro na thread de atualização de status do DB: " + dbEx.getMessage());
                        dbEx.printStackTrace(fileLogger);
                    }
                });

                dbUpdateThread.setDaemon(true);
                dbUpdateThread.start();

            } catch (Exception e) {
                System.err.println("Erro ao criar thread para atualização de status em stop(): " + e.getMessage());
                fileLogger.println("ERRO (Main - stop): Erro ao criar thread para atualização de status: " + e.getMessage());
                e.printStackTrace(fileLogger);
            }
        }

        System.out.println("DEBUG (Main - stop): Fechando logger de arquivo...");
        fileLogger.println("DEBUG (Main - stop): Fechando logger de arquivo...");
        closeFileLogging();

        System.out.println("Método stop concluído.");
        fileLogger.println("DEBUG (Main - stop): Método stop() concluído.");
    }
}
// ---------------------- STOPS ---------------------- //