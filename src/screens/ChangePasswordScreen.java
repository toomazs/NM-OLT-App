package screens;

import database.DatabaseManager;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
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

        HBox titleBar = createTitleBar(stage, iconFileName);

        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setDelay(Duration.millis(100));

        ImageView icon = createIcon(iconFileName);
        Label headerLabel = new Label("Alteração de Senha");
        headerLabel.getStyleClass().add("olt-name");

        VBox headerBox = new VBox(10, icon, headerLabel);
        headerBox.setAlignment(Pos.CENTER);

        VBox formFields = createFormFields(stage);

        root.getChildren().addAll(headerBox, formFields);

        mainLayout.setTop(titleBar);
        mainLayout.setCenter(root);

        Scene scene = new Scene(mainLayout, 360, 520);
        ThemeManager.applyThemeToNewScene(scene);


        scene.getRoot().setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.6)));

        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.centerOnScreen();
        stage.setOnShown(e -> fadeIn.play());
        stage.showAndWait();
    }

    private static void loadWindowIcon(Stage stage, String iconFileName) {
        try {
            InputStream iconStream = ChangePasswordScreen.class.getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone da janela (ChangePwd): " + iconFileName + ". Tentando fallback.");
                iconStream = ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                System.err.println("Ícone de fallback da janela (ChangePwd) também não encontrado.");
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone da janela (ChangePwd): " + iconFileName + " - " + e.getMessage());
        }
    }

    private static HBox createTitleBar(Stage stage, String iconFileName) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPrefHeight(30);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        try {
            InputStream iconStream = ChangePasswordScreen.class.getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone da barra de título (ChangePwd): " + iconFileName + ". Tentando fallback.");
                iconStream = ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                titleBarIconView = new ImageView(new Image(iconStream));
                titleBarIconView.setFitHeight(18);
                titleBarIconView.setFitWidth(18);
                titleBarIconView.setPreserveRatio(true);
                HBox.setMargin(titleBarIconView, new Insets(0, 5, 0, 10));
            } else {
                System.err.println("Ícone de fallback da barra de título (ChangePwd) também não encontrado.");
                titleBarIconView = new ImageView();
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone da barra de título (ChangePwd): " + iconFileName + " - " + e.getMessage());
            titleBarIconView = new ImageView();
        }

        Label titleLabel = new Label("Alterar Senha");
        titleLabel.getStyleClass().add("olt-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().addAll("window-btn", "minimize-btn");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        Region buttonSpacer = new Region();
        buttonSpacer.setPrefWidth(5);

        Button closeBtn = new Button("×");
        closeBtn.getStyleClass().addAll("window-btn", "close-btn");
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

    private static ImageView createIcon(String iconFileName) {
        try {
            InputStream iconStream = ChangePasswordScreen.class.getResourceAsStream(iconFileName);
            if (iconStream == null) {
                System.err.println("Stream nulo para ícone principal (ChangePwd): " + iconFileName + ". Tentando fallback.");
                iconStream = ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png");
            }
            if (iconStream != null) {
                mainIconView = new ImageView(new Image(iconStream));
                mainIconView.setFitHeight(48);
                mainIconView.setFitWidth(48);
                mainIconView.setPreserveRatio(true);

                ScaleTransition pulse = new ScaleTransition(Duration.millis(2000), mainIconView);
                pulse.setFromX(1.0); pulse.setFromY(1.0);
                pulse.setToX(1.05); pulse.setToY(1.05);
                pulse.setCycleCount(Animation.INDEFINITE);
                pulse.setAutoReverse(true);
                pulse.play();
            } else {
                System.err.println("Ícone de fallback principal (ChangePwd) também não encontrado.");
                mainIconView = new ImageView();
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone principal (ChangePwd): " + iconFileName + " - " + e.getMessage());
            mainIconView = new ImageView();
        }
        return mainIconView;
    }

    private static VBox createFormFields(Stage stage) {
        TextField userField = new TextField();
        userField.setPromptText("Usuário");
        userField.setMaxWidth(260);
        userField.getStyleClass().add("modern-text-field");

        PasswordField newPassHidden = new PasswordField();
        newPassHidden.setPromptText("Nova Senha");
        newPassHidden.getStyleClass().add("modern-text-field");
        newPassHidden.setPrefWidth(230);

        TextField newPassVisible = new TextField();
        newPassVisible.setPromptText("Nova Senha");
        newPassVisible.getStyleClass().add("modern-text-field");
        newPassVisible.setPrefWidth(230);
        newPassVisible.setVisible(false);
        newPassVisible.setManaged(false);

        Button toggleNewPassBtn = new Button("\uD83D\uDC41\uFE0F");
        toggleNewPassBtn.getStyleClass().add("eye-button");

        toggleNewPassBtn.setOnAction(e -> togglePasswordVisibility(newPassHidden, newPassVisible, toggleNewPassBtn));

        HBox newPassBox = new HBox(5, newPassHidden, newPassVisible, toggleNewPassBtn);
        newPassBox.setAlignment(Pos.CENTER_LEFT);
        newPassBox.setMaxWidth(260);

        PasswordField confirmPassHidden = new PasswordField();
        confirmPassHidden.setPromptText("Confirmação");
        confirmPassHidden.getStyleClass().add("modern-text-field");
        confirmPassHidden.setPrefWidth(230);

        TextField confirmPassVisible = new TextField();
        confirmPassVisible.setPromptText("Confirmação");
        confirmPassVisible.getStyleClass().add("modern-text-field");
        confirmPassVisible.setPrefWidth(230);
        confirmPassVisible.setVisible(false);
        confirmPassVisible.setManaged(false);

        Button toggleConfirmPassBtn = new Button("\uD83D\uDC41\uFE0F");
        toggleConfirmPassBtn.getStyleClass().add("eye-button");

        toggleConfirmPassBtn.setOnAction(e -> togglePasswordVisibility(confirmPassHidden, confirmPassVisible, toggleConfirmPassBtn));

        HBox confirmPassBox = new HBox(5, confirmPassHidden, confirmPassVisible, toggleConfirmPassBtn);
        confirmPassBox.setAlignment(Pos.CENTER_LEFT);
        confirmPassBox.setMaxWidth(260);

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

        Button voltarBtn = new Button("Voltar");
        voltarBtn.setPrefWidth(260);
        voltarBtn.getStyleClass().add("secondary-button");


        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);

        HBox progressBox = new HBox(10, progressIndicator);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setMinHeight(20);


        alterarBtn.setOnAction(e -> {
            String usuario = userField.getText().trim();
            String newPassword = newPassHidden.isVisible() ? newPassHidden.getText() : newPassVisible.getText();
            String confirmPassword = confirmPassHidden.isVisible() ? confirmPassHidden.getText() : confirmPassVisible.getText();

            if (usuario.isEmpty()) {
                showStatusMessage(status, "Nome de usuário é obrigatório.", true);
                return;
            }
            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                showStatusMessage(status, "Preencha e confirme a nova senha.", true);
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                showStatusMessage(status, "As senhas não coincidem.", true);
                return;
            }

            alterarBtn.setDisable(true);
            voltarBtn.setDisable(true);
            progressIndicator.setVisible(true);
            status.setText("Alterando senha...");
            status.getStyleClass().removeAll("error-label", "success-label");

            PauseTransition pause = new PauseTransition(Duration.millis(600));
            pause.setOnFinished(event -> {
                boolean success = DatabaseManager.changePassword(usuario, newPassword);

                progressIndicator.setVisible(false);
                alterarBtn.setDisable(false);
                voltarBtn.setDisable(false);

                if (success) {
                    showStatusMessage(status, "Senha alterada com sucesso!", false);
                    userField.clear();
                    newPassHidden.clear();
                    newPassVisible.clear();
                    confirmPassHidden.clear();
                    confirmPassVisible.clear();
                } else {
                    showStatusMessage(status, "Erro ao alterar senha. Verifique o usuário.", true);
                }
            });
            pause.play();
        });

        voltarBtn.setOnAction(e -> {
            Node rootNode = stage.getScene().getRoot();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootNode);
            fadeOut.setFromValue(rootNode.getOpacity());
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> stage.close());
            fadeOut.play();
        });

        userField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) newPassHidden.requestFocus();
        });
        newPassHidden.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) confirmPassHidden.requestFocus();
        });
        newPassVisible.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) confirmPassHidden.requestFocus();
        });
        confirmPassHidden.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) alterarBtn.fire();
        });
        confirmPassVisible.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) alterarBtn.fire();
        });



        VBox formFieldsVBox = new VBox(15);
        formFieldsVBox.setAlignment(Pos.CENTER);
        formFieldsVBox.getChildren().addAll(
                userField,
                newPassBox,
                confirmPassBox,
                alterarBtn,
                voltarBtn,
                progressBox,
                status
        );

        return formFieldsVBox;
    }

    private static void togglePasswordVisibility(PasswordField hiddenField, TextField visibleField, Button toggleButton) {
        if (hiddenField.isVisible()) {
            visibleField.setText(hiddenField.getText());
            hiddenField.setVisible(false);
            hiddenField.setManaged(false);
            visibleField.setVisible(true);
            visibleField.setManaged(true);
            toggleButton.setText("\uD83D\uDC41\uFE0F");
            visibleField.requestFocus();
            visibleField.positionCaret(visibleField.getText().length());
        } else {
            hiddenField.setText(visibleField.getText());
            visibleField.setVisible(false);
            visibleField.setManaged(false);
            hiddenField.setVisible(true);
            hiddenField.setManaged(true);
            toggleButton.setText("\uD83D\uDC41");
            hiddenField.requestFocus();
            hiddenField.positionCaret(hiddenField.getText().length());
        }
    }


    private static void showStatusMessage(Label statusLabel, String message, boolean isError) {
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