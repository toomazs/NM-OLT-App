import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class StageResizer {
    private static final int RESIZE_MARGIN = 8;
    private final Stage stage;
    private final Scene scene;
    private double startMouseX, startMouseY;
    private double startStageX, startStageY;
    private double startStageWidth, startStageHeight;
    private boolean isResizing = false;
    private ResizeDirection resizeDirection = ResizeDirection.NONE;

    // Esse código foi totalmente vibe-coded pelo Claude.AI - Refatorei diversas vezes e não consegui corrigir bugs, então tive que sucumbir a IA xd

    public StageResizer(Stage stage) {
        this.stage = stage;
        this.scene = stage.getScene();

        if (this.scene == null) {
            throw new IllegalStateException("A Scene deve ser definida no Stage antes de criar um StageResizer.");
        }
        addResizeListeners();
    }

    private void addResizeListeners() {
        scene.setOnMouseMoved(event -> {
            if (stage.isMaximized() || stage.isIconified() || isResizing) {
                return;
            }

            double mouseX = event.getSceneX();
            double mouseY = event.getSceneY();
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();

            ResizeDirection direction = getResizeDirection(mouseX, mouseY, sceneWidth, sceneHeight);
            Cursor cursor = getCursorForDirection(direction);

            if (scene.getCursor() != cursor) {
                scene.setCursor(cursor);
            }
        });

        scene.setOnMousePressed(event -> {
            if (stage.isMaximized() || stage.isIconified()) {
                return;
            }

            double mouseX = event.getSceneX();
            double mouseY = event.getSceneY();
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();

            resizeDirection = getResizeDirection(mouseX, mouseY, sceneWidth, sceneHeight);

            if (resizeDirection != ResizeDirection.NONE) {
                isResizing = true;
                startMouseX = event.getScreenX();
                startMouseY = event.getScreenY();
                startStageX = stage.getX();
                startStageY = stage.getY();
                startStageWidth = stage.getWidth();
                startStageHeight = stage.getHeight();
                event.consume();
            }
        });

        scene.setOnMouseDragged(event -> {
            if (!isResizing || resizeDirection == ResizeDirection.NONE) {
                return;
            }

            double currentMouseX = event.getScreenX();
            double currentMouseY = event.getScreenY();
            double deltaX = currentMouseX - startMouseX;
            double deltaY = currentMouseY - startMouseY;

            applyResize(deltaX, deltaY);
            event.consume();
        });

        scene.setOnMouseReleased(event -> {
            if (isResizing) {
                isResizing = false;
                resizeDirection = ResizeDirection.NONE;
                scene.setCursor(Cursor.DEFAULT);
            }
        });
    }

    private ResizeDirection getResizeDirection(double mouseX, double mouseY, double sceneWidth, double sceneHeight) {
        boolean nearTop = mouseY <= RESIZE_MARGIN;
        boolean nearBottom = mouseY >= sceneHeight - RESIZE_MARGIN;
        boolean nearLeft = mouseX <= RESIZE_MARGIN;
        boolean nearRight = mouseX >= sceneWidth - RESIZE_MARGIN;

        if (nearTop && nearLeft) return ResizeDirection.NORTHWEST;
        if (nearTop && nearRight) return ResizeDirection.NORTHEAST;
        if (nearBottom && nearLeft) return ResizeDirection.SOUTHWEST;
        if (nearBottom && nearRight) return ResizeDirection.SOUTHEAST;

        if (nearTop) return ResizeDirection.NORTH;
        if (nearBottom) return ResizeDirection.SOUTH;
        if (nearLeft) return ResizeDirection.WEST;
        if (nearRight) return ResizeDirection.EAST;

        return ResizeDirection.NONE;
    }

    private Cursor getCursorForDirection(ResizeDirection direction) {
        switch (direction) {
            case NORTH: return Cursor.N_RESIZE;
            case SOUTH: return Cursor.S_RESIZE;
            case EAST: return Cursor.E_RESIZE;
            case WEST: return Cursor.W_RESIZE;
            case NORTHEAST: return Cursor.NE_RESIZE;
            case NORTHWEST: return Cursor.NW_RESIZE;
            case SOUTHEAST: return Cursor.SE_RESIZE;
            case SOUTHWEST: return Cursor.SW_RESIZE;
            default: return Cursor.DEFAULT;
        }
    }

    private void applyResize(double deltaX, double deltaY) {
        double newX = startStageX;
        double newY = startStageY;
        double newWidth = startStageWidth;
        double newHeight = startStageHeight;

        double minWidth = Math.max(stage.getMinWidth(), 200);
        double minHeight = Math.max(stage.getMinHeight(), 150);

        switch (resizeDirection) {
            case NORTH:
                newHeight = startStageHeight - deltaY;
                newY = startStageY + deltaY;
                break;
            case SOUTH:
                newHeight = startStageHeight + deltaY;
                break;
            case EAST:
                newWidth = startStageWidth + deltaX;
                break;
            case WEST:
                newWidth = startStageWidth - deltaX;
                newX = startStageX + deltaX;
                break;
            case NORTHEAST:
                newWidth = startStageWidth + deltaX;
                newHeight = startStageHeight - deltaY;
                newY = startStageY + deltaY;
                break;
            case NORTHWEST:
                newWidth = startStageWidth - deltaX;
                newHeight = startStageHeight - deltaY;
                newX = startStageX + deltaX;
                newY = startStageY + deltaY;
                break;
            case SOUTHEAST:
                newWidth = startStageWidth + deltaX;
                newHeight = startStageHeight + deltaY;
                break;
            case SOUTHWEST:
                newWidth = startStageWidth - deltaX;
                newHeight = startStageHeight + deltaY;
                newX = startStageX + deltaX;
                break;
        }

        if (newWidth < minWidth) {
            if (resizeDirection == ResizeDirection.WEST ||
                    resizeDirection == ResizeDirection.NORTHWEST ||
                    resizeDirection == ResizeDirection.SOUTHWEST) {
                newX = startStageX + startStageWidth - minWidth;
            }
            newWidth = minWidth;
        }

        if (newHeight < minHeight) {
            if (resizeDirection == ResizeDirection.NORTH ||
                    resizeDirection == ResizeDirection.NORTHEAST ||
                    resizeDirection == ResizeDirection.NORTHWEST) {
                newY = startStageY + startStageHeight - minHeight;
            }
            newHeight = minHeight;
        }

        stage.setX(newX);
        stage.setY(newY);
        stage.setWidth(newWidth);
        stage.setHeight(newHeight);
    }

    private enum ResizeDirection {
        NONE, NORTH, SOUTH, EAST, WEST, NORTHEAST, NORTHWEST, SOUTHEAST, SOUTHWEST
    }
}