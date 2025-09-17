package app;

import com.digitalpersona.uareu.Fmd;
import java.util.ArrayList;
import java.util.List;

/** Lógica de verificación/identificación contra todas las huellas del backend. */
public final class Verifier {
    private Verifier() {}

    public static class MatchResult {
        public final Integer idOperador;
        public final Integer score; // menor es mejor
        public MatchResult(Integer idOperador, Integer score) {
            this.idOperador = idOperador;
            this.score = score;
        }
        @Override public String toString() {
            return "MatchResult{idOperador=" + idOperador + ", score=" + score + "}";
        }
    }

    /** Descarga todas las huellas del backend, compara y devuelve el mejor match por score (siempre que esté por debajo del umbral). */
    public static MatchResult identifyOperator(Fmd probe, int threshold) throws Exception {
        List<FingerApi.FingerprintRecord> records = FingerApi.listAll();
        if (records == null || records.isEmpty()) {
            return null;
        }

        Integer bestId = null;
        Integer bestScore = null;

        for (FingerApi.FingerprintRecord r : records) {
            Fmd candidate = FmdCodec.fromBase64(r.fmdBase64);
            if (candidate == null) continue;

            Integer score = UareUUtil.compareScore(probe, candidate);
            if (score == null) {
                // fallback boolean
                boolean ok = UareUUtil.matches(probe, candidate, threshold);
                if (ok) {
                    // si no hay score, devolvemos primer match con score ficticio = threshold/2
                    return new MatchResult(r.idOperador, threshold / 2);
                } else {
                    continue;
                }
            }

            if (score < threshold && (bestScore == null || score < bestScore)) {
                bestScore = score;
                bestId = r.idOperador;
            }
        }

        if (bestId == null) return null;
        return new MatchResult(bestId, bestScore);
    }
}
