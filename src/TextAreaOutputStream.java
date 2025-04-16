import javafx.scene.control.TextArea;
import java.io.IOException;
import java.io.OutputStream;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class TextAreaOutputStream extends OutputStream {
    private TextArea textArea;
    private StringBuilder buffer = new StringBuilder();
    private static final int MAX_LINES = 1000; // Limite de linhas para evitar lentidão
    private PauseTransition autoScrollDelay;

    public TextAreaOutputStream(TextArea textArea) {
        this.textArea = textArea;

        // Configurar um delay para scroll automático mais suave
        autoScrollDelay = new PauseTransition(Duration.millis(50));
        autoScrollDelay.setOnFinished(e -> scrollToBottom());
    }

    @Override
    public void write(int b) throws IOException {
        // converte byte para char e adiciona p buffer
        char c = (char) b;
        buffer.append(c);

        // quebra de linha ou buffer muito grande = flush
        if (c == '\n' || buffer.length() > 100) {
            flushBuffer();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            write(b[i]);
        }
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
    }

    private void flushBuffer() {
        if (buffer.length() > 0) {
            String text = buffer.toString();
            buffer.setLength(0);

            Platform.runLater(() -> {
                // Aplicar formatação e cores para destacar comandos, erros, etc.
                String formattedText = formatOutput(text);

                // Adicionar o texto formatado
                textArea.appendText(formattedText);

                // Limitar o número de linhas no terminal para evitar lentidão
                limitTextAreaLines();

                // Programar um scroll suave para o final
                autoScrollDelay.playFromStart();
            });
        }
    }

    private String formatOutput(String text) {
        // Aqui você pode adicionar lógica para destacar comandos, erros, etc.
        // Por exemplo, colorir saídas de erro em vermelho
        // Obs: Infelizmente o TextArea do JavaFX não suporta ANSI colors ou HTML
        // então não podemos realmente colorir o texto, mas podemos formatar

        // Adicione outras formatações se precisar no futuro
        return text;
    }

    private void limitTextAreaLines() {
        String content = textArea.getText();
        String[] lines = content.split("\n");

        if (lines.length > MAX_LINES) {
            // Manter apenas as últimas MAX_LINES linhas
            StringBuilder newContent = new StringBuilder();
            for (int i = lines.length - MAX_LINES; i < lines.length; i++) {
                newContent.append(lines[i]).append("\n");
            }
            textArea.setText(newContent.toString());
        }
    }

    private void scrollToBottom() {
        textArea.positionCaret(textArea.getLength());
        textArea.setScrollTop(Double.MAX_VALUE);
    }
}