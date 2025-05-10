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
import java.util.Collections;
import java.util.Optional;


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

    public static LoginResultStatus attemptLogin(String username, String password) {
        String checkStatusSql = "SELECT status FROM usuarios WHERE usuario = ? AND senha = ?";
        String updateStatusSql = "UPDATE usuarios SET status = 'online', ultima_atividade = ?, aba_atual = 'OLTs' WHERE usuario = ?";

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rsCheck = null;

        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction

            // 1. Check credentials and current status
            checkStmt = conn.prepareStatement(checkStatusSql);
            checkStmt.setString(1, username);
            checkStmt.setString(2, password);
            rsCheck = checkStmt.executeQuery();

            if (rsCheck.next()) {
                String currentStatus = rsCheck.getString("status");

                // 2. Check if user is already considered online ('online' or 'ausente')
                if ("online".equalsIgnoreCase(currentStatus) || "ausente".equalsIgnoreCase(currentStatus)) {
                    System.out.println("Login attempt blocked: User '" + username + "' is already logged in (status: " + currentStatus + ").");
                    conn.rollback(); // Rollback transaction
                    return LoginResultStatus.ALREADY_LOGGED_IN; // Specific status for this case
                }

                // 3. User exists, password correct, and not online - Update status
                updateStmt = conn.prepareStatement(updateStatusSql);
                updateStmt.setTimestamp(1, Timestamp.from(Instant.now()));
                updateStmt.setString(2, username);
                int updatedRows = updateStmt.executeUpdate();

                if (updatedRows > 0) {
                    conn.commit(); // Commit transaction
                    return LoginResultStatus.SUCCESS; // Login successful
                } else {
                    System.err.println("Login error: Failed to update status to online for user: " + username);
                    conn.rollback();
                    return LoginResultStatus.DATABASE_ERROR; // Indicate a DB issue
                }
            } else {
                // Invalid username or password
                conn.rollback();
                return LoginResultStatus.INVALID_CREDENTIALS; // Specific status
            }

        } catch (SQLException e) {
            System.err.println("Database error during login attempt for user " + username + ": " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            }
            return LoginResultStatus.DATABASE_ERROR; // General DB error
        } finally {
            // Close resources
            try { if (rsCheck != null) rsCheck.close(); } catch (SQLException e) { /* ignore */ }
            try { if (updateStmt != null) updateStmt.close(); } catch (SQLException e) { /* ignore */ }
            try { if (checkStmt != null) checkStmt.close(); } catch (SQLException e) { /* ignore */ }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) { /* ignore */ }
        }
    }

    // Keep getUsuarioByUsername or a similar method to fetch details after successful login
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

    // MODIFICADO para retornar List<Integer>
    public static List<Integer> marcarMensagensComoLidas(String destinatario, String remetente) {
        List<Integer> markedIds = new ArrayList<>();
        String selectSql = "SELECT id FROM mensagens WHERE remetente = ? AND destinatario = ? AND lida = false";
        String updateSql = "UPDATE mensagens SET lida = true " +
                "WHERE remetente = ? AND destinatario = ? AND lida = false";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            // 1. Select IDs of unread messages for this conversation
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, remetente);
                selectStmt.setString(2, destinatario);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        markedIds.add(rs.getInt("id"));
                    }
                }
            }


            // 2. Update the messages to be read
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, remetente);
                updateStmt.setString(2, destinatario);
                updateStmt.executeUpdate(); // No need to check rows affected here as we already got the IDs
            }

            conn.commit(); // Commit transaction

        } catch (SQLException e) {
            System.err.println("Erro ao marcar mensagens como lidas de " + remetente + " para " + destinatario + ": " + e.getMessage());
            e.printStackTrace(); // Log the error appropriately
            return Collections.emptyList(); // Return empty list on error
        }
        return markedIds; // Return the IDs that were marked as read
    }



    public static boolean enviarMensagemComReferencia(String remetente, String destinatario, String conteudo, Integer referenciaId) {
        // Primeiro, insere a mensagem normalmente
        String sql = "INSERT INTO mensagens (remetente, destinatario, conteudo, data_hora, lida) VALUES (?, ?, ?, ?, ?)";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet generatedKeys = null;

        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, remetente);
            stmt.setString(2, destinatario);
            stmt.setString(3, conteudo);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.setBoolean(5, false);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                return false;
            }

            // Se há um ID de referência, registra a relação de resposta
            if (referenciaId != null) {
                generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int novoMensagemId = generatedKeys.getInt(1);
                    return registrarReferenciaMensagem(novoMensagemId, referenciaId);
                } else {
                    return false;
                }
            }

            return true;
        } catch (SQLException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (generatedKeys != null) generatedKeys.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Registra a relação entre uma mensagem e outra à qual ela responde
     */
    private static boolean registrarReferenciaMensagem(int mensagemId, int referenciaId) {
        String sql = "INSERT INTO mensagens_referencias (mensagem_id, referencia_id) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mensagemId);
            stmt.setInt(2, referenciaId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao registrar referência de mensagem: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtém o ID da mensagem que está sendo respondida por uma mensagem específica
     */
    public static Integer getMensagemRespondidaId(int mensagemId) {
        String sql = "SELECT referencia_id FROM mensagens_referencias WHERE mensagem_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mensagemId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("referencia_id");
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Erro ao obter referência de mensagem: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Obtém a última mensagem trocada entre dois usuários
     */
    public static Optional<Mensagem> getUltimaMensagem(String usuario1, String usuario2) {
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
                return Optional.of(new Mensagem(
                        rs.getInt("id"),
                        rs.getString("remetente"),
                        rs.getString("destinatario"),
                        rs.getString("conteudo"),
                        rs.getTimestamp("data_hora"),
                        rs.getBoolean("lida")
                ));
            }
            return Optional.empty();
        } catch (SQLException e) {
            System.err.println("Erro ao obter última mensagem entre " + usuario1 + " e " + usuario2 + ": " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Obtém um lote paginado de mensagens privadas entre dois usuários.
     * Mensagens são retornadas em ordem cronológica ASC (mais antigas primeiro) para exibição.
     */
    public static List<Mensagem> getMensagensPrivadasPaginado(String usuario1, String usuario2, int limit, int offset) {
        List<Mensagem> mensagens = new ArrayList<>();
        String sql = """
                    SELECT id, remetente, destinatario, conteudo, data_hora, lida
                    FROM mensagens
                    WHERE (remetente = ? AND destinatario = ?) OR
                          (remetente = ? AND destinatario = ?)
                    ORDER BY data_hora DESC -- Buscar mais recentes primeiro no DB para facilitar o OFFSET
                    LIMIT ? OFFSET ?
                """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario1);
            stmt.setString(2, usuario2);
            stmt.setString(3, usuario2);
            stmt.setString(4, usuario1);
            stmt.setInt(5, limit);
            stmt.setInt(6, offset);

            ResultSet rs = stmt.executeQuery();
            List<Mensagem> fetchedMessages = new ArrayList<>();
            while (rs.next()) {
                fetchedMessages.add(new Mensagem(
                        rs.getInt("id"),
                        rs.getString("remetente"),
                        rs.getString("destinatario"),
                        rs.getString("conteudo"),
                        rs.getTimestamp("data_hora"),
                        rs.getBoolean("lida")
                ));
            }
            // Reverter a ordem para que as mensagens mais antigas fiquem no início da lista para a UI
            Collections.reverse(fetchedMessages);
            mensagens.addAll(fetchedMessages);

        } catch (SQLException e) {
            System.err.println("Error fetching paginated private messages between " + usuario1 + " and " + usuario2 + ": " + e.getMessage());
            e.printStackTrace();
        }
        return mensagens;
    }

    public static int getTotalMensagensPrivadas(String usuario1, String usuario2) {
        int count = 0;
        String sql = """
                    SELECT COUNT(*) AS total
                    FROM mensagens
                    WHERE (remetente = ? AND destinatario = ?) OR
                          (remetente = ? AND destinatario = ?)
                """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario1);
            stmt.setString(2, usuario2);
            stmt.setString(3, usuario2);
            stmt.setString(4, usuario1);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt("total");
            }
        } catch (SQLException e) {
            System.err.println("Error getting total private messages count between " + usuario1 + " and " + usuario2 + ": " + e.getMessage());
            e.printStackTrace();
        }
        return count;
    }

    /**
     * Obtém uma mensagem específica pelo seu ID.
     * Necessário para carregar mensagens referenciadas que podem não estar no lote inicial.
     */
    public static Optional<Mensagem> getMensagemPorId(int id) {
        String sql = """
                    SELECT id, remetente, destinatario, conteudo, data_hora, lida
                    FROM mensagens
                    WHERE id = ?
                """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new Mensagem(
                        rs.getInt("id"),
                        rs.getString("remetente"),
                        rs.getString("destinatario"),
                        rs.getString("conteudo"),
                        rs.getTimestamp("data_hora"),
                        rs.getBoolean("lida")
                ));
            }
            return Optional.empty();
        } catch (SQLException e) {
            System.err.println("Erro ao obter mensagem por ID: " + id + ": " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }



}