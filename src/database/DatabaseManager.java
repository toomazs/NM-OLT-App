package database;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import models.Usuario;


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
                String usuario = rs.getString("usuario"); // <-- adicionado aqui
                String cargo = rs.getString("cargo");

                return new Usuario(nome, usuario, cargo); // <-- agora usa o valor certo
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

}