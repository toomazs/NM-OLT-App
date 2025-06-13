package utils;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.animation.FadeTransition;
import javafx.scene.control.DialogPane;
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
            case "style-gdark.css" -> "/oltapp-icon-gdark.png";
            case "style-sop.css" -> "/oltapp-icon-sop.png";
            case "style-nightowl.css" -> "/oltapp-icon-nightowl.png";
            case "style-lightowl.css" -> "/oltapp-icon-lightowl.png";
            case "style-creme.css" -> "/oltapp-icon-creme.png";
            case "style-terminal.css" -> "/oltapp-icon-terminal.png";
            case "style-blue.css" -> "/oltapp-icon-blue.png";
            case "style-green.css" -> "/oltapp-icon-green.png";
            case "style-red.css" -> "/oltapp-icon-red.png";
            case "style-pink.css" -> "/oltapp-icon-pink.png";
            default -> "/oltapp-icon.png";
        };
    }

    public static void applyTheme(Scene scene, String themeName) {
        if (scene == null) {
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
                    applyThemeStylesheets(currentScene, themeName);
                    fadeOut.play();
                });

                fadeIn.play();
            } else {
                applyThemeStylesheets(currentScene, themeName);
            }
        });
    }

    public static void applyThemeToNewScene(Scene scene) {
        String themeName = configManager.getTheme();
        applyThemeStylesheets(scene, themeName);
    }

    public static void applyThemeToDialog(DialogPane dialogPane, String themeName) {
        if (dialogPane == null) {
            return;
        }
        if (themeName == null || themeName.isEmpty()) {
            themeName = "style.css";
        }
        dialogPane.getStylesheets().clear();

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
                dialogPane.getStylesheets().add(resource.toExternalForm());
            } else {
                var defaultResource = ThemeManager.class.getResource("/resources/style.css");
                if (defaultResource != null) {
                    dialogPane.getStylesheets().add(defaultResource.toExternalForm());
                } else {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                return true;
            } else {
                var defaultResource = ThemeManager.class.getResource("/resources/style.css");
                if (defaultResource != null) {
                    scene.getStylesheets().add(defaultResource.toExternalForm());
                } else {
                }
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}