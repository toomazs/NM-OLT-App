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


public class LoginScreen {
    private Usuario usuarioLogado;
    private double xOffset = 0;
    private double yOffset = 0;

    public Usuario showLogin(Stage stage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #1e293b, #0f172a);");

        HBox titleBar = createTitleBar(stage);

        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.CENTER);
        content.setOpacity(0);

        Image iconImage = null;
        try {
            iconImage = new Image(LoginScreen.class.getResourceAsStream("/oltapp-icon.png"));
            stage.getIcons().add(iconImage);
        } catch (Exception e) {
            System.out.println("Ícone não encontrado: " + e.getMessage());
        }

        VBox titleBox = createTitleBox(iconImage);

        VBox form = createLoginForm(stage);

        content.getChildren().addAll(titleBox, form);

        mainLayout.setTop(titleBar);
        mainLayout.setCenter(content);

        Scene scene = createScene(mainLayout);

        try {
            stage.getIcons().add(new Image(LoginScreen.class.getResourceAsStream("/oltapp-icon.png")));
        } catch (Exception e) {
            System.out.println("Ícone não encontrado: " + e.getMessage());
        }

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

    private HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPrefHeight(30);
        titleBar.setAlignment(Pos.CENTER_RIGHT);
        titleBar.setStyle("-fx-background-color: #1e293b;");

        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        Label titleLabel = new Label("ㅤㅤㅤㅤLogin");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5 5 5 5;");

        Region spacerRight = new Region();
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().add("window-button");

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("window-button", "window-close-button");

        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        closeBtn.setOnAction(e -> {
            BorderPane root = (BorderPane) stage.getScene().getRoot();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> stage.close());
            fadeOut.play();
        });

        titleBar.getChildren().addAll(spacerLeft, titleLabel, spacerRight, minimizeBtn, closeBtn);

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

    private VBox createTitleBox(Image iconImage) {
        ImageView icon = iconImage != null ? new ImageView(iconImage) : new ImageView();

        if (iconImage != null) {
            icon.setFitHeight(64);
            icon.setFitWidth(64);

            ScaleTransition pulse = new ScaleTransition(Duration.millis(2000), icon);
            pulse.setFromX(1.0);
            pulse.setFromY(1.0);
            pulse.setToX(1.05);
            pulse.setToY(1.05);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
        }

        Label title = new Label("Gerenciador de OLTs");
        title.setStyle("-fx-font-size: 22px; -fx-text-fill: white; -fx-font-weight: bold;");

        VBox titleBox = new VBox(10, icon, title);
        titleBox.setAlignment(Pos.CENTER);

        return titleBox;
    }

    private VBox createLoginForm(Stage stage) {
        TextField userField = new TextField();
        userField.setPromptText("Usuário");
        userField.setMaxWidth(250);
        userField.getStyleClass().add("modern-text-field");

        userField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                userField.setStyle("-fx-border-color: #3b82f6; -fx-border-width: 0 0 2 0;");
            } else {
                userField.setStyle("-fx-border-color: #475569; -fx-border-width: 0 0 1 0;");
            }
        });

        PasswordField passField = new PasswordField();
        passField.setPromptText("Senha");
        passField.setMaxWidth(250);
        passField.getStyleClass().add("modern-text-field");

        passField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                passField.setStyle("-fx-border-color: #3b82f6; -fx-border-width: 0 0 2 0;");
            } else {
                passField.setStyle("-fx-border-color: #475569; -fx-border-width: 0 0 1 0;");
            }
        });

        Button loginBtn = new Button("Entrar");
        loginBtn.getStyleClass().add("modern-button");
        loginBtn.setMaxWidth(250);

        loginBtn.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), loginBtn);
            scale.setToX(1.03);
            scale.setToY(1.03);
            scale.play();
        });

        loginBtn.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), loginBtn);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
        });

        passField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                loginBtn.fire();
            }
        });

        userField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                loginBtn.fire();
            }
        });

        Button alterarSenhaBtn = new Button("Alterar Senha");
        alterarSenhaBtn.getStyleClass().add("link-button");
        alterarSenhaBtn.setMaxWidth(250);

        Label status = new Label();
        status.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-font-size: 12px;");

        loginBtn.setOnAction(e -> {
            String usuario = userField.getText().trim();
            String senha = passField.getText().trim();

            loginBtn.setDisable(true);

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setMaxSize(20, 20);
            progressIndicator.setStyle("-fx-progress-color: white;");

            String originalText = loginBtn.getText();

            HBox loadingBox = new HBox(5);
            loadingBox.setAlignment(Pos.CENTER);
            loadingBox.getChildren().addAll(progressIndicator, new Label("Verificando..."));

            loginBtn.setGraphic(loadingBox);
            loginBtn.setText("");

            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(event -> {
                Usuario usuarioObj = DatabaseManager.login(usuario, senha);
                if (usuarioObj != null) {
                    usuarioLogado = usuarioObj;

                    BorderPane root = (BorderPane) stage.getScene().getRoot();
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
                    fadeOut.setFromValue(1.0);
                    fadeOut.setToValue(0.0);
                    fadeOut.setOnFinished(finishEvent -> stage.close());
                    fadeOut.play();
                } else {
                    loginBtn.setGraphic(null);
                    loginBtn.setText(originalText);
                    loginBtn.setDisable(false);

                    status.setText("Usuário ou senha inválidos.");
                    status.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-font-size: 12px;");

                    TranslateTransition shake = new TranslateTransition(Duration.millis(50), status);
                    shake.setFromX(0);
                    shake.setByX(5);
                    shake.setCycleCount(6);
                    shake.setAutoReverse(true);
                    shake.play();
                }
            });
            pause.play();
        });

        alterarSenhaBtn.setOnAction(e -> {
            ChangePasswordScreen.show(stage);
        });

        VBox form = new VBox(15, userField, passField, loginBtn, alterarSenhaBtn, status);
        form.setAlignment(Pos.CENTER);

        return form;
    }

    private Scene createScene(BorderPane mainLayout) {
        Scene scene = new Scene(mainLayout, 380, 480);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        return scene;
    }

    public class ShakeTransition extends Transition {
        private final Node node;
        private final double initialX;
        private static final int SHAKE_COUNT = 3;

        public ShakeTransition(Node node) {
            this.node = node;
            this.initialX = node.getTranslateX();
            setCycleDuration(Duration.millis(100 * SHAKE_COUNT));
            setInterpolator(Interpolator.EASE_BOTH);
        }

        @Override
        protected void interpolate(double frac) {
            double offset = 10 * Math.sin(SHAKE_COUNT * Math.PI * frac);
            node.setTranslateX(initialX + offset);
        }
    }
}