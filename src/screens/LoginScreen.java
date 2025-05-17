package screens;

import database.DatabaseManager;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import models.Usuario;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import utils.ConfigManager;
import utils.ThemeManager;
import javafx.application.Platform;
import java.io.InputStream;
import database.LoginResultStatus;
import java.util.Optional;

public class LoginScreen {
    private Usuario usuarioLogado;
    private double xOffset = 0;
    private double yOffset = 0;
    private ConfigManager configManager = ConfigManager.getInstance();
    private ImageView titleBarIconView;
    private ImageView mainIconView;

    public Usuario showLogin(Stage stage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("login-background");

        String currentThemeFile = configManager.getTheme();
        String iconFileName = ThemeManager.getIconFileNameForTheme(currentThemeFile);

        HBox titleBar = createTitleBar(stage, iconFileName);

        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.CENTER);
        content.setOpacity(0);

        loadWindowIcon(stage, iconFileName);

        VBox titleBox = createTitleBox(iconFileName);
        VBox form = createLoginForm(stage);
        content.getChildren().addAll(titleBox, form);
        mainLayout.setTop(titleBar);
        mainLayout.setCenter(content);

        Scene scene = createScene(mainLayout);

        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.centerOnScreen();

        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(content.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(800), new KeyValue(content.opacityProperty(), 1, Interpolator.EASE_BOTH))
        );
        fadeIn.play();

        scene.getRoot().setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.6)));

        stage.showAndWait();
        return usuarioLogado;
    }

    private void loadWindowIcon(Stage stage, String iconFileName) {
        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone da janela: " + iconFileName + ". Tentando fallback.");
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                System.err.println("Ícone de fallback da janela também não encontrado.");
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone da janela: " + iconFileName + " - " + e.getMessage());
        }
    }


    private HBox createTitleBar(Stage stage, String iconFileName) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPrefHeight(30);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone da barra de título: " + iconFileName + ". Tentando fallback.");
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                titleBarIconView = new ImageView(new Image(iconStream));
                titleBarIconView.setFitHeight(18);
                titleBarIconView.setFitWidth(18);
                titleBarIconView.setPreserveRatio(true);
                HBox.setMargin(titleBarIconView, new Insets(0, 5, 0, 10));
            } else {
                System.err.println("Ícone de fallback da barra de título também não encontrado.");
                titleBarIconView = new ImageView();
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone da barra de título: " + iconFileName + " - " + e.getMessage());
            titleBarIconView = new ImageView();
        }

        Label titleLabel = new Label("Login");
        titleLabel.getStyleClass().add("olt-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().addAll("window-btn", "minimize-btn");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        Region buttonSpacer = new Region();
        buttonSpacer.setPrefWidth(5);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("close-btn", "window-btn");
        closeBtn.setPadding(new Insets(12, 12, 12, 12));
        closeBtn.setOnAction(e -> {
            Node rootNode = stage.getScene().getRoot();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootNode);
            fadeOut.setFromValue(rootNode.getOpacity());
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> stage.close());
            fadeOut.play();
        });

        titleBar.getChildren().addAll(titleBarIconView, titleLabel, spacer, minimizeBtn, buttonSpacer, closeBtn);
        HBox.setMargin(closeBtn, new Insets(0, 10, 0, 0));

        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        return titleBar;
    }

    private VBox createTitleBox(String iconFileName) {
        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone principal: " + iconFileName + ". Tentando fallback.");
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                mainIconView = new ImageView(new Image(iconStream));
                mainIconView.setFitHeight(64);
                mainIconView.setFitWidth(64);
                mainIconView.setPreserveRatio(true);

                ScaleTransition pulse = new ScaleTransition(Duration.millis(2000), mainIconView);
                pulse.setFromX(1.0); pulse.setFromY(1.0);
                pulse.setToX(1.05); pulse.setToY(1.05);
                pulse.setCycleCount(Animation.INDEFINITE);
                pulse.setAutoReverse(true);
                pulse.play();
            } else {
                System.err.println("Ícone de fallback principal também não encontrado.");
                mainIconView = new ImageView();
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone principal: " + iconFileName + " - " + e.getMessage());
            mainIconView = new ImageView();
        }


        Label title = new Label("Gerenciador de OLTs");
        title.getStyleClass().add("olt-name");

        VBox titleBox = new VBox(10, mainIconView, title);
        titleBox.setAlignment(Pos.CENTER);

        return titleBox;
    }


    private VBox createLoginForm(Stage stage) {
        TextField userField = new TextField();
        userField.setPromptText("Usuário");
        userField.setMaxWidth(250);
        userField.getStyleClass().add("modern-text-field");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Senha");
        passField.setMaxWidth(250);
        passField.getStyleClass().add("modern-text-field");

        String lastUser = configManager.getLastUser();
        if (lastUser != null && !lastUser.isEmpty()) {
            userField.setText(lastUser);
            Platform.runLater(passField::requestFocus);
        }

        Button loginBtn = new Button("Entrar");
        loginBtn.getStyleClass().add("modern-button");
        loginBtn.setId("login-button");
        loginBtn.setMaxWidth(250);
        loginBtn.setDefaultButton(true);

        loginBtn.setOnMouseEntered(e -> loginBtn.setScaleX(1.03));
        loginBtn.setOnMouseExited(e -> loginBtn.setScaleX(1.0));
        loginBtn.setOnMousePressed(e -> loginBtn.setScaleX(0.98));
        loginBtn.setOnMouseReleased(e -> loginBtn.setScaleX(1.03));

        Button alterarSenhaBtn = new Button("Alterar Senha");
        alterarSenhaBtn.getStyleClass().add("link-button");
        alterarSenhaBtn.setMaxWidth(250);

        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        status.setMaxWidth(250);
        status.setMinHeight(30);

        loginBtn.setOnAction(e -> {
            String usuario = userField.getText().trim();
            String senha = passField.getText().trim();

            if (usuario.isEmpty() || senha.isEmpty()) {
                showStatusMessage(status, "Usuário e senha são obrigatórios.", true);
                return;
            }

            configManager.setLastUser(usuario);

            loginBtn.setDisable(true);
            alterarSenhaBtn.setDisable(true);
            userField.setDisable(true);
            passField.setDisable(true);

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setMaxSize(20, 20);
            String originalText = loginBtn.getText();
            Label loadingLabel = new Label("Verificando...");
            loadingLabel.getStyleClass().add("loading-label");
            HBox loadingBox = new HBox(5, progressIndicator, loadingLabel);
            loadingBox.setAlignment(Pos.CENTER);
            loginBtn.setGraphic(loadingBox);
            loginBtn.setText("");
            status.setText("");

            Thread loginThread = new Thread(() -> {
                LoginResultStatus loginStatus = DatabaseManager.attemptLogin(usuario, senha);

                Platform.runLater(() -> {
                    loginBtn.setGraphic(null);
                    loginBtn.setText(originalText);
                    loginBtn.setDisable(false);
                    alterarSenhaBtn.setDisable(false);
                    userField.setDisable(false);
                    passField.setDisable(false);

                    switch (loginStatus) {
                        case SUCCESS:
                            Optional<Usuario> userOpt = DatabaseManager.getUsuarioByUsername(usuario);
                            if (userOpt.isPresent()) {
                                usuarioLogado = userOpt.get();
                                DatabaseManager.logUsuario(usuarioLogado.getNome(), "Fez login no sistema");

                                Node rootNode = stage.getScene().getRoot();
                                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), rootNode);
                                fadeOut.setFromValue(rootNode.getOpacity());
                                fadeOut.setToValue(0.0);
                                fadeOut.setOnFinished(finishEvent -> stage.close());
                                fadeOut.play();
                            } else {
                                showStatusMessage(status, "❌ Erro ao carregar dados do usuário após login.", true);
                            }
                            break;

                        case ALREADY_LOGGED_IN:
                            showStatusMessage(status, "❌ Este usuário já está logado em outra máquina.", true);
                            passField.clear();
                            passField.requestFocus();
                            break;

                        case INVALID_CREDENTIALS:
                            showStatusMessage(status, "❌ Usuário ou senha inválidos.", true);
                            passField.clear();
                            passField.requestFocus();
                            break;

                        case DATABASE_ERROR:
                        default:
                            showStatusMessage(status, "❌ Erro de conexão com o banco de dados.", true);
                            break;
                    }
                });
            });
            loginThread.setDaemon(true);
            loginThread.start();

        });

        alterarSenhaBtn.setOnAction(e -> {
            ChangePasswordScreen.show(stage);
        });

        userField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                passField.requestFocus();
            }
        });
        passField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                loginBtn.fire();
            }
        });


        VBox form = new VBox(15, userField, passField, loginBtn, alterarSenhaBtn, status);
        form.setAlignment(Pos.CENTER);

        return form;
    }

    private Scene createScene(BorderPane mainLayout) {
        Scene scene = new Scene(mainLayout, 380, 475);
        ThemeManager.applyThemeToNewScene(scene);
        return scene;
    }

    private void showStatusMessage(Label statusLabel, String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("error-label", "success-label");
        if (isError) {
            statusLabel.getStyleClass().add("error-label");
            TranslateTransition shake = new TranslateTransition(Duration.millis(60), statusLabel);
            shake.setFromX(0);
            shake.setByX(6);
            shake.setCycleCount(4);
            shake.setAutoReverse(true);
            shake.play();
        } else {
            statusLabel.getStyleClass().add("success-label");
        }
    }

}