package app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import jpos.Biometrics;
import jpos.JposException;

import java.net.URL;
import java.nio.file.Paths;

import jpos.loader.JposServiceLoader;
import jpos.config.JposEntry;
import jpos.config.JposEntryRegistry;

public class Main extends Application {

    // ⚠️ Debe coincidir con el logicalName definido en jposUareU.xml
    private static final String LOGICAL_NAME = "DPFingerprintReader";

    @Override
    public void start(Stage stage) {
        Label status = new Label("Listo. Da clic para detectar el lector.");
        Button btn = new Button("Detectar sensor");

        btn.setOnAction(e -> detectar(status));

        VBox root = new VBox(12, status, btn);
        root.setPadding(new Insets(16));
        stage.setTitle("Detector U.are.U (JavaFX + JavaPOS)");
        stage.setScene(new Scene(root, 460, 160));
        stage.show();
    }

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
            jpos.config.JposEntryRegistry reg = jpos.loader.JposServiceLoader.getManager().getEntryRegistry();
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
