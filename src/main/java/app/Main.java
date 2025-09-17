package app;

import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.Reader;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Mini UI JavaFX para enrolar y verificar contra backend. */
public class Main extends Application {

    private Label status = new Label("—");
    private TextField txtOperador = new TextField();

    @Override
    public void start(Stage stage) {
        txtOperador.setPromptText("ID de operador (para ENROLAR)");
        Button btnEnroll = new Button("Enrolar");
        btnEnroll.setOnAction(e -> enrollAsync());

        Button btnVerify = new Button("Verificar huella");
        btnVerify.setOnAction(e -> verifyAsync());

        HBox buttons = new HBox(10, btnEnroll, btnVerify);
        VBox root = new VBox(10, new Label("Eikon Touch – Demo Enrolamiento/Verificación"),
                txtOperador, buttons, status);
        root.setPadding(new Insets(16));

        stage.setScene(new Scene(root, 560, 220));
        stage.setTitle("Eikon Touch – Enrolamiento y Verificación");
        stage.show();
    }

    private void enrollAsync() {
        String raw = txtOperador.getText() == null ? "" : txtOperador.getText().trim();
        if (raw.isEmpty()) {
            status.setText("Ingresa el ID de operador para enrolar.");
            return;
        }
        int idOperador;
        try {
            idOperador = Integer.parseInt(raw);
        } catch (NumberFormatException nfe) {
            status.setText("El ID de operador debe ser numérico.");
            return;
        }

        status.setText("Preparando enrolamiento...");
        new Thread(() -> {
            Reader reader = null;
            try {
                reader = UareUUtil.openFirstReader();
                setStatus("Lector abierto.");
                List<Fmd> samples = UareUUtil.captureSamples(reader, 4, this::setStatus);
                Fmd enrolled = UareUUtil.createEnrollmentFmd(samples);
                String b64 = FmdCodec.toBase64(enrolled);
                setStatus("Plantilla lista. Enviando...");
                String server = FingerApi.enroll(idOperador, b64, UareUUtil.FMD_FORMAT.name());
                setStatus("Backend respondió: " + acorta(server));
                try {
                    String verify = FingerApi.getHuella(idOperador);
                    setStatus("GET verificación: " + acorta(verify));
                } catch (Throwable ignore) {}
            } catch (Throwable t) {
                setStatus("Error en enrolamiento: " + t.getMessage());
                t.printStackTrace();
            } finally {
                UareUUtil.closeQuietly(reader);
            }
        }, "enroll-thread").start();
    }

    private void verifyAsync() {
        status.setText("Preparando verificación...");
        new Thread(() -> {
            Reader reader = null;
            try {
                reader = UareUUtil.openFirstReader();
                setStatus("Lector abierto. Capturando huella...");
                // Capturamos una muestra para verificación rápida
                Fmd probe = UareUUtil.createFmd(UareUUtil.capture(reader, 10_000));
                setStatus("Huella capturada. Buscando coincidencias en backend...");
                Verifier.MatchResult res = Verifier.identifyOperator(probe, UareUUtil.DEFAULT_THRESHOLD);
                if (res == null) {
                    setStatus("No se encontró coincidencia por debajo del umbral.");
                } else {
                    setStatus("Coincide con idOperador=" + res.idOperador + " (score=" + res.score + ").");
                }
            } catch (Throwable t) {
                setStatus("Error en verificación: " + t.getMessage());
                t.printStackTrace();
            } finally {
                UareUUtil.closeQuietly(reader);
            }
        }, "verify-thread").start();
    }

    private static String acorta(String s) {
        if (s == null) return "—";
        s = s.replaceAll("\s+", " ");
        if (s.length() > 200) return s.substring(0, 200) + "...";
        return s;
    }

    private void setStatus(String s) {
        javafx.application.Platform.runLater(() -> status.setText(s));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
