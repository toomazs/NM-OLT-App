import com.jcraft.jsch.*;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

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
    private ObservableList<Main.RompimentoData> breakageList = FXCollections.observableArrayList();
    private Map<String, Map<String, List<OntFailureInfo>>> oltBreakageData = new HashMap<>();
    private String currentOltName;

    public static class OntFailureInfo {
        private final String ontId;
        private final String reason;
        private final LocalDateTime timestamp;

        public OntFailureInfo(String ontId, String reason) {
            this.ontId = ontId;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }

        public String getOntId() { return ontId; }
        public String getReason() { return reason; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

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

                    if (outputText.contains("---- More ( Press 'Q' to break ) ----")) {
                        outputText = outputText.replaceAll("---- More \\( Press 'Q' to break \\) ----\\[37D \\[37D", "");
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (outputText.contains("More: <space>") || outputText.contains("-- More --")) {
                        try {
                            outputStream.write(' ');
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (outputText.contains("{ <cr>||<K> }:")) {
                        try {
                            outputStream.write('\r');
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    outputText = outputText.replaceAll("\\[\\d+D", "");

                    if (capturingOutput) {
                        commandOutput.append(outputText);
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
        StringBuilder result = new StringBuilder();

        Pattern pattern = Pattern.compile("(\\d+)\\s+(-\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(-\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)");
        Matcher matcher = pattern.matcher(output);

        int totalOnts = 0;
        int weakSignal = 0;
        int noSignal = 0;
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
            double tx = Double.parseDouble(txPower);

            totalRx += rx;
            totalTx += tx;

            String status = "";

            if (rx < -29.0) {
                status = " (⚠ CRÍTICO)";
                noSignal++;
            } else if (rx <= -27.0 && rx >= -29.0) {
                status = " (ℹ VERIFICAR MÉDIA)";
                weakSignal++;
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
            result.append("ONTs com sinal fraco: ").append(weakSignal).append("\n");
            result.append("ONTs com sinal crítico: ").append(noSignal).append("\n");

            double avgRx = totalRx / totalOnts;
            double avgTx = totalTx / totalOnts;

            result.append(String.format("\nMédia Sinal RX: %.2f dBm", avgRx));
            result.append(String.format("\nMédia Sinal TX: %.2f dBm\n", avgTx));
        } else {
            result.append("Não foram encontrados dados de sinal óptico.\n");
            result.append("Verifique se o comando foi executado corretamente e se há ONTs configuradas nesta porta.\n");
        }

        return result.toString();
    }

    public void scanForBreakages(String oltName) {
        this.currentOltName = oltName;
        try {
            for (int frame = 0; frame <= 0; frame++) {
                for (int slot = 1; slot <= 20; slot++) {
                    for (int port = 0; port <= 20; port++) {
                        String ponId = frame + "/" + slot + "/" + port;
                        commandOutput.setLength(0);
                        capturingOutput = true;

                        sendCommand("enable");
                        Thread.sleep(500);
                        sendCommand("config");
                        Thread.sleep(500);
                        sendCommand("display ont info summary " + ponId);
                        Thread.sleep(3000);

                        capturingOutput = false;
                        parseBreakageOutput(commandOutput.toString(), ponId);
                        Thread.sleep(1000);
                    }
                }
            }

            analyzeBreakages();

        } catch (Exception e) {
            Platform.runLater(() ->
                    terminalArea.appendText("\n❌ Erro ao escanear rompimentos: " + e.getMessage() + "\n")
            );
        }
    }

    private void parseBreakageOutput(String output, String pon) {
        if (!oltBreakageData.containsKey(currentOltName)) {
            oltBreakageData.put(currentOltName, new HashMap<>());
        }

        Map<String, List<OntFailureInfo>> oltData = oltBreakageData.get(currentOltName);
        if (!oltData.containsKey(pon)) {
            oltData.put(pon, new ArrayList<>());
        }

        List<OntFailureInfo> ponFailures = oltData.get(pon);

        Pattern pattern = Pattern.compile("(\\d+)\\s+(online|offline)(?:.*?(LOSi|LOBi|LOFi|LOKi|LOS))?");
        Matcher matcher = pattern.matcher(output);

        while (matcher.find()) {
            String ontId = matcher.group(1);
            String status = matcher.group(2);

            if ("offline".equalsIgnoreCase(status) && matcher.groupCount() >= 3) {
                String reason = matcher.group(3);
                if (reason != null && (reason.equals("LOSi") || reason.equals("LOBi") ||
                        reason.equals("LOFi") || reason.equals("LOKi") || reason.equals("LOS"))) {
                    ponFailures.add(new OntFailureInfo(ontId, reason));
                }
            }
        }
    }

    private void analyzeBreakages() {
        breakageList.clear();

        for (String oltName : oltBreakageData.keySet()) {
            Map<String, List<OntFailureInfo>> oltData = oltBreakageData.get(oltName);

            for (String pon : oltData.keySet()) {
                List<OntFailureInfo> failures = oltData.get(pon);

                if (!failures.isEmpty()) {
                    Map<LocalDateTime, List<OntFailureInfo>> timeGroups = new HashMap<>();

                    for (OntFailureInfo failure : failures) {
                        boolean addedToGroup = false;

                        for (LocalDateTime groupTime : timeGroups.keySet()) {
                            if (Math.abs(ChronoUnit.MINUTES.between(groupTime, failure.getTimestamp())) <= 5) {
                                timeGroups.get(groupTime).add(failure);
                                addedToGroup = true;
                                break;
                            }
                        }

                        if (!addedToGroup) {
                            List<OntFailureInfo> newGroup = new ArrayList<>();
                            newGroup.add(failure);
                            timeGroups.put(failure.getTimestamp(), newGroup);
                        }
                    }

                    for (Map.Entry<LocalDateTime, List<OntFailureInfo>> entry : timeGroups.entrySet()) {
                        LocalDateTime timestamp = entry.getKey();
                        List<OntFailureInfo> group = entry.getValue();

                        String status = group.size() >= 6 ? "Crítico" : "Alerta";
                        String formattedTime = timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                        String location = derivePonLocation(oltName, pon);

                        Main.RompimentoData rompimento = new Main.RompimentoData(
                                oltName,
                                pon,
                                String.valueOf(group.size()),
                                location,
                                status,
                                formattedTime
                        );

                        breakageList.add(rompimento);
                    }
                }
            }
        }
    }

    private String derivePonLocation(String oltName, String pon) {
        return oltName.replace("OLT_", "") + " - P" + pon.replace("/", "");
    }

    public ObservableList<Main.RompimentoData> getBreakageList() {
        return breakageList;
    }

    public int[] getBreakageCounters() {
        int totalOnts = 0;
        int alertCount = 0;
        int criticalCount = 0;

        for (Main.RompimentoData data : breakageList) {
            int impacted = Integer.parseInt(data.getImpacted());
            totalOnts += impacted;

            if ("Crítico".equals(data.getStatus())) {
                criticalCount++;
            } else if ("Alerta".equals(data.getStatus())) {
                alertCount++;
            }
        }

        return new int[] { totalOnts, alertCount, criticalCount };
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