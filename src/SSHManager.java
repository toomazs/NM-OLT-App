import com.jcraft.jsch.*;
import javafx.scene.control.TextArea;
import javafx.application.Platform;
import java.util.function.BiConsumer;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class SSHManager {
    private static Channel channel;
    private static Session session;
    private static PipedOutputStream pipeOut;
    private static boolean sentCommand = false;
    private static Timeline connectionTimeoutTimer;

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

                // Mensagem de conexão com efeito de "carregando"
                Platform.runLater(() -> {
                    Main.appendToTerminal("Conectando a " + ip + "... ");
                    startLoadingAnimation(terminalArea);
                });

                session.connect();

                // Parar animação de carregamento
                Platform.runLater(() -> {
                    stopLoadingAnimation();
                });

                pipeOut = new PipedOutputStream();
                PipedInputStream pipeIn = new PipedInputStream(pipeOut);

                channel = session.openChannel("shell");
                ((ChannelShell)channel).setPtyType("xterm");
                channel.setInputStream(pipeIn);
                channel.setOutputStream(new TextAreaOutputStream(terminalArea));
                channel.connect();

                // Mensagem de sucesso com cores e emojis
                Platform.runLater(() -> {
                    Main.appendToTerminal("\n✅ Conectado com sucesso!\n");
                    Main.appendToTerminal("\nDigite comandos como: display ont info summary 0/7/7\n\n");
                });

            } catch (Exception e) {
                String errorMessage = getErrorMessage(e);
                // Parar animação de carregamento em caso de erro
                Platform.runLater(() -> {
                    stopLoadingAnimation();
                    Main.appendToTerminal("\n❌ Falha na conexão: " + errorMessage + "\n");
                });
                errorHandler.accept("Erro SSH", errorMessage);
                disconnect();
            }
        }).start();
    }

    private static Timeline loadingAnimation;
    private static String[] loadingFrames = {".  ", ".. ", "...", " ..", "  .", "   "};
    private static int frameIndex = 0;

    private static void startLoadingAnimation(TextArea terminalArea) {
        if (loadingAnimation != null) {
            loadingAnimation.stop();
        }

        frameIndex = 0;
        loadingAnimation = new Timeline(
                new KeyFrame(Duration.millis(200), event -> {
                    // Deletar os últimos 3 caracteres e substituir com o novo frame
                    String text = terminalArea.getText();
                    if (text.length() >= 3) {
                        String newText = text.substring(0, text.length() - 3) + loadingFrames[frameIndex];
                        terminalArea.setText(newText);
                    }
                    frameIndex = (frameIndex + 1) % loadingFrames.length;
                })
        );
        loadingAnimation.setCycleCount(Timeline.INDEFINITE);
        loadingAnimation.play();

        // Configurar timeout para a conexão
        connectionTimeoutTimer = new Timeline(
                new KeyFrame(Duration.seconds(15), event -> {
                    stopLoadingAnimation();
                    Main.appendToTerminal("\n❌ Tempo esgotado! Conexão não estabelecida após 15 segundos.\n");
                })
        );
        connectionTimeoutTimer.setCycleCount(1);
        connectionTimeoutTimer.play();
    }

    private static void stopLoadingAnimation() {
        if (loadingAnimation != null) {
            loadingAnimation.stop();
            loadingAnimation = null;
        }

        if (connectionTimeoutTimer != null) {
            connectionTimeoutTimer.stop();
            connectionTimeoutTimer = null;
        }
    }

    public static void sendCommand(String command) {
        try {
            if (pipeOut != null && channel != null && channel.isConnected()) {
                // Se já enviamos um comando recentemente, aguarde um momento para evitar duplicação
                if (sentCommand) {
                    Thread.sleep(100);
                }

                // Efeito visual: mostrar o comando sendo executado em verde
                Platform.runLater(() -> {
                    Main.appendToTerminal("\n$ " + command + "\n");
                });

                // envia o comando seguido por enter com (\r\n)
                pipeOut.write((command + "\r\n").getBytes("UTF-8"));
                pipeOut.flush();

                // Marcar que enviamos um comando
                sentCommand = true;

                // Reset o flag após um período
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        sentCommand = false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                Main.appendToTerminal("\n❌ Erro ao enviar comando: " + e.getMessage() + "\n");
            });
        }
    }

    public static void disconnect() {
        try {
            // Mensagem de desconexão
            Platform.runLater(() -> {
                Main.appendToTerminal("\nDesconectando...\n");
            });

            stopLoadingAnimation();

            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            if (pipeOut != null) {
                pipeOut.close();
            }

            // Mensagem final
            Platform.runLater(() -> {
                Main.appendToTerminal("Desconectado com sucesso.\n");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getErrorMessage(Exception e) {
        if (e.getMessage() == null) {
            return "Erro desconhecido";
        } else if (e.getMessage().contains("Auth fail")) {
            return "Credenciais inválidas! Verifique usuário/senha da OLT";
        } else if (e.getMessage().contains("Connection timed out")) {
            return "Timeout de conexão. OLT pode estar lenta, valide a VPN/IP";
        } else if (e.getMessage().contains("Network is unreachable")) {
            return "Rede inacessível. Você está na rede interna?";
        } else {
            return "Erro técnico bizonho (avise o Eduardo): " + e.getMessage();
        }
    }
}