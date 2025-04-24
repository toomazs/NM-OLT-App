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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class ChangePasswordScreen {
    private static double xOffset = 0;
    private static double yOffset = 0;

    public static void show(Stage owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);

        try {
            stage.getIcons().add(new Image(ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png")));
        } catch (Exception e) {
            System.out.println("Ícone não encontrado: " + e.getMessage());
        }

        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #140F26, #19132D);");

        // Barra de título
        HBox titleBar = createTitleBar(stage);

        // Conteúdo principal - configurado para animação inicial
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setOpacity(0); // Começa invisível para a animação de entrada

        // Animação de entrada suave
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setDelay(Duration.millis(100));

        // Adiciona o ícone no topo
        ImageView icon = createIcon();

        // Título da tela
        Label headerLabel = new Label("Alteração de Senha");
        headerLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: white; -fx-font-weight: bold;");

        VBox headerBox = new VBox(10, icon, headerLabel);
        headerBox.setAlignment(Pos.CENTER);

        // Campos do formulário com animações
        VBox formFields = createFormFields(stage);

        root.getChildren().addAll(headerBox, formFields);

        mainLayout.setTop(titleBar);
        mainLayout.setCenter(root);

        Scene scene = new Scene(mainLayout, 340, 480);
        scene.getStylesheets().add("file:resources/style.css");

        // Adiciona efeito de sombra
        scene.getRoot().setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.6)));

        stage.setScene(scene);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.centerOnScreen();

        // Inicia a animação quando a tela é exibida
        stage.setOnShown(e -> fadeIn.play());

        stage.showAndWait();
    }

    private static HBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setPrefHeight(30);
        titleBar.setAlignment(Pos.CENTER_RIGHT);
        titleBar.setStyle("-fx-background-color: linear-gradient(to bottom right, #140F26, #19132D);");

        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        Label titleLabel = new Label("ㅤㅤㅤㅤAlterar Senha");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 0 0 0 10;");

        Region spacerRight = new Region();
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().add("window-button");

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("window-button", "window-close-button");

        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        closeBtn.setOnAction(e -> {
            // Animação de saída
            BorderPane root = (BorderPane) stage.getScene().getRoot();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> stage.close());
            fadeOut.play();
        });

        titleBar.getChildren().addAll(spacerLeft, titleLabel, spacerRight, minimizeBtn, closeBtn);

        // Comportamento de arrastar a janela
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

    private static ImageView createIcon() {
        Image iconImage = null;
        try {
            iconImage = new Image(ChangePasswordScreen.class.getResourceAsStream("/oltapp-icon.png"));
        } catch (Exception e) {
            System.out.println("Ícone não encontrado: " + e.getMessage());
        }

        ImageView icon = iconImage != null ? new ImageView(iconImage) : new ImageView();

        if (iconImage != null) {
            icon.setFitHeight(48);
            icon.setFitWidth(48);

            // Adiciona efeito de pulsação sutil ao ícone
            ScaleTransition pulse = new ScaleTransition(Duration.millis(2000), icon);
            pulse.setFromX(1.0);
            pulse.setFromY(1.0);
            pulse.setToX(1.05);
            pulse.setToY(1.05);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
        }

        return icon;
    }

    private static VBox createFormFields(Stage stage) {
        TextField userField = new TextField();
        userField.setPromptText("Usuário");
        userField.setMaxWidth(260);
        userField.getStyleClass().add("modern-text-field");

        // Animação de foco para campo de usuário
        userField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                userField.setStyle("-fx-border-color: #6b46c1; -fx-border-width: 0 0 2 0;");
            } else {
                userField.setStyle("-fx-border-color: #2d3748; -fx-border-width: 0 0 1 0;");
            }
        });

        // Criação do campo de nova senha com botão de visualização
        PasswordField newPassHidden = new PasswordField();
        newPassHidden.setPromptText("Nova Senha");
        newPassHidden.setMaxWidth(300); // Reduzido para dar espaço ao botão
        newPassHidden.getStyleClass().add("modern-text-field");

        TextField newPassVisible = new TextField();
        newPassVisible.setPromptText("Nova Senha");
        newPassVisible.setMaxWidth(300);
        newPassVisible.getStyleClass().add("modern-text-field");
        newPassVisible.setVisible(false); // Inicialmente invisível
        newPassVisible.setManaged(false); // Não ocupa espaço quando invisível

        Button toggleNewPassBtn = new Button("\uD83D\uDC41"); // Emoji de olho
        toggleNewPassBtn.getStyleClass().add("eye-button");
        toggleNewPassBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a5568;");

        // Configurar o botão de alternar visibilidade
        toggleNewPassBtn.setOnAction(e -> {
            if (newPassHidden.isVisible()) {
                newPassVisible.setText(newPassHidden.getText());
                newPassHidden.setVisible(false);
                newPassHidden.setManaged(false);
                newPassVisible.setVisible(true);
                newPassVisible.setManaged(true);
                toggleNewPassBtn.setText("\uD83D\uDC41\u200D\u2620"); // Olho riscado
            } else {
                newPassHidden.setText(newPassVisible.getText());
                newPassHidden.setVisible(true);
                newPassHidden.setManaged(true);
                newPassVisible.setVisible(false);
                newPassVisible.setManaged(false);
                toggleNewPassBtn.setText("\uD83D\uDC41"); // Olho normal
            }
        });

        // Animação de foco para nova senha
        newPassHidden.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                newPassHidden.setStyle("-fx-border-color: #6b46c1; -fx-border-width: 0 0 2 0;");
            } else {
                newPassHidden.setStyle("-fx-border-color: #2d3748; -fx-border-width: 0 0 1 0;");
            }
        });

        newPassVisible.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                newPassVisible.setStyle("-fx-border-color: #6b46c1; -fx-border-width: 0 0 2 0;");
            } else {
                newPassVisible.setStyle("-fx-border-color: #2d3748; -fx-border-width: 0 0 1 0;");
            }
        });

        // Layout para campo e botão de nova senha
        HBox newPassBox = new HBox(5);
        newPassBox.setAlignment(Pos.CENTER_LEFT);
        newPassBox.getChildren().addAll(newPassHidden, newPassVisible, toggleNewPassBtn);
        newPassBox.setMaxWidth(260);

        // Criação do campo de confirmação de senha com botão de visualização
        PasswordField confirmPassHidden = new PasswordField();
        confirmPassHidden.setPromptText("Confirmação");
        confirmPassHidden.setMaxWidth(300); // Reduzido para dar espaço ao botão
        confirmPassHidden.getStyleClass().add("modern-text-field");

        TextField confirmPassVisible = new TextField();
        confirmPassVisible.setPromptText("Confirmação");
        confirmPassVisible.setMaxWidth(300);
        confirmPassVisible.getStyleClass().add("modern-text-field");
        confirmPassVisible.setVisible(false);
        confirmPassVisible.setManaged(false);

        Button toggleConfirmPassBtn = new Button("\uD83D\uDC41");
        toggleConfirmPassBtn.getStyleClass().add("eye-button");
        toggleConfirmPassBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a5568;");

        // Configurar o botão de alternar visibilidade da confirmação
        toggleConfirmPassBtn.setOnAction(e -> {
            if (confirmPassHidden.isVisible()) {
                confirmPassVisible.setText(confirmPassHidden.getText());
                confirmPassHidden.setVisible(false);
                confirmPassHidden.setManaged(false);
                confirmPassVisible.setVisible(true);
                confirmPassVisible.setManaged(true);
                toggleConfirmPassBtn.setText("\uD83D\uDC41\u200D\u2620"); // Olho riscado
            } else {
                confirmPassHidden.setText(confirmPassVisible.getText());
                confirmPassHidden.setVisible(true);
                confirmPassHidden.setManaged(true);
                confirmPassVisible.setVisible(false);
                confirmPassVisible.setManaged(false);
                toggleConfirmPassBtn.setText("\uD83D\uDC41"); // Olho normal
            }
        });

        // Animação de foco para confirmação de senha
        confirmPassHidden.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                confirmPassHidden.setStyle("-fx-border-color: #6b46c1; -fx-border-width: 0 0 2 0;");
            } else {
                confirmPassHidden.setStyle("-fx-border-color: #2d3748; -fx-border-width: 0 0 1 0;");
            }
        });

        confirmPassVisible.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                confirmPassVisible.setStyle("-fx-border-color: #6b46c1; -fx-border-width: 0 0 2 0;");
            } else {
                confirmPassVisible.setStyle("-fx-border-color: #2d3748; -fx-border-width: 0 0 1 0;");
            }
        });

        // Layout para campo e botão de confirmação de senha
        HBox confirmPassBox = new HBox(5);
        confirmPassBox.setAlignment(Pos.CENTER_LEFT);
        confirmPassBox.getChildren().addAll(confirmPassHidden, confirmPassVisible, toggleConfirmPassBtn);
        confirmPassBox.setMaxWidth(260);

        // Label para status com espaço adequado
        Label status = new Label();
        status.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
        status.setMaxWidth(260);
        status.setWrapText(true);
        status.setMinHeight(60);

        // Botões com animações
        Button alterarBtn = new Button("Alterar Senha");
        alterarBtn.setPrefWidth(260);
        alterarBtn.getStyleClass().add("modern-button");

        // Efeito hover para o botão alterar
        alterarBtn.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), alterarBtn);
            scale.setToX(1.03);
            scale.setToY(1.03);
            scale.play();
        });

        alterarBtn.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), alterarBtn);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
        });

        Button voltarBtn = new Button("Voltar");
        voltarBtn.setPrefWidth(260);
        voltarBtn.getStyleClass().add("secondary-button");

        // Efeito hover para o botão voltar
        voltarBtn.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), voltarBtn);
            scale.setToX(1.03);
            scale.setToY(1.03);
            scale.play();
        });

        voltarBtn.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), voltarBtn);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
        });

        // Indicador de progresso
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(20, 20);
        progressIndicator.setVisible(false);

        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.getChildren().add(progressIndicator);

        // Organiza os botões centralizados
        VBox buttonBox = new VBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(alterarBtn, voltarBtn, progressBox);

        alterarBtn.setOnAction(e -> {
            String currentPassword = newPassHidden.isVisible() ? newPassHidden.getText() : newPassVisible.getText();
            String confirmPassword = confirmPassHidden.isVisible() ? confirmPassHidden.getText() : confirmPassVisible.getText();

            if (userField.getText().trim().isEmpty()) {
                showErrorAnimation(status, "Preencha o nome de usuário.");
                return;
            }

            if (currentPassword.isEmpty() || confirmPassword.isEmpty()) {
                showErrorAnimation(status, "Preencha ambos os campos de senha.");
                return;
            }

            if (!currentPassword.equals(confirmPassword)) {
                showErrorAnimation(status, "Senhas não coincidem.");
                return;
            }

            // Mostra o indicador de progresso e desabilita o botão
            progressIndicator.setVisible(true);
            alterarBtn.setDisable(true);
            voltarBtn.setDisable(true);

            // Simula um pequeno atraso para mostrar o processamento
            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(event -> {
                boolean success = DatabaseManager.changePassword(userField.getText(), currentPassword);

                // Esconde o indicador de progresso e reabilita os botões
                progressIndicator.setVisible(false);
                alterarBtn.setDisable(false);
                voltarBtn.setDisable(false);

                if (success) {
                    status.setStyle("-fx-text-fill: #68d391; -fx-font-weight: bold; -fx-font-size: 13px;");
                    status.setText("Senha alterada com sucesso!");
                    userField.clear();
                    newPassHidden.clear();
                    newPassVisible.clear();
                    confirmPassHidden.clear();
                    confirmPassVisible.clear();

                    // Animação de sucesso
                    FadeTransition fadeStatus = new FadeTransition(Duration.millis(300), status);
                    fadeStatus.setFromValue(0.3);
                    fadeStatus.setToValue(1.0);
                    fadeStatus.play();
                } else {
                    showErrorAnimation(status, "Erro ao alterar senha.");
                }
            });
            pause.play();
        });

        voltarBtn.setOnAction(e -> {
            // Animação de saída
            BorderPane root = (BorderPane) stage.getScene().getRoot();
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), root);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> stage.close());
            fadeOut.play();
        });

        VBox formFields = new VBox(15);
        formFields.setAlignment(Pos.CENTER);
        formFields.getChildren().addAll(userField, newPassBox, confirmPassBox, buttonBox, status);

        return formFields;
    }

    // Método auxiliar para animação de erro
    private static void showErrorAnimation(Label status, String message) {
        status.setText(message);
        status.setStyle("-fx-text-fill: #fc8181; -fx-font-weight: bold; -fx-font-size: 13px;");

        // Animação de tremor para indicar erro
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), status);
        shake.setFromX(0);
        shake.setByX(5);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
    }
}