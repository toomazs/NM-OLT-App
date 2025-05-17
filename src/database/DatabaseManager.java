package database;

import models.Ticket;
import models.Usuario;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

public class DatabaseManager {
    private static final String URL = SecretsDB.DB_URL;
    private static final String USER = SecretsDB.DB_USER;
    private static final String PASSWORD = SecretsDB.DB_PASSWORD;
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }


    // ---------------------- LOGIN E ALTERAR SENHA ---------------------- //
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

    public static boolean verifyCurrentPassword(String username, String currentPassword) {
        String sql = "SELECT senha FROM usuarios WHERE usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedPassword = rs.getString("senha");
                return storedPassword.equals(currentPassword);
            }
            return false;
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

    public static LoginResultStatus attemptLogin(String username, String password) {
        String checkUserSql = "SELECT 1 FROM usuarios WHERE usuario = ? AND senha = ?";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {

            checkStmt.setString(1, username);
            checkStmt.setString(2, password);
            ResultSet rsCheck = checkStmt.executeQuery();

            if (rsCheck.next()) {
                return LoginResultStatus.SUCCESS;
            } else {
                return LoginResultStatus.INVALID_CREDENTIALS;
            }

        } catch (SQLException e) {
            System.err.println("Database error during login attempt for user " + username + ": " + e.getMessage());
            e.printStackTrace();
            return LoginResultStatus.DATABASE_ERROR;
        }
    }

    public static Optional<Usuario> getUsuarioByUsername(String username) {
        String sql = "SELECT nome, usuario, cargo FROM usuarios WHERE usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new Usuario(
                        rs.getString("nome"),
                        rs.getString("usuario"),
                        rs.getString("cargo")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user by username: " + username);
            e.printStackTrace();
        }
        return Optional.empty();
    }
    // ---------------------- LOGIN E ALTERAR SENHA ---------------------- //


    // ---------------------- TICKETS ---------------------- //
    public static void criarTicket(String nome, String cargo, String descricao, String previsao) {
        String sql = "INSERT INTO tickets (criado_por, cargo, olt_nome, descricao, previsao, status) VALUES (?, ?, ?, ?, ?, 'Pendente')";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nome);
            stmt.setString(2, cargo);
            stmt.setString(3, "");
            stmt.setString(4, descricao);
            stmt.setString(5, previsao);
            stmt.executeUpdate();
            System.out.println("Ticket criado com sucesso!");
        } catch (SQLException e) {
            System.err.println("Erro ao criar ticket: " + e.getMessage());
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
                        rs.getString("criado_por"),
                        rs.getString("cargo"),
                        rs.getString("descricao"),
                        rs.getString("previsao"),
                        rs.getTimestamp("data_hora").toString(),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar tickets: " + e.getMessage());
            e.printStackTrace();
        }
        return tickets;
    }

    public static void excluirTicket(Ticket ticket) {
        String sql = "DELETE FROM tickets WHERE criado_por = ? AND descricao = ? AND data_hora = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ticket.getCriadoPor());
            stmt.setString(2, ticket.getDescricao());

            try {
                Timestamp dataHora = Timestamp.valueOf(ticket.getDataHora());
                stmt.setTimestamp(3, dataHora);
            } catch (IllegalArgumentException e) {
                System.err.println("Formato de data/hora inválido: " + ticket.getDataHora());
                return;
            }

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Ticket excluído com sucesso. (" + rowsAffected + " linhas afetadas)");
            } else {
                System.out.println("Nenhum ticket encontrado para exclusão.");
            }

        } catch (SQLException e) {
            System.err.println("Erro ao excluir ticket: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // ---------------------- TICKETS ---------------------- //


    // ---------------------- LOGS USUARIOS ---------------------- //
    public static void logUsuario(String usuario, String acao) {
        String sql = "INSERT INTO logs_usuario (usuario, acao) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario);
            stmt.setString(2, acao);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    // ---------------------- LOGS USUARIOS ---------------------- //

}