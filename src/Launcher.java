import org.update4j.Configuration;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;

public class Launcher {
    public static void main(String[] args) {
        try {
            System.out.println("🔄 Verificando atualizações...");

            // Teste de conexão
            URL testUrl = new URL("https://raw.githubusercontent.com");
            URLConnection testConn = testUrl.openConnection();
            testConn.connect();
            System.out.println("✅ Conexão com GitHub OK");

            URL configUrl = new URL("https://raw.githubusercontent.com/toomazs/NM-OLT-App/main/update/config.xml");
            System.out.println("📦 Tentando baixar: " + configUrl);

            URLConnection conn = configUrl.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            Configuration config = Configuration.read(new InputStreamReader(conn.getInputStream()));
            System.out.println("✅ Configuração carregada com sucesso!");

            config.update();
            System.out.println("✅ Atualização concluída!");

            config.launch();
        } catch (Exception e) {
            System.err.println("❌ ERRO na atualização online:");
            e.printStackTrace();

            try {
                System.out.println("🔄 Tentando executar versão local...");
                Path localJar = Paths.get("OLTApp.jar");

                if (Files.exists(localJar)) {
                    System.out.println("✅ OLTApp.jar encontrado (" + Files.size(localJar) + " bytes)");
                    System.out.println("⚙️ Testando execução manual...");

                    // Teste direto do JAR
                    Process p = new ProcessBuilder("java", "-jar", "OLTApp.jar")
                            .inheritIO()
                            .start();

                    int exitCode = p.waitFor();
                    System.out.println("🔚 Processo finalizado com código: " + exitCode);

                    if (exitCode != 0) {
                        System.err.println("❌ Falha na execução do OLTApp.jar");
                        // Mostra possíveis causas
                        System.err.println("\nPossíveis causas:");
                        System.err.println("1. MANIFEST.MF incorreto");
                        System.err.println("2. Dependências faltando");
                        System.err.println("3. JavaFX não configurado");
                    }
                } else {
                    System.err.println("❌ Arquivo OLTApp.jar não encontrado!");
                    System.err.println("📂 Diretório atual: " + Paths.get("").toAbsolutePath());
                    System.err.println("🔍 Conteúdo do diretório:");
                    Files.list(Paths.get("")).forEach(System.err::println);
                }
            } catch (Exception ex) {
                System.err.println("❌ Falha crítica ao iniciar aplicação local:");
                ex.printStackTrace();
            }
        }
    }
}