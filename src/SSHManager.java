import com.jcraft.jsch.*;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.function.Consumer;

public class SSHManager {
    private Session session;
    private ChannelShell channel;
    private OutputStream outputStream;
    private final Consumer<String> outputCallback;

    public SSHManager(Consumer<String> outputCallback) {
        this.outputCallback = outputCallback;
    }

    public void connect(String host, String user, String password, TextArea terminalArea) {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            config.put("HostKeyAlgorithms", "+ssh-rsa");
            session.setConfig(config);

            // Configurar timeout mais curto para melhor experiência do usuário
            session.setTimeout(10000); // 10 segundos

            session.connect();

            channel = (ChannelShell) session.openChannel("shell");

            PipedInputStream pis = new PipedInputStream();
            PipedOutputStream pos = new PipedOutputStream(pis);
            outputStream = pos;

            channel.setInputStream(pis);
            channel.setOutputStream(new OutputStream() {
                @Override
                public void write(int b) {
                    outputCallback.accept(String.valueOf((char) b));
                }
            });

            channel.connect();

            Platform.runLater(() -> terminalArea.appendText("\n✅ Conectado a " + host + "\n"));
        } catch (JSchException e) {
            Platform.runLater(() -> {
                String errorMessage = e.getMessage();

                // Tratamento específico para "socket is not established"
                if (errorMessage.contains("socket is not established") || errorMessage.contains("timeout")) {
                    terminalArea.appendText("\n❌ Erro de conexão: Falha no estabelecimento de conexão com " + host + "\n");
                    terminalArea.appendText("\nDetalhes do problema:\n");
                    terminalArea.appendText("• A conexão inicial foi feita, mas o protocolo SSH não conseguiu ser estabelecido\n");
                    terminalArea.appendText("• Isso geralmente acontece quando um firewall está interceptando ou bloqueando conexões SSH\n");
                    terminalArea.appendText("• Você pode estar conectado à rede, mas sem acesso à rede interna onde a OLT está localizada\n");
                    terminalArea.appendText("• A OLT pode estar com problemas no serviço SSH\n\n");
                    terminalArea.appendText("Sugestões:\n");
                    terminalArea.appendText("1. Verifique se você está na rede corporativa ou VPN\n");
                    terminalArea.appendText("2. Certifique-se de que a OLT está online (ping " + host + ")\n");
                    terminalArea.appendText("3. Consulte o administrador de rede se o acesso SSH está liberado\n");
                }
                // Causa específica: Connection timed out
                else if (e.getCause() instanceof ConnectException || e.getCause() instanceof SocketTimeoutException ||
                        errorMessage.contains("Connection timed out")) {
                    terminalArea.appendText("\n❌ Erro de conexão: Tempo esgotado ao conectar à OLT " + host + "\n");
                    terminalArea.appendText("\nPossíveis causas:\n");
                    terminalArea.appendText("• Você não está conectado à rede interna da empresa\n");
                    terminalArea.appendText("• A OLT está desligada ou inacessível\n");
                    terminalArea.appendText("• Um firewall está bloqueando a conexão na porta 22\n");
                    terminalArea.appendText("• O endereço IP da OLT mudou ou está incorreto\n\n");
                    terminalArea.appendText("Sugestão: Verifique sua conexão VPN ou rede corporativa e tente novamente.\n");
                }
                // Falha de autenticação
                else if (errorMessage.contains("Auth fail") || errorMessage.contains("authentication")) {
                    terminalArea.appendText("\n❌ Erro de autenticação\n");
                    terminalArea.appendText("\nO nome de usuário ou senha estão incorretos.\n");
                    terminalArea.appendText("Verifique suas credenciais e tente novamente.\n");
                }
                // Host desconhecido
                else if (e.getCause() instanceof UnknownHostException) {
                    terminalArea.appendText("\n❌ Host desconhecido: " + host + "\n");
                    terminalArea.appendText("\nO endereço IP não pode ser resolvido. Verifique se o IP está correto.\n");
                }
                // Erro genérico
                else {
                    terminalArea.appendText("\n❌ Erro na conexão: " + errorMessage + "\n");
                    terminalArea.appendText("\nDetalhes técnicos: JSchException" +
                            (e.getCause() != null ? " causado por " + e.getCause().getClass().getSimpleName() : "") + "\n");
                    terminalArea.appendText("\nVerifique sua conexão com a rede e se o serviço SSH está disponível na OLT.\n");
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                terminalArea.appendText("\n❌ Erro inesperado: " + e.getMessage() + "\n");
                terminalArea.appendText("Tipo: " + e.getClass().getSimpleName() + "\n");
                terminalArea.appendText("\nPor favor, reporte este erro para o suporte técnico.\n");
            });
        }
    }

    public void sendCommand(String command) {
        try {
            if (outputStream != null && channel != null && channel.isConnected()) {
                outputStream.write((command + "\n").getBytes());
                outputStream.flush();
            } else {
                outputCallback.accept("\n⚠️ Não foi possível enviar o comando: conexão não estabelecida ou fechada.\n");
            }
        } catch (IOException e) {
            outputCallback.accept("\n❌ Erro ao enviar comando: " + e.getMessage() + "\n");
            if (e.getMessage().contains("Broken pipe") || e.getMessage().contains("closed")) {
                outputCallback.accept("A conexão foi interrompida. Reconecte à OLT para continuar.\n");
            }
        } catch (Exception e) {
            outputCallback.accept("\n❌ Erro inesperado: " + e.getMessage() + "\n");
        }
    }

    public void disconnect() {
        try {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        } catch (Exception e) {
            // Absorve exceções durante o fechamento
        }
    }
}