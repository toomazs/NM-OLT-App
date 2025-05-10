import org.update4j.Configuration;
import java.io.InputStreamReader;
import java.net.URL;

public class Launcher {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://raw.githubusercontent.com/toomazs/NM-OLT-App/main/update/config.xml");
        Configuration config = Configuration.read(new InputStreamReader(url.openStream()));
        config.update();
        config.launch();
    }
}
