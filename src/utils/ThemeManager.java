package utils;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.animation.FadeTransition;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class ThemeManager {
    private static Scene currentScene;
    private static ConfigManager configManager = ConfigManager.getInstance();

    public static String getIconFileNameForTheme(String themeCssFile) {
        if (themeCssFile == null) {
            themeCssFile = "style.css";
        }

        return switch (themeCssFile) {
            case "style.css" -> "/oltapp-icon.png";
            case "style-allblack.css" -> "/oltapp-icon-black.png";
            case "style-allwhite.css" -> "/oltapp-icon-white.png";
            case "style-dracula.css" -> "/oltapp-icon-dracula.png";
            case "style-nightowl.css" -> "/oltapp-icon-nightowl.png";
            case "style-lightowl.css" -> "/oltapp-icon-lightowl.png";
            case "style-creme.css" -> "/oltapp-icon-creme.png";
            case "style-blue.css" -> "/oltapp-icon-blue.png";
            case "style-green.css" -> "/oltapp-icon-green.png";
            case "style-red.css" -> "/oltapp-icon-red.png";
            case "style-pink.css" -> "/oltapp-icon-pink.png";
            default -> "/oltapp-icon.png";
        };
    }

    public static void applyTheme(Scene scene, String themeName) {
        if (scene == null) {
            System.err.println("ThemeManager: Tentativa de aplicar tema a uma cena nula.");
            return;
        }
        currentScene = scene;

        Platform.runLater(() -> {
            if (currentScene.getRoot() instanceof Pane rootPane) {
                Rectangle overlay = new Rectangle();
                overlay.setFill(Color.BLACK);
                overlay.setOpacity(0.0);

                if (!rootPane.getChildren().contains(overlay)) {
                    rootPane.getChildren().add(overlay);
                }
                overlay.toFront();

                if (rootPane instanceof Region) {
                    overlay.widthProperty().bind(((Region) rootPane).widthProperty());
                    overlay.heightProperty().bind(((Region) rootPane).heightProperty());
                } else {
                    overlay.widthProperty().bind(currentScene.widthProperty());
                    overlay.heightProperty().bind(currentScene.heightProperty());
                }
                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), overlay);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(0.7);

                FadeTransition fadeOut = new FadeTransition(Duration.millis(250), overlay);
                fadeOut.setFromValue(0.7);
                fadeOut.setToValue(0.0);

                fadeOut.setOnFinished(event -> {
                    if (rootPane.getChildren().contains(overlay)) {
                        rootPane.getChildren().remove(overlay);
                    }
                    overlay.widthProperty().unbind();
                    overlay.heightProperty().unbind();
                });

                fadeIn.setOnFinished(event -> {
                    if (applyThemeStylesheets(currentScene, themeName)) {
                    }
                    fadeOut.play();
                });

                fadeIn.play();
            } else {
                System.out.println("Aplicando tema diretamente (root não é Pane ou cena nula).");
                if (applyThemeStylesheets(currentScene, themeName)) {
                }
            }
        });
    }

    public static void applyThemeToNewScene(Scene scene) {
        String themeName = configManager.getTheme();
        applyThemeStylesheets(scene, themeName);
    }

    private static boolean applyThemeStylesheets(Scene scene, String themeName) {
        if (themeName == null || themeName.isEmpty()) {
            themeName = "style.css";
        }
        scene.getStylesheets().clear();

        try {
            String themePath = "/resources/" + themeName;
            var resource = ThemeManager.class.getResource(themePath);

            if (resource == null) {
                themePath = "resources/" + themeName;
                resource = ThemeManager.class.getResource(themePath);
            }
            if (resource == null) {
                resource = ThemeManager.class.getResource("/" + themeName);
            }

            if (resource != null) {
                scene.getStylesheets().add(resource.toExternalForm());
                System.out.println("Stylesheet aplicado: " + resource.toExternalForm());
                return true;

            } else {
                System.err.println("❌ Erro: Arquivo de tema não encontrado: " + themeName);
                var defaultResource = ThemeManager.class.getResource("/resources/style.css");
                if (defaultResource != null) {
                    scene.getStylesheets().add(defaultResource.toExternalForm());
                    System.out.println("Aplicado tema padrão como fallback.");
                } else {
                    System.err.println("❌ Erro: Tema padrão também não encontrado.");
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Erro ao aplicar stylesheets do tema '" + themeName + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}