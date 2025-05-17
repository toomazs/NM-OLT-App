import com.jcraft.jsch.*;

import java.io.PrintStream;
import java.util.Locale;
import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class SSHManager {
    private Session session;
    private ChannelShell channel;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ExecutorService executor;
    private boolean isRunning = false;
    private CodeArea terminalArea;
    private static final int BUFFER_SIZE = 1024;
    private final Object outputLock = new Object();
    private StringBuilder commandOutput = new StringBuilder();
    private boolean capturingOutput = false;
    private int connectTimeout = 30000;
    private int commandTimeout = 20000;
    private long lastActivityTime;
    private ScheduledExecutorService inactivityTimer;
    private long inactivityTimeout = 300000;

    // Detecção de IP avançada por Regex + Pattern feita pelo chatgepeto
    private static final String IP_PATTERN =
            "\\b((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b";
    private static final Pattern IP_REGEX = Pattern.compile(IP_PATTERN);

    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }


    // ---------------------- CONNECT/DISCONNECT ---------------------- //
    public boolean connect(String host, String user, String password, CodeArea terminalArea) {
        this.terminalArea = terminalArea;
        this.lastActivityTime = System.currentTimeMillis();

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

            session.connect(connectTimeout);
            channel = (ChannelShell) session.openChannel("shell");
            channel.setPtyType("vt100");
            channel.setPtySize(120, 40, 800, 600);

            inputStream = channel.getInputStream();
            outputStream = channel.getOutputStream();

            channel.connect(3000);

            isRunning = true;
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this::readChannelOutput);

            Platform.runLater(() -> {
                terminalArea.appendText("\n✅ Conectado a " + host + "\n");
                destacarIPs(terminalArea);
            });

            inactivityTimer = Executors.newSingleThreadScheduledExecutor();
            inactivityTimer.scheduleAtFixedRate(this::checkInactivity,
                    1, 1, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            Platform.runLater(() -> {
                terminalArea.appendText(
                        "\n❌ Não foi possível conectar à OLT.\n\n" +
                                "Verifique se:\n" +
                                "1 - Você está na rede interna da empresa\n" +
                                "2 - Alguém derrubou a OLT, ou se ela está desativada\n" +
                                "3 - Se não há firewall ou antivírus bloqueando\n\n" +
                                "Caso esteja tudo correto, contate o Eduardo sobre esse erro abaixo:\n" +
                                e.getClass().getSimpleName() + " - " + e.getMessage() + "\n"
                );
                destacarIPs(terminalArea);
            });
            disconnect();
            return false;
        }
    }

    public void disconnect() {
        isRunning = false;

        try {
            if (inactivityTimer != null) {
                inactivityTimer.shutdownNow();
                inactivityTimer = null;
            }

            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }

            if (channel != null) {
                try {
                    if (outputStream != null) outputStream.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar outputStream: " + e.getMessage());
                }
                try {
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar inputStream: " + e.getMessage());
                }
                channel.disconnect();
            }

            if (session != null) session.disconnect();

            if (terminalArea != null) {
                Platform.runLater(() -> {
                    terminalArea.appendText("\n🔌 Desconectado\n");
                    destacarIPs(terminalArea);
                });
            }

        } catch (Exception e) {
            System.err.println("Erro durante desconexão: " + e.getMessage());
        }
    }

    private void showToast(String message) {
        Platform.runLater(() -> {
            if (terminalArea != null && terminalArea.getScene() != null) {
                StackPane root = (StackPane) terminalArea.getScene().getRoot();

                Label toastLabel = new Label(message);
                toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); " +
                        "-fx-text-fill: white; -fx-padding: 10px 20px; " +
                        "-fx-background-radius: 20px;");
                toastLabel.setOpacity(0);

                root.getChildren().add(toastLabel);
                StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
                StackPane.setMargin(toastLabel, new Insets(0, 0, 80, 0));

                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toastLabel);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);

                PauseTransition stay = new PauseTransition(Duration.seconds(2.5));
                FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toastLabel);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);

                fadeOut.setOnFinished(e -> root.getChildren().remove(toastLabel));
                SequentialTransition seq = new SequentialTransition(fadeIn, stay, fadeOut);
                seq.play();
            }
        });
    }

    private void checkInactivity() {
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastActivityTime) > inactivityTimeout) {
            Platform.runLater(() -> {
                terminalArea.appendText("\n⏱️ Desconectado por inatividade (sem comandos por " +
                        (inactivityTimeout / 60000) + " minutos)\n");
                showToast("🔌 Desconectado por inatividade");
            });
            disconnect();
        }
    }
    // ---------------------- CONNECT/DISCONNECT ---------------------- //



    // ---------------------- TRATAMENTO TERMINAL e IP ---------------------- //
    private void readChannelOutput() {
        byte[] buffer = new byte[BUFFER_SIZE];
        boolean waitingForMore = false;
        long lastMorePromptTime = 0;

        try {
            while (isRunning && channel != null && channel.isConnected()) {
                while (inputStream.available() > 0 || waitingForMore) {
                    if (waitingForMore) {
                        long currentTime = System.currentTimeMillis();
                        if (inputStream.available() == 0) {
                            if (currentTime - lastMorePromptTime > 500) {
                                try {
                                    outputStream.write(' ');
                                    outputStream.flush();
                                    Thread.sleep(300);

                                    if (inputStream.available() == 0) {
                                        outputStream.write('\r');
                                        outputStream.flush();
                                        Thread.sleep(300);

                                        if (inputStream.available() == 0) {
                                            waitingForMore = false;
                                            continue;
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Erro ao lidar com 'More' prompt: " + e.getMessage());
                                    waitingForMore = false;
                                }
                            } else {
                                Thread.sleep(100);
                                continue;
                            }
                        }
                    }

                    int bytesRead = inputStream.read(buffer, 0, BUFFER_SIZE);
                    if (bytesRead < 0) {
                        waitingForMore = false;
                        break;
                    }

                    Platform.runLater(() -> {
                        if (terminalArea.getText().length() > 100000) {
                            terminalArea.replaceText(0, 50000, "");
                        }
                    });

                    String outputText = new String(buffer, 0, bytesRead);
                    String originalOutput = outputText;

                    boolean hasMorePrompt =
                            originalOutput.contains("---- More ( Press 'Q' to break ) ----") ||
                                    originalOutput.contains("More: <space>") ||
                                    originalOutput.contains("-- More --");

                    if (hasMorePrompt) {
                        waitingForMore = true;
                        lastMorePromptTime = System.currentTimeMillis();
                    }

                    outputText = cleanTerminalOutput(outputText);

                    if (originalOutput.contains("{ <cr>||<K> }:")) {
                        outputText = outputText.replaceAll("\\{ <cr>\\|\\|<K> \\}:", "");
                        try {
                            outputStream.write('\r');
                            outputStream.flush();
                        } catch (Exception e) {
                            System.err.println("Erro ao enviar CR: " + e.getMessage());
                        }
                    }

                    if (waitingForMore) {
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                        } catch (Exception e) {
                            System.err.println("Erro ao enviar espaço: " + e.getMessage());
                        }
                    }

                    if (capturingOutput) {
                        synchronized(outputLock) {
                            commandOutput.append(outputText);
                        }
                    }

                    final String finalText = outputText;
                    if (!finalText.isEmpty()) {
                        Platform.runLater(() -> {
                            terminalArea.appendText(finalText);
                            destacarIPs(terminalArea);
                            terminalArea.moveTo(terminalArea.getLength());
                            terminalArea.requestFollowCaret();
                        });
                    }

                    if (waitingForMore) {
                        Thread.sleep(200);
                    }
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            if (isRunning) {
                Platform.runLater(() -> {
                    terminalArea.appendText("\n❌ Erro na leitura do terminal: " +
                            e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
                    destacarIPs(terminalArea);
                });
            }
        }
    }

    public void sendCommand(String command) {
        lastActivityTime = System.currentTimeMillis();

        try {
            if (outputStream != null && channel != null && channel.isConnected()) {
                outputStream.write((command + "\n").getBytes());
                outputStream.flush();
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                terminalArea.appendText("\n❌ Erro ao enviar comando: " +
                        e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
                destacarIPs(terminalArea);
            });
        }
    }

    public String sendCommandWithResponse(String command) throws Exception {
        if (channel == null || !channel.isConnected()) {
            throw new Exception("Canal SSH não conectado");
        }

        InputStream in = channel.getInputStream();
        OutputStream out = channel.getOutputStream();

        out.write((command + "\n").getBytes());
        out.flush();

        byte[] buffer = new byte[1024];
        StringBuilder response = new StringBuilder();

        while (true) {
            while (in.available() > 0) {
                int i = in.read(buffer, 0, 1024);
                if (i < 0) {
                    break;
                }
                response.append(new String(buffer, 0, i));
            }

            if (channel.isClosed()) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (Exception ee) {
                ee.printStackTrace();
            }
        }

        return response.toString();
    }


   private String cleanTerminalOutput(String output) {

        String cleaned = output
                .replaceAll("---- More \\( Press 'Q' to break \\) ----", "")
                .replaceAll("More: <space>", "")
                .replaceAll("-- More --", "")
                .replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "")
                .replaceAll("\\x1B\\[\\?[0-9;]*[a-zA-Z]", "")
                .replaceAll("[\\p{Cc}&&[^\r\n\t]]", "")
                .replaceAll("\r\n", "\n")
                .replaceAll("\r", "\n");

        StringBuilder fixedOutput = new StringBuilder();
        String[] lines = cleaned.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String currentLine = lines[i].trim();

            if (currentLine.isEmpty()) {
                fixedOutput.append("\n");
                continue;
            }

            if (currentLine.endsWith("EG") && i + 1 < lines.length) {
                String nextLine = lines[i+1].trim();
                if (nextLine.matches("^[0-9A-Za-z].*")) {
                    currentLine = currentLine.substring(0, currentLine.length() - 2) + "EG" + nextLine;
                    i++;
                }
            }

            if (currentLine.matches("^[A-Za-z]+$") && i + 1 < lines.length) {
                String nextLine = lines[i+1].trim();
                if (nextLine.startsWith(":")) {
                    currentLine = currentLine + nextLine;
                    i++;
                }
            }

            if (currentLine.matches("^\\d+\\s+[0-9A-F]{12,}\\s+EG$") && i + 1 < lines.length) {
                String nextLine = lines[i+1].trim();
                if (nextLine.matches("^[0-9A-Za-z].*")) {
                    currentLine = currentLine + nextLine;
                    i++;
                }
            }

            fixedOutput.append(currentLine).append("\n");
        }

        cleaned = fixedOutput.toString();

        StringBuilder result = new StringBuilder();
        lines = cleaned.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty()) {
                result.append("\n");
                continue;
            }

            if (line.matches("^[A-Za-z][A-Za-z -]+\\s*:.*")) {
                Matcher matcher = Pattern.compile("^([A-Za-z][A-Za-z -]+)\\s*:(.*)$").matcher(line);
                if (matcher.find()) {
                    String label = matcher.group(1);
                    String value = matcher.group(2).trim();
                    result.append(String.format("  %-25s : %s\n", label, value));
                } else {
                    result.append("  ").append(line).append("\n");
                }
            }
            else if (line.matches("^\\d+\\s+[0-9A-F]{12,}.*")) {
                Matcher numMatcher = Pattern.compile("^(\\d+)\\s+(.*)$").matcher(line);
                if (numMatcher.find()) {
                    String num = numMatcher.group(1);
                    String rest = numMatcher.group(2);

                    Matcher serialMatcher = Pattern.compile("^([0-9A-F]{12,})\\s+(.*)$").matcher(rest);
                    if (serialMatcher.find()) {
                        String serial = serialMatcher.group(1);
                        String remaining = serialMatcher.group(2);

                        Matcher modelMatcher = Pattern.compile("^(EG[0-9A-Za-z-]+)\\s+(.*)$").matcher(remaining);
                        if (modelMatcher.find()) {
                            String model = modelMatcher.group(1);
                            String stats = modelMatcher.group(2);
                            result.append(String.format("  %-2s  %-16s %-15s %s\n", num, serial, model, stats));
                        } else {
                            result.append(String.format("  %-2s  %-16s %s\n", num, serial, remaining));
                        }
                    } else {
                        result.append(String.format("  %-2s  %s\n", num, rest));
                    }
                } else {
                    result.append("  ").append(line).append("\n");
                }
            }

            else if (line.matches("^\\d+\\s+(online|offline).*")) {
                Matcher matcher = Pattern.compile("^(\\d+)\\s+(online|offline)\\s+(.*)$").matcher(line);
                if (matcher.find()) {
                    String num = matcher.group(1);
                    String status = matcher.group(2);
                    String dates = matcher.group(3);
                    result.append(String.format("  %-2s  %-7s %s\n", num, status, dates));
                } else {
                    result.append("  ").append(line).append("\n");
                }
            }

            else if (line.matches("^\\d+\\s+.*")) {
                Matcher matcher = Pattern.compile("^(\\d+)\\s+(.*)$").matcher(line);
                if (matcher.find()) {
                    String num = matcher.group(1);
                    String rest = matcher.group(2);
                    result.append(String.format("  %-2s  %s\n", num, rest));
                } else {
                    result.append("  ").append(line).append("\n");
                }
            }

            else {
                result.append("  ").append(line).append("\n");
            }
        }

        return result.toString();

    }

    public String cleanCapturedOutput(String output) {
        return cleanTerminalOutput(output);
    }

    private void destacarIPs(CodeArea codeArea) {
        if (codeArea == null) return;

        String texto = codeArea.getText();
        codeArea.setStyleSpans(0, computeHighlighting(texto));
    }

    public void sendEnterKey() throws IOException {
        if (session != null && session.isConnected() && channel != null && channel.isConnected()) {
            PrintStream commander = new PrintStream(channel.getOutputStream());
            commander.println();
            commander.flush();
        }
    }


    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = IP_REGEX.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton("ip-address"), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    // ---------------------- TRATAMENTO TERMINAL e IP ---------------------- //



    // ---------------------- CONSULTA DE SINAL - REGEX ESPECIFICADO ---------------------- //
    public String queryOpticalSignal(String fs, String p) {
        synchronized(outputLock) {
            commandOutput.setLength(0);
            capturingOutput = true;
        }

        final boolean[] responseComplete = {false};
        final int[] previousLineCount = {0};
        final long[] lastChangeTime = {System.currentTimeMillis()};

        Thread monitorThread = new Thread(() -> {
            try {
                int stableCount = 0;
                while (!responseComplete[0] && capturingOutput) {
                    Thread.sleep(500);

                    int currentLines;
                    synchronized(outputLock) {
                        currentLines = countLines(commandOutput.toString());
                    }

                    if (currentLines > previousLineCount[0]) {
                        previousLineCount[0] = currentLines;
                        lastChangeTime[0] = System.currentTimeMillis();
                        stableCount = 0;
                    } else {
                        if (currentLines > 5) {
                            stableCount++;

                            if (stableCount >= 3 || (System.currentTimeMillis() - lastChangeTime[0]) > 3000) {
                                responseComplete[0] = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro no monitor de resposta: " + e.getMessage());
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.start();

        try {
            sendCommand("enable");
            Thread.sleep(500);
            sendCommand("config");
            Thread.sleep(500);
            sendCommand("interface gpon " + fs);
            Thread.sleep(500);
            sendCommand("display ont optical-info " + p + " all");

            long startTime = System.currentTimeMillis();
            while (!responseComplete[0] && (System.currentTimeMillis() - startTime < commandTimeout)) {
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Consulta interrompida: " + e.getMessage();
        } finally {
            capturingOutput = false;
            responseComplete[0] = true;
            monitorThread.interrupt();
        }

        String output;
        synchronized(outputLock) {
            output = commandOutput.toString();
        }

        return parseRealOpticalSignalOutput(output, fs + "/" + p);
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n").length;
    }

    private String parseRealOpticalSignalOutput(String output, String ontId) {
        output = cleanCapturedOutput(output);
        StringBuilder result = new StringBuilder();

        Pattern pattern = Pattern.compile(
                "(\\d+)\\s+(-\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(-\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)"
        );

        Pattern altPattern = Pattern.compile(
                "(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)"
        );

        List<String[]> ontData = new ArrayList<>();
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            String[] data = new String[8];
            for (int i = 0; i < 8; i++) {
                data[i] = matcher.group(i + 1);
            }
            ontData.add(data);
        }

        if (ontData.size() < 5) {
            ontData.clear();
            Matcher altMatcher = altPattern.matcher(output);

            while (altMatcher.find()) {
                String[] data = new String[8];
                for (int i = 0; i < 8; i++) {
                    data[i] = altMatcher.group(i + 1);
                }
                ontData.add(data);
            }
        }

        if (ontData.size() < 5) {
            ontData.clear();
            String[] lines = output.split("\n");

            for (String line : lines) {
                line = line.trim();
                if (line.matches("^\\d+\\s+[-\\d.]+\\s+[\\d.]+\\s+.*")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 8) {
                        try {
                            Integer.parseInt(parts[0]);
                            Double.parseDouble(parts[1]);
                            Double.parseDouble(parts[2]);

                            String[] data = new String[8];
                            System.arraycopy(parts, 0, data, 0, Math.min(parts.length, 8));
                            ontData.add(data);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        }

        int totalOnts = ontData.size();
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

        for (String[] data : ontData) {
            String ont = data[0];
            String rxPower = data[1];
            String txPower = data[2];
            String oltRxPower = data[3];
            String temp = data[4];
            String distance = data[7];

            try {
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
                } else if (rx <= -27.0) {
                    statusRX = " (ℹ RX VERIFICAR MÉDIA)";
                    weakSignalRX++;
                }

                if (tx < -29.0) {
                    statusTX = " (⚠ TX CRÍTICO)";
                    noSignalTX++;
                } else if (tx <= -27.0) {
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

                result.append(String.format(Locale.US, "%-9s%-16s%-16s%-14s%-12s%-8s%s\n",
                        ont, rxPower, txPower, oltRxPower, temp, distance, status));
            } catch (NumberFormatException e) {
                result.append(String.format("%-9s%-16s%-16s%-14s%-12s%-8s%s\n",
                        ont, rxPower, txPower, oltRxPower, temp, distance, " (⚠ ERRO NA ANÁLISE)"));
            }
        }

        result.append("----------------------------------------------------------------------\n\n");

        if (totalOnts > 0) {
            result.append("Status:\n");
            result.append("• (ℹ VERIFICAR MÉDIA) entre -27 e -29 dBm — analisar com média da primária\n");
            result.append("• (⚠ CRÍTICO) menor que -29 dBm — pode causar quedas/oscilação\n");

            double avgRx = totalRx / totalOnts;
            double avgTx = totalTx / totalOnts;

            result.append(String.format(Locale.US, "\nMédia Sinal RX: %.2f dBm", avgRx));
            result.append(String.format(Locale.US, "\nMédia Sinal TX: %.2f dBm\n", avgTx));

            result.append("\n");

            result.append("Total de ONTs: ").append(totalOnts).append("\n");
            result.append("ONTs com sinal RX acima do padrão: ").append(weakSignalRX).append("\n");
            result.append("ONTs com sinal RX crítico: ").append(noSignalRX).append("\n");
            result.append("ONTs com sinal TX acima do padrão: ").append(weakSignalTX).append("\n");
            result.append("ONTs com sinal TX crítico: ").append(noSignalTX).append("\n");
        } else {
            result.append("Não foram encontrados dados de sinal óptico.\n");
            result.append("Verifique se o comando foi executado corretamente e se há ONTs configuradas nesta porta.\n");
        }

        return result.toString();
    }
}
// ---------------------- CONSULTA DE SINAL - REGEX ESPECIFICADO ---------------------- //
