import org.update4j.Configuration;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.*;

public class Launcher {
    public static void main(String[] args) {
        try {
            System.out.println("🔄 Verificando atualizações...");
            URL url = new URL("https://raw.githubusercontent.com/toomazs/NM-OLT-App/main/update/config.xml");

            // Adicione timeout para evitar travamentos
            Configuration config = Configuration.read(new InputStreamReader(url.openStream()));
            System.out.println("✅ Configuração carregada com sucesso!");

            config.update();
            System.out.println("✅ Atualização concluída!");

            config.launch();
        } catch (Exception e) {
            System.err.println("❌ Falha na atualização online: " + e.getMessage());
            e.printStackTrace();

            try {
                System.out.println("🔄 Tentando executar versão local...");
                Path localJar = Paths.get("OLTApp.jar");

                if (Files.exists(localJar)) {
                    System.out.println("✅ OLTApp.jar encontrado. Iniciando...");
                    new ProcessBuilder("java", "-jar", localJar.toString())
                            .inheritIO()  // Mostra a saída do processo filho
                            .start()
                            .waitFor();   // Espera o processo terminar
                } else {
                    System.err.println("❌ Arquivo OLTApp.jar não encontrado!");
                }
            } catch (Exception ex) {
                System.err.println("❌ Falha crítica ao iniciar aplicação local:");
                ex.printStackTrace();
            }
        }
    }
}