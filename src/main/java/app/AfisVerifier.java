package app;

import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;

import java.util.List;

/** Verificación 1:N con SourceAFIS contra las plantillas del backend. */
public final class AfisVerifier {
    private AfisVerifier() {}

    /** Umbral típico; ajústalo según tus pruebas (30–50). */
    public static double THRESHOLD = 40.0;

    public static class Result {
        public final Integer idOperador; // null si no supera el umbral
        public final double bestScore;
        public final int total;
        public Result(Integer idOperador, double bestScore, int total) {
            this.idOperador = idOperador;
            this.bestScore = bestScore;
            this.total = total;
        }
    }

    /** Compara la plantilla probe contra todo el backend y devuelve el mejor match si supera el umbral. */
    public static Result identify(FingerprintTemplate probeTpl) throws Exception {
        List<FingerApi.FingerprintRecord> list = FingerApi.listAll();
        if (list == null || list.isEmpty()) {
            return new Result(null, 0, 0);
        }

        FingerprintMatcher matcher = new FingerprintMatcher(probeTpl);
        double bestScore = 0.0;
        Integer bestId = null;

        for (FingerApi.FingerprintRecord rec : list) {
            FingerprintTemplate cand = AfisCodec.fromFmdBase64(rec.fmdBase64); // tu backend manda "fmd" o "fmdBase64"
            double score = matcher.match(cand);
            if (score > bestScore) {
                bestScore = score;
                bestId = rec.idOperador;
            }
        }

        if (bestScore >= THRESHOLD) {
            return new Result(bestId, bestScore, list.size());
        } else {
            return new Result(null, bestScore, list.size());
        }
    }
}
