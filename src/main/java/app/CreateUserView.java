package app;

import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.Reader;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class CreateUserView {
    // Ya no pedimos idOperador ni fingerId: los asigna el backend
    private final TextField nombre = new TextField();
    private final TextField apellidoPaterno = new TextField();
    private final TextField apellidoMaterno = new TextField();
    private final TextField numeroOperador = new TextField();

    private final Label status = new Label("");

    public Scene scene() {
        Label title = new Label("Crear Usuario");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 700;");

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(10); form.setPadding(new Insets(10));

        nombre.setPromptText("Emilio");
        apellidoPaterno.setPromptText("Gomez");
        apellidoMaterno.setPromptText("Breschi");
        numeroOperador.setPromptText("2");

        int r = 0;
        form.add(new Label("Nombre:"), 0, r); form.add(nombre, 1, r++);
        form.add(new Label("Apellido paterno:"), 0, r); form.add(apellidoPaterno, 1, r++);
        form.add(new Label("Apellido materno:"), 0, r); form.add(apellidoMaterno, 1, r++);
        form.add(new Label("Número de operador:"), 0, r); form.add(numeroOperador, 1, r++);

        Button crear = new Button("Crear usuario y enrolar huella");
        crear.setOnAction(e -> createAndEnroll());

        Button cancelar = new Button("Cancelar");
        cancelar.setOnAction(e -> Navigator.show(new MenuView().scene()));

        HBox buttons = new HBox(10, crear, cancelar);

        VBox root = new VBox(12, title, form, buttons, status);
        root.setPadding(new Insets(20));

        return new Scene(root, 860, 520);
    }

    private void setStatus(String s) {
        Platform.runLater(() -> status.setText(s));
    }

    private void createAndEnroll() {
        String n = nombre.getText() == null ? "" : nombre.getText().trim();
        String ap = apellidoPaterno.getText() == null ? "" : apellidoPaterno.getText().trim();
        String am = apellidoMaterno.getText() == null ? "" : apellidoMaterno.getText().trim();
        String num = numeroOperador.getText() == null ? "" : numeroOperador.getText().trim();

        if (n.isEmpty() || ap.isEmpty() || am.isEmpty() || num.isEmpty()) {
            setStatus("Completa todos los campos.");
            return;
        }

        setStatus("Creando usuario…");
        new Thread(() -> {
            try {
                // 1) Crear operador (el backend devuelve idOperador autoincremental)
                int idOperadorCreado = FingerApi.createOperador(n, ap, am, num);
                setStatus("Usuario creado (idOperador=" + idOperadorCreado + "). Iniciando enrolamiento…");

                // 2) Capturar y enrolar la huella para ese operador
                Reader reader = null;
                try {
                    reader = UareUUtil.openFirstReader();
                    setStatus("Lector listo. Capturando 4 muestras…");
                    List<Fmd> samples = UareUUtil.captureSamples(reader, 4, msg -> setStatus("Captura: " + msg));
                    Fmd enrolled = UareUUtil.createEnrollmentFmd(samples);

                    String b64 = AfisCodec.fmdToBase64(enrolled);
                    setStatus("Enviando huella al backend…");
                    String resp = FingerApi.enroll(idOperadorCreado, b64, UareUUtil.FMD_FORMAT.name());
                    setStatus("Enrolamiento ok: " + acorta(resp));
                } finally {
                    UareUUtil.closeQuietly(reader);
                }

                // 3) Ir a préstamos con el operador recién creado
                Platform.runLater(() -> Navigator.show(new PrestamoView(idOperadorCreado).scene()));
            } catch (Throwable t) {
                setStatus("Error: " + t.getMessage());
                t.printStackTrace();
            }
        }, "create+enroll").start();
    }

    private static String acorta(String s) {
        if (s == null) return "—";
        s = s.replaceAll("\\s+", " ");
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }
}
