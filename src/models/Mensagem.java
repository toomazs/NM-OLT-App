package models;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Mensagem {
    private int id;
    private String remetente;
    private String destinatario;
    private String conteudo;
    private String dataHoraString;
    private Timestamp timestampObject;
    private boolean lida;

    public Mensagem(int id, String remetente, String destinatario, String conteudo, Timestamp timestamp, boolean lida) {
        this.id = id;
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.conteudo = conteudo;
        this.timestampObject = timestamp;
        this.dataHoraString = (timestamp != null) ? timestamp.toString() : null;
        this.lida = lida;
    }

    public Mensagem(String remetente, String destinatario, String conteudo, String dataHoraStr) {
        this(-1, remetente, destinatario, conteudo, null, false);
        this.dataHoraString = dataHoraStr;
        try {
            if (dataHoraStr != null) {
                String correctedTimestampStr = dataHoraStr;
                if (correctedTimestampStr.length() == 19) {
                    correctedTimestampStr += ".0";
                }
                this.timestampObject = Timestamp.valueOf(correctedTimestampStr);
            } else {
                this.timestampObject = null;
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Could not parse timestamp string in Mensagem constructor: " + dataHoraStr);
            this.timestampObject = null;
        }
    }


    public int getId() { return id; }
    public String getRemetente() { return remetente; }
    public String getDestinatario() { return destinatario; }
    public String getConteudo() { return conteudo; }

    @Deprecated
    public String getDataHora() { return dataHoraString; }

    public Timestamp getTimestampObject() { return timestampObject; }
    public boolean isLida() { return lida; }


    public String getFormattedTimestamp(String pattern, ZoneId zoneId) {
        if (this.timestampObject == null) {
            System.err.println("getFormattedTimestamp called with null timestampObject for message ID: " + this.id);
            return "--:--";
        }
        try {
            LocalDateTime utcDateTime = this.timestampObject.toLocalDateTime();
            ZonedDateTime utcZoned = utcDateTime.atZone(ZoneId.of("UTC-3"));
            ZonedDateTime localZoned = utcZoned.withZoneSameInstant(zoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return formatter.format(localZoned);

        } catch (Exception e) {
            System.err.println("Error formatting timestamp for message ID " + this.id + ": " + e.getMessage());
            e.printStackTrace();
            return "??:??";
        }
    }

    public void setId(int id) { this.id = id; }
    public void setRemetente(String remetente) { this.remetente = remetente; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    @Deprecated
    public void setDataHora(String dataHora) {
        this.dataHoraString = dataHora;
        try {
            if (dataHora != null) {
                String correctedTimestampStr = dataHora;
                if (correctedTimestampStr.length() == 19) {
                    correctedTimestampStr += ".0";
                }
                this.timestampObject = Timestamp.valueOf(correctedTimestampStr);
            } else {
                this.timestampObject = null;
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Could not parse timestamp string in setDataHora: " + dataHora);
        }
    }
    public void setTimestampObject(Timestamp timestamp) {
        this.timestampObject = timestamp;
        this.dataHoraString = (timestamp != null) ? timestamp.toString() : null;
    }
    public void setLida(boolean lida) { this.lida = lida; }
}