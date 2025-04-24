import java.awt.*;
import java.awt.TrayIcon.MessageType;

public class Notifier {
    public static void notify(String title, String message) {
        if (!SystemTray.isSupported()) return;

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = Toolkit.getDefaultToolkit().createImage(""); // ícone opcional
            TrayIcon trayIcon = new TrayIcon(image, "Alerta de Rompimento");
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);

            trayIcon.displayMessage(title, message, MessageType.WARNING);

            Thread.sleep(3000); // tempo para exibir
            tray.remove(trayIcon);
        } catch (Exception e) {
            System.err.println("Erro ao exibir notificação: " + e.getMessage());
        }
    }
}
