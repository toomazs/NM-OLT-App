package screens;

import database.DatabaseManager;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
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
    private VBox splashContent;
    private VBox loginContent;
    private BorderPane mainLayout;

    public Usuario showLogin(Stage stage) {
        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("login-background");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(mainLayout.widthProperty());
        clip.heightProperty().bind(mainLayout.heightProperty());
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        mainLayout.setClip(clip);

        String currentThemeFile = configManager.getTheme();
        String iconFileName = ThemeManager.getIconFileNameForTheme(currentThemeFile);

        loadWindowIcon(stage, iconFileName);

        createSplashContent(iconFileName);
        createLoginContent(stage, iconFileName);

        mainLayout.setCenter(splashContent);

        Scene scene = createScene(mainLayout);
        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);
        scene.setFill(Color.TRANSPARENT);
        stage.centerOnScreen();

        DropShadow shadow = new DropShadow();
        shadow.setRadius(25);
        shadow.setOffsetX(0);
        shadow.setOffsetY(10);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        scene.getRoot().setEffect(shadow);

        startSplashSequence();

        stage.showAndWait();
        return usuarioLogado;
    }

    private void createSplashContent(String iconFileName) {
        splashContent = new VBox(25);
        splashContent.setPadding(new Insets(60, 50, 60, 50));
        splashContent.setAlignment(Pos.CENTER);
        splashContent.setOpacity(0);

        ImageView splashIcon;
        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                splashIcon = new ImageView(new Image(iconStream));
                splashIcon.setFitHeight(100);
                splashIcon.setFitWidth(100);
                splashIcon.setPreserveRatio(true);
                splashIcon.getStyleClass().add("icon-shadow");
            } else {
                splashIcon = new ImageView();
            }
        } catch (Exception e) {
            splashIcon = new ImageView();
        }

        Label title = new Label("NM OLT App");
        title.getStyleClass().add("olt-name");

        Rectangle separator = new Rectangle(80, 2);
        separator.getStyleClass().add("separator");

        Label loadingLabel = new Label("Carregando...");
        loadingLabel.setStyle("-fx-font-weight: bold;");
        loadingLabel.getStyleClass().add("loading-label");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(30, 30);
        progressIndicator.getStyleClass().add("modern-progress");

        VBox loadingBox = new VBox(15);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.getChildren().addAll(progressIndicator, loadingLabel);

        splashContent.getChildren().addAll(splashIcon, title, separator, loadingBox);
    }

    private void createLoginContent(Stage stage, String iconFileName) {
        loginContent = new VBox();
        loginContent.setOpacity(0);
        loginContent.setTranslateY(50);

        HBox titleBar = createTitleBar(stage, iconFileName);

        VBox content = new VBox(30);
        content.setPadding(new Insets(40, 50, 50, 50));
        content.setAlignment(Pos.CENTER);

        VBox titleBox = createTitleBox(iconFileName);
        VBox form = createLoginForm(stage);

        content.getChildren().addAll(titleBox, form);

        loginContent.getChildren().addAll(titleBar, content);
    }

    private void startSplashSequence() {
        Timeline splashFadeIn = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(splashContent.opacityProperty(), 0),
                        new KeyValue(splashContent.translateYProperty(), 30)
                ),
                new KeyFrame(Duration.millis(800),
                        new KeyValue(splashContent.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(splashContent.translateYProperty(), 0, Interpolator.SPLINE(0.25, 0.46, 0.45, 0.94))
                )
        );

        PauseTransition splashPause = new PauseTransition(Duration.millis(1000));

        Timeline splashToLogin = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(splashContent.opacityProperty(), 1),
                        new KeyValue(splashContent.translateYProperty(), 0),
                        new KeyValue(loginContent.opacityProperty(), 0),
                        new KeyValue(loginContent.translateYProperty(), 50)
                ),
                new KeyFrame(Duration.millis(600),
                        new KeyValue(splashContent.opacityProperty(), 0, Interpolator.EASE_IN),
                        new KeyValue(splashContent.translateYProperty(), -30, Interpolator.EASE_IN),
                        new KeyValue(loginContent.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(loginContent.translateYProperty(), 0, Interpolator.SPLINE(0.25, 0.46, 0.45, 0.94))
                )
        );

        SequentialTransition fullSequence = new SequentialTransition(
                splashFadeIn,
                splashPause,
                new Timeline(new KeyFrame(Duration.millis(1), e -> {
                    mainLayout.getChildren().clear();
                    mainLayout.setCenter(loginContent);
                })),
                splashToLogin
        );

        fullSequence.play();
    }

    private void loadWindowIcon(Stage stage, String iconFileName) {
        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
        }
    }

    private HBox createTitleBar(Stage stage, String iconFileName) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPrefHeight(35);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                titleBarIconView = new ImageView(new Image(iconStream));
                titleBarIconView.setFitHeight(20);
                titleBarIconView.setFitWidth(20);
                titleBarIconView.setPreserveRatio(true);

                DropShadow iconGlow = new DropShadow();
                iconGlow.setRadius(8);
                iconGlow.setColor(Color.rgb(255, 255, 255, 0.3));
                titleBarIconView.setEffect(iconGlow);

                HBox.setMargin(titleBarIconView, new Insets(0, 8, 0, 15));
            } else {
                titleBarIconView = new ImageView();
            }
        } catch (Exception e) {
            titleBarIconView = new ImageView();
        }

        Label titleLabel = new Label("Login");
        titleLabel.getStyleClass().add("olt-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = createModernWindowButton("—", "minimize-btn");
        minimizeBtn.setOnAction(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(100), minimizeBtn);
            scale.setFromX(1.0);
            scale.setFromY(1.0);
            scale.setToX(0.9);
            scale.setToY(0.9);
            scale.setAutoReverse(true);
            scale.setCycleCount(2);
            scale.setOnFinished(event -> stage.setIconified(true));
            scale.play();
        });

        Region buttonSpacer = new Region();
        buttonSpacer.setPrefWidth(8);

        Button closeBtn = createModernWindowButton("✕", "close-btn");
        closeBtn.setOnAction(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(100), closeBtn);
            scale.setFromX(1.0);
            scale.setFromY(1.0);
            scale.setToX(0.9);
            scale.setToY(0.9);
            scale.setAutoReverse(true);
            scale.setCycleCount(2);
            scale.setOnFinished(event -> {
                Node rootNode = stage.getScene().getRoot();
                FadeTransition fadeOut = new FadeTransition(Duration.millis(250), rootNode);
                fadeOut.setFromValue(rootNode.getOpacity());
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(finishEvent -> stage.close());
                fadeOut.play();
            });
            scale.play();
        });

        titleBar.getChildren().addAll(titleBarIconView, titleLabel, spacer, minimizeBtn, buttonSpacer, closeBtn);
        HBox.setMargin(closeBtn, new Insets(0, 15, 0, 0));

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

    private Button createModernWindowButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("window-btn", styleClass);
        button.setPadding(new Insets(8, 12, 8, 12));

        button.setOnMouseEntered(e -> {
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), button);
            scaleIn.setToX(1.1);
            scaleIn.setToY(1.1);
            scaleIn.setInterpolator(Interpolator.EASE_OUT);
            scaleIn.play();
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), button);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.setInterpolator(Interpolator.EASE_OUT);
            scaleOut.play();
        });

        return button;
    }

    private VBox createTitleBox(String iconFileName) {
        try {
            InputStream iconStream = getClass().getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                mainIconView = new ImageView(new Image(iconStream));
                mainIconView.setFitHeight(80);
                mainIconView.setFitWidth(80);
                mainIconView.setPreserveRatio(true);
                mainIconView.getStyleClass().add("icon-shadow");
            } else {
                mainIconView = new ImageView();
            }
        } catch (Exception e) {
            mainIconView = new ImageView();
        }

        Label title = new Label("NM OLT App");
        title.getStyleClass().add("olt-name");

        Rectangle separator = new Rectangle(60, 2);
        separator.getStyleClass().add("separator");

        VBox titleBox = new VBox(15, mainIconView, title, separator);
        titleBox.setAlignment(Pos.CENTER);

        return titleBox;
    }

    private VBox createLoginForm(Stage stage) {
        VBox fieldsContainer = new VBox(20);
        fieldsContainer.setAlignment(Pos.CENTER);
        fieldsContainer.setPadding(new Insets(30, 40, 30, 40));
        fieldsContainer.getStyleClass().add("glass-container");

        TextField userField = new TextField();
        userField.setPromptText("Usuário");
        userField.setMaxWidth(280);
        userField.getStyleClass().add("modern-text-field");
        addFieldFocusEffects(userField);

        PasswordField passField = new PasswordField();
        passField.setPromptText("Senha");
        passField.setMaxWidth(280);
        passField.getStyleClass().add("modern-text-field");
        addFieldFocusEffects(passField);

        passField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                if (!passField.getStyleClass().contains("has-text")) {
                    passField.getStyleClass().add("has-text");
                }
            } else {
                passField.getStyleClass().remove("has-text");
            }
        });

        String lastUser = configManager.getLastUser();
        if (lastUser != null && !lastUser.isEmpty()) {
            userField.setText(lastUser);
            Platform.runLater(passField::requestFocus);
        }

        Button loginBtn = new Button("Entrar");
        loginBtn.getStyleClass().add("login-btn");
        loginBtn.setId("login-button");
        loginBtn.setMaxWidth(280);
        loginBtn.setDefaultButton(true);
        addModernButtonEffects(loginBtn);

        Button alterarSenhaBtn = new Button("Alterar Senha");
        alterarSenhaBtn.getStyleClass().add("login-btn");
        alterarSenhaBtn.setMaxWidth(280);
        addModernButtonEffects(alterarSenhaBtn);

        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.setWrapText(true);
        status.setMaxWidth(280);
        status.setMinHeight(35);

        fieldsContainer.getChildren().addAll(userField, passField, loginBtn, alterarSenhaBtn, status);

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
            progressIndicator.setMaxSize(22, 22);
            String originalText = loginBtn.getText();
            Label loadingLabel = new Label("Verificando...");
            loadingLabel.getStyleClass().add("loading-label");
            HBox loadingBox = new HBox(8, progressIndicator, loadingLabel);
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

                                showStatusMessage(status, "Bem-vindo, " + (usuarioLogado.getNome()) + "!", false);

                                PauseTransition pause = new PauseTransition(Duration.seconds(1.8));
                                pause.setOnFinished(event -> {
                                    Node rootNode = stage.getScene().getRoot();
                                    FadeTransition fadeOut = new FadeTransition(Duration.millis(400), rootNode);
                                    fadeOut.setFromValue(rootNode.getOpacity());
                                    fadeOut.setToValue(0.0);
                                    fadeOut.setOnFinished(finishEvent -> stage.close());
                                    fadeOut.play();
                                });
                                pause.play();

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

        VBox form = new VBox(25, fieldsContainer);
        form.setAlignment(Pos.CENTER);

        return form;
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

    private void addModernButtonEffects(Button button) {
        button.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), button);
            scale.setToX(1.05);
            scale.setToY(1.05);
            scale.setInterpolator(Interpolator.EASE_OUT);
            scale.play();

            DropShadow shadow = new DropShadow();
            shadow.setRadius(12);
            shadow.setColor(Color.rgb(100, 150, 255, 0.4));
            shadow.setOffsetY(4);
            button.setEffect(shadow);
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), button);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);
            scale.play();

            button.setEffect(null);
        });

        button.setOnMousePressed(e -> {
            ScaleTransition press = new ScaleTransition(Duration.millis(50), button);
            press.setToX(0.98);
            press.setToY(0.98);
            press.play();
        });

        button.setOnMouseReleased(e -> {
            ScaleTransition release = new ScaleTransition(Duration.millis(100), button);
            release.setToX(1.05);
            release.setToY(1.05);
            release.play();
        });
    }

    private Scene createScene(BorderPane mainLayout) {
        Scene scene = new Scene(mainLayout, 400, 550);
        ThemeManager.applyThemeToNewScene(scene);
        return scene;
    }

    private void showStatusMessage(Label statusLabel, String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("error-label", "success-label");
        statusLabel.setAlignment(Pos.CENTER);

        if (isError) {
            statusLabel.getStyleClass().add("error-label");
            TranslateTransition shake = new TranslateTransition(Duration.millis(50), statusLabel);
            shake.setFromX(0);
            shake.setByX(8);
            shake.setCycleCount(6);
            shake.setAutoReverse(true);
            shake.setInterpolator(Interpolator.EASE_BOTH);
            shake.play();
        } else {
            statusLabel.getStyleClass().add("success-label");
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), statusLabel);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        }
    }
}