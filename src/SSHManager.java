import com.jcraft.jsch.*;

import java.io.PrintStream;
import java.util.Locale;
import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.InetAddress;


class OntEvent {
    int ontId;
    String downDate;
    String downTime;
    String downCause;

    public OntEvent(int ontId, String downDate, String downTime, String downCause) {
        this.ontId = ontId;
        this.downDate = downDate;
        this.downTime = downTime;
        this.downCause = downCause;
    }
}


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
    private final AtomicBoolean waitingForMorePrompt = new AtomicBoolean(false);
    private int connectTimeout = 30000;
    private int commandTimeout = 120000;
    private int shortCommandTimeout = 30000;
    private int summaryDataCommandTimeout = 180000;
    private long lastActivityTime;
    private ScheduledExecutorService inactivityTimer;
    private long inactivityTimeout = 300000;
    private Runnable onDisconnectCallback;

    // ---------------------- Pattern do IP ---------------------- //
    private static final String IP_PATTERN =
            "\\b((?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?::\\d{1,5}|/\\d{1,2})?\\b";
    private static final Pattern IP_REGEX = Pattern.compile(IP_PATTERN);
    // ---------------------- Pattern do IP ---------------------- //


    // ---------------------- OLT Reachable - TCP e ICMP ---------------------- //
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    public boolean isReachable(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public CompletableFuture<Boolean> checkOLTStatus(String host) {
        return CompletableFuture.supplyAsync(() -> {
            boolean reachable = false;
            try {
                reachable = isReachable(host, 22, 7000);

                if (!reachable) {
                }
            } catch (Exception e) {
                return false;
            }
            return reachable;
        });
    }

    // FALLBACK
    public boolean isOLTReachable(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            boolean pingResult = address.isReachable(5000);
            if (pingResult) {
                return true;
            }
        } catch (IOException e) {
        }
        int[] commonPorts = {22, 23, 80, 443, 161, 8080, 8443};
        for (int port : commonPorts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 3000);
                return true;
            } catch (IOException e) {
            }
        }
        return false;
    }
    // ---------------------- OLT Reachable - TCP e ICMP ---------------------- //


    // ---------------------- Connect & Disconnect SSH ---------------------- //
    public boolean connect(String host, String user, String password, CodeArea terminalArea, boolean isInteractiveTerminal) {
        this.terminalArea = terminalArea;
        this.lastActivityTime = System.currentTimeMillis();
        this.waitingForMorePrompt.set(false);

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "password");
            config.put("HostKeyAlgorithms", "ssh-rsa,ssh-dss,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256");
            config.put("kex", "diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group1-sha1,diffie-hellman-group14-sha1");
            config.put("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes192-cbc,aes256-cbc,3des-cbc,arcfour128,arcfour256,arcfour");
            config.put("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-cbc,aes192-cbc,aes256-cbc,3des-cbc,arcfour128,arcfour256,arcfour");
            config.put("mac.s2c", "hmac-sha2-256,hmac-sha2-512,hmac-sha1,hmac-md5,hmac-sha1-96,hmac-md5-96");
            config.put("mac.c2s", "hmac-sha2-256,hmac-sha2-512,hmac-sha1,hmac-md5,hmac-sha1-96,hmac-md5-96");
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

            if (isInteractiveTerminal && this.terminalArea != null) {
                Platform.runLater(() -> {
                    this.terminalArea.appendText("\n✅ Conectado a " + host + "\n");
                    destacarIPs(this.terminalArea);
                });
            } else if (!isInteractiveTerminal && terminalArea != null) {
                Platform.runLater(() -> {
                    terminalArea.appendText("");
                    destacarIPs(terminalArea);
                });
            }

            if (isInteractiveTerminal) {
                inactivityTimer = Executors.newSingleThreadScheduledExecutor();
                inactivityTimer.scheduleAtFixedRate(this::checkInactivity,
                        1, 1, TimeUnit.MINUTES);
            }
            return true;

        } catch (Exception e) {
            String errorMsg = "\n❌ Não foi possível conectar à OLT (" + host + ").\n\n" +
                    "Verifique se:\n" +
                    "1 - Você está na rede interna da empresa;\n" +
                    "2 - Se a OLT está bloqueada ou offline;\n" +
                    "3 - Não há firewall ou antivírus bloqueando a conexão com a porta 22.\n\n" +
                    "Detalhes do Erro:\n" +
                    e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" +
                    "Caso esteja tudo correto, contate imediatamente o Eduardo.";
            if (terminalArea != null) {
                Platform.runLater(() -> {
                    terminalArea.appendText(errorMsg);
                    destacarIPs(terminalArea);
                });
            } else {
                System.err.println(errorMsg);
            }
            disconnect();
            return false;
        }
    }

    public void setOnDisconnectCallback(Runnable onDisconnectCallback) {
        this.onDisconnectCallback = onDisconnectCallback;
    }

    public void disconnect() {
        if (!isRunning) {
            return;
        }
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
                executor = null;
            }

            if (channel != null) {
                try {
                    if (outputStream != null) outputStream.close();
                } catch (IOException e) { }
                try {
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) { }
                if (channel.isConnected()) channel.disconnect();
            }

            if (session != null && session.isConnected()) {
                session.disconnect();
            }

        } catch (Exception e) {
            System.err.println("Erro durante desconexão: " + e.getMessage());
            if (this.terminalArea != null) {
                Platform.runLater(() -> {
                    this.terminalArea.appendText("\n");
                    destacarIPs(this.terminalArea);
                });
            }
        } finally {
            session = null;
            channel = null;
            outputStream = null;
            inputStream = null;
        }
    }

    private void handleServerDisconnect() {
        if (isRunning) {
            if (onDisconnectCallback != null) {
                Platform.runLater(onDisconnectCallback);
            }
            disconnect();
        }
    }

    public boolean isWaitingForMorePromptActive() {
        return waitingForMorePrompt.get();
    }

    // FALLBACK
    private void showToastToGlobalTerminal(String message) {
        Platform.runLater(() -> {
            if (this.terminalArea != null && this.terminalArea.getScene() != null && this.terminalArea.getScene().getRoot() instanceof StackPane) {
                StackPane root = (StackPane) this.terminalArea.getScene().getRoot();
                Label toastLabel = new Label(message);
                toastLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); " +
                        "-fx-text-fill: white; -fx-padding: 10px 20px; " +
                        "-fx-background-radius: 20px;");
                toastLabel.setOpacity(0);

                root.getChildren().add(toastLabel);
                StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
                StackPane.setMargin(toastLabel, new Insets(0, 0, 95, 180));

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
        if (!isRunning || inactivityTimer == null) return;
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastActivityTime) > inactivityTimeout) {
            Platform.runLater(() -> {
                if (this.terminalArea != null) {
                    this.terminalArea.appendText("\n\n⏱ Estamos desconectando por inatividade (sem comandos por " +
                            (inactivityTimeout / 60000) + " minutos)...\n");
                    disconnect();
                }
            });
        }
    }
    // ---------------------- Connect & Disconnect SSH ---------------------- //


    // ---------------------- Tratamento Inside - Terminal ---------------------- //
    private static final Pattern CR_PROMPT_PATTERN = Pattern.compile("\\{\\s*<cr>.*?\\}:");
    private static final Pattern MORE_PROMPT_PATTERN = Pattern.compile(
            "(--|----) *More *(--|----|\\( *Press *'Q' *to *break *\\) *----)");

    private void readChannelOutput() {
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            while (isRunning && channel != null && channel.isConnected()) {

                int bytesRead = inputStream.read(buffer, 0, BUFFER_SIZE);

                if (bytesRead < 0) {
                    handleServerDisconnect();
                    break;
                }

                String rawChunk = new String(buffer, 0, bytesRead);

                if (CR_PROMPT_PATTERN.matcher(rawChunk).find()) {
                    try {
                        if (outputStream != null && channel != null && channel.isConnected()) {
                            outputStream.write('\r');
                            outputStream.flush();
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao enviar <cr> para o prompt: " + e.getMessage());
                    }
                }

                if (MORE_PROMPT_PATTERN.matcher(rawChunk).find()) {
                    waitingForMorePrompt.set(true);
                }

                String cleanedChunk = cleanTerminalOutput(rawChunk);

                if (capturingOutput) {
                    synchronized (outputLock) {
                        commandOutput.append(cleanedChunk);
                    }
                } else {
                    if (!cleanedChunk.isEmpty() && this.terminalArea != null) {
                        Platform.runLater(() -> {
                            if (this.terminalArea.getText().length() > 150000) {
                                this.terminalArea.replaceText(0, 75000, "");
                            }
                            this.terminalArea.appendText(cleanedChunk);
                            destacarIPs(this.terminalArea);
                            this.terminalArea.moveTo(this.terminalArea.getLength());
                            this.terminalArea.requestFollowCaret();
                        });
                    }
                }
            }

            if (isRunning && (channel == null || !channel.isConnected())) {
                handleServerDisconnect();
            }

        } catch (Exception e) {
            if (isRunning) {
                System.err.println("Exceção no readChannelOutput, tratando como desconexão: " + e.getMessage());
                handleServerDisconnect();
            }
        }
    }

    private String cleanTerminalOutput(String output) {
        if (output == null) return "";
        String cleaned = output;

        cleaned = cleaned.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "");
        cleaned = cleaned.replaceAll("\\x1B\\[\\?[0-9;]*[a-zA-Z]", "");
        cleaned = cleaned.replaceAll("\\x1B[()][0-9A-Za-z]", "");
        cleaned = cleaned.replaceAll("\\x1B=", "");
        cleaned = cleaned.replaceAll("\\x1B>", "");
        cleaned = cleaned.replaceAll("\\{ <cr>\\|\\|([A-Za-z0-9<>]+)\\|\\|<K> \\}:", "");
        cleaned = cleaned.replaceAll("\\{ <cr>\\|([A-Za-z0-9<>]+)\\|\\|<K> \\}:", "");
        cleaned = cleaned.replaceAll("\\{ <cr>\\|<[A-Za-z]+> \\}:", "");
        cleaned = cleaned.replaceAll("\\{ <cr> \\}:", "");
        cleaned = cleaned.replaceAll("\\{ [^}]*<cr>[^}]*\\}\\s*:", "");
        cleaned = cleaned.replaceAll("                                       ", "  ");
        cleaned = cleaned.replaceAll("                                       ", "  ");
        cleaned = cleaned.replaceAll("                                     ", "");
        //cleaned = cleaned.replaceAll("---- More ( Press 'Q' to break ) ----                                       ", "  ");
        //cleaned = cleaned.replaceAll("(--|----) *More *(--|----|\\( *Press *'Q' *to *break *\\) *----) * *([A-Za-z0-9<>]+)", "  ");
        //cleaned = cleaned.replaceAll("(--|----) *More *(--|----|\\( *Press *'Q' *to *break *\\) *---- * *)", "  ");
        //cleaned = cleaned.replaceAll("(--|----) *More *(--|----|\\( *Press *'Q' *to *break *\\) *----) * * *([A-Za-z0-9<>]+)", "  ");
        //cleaned = cleaned.replaceAll("(--|----) *More *(--|----|\\( *Press *'Q' *to *break *\\) *---- * * *)", "  ");
        cleaned = cleaned.replaceAll("[\\p{Cc}&&[^\\r\\n\\t\u0007]]", "");
        cleaned = cleaned.replace("\u0008", "");
        cleaned = cleaned.replaceAll("\r\n", "\n");
        cleaned = cleaned.replaceAll("\r", "\n");

        return cleaned;
    }

    public String cleanCapturedOutput(String output) {
        String cleaned = cleanTerminalOutput(output);
        cleaned = cleaned.replaceAll("(?m)^[ \t]*\r?\n", "").trim();
        return cleaned;
    }

    public void sendCommand(String command) {
        lastActivityTime = System.currentTimeMillis();
        try {
            if (outputStream != null && channel != null && channel.isConnected()) {
                outputStream.write((command + "\n").getBytes());
                outputStream.flush();
                waitingForMorePrompt.set(false);
            }
        } catch (IOException e) {
            String errorMsg = "\n❌ Erro ao enviar comando: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n";
            if (this.terminalArea != null && !capturingOutput) {
                Platform.runLater(() -> {
                    this.terminalArea.appendText(errorMsg);
                    destacarIPs(this.terminalArea);
                });
            } else if (capturingOutput) {
                synchronized (outputLock) {
                    commandOutput.append(errorMsg);
                }
            } else {
                System.err.println("Erro ao enviar comando (SSHManager): " + e.getMessage());
            }
        }
    }

    public void sendRawInput(String input) {
        lastActivityTime = System.currentTimeMillis();
        boolean wasWaitingForMore = waitingForMorePrompt.get();

        try {
            if (outputStream != null && channel != null && channel.isConnected()) {
                outputStream.write(input.getBytes());
                outputStream.flush();
                waitingForMorePrompt.set(false);

                if (wasWaitingForMore && this.terminalArea != null && !capturingOutput &&
                        (" ".equals(input) || "\r".equals(input) || "\n".equals(input) || input.isEmpty())) {
                    Platform.runLater(() -> {
                        String currentText = this.terminalArea.getText();

                        String[] morePrompts = {
                                "---- More ( Press 'Q' to break ) ----",
                                "More: <space>",
                                "-- More --"
                        };

                        Pattern[] morePromptPatterns = new Pattern[morePrompts.length + 1];
                        for (int i = 0; i < morePrompts.length; i++) {
                            morePromptPatterns[i] = Pattern.compile(Pattern.quote(morePrompts[i]));
                        }
                        morePromptPatterns[morePrompts.length] = MORE_PROMPT_PATTERN;

                        for (Pattern pattern : morePromptPatterns) {
                            Matcher m = pattern.matcher(currentText);

                            int lastMatchStart = -1;
                            int lastMatchEnd = -1;

                            while (m.find()) {
                                lastMatchStart = m.start();
                                lastMatchEnd = m.end();
                            }

                            if (lastMatchStart != -1) {
                                int endDelete = lastMatchEnd;
                                if (endDelete < currentText.length()) {
                                    char nextChar = currentText.charAt(endDelete);
                                    if (nextChar == '\r' || nextChar == '\n') {
                                        endDelete++;
                                        if (nextChar == '\r' && endDelete < currentText.length()
                                                && currentText.charAt(endDelete) == '\n') {
                                            endDelete++;
                                        }
                                    }
                                }
                                this.terminalArea.deleteText(lastMatchStart, endDelete);
                                break;
                            }
                        }

                        this.terminalArea.requestFollowCaret();
                    });
                }
            }
        } catch (IOException e) {
            String errorMsg = "\n❌ Erro ao enviar input bruto: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n";
            if (this.terminalArea != null && !capturingOutput) {
                Platform.runLater(() -> {
                    this.terminalArea.appendText(errorMsg);
                    destacarIPs(this.terminalArea);
                });
            } else {
                System.err.println("Erro ao enviar input bruto (SSHManager): " + e.getMessage());
                if (capturingOutput) {
                    synchronized (outputLock) {
                        commandOutput.append(errorMsg);
                    }
                }
            }
        }
    }


    public void sendEnterKey() throws IOException {
        if (session != null && session.isConnected() && channel != null && channel.isConnected() && outputStream != null) {
            outputStream.write('\r');
            outputStream.flush();
        } else {
            throw new IOException("Não conectado ou stream não disponível para enviar Enter.");
        }
    }

    private String executeCommandAndCaptureOutput(List<String> commandsToExecute, long overallTimeoutMillis) {
        synchronized (outputLock) {
            commandOutput.setLength(0);
            capturingOutput = true;
        }

        final boolean[] responseComplete = {false};
        final int[] previousLineCount = {0};
        final long[] lastOutputTime = {System.currentTimeMillis()};

        final long stabilityCheckInterval = 750;
        final long initialGracePeriodMillis = 2000;
        final int requiredStableIterationsForPromptCheck = 3;
        final long longPauseAfterOutputTimeoutMillis = 45000;

        final String oltPromptPatternStr = "^\\s*[\\w.-]+(?:\\([\\w-./]*\\))?[>#]\\s*$";
        final Pattern oltPromptRegex = Pattern.compile(oltPromptPatternStr);

        Thread monitorThread = new Thread(() -> {
            int stableIterations = 0;
            long initialGracePeriodEndTime = System.currentTimeMillis() + initialGracePeriodMillis;

            try {
                while (!responseComplete[0] && capturingOutput) {
                    Thread.sleep(stabilityCheckInterval);

                    String currentOutputSnapshot;
                    int currentLines;
                    synchronized (outputLock) {
                        currentOutputSnapshot = commandOutput.toString();
                        currentLines = countLines(currentOutputSnapshot);
                    }

                    Matcher moreMatcher = MORE_PROMPT_PATTERN.matcher(currentOutputSnapshot);
                    if (moreMatcher.find()) {
                        try {
                            if (outputStream != null && channel != null && channel.isConnected()) {
                                outputStream.write(' ');
                                outputStream.flush();

                                previousLineCount[0] = currentLines;
                                lastOutputTime[0] = System.currentTimeMillis();
                                stableIterations = 0;

                                synchronized (outputLock) {
                                    String tempOutput = MORE_PROMPT_PATTERN.matcher(commandOutput.toString()).replaceAll("");
                                    tempOutput = tempOutput.replaceAll("                                       ", "");
                                    commandOutput.setLength(0);
                                    commandOutput.append(tempOutput);
                                }
                                continue;
                            }
                        } catch (IOException e) {
                            System.err.println("Error sending space for 'More' during capture: " + e.getMessage());
                        }
                    }

                    boolean hasNewOutput = (currentLines > previousLineCount[0]);
                    if (hasNewOutput) {
                        previousLineCount[0] = currentLines;
                        lastOutputTime[0] = System.currentTimeMillis();
                        stableIterations = 0;
                    } else if (currentLines > 0 && System.currentTimeMillis() > initialGracePeriodEndTime) {
                        stableIterations++;
                    }


                    if (System.currentTimeMillis() < initialGracePeriodEndTime && currentLines == 0) {
                        continue;
                    }

                    String lowerCurrentSnapshot = currentOutputSnapshot.toLowerCase();
                    if ((lowerCurrentSnapshot.contains("failure:") &&
                            !lowerCurrentSnapshot.contains("failure: the ont is offline") &&
                            !lowerCurrentSnapshot.contains("failure: record does not exist")) ||
                            lowerCurrentSnapshot.contains("parameter error") ||
                            lowerCurrentSnapshot.contains("unknown command") ||
                            lowerCurrentSnapshot.contains("can not find the path") ||
                            lowerCurrentSnapshot.contains("system busy") ||
                            lowerCurrentSnapshot.contains("error:")) {

                        Thread.sleep(500);
                        synchronized (outputLock) {
                            currentOutputSnapshot = commandOutput.toString();
                        }
                        String updatedLowerSnapshot = currentOutputSnapshot.toLowerCase();

                        if ((updatedLowerSnapshot.contains("failure:") &&
                                !updatedLowerSnapshot.contains("failure: the ont is offline") &&
                                !updatedLowerSnapshot.contains("failure: record does not exist")) ||
                                updatedLowerSnapshot.contains("parameter error") ||
                                updatedLowerSnapshot.contains("unknown command") ||
                                updatedLowerSnapshot.contains("can not find the path") ||
                                updatedLowerSnapshot.contains("system busy") ||
                                updatedLowerSnapshot.contains("error:")) {
                            responseComplete[0] = true;
                            continue;
                        }
                    }

                    if (currentLines > 0 && stableIterations >= requiredStableIterationsForPromptCheck) {
                        String[] outputLinesSnapshot = currentOutputSnapshot.split("\\r?\\n");
                        String lastNonEmptyLineSnapshot = "";
                        if (outputLinesSnapshot.length > 0) {
                            for (int k = outputLinesSnapshot.length - 1; k >= 0; k--) {
                                if (!outputLinesSnapshot[k].trim().isEmpty()) {
                                    lastNonEmptyLineSnapshot = outputLinesSnapshot[k].trim();
                                    break;
                                }
                            }
                        }
                        if (!lastNonEmptyLineSnapshot.isEmpty() && oltPromptRegex.matcher(lastNonEmptyLineSnapshot).matches()) {
                            responseComplete[0] = true;
                            continue;
                        }
                    }

                    if (currentLines > 0 && (System.currentTimeMillis() - lastOutputTime[0]) > longPauseAfterOutputTimeoutMillis) {
                        responseComplete[0] = true;
                        continue;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                responseComplete[0] = true;
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();

        String capturedStr = "Failure in capture or command produced no output.";
        try {
            for (String cmd : commandsToExecute) {
                sendCommand(cmd);
                Thread.sleep(500);
            }

            long startTime = System.currentTimeMillis();
            while (!responseComplete[0] && (System.currentTimeMillis() - startTime < overallTimeoutMillis)) {
                Thread.sleep(200);
            }

            if (!responseComplete[0] && (System.currentTimeMillis() - startTime >= overallTimeoutMillis)) {
                String partialOutputPreview = "";
                synchronized (outputLock) {
                    partialOutputPreview = commandOutput.substring(0, Math.min(150, commandOutput.length())).replace("\n", " ");
                }
                System.err.println("Command (" + String.join("; ", commandsToExecute) +
                        ") OVERALL TIMEOUT (" + overallTimeoutMillis + "ms). Partial output: " + partialOutputPreview);
            }

            if (monitorThread.isAlive()) {
                monitorThread.interrupt();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            capturedStr = "Command execution interrupted: " + e.getMessage();
            System.err.println(capturedStr);
        } catch (Exception e) {
            capturedStr = "Error executing command: " + e.getMessage();
            System.err.println("Error in executeCommandAndCaptureOutput: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            capturingOutput = false;
            try {
                if (monitorThread.isAlive()) {
                    monitorThread.join(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while joining monitorThread.");
            }
        }

        synchronized (outputLock) {
            capturedStr = commandOutput.toString();
        }
        return cleanCapturedOutput(capturedStr);
    }

    public void forceAdvancePagination() {
        waitingForMorePrompt.set(true);
        sendRawInput(" ");
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }


    private void destacarIPs(CodeArea codeAreaToHighlight) {
        if (codeAreaToHighlight == null) return;
        Platform.runLater(() -> {
            String texto = codeAreaToHighlight.getText();
            codeAreaToHighlight.setStyleSpans(0, computeHighlighting(texto));
        });
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
    // ---------------------- Tratamento Inside - Terminal ---------------------- //


    // ------------------------------ Consulta de Sinal ------------------------------ //
    public String queryOpticalSignal(String frameSlot, String portOnCard) {
        executeCommandAndCaptureOutput(
                Arrays.asList("enable", "config", "interface gpon " + frameSlot),
                shortCommandTimeout
        );

        String rawOutput = executeCommandAndCaptureOutput(
                Collections.singletonList("display ont optical-info " + portOnCard + " all"),
                commandTimeout
        );
        return parseRealOpticalSignalOutput(rawOutput, frameSlot + "/" + portOnCard);
    }

    private String parseRealOpticalSignalOutput(String output, String gponPortContext) {
        StringBuilder result = new StringBuilder();
        result.append("RESULTADO DA CONSULTA DE SINAL (PON ").append(gponPortContext).append("):\n");
        result.append("-------------------------------------------------------------------------------------------------------------\n");

        if (output.toLowerCase().contains("failure: the ont is offline") ||
                output.toLowerCase().contains("parameter error") ||
                output.toLowerCase().contains("ont does not exist")) {
            result.append("Falha ao obter informações. A ONT pode estar offline, não existir ou o parâmetro está incorreto.\n");
            result.append("-------------------------------------------------------------------------------------------------------------\n");
            return result.toString();
        }

        boolean hasDistance = output.contains("Distance") || output.contains("(m)");

        Pattern dataPatternWithDistance = Pattern.compile(
                "^\\s*(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s*$",
                Pattern.MULTILINE
        );

        Pattern dataPatternWithoutDistance = Pattern.compile(
                "^\\s*(\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+)\\s*$",
                Pattern.MULTILINE
        );

        List<Map<String, String>> ontDataList = new ArrayList<>();
        String[] lines = output.split("\n");
        boolean dataStarted = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty()) {
                continue;
            }

            if (trimmedLine.contains("ONT") && trimmedLine.contains("Rx power") &&
                    trimmedLine.contains("Tx power") && trimmedLine.contains("OLT Rx")) {
                dataStarted = false;
                continue;
            }

            if (trimmedLine.matches("-+\\s*-+.*")) {
                dataStarted = true;
                continue;
            }

            if (!dataStarted || trimmedLine.startsWith("Command:") ||
                    trimmedLine.startsWith("display ont optical-info") ||
                    trimmedLine.contains("More") || trimmedLine.contains("Press")) {
                continue;
            }

            Matcher m = null;
            if (hasDistance) {
                m = dataPatternWithDistance.matcher(trimmedLine);
            } else {
                m = dataPatternWithoutDistance.matcher(trimmedLine);
            }

            if (m != null && m.matches()) {
                Map<String, String> data = new HashMap<>();
                data.put("ontId", m.group(1));
                data.put("rxPower", m.group(2));
                data.put("txPower", m.group(3));
                data.put("oltRxPower", m.group(4));
                data.put("temp", m.group(5));
                data.put("voltage", m.group(6));
                data.put("current", m.group(7));

                if (hasDistance && m.groupCount() >= 8) {
                    data.put("distance", m.group(8));
                } else {
                    data.put("distance", "N/A");
                }

                ontDataList.add(data);
            }
        }

        if (hasDistance) {
            result.append(String.format("%-7s %-15s %-15s %-15s %-10s %-12s %-12s %-10s\n",
                    "ONT-ID", "RX Power(dBm)", "TX Power(dBm)", "OLT RX(dBm)", "Temp(°C)", "Voltage(V)", "Current(mA)", "Dist(m)"));
        } else {
            result.append(String.format("%-7s %-15s %-15s %-15s %-10s %-12s %-12s\n",
                    "ONT-ID", "RX Power(dBm)", "TX Power(dBm)", "OLT RX(dBm)", "Temp(°C)", "Voltage(V)", "Current(mA)"));
        }
        result.append("-------------------------------------------------------------------------------------------------------------\n");

        if (ontDataList.isEmpty()) {
            result.append("Não foram encontrados dados de sinal óptico formatados.\n");
            result.append("Output recebido para debug:\n");
            result.append("=".repeat(50)).append("\n");

            result.append("Formato detectado: ").append(hasDistance ? "COM Distance" : "SEM Distance").append("\n");

            String[] debugLines = output.split("\n");
            for (int i = 0; i < Math.min(15, debugLines.length); i++) {
                result.append("Linha ").append(i).append(": '").append(debugLines[i]).append("'\n");
            }
            result.append("=".repeat(50)).append("\n");
            result.append("Verifique se o comando foi executado corretamente e se há ONTs online.\n");
        } else {
            int totalOnts = ontDataList.size();
            int weakSignalRX = 0, noSignalRX = 0, weakSignalTX_OLTRX = 0, noSignalTX_OLTRX = 0;
            double sumRx = 0, sumOltRx = 0;

            for (Map<String, String> data : ontDataList) {
                String ontId = data.get("ontId");
                String rxPowerStr = data.get("rxPower");
                String txPowerStr = data.get("txPower");
                String oltRxPowerStr = data.get("oltRxPower");
                String temp = data.get("temp");
                String voltage = data.get("voltage");
                String current = data.get("current");
                String distance = data.get("distance");
                String statusRemark = "";

                try {
                    double rx = Double.parseDouble(rxPowerStr);
                    double oltRx = Double.parseDouble(oltRxPowerStr);
                    sumRx += rx;
                    sumOltRx += oltRx;

                    if (rx <= -29.0 || rx == 0.0) {
                        statusRemark += " (RX CRÍTICO)";
                        noSignalRX++;
                    } else if (rx <= -27.0) {
                        statusRemark += " (RX Atenção)";
                        weakSignalRX++;
                    }

                    if (oltRx <= -29.0 || oltRx == 0.0) {
                        statusRemark += " (TX CRÍTICO)";
                        noSignalTX_OLTRX++;
                    } else if (oltRx <= -27.0) {
                        statusRemark += " (TX Atenção)";
                        weakSignalTX_OLTRX++;
                    }

                    if (statusRemark.isEmpty()) statusRemark = " (OK)";

                    if (hasDistance) {
                        result.append(String.format(Locale.US, "%-7s %-15s %-15s %-15s %-10s %-12s %-12s %-10s %s\n",
                                ontId, rxPowerStr, txPowerStr, oltRxPowerStr, temp, voltage, current, distance, statusRemark.trim()));
                    } else {
                        result.append(String.format(Locale.US, "%-7s %-15s %-15s %-15s %-10s %-12s %-12s %s\n",
                                ontId, rxPowerStr, txPowerStr, oltRxPowerStr, temp, voltage, current, statusRemark.trim()));
                    }
                } catch (NumberFormatException e) {
                    if (hasDistance) {
                        result.append(String.format(Locale.US, "%-7s %-15s %-15s %-15s %-10s %-12s %-12s %-10s (Erro ao parsear dados)\n",
                                ontId, rxPowerStr, txPowerStr, oltRxPowerStr, temp, voltage, current, distance));
                    } else {
                        result.append(String.format(Locale.US, "%-7s %-15s %-15s %-15s %-10s %-12s %-12s (Erro ao parsear dados)\n",
                                ontId, rxPowerStr, txPowerStr, oltRxPowerStr, temp, voltage, current));
                    }
                }
            }

            result.append("-------------------------------------------------------------------------------------------------------------\n\n");
            result.append("Status Legenda:\n");
            result.append("• (Atenção) Sinal entre -27 e -29 dBm.\n");
            result.append("• (CRÍTICO) Sinal abaixo de -29 dBm ou 0.00 dBm (sem sinal).\n");

            if (totalOnts > 0) {
                result.append(String.format(Locale.US, "\nMédia Sinal RX: %.2f dBm", (sumRx / totalOnts)));
                result.append(String.format(Locale.US, "\nMédia Sinal TX: %.2f dBm\n", (sumOltRx / totalOnts)));
                result.append("\nTotal de ONTs na consulta: ").append(totalOnts).append("\n");
                result.append("ONTs com RX em Atenção: ").append(weakSignalRX).append(", Crítico: ").append(noSignalRX).append("\n");
                result.append("ONTs com TX em Atenção: ").append(weakSignalTX_OLTRX).append(", Crítico: ").append(noSignalTX_OLTRX).append("\n");
            }
        }
        result.append("--------------------------------------------------------------------------------------\n");
        return result.toString();
    }
    // ------------------------------ Consulta de Sinal ------------------------------ //


    // ------------------------------ Summary ------------------------------ //
    private String formatarPonParaModelinho(String fsp) {
        if (fsp == null) return "PON N/A";
        return "" + fsp.replace("/", "/");
    }

    public String queryPonSummary(String oltName, String fsp) {
        StringBuilder combinedOutputForParsing = new StringBuilder();
        String errorEncounteredMessage = null;

        String modeSetupOutput = executeCommandAndCaptureOutput(Arrays.asList("enable", "config"), shortCommandTimeout);
        if (modeSetupOutput.toLowerCase().contains("failure") || modeSetupOutput.toLowerCase().contains("error:")) {
            System.err.println("PON Summary (" + fsp + "): Failed to enter enable/config mode. Output: " + modeSetupOutput);
        }

        String portDescData = executeCommandAndCaptureOutput(
                Collections.singletonList("display port desc " + fsp),
                shortCommandTimeout
        );
        if (portDescData.toLowerCase().contains("failure:") || portDescData.toLowerCase().contains("error:")) {
            errorEncounteredMessage = "Erro ao buscar descrição da porta: " + portDescData.lines().filter(l->l.toLowerCase().contains("failure:")||l.toLowerCase().contains("error:")).findFirst().orElse("");
        }
        combinedOutputForParsing.append("--- PORT DESCRIPTION START ---\n")
                .append(portDescData)
                .append("\n--- PORT DESCRIPTION END ---\n\n");

        String ontInfoSummaryData = executeCommandAndCaptureOutput(
                Collections.singletonList("display ont info summary " + fsp),
                summaryDataCommandTimeout
        );
        if (ontInfoSummaryData.toLowerCase().contains("failure:") || ontInfoSummaryData.toLowerCase().contains("error:")) {
            if(errorEncounteredMessage == null) errorEncounteredMessage = ""; else errorEncounteredMessage += "; ";
            errorEncounteredMessage += "Erro ao buscar summary de ONTs: " + ontInfoSummaryData.lines().filter(l->l.toLowerCase().contains("failure:")||l.toLowerCase().contains("error:")).findFirst().orElse("");
        }
        combinedOutputForParsing.append("--- ONT INFO SUMMARY START ---\n")
                .append(ontInfoSummaryData)
                .append("\n--- ONT INFO SUMMARY END ---\n");

        if(errorEncounteredMessage != null){
            System.err.println("PON Summary query for " + fsp + " encontrou erros: " + errorEncounteredMessage);
        }
        return parsePonSummaryOutput(oltName, combinedOutputForParsing.toString(), fsp);
    }

    private String parsePonSummaryOutput(String oltName, String combinedOutput, String fsp) {
        StringBuilder result = new StringBuilder("SUMMARY DA PON ").append(fsp).append(" (").append(oltName).append("):\n");
        result.append("---------------------------------------------------------------------------------------------------\n");

        String portDescription = "N/A";
        String portDescContent = "";
        String ontSummaryContent = "";

        Matcher portDescMatcher = Pattern.compile("--- PORT DESCRIPTION START ---(.*?)--- PORT DESCRIPTION END ---", Pattern.DOTALL).matcher(combinedOutput);
        if (portDescMatcher.find()) {
            portDescContent = portDescMatcher.group(1).trim();
        }

        Matcher ontSummaryMatcher = Pattern.compile("--- ONT INFO SUMMARY START ---(.*?)--- ONT INFO SUMMARY END ---", Pattern.DOTALL).matcher(combinedOutput);
        if (ontSummaryMatcher.find()) {
            ontSummaryContent = ontSummaryMatcher.group(1).trim();
        }


        if (!portDescContent.isEmpty()) {
            String[] portDescLines = portDescContent.split("\\r?\\n");
            boolean descHeaderParsed = false;
            Pattern descDataLinePattern = Pattern.compile(
                    "^([0-9]+\\s*/\\s*[0-9]+\\s*/\\s*[0-9\\sA-Za-z_-]+?)\\s+([^\\s]+)\\s+(.+)$"
            );

            for (String line : portDescLines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("---") ||
                        trimmedLine.toLowerCase().startsWith("command:") ||
                        trimmedLine.toLowerCase().startsWith("display port desc")) continue;

                if (!descHeaderParsed &&
                        trimmedLine.toLowerCase().contains("f/ s/ p") &&
                        trimmedLine.toLowerCase().contains("ima group") &&
                        trimmedLine.toLowerCase().contains("port description")) {
                    descHeaderParsed = true;
                    continue;
                }

                Matcher matcher = descDataLinePattern.matcher(trimmedLine);
                if (matcher.matches()) {
                    String fspFromTableRaw = matcher.group(1).trim();
                    String descriptionCandidate = matcher.group(3).trim();
                    String fspTableNormalized = fspFromTableRaw.replaceAll("\\s+", "");
                    String commandFspNormalized = fsp.replaceAll("\\s+", "");

                    if (fspTableNormalized.equals(commandFspNormalized)) {
                        if (!descriptionCandidate.equalsIgnoreCase("Port Description") && !descriptionCandidate.matches("-+")) {
                            portDescription = descriptionCandidate;
                            break;
                        }
                    }
                }
            }
        }
        if (portDescContent.toLowerCase().contains("failure:") || portDescContent.toLowerCase().contains("error:")){
            portDescription = "Erro ao obter descrição";
        }


        result.append("Descrição da Porta ").append(fsp).append(": ").append(portDescription).append("\n");
        result.append("---------------------------------------------------------------------------------------------------\n");

        String totalOnts = "N/A";
        String onlineOnts = "N/A";

        if (!ontSummaryContent.isEmpty()) {
            Pattern summaryCountPattern = Pattern.compile(
                    "In port\\s+" + fsp.replace("/", "\\s*/\\s*") +
                            ".*?total of ONTs are:\\s*(\\d+)" +
                            ".*?online:\\s*(\\d+)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher summaryCountMatcher = summaryCountPattern.matcher(ontSummaryContent);
            if (summaryCountMatcher.find()) {
                totalOnts = summaryCountMatcher.group(1);
                onlineOnts = summaryCountMatcher.group(2);
            }

            if (!"N/A".equals(totalOnts)) {
                result.append("Porta: ").append(fsp)
                        .append(" | Total de ONTs: ").append(totalOnts)
                        .append(" | Online: ").append(onlineOnts).append("\n");
            } else if (ontSummaryContent.toLowerCase().contains("failure:") || ontSummaryContent.toLowerCase().contains("error:")){
                result.append("Erro ao obter contagem de ONTs para ").append(fsp).append(".\n");
            }
            else {
                result.append("Contagem de ONTs (linha 'In port...') não encontrada ou formato não reconhecido para ").append(fsp).append(".\n");
            }
            result.append("---------------------------------------------------------------------------------------------------\n");


            result.append("\nTABELA DE STATUS DAS ONTs:\n");
            result.append(String.format("%-7s %-10s %-22s %-22s %-25s\n", "ONT ID", "Estado", "Último UpTime", "Último DownTime", "Causa Queda"));
            result.append("---------------------------------------------------------------------------------------------------\n");

            Pattern statusPattern = Pattern.compile(
                    "^\\s*(\\d+)\\s+(online|offline)\\s+(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(.+?)\\s*$",
                    Pattern.MULTILINE
            );
            Matcher statusMatcher = statusPattern.matcher(ontSummaryContent);
            boolean statusFound = false;
            List<OntEvent> allOfflineEventsForAnalysis = new ArrayList<>();

            while (statusMatcher.find()) {
                statusFound = true;
                String ontIdStr = statusMatcher.group(1);
                String state = statusMatcher.group(2);
                String upTime = statusMatcher.group(3);
                String downTime = statusMatcher.group(4);
                String downCause = statusMatcher.group(5).trim();

                result.append(String.format("%-7s %-10s %-22s %-22s %-25s\n",
                        ontIdStr, state, upTime, downTime, downCause));

                if ("offline".equalsIgnoreCase(state)) {
                    try {
                        allOfflineEventsForAnalysis.add(new OntEvent(Integer.parseInt(ontIdStr),
                                downTime.substring(0, 10),
                                downTime.substring(11),
                                downCause));
                    } catch (Exception e) {
                        System.err.println("Error parsing ONT event for analysis: " + e.getMessage() + " Line: " + statusMatcher.group(0));
                    }
                }
            }
            if (!statusFound && !ontSummaryContent.toLowerCase().contains("failure:") && !ontSummaryContent.toLowerCase().contains("error:")) {
                result.append("Nenhuma ONT encontrada na tabela de status ou formato não reconhecido.\n");
            } else if (!statusFound && (ontSummaryContent.toLowerCase().contains("failure:") || ontSummaryContent.toLowerCase().contains("error:"))){
                result.append("Falha ao obter dados de status das ONTs.\n");
            }
            result.append("---------------------------------------------------------------------------------------------------\n");

            result.append("\nTABELA DE INFORMAÇÕES DAS ONTs:\n");
            result.append(String.format("%-7s %-20s %-18s %-10s %-15s %s\n", "ONT ID", "SN", "Tipo", "Dist(m)", "RX/TX Pwr", "Descrição"));
            result.append("---------------------------------------------------------------------------------------------------\n");
            Pattern infoPattern = Pattern.compile(
                    "^\\s*(\\d+)\\s+([0-9A-Z]+(?:\\([0-9A-Z-]+\\))?)\\s+([0-9A-Za-z\\s\\-.\\(\\)]+?)\\s+(\\d+)\\s+(-?\\d+\\.\\d+\\/-?\\d+\\.\\d+)\\s*(.*)$",
                    Pattern.MULTILINE
            );
            Matcher infoMatcher = infoPattern.matcher(ontSummaryContent);
            boolean infoFound = false;
            while (infoMatcher.find()) {
                infoFound = true;
                result.append(String.format("%-7s %-20s %-18s %-10s %-15s %s\n",
                        infoMatcher.group(1), infoMatcher.group(2), infoMatcher.group(3).trim(),
                        infoMatcher.group(4), infoMatcher.group(5), infoMatcher.group(6).trim()));
            }
            if (!infoFound && !ontSummaryContent.toLowerCase().contains("failure:") && !ontSummaryContent.toLowerCase().contains("error:")) {
                result.append("Nenhuma ONT encontrada na tabela de informações ou formato não reconhecido.\n");
            } else if (!infoFound && (ontSummaryContent.toLowerCase().contains("failure:") || ontSummaryContent.toLowerCase().contains("error:"))){
                result.append("Falha ao obter dados de informações das ONTs.\n");
            }
            result.append("---------------------------------------------------------------------------------------------------\n");

        } else {
            result.append("Não foi possível obter dados de resumo das ONTs para ").append(fsp).append(".\n");
            result.append("---------------------------------------------------------------------------------------------------\n");
        }

        List<OntEvent> offlineEventsForAnalysisFromSummary = new ArrayList<>();
        if (ontSummaryContent != null && !ontSummaryContent.isEmpty()) {
            Pattern statusPatternForAnalysis = Pattern.compile(
                    "^\\s*(\\d+)\\s+offline\\s+\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(.+?)\\s*$",
                    Pattern.MULTILINE);
            Matcher statusMatcherForAnalysis = statusPatternForAnalysis.matcher(ontSummaryContent);
            while(statusMatcherForAnalysis.find()){
                try {
                    offlineEventsForAnalysisFromSummary.add(new OntEvent(Integer.parseInt(statusMatcherForAnalysis.group(1)),
                            statusMatcherForAnalysis.group(2).substring(0, 10),
                            statusMatcherForAnalysis.group(2).substring(11),
                            statusMatcherForAnalysis.group(3).trim()));
                } catch (Exception e) {
                    System.err.println("Outage Analysis - Error parsing offline event: " + e.getMessage() + " Line: " + statusMatcherForAnalysis.group(0));
                }
            }
        }

        result.append("\n\n------------------------------------------------------------------------------\n");
        result.append("⚠ ANÁLISE DE POSSÍVEIS ROMPIMENTOS\n");

        Map<String, Map<String, List<OntEvent>>> eventsByDateHour = new HashMap<>();
        for (OntEvent event : offlineEventsForAnalysisFromSummary) {
            String hour = event.downTime.substring(0, 2);
            String dateHourKey = event.downDate + " " + hour;
            eventsByDateHour.computeIfAbsent(dateHourKey, k -> new HashMap<>())
                    .computeIfAbsent(event.downCause, k -> new ArrayList<>())
                    .add(event);
        }

        boolean foundPotentialOutage = false;
        if (!offlineEventsForAnalysisFromSummary.isEmpty()){
            for (Map.Entry<String, Map<String, List<OntEvent>>> dateHourEntry : eventsByDateHour.entrySet()) {
                String dateHour = dateHourEntry.getKey();
                Map<String, List<OntEvent>> causeMap = dateHourEntry.getValue();
                int totalEventsInHour = causeMap.values().stream().mapToInt(List::size).sum();

                if (totalEventsInHour >= 2) {
                    foundPotentialOutage = true;
                    result.append("\n⚠ POSSÍVEL ROMPIMENTO OU DROP ROMPIDO DETECTADO! \n");
                    result.append("- Data/Hora (aproximada): ").append(dateHour).append(":XX:XX\n");
                    result.append("- Total de ONTs offline afetadas nesta hora: ").append(totalEventsInHour).append("\n");
                    result.append("- Motivos da Queda:\n");

                    String motivoPrincipal = "";
                    int maxCountMotivoPrincipal = 0;

                    for (Map.Entry<String, List<OntEvent>> causeEntry : causeMap.entrySet()) {
                        String motivo = causeEntry.getKey();
                        List<OntEvent> ontEventsForCause = causeEntry.getValue();
                        int quantidade = ontEventsForCause.size();

                        if (quantidade > maxCountMotivoPrincipal) {
                            motivoPrincipal = motivo;
                            maxCountMotivoPrincipal = quantidade;
                        }
                        result.append("  • ").append(motivo).append(": ").append(quantidade).append(" ONTs\n");
                        result.append("    ONT IDs: ");
                        result.append(ontEventsForCause.stream().map(e -> String.valueOf(e.ontId)).collect(Collectors.joining(", ")));
                        result.append("\n");
                    }

                    result.append("\n📝 MODELINHO:\n");
                    result.append("").append(oltName).append("\n");
                    result.append("").append(formatarPonParaModelinho(fsp)).append("    -    ");
                    result.append(portDescription.isEmpty() || "N/A".equals(portDescription) || portDescription.contains("Erro ao obter") ? "DESCRIÇÃO NÃO ENCONTRADA/ERRO" : portDescription).append("\n");
                    result.append("").append(totalEventsInHour).append(" clientes OFF por ").append(motivoPrincipal.isEmpty() ? "Causa Indeterminada" : motivoPrincipal)
                            .append(" desde ").append(dateHour).append(":XX:XX\n");
                    result.append("(Endereço do cliente afetado/referência)\n");
                }
            }
        }


        if (offlineEventsForAnalysisFromSummary.isEmpty() && (ontSummaryContent != null && !ontSummaryContent.toLowerCase().contains("failure:") && !ontSummaryContent.toLowerCase().contains("error:"))) {
            result.append("\n✅ NENHUMA ONT OFFLINE DETECTADA NA PON.\n");
        } else if (!foundPotentialOutage && !offlineEventsForAnalysisFromSummary.isEmpty()) {
            result.append("\n✅ NENHUM PADRÃO DE ROMPIMENTO EM GRUPO DETECTADO.\n");
            result.append("  - ONTs e ONUs offline podem ser por motivos isolados.\n");
        } else if (offlineEventsForAnalysisFromSummary.isEmpty() && (ontSummaryContent == null || ontSummaryContent.toLowerCase().contains("failure:") || ontSummaryContent.toLowerCase().contains("error:"))){
            result.append("\nℹ️ Análise de rompimento não pôde ser realizada devido à ausência ou erro nos dados de status das ONTs.\n");
        }
        result.append("------------------------------------------------------------------------------\n");

        return result.toString();
    }
    // ------------------------------ Summary ------------------------------ //


    // ------------------------------ By-SN ------------------------------ //
    public String queryOntInfoBySn(String serialNumber) {
        executeCommandAndCaptureOutput(Arrays.asList("enable", "config"), shortCommandTimeout);

        List<String> commands = Collections.singletonList("display ont info by-sn " + serialNumber);
        String rawOutput = executeCommandAndCaptureOutput(commands, commandTimeout);
        return parseOntInfoBySnOutput(rawOutput, serialNumber);
    }

    private String parseOntInfoBySnOutput(String rawCommandOutput, String serialNumber) {
        StringBuilder result = new StringBuilder();
        result.append("🔎 INFORMAÇÕES DA ONT POR SN: ").append(serialNumber).append("\n");
        result.append("==================================================================\n");

        if (rawCommandOutput.toLowerCase().contains("ont does not exist") ||
                rawCommandOutput.toLowerCase().contains("parameter input error") ||
                rawCommandOutput.toLowerCase().contains("failure: sn error") ||
                rawCommandOutput.toLowerCase().contains("parameter error, the sn is invalid")) {
            result.append("❌ ONT não encontrada, erro no parâmetro (SN inválido?), ou falha ao consultar SN.\n");
            String cleanedErrorOutput = rawCommandOutput.lines()
                    .filter(line -> line.toLowerCase().contains("failure") || line.toLowerCase().contains("error") || line.toLowerCase().contains("ont does not exist"))
                    .map(String::trim)
                    .collect(Collectors.joining("; "));
            if (!cleanedErrorOutput.isEmpty()) {
                result.append("   Detalhe do erro: ").append(cleanedErrorOutput.substring(0, Math.min(cleanedErrorOutput.length(), 150))).append("\n");
            }
            result.append("==================================================================\n");
            return result.toString();
        }
        if (rawCommandOutput.trim().isEmpty()) {
            result.append("❌ Falha ao obter informações: output vazio (possível timeout ou problema de conexão não capturado).\n");
            result.append("==================================================================\n");
            return result.toString();
        }


        List<String> linesForParsing = new ArrayList<>();
        String[] allLines = rawCommandOutput.split("\n");
        boolean dataSectionStarted = false;
        int parseStartIndex = -1;

        String commandSignatureCore = "display ont info by-sn " + serialNumber;

        for (int i = 0; i < allLines.length; i++) {
            String currentLine = allLines[i];
            String trimmedCurrentLine = currentLine.trim();
            String lowerTrimmedCurrentLine = trimmedCurrentLine.toLowerCase();

            if (lowerTrimmedCurrentLine.contains(commandSignatureCore.toLowerCase())) {
                for (int j = i + 1; j < allLines.length; j++) {
                    String potentialDataLine = allLines[j].trim();
                    if (potentialDataLine.matches("^\\s*----.*----\\s*$")) {
                        if (j + 1 < allLines.length) {
                            parseStartIndex = j + 1;
                            dataSectionStarted = true;
                            break;
                        }
                    } else if (potentialDataLine.matches("^\\s*F/S/P\\s*:.*")) {
                        parseStartIndex = j;
                        dataSectionStarted = true;
                        break;
                    } else if (!potentialDataLine.isEmpty() && !potentialDataLine.matches("^\\s*[\\w.-]+(?:\\([\\w-./]*\\))?[>#]\\s*$") && !potentialDataLine.toLowerCase().startsWith("command:")) {
                        parseStartIndex = j;
                        dataSectionStarted = true;
                        break;
                    }
                }
                if (dataSectionStarted) break;
            }
        }

        // FALLBACK
        if (!dataSectionStarted) {
            for (int i = 0; i < allLines.length; i++) {
                if (allLines[i].trim().matches("^\\s*F/S/P\\s*:.*")) {
                    parseStartIndex = i;
                    dataSectionStarted = true;
                    break;
                }
            }
        }

        if (!dataSectionStarted) {
            result.append("⚠️ Não foi possível isolar a seção de dados para 'display ont info by-sn'.\n");
            result.append("   Isso pode ocorrer se a ONT estiver offline, o SN for incorreto, ou a saída da OLT for inesperada.\n");
            String warningLine = "";
            for (String l : allLines) {
                if (l.trim().toLowerCase().startsWith("warning:")) {
                    warningLine = l.trim();
                    break;
                }
            }
            if (!warningLine.isEmpty()) {
                result.append("\n   Aviso da OLT: ").append(warningLine).append("\n");
            } else {
                result.append("   Output bruto recebido (primeiras linhas para depuração):\n");
                for(int k=0; k < Math.min(allLines.length, 10); k++){
                    if(!allLines[k].trim().isEmpty()) result.append("     ").append(allLines[k].trim()).append("\n");
                }
            }
            result.append("==================================================================\n");
            return result.toString();
        }

        for (int i = parseStartIndex; i < allLines.length; i++) {
            linesForParsing.add(allLines[i]);
        }

        if (linesForParsing.isEmpty()) {
            result.append("⚠️ Nenhuma linha de dados encontrada para parsear após isolar a seção do comando.\n");
            result.append("==================================================================\n");
            return result.toString();
        }

        List<String> ontIpLines = new ArrayList<>();
        List<String> generalInfoLines = new ArrayList<>();
        List<String> tcontGemLines = new ArrayList<>();
        List<String> serviceProfileLines = new ArrayList<>();
        List<String> otherTablesAndNotes = new ArrayList<>();

        boolean inTcontGemBlock = false;
        boolean inServiceProfileRelatedBlock = false;

        Pattern keyValuePattern = Pattern.compile("^\\s*([^:]+?)\\s*:\\s*(.+)$");
        Pattern ontIpPattern = Pattern.compile("^\\s*(ONT IP \\d+ address/mask)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        String oltPromptPattern = "^\\s*[\\w.-]+(?:\\([\\w-./]*\\))?[>#]\\s*$";

        for (String line : linesForParsing) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty() ||
                    trimmedLine.matches(oltPromptPattern) ||
                    trimmedLine.toLowerCase().startsWith("display ont info by-sn") ||
                    trimmedLine.equals("{ <cr>||<K> }:") ||
                    trimmedLine.toLowerCase().startsWith("command:")) {
                continue;
            }


            Matcher ontIpMatcher = ontIpPattern.matcher(trimmedLine);
            if (ontIpMatcher.matches()) {
                ontIpLines.add(String.format("%-28s : %s", ontIpMatcher.group(1).trim(), ontIpMatcher.group(2).trim()));
                inTcontGemBlock = false; inServiceProfileRelatedBlock = false;
                continue;
            }

            if (trimmedLine.startsWith("<T-CONT") || trimmedLine.startsWith("<Gem Index")) {
                inTcontGemBlock = true;
                inServiceProfileRelatedBlock = false;
                tcontGemLines.add(line);
                continue;
            }
            if (inTcontGemBlock) {
                if (trimmedLine.startsWith("--------------------------------------------------------------------") ||
                        trimmedLine.startsWith("|Serv-Type:") ||
                        trimmedLine.matches("^\\s*Mapping VLAN.*") ||
                        trimmedLine.matches("^\\s*\\d+\\s+\\d+.*") ||
                        trimmedLine.matches("^\\s*\\|Upstream-priority-queue:.*")) {
                    tcontGemLines.add(line);
                } else {
                    inTcontGemBlock = false;
                }
                if(inTcontGemBlock) continue;
            }

            if (trimmedLine.toLowerCase().startsWith("service profile id") || trimmedLine.toLowerCase().startsWith("service profile name") ||
                    trimmedLine.matches("^Port-type\\s+Port-ID\\s+QinQmode.*") ||
                    trimmedLine.matches("^Port-type\\s+Port-ID\\s+DownstreamMode.*") ||
                    trimmedLine.matches("^Port-type\\s+Port-ID\\s+Dscp-mapping-table-index.*") ||
                    trimmedLine.matches("^Port\\s+Port\\s+Service-type.*") ||
                    trimmedLine.matches("^Port-type\\s+Port-ID\\s+IGMP-mode.*") ||
                    trimmedLine.matches("^Port-type\\s+Port-number.*")
            ) {
                inServiceProfileRelatedBlock = true;
                inTcontGemBlock = false;
                serviceProfileLines.add(line);
                continue;
            }
            if (inServiceProfileRelatedBlock && trimmedLine.matches("^(ETH|POTS|VDSL|IPHOST|CATV|MOCA)\\s+\\d+.*")) {
                serviceProfileLines.add(line);
                continue;
            }
            if (inServiceProfileRelatedBlock && !trimmedLine.matches("^\\s*----.*----\\s*$") && !keyValuePattern.matcher(trimmedLine).matches() && !trimmedLine.startsWith("Notes:")) {
            } else if (inServiceProfileRelatedBlock) {
                inServiceProfileRelatedBlock = false;
            }

            Matcher kvMatcher = keyValuePattern.matcher(trimmedLine);
            if (kvMatcher.matches()) {
                String key = kvMatcher.group(1).trim();
                String value = kvMatcher.group(2).trim();
                if (key.toLowerCase().startsWith("alarm policy profile id") || key.toLowerCase().startsWith("alarm policy profile name") ||
                        key.toLowerCase().startsWith("tr069 server profile id") || key.toLowerCase().startsWith("tr069 server profile name")) {
                    serviceProfileLines.add(String.format("%-30s : %s", key, value));
                } else {
                    generalInfoLines.add(String.format("%-30s : %s", key, value));
                }
            } else if (trimmedLine.startsWith("Notes:") ||
                    (!trimmedLine.matches("^\\s*----.*----\\s*$") && !trimmedLine.matches(oltPromptPattern))) {
                otherTablesAndNotes.add(line);
            }
        }

        boolean dataFound = !ontIpLines.isEmpty() || !generalInfoLines.isEmpty() || !tcontGemLines.isEmpty() || !serviceProfileLines.isEmpty() || !otherTablesAndNotes.isEmpty();

        if (!dataFound && linesForParsing.stream().noneMatch(l -> l.trim().toLowerCase().startsWith("warning:"))) {
            result.append("⚠️ Nenhuma informação estruturada da ONT foi encontrada após aplicar os filtros.\n");
            result.append("   Verifique se o SN está correto e a ONT está comunicando adequadamente.\n");
            result.append("   Conteúdo parseado (se houver algo além de prompts):\n");
            StringBuilder parsedPreview = new StringBuilder();
            int count = 0;
            for (String l : linesForParsing) {
                if (!l.trim().matches(oltPromptPattern) && !l.trim().isEmpty() && !l.toLowerCase().startsWith("display ont info by-sn")) {
                    parsedPreview.append("     ").append(l.trim()).append("\n");
                    count++;
                }
                if (count >= 10) break;
            }
            result.append(parsedPreview.toString().isEmpty() ? "     (Nenhum conteúdo relevante restante após filtros)\n" : parsedPreview.toString());
        } else {
            if (!ontIpLines.isEmpty()) {
                result.append("\n📡 INFORMAÇÕES DE IP DA ONT (DHCP ROTEADOR):\n");
                result.append("------------------------------------------------------------------\n");
                ontIpLines.forEach(ipLine -> result.append(ipLine).append("\n"));
                result.append("------------------------------------------------------------------\n");
            } else if (dataFound) {
                result.append("\n📡 Nenhuma informação de IP da ONT (DHCP ROTEADOR) encontrada.\n");
                result.append("------------------------------------------------------------------\n");
            }

            if (!generalInfoLines.isEmpty()) {
                result.append("\n📋 DETALHES GERAIS DA ONT:\n");
                result.append("------------------------------------------------------------------\n");
                generalInfoLines.forEach(infoLine -> result.append(infoLine).append("\n"));
                result.append("------------------------------------------------------------------\n");
            }

            if (!tcontGemLines.isEmpty()) {
                result.append("\n🧬 INFORMAÇÕES DE T-CONT / GEM PORT:\n");
                tcontGemLines.forEach(tcontLine -> result.append(tcontLine).append("\n"));
            }

            if (!serviceProfileLines.isEmpty()) {
                result.append("\n🛠️ PERFIL DE SERVIÇO, CONFIGS DE PORTA, ALARME & TR069:\n");
                result.append("------------------------------------------------------------------\n");
                boolean firstServiceBlockLine = true;
                for (String spLine : serviceProfileLines) {
                    if (!firstServiceBlockLine && spLine.trim().matches("^Port-type\\s+Port-ID.*") && !spLine.trim().toLowerCase().startsWith("service profile")) {
                        result.append("  ...\n");
                    }
                    result.append(spLine).append("\n");
                    firstServiceBlockLine = spLine.trim().toLowerCase().startsWith("service profile");
                }
                result.append("------------------------------------------------------------------\n");
            }

            if (!otherTablesAndNotes.isEmpty()) {
                result.append("\n📝 OUTRAS TABELAS E NOTAS:\n");
                result.append("------------------------------------------------------------------\n");
                for (String noteLine : otherTablesAndNotes) {
                    if (noteLine.trim().startsWith("Notes:")) result.append("  ---\n");
                    result.append(noteLine).append("\n");
                }
            }
        }
        result.append("==================================================================\n");
        return result.toString();
    }
    // ------------------------------ By-SN ------------------------------ //


    // ------------------------------ Quedas ------------------------------ //
    public String queryOntRegisterInfo(String frameSlot, String portOnCard, String ontId) {
        executeCommandAndCaptureOutput(Arrays.asList("enable", "config", "interface gpon " + frameSlot), shortCommandTimeout);

        List<String> commands = Collections.singletonList("display ont register-info " + portOnCard + " " + ontId);
        String rawOutput = executeCommandAndCaptureOutputForRegisterInfo(commands, commandTimeout * 4);
        return parseOntRegisterInfoOutput(rawOutput, frameSlot + "/" + portOnCard, ontId);
    }

    private String parseOntRegisterInfoOutput(String output, String gponPortContext, String ontId) {
        StringBuilder result = new StringBuilder("📊 RESUMO GERAL DAS QUEDAS: ").append(gponPortContext).append(" - ONT ID ").append(ontId).append(":\n\n");
        result.append("===========================================================================\n");
        String lowerOutput = output.toLowerCase();
        if (lowerOutput.contains("failure: record does not exist") ||
                lowerOutput.contains("no related information to display") ||
                lowerOutput.contains("ont does not exist")) {
            result.append("❌ Nenhum registro de queda encontrado para esta ONT ou a ONT não existe.\n");
            result.append("===========================================================================\n");
            return result.toString();
        }
        if (lowerOutput.contains("parameter error")) {
            result.append("❌ Erro de parâmetro. Verifique os IDs fornecidos (PON e ONT ID).\n");
            result.append("===========================================================================\n");
            return result.toString();
        }

        if (!output.contains("Index")) {
            result.append("ℹ COMANDO EXECUTADO - SEM DADOS DE REGISTRO RETORNADOS\n");
            result.append("A OLT processou o comando, mas não retornou nenhum registro de queda. Valide se os parâmetros estão corretos.\n\n");
            String portForCommand;
            if (gponPortContext.contains("/")) {
                String[] parts = gponPortContext.split("/");
                portForCommand = parts[parts.length - 1];
            } else {
                portForCommand = gponPortContext;
            }
            result.append("===========================================================================\n");
            return result.toString();
        }

        return parseOntRegisterInfoOutputAlternative(output, gponPortContext, ontId);
    }

    private String parseOntRegisterInfoOutputAlternative(String output, String gponPortContext, String ontId) {
        StringBuilder result = new StringBuilder();

        List<String> registrosFormatados = new ArrayList<>();
        Map<String, Integer> causasQueda = new HashMap<>();
        String lastSeenSN = "";
        String lastSeenType = "";
        int totalEventBlocksFound = 0;

        String[] lines = output.split("\\r?\\n");
        boolean foundAnyDataBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith("Index") && line.contains(":")) {
                foundAnyDataBlock = true;
                totalEventBlocksFound++;
                StringBuilder currentEntryBuilder = new StringBuilder();

                String indexVal = extractValue(line);
                currentEntryBuilder.append(String.format("%-15s: %s\n", "Index", indexVal));

                for (int j = i + 1; j < lines.length; j++) {
                    String dataLine = lines[j].trim();

                    if (dataLine.startsWith("Index") && dataLine.contains(":")) {
                        i = j - 1;
                        break;
                    }
                    if (dataLine.startsWith("Total") && dataLine.contains(":")){
                        i = j;
                        break;
                    }
                    if (dataLine.isEmpty() || dataLine.startsWith("---")) {
                        continue;
                    }

                    String key = extractKey(dataLine);
                    String value = extractValue(dataLine);

                    if (key != null && value != null) {
                        currentEntryBuilder.append(String.format("  %-12s : %s\n", key, value));
                        if ("SN".equalsIgnoreCase(key) && !value.isEmpty()) lastSeenSN = value;
                        if ("TYPE".equalsIgnoreCase(key) && !value.isEmpty()) lastSeenType = value;
                        if ("DownCause".equalsIgnoreCase(key) && !"-".equals(value) && !value.isEmpty()) {
                            causasQueda.put(value, causasQueda.getOrDefault(value, 0) + 1);
                        }
                    }
                }
                registrosFormatados.add(currentEntryBuilder.toString());
            }
        }

        if (foundAnyDataBlock) {
            result.append("📊 RESUMO GERAL DAS QUEDAS:\n");
            result.append("===========================================================================\n");
            result.append("• Total de quedas encontradas: ").append(totalEventBlocksFound).append("\n");
            result.append("• Modelo da ONT/ONU: ").append(lastSeenType.isEmpty() ? "N/A" : lastSeenType).append("\n");
            result.append("• SN: ").append(lastSeenSN.isEmpty() ? "N/A" : lastSeenSN).append("\n");

            if (!causasQueda.isEmpty()) {
                result.append("• Causas das quedas registradas:\n");
                causasQueda.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .forEach(entry -> result.append(String.format("  - %-30s: %d ocorrência(s)\n", entry.getKey(), entry.getValue())));
            } else {
                result.append("• Nenhuma 'DownCause' específica registrada (ONT pode estar online).\n");
            }
            result.append("===========================================================================\n\n");
            result.append("📋 DETALHAMENTO DAS QUEDAS:\n");
            result.append("---------------------------------------------------------------------------\n");

            registrosFormatados.forEach(regEntry -> {
                result.append(regEntry);
                result.append("---------------------------------------------------------------------------\n");
            });
        } else {
            result.append("❌ Não foi possível encontrar blocos de registro de quedas formatados.\n");
            result.append("   Verifique o output bruto da OLT ou os parâmetros da consulta.\n");
        }

        return result.toString();
    }

    private String extractKey(String line) {
        int colonIndex = line.indexOf(":");
        if (colonIndex != -1 && colonIndex > 0) {
            return line.substring(0, colonIndex).trim();
        }
        return null;
    }

    private String extractValue(String line) {
        int colonIndex = line.indexOf(":");
        if (colonIndex != -1 && colonIndex < line.length() - 1) {
            return line.substring(colonIndex + 1).trim();
        }
        return "";
    }

    private String executeCommandAndCaptureOutputForRegisterInfo(List<String> commandsToExecute, long timeoutMillis) {
        synchronized (outputLock) {
            commandOutput.setLength(0);
            capturingOutput = true;
        }

        final boolean[] responseComplete = {false};
        final int[] previousLineCount = {0};
        final long[] lastOutputTime = {System.currentTimeMillis()};

        final long stabilityCheckInterval = 500;
        final long initialGracePeriodMillis = 4000;
        final int requiredStableIterationsForPromptCheck = 5;
        final long longPauseAfterOutputTimeoutMillis = 60000;
        final long maxWaitForDataAfterCommandEcho = 45000;

        final String oltPromptPatternStr = "^\\s*[\\w.-]+(?:\\([\\w-./]*\\))?[>#]\\s*$";
        final Pattern oltPromptRegex = Pattern.compile(oltPromptPatternStr);

        Thread monitorThread = new Thread(() -> {
            int stableIterations = 0;
            long initialGracePeriodEndTime = System.currentTimeMillis() + initialGracePeriodMillis;
            long commandEchoTime = 0;
            boolean commandEchoDetected = false;

            try {
                while (!responseComplete[0] && capturingOutput) {
                    Thread.sleep(stabilityCheckInterval);

                    String currentOutputSnapshot;
                    int currentLines;
                    synchronized (outputLock) {
                        currentOutputSnapshot = commandOutput.toString();
                        currentLines = countLines(currentOutputSnapshot);
                    }

                    String lowerCaseSnapshot = currentOutputSnapshot.toLowerCase();

                    if (!commandEchoDetected && lowerCaseSnapshot.contains("display ont register-info")) {
                        commandEchoDetected = true;
                        commandEchoTime = System.currentTimeMillis();
                    }

                    if (currentOutputSnapshot.contains("---- More ( Press 'Q' to break ) ----") ||
                            currentOutputSnapshot.contains("More: <space>") ||
                            currentOutputSnapshot.contains("-- More --")) {
                        try {
                            if (outputStream != null && channel != null && channel.isConnected()) {
                                outputStream.write(' ');
                                outputStream.flush();
                                previousLineCount[0] = currentLines;
                                lastOutputTime[0] = System.currentTimeMillis();
                                stableIterations = 0;
                                synchronized (outputLock) {
                                    String temp = commandOutput.toString().replaceAll("---- More \\( Press 'Q' to break \\) ----\\s*\\r?\\n?", "")
                                            .replaceAll("More: <space>\\s*\\r?\\n?", "")
                                            .replaceAll("-- More --\\s*\\r?\\n?", "")
                                            .replaceAll("\\s{20,}", "");
                                    commandOutput.setLength(0);
                                    commandOutput.append(temp);
                                }
                                Thread.sleep(1000);
                                continue;
                            }
                        } catch (IOException | InterruptedException e) {
                            System.err.println("Error sending space for 'More' during register-info capture: " + e.getMessage());
                        }
                    }

                    boolean hasNewOutput = (currentLines > previousLineCount[0]);
                    if (hasNewOutput) {
                        previousLineCount[0] = currentLines;
                        lastOutputTime[0] = System.currentTimeMillis();
                        stableIterations = 0;
                    } else if (currentLines > 0 && System.currentTimeMillis() > initialGracePeriodEndTime) {
                        stableIterations++;
                    }

                    if (System.currentTimeMillis() < initialGracePeriodEndTime && currentLines == 0) {
                        continue;
                    }

                    if ((lowerCaseSnapshot.contains("failure:") &&
                            !lowerCaseSnapshot.contains("failure: the ont is offline") &&
                            !lowerCaseSnapshot.contains("failure: record does not exist")) ||
                            lowerCaseSnapshot.contains("parameter error:") ||
                            lowerCaseSnapshot.contains("unknown command") ||
                            lowerCaseSnapshot.contains("can not find the path") ||
                            lowerCaseSnapshot.contains("system busy") ||
                            (lowerCaseSnapshot.contains("error:") && !lowerCaseSnapshot.contains("parameter error:"))
                    ) {
                        Thread.sleep(1000);
                        synchronized(outputLock){ currentOutputSnapshot = commandOutput.toString(); lowerCaseSnapshot = currentOutputSnapshot.toLowerCase(); }
                        if ((lowerCaseSnapshot.contains("failure:") &&
                                !lowerCaseSnapshot.contains("failure: the ont is offline") &&
                                !lowerCaseSnapshot.contains("failure: record does not exist")) ||
                                lowerCaseSnapshot.contains("parameter error:") ||
                                lowerCaseSnapshot.contains("unknown command") ||
                                (lowerCaseSnapshot.contains("error:") && !lowerCaseSnapshot.contains("parameter error:"))
                        ) {
                            responseComplete[0] = true;
                            continue;
                        }
                    }

                    boolean hasDataKeywords = lowerCaseSnapshot.contains("index") || lowerCaseSnapshot.contains("total :");

                    if (commandEchoDetected) {
                        if (hasDataKeywords) {
                            if (stableIterations >= requiredStableIterationsForPromptCheck || (System.currentTimeMillis() - lastOutputTime[0]) > 15000) {
                                String[] lines = currentOutputSnapshot.split("\\r?\\n");
                                String lastNonEmpty = "";
                                if (lines.length > 0) { for(int k=lines.length-1; k>=0; k--) if(!lines[k].trim().isEmpty()){ lastNonEmpty=lines[k].trim(); break;}}
                                if (oltPromptRegex.matcher(lastNonEmpty).matches() || (System.currentTimeMillis() - lastOutputTime[0]) > 15000) {
                                    responseComplete[0] = true;
                                    continue;
                                }
                            }
                        } else {
                            if ((System.currentTimeMillis() - commandEchoTime) > maxWaitForDataAfterCommandEcho) {
                                String[] lines = currentOutputSnapshot.split("\\r?\\n");
                                String lastNonEmpty = "";
                                if (lines.length > 0) { for(int k=lines.length-1; k>=0; k--) if(!lines[k].trim().isEmpty()){ lastNonEmpty=lines[k].trim(); break;}}
                                if (oltPromptRegex.matcher(lastNonEmpty).matches() || stableIterations >= requiredStableIterationsForPromptCheck ) {
                                    responseComplete[0] = true;
                                    continue;
                                }
                            }
                        }
                    } else if (currentLines > 0 && stableIterations >= requiredStableIterationsForPromptCheck) {
                        String[] lines = currentOutputSnapshot.split("\\r?\\n");
                        String lastNonEmpty = "";
                        if (lines.length > 0) { for(int k=lines.length-1; k>=0; k--) if(!lines[k].trim().isEmpty()){ lastNonEmpty=lines[k].trim(); break;}}
                        if (oltPromptRegex.matcher(lastNonEmpty).matches()) {
                            responseComplete[0] = true;
                            continue;
                        }
                    }

                    if (currentLines > 0 && (System.currentTimeMillis() - lastOutputTime[0]) > longPauseAfterOutputTimeoutMillis) {
                        responseComplete[0] = true;
                        continue;
                    }
                    if (currentLines == 0 && (System.currentTimeMillis() - initialGracePeriodEndTime) > longPauseAfterOutputTimeoutMillis ) {
                        responseComplete[0] = true;
                        continue;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                responseComplete[0] = true;
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();

        String capturedStr = "Failure in capture or command produced no output.";
        try {
            for (String cmd : commandsToExecute) {
                sendCommand(cmd);
                Thread.sleep(cmd.toLowerCase().contains("display ont register-info") ? 4000 : 1500);
            }

            long startTime = System.currentTimeMillis();
            while (!responseComplete[0] && (System.currentTimeMillis() - startTime < timeoutMillis)) {
                Thread.sleep(200);
            }

            if (!responseComplete[0] && (System.currentTimeMillis() - startTime >= timeoutMillis)) {
                String partialOutputPreview = "";
                synchronized (outputLock) {
                    partialOutputPreview = commandOutput.substring(0, Math.min(150, commandOutput.length())).replace("\n", " ");
                }
                System.err.println("Command (register-info: " + String.join("; ", commandsToExecute) +
                        ") OVERALL TIMEOUT (" + timeoutMillis + "ms). Partial output: " + partialOutputPreview);
            }

            if (monitorThread.isAlive()) {
                monitorThread.interrupt();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            capturedStr = "Command execution interrupted: " + e.getMessage();
            System.err.println(capturedStr);
        } catch (Exception e) {
            capturedStr = "Error executing command: " + e.getMessage();
            System.err.println("Error in executeCommandAndCaptureOutputForRegisterInfo: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            capturingOutput = false;
            try {
                if (monitorThread.isAlive()) {
                    monitorThread.join(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while joining monitorThread for register-info.");
            }
        }

        synchronized (outputLock) {
            capturedStr = commandOutput.toString();
        }
        return cleanCapturedOutput(capturedStr);
    }

    // FALLBACKS
    public String queryOntRegisterInfoFixed(String frameSlot, String portOnCard, String ontId) {
        executeCommandAndCaptureOutput(Arrays.asList("enable", "config", "interface gpon " + frameSlot), shortCommandTimeout);

        List<String> commands = Collections.singletonList("display ont register-info " + portOnCard + " " + ontId);
        String rawOutput = executeCommandAndCaptureOutputForRegisterInfo(commands, commandTimeout * 5);
        return parseOntRegisterInfoOutput(rawOutput, frameSlot + "/" + portOnCard, ontId);
    }

    public String queryOntRegisterInfoWithRetry(String frameSlot, String portOnCard, String ontId) {
        int maxRetries = 2;
        long baseTimeout = this.commandTimeout;

        String modeSetupOutput = executeCommandAndCaptureOutput(Arrays.asList("enable", "config", "interface gpon " + frameSlot), shortCommandTimeout);
        if (modeSetupOutput.toLowerCase().contains("failure:") || modeSetupOutput.toLowerCase().contains("error:")) {
            return "❌ Falha ao configurar modo para consulta de registro: " + modeSetupOutput.lines().filter(l->l.toLowerCase().contains("failure:")||l.toLowerCase().contains("error:")).findFirst().orElse("Erro não especificado");
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            List<String> commands = Collections.singletonList("display ont register-info " + portOnCard + " " + ontId);

            long currentAttemptTimeout;
            if (attempt == 1) currentAttemptTimeout = Math.max(baseTimeout * 4, 240000L);
            else currentAttemptTimeout = Math.max(baseTimeout * 5, 300000L);


            String rawOutput = executeCommandAndCaptureOutputForRegisterInfo(commands, currentAttemptTimeout);
            String result = parseOntRegisterInfoOutput(rawOutput, frameSlot + "/" + portOnCard, ontId);

            if (result.contains("Nenhum registro de queda encontrado") ||
                    result.contains("COMANDO EXECUTADO - SEM DADOS DE REGISTRO RETORNADOS") ||
                    (!result.contains("Não foi possível encontrar blocos de registros de quedas formatados") &&
                            !result.contains("Falha ao obter informações: output vazio") &&
                            result.contains("RESUMO GERAL DOS EVENTOS REGISTRADOS"))
            ) {
                return result;
            }

            if (attempt < maxRetries) {
                System.err.println("Tentativa de registro " + attempt + " para " + frameSlot + "/" + portOnCard + " ONT " + ontId +
                        " falhou ou incompleta. Tentando novamente em 3 segundos... (Timeout usado: " + currentAttemptTimeout/1000 + "s)");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "❌ Consulta de registro interrompida durante retry.";
                }
            }
        }

        return "❌ Falha ao obter registro de quedas para " + frameSlot + "/" + portOnCard + " ONT " + ontId + " após " + maxRetries + " tentativas. Verifique a OLT e os parâmetros.";
    }
    // ------------------------------ Quedas ------------------------------ //


    // ------------------------------ Trafego ------------------------------ //
    public String queryOntTraffic(String frameSlot, String portOnCard, String ontId) {
        executeCommandAndCaptureOutput(Arrays.asList("enable", "config", "interface gpon " + frameSlot), shortCommandTimeout);

        String trafficCommand = "display ont traffic " + portOnCard + " " + ontId;
        List<String> commands = Collections.singletonList(trafficCommand);

        String rawOutput = executeCommandAndCaptureOutput(commands, shortCommandTimeout);
        return parseOntTrafficOutput(rawOutput, frameSlot + "/" + portOnCard, ontId);
    }

    private String parseOntTrafficOutput(String output, String gponPortContext, String ontId) {
        StringBuilder result = new StringBuilder("TRÁFEGO DA PON ").append(gponPortContext).append(" - ONT ID ").append(ontId).append(":\n");
        result.append("-------------------------------------\n");

        Pattern upTrafficPattern = Pattern.compile("Up traffic\\s*\\(kbps\\)\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern downTrafficPattern = Pattern.compile("Down traffic\\s*\\(kbps\\)\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

        Matcher upMatcher = upTrafficPattern.matcher(output);
        Matcher downMatcher = downTrafficPattern.matcher(output);

        boolean upFound = false;
        boolean downFound = false;

        if (upMatcher.find()) {
            upFound = true;
            int upKbps = Integer.parseInt(upMatcher.group(1));
            result.append(String.format("Tráfego Upload   : %d kbps (%.2f Mbps)\n", upKbps, upKbps / 1000.0));
        }

        if (downMatcher.find()) {
            downFound = true;
            int downKbps = Integer.parseInt(downMatcher.group(1));
            result.append(String.format("Tráfego Download : %d kbps (%.2f Mbps)\n", downKbps, downKbps / 1000.0));
        }


        if (upFound || downFound) {
            if (!upFound) result.append("Tráfego Upload   : N/A (ou 0 kbps)\n");
            if (!downFound) result.append("Tráfego Download : N/A (ou 0 kbps)\n");
        } else {
            if (output.toLowerCase().contains("ont does not exist") ||
                    output.toLowerCase().contains("parameter error") ||
                    output.toLowerCase().contains("ont not exist")) {
                result.append("ONT não encontrada ou erro no parâmetro.\n");
            } else {
                result.append("Dados de tráfego não encontrados ou formato não reconhecido.\n");

            }
        }

        result.append("-------------------------------------\n");
        return result.toString();
    }
    // ------------------------------ Trafego ------------------------------ //


    // ------------------------------ Serviços ------------------------------ //
    public String queryServicePortInfo(String fsp, String ontId) {
        executeCommandAndCaptureOutput(Arrays.asList("enable", "config"), shortCommandTimeout);

        List<String> commands = Collections.singletonList("display service-port port " + fsp + " ont " + ontId);
        String rawOutput = executeCommandAndCaptureOutput(commands, commandTimeout);
        return parseServicePortInfoOutput(rawOutput, fsp, ontId);
    }

    private String parseServicePortInfoOutput(String output, String fsp, String ontId) {
        output = output.replaceAll("OLT_[A-Z0-9_]+\\([^)]*\\)#.*", "").trim();

        StringBuilder result = new StringBuilder("SERVIÇOS CONFIGURADOS NA PON ").append(fsp).append(" - ONT ID ").append(ontId).append(":\n");
        result.append("---------------------------------------------------------------------------------------------------------------------------\n");

        if (output.toLowerCase().contains("no service virtual port that meets the condition exists") ||
                output.toLowerCase().contains("ont does not exist")) {
            result.append("Nenhum serviço (service-port) configurado para esta ONT ou ONT não existe.\n");
            result.append("---------------------------------------------------------------------------------------------------------------------------\n");
            return result.toString();
        }

        Pattern dataPattern = Pattern.compile(
                "^\\s*(\\d+)\\s+" +
                        "(\\d+)\\s+" +
                        "([a-zA-Z0-9_/-]+)\\s+" +
                        "([a-zA-Z0-9_/-]+)\\s+" +
                        "(\\d+\\s*/\\s*\\d+\\s*/\\s*\\d+)\\s+" +
                        "(\\S+)\\s+" +
                        "(\\S+)\\s+" +
                        "([a-zA-Z0-9_/-]+)\\s+" +
                        "(\\S+)\\s+" +
                        "(\\S+)\\s+" +
                        "(\\S+)\\s+" +
                        "(\\w+)\\s*$",
                Pattern.MULTILINE
        );

        String[] lines = output.split("\n");
        boolean headerFound = false;
        boolean dataParsed = false;
        Set<Integer> flowParaValues = new HashSet<>();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("---") ||
                    trimmedLine.toLowerCase().startsWith("command:") ||
                    trimmedLine.toLowerCase().startsWith("display service-port port")) continue;

            if (!headerFound && trimmedLine.toUpperCase().contains("INDEX") &&
                    trimmedLine.toUpperCase().contains("VLAN ID") &&
                    trimmedLine.toUpperCase().contains("STATE")) {
                headerFound = true;
                result.append(String.format("%-7s %-8s %-10s %-10s %-12s %-5s %-5s %-10s %-10s %-7s %-7s %-5s\n",
                        "INDEX", "VLAN ID", "VLAN ATTR", "PORT TYPE", "F/S/P", "VPI", "VCI", "FLOW TYPE", "FLOW PARA", "RX", "TX", "STATE"));
                result.append("---------------------------------------------------------------------------------------------------------------------------\n");
                continue;
            }

            if (headerFound || (!trimmedLine.startsWith("display service-port") && trimmedLine.matches("^\\s*\\d+.*"))) {
                Matcher matcher = dataPattern.matcher(trimmedLine);
                if (matcher.matches()) {
                    dataParsed = true;
                    String vlanIdStr = matcher.group(2);
                    String flowParaStr = matcher.group(9);

                    try {
                        int flowParaValue = Integer.parseInt(flowParaStr);
                        flowParaValues.add(flowParaValue);
                    } catch (NumberFormatException e) {
                    }

                    result.append(String.format("%-7s %-8s %-10s %-10s %-12s %-5s %-5s %-10s %-10s %-7s %-7s %-5s\n",
                            matcher.group(1), vlanIdStr, matcher.group(3), matcher.group(4),
                            matcher.group(5).replaceAll("\\s", ""),
                            matcher.group(6), matcher.group(7), matcher.group(8),
                            matcher.group(9), matcher.group(10), matcher.group(11), matcher.group(12)
                    ));
                }
            }
        }

        Matcher totalMatcher = Pattern.compile("Total\\s+:\\s*(\\d+)\\s*\\(Up/Down\\s+:\\s*(\\d+/\\d+)\\)").matcher(output);
        if (totalMatcher.find()) {
            if(!dataParsed && !headerFound) {
                result.append(String.format("%-7s %-8s %-10s %-10s %-12s %-5s %-5s %-10s %-10s %-7s %-7s %-5s\n",
                        "INDEX", "VLAN ID", "VLAN ATTR", "PORT TYPE", "F/S/P", "VPI", "VCI", "FLOW TYPE", "FLOW PARA", "RX", "TX", "STATE"));
                result.append("---------------------------------------------------------------------------------------------------------------------------\n");
            }
            dataParsed = true;
            result.append("---------------------------------------------------------------------------------------------------------------------------\n");
            result.append("Total: ").append(totalMatcher.group(1))
                    .append(" (Up/Down: ").append(totalMatcher.group(2)).append(")\n");
        }

        if (!dataParsed && !output.toLowerCase().contains("no service virtual port")) {
            result.append("Não foi possível parsear os dados de service-port ou formato não reconhecido.\n");
        }

        result.append("---------------------------------------------------------------------------------------------------------------------------\n");
        result.append("ANÁLISE DE SERVIÇOS:\n\n");

        if (flowParaValues.isEmpty() && dataParsed) {
            result.append("ℹ️ Nenhum valor FLOW PARA foi encontrado para análise.\n");
        } else if (flowParaValues.isEmpty()) {
        } else {
            boolean hasInternet = flowParaValues.contains(100);
            boolean hasRemoteAccess = flowParaValues.contains(101);
            boolean hasVoip = flowParaValues.contains(102);

            if (!hasInternet && !hasRemoteAccess && !hasVoip) {
                result.append("ℹ️ Valores FLOW PARA encontrados: ");
                result.append(flowParaValues.stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", ")));
                result.append("\n");
                result.append("⚠️ Padrão de serviços não reconhecido para análise automática.\n");
            } else {
                if (hasRemoteAccess) {
                    result.append("✅ Tipo de Equipamento: ONT (Possui Acesso Remoto)\n");
                    result.append("   - Exemplos: X6-10, X6, V5, HG8, HS5, A5, etc. - Validar no IXC.\n");
                } else {
                    result.append("✅ Tipo de Equipamento: ONU ou Bridge (Sem Acesso Remoto)\n");
                    result.append("   - Exemplos: FRKW, FIOG, ECDR, etc. - Validar no IXC.\n");
                }

                if (hasInternet) {
                    result.append("✅ Serviço de Internet: Ativo.\n");
                } else {
                    result.append("❌ Serviço de Internet: Não configurado.\n");
                }

                if (hasVoip) {
                    result.append("✅ Serviço de Telefonia (VoIP): Ativo.\n");
                } else {
                    result.append("❌ Serviço de Telefonia (VoIP): Não configurado/Não existente.\n");
                }
            }
        }

        int noteIndex = output.indexOf("Note :");
        if (noteIndex != -1) {
            if(dataParsed) result.append("\n");
            String noteSection = output.substring(noteIndex);
            noteSection = noteSection.replaceAll("OLT_[A-Z0-9_]+\\([^)]*\\)#.*", "").trim();
            result.append(noteSection);
        }

        result.append("\n---------------------------------------------------------------------------------------------------------------------------\n");
        return result.toString();
    }
    // ------------------------------ Serviços ------------------------------ //
}