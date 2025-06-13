package screens;

import database.DatabaseManager;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import utils.ConfigManager;
import utils.ThemeManager;
import java.io.InputStream;

public class ChangePasswordScreen {
    private static double xOffset = 0;
    private static double yOffset = 0;
    private static ImageView titleBarIconView;
    private static ImageView mainIconView;

    public static void show(Stage owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);

        ConfigManager configManager = ConfigManager.getInstance();
        String currentThemeFile = configManager.getTheme();
        String iconFileName = ThemeManager.getIconFileNameForTheme(currentThemeFile);

        loadWindowIcon(stage, iconFileName);

        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("change-password-background");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(mainLayout.widthProperty());
        clip.heightProperty().bind(mainLayout.heightProperty());
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        mainLayout.setClip(clip);

        HBox titleBar = createTitleBar(stage, iconFileName);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setOpacity(0);

        ImageView icon = createIcon(iconFileName);
        Label headerLabel = new Label("Alteração de Senha");
        headerLabel.getStyleClass().add("olt-name");

        VBox headerBox = new VBox(10, icon, headerLabel);
        headerBox.setAlignment(Pos.CENTER);

        VBox formFields = createFormFields(stage, root);

        root.getChildren().addAll(headerBox, formFields);

        mainLayout.setTop(titleBar);
        mainLayout.setCenter(root);

        Scene scene = new Scene(mainLayout, 400, 550);
        ThemeManager.applyThemeToNewScene(scene);

        DropShadow shadow = new DropShadow();
        shadow.setRadius(25);
        shadow.setOffsetX(0);
        shadow.setOffsetY(10);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        scene.getRoot().setEffect(shadow);

        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);
        scene.setFill(Color.TRANSPARENT);
        stage.centerOnScreen();

        Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), 0),
                        new KeyValue(root.translateYProperty(), 30)
                ),
                new KeyFrame(Duration.millis(1000),
                        new KeyValue(root.opacityProperty(), 1, Interpolator.EASE_OUT),
                        new KeyValue(root.translateYProperty(), 0, Interpolator.SPLINE(0.25, 0.46, 0.45, 0.94))
                )
        );
        fadeIn.play();

        stage.showAndWait();
    }

    private static void loadWindowIcon(Stage stage, String iconFileName) {
        try {
            InputStream iconStream = ChangePasswordScreen.class.getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
        }
    }

    private static HBox createTitleBar(Stage stage, String iconFileName) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPrefHeight(35);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        try {
            InputStream iconStream = ChangePasswordScreen.class.getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                titleBarIconView = new ImageView(new Image(iconStream));
                titleBarIconView.setFitHeight(20);
                titleBarIconView.setFitWidth(20);
                titleBarIconView.setPreserveRatio(true);
                HBox.setMargin(titleBarIconView, new Insets(0, 8, 0, 15));
            } else {
                titleBarIconView = new ImageView();
            }
        } catch (Exception e) {
            titleBarIconView = new ImageView();
        }

        Label titleLabel = new Label("Alterar Senha");
        titleLabel.getStyleClass().add("olt-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Region buttonSpacer = new Region();
        buttonSpacer.setPrefWidth(8);

        Button closeBtn = createModernWindowButton("✕", "close-btn");
        closeBtn.setOnAction(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(100), closeBtn);
            scale.setFromX(1.0); scale.setFromY(1.0);
            scale.setToX(0.9); scale.setToY(0.9);
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

        titleBar.getChildren().addAll(titleBarIconView, titleLabel, spacer, buttonSpacer, closeBtn);
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

    private static Button createModernWindowButton(String text, String styleClass) {
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

    private static ImageView createIcon(String iconFileName) {
        try {
            InputStream iconStream = ChangePasswordScreen.class.getResourceAsStream(iconFileName);
            if (iconStream == null) {
                iconStream = ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                mainIconView = new ImageView(new Image(iconStream));
                mainIconView.setFitHeight(48);
                mainIconView.setFitWidth(48);
                mainIconView.setPreserveRatio(true);

                mainIconView.getStyleClass().add("icon-shadow");

            } else {
                mainIconView = new ImageView();
            }
        } catch (Exception e) {
            mainIconView = new ImageView();
        }
        return mainIconView;
    }

    private static VBox createFormFields(Stage stage, Node... fieldsToDisable) {
        TextField userField = new TextField();
        userField.setPromptText("Usuário");
        userField.setMaxWidth(260);
        userField.getStyleClass().add("modern-text-field");
        addFieldFocusEffects(userField);

        PasswordField currentPassHidden = new PasswordField();
        currentPassHidden.setPromptText("Senha Atual");
        currentPassHidden.getStyleClass().add("modern-text-field");
        currentPassHidden.setPrefWidth(230);
        addFieldFocusEffects(currentPassHidden);

        TextField currentPassVisible = new TextField();
        currentPassVisible.setPromptText("Senha Atual");
        currentPassVisible.getStyleClass().add("modern-text-field");
        currentPassVisible.setPrefWidth(230);
        currentPassVisible.setVisible(false);
        currentPassVisible.setManaged(false);
        addFieldFocusEffects(currentPassVisible);

        currentPassHidden.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                if (!currentPassHidden.getStyleClass().contains("has-text")) {
                    currentPassHidden.getStyleClass().add("has-text");
                }
            } else {
                currentPassHidden.getStyleClass().remove("has-text");
            }
        });

        Button toggleCurrentPassBtn = new Button("👁");
        toggleCurrentPassBtn.getStyleClass().add("floating-btn");
        toggleCurrentPassBtn.setOnAction(e -> togglePasswordVisibility(currentPassHidden, currentPassVisible));

        HBox currentPassBox = new HBox(5, currentPassHidden, toggleCurrentPassBtn);
        currentPassBox.setAlignment(Pos.CENTER_LEFT);
        currentPassBox.setMaxWidth(260);
        StackPane currentPassPane = new StackPane(currentPassHidden, currentPassVisible);
        HBox currentPassLayout = new HBox(5, currentPassPane, toggleCurrentPassBtn);
        currentPassLayout.setAlignment(Pos.CENTER_LEFT);
        currentPassLayout.setMaxWidth(260);

        PasswordField newPassHidden = new PasswordField();
        newPassHidden.setPromptText("Nova Senha");
        newPassHidden.getStyleClass().add("modern-text-field");
        newPassHidden.setPrefWidth(230);
        addFieldFocusEffects(newPassHidden);

        newPassHidden.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                if (!newPassHidden.getStyleClass().contains("has-text")) {
                    newPassHidden.getStyleClass().add("has-text");
                }
            } else {
                newPassHidden.getStyleClass().remove("has-text");
            }
        });

        TextField newPassVisible = new TextField();
        newPassVisible.setPromptText("Nova Senha");
        newPassVisible.getStyleClass().add("modern-text-field");
        newPassVisible.setPrefWidth(230);
        newPassVisible.setVisible(false);
        newPassVisible.setManaged(false);
        addFieldFocusEffects(newPassVisible);

        Button toggleNewPassBtn = new Button("👁");
        toggleNewPassBtn.getStyleClass().add("floating-btn");
        toggleNewPassBtn.setOnAction(e -> togglePasswordVisibility(newPassHidden, newPassVisible));

        StackPane newPassPane = new StackPane(newPassHidden, newPassVisible);
        HBox newPassLayout = new HBox(5, newPassPane, toggleNewPassBtn);
        newPassLayout.setAlignment(Pos.CENTER_LEFT);
        newPassLayout.setMaxWidth(260);

        PasswordField confirmPassHidden = new PasswordField();
        confirmPassHidden.setPromptText("Confirme a Nova Senha");
        confirmPassHidden.getStyleClass().add("modern-text-field");
        confirmPassHidden.setPrefWidth(230);
        addFieldFocusEffects(confirmPassHidden);

        confirmPassHidden.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                if (!confirmPassHidden.getStyleClass().contains("has-text")) {
                    confirmPassHidden.getStyleClass().add("has-text");
                }
            } else {
                confirmPassHidden.getStyleClass().remove("has-text");
            }
        });

        TextField confirmPassVisible = new TextField();
        confirmPassVisible.setPromptText("Confirme a Nova Senha");
        confirmPassVisible.getStyleClass().add("modern-text-field");
        confirmPassVisible.setPrefWidth(230);
        confirmPassVisible.setVisible(false);
        confirmPassVisible.setManaged(false);
        addFieldFocusEffects(confirmPassVisible);

        Button toggleConfirmPassBtn = new Button("👁");
        toggleConfirmPassBtn.getStyleClass().add("floating-btn");
        toggleConfirmPassBtn.setOnAction(e -> togglePasswordVisibility(confirmPassHidden, confirmPassVisible));

        StackPane confirmPassPane = new StackPane(confirmPassHidden, confirmPassVisible);
        HBox confirmPassLayout = new HBox(5, confirmPassPane, toggleConfirmPassBtn);
        confirmPassLayout.setAlignment(Pos.CENTER_LEFT);
        confirmPassLayout.setMaxWidth(260);

        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.setMaxWidth(260);
        status.setWrapText(true);
        status.setMinHeight(40);

        Button alterarBtn = new Button("Alterar Senha");
        alterarBtn.setPrefWidth(260);
        alterarBtn.getStyleClass().add("modern-button");
        alterarBtn.setId("change-password-button");
        alterarBtn.setDefaultButton(true);
        addModernButtonEffects(alterarBtn);

        Button voltarBtn = new Button("Voltar");
        voltarBtn.setPrefWidth(260);
        voltarBtn.getStyleClass().add("secondary-button");
        addModernButtonEffects(voltarBtn);

        alterarBtn.setOnAction(e -> {
            String usuario = userField.getText().trim();
            String currentPassword = currentPassHidden.isVisible() ? currentPassHidden.getText() : currentPassVisible.getText();
            String newPassword = newPassHidden.isVisible() ? newPassHidden.getText() : newPassVisible.getText();
            String confirmPassword = confirmPassHidden.isVisible() ? confirmPassHidden.getText() : confirmPassVisible.getText();

            if (usuario.isEmpty()) {
                showStatusMessage(status, "Nome de usuário é obrigatório.", true); return;
            }
            if (currentPassword.isEmpty()) {
                showStatusMessage(status, "A senha atual é obrigatória.", true); return;
            }
            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                showStatusMessage(status, "Preencha e confirme a nova senha.", true); return;
            }
            if (!newPassword.equals(confirmPassword)) {
                showStatusMessage(status, "As novas senhas não coincidem.", true); return;
            }
            if (!DatabaseManager.verifyCurrentPassword(usuario, currentPassword)) {
                showStatusMessage(status, "Senha atual incorreta.", true); return;
            }

            alterarBtn.setDisable(true);
            voltarBtn.setDisable(true);
            userField.setDisable(true);

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setMaxSize(22, 22);
            String originalText = alterarBtn.getText();
            Label loadingLabel = new Label("Alterando...");
            loadingLabel.getStyleClass().add("loading-label");
            HBox loadingBox = new HBox(8, progressIndicator, loadingLabel);
            loadingBox.setAlignment(Pos.CENTER);
            alterarBtn.setGraphic(loadingBox);
            alterarBtn.setText("");
            status.setText("");

            Thread changePassThread = new Thread(() -> {
                boolean success = DatabaseManager.changePassword(usuario, newPassword);

                Platform.runLater(() -> {
                    alterarBtn.setGraphic(null);
                    alterarBtn.setText(originalText);
                    alterarBtn.setDisable(false);
                    voltarBtn.setDisable(false);
                    userField.setDisable(false);

                    if (success) {
                        showStatusMessage(status, "Senha alterada com sucesso!", false);
                        userField.clear();
                        currentPassHidden.clear(); currentPassVisible.clear();
                        newPassHidden.clear(); newPassVisible.clear();
                        confirmPassHidden.clear(); confirmPassVisible.clear();
                    } else {
                        showStatusMessage(status, "Erro ao alterar senha.", true);
                    }
                });
            });
            changePassThread.setDaemon(true);
            changePassThread.start();
        });

        voltarBtn.setOnAction(e -> {
            Node rootNode = stage.getScene().getRoot();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootNode);
            fadeOut.setFromValue(rootNode.getOpacity());
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> stage.close());
            fadeOut.play();
        });

        userField.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) currentPassHidden.requestFocus(); });
        currentPassHidden.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) newPassHidden.requestFocus(); });
        newPassHidden.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) confirmPassHidden.requestFocus(); });
        confirmPassHidden.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) alterarBtn.fire(); });

        VBox formFieldsVBox = new VBox(15);
        formFieldsVBox.setAlignment(Pos.CENTER);
        formFieldsVBox.getChildren().addAll(
                userField,
                currentPassLayout,
                newPassLayout,
                confirmPassLayout,
                status,
                alterarBtn,
                voltarBtn
        );
        VBox.setMargin(status, new Insets(0, 0, 5, 0));


        return formFieldsVBox;
    }

    private static void togglePasswordVisibility(PasswordField hiddenField, TextField visibleField) {
        if (hiddenField.isVisible()) {
            visibleField.setText(hiddenField.getText());
            hiddenField.setVisible(false);
            hiddenField.setManaged(false);
            visibleField.setVisible(true);
            visibleField.setManaged(true);
            visibleField.requestFocus();
            visibleField.positionCaret(visibleField.getText().length());
        } else {
            hiddenField.setText(visibleField.getText());
            visibleField.setVisible(false);
            visibleField.setManaged(false);
            hiddenField.setVisible(true);
            hiddenField.setManaged(true);
            hiddenField.requestFocus();
            hiddenField.positionCaret(hiddenField.getText().length());
        }
    }

    private static void addFieldFocusEffects(Control field) {
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), field);
                scaleIn.setToX(1.02); scaleIn.setToY(1.02);
                scaleIn.setInterpolator(Interpolator.EASE_OUT);
                scaleIn.play();
                field.setEffect(new Glow(0.3));
            } else {
                ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), field);
                scaleOut.setToX(1.0); scaleOut.setToY(1.0);
                scaleOut.setInterpolator(Interpolator.EASE_OUT);
                scaleOut.play();
                field.setEffect(null);
            }
        });
    }

    private static void addModernButtonEffects(Button button) {
        button.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), button);
            scale.setToX(1.05); scale.setToY(1.05);
            scale.setInterpolator(Interpolator.EASE_OUT);
            scale.play();
            DropShadow shadow = new DropShadow(12, Color.rgb(100, 150, 255, 0.4));
            shadow.setOffsetY(4);
            button.setEffect(shadow);
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), button);
            scale.setToX(1.0); scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);
            scale.play();
            button.setEffect(null);
        });

        button.setOnMousePressed(e -> {
            ScaleTransition press = new ScaleTransition(Duration.millis(50), button);
            press.setToX(0.98); press.setToY(0.98);
            press.play();
        });

        button.setOnMouseReleased(e -> {
            ScaleTransition release = new ScaleTransition(Duration.millis(100), button);
            release.setToX(1.05); release.setToY(1.05);
            release.play();
        });
    }

    private static void showStatusMessage(Label statusLabel, String message, boolean isError) {
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