package app;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class PrestamoView {
    private final int idOperador;

    public PrestamoView(int idOperador) {
        this.idOperador = idOperador;
    }

    public Scene scene() {
        Label title = new Label("Creación de Préstamos");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 700;");

        Label info = new Label("Operador identificado: " + idOperador);
        info.setStyle("-fx-font-size: 14px; -fx-text-fill:#374151;");

        Button volver = new Button("Volver al inicio");
        volver.setOnAction(e -> Navigator.show(new MenuView().scene()));

        VBox root = new VBox(12, title, info, volver);
        root.setPadding(new Insets(20));

        return new Scene(root, 860, 420);
        // Aquí agrega tu UI real de préstamos.
    }
}
