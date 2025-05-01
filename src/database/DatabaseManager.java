package database;

import models.Ticket;
import models.Usuario;
import models.Mensagem;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

    // Hash usando Sha-256
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

        String sql = "SELECT nome, usuario, cargo, status, foto_perfil, aba_atual FROM usuarios WHERE usuario = ? AND senha = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String nome = rs.getString("nome");
                String usuario = rs.getString("usuario");
                String cargo = rs.getString("cargo");
                byte[] fotoPerfil = rs.getBytes("foto_perfil");
                String abaAtual = rs.getString("aba_atual");

                if (atualizarStatus(usuario, "online")) {

                    return new Usuario(nome, usuario, cargo, "online", fotoPerfil, abaAtual);
                } else {
                    System.err.println("Failed to update status to online for user: " + usuario);
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


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


    public static void excluirTicket(Ticket ticket) {
        String sql = "DELETE FROM tickets WHERE criado_por = ? AND descricao = ? AND data_hora = ?";

        try (
                Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
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

    public static boolean atualizarStatus(String usuario, String status) {
        String sql = "UPDATE usuarios SET status = ?, ultima_atividade = ? WHERE usuario = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.setString(3, usuario);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error updating status for user " + usuario + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    public static List<Usuario> getTodosUsuarios() {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = """
                SELECT nome, usuario, cargo, status, foto_perfil, aba_atual FROM usuarios ORDER BY
                    CASE
                        WHEN cargo = 'Supervisor' THEN 1
                        WHEN cargo = 'Analista' THEN 2
                        WHEN cargo = 'Assistente' THEN 3
                        WHEN cargo = 'Estagiário' THEN 4
                        ELSE 5
                    END, nome ASC
                """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                usuarios.add(new Usuario(
                        rs.getString("nome"),
                        rs.getString("usuario"),
                        rs.getString("cargo"),
                        rs.getString("status"),
                        rs.getBytes("foto_perfil"),
                        rs.getString("aba_atual")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return usuarios;
    }

    public static boolean enviarMensagem(String remetente, String destinatario, String conteudo) {
        String sql = "INSERT INTO mensagens (remetente, destinatario, conteudo, data_hora, lida) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, remetente);
            stmt.setString(2, destinatario);
            stmt.setString(3, conteudo);
            stmt.setTimestamp(4, Timestamp.from(Instant.now()));
            stmt.setBoolean(5, false);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error sending message from " + remetente + " to " + destinatario + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static List<Mensagem> getMensagens(String destinatario) {
        List<Mensagem> mensagens = new ArrayList<>();
        String sql = "SELECT * FROM mensagens WHERE destinatario IS NULL OR destinatario = ? ORDER BY data_hora ASC";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, destinatario);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                mensagens.add(new Mensagem(
                        rs.getString("remetente"),
                        rs.getString("destinatario"),
                        rs.getString("conteudo"),
                        rs.getTimestamp("data_hora").toString()
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return mensagens;
    }

    public static boolean atualizarUltimaAtividade(String usuario, String abaAtual) {
        String sql = "UPDATE usuarios SET ultima_atividade = ?, aba_atual = ? WHERE usuario = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, abaAtual);
            stmt.setString(3, usuario);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                System.err.println("Attempted to update activity for non-existent or unchanged user: " + usuario);
            }
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("SQLException during activity update for user " + usuario + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error during activity update for user " + usuario + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void atualizarFotoPerfil(String usuario, byte[] imagemBytes) {
        String sql = "UPDATE usuarios SET foto_perfil = ? WHERE usuario = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBytes(1, imagemBytes);
            stmt.setString(2, usuario);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Mensagem> getMensagensPrivadas(String usuario1, String usuario2) {
        List<Mensagem> mensagens = new ArrayList<>();
        String sql = """
                    SELECT id, remetente, destinatario, conteudo, data_hora, lida
                    FROM mensagens
                    WHERE (remetente = ? AND destinatario = ?) OR
                          (remetente = ? AND destinatario = ?)
                    ORDER BY data_hora ASC
                """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario1);
            stmt.setString(2, usuario2);
            stmt.setString(3, usuario2);
            stmt.setString(4, usuario1);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                mensagens.add(new Mensagem(
                        rs.getInt("id"),
                        rs.getString("remetente"),
                        rs.getString("destinatario"),
                        rs.getString("conteudo"),
                        rs.getTimestamp("data_hora"),
                        rs.getBoolean("lida")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching private messages between " + usuario1 + " and " + usuario2 + ": " + e.getMessage());
            e.printStackTrace();
        }
        return mensagens;
    }


    public static class ConversationInfo {
        private final Usuario outroUsuario;
        private final Mensagem ultimaMensagem;
        private final int mensagensNaoLidas;

        public ConversationInfo(Usuario outroUsuario, Mensagem ultimaMensagem, int mensagensNaoLidas) {
            this.outroUsuario = outroUsuario;
            this.ultimaMensagem = ultimaMensagem;
            this.mensagensNaoLidas = mensagensNaoLidas;
        }

        public Usuario getOutroUsuario() {
            return outroUsuario;
        }

        public Mensagem getUltimaMensagem() {
            return ultimaMensagem;
        }

        public int getMensagensNaoLidas() {
            return mensagensNaoLidas;
        }
    }

    public static List<ConversationInfo> getConversas(String usuarioAtual) {
        List<ConversationInfo> conversas = new ArrayList<>();
        List<String> partners = new ArrayList<>();

        String findPartnersSql = """
        SELECT DISTINCT
            CASE WHEN remetente = ? THEN destinatario ELSE remetente END as partner
        FROM mensagens
        WHERE (remetente = ? OR destinatario = ?) AND destinatario IS NOT NULL AND remetente IS NOT NULL
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmtPartners = conn.prepareStatement(findPartnersSql)) {

            stmtPartners.setString(1, usuarioAtual);
            stmtPartners.setString(2, usuarioAtual);
            stmtPartners.setString(3, usuarioAtual);

            ResultSet rsPartners = stmtPartners.executeQuery();
            while (rsPartners.next()) {
                String partner = rsPartners.getString("partner");
                if (partner != null && !partner.trim().isEmpty() && !partner.equals(usuarioAtual)) {
                    partners.add(partner);
                }
            }

            for (String partnerUsername : partners) {
                Optional<Usuario> partnerUserOpt = getUsuarioByUsername(partnerUsername);

                if (partnerUserOpt.isPresent()) {
                    Usuario partnerUser = partnerUserOpt.get();
                    Optional<Mensagem> lastMsgOpt = getUltimaMensagem(usuarioAtual, partnerUsername);
                    int unreadCount = contarMensagensNaoLidas(usuarioAtual, partnerUsername);

                    lastMsgOpt.ifPresent(mensagem ->
                            conversas.add(new ConversationInfo(partnerUser, mensagem, unreadCount))
                    );

                } else {
                    System.err.println("Could not find user details for conversation partner: " + partnerUsername);
                }
            }

            conversas.sort((c1, c2) -> {
                Timestamp ts1 = (c1.getUltimaMensagem() != null) ? c1.getUltimaMensagem().getTimestampObject() : null;
                Timestamp ts2 = (c2.getUltimaMensagem() != null) ? c2.getUltimaMensagem().getTimestampObject() : null;

                if (ts1 == null && ts2 == null) return 0;
                if (ts1 == null) return 1;
                if (ts2 == null) return -1;
                return ts2.compareTo(ts1);
            });

        } catch (SQLException e) {
            System.err.println("Erro ao recuperar conversas para " + usuarioAtual + ": " + e.getMessage());
            e.printStackTrace();
        }

        return conversas;
    }

    private static Optional<Mensagem> getUltimaMensagem(String usuario1, String usuario2) {
        Mensagem lastMessage = null;
        String sql = """
                    SELECT id, remetente, destinatario, conteudo, data_hora, lida
                    FROM mensagens
                    WHERE (remetente = ? AND destinatario = ?) OR (remetente = ? AND destinatario = ?)
                    ORDER BY data_hora DESC LIMIT 1
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario1);
            stmt.setString(2, usuario2);
            stmt.setString(3, usuario2);
            stmt.setString(4, usuario1);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                lastMessage = new Mensagem(
                        rs.getInt("id"),
                        rs.getString("remetente"),
                        rs.getString("destinatario"),
                        rs.getString("conteudo"),
                        rs.getTimestamp("data_hora"),
                        rs.getBoolean("lida")
                );
            }
        } catch (SQLException e) {
            System.err.println("Erro ao obter última mensagem entre " + usuario1 + " e " + usuario2 + ": " + e.getMessage());
        }
        return Optional.ofNullable(lastMessage);
    }

    private static int contarMensagensNaoLidas(String destinatario, String remetente) {
        int count = 0;
        String sql = "SELECT COUNT(*) as total FROM mensagens " +
                "WHERE remetente = ? AND destinatario = ? AND lida = false";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, remetente);
            stmt.setString(2, destinatario);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                count = rs.getInt("total");
            }
        } catch (SQLException e) {
            System.err.println("Erro ao contar mensagens não lidas de " + remetente + " para " + destinatario + ": " + e.getMessage());
        }
        return count;
    }

    public static boolean marcarMensagensComoLidas(String destinatario, String remetente) {
        String sql = "UPDATE mensagens SET lida = true " +
                "WHERE remetente = ? AND destinatario = ? AND lida = false";
        int rowsAffected = 0;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, remetente);
            stmt.setString(2, destinatario);
            rowsAffected = stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao marcar mensagens como lidas de " + remetente + " para " + destinatario + ": " + e.getMessage());
        }
        return rowsAffected > 0;
    }

    public static Optional<Usuario> getUsuarioByUsername(String username) {
        String sql = "SELECT nome, usuario, cargo, status, foto_perfil, aba_atual FROM usuarios WHERE usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new Usuario(
                        rs.getString("nome"),
                        rs.getString("usuario"),
                        rs.getString("cargo"),
                        rs.getString("status"),
                        rs.getBytes("foto_perfil"),
                        rs.getString("aba_atual")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user by username: " + username);
            e.printStackTrace();
        }
        return Optional.empty();
    }
}