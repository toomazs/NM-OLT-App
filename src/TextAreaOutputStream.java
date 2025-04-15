import javafx.scene.control.TextArea;
import java.io.IOException;
import java.io.OutputStream;
import javafx.application.Platform;

public class TextAreaOutputStream extends OutputStream {
    private TextArea textArea;
    private StringBuilder buffer = new StringBuilder();

    public TextAreaOutputStream(TextArea textArea) {
        this.textArea = textArea;
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
    public void flush() throws IOException {
        flushBuffer();
    }

    private void flushBuffer() {
        String text = buffer.toString();
        buffer.setLength(0);
        Platform.runLater(() -> {
            textArea.appendText(text);
            textArea.positionCaret(textArea.getLength());
        });
    }
}