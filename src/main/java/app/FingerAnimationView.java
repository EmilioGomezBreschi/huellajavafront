package app;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class FingerAnimationView extends StackPane {
    private final Label subtitle = new Label("Coloca tu dedo en el lector…");
    private final Label helper = new Label("");
    private final Circle ring = new Circle(90, Color.TRANSPARENT);
    private final Circle dot = new Circle(6, Color.web("#4f46e5"));
    private final SVGPath finger = new SVGPath();

    public FingerAnimationView() {
        setPadding(new Insets(24));
        setStyle("-fx-background-color: white;");

        // Icono de huella (SVG)
        finger.setContent("M66 20c-15 0-27 12-27 27 0 24 18 35 24 57 1 4 7 4 8 0 6-22 24-33 24-57 0-15-12-27-27-27zm0 8c10 0 19 9 19 19 0 19-14 28-20 47-6-19-20-28-20-47 0-10 9-19 19-19z");
        finger.setFill(Color.web("#312e81"));

        ring.setStroke(Color.web("#a5b4fc"));
        ring.setStrokeWidth(3);

        var pulse = new ScaleTransition(Duration.millis(900), ring);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.12);  pulse.setToY(1.12);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);

        var glow = new FillTransition(Duration.millis(900), dot, Color.web("#4f46e5"), Color.web("#818cf8"));
        glow.setAutoReverse(true);
        glow.setCycleCount(Animation.INDEFINITE);

        subtitle.setStyle("-fx-font-size: 18px; -fx-text-fill:#111827; -fx-font-weight:600;");
        helper.setStyle("-fx-font-size: 13px; -fx-text-fill:#6b7280;");

        Group icon = new Group(ring, finger, dot);
        StackPane.setAlignment(icon, Pos.CENTER);
        StackPane.setAlignment(subtitle, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(helper, Pos.BOTTOM_CENTER);

        var container = new StackPane(icon);
        container.setPrefSize(240, 240);
        container.setMinSize(240, 240);
        container.setMaxSize(240, 240);

        var root = new StackPane(container);
        root.setPrefHeight(260);

        var wrap = new StackPane(new VBoxWithGap(8, subtitle, helper));
        wrap.setAlignment(Pos.TOP_CENTER);
        wrap.setPadding(new Insets(0,0,0,0));

        getChildren().addAll(root, new VBoxWithGap(16, wrap));
        StackPane.setAlignment(wrap, Pos.BOTTOM_CENTER);

        pulse.play();
        glow.play();
    }

    public void setStatus(String main, String sub) {
        subtitle.setText(main == null ? "" : main);
        helper.setText(sub == null ? "" : sub);
    }

    /** VBox minimalista con espaciado. */
    static final class VBoxWithGap extends javafx.scene.layout.VBox {
        VBoxWithGap(double gap, javafx.scene.Node... nodes) {
            super(gap, nodes);
            setAlignment(Pos.TOP_CENTER);
        }
    }
}
