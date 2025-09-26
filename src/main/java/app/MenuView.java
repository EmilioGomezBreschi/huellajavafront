package app;

import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.Reader;
import com.machinezoo.sourceafis.FingerprintTemplate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.List;

public class MenuView {
    private final BorderPane root = new BorderPane();
    private final FingerAnimationView anim = new FingerAnimationView();
    private final Label title = new Label("Bienvenido • Ponga su dedo para continuar");

    public MenuView() {
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 700; -fx-text-fill:#111827;");
        var header = new BorderPane();
        header.setPadding(new Insets(16));
        header.setCenter(title);

        root.setTop(header);
        root.setCenter(anim);
        anim.setStatus("Coloca tu dedo en el lector…", "Mantén el dedo firme hasta que termine la lectura.");
    }

    public Scene scene() {
        var s = new Scene(root, 860, 420);
        s.windowProperty().addListener((obs, o, n) -> {
            if (n != null) {
                // cuando esté visible, lanza la verificación
                Platform.runLater(this::startVerifyFlow);
            }
        });
        return s;
    }

    private void startVerifyFlow() {
        anim.setStatus("Preparando lector…", "Abriendo dispositivo.");
        new Thread(() -> {
            Reader reader = null;
            try {
                reader = UareUUtil.openFirstReader();
                setAnim("Lector listo", "Capturando 3 muestras…");
                List<Fmd> samples = UareUUtil.captureSamples(reader, 3, msg -> setAnim("Capturando…", msg));
                Fmd probeFmd = UareUUtil.createEnrollmentFmd(samples);

                String probeBase64 = AfisCodec.fmdToBase64(probeFmd);
                FingerprintTemplate probe = AfisCodec.fromFmdBase64(probeBase64);

                setAnim("Comparando…", "Buscando coincidencias (umbral " + AfisVerifier.THRESHOLD + ")");

                AfisVerifier.Result res = AfisVerifier.identify(probe);

                if (res.idOperador != null) {
                    setAnim("¡Bienvenido!", "Operador: " + res.idOperador + " • score=" + String.format("%.2f", res.bestScore));
                    int id = res.idOperador;
                    Platform.runLater(() -> Navigator.show(new PrestamoView(id).scene()));
                } else {
                    setAnim("Usuario no encontrado", "Mejor score=" + String.format("%.2f", res.bestScore) + ". Vamos a crear usuario.");
                    Platform.runLater(() -> Navigator.show(new CreateUserView().scene()));
                }
            } catch (Throwable t) {
                setAnim("Error", t.getMessage());
                t.printStackTrace();
            } finally {
                UareUUtil.closeQuietly(reader);
            }
        }, "verify-on-start").start();
    }

    private void setAnim(String main, String sub) {
        Platform.runLater(() -> anim.setStatus(main, sub));
    }
}
