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
import javafx.beans.property.SimpleStringProperty;
import java.util.Optional;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Pagination;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.FadeTransition;
import javafx.scene.text.Font;
import models.Ticket;
import models.Usuario;
import database.DatabaseManager;
import screens.LoginScreen;


public class Main extends Application {
    private Usuario usuario;
    private VBox rootLayout;
    private Stage primaryStage;
    private static TextArea terminalArea;
    private double xOffset = 0;
    private double yOffset = 0;
    private SSHManager sshManager;
    private ScheduledExecutorService breakageMonitor;
    private BorderPane mainContent;
    private ToggleGroup navGroup;
    private String currentSection = null;
    private Map<String, Node> contentCache = new HashMap<>();
    private boolean isConnectedToOLT = false;
    private OLT connectedOLT;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    /* ATENÇÃO!!!!
    - Abaixo tem +300h de código, chatgpt e muito café/energético. Tomar cuidado <3
     */


    private static ObservableList<RompimentoData> rompimentosDetectados = FXCollections.observableArrayList();

    public static void adicionarRompimento(String olt, String pon, String impacted, String location, String status, String time) {
        Platform.runLater(() -> {
            rompimentosDetectados.add(new RompimentoData(olt, pon, impacted, location, status, time));
        });
    }


    @Override
    public void start(Stage primaryStage) {
        try {
            LoginScreen loginScreen = new LoginScreen();
            Font.loadFont(getClass().getResourceAsStream("/fonts/maplemono-regular.ttf"), 12);
            this.usuario = loginScreen.showLogin(new Stage());

            if (this.usuario == null) {
                Platform.exit();
                return;
            }


            // Inicializa os serviços de Monitoramento Rompimentos
            breakageMonitor = Executors.newSingleThreadScheduledExecutor();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            // Inicializa os serviços de Monitoramento Rompimentos


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

            scene.setOnKeyPressed(event -> {
                if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.F) {
                    showSection("ONT/ONU By-SN");

                    Platform.runLater(() -> {
                        Node content = contentCache.get("ONT/ONU By-SN");
                        if (content != null) {
                            TextField snField = (TextField) content.lookup("#snField");
                            if (snField != null) {
                                snField.requestFocus();
                            }
                        }
                    });

                    event.consume();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.exit();
        }
    }


    private void initializeBreakageMonitoring() {
        runBreakageDetection();

        scheduler.scheduleAtFixedRate(
                this::runBreakageDetection,
                30,
                30,
                TimeUnit.MINUTES
        );
    }

    private void runBreakageDetection() {
        rompimentosDetectados.clear();

        for (OLT olt : OLTList.getOLTs()) {
            Platform.runLater(() -> {
            });

            try {
                SSHManager sshManager = new SSHManager();
                sshManager.connect(olt.ip, Secrets.SSH_USER, Secrets.SSH_PASS, new TextArea());

                Thread.sleep(2000);

                if (sshManager.isConnected()) {
                    sshManager.scanForBreakages(olt.name);
                    rompimentosDetectados.addAll(sshManager.getBreakageList());
                    sshManager.disconnect();
                }

                Thread.sleep(5000);

            } catch (Exception e) {
                System.err.println("Error scanning OLT " + olt.name + ": " + e.getMessage());
            }
        }

        Platform.runLater(() -> {
        });
    }


    private VBox createSideNavigation() {
        VBox sideNav = new VBox(10);
        sideNav.getStyleClass().add("side-nav");
        sideNav.setPrefWidth(200);
        sideNav.setPadding(new Insets(20, 0, 20, 0));

        Label menuTitle = new Label("Feito por Eduardo Tomaz\n v1.5.1.0");
        menuTitle.getStyleClass().add("menu-title");
        menuTitle.setPadding(new Insets(0, 0, 10, 15));

        navGroup = new ToggleGroup();

        ToggleButton oltBtn = createNavButton("OLTs", false);
        ToggleButton signalBtn = createNavButton("Consulta de Sinal", false);
        ToggleButton ponSummaryBtn = createNavButton("PON Summary", false);
        ToggleButton onuBySNBtn = createNavButton("ONT/ONU By-SN", false);
        ToggleButton diagnosisBtn = createNavButton("ONT/ONU Quedas", false);
        ToggleButton breaksBtn = createNavButton("Rompimentos", false);

        sideNav.getChildren().addAll(oltBtn, signalBtn, ponSummaryBtn, onuBySNBtn, diagnosisBtn, breaksBtn);

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

        Button logoutBtn = new Button("Deslogar");
        logoutBtn.getStyleClass().add("logout-button");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);

        VBox logoutContainer = new VBox();
        logoutContainer.setPadding(new Insets(5, 0, 0, 0));
        logoutContainer.getChildren().add(logoutBtn);

        logoutContainer.setVisible(false);
        logoutContainer.setManaged(false);

        dropdownBtn.setOnAction(e -> {
            boolean isVisible = !logoutContainer.isVisible();
            logoutContainer.setVisible(isVisible);
            logoutContainer.setManaged(isVisible);
            dropdownBtn.setText(isVisible ? "▴" : "▾");

            dropdownBtn.getStyleClass().remove("dropdown-arrow-active");
            if (isVisible) {
                dropdownBtn.getStyleClass().add("dropdown-arrow-active");
            }

            logoutBtn.setVisible(true);
            logoutBtn.setManaged(true);

            sideNav.requestLayout();
        });

        logoutBtn.setOnAction(e -> {
            primaryStage.close();

            Stage loginStage = new Stage();
            Usuario novoLogin = new LoginScreen().showLogin(loginStage);

            System.out.println("Usuário retornado: " + (novoLogin != null ? novoLogin.getNome() : "null"));

            if (novoLogin != null) {
                Platform.runLater(() -> new Main().start(new Stage()));
            }
        });

        footerBox.getChildren().addAll(menuTitle, userInfoBox, logoutContainer);
        sideNav.getChildren().addAll(spacer, footerBox);

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
                case "PON Summary":
                    newContent = createPONSummaryScreen();
                    break;
                case "ONT/ONU By-SN":
                    newContent = createONUBySNScreen();
                    break;
                case "ONT/ONU Quedas":
                    newContent = createDropDiagnosisScreen();
                    break;
                case "Rompimentos":
                    newContent = createBreaksScreen();
                    break;
                case "Chamados":
                    newContent = createTechnicalTicketsScreen();
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


    // ------------ OLTS
    private Node createOLTSelectionScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));

        Label title = new Label("Selecione uma OLT");
        title.getStyleClass().add("title");

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(15);
        cardsPane.setVgap(15);
        cardsPane.setPadding(new Insets(25));
        cardsPane.setAlignment(Pos.TOP_CENTER);
        cardsPane.setPrefWrapLength(900);
        cardsPane.setMaxWidth(Double.MAX_VALUE);
        cardsPane.getStyleClass().add("scroll-content");

        mainContent.widthProperty().addListener((obs, oldVal, newVal) -> {
            cardsPane.setPrefWrapLength(newVal.doubleValue() - 250);
        });


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
    // ------------ OLTS


    // ------------ CONSULTA DE SINAL
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

        Label infoLabel = new Label("Verifique o Sinal Óptico da Primária.");
        infoLabel.getStyleClass().add("info-label");

        HBox formRow1 = new HBox(15);
        formRow1.setAlignment(Pos.CENTER_LEFT);

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");
        HBox.setHgrow(oltComboBox, Priority.ALWAYS);

        formRow1.getChildren().addAll(oltComboBox);

        HBox formRow2 = new HBox(15);
        formRow2.setAlignment(Pos.CENTER_LEFT);

        TextField fsField = new TextField();
        fsField.setPromptText("Digite o F/S");
        fsField.setMaxWidth(110);
        fsField.getStyleClass().add("text-field");

        TextField pField = new TextField();
        pField.setPromptText("Digite o P");
        pField.setMaxWidth(103);
        pField.getStyleClass().add("text-field");

        TextFormatter<String> pFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,3}")) {
                return change;
            }
            return null;
        });
        pField.setTextFormatter(pFormatter);


        formRow2.getChildren().addAll(fsField, pField);

        Button queryBtn = new Button("Consultar");
        queryBtn.getStyleClass().add("connect-btn");

        fsField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                queryBtn.fire();
            }
        });

        pField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                queryBtn.fire();
            }
        });

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.getStyleClass().add("text-area");
        resultArea.setPrefHeight(350);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        queryBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String p = pField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || p.isEmpty()) {
                resultArea.setText("Por favor, preencha todos os campos corretamente.");
                return;
            }

            resultArea.setText("Conectando e executando comandos à " + selectedOLT.name + " (" + selectedOLT.ip + ")...\n");

            Thread queryThread = new Thread(() -> {
                SSHManager tempSSHManager = new SSHManager();
                try {
                    tempSSHManager.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, resultArea);
                    String queryResult = tempSSHManager.queryOpticalSignal(fs, p);
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

        HBox exportRow = new HBox(10);
        exportRow.setAlignment(Pos.CENTER_RIGHT);

        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");

        exportBtn.setOnAction(e -> {
            String resultado = resultArea.getText();
            if (resultado.isEmpty()) {
                resultArea.setText("Nada para exportar. Faça uma consulta primeiro.");
                return;
            }

            Dialog<String> exportDialog = new Dialog<>();
            exportDialog.setTitle("Exportar");

            DialogPane dialogPane = exportDialog.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");

            ButtonType csvButton = new ButtonType("CSV", ButtonBar.ButtonData.OK_DONE);
            ButtonType pdfButton = new ButtonType("PDF", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialogPane.getButtonTypes().addAll(csvButton, pdfButton, cancelButton);

            VBox dialogContent = new VBox(10);
            dialogContent.setStyle("-fx-padding: 10;");
            Label label = new Label("Escolha o formato de exportação:\n" +
                    "Irá para a pasta consultas.");
            label.getStyleClass().add("info-label");
            dialogContent.getChildren().add(label);
            dialogPane.setContent(dialogContent);

            exportDialog.setOnShown(event -> {
                Button btnCSV = (Button) dialogPane.lookupButton(csvButton);
                Button btnPDF = (Button) dialogPane.lookupButton(pdfButton);
                Button btnCancel = (Button) dialogPane.lookupButton(cancelButton);

                btnCSV.getStyleClass().add("connect-btn");
                btnPDF.getStyleClass().add("connect-btn");
                btnCancel.getStyleClass().add("back-btn");
            });

            exportDialog.setResultConverter(dialogBtn -> {
                if (dialogBtn == csvButton) return "CSV";
                if (dialogBtn == pdfButton) return "PDF";
                return null;
            });

            exportDialog.showAndWait().ifPresent(formato -> {
                switch (formato) {
                    case "CSV":
                        exportarCSV(resultado);
                        break;
                    case "PDF":
                        exportarPDF(resultado);
                        break;
                }
            });
        });

        exportRow.getChildren().add(exportBtn);
        formArea.getChildren().addAll(infoLabel, formRow1, formRow2, queryBtn, resultArea, exportRow);
        content.getChildren().addAll(title, formArea);

        return content;
    }


    // exportar p csv
    private void exportarCSV(String texto) {
        try {
            File dir = new File("csv");
            if (!dir.exists()) dir.mkdir();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String nomeArquivo = "consultas/sinal_" + timestamp + ".csv";
            FileWriter writer = new FileWriter(nomeArquivo);

            for (String line : texto.split("\n")) {
                writer.append(line.replaceAll("\\s{2,}", ",").replaceAll("\\s", ",")).append("\n");
            }

            writer.flush();
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void exportarCSV(String texto, String tipo) {
        try {
            File dir = new File("consultas");
            if (!dir.exists()) dir.mkdir();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String nomeArquivo = "consultas/" + tipo + "_" + timestamp + ".csv";
            FileWriter writer = new FileWriter(nomeArquivo);
            writer.write(texto);
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // exportar p pdf
    private void exportarPDF(String texto) {
        try {
            File dir = new File("consultas");
            if (!dir.exists()) dir.mkdir();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String nomeArquivo = "consultas/sinal_" + timestamp + ".pdf";

            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, new FileOutputStream(nomeArquivo));
            document.open();
            document.add(new com.lowagie.text.Paragraph(texto));
            document.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void exportarPDF(String texto, String tipo) {
        try {
            File dir = new File("consultas");
            if (!dir.exists()) dir.mkdir();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String nomeArquivo = "consultas/" + tipo + "_" + timestamp + ".pdf";

            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, new FileOutputStream(nomeArquivo));
            document.open();
            document.add(new com.lowagie.text.Paragraph(texto));
            document.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    // ------------ CONSULTA DE SINAL


    // ------------ PON SUMMARY
    private Node createPONSummaryScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Resumo da PON");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));

        Label infoLabel = new Label("Verifique todas as informações da Primária.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");

        TextField ponField = new TextField();
        ponField.setPromptText("Digite o F/S/P");
        ponField.setMaxWidth(110);
        ponField.getStyleClass().add("text-field");

        TextFormatter<String> ponFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9/]{0,7}")) {
                return change;
            }
            return null;
        });
        ponField.setTextFormatter(ponFormatter);


        Button consultarBtn = new Button("Consultar");
        consultarBtn.getStyleClass().add("connect-btn");

        ponField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                consultarBtn.fire();
            }
        });

        TextArea resultadoArea = new TextArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("text-area");
        resultadoArea.setPrefHeight(350);

        consultarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String pon = ponField.getText().trim();

            if (selectedOLT == null || pon.isEmpty()) {
                resultadoArea.setText("Por favor, selecione a OLT e informe a PON.");
                return;
            }

            resultadoArea.setText("Conectando e executando comandos na " + selectedOLT.name + "...\n");

            Thread thread = new Thread(() -> {
                SSHManager tempSSH = new SSHManager();
                try {
                    tempSSH.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, resultadoArea);
                    Thread.sleep(800);
                    tempSSH.sendCommand("enable");
                    Thread.sleep(800);
                    tempSSH.sendCommand("config");
                    Thread.sleep(800);
                    tempSSH.sendCommand("display ont info summary " + pon);
                    Thread.sleep(8000);
                } catch (Exception ex) {
                    Platform.runLater(() -> resultadoArea.appendText("Erro: " + ex.getMessage()));
                } finally {
                    tempSSH.disconnect();
                }
            });

            thread.setDaemon(true);
            thread.start();
        });

        formArea.getChildren().addAll(infoLabel, oltComboBox, ponField, consultarBtn, resultadoArea);
        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ------------ PON SUMMARY


    // ------------ ONT/ONU BY-SN
    private Node createONUBySNScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Consulta ONT/ONU por SN");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));

        Label infoLabel = new Label("Verifique todos as informações do SN.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");

        TextField snField = new TextField();
        snField.setPromptText("Digite o SN da ONT/ONU");
        snField.setMaxWidth(228);
        snField.setId("snField");
        snField.getStyleClass().add("text-field");

        TextFormatter<String> snFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[A-Za-z0-9]{0,20}")) {
                return change;
            }
            return null;
        });
        snField.setTextFormatter(snFormatter);


        Button consultarBtn = new Button("Consultar");
        consultarBtn.getStyleClass().add("connect-btn");

        snField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                consultarBtn.fire();
            }
        });

        TextArea resultadoArea = new TextArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("text-area");
        resultadoArea.setPrefHeight(350);

        consultarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String sn = snField.getText().trim();

            if (selectedOLT == null || sn.isEmpty()) {
                resultadoArea.setText("Por favor, selecione a OLT e informe o SN do cliente.");
                return;
            }

            resultadoArea.setText("Conectando e buscando ONU com SN " + sn + "...\n");

            Thread thread = new Thread(() -> {
                SSHManager tempSSH = new SSHManager();
                try {
                    tempSSH.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, resultadoArea);
                    Thread.sleep(800);
                    tempSSH.sendCommand("enable");
                    Thread.sleep(800);
                    tempSSH.sendCommand("config");
                    Thread.sleep(800);
                    tempSSH.sendCommand("display ont info by-sn " + sn);
                    Thread.sleep(8000);
                } catch (Exception ex) {
                    Platform.runLater(() -> resultadoArea.appendText("Erro: " + ex.getMessage()));
                } finally {
                    tempSSH.disconnect();
                }
            });

            thread.setDaemon(true);
            thread.start();
        });

        formArea.getChildren().addAll(infoLabel, oltComboBox, snField, consultarBtn, resultadoArea);
        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ------------ ONT/ONU BY-SN


    // ------------ DIAGNÓSTICO DE QUEDAS
    private Node createDropDiagnosisScreen() {
        VBox content = new VBox(20);
        content.getStyleClass().add("content-area");
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Diagnóstico de Quedas da ONT/ONU");
        title.getStyleClass().add("title");

        VBox formArea = new VBox(15);
        formArea.getStyleClass().add("form-area");
        formArea.setMaxWidth(800);
        formArea.setPadding(new Insets(25));

        Label infoLabel = new Label("Verifique o diagnóstico de quedas da ONT/ONU.");
        infoLabel.getStyleClass().add("info-label");

        ComboBox<OLT> oltComboBox = new ComboBox<>();
        oltComboBox.getItems().addAll(OLTList.getOLTs());
        oltComboBox.setPromptText("Selecione a OLT");
        oltComboBox.getStyleClass().add("combo-box");

        HBox formRow = new HBox(15);
        formRow.setAlignment(Pos.CENTER_LEFT);

        TextField fsField = new TextField();
        fsField.setPromptText("Digite F/S");
        fsField.setMaxWidth(110);
        fsField.getStyleClass().add("text-field");

        TextFormatter<String> fsFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9/]{0,4}")) {
                return change;
            }
            return null;
        });
        fsField.setTextFormatter(fsFormatter);


        TextField pidField = new TextField();
        pidField.setPromptText("Digite o P ID");
        pidField.setMaxWidth(100);
        pidField.getStyleClass().add("text-field");

        TextFormatter<String> pidFormatter = new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("[0-9]{0,6}")) {
                return change;
            }
            return null;
        });
        pidField.setTextFormatter(pidFormatter);


        formRow.getChildren().addAll(fsField, pidField);

        Button diagnosticarBtn = new Button("Consultar");
        diagnosticarBtn.getStyleClass().add("connect-btn");

        fsField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                diagnosticarBtn.fire();
            }
        });

        pidField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                diagnosticarBtn.fire();
            }
        });

        TextArea resultadoArea = new TextArea();
        resultadoArea.setEditable(false);
        resultadoArea.getStyleClass().add("text-area");
        resultadoArea.setPrefHeight(350);

        diagnosticarBtn.setOnAction(e -> {
            OLT selectedOLT = oltComboBox.getValue();
            String fs = fsField.getText().trim();
            String pid = pidField.getText().trim();

            if (selectedOLT == null || fs.isEmpty() || pid.isEmpty()) {
                resultadoArea.setText("Preencha todos os campos corretamente.");
                return;
            }

            resultadoArea.setText("Executando diagnóstico na " + selectedOLT.name + "...\n");

            Thread thread = new Thread(() -> {
                SSHManager ssh = new SSHManager();
                try {
                    ssh.connect(selectedOLT.ip, Secrets.SSH_USER, Secrets.SSH_PASS, resultadoArea);
                    Thread.sleep(800);
                    ssh.sendCommand("enable");
                    Thread.sleep(800);
                    ssh.sendCommand("config");
                    Thread.sleep(800);
                    ssh.sendCommand("interface gpon " + fs);
                    Thread.sleep(800);
                    ssh.sendCommand("display ont register-info " + pid);
                    Thread.sleep(5000);
                } catch (Exception ex) {
                    Platform.runLater(() -> resultadoArea.appendText("Erro: " + ex.getMessage()));
                } finally {
                    ssh.disconnect();
                }
            });
            thread.setDaemon(true);
            thread.start();
        });

        formArea.getChildren().addAll(infoLabel, oltComboBox, formRow, diagnosticarBtn, resultadoArea);
        content.getChildren().addAll(title, formArea);
        return content;
    }
    // ------------ DIAGNÓSTICO DE QUEDAS


    // ------------ ROMPIMENTOS
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

        int totalOnts = rompimentosDetectados.stream()
                .mapToInt(d -> Integer.parseInt(d.getImpacted()))
                .sum();
        int criticos = (int) rompimentosDetectados.stream()
                .filter(d -> d.getStatus().equals("Crítico"))
                .count();
        int alertas = (int) rompimentosDetectados.stream()
                .filter(d -> d.getStatus().equals("Alerta"))
                .count();

        HBox statusRow = new HBox(15);
        statusRow.setAlignment(Pos.CENTER);

        VBox statusBox1 = createStatusBox("ONTs Ativos", String.valueOf(totalOnts), "status-normal");
        VBox statusBox2 = createStatusBox("Perda de Sinal", String.valueOf(alertas), "status-warning");
        VBox statusBox3 = createStatusBox("Rompimentos Críticos", String.valueOf(criticos), "status-critical");

        statusRow.getChildren().addAll(statusBox1, statusBox2, statusBox3);
        HBox.setHgrow(statusBox1, Priority.ALWAYS);
        HBox.setHgrow(statusBox2, Priority.ALWAYS);
        HBox.setHgrow(statusBox3, Priority.ALWAYS);

        Label breakagesTitle = new Label("Rompimentos Detectados");
        breakagesTitle.getStyleClass().add("subtitle");

        TableView<RompimentoData> tableView = new TableView<>();
        tableView.getStyleClass().add("data-table");
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<RompimentoData, String> oltCol = new TableColumn<>("OLT");
        oltCol.setCellValueFactory(cellData -> cellData.getValue().oltProperty());
        oltCol.setPrefWidth(150);

        TableColumn<RompimentoData, String> ponCol = new TableColumn<>("PON");
        ponCol.setCellValueFactory(cellData -> cellData.getValue().ponProperty());
        ponCol.setPrefWidth(100);

        TableColumn<RompimentoData, String> impactedCol = new TableColumn<>("Afetados");
        impactedCol.setCellValueFactory(cellData -> cellData.getValue().impactedProperty());
        impactedCol.setPrefWidth(120);

        TableColumn<RompimentoData, String> locationCol = new TableColumn<>("Localização");
        locationCol.setCellValueFactory(cellData -> cellData.getValue().locationProperty());
        locationCol.setPrefWidth(180);

        TableColumn<RompimentoData, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(120);

        statusCol.setCellFactory(column -> {
            return new TableCell<RompimentoData, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        if ("Crítico".equals(item)) {
                            setStyle("-fx-text-fill: #cc0000; -fx-font-weight: bold;");
                        } else if ("Alerta".equals(item)) {
                            setStyle("-fx-text-fill: #ff9900; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            };
        });

        TableColumn<RompimentoData, String> timeCol = new TableColumn<>("Tempo");
        timeCol.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
        timeCol.setPrefWidth(150);

        TableColumn<RompimentoData, String> predictionCol = new TableColumn<>("Previsão");
        predictionCol.setCellValueFactory(cellData -> new SimpleStringProperty(""));
        predictionCol.setPrefWidth(130);

        predictionCol.setCellFactory(column -> {
            return new TableCell<RompimentoData, String>() {
                private final Button button = new Button("Definir");

                {
                    button.setOnAction(event -> {
                        RompimentoData data = getTableView().getItems().get(getIndex());
                        TextInputDialog dialog = new TextInputDialog("");
                        dialog.setTitle("Previsão de Normalização");
                        dialog.setHeaderText("Defina uma previsão para normalização do rompimento");
                        dialog.setContentText("Previsão (horas):");

                        Optional<String> result = dialog.showAndWait();
                        result.ifPresent(hours -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Previsão Definida");
                            alert.setHeaderText(null);
                            alert.setContentText("Previsão de " + hours + " horas definida para " +
                                    data.getOlt() + " - PON " + data.getPon());
                            alert.showAndWait();
                        });
                    });
                    button.getStyleClass().add("small-btn");
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(button);
                    }
                }
            };
        });

        tableView.getColumns().addAll(oltCol, ponCol, impactedCol, locationCol, statusCol, timeCol, predictionCol);

        ScrollPane tableScroll = new ScrollPane(tableView);
        tableScroll.setFitToWidth(true);
        tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.getStyleClass().add("scroll-pane");

        tableView.setItems(rompimentosDetectados);

        Pagination pagination = new Pagination();
        pagination.setPageCount(calculatePageCount(rompimentosDetectados.size(), 10));
        pagination.setMaxPageIndicatorCount(5);
        pagination.getStyleClass().add("pagination");

        rompimentosDetectados.addListener((ListChangeListener.Change<? extends RompimentoData> c) -> {
            pagination.setPageCount(calculatePageCount(rompimentosDetectados.size(), 10));
        });

        int itemsPerPage = 10;
        pagination.setPageFactory(pageIndex -> {
            int fromIndex = pageIndex * itemsPerPage;
            int toIndex = Math.min(fromIndex + itemsPerPage, rompimentosDetectados.size());

            if (fromIndex >= rompimentosDetectados.size()) {
                tableView.setItems(FXCollections.observableArrayList());
            } else {
                tableView.setItems(FXCollections.observableArrayList(
                        rompimentosDetectados.subList(fromIndex, toIndex)
                ));
            }
            return tableScroll;
        });

        VBox tableContainer = new VBox(10, tableScroll, pagination);
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        Label lastScanLabel = new Label("Última verificação: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        lastScanLabel.getStyleClass().add("info-label");

        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Button refreshBtn = new Button("Atualizar");
        refreshBtn.getStyleClass().add("connect-btn");
        refreshBtn.setOnAction(e -> {
            tableView.setOpacity(0.5);
            refreshBtn.setDisable(true);

            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    runBreakageDetection();
                    return null;
                }
            };

            task.setOnSucceeded(event -> {
                Platform.runLater(() -> {
                    lastScanLabel.setText("Última verificação: " +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

                    FadeTransition fade = new FadeTransition(Duration.millis(500), tableView);
                    fade.setFromValue(0.5);
                    fade.setToValue(1.0);
                    fade.play();

                    refreshBtn.setDisable(false);
                });
            });

            new Thread(task).start();
        });


        Button exportBtn = new Button("Exportar");
        exportBtn.getStyleClass().add("connect-btn");
        exportBtn.setStyle("-fx-background-color: linear-gradient(to right, #5a6caf, #6a7cbf);");
        exportBtn.setOnAction(e -> {
            if (rompimentosDetectados.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Exportar");
                alert.setHeaderText(null);
                alert.setContentText("Nenhum dado disponível para exportação.");
                alert.showAndWait();
                return;
            }

            Dialog<String> exportDialog = new Dialog<>();
            exportDialog.setTitle("Exportar");

            DialogPane dialogPane = exportDialog.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            dialogPane.getStyleClass().add("dialog-pane");

            ButtonType csvButton = new ButtonType("CSV", ButtonBar.ButtonData.OK_DONE);
            ButtonType pdfButton = new ButtonType("PDF", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

            dialogPane.getButtonTypes().addAll(csvButton, pdfButton, cancelButton);

            VBox dialogContent = new VBox(10);
            dialogContent.setStyle("-fx-padding: 10;");
            Label label = new Label("Escolha o formato de exportação:");
            label.getStyleClass().add("info-label");
            dialogContent.getChildren().add(label);
            dialogPane.setContent(dialogContent);

            exportDialog.setResultConverter(dialogBtn -> {
                if (dialogBtn == csvButton) return "CSV";
                if (dialogBtn == pdfButton) return "PDF";
                return null;
            });

            exportDialog.showAndWait().ifPresent(formato -> {
                StringBuilder sb = new StringBuilder();
                sb.append("OLT,PON,Afetados,Localização,Status,Tempo\n");
                for (RompimentoData d : rompimentosDetectados) {
                    sb.append(d.getOlt()).append(",")
                            .append(d.getPon()).append(",")
                            .append(d.getImpacted()).append(",")
                            .append(d.getLocation()).append(",")
                            .append(d.getStatus()).append(",")
                            .append(d.getTime()).append("\n");
                }

                if (formato.equals("CSV")) {
                    exportarCSV(sb.toString(), "rompimentos");
                } else if (formato.equals("PDF")) {
                    exportarPDF(sb.toString(), "rompimentos");
                }
            });
        });


        actionRow.getChildren().addAll(refreshBtn, exportBtn);

        dashboardArea.getChildren().addAll(
                infoLabel,
                statusRow,
                breakagesTitle,
                tableContainer,
                lastScanLabel,
                actionRow
        );

        content.getChildren().addAll(title, dashboardArea);
        return content;
    }

    private int calculatePageCount(int itemsCount, int itemsPerPage) {
        return (itemsCount > 0) ? (int) Math.ceil((double) itemsCount / itemsPerPage) : 1;
    }
    // ------------ ROMPIMENTOS


    // ----------- ABA PRIVADA TICKET
    private Node createTechnicalTicketsScreen() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("content-area");

        Label title = new Label("Chamados");
        title.getStyleClass().add("title");

        TableView<Ticket> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setItems(FXCollections.observableArrayList(DatabaseManager.getAllTickets()));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().addAll(
                createColumn("OLT", "oltNome"),
                createColumn("Criado por", "criadoPor"),
                createColumn("Cargo", "cargo"),
                createColumn("Descrição", "descricao"),
                createColumn("Prioridade", "previsao"),
                createColumn("Data/Hora", "dataHora"),
                createColumn("Status", "status")
        );

        content.getChildren().addAll(title, table);
        return content;
    }

    private <T> TableColumn<Ticket, String> createColumn(String title, String prop) {
        TableColumn<Ticket, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        return col;
    }
    // ----------- ABA PRIVADA TICKET


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

        card.setPrefSize(180, 120);
        Rectangle clip = new Rectangle(180, 120);


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


    // -------- TERMINAL SCREEN
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
                sshManager.connect(olt.ip, Secrets.SSH_USER, Secrets.SSH_PASS, terminalArea);
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

        List<String> commandHistory = new ArrayList<>();
        int[] commandHistoryIndex = {-1};

        commandField.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER: {
                    String cmd = commandField.getText();
                    if (cmd != null && !cmd.trim().isEmpty() && sshManager != null) {
                        sshManager.sendCommand(cmd.trim());
                        commandHistory.add(cmd.trim());
                        commandHistoryIndex[0] = commandHistory.size();
                        commandField.clear();
                    }
                    break;
                }
                case UP:
                    if (!commandHistory.isEmpty() && commandHistoryIndex[0] > 0) {
                        commandHistoryIndex[0]--;
                        commandField.setText(commandHistory.get(commandHistoryIndex[0]));
                        commandField.positionCaret(commandField.getText().length());
                    }
                    break;
                case DOWN:
                    if (!commandHistory.isEmpty() && commandHistoryIndex[0] < commandHistory.size() - 1) {
                        commandHistoryIndex[0]++;
                        commandField.setText(commandHistory.get(commandHistoryIndex[0]));
                        commandField.positionCaret(commandField.getText().length());
                    } else {
                        commandHistoryIndex[0] = commandHistory.size();
                        commandField.clear();
                    }
                    break;
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
    // -------- TERMINAL SCREEN


    // ------------ AJUDA INSIDE-TERMINAL
    private void showHelpDialog() {
        Stage helpStage = new Stage();
        helpStage.initStyle(StageStyle.UNDECORATED);
        helpStage.initOwner(primaryStage);

        VBox helpContent = new VBox(15);
        helpContent.getStyleClass().add("help-content");
        helpContent.setPadding(new Insets(10));
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

        Button criarTicketBtn = new Button("Criar Ticket");
        criarTicketBtn.getStyleClass().add("connect-btn");
        criarTicketBtn.setOnAction(e -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Novo Ticket para Eduardo");

            DialogPane pane = dialog.getDialogPane();
            pane.getStyleClass().add("glass-pane");
            pane.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            VBox content = new VBox(12);
            content.setPadding(new Insets(15));

            Label descLabel = new Label("Descrição:");
            descLabel.getStyleClass().add("form-label");

            TextArea descricaoArea = new TextArea();
            descricaoArea.getStyleClass().add("text-area");

            Label prioridadeLabel = new Label("Prioridade:");
            prioridadeLabel.getStyleClass().add("form-label");

            ComboBox<String> prioridadeBox = new ComboBox<>();
            prioridadeBox.getItems().addAll("Baixa", "Média", "Alta", "Crítica");
            prioridadeBox.getStyleClass().add("combo-box");
            prioridadeBox.setPromptText("Selecione");

            content.getChildren().addAll(descLabel, descricaoArea, prioridadeLabel, prioridadeBox);
            pane.setContent(content);

            dialog.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    DatabaseManager.criarTicket(
                            usuario.getNome(),
                            usuario.getCargo(),
                            connectedOLT.name,
                            descricaoArea.getText(),
                            prioridadeBox.getValue()
                    );
                }
            });
        });


        helpContent.getChildren().addAll(title, commandsBox, closeBtn, criarTicketBtn);

        Scene helpScene = new Scene(helpContent, 550, 500);
        helpScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        helpStage.setScene(helpScene);
        helpStage.show();
    }
    // ------------ AJUDA INSIDE-TERMINAL


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


        // --------- GETTERS
        public String getOlt() {
            return olt.get();
        }

        public String getPon() {
            return pon.get();
        }

        public String getImpacted() {
            return impacted.get();
        }

        public String getLocation() {
            return location.get();
        }

        public String getStatus() {
            return status.get();
        }

        public String getTime() {
            return time.get();
        }

        // PROPERTYS
        public javafx.beans.property.StringProperty oltProperty() {
            return olt;
        }

        public javafx.beans.property.StringProperty ponProperty() {
            return pon;
        }

        public javafx.beans.property.StringProperty impactedProperty() {
            return impacted;
        }

        public javafx.beans.property.StringProperty locationProperty() {
            return location;
        }

        public javafx.beans.property.StringProperty statusProperty() {
            return status;
        }

        public javafx.beans.property.StringProperty timeProperty() {
            return time;
        }
    }


    public static void main(String[] args) {
        launch(args);
    }

    // --------- STOPS
    @Override
    public void stop() {
        if (sshManager != null) sshManager.disconnect();
        if (breakageMonitor != null) {
            breakageMonitor.shutdownNow();
            try {
                if (!breakageMonitor.awaitTermination(2, TimeUnit.SECONDS)) {
                    breakageMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                breakageMonitor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}