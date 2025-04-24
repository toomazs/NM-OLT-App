package database;

import models.Ticket;
import models.Usuario;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.sql.Timestamp;


public class DatabaseManager {
    private static final String URL = SecretsDB.DB_URL;
    private static final String USER = SecretsDB.DB_USER;
    private static final String PASSWORD = SecretsDB.DB_PASSWORD;

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // implementacao usando hash
    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return password;
        }
    }

    public static boolean validateUser(String username, String password) {
        String sql = "SELECT * FROM usuarios WHERE usuario = ? AND senha = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getUserRole(String username) {
        String sql = "SELECT cargo FROM usuarios WHERE usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("cargo");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static boolean changePassword(String username, String newPassword) {
        if (!userExists(username)) {
            return false;
        }

        String sql = "UPDATE usuarios SET senha = ? WHERE usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean userExists(String username) {
        String sql = "SELECT 1 FROM usuarios WHERE usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Usuario login(String username, String password) {
        String sql = "SELECT nome, usuario, cargo FROM usuarios WHERE usuario = ? AND senha = ?";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String nome = rs.getString("nome");
                String usuario = rs.getString("usuario");
                String cargo = rs.getString("cargo");

                return new Usuario(nome, usuario, cargo);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void criarTicket(String nome, String cargo, String oltNome, String descricao, String previsao) {
        String sql = "INSERT INTO tickets (criado_por, cargo, olt_nome, descricao, previsao) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nome);
            stmt.setString(2, cargo);
            stmt.setString(3, oltNome);
            stmt.setString(4, descricao);
            stmt.setString(5, previsao);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Ticket> getAllTickets() {
        List<Ticket> tickets = new ArrayList<>();
        String sql = "SELECT * FROM tickets ORDER BY data_hora DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tickets.add(new Ticket(
                        rs.getString("olt_nome"),
                        rs.getString("criado_por"),
                        rs.getString("cargo"),
                        rs.getString("descricao"),
                        rs.getString("previsao"),
                        rs.getTimestamp("data_hora").toString(),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tickets;
    }

    public static void salvarRompimento(String olt, String pon, int qtd, String status, String local, LocalDateTime hora) {
        String sql = "INSERT INTO rompimentos (olt_nome, pon, quantidade, status, localizacao, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, olt);
            stmt.setString(2, pon);
            stmt.setInt(3, qtd);
            stmt.setString(4, status);
            stmt.setString(5, local);
            stmt.setTimestamp(6, Timestamp.valueOf(hora));
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void logUsuario(String usuario, String acao, String olt) {
        String sql = "INSERT INTO logs_usuario (usuario, acao, olt_nome) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario);
            stmt.setString(2, acao);
            stmt.setString(3, olt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}