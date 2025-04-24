import com.jcraft.jsch.*;
import java.util.Locale;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;


public class SSHManager {
    private Session session;
    private ChannelShell channel;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ExecutorService executor;
    private boolean isRunning = false;
    private TextArea terminalArea;
    private static final int BUFFER_SIZE = 1024;
    private StringBuilder commandOutput = new StringBuilder();
    private boolean capturingOutput = false;


    // ---------------------- CONNECT & TRATAMENTO TERMINAL ---------------------- //
    public boolean connect(String host, String user, String password, TextArea terminalArea) {
        this.terminalArea = terminalArea;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            config.put("HostKeyAlgorithms", "+ssh-rsa");
            config.put("kex", "diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1,diffie-hellman-group-exchange-sha256");
            session.setConfig(config);

            session.connect(30000);
            channel = (ChannelShell) session.openChannel("shell");
            channel.setPtyType("vt100");
            channel.setPtySize(120, 40, 800, 600);

            inputStream = channel.getInputStream();
            outputStream = channel.getOutputStream();

            channel.connect(3000);

            isRunning = true;
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this::readChannelOutput);

            Platform.runLater(() -> terminalArea.appendText("\n✅ Conectado a " + host + "\n"));
        } catch (Exception e) {
            Platform.runLater(() -> {
                terminalArea.appendText(
                        "\n❌ Não foi possível conectar à OLT.\n\n" +
                                "Verifique se:\n" +
                                "1 - Você está na rede interna da empresa\n" +
                                "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                "Caso esteja tudo correto, contate imediatamente o Eduardo.\n" +
                                "Detalhes técnicos: " + e.getMessage() + "\n"
                );
            });
            disconnect();
        }
        return false;
    }

    private void readChannelOutput() {
        byte[] buffer = new byte[BUFFER_SIZE];
        boolean waitingForMore = false;

        try {
            while (isRunning && channel != null && channel.isConnected()) {
                while (inputStream.available() > 0 || waitingForMore) {
                    if (waitingForMore && inputStream.available() == 0) {
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                            Thread.sleep(300);

                            if (inputStream.available() == 0) {
                                waitingForMore = false;
                                continue;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            waitingForMore = false;
                        }
                    }

                    int bytesRead = inputStream.read(buffer, 0, BUFFER_SIZE);
                    if (bytesRead < 0) {
                        waitingForMore = false;
                        break;
                    }

                    Platform.runLater(() -> {
                        if (terminalArea.getText().length() > 100000) {
                            terminalArea.setText(terminalArea.getText().substring(50000));
                        }
                    });

                    String outputText = new String(buffer, 0, bytesRead);
                    String originalOutput = outputText;

                    waitingForMore = originalOutput.contains("---- More ( Press 'Q' to break ) ----") ||
                            originalOutput.contains("More: <space>") ||
                            originalOutput.contains("-- More --");

                    outputText = outputText.replaceAll("---- More \\( Press 'Q' to break \\) ----", "");
                    outputText = outputText.replaceAll("\\[37D \\[37D", "");
                    outputText = outputText.replaceAll("More: <space>", "");
                    outputText = outputText.replaceAll("-- More --", "");
                    outputText = outputText.replaceAll("\\[\\d+[A-Za-z]", "");
                    outputText = outputText.replaceAll("\\[\\d+;\\d+[A-Za-z]", "");
                    outputText = outputText.replaceAll("\r", "");

                    if (originalOutput.contains("{ <cr>||<K> }:")) {
                        outputText = outputText.replaceAll("\\{ <cr>\\|\\|<K> \\}:", "");
                        try {
                            outputStream.write('\r');
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (waitingForMore) {
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                            waitingForMore = false;
                        }
                    }

                    if (capturingOutput) {
                        commandOutput.append(outputText);
                    }

                    final String finalText = outputText;
                    if (!finalText.isEmpty()) {
                        Platform.runLater(() -> terminalArea.appendText(finalText));
                    }

                    if (waitingForMore) {
                        Thread.sleep(200);
                    }
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            if (isRunning) {
                Platform.runLater(() -> terminalArea.appendText("\n❌ Erro na leitura do terminal: " + e.getMessage() + "\n"));
            }
        }
    }

    public void sendCommand(String command) {
        try {
            if (outputStream != null && channel != null && channel.isConnected()) {
                outputStream.write((command + "\n").getBytes());
                outputStream.flush();
            }
        } catch (IOException e) {
            Platform.runLater(() -> terminalArea.appendText("\n❌ Erro ao enviar comando: " + e.getMessage() + "\n"));
        }
    }

    public String cleanCapturedOutput(String output) {
        output = output.replaceAll("---- More \\( Press 'Q' to break \\) ----", "");
        output = output.replaceAll("\\[37D \\[37D", "");
        output = output.replaceAll("More: <space>", "");
        output = output.replaceAll("-- More --", "");
        output = output.replaceAll("\\[\\d+[A-Za-z]", "");
        output = output.replaceAll("\\[\\d+;\\d+[A-Za-z]", "");
        output = output.replaceAll("\r", "");
        return output;
    }
    // ---------------------- CONNECT & TRATAMENTO TERMINAL ---------------------- //


    // ---------------------- CONSULTA DE SINAL ---------------------- //
    public String queryOpticalSignal(String fs, String p) {
        try {
            commandOutput.setLength(0);
            capturingOutput = true;

            sendCommand("enable");
            Thread.sleep(1000);
            sendCommand("config");
            Thread.sleep(1000);
            sendCommand("interface gpon " + fs);
            Thread.sleep(1000);
            sendCommand("display ont optical-info " + p + " all");

            Thread.sleep(10000);
            capturingOutput = false;

            return parseRealOpticalSignalOutput(commandOutput.toString(), fs + "/" + p);

        } catch (Exception e) {
            e.printStackTrace();
            return "Erro na consulta: " + e.getMessage();
        }
    }

    private String parseRealOpticalSignalOutput(String output, String ontId) {
        output = cleanCapturedOutput(output);
        StringBuilder result = new StringBuilder();


        Pattern pattern = Pattern.compile("(\\d+)\\s+(-\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(-\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)");
        Matcher matcher = pattern.matcher(output);

        int totalOnts = 0;
        int weakSignalRX = 0;
        int noSignalRX = 0;
        int weakSignalTX = 0;
        int noSignalTX = 0;
        double totalRx = 0;
        double totalTx = 0;

        result.append("RESULTADO DA CONSULTA DE SINAL:\n");
        result.append("----------------------------------------------------------------------\n");
        result.append("ONT-ID   RX Power(dBm)   TX Power(dBm)   OLT RX(dBm)   Temp(°C)   Dist(m)\n");
        result.append("----------------------------------------------------------------------\n");

        while (matcher.find()) {
            totalOnts++;

            String ont = matcher.group(1);
            String rxPower = matcher.group(2);
            String txPower = matcher.group(3);
            String oltRxPower = matcher.group(4);
            String temp = matcher.group(5);
            String distance = matcher.group(8);

            double rx = Double.parseDouble(rxPower);
            double tx = Double.parseDouble(oltRxPower);

            totalRx += rx;
            totalTx += tx;

            String statusRX = "";
            String statusTX = "";
            String status = "";

            if (rx < -29.0) {
                statusRX = " (⚠ RX CRÍTICO)";
                noSignalRX++;
            } else if (rx <= -27.0 && rx >= -29.0) {
                statusRX = " (ℹ RX VERIFICAR MÉDIA)";
                weakSignalRX++;
            }

            if (tx < -29.0) {
                statusTX = " (⚠ TX CRÍTICO)";
                noSignalTX++;
            } else if (tx <= -27.0 && tx >= -29.0) {
                statusTX = " (ℹ TX VERIFICAR MÉDIA)";
                weakSignalTX++;
            }

            if (!statusRX.isEmpty() && !statusTX.isEmpty()) {
                status = statusRX + " " + statusTX;
            } else if (!statusRX.isEmpty()) {
                status = statusRX;
            } else if (!statusTX.isEmpty()) {
                status = statusTX;
            }

            result.append(String.format("%-9s%-16s%-16s%-14s%-12s%-8s%s\n",
                    ont, rxPower, txPower, oltRxPower, temp, distance, status));
        }

        result.append("----------------------------------------------------------------------\n\n");

        if (totalOnts > 0) {
            result.append("Status:\n");
            result.append("• (ℹ VERIFICAR MÉDIA) entre -27 e -29 dBm — analisar com média da primária\n");
            result.append("• (⚠ CRÍTICO) menor que -29 dBm — pode causar quedas/oscilação\n\n");

            result.append("Total de ONTs: ").append(totalOnts).append("\n");
            result.append("ONTs com sinal RX fraco: ").append(weakSignalRX).append("\n");
            result.append("ONTs com sinal RX crítico: ").append(noSignalRX).append("\n");
            result.append("ONTs com sinal TX fraco: ").append(weakSignalTX).append("\n");
            result.append("ONTs com sinal TX crítico: ").append(noSignalTX).append("\n");

            double avgRx = totalRx / totalOnts;
            double avgTx = totalTx / totalOnts;

            result.append(String.format(Locale.US, "\nMédia Sinal RX: %.2f dBm", avgRx));
            result.append(String.format(Locale.US, "\nMédia Sinal TX: %.2f dBm\n", avgTx));
        } else {
            result.append("Não foram encontrados dados de sinal óptico.\n");
            result.append("Verifique se o comando foi executado corretamente e se há ONTs configuradas nesta porta.\n");
        }

        return result.toString();
    }
    // ---------------------- CONSULTA DE SINAL ---------------------- //


    // ---------------------- DISCONNECT ---------------------- //
    public void disconnect() {
        isRunning = false;
        if (executor != null) executor.shutdownNow();
        if (channel != null) channel.disconnect();
        if (session != null) session.disconnect();
        if (terminalArea != null) {
            Platform.runLater(() -> terminalArea.appendText("\n🔌 Desconectado\n"));
        }
    }
}
    // ---------------------- CONSULTA DE SINAL ---------------------- //