package app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import jpos.Biometrics;
import jpos.JposException;

import jpos.loader.JposServiceLoader;

// U.are.U SDK
import com.digitalpersona.uareu.Fid;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.UareUException;
import com.digitalpersona.uareu.UareUGlobal;

public class Main extends Application {

    // Debe coincidir con el logicalName del jposUareU.xml
    private static final String LOGICAL_NAME = "DPFingerprintReader";

    // Demo simple: id fijo para guardar/cargar la plantilla
    private static final String USER_ID = "usuario1";

    // Mantenemos un reader abierto para enrolar/verificar
    private Reader reader;

    // Fallback en memoria por si tu SDK no permite reconstruir FMD desde bytes
    private static Fmd lastEnrolledFmd;

    @Override
    public void start(Stage stage) {
        Label status = new Label("Listo. Usa los botones.");
        Button btnDetectar = new Button("Detectar sensor (JavaPOS)");
        Button btnEnroll   = new Button("Enrolar (capturar y guardar)");
        Button btnVerify   = new Button("Verificar (comparar)");

        btnDetectar.setOnAction(e -> detectar(status));
        btnEnroll.setOnAction(e -> enrolar(status));
        btnVerify.setOnAction(e -> verificar(status));

        HBox row = new HBox(12, btnDetectar, btnEnroll, btnVerify);
        VBox root = new VBox(12, status, row);
        root.setPadding(new Insets(16));

        stage.setTitle("Detector U.are.U (JavaFX + JavaPOS + UareU)");
        stage.setScene(new Scene(root, 680, 160));
        stage.show();
    }

    // --------- Botón: detectar por JavaPOS (lo que ya tenías) ----------
    private void detectar(Label status) {
        Biometrics bio = new Biometrics();
        try {
            bio.open(LOGICAL_NAME);      // busca la entrada en el registry
            bio.claim(2000);             // toma control
            bio.setDeviceEnabled(true);  // habilita
            status.setText("✅ Sensor detectado y habilitado");
            alert(Alert.AlertType.INFORMATION, "Éxito", "Sensor detectado", null);
        } catch (JposException ex) {
            ex.printStackTrace(); // muestra la causa real en consola
            status.setText("❌ No se detectó el sensor: " + ex.getMessage());
            alert(Alert.AlertType.ERROR, "Error", "No se detectó el sensor", ex.toString());
        } finally {
            try { bio.setDeviceEnabled(false); } catch (Exception ignore) {}
            try { bio.release(); } catch (Exception ignore) {}
            try { bio.close(); } catch (Exception ignore) {}
        }
    }

    // --------- Botón: ENROLAR (captura + generar FMD + guardar) ----------
    private void enrolar(Label status) {
        try {
            ensureReader();
            status.setText("Coloca el dedo para ENROLAR…");
            // Timeout efectivo 15s; UareUUtil internamente toma resolución soportada y cancela capturas previas
            Fid fid = UareUUtil.capture(reader, 15000);
            Fmd fmd = UareUGlobal.GetEngine().CreateFmd(fid, Fmd.Format.ANSI_378_2004);
            TemplateStore.save(USER_ID, fmd);   // guarda bytes en disco
            lastEnrolledFmd = fmd;              // cache en memoria para esta sesión
            status.setText("✅ Enrolado guardado en " + TemplateStore.pathFor(USER_ID));
            alert(Alert.AlertType.INFORMATION, "Enrolado", "Plantilla guardada", null);
        } catch (Exception ex) {
            ex.printStackTrace();
            status.setText("❌ Enrolado falló: " + ex.getMessage());
            alert(Alert.AlertType.ERROR, "Error", "Enrolado falló", ex.toString());
        }
    }

    // --------- Botón: VERIFICAR (captura + comparar contra guardado) ----------
    private void verificar(Label status) {
        try {
            ensureReader();

            // 1) Recuperar plantilla de referencia (disco o fallback memoria)
            Fmd reference;
            try {
                if (!TemplateStore.exists(USER_ID)) {
                    // Si no hay archivo, usa el FMD en memoria si existe
                    if (lastEnrolledFmd == null) {
                        status.setText("No hay plantilla guardada. Enrola primero.");
                        alert(Alert.AlertType.WARNING, "Aviso",
                                "No hay plantilla guardada para " + USER_ID, null);
                        return;
                    }
                    reference = lastEnrolledFmd;
                } else {
                    reference = TemplateStore.load(USER_ID);
                }
            } catch (Exception loadEx) {
                // Si tu SDK no permite reconstruir desde bytes, usamos el cache en memoria
                if (lastEnrolledFmd != null) {
                    reference = lastEnrolledFmd;
                    System.out.println("Usando FMD en memoria (fallback). Carga desde disco falló: " + loadEx);
                } else {
                    throw loadEx;
                }
            }

            // 2) Capturar huella actual y generar FMD de prueba
            status.setText("Coloca el dedo para VERIFICAR…");
            Fid fid = UareUUtil.capture(reader, 15000);
            Fmd probe = UareUGlobal.GetEngine().CreateFmd(fid, Fmd.Format.ANSI_378_2004);

            // 3) Verificar (Identify de 5 parámetros en tu SDK)
            boolean match = UareUUtil.verify(probe, reference);
            status.setText(match ? "✅ MATCH" : "❌ NO MATCH");
            alert(Alert.AlertType.INFORMATION, "Verificación",
                    match ? "Huella coincide ✅" : "Huella NO coincide ❌", null);

        } catch (Exception ex) {
            ex.printStackTrace();
            status.setText("❌ Verificación falló: " + ex.getMessage());
            alert(Alert.AlertType.ERROR, "Error", "Verificación falló", ex.toString());
        }
    }

    // Abre/recicla el primer lector U.are.U (UareUUtil ya lo abre en EXCLUSIVE)
    private void ensureReader() throws UareUException {
        if (reader != null) return;
        reader = UareUUtil.openFirstReader();
    }

    @Override
    public void stop() {
        try { if (reader != null) reader.Close(); } catch (Exception ignore) {}
    }

    public static void main(String[] args) throws Exception {
        // ========= Forzar configuración del JCL por código =========
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("jpos.loader.serviceManagerClass",
                "jpos.loader.simple.SimpleServiceManager");

        // Populator simple (sin validación de XSD/DTD)
        String pop = "jpos.config.simple.xml.SimpleXmlRegPopulator";

        // Cubrimos AMBOS nombres de propiedad (viejos y por índice)
        System.setProperty("jpos.config.regPopulatorClass", pop);
        System.setProperty("jpos.config.populator.class.0", pop);

        // Resolver el XML de resources -> ruta absoluta
        var xmlUrl = Main.class.getClassLoader().getResource("jpos/res/jposUareU.xml");
        if (xmlUrl == null) throw new IllegalStateException("Falta jpos/res/jposUareU.xml en resources");
        var xmlPath = java.nio.file.Paths.get(xmlUrl.toURI()).toString();

        // Cubrimos AMBOS nombres de propiedad para el archivo
        System.setProperty("jpos.config.populatorFile", xmlPath);
        System.setProperty("jpos.config.populator.file.0", xmlPath);

        // Diagnóstico: muestra exactamente qué quedó seteado
        System.out.println("JCL populator class  = " + System.getProperty("jpos.config.regPopulatorClass")
                + " | " + System.getProperty("jpos.config.populator.class.0"));
        System.out.println("JCL populator file   = " + System.getProperty("jpos.config.populatorFile")
                + " | " + System.getProperty("jpos.config.populator.file.0"));

        // ======== Listar lo que el JCL cargó (debe salir DPFingerprintReader) ========
        try {
            var reg = JposServiceLoader.getManager().getEntryRegistry();
            var en = reg.getEntries();
            System.out.println("Entries en JCL:");
            while (en.hasMoreElements()) {
                var je = (jpos.config.JposEntry) en.nextElement();
                System.out.println(" - " + je.getLogicalName());
            }
        } catch (Throwable t) {
            System.out.println("No se pudo listar entries: " + t);
        }
        // ===========================================================

        launch(args);
    }

    private static void alert(Alert.AlertType type, String title, String header, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }
}
