package app;

import javafx.scene.Scene;
import javafx.stage.Stage;

public final class Navigator {
    private static Stage primary;

    private Navigator() {}

    public static void init(Stage stage) {
        primary = stage;
        primary.setTitle("Eikon Touch – Demo");
    }

    public static void show(Scene scene) {
        if (primary == null) throw new IllegalStateException("Navigator no inicializado.");
        primary.setScene(scene);
        primary.sizeToScene();
        primary.centerOnScreen();
        primary.show();
    }
}
