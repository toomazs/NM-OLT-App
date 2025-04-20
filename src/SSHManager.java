import com.jcraft.jsch.*;
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
    private Map<String, List<String>> breakageData = new HashMap<>();

    public void connect(String host, String user, String password, TextArea terminalArea) {
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
            Platform.runLater(() -> terminalArea.appendText("\n❌ Erro na conexão: " + e.getMessage() + "\n"));
            disconnect();
        }
    }

    private void readChannelOutput() {
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            while (isRunning && channel != null && channel.isConnected()) {
                while (inputStream.available() > 0) {
                    int bytesRead = inputStream.read(buffer, 0, BUFFER_SIZE);
                    if (bytesRead < 0) break;

                    Platform.runLater(() -> {
                        if (terminalArea.getText().length() > 100000) {
                            terminalArea.setText(terminalArea.getText().substring(50000));
                        }
                    });

                    String outputText = new String(buffer, 0, bytesRead);

                    if (capturingOutput) {
                        commandOutput.append(outputText);
                    }

                    if (outputText.contains("---- More ( Press 'Q' to break ) ----") ||
                            outputText.contains("{ <cr>||<K> }:") ||
                            outputText.contains("More: <space>") ||
                            outputText.contains("-- More --")) {
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    final String finalText = outputText;
                    if (!finalText.isEmpty()) {
                        Platform.runLater(() -> terminalArea.appendText(finalText));
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

    public String queryOpticalSignal(String ontId) {
        try {
            commandOutput.setLength(0);
            capturingOutput = true;

            sendCommand("enable");
            Thread.sleep(1000);
            sendCommand("config");
            Thread.sleep(1000);

            if (ontId.isEmpty()) {
                sendCommand("display ont optical-info all");
            } else {
                sendCommand("display ont optical-info " + ontId);
            }

            Thread.sleep(5000);
            capturingOutput = false;

            return parseRealOpticalSignalOutput(commandOutput.toString(), ontId);

        } catch (Exception e) {
            return "Erro na consulta: " + e.getMessage();
        }
    }

    private String parseRealOpticalSignalOutput(String output, String ontId) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("(\\d+/\\d+/\\d+)\\s+(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(output);

        int totalOnts = 0;
        int weakSignal = 0;
        int noSignal = 0;

        result.append("RESULTADO DA CONSULTA DE SINAL:\n");
        result.append("--------------------------------------------------\n");
        result.append("F/S/P   ONT-ID   RX Power(dBm)   TX Power(dBm)   Temperature(°C)\n");
        result.append("--------------------------------------------------\n");

        while (matcher.find()) {
            totalOnts++;
            String port = matcher.group(1);
            String ont = matcher.group(2);
            String rxPower = matcher.group(3);
            String txPower = matcher.group(4);
            String temp = matcher.group(5);

            double rx = Double.parseDouble(rxPower);
            String status = "";

            if (rx < -27.0) {
                status = " (!)";
                weakSignal++;
            } else if (rx < -40.0) {
                status = " (X)";
                noSignal++;
            }

            result.append(String.format("%-8s%-9s%-16s%-16s%-16s%s\n",
                    port, ont, rxPower, txPower, temp, status));
        }

        result.append("--------------------------------------------------\n");
        result.append("Status: (!) indica sinal fraco | (X) indica sem sinal\n\n");
        result.append("Total de ONTs: ").append(totalOnts).append("\n");
        result.append("ONTs com sinal fraco: ").append(weakSignal).append("\n");
        result.append("ONTs sem sinal: ").append(noSignal).append("\n");

        return result.toString();
    }

    public void scanForBreakages() {
        try {
            commandOutput.setLength(0);
            capturingOutput = true;

            sendCommand("enable");
            Thread.sleep(1000);
            sendCommand("config");
            Thread.sleep(1000);
            sendCommand("display ont info summary");
            Thread.sleep(5000);
            capturingOutput = false;

            parseBreakageOutput(commandOutput.toString());

        } catch (Exception e) {
            System.err.println("Erro ao escanear rompimentos: " + e.getMessage());
        }
    }

    private void parseBreakageOutput(String output) {
        Pattern pattern = Pattern.compile("(\\d+/\\d+/\\d+).*?(\\d+)\\s+\\d+\\s+\\d+\\s+(\\d+).*?(LOSi|LOBi)");
        Matcher matcher = pattern.matcher(output);

        Map<String, List<String>> ponFailures = new HashMap<>();

        while (matcher.find()) {
            String pon = matcher.group(1);
            String ontId = matcher.group(2);
            String offlineCount = matcher.group(3);
            String reason = matcher.group(4);

            if (!ponFailures.containsKey(pon)) {
                ponFailures.put(pon, new ArrayList<>());
            }
            ponFailures.get(pon).add(ontId + "|" + reason + "|" + offlineCount);
        }

        for (Map.Entry<String, List<String>> entry : ponFailures.entrySet()) {
            String pon = entry.getKey();
            List<String> failures = entry.getValue();

            if (failures.size() >= 6) {
                breakageData.put(pon, failures);
            }
        }
    }

    public Map<String, List<String>> getBreakageData() {
        return breakageData;
    }

    public String getBreakagesReport() {
        StringBuilder report = new StringBuilder();
        report.append("RELATÓRIO DE ROMPIMENTOS DETECTADOS:\n");
        report.append("--------------------------------------------------\n");

        int critical = 0;
        int warning = 0;

        for (Map.Entry<String, List<String>> entry : breakageData.entrySet()) {
            String pon = entry.getKey();
            List<String> failures = entry.getValue();

            if (failures.size() >= 15) {
                report.append("[URGENTE] PON ").append(pon).append(": ")
                        .append(failures.size()).append(" ONTs offline por LOSi/LOBi\n");
                critical++;
            } else if (failures.size() >= 6) {
                report.append("[ALERTA] PON ").append(pon).append(": ")
                        .append(failures.size()).append(" ONTs offline por LOSi/LOBi\n");
                warning++;
            }
        }

        report.append("\nTOTAL:\n");
        report.append("Rompimentos críticos: ").append(critical).append("\n");
        report.append("Rompimentos em alerta: ").append(warning).append("\n");

        return report.toString();
    }

    public void sendCtrlC() {
        try {
            if (outputStream != null && channel != null && channel.isConnected()) {
                outputStream.write(3);
                outputStream.flush();
            }
        } catch (IOException e) {
            Platform.runLater(() -> terminalArea.appendText("\n❌ Erro ao enviar Ctrl+C: " + e.getMessage() + "\n"));
        }
    }

    public void disconnect() {
        isRunning = false;
        if (executor != null) executor.shutdownNow();
        if (channel != null) channel.disconnect();
        if (session != null) session.disconnect();
        if (terminalArea != null) {
            Platform.runLater(() -> terminalArea.appendText("\n🔌 Desconectado\n"));
        }
    }

    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }
}