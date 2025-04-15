import com.jcraft.jsch.*;
import javafx.scene.control.TextArea;
import javafx.application.Platform;
import java.util.function.BiConsumer;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class SSHManager {
    private static Channel channel;
    private static Session session;
    private static PipedOutputStream pipeOut;

    public static boolean testConnection(String ip) {
        try {
            Process pingProcess = Runtime.getRuntime().exec("ping -n 2 " + ip);
            return pingProcess.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void connect(String ip, String user, String password,
                               TextArea terminalArea, BiConsumer<String, String> errorHandler) {
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(user, ip, 22);
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setTimeout(10000);

                Platform.runLater(() -> {
                    Main.appendToTerminal("Conectando à " + ip + "...\n");
                });

                session.connect();

                pipeOut = new PipedOutputStream();
                PipedInputStream pipeIn = new PipedInputStream(pipeOut);

                channel = session.openChannel("shell");
                ((ChannelShell)channel).setPtyType("xterm");
                channel.setInputStream(pipeIn);
                channel.setOutputStream(new TextAreaOutputStream(terminalArea));
                channel.connect();

                Platform.runLater(() -> {
                    Main.appendToTerminal("Conectado com sucesso!\n");
                });

            } catch (Exception e) {
                String errorMessage = getErrorMessage(e);
                Platform.runLater(() -> {
                    Main.appendToTerminal("Falha na conexão: " + errorMessage + "\n");
                });
                errorHandler.accept("Erro SSH", errorMessage);
                disconnect();
            }
        }).start();
    }

    public static void sendCommand(String command) {
        try {
            if (pipeOut != null && channel != null && channel.isConnected()) {
                // envia o comando seguido por enter com (\r\n)
                pipeOut.write((command + "\r\n").getBytes("UTF-8"));
                pipeOut.flush();

                // add comando no terminal com format correta
                Main.appendToTerminal(command + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Main.appendToTerminal("Erro ao enviar comando: " + e.getMessage() + "\n");
        }
    }

    public static void disconnect() {
        try {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            if (pipeOut != null) {
                pipeOut.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getErrorMessage(Exception e) {
        if (e.getMessage().contains("Auth fail")) {
            return "Credenciais inválidas! Verifique usuário/senha da OLT";
        } else if (e.getMessage().contains("Connection timed out")) {
            return "Timeout de conexão. OLT pode estar lenta, valida a VPN/IP";
        } else if (e.getMessage().contains("Network is unreachable")) {
            return "Rede inacessível. Você está na rede interna?";
        } else {
            return "Erro técnico bizonho (avise o Eduardo): " + e.getMessage();
        }
    }
}