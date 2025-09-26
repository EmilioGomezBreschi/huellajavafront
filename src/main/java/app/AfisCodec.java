package app;

import com.digitalpersona.uareu.Fmd;
import com.machinezoo.sourceafis.FingerprintCompatibility;
import com.machinezoo.sourceafis.FingerprintTemplate;

import java.lang.reflect.Method;
import java.util.Base64;

/** Puente entre FMD (DigitalPersona) y SourceAFIS. */
public final class AfisCodec {
    private AfisCodec() {}

    /** Toma el FMD que produce el SDK DP y devuelve su blob ANSI/ISO en Base64. */
    public static String fmdToBase64(Fmd fmd) {
        if (fmd == null) return null;
        try {
            Method getData = Fmd.class.getMethod("getData");
            byte[] data = (byte[]) getData.invoke(fmd);
            return Base64.getEncoder().encodeToString(data);
        } catch (Throwable t) {
            throw new RuntimeException("No pude serializar FMD a Base64 (DP SDK).", t);
        }
    }

    /** Crea una plantilla SourceAFIS desde un template ANSI/ISO en Base64 (lo que entrega tu backend). */
    public static FingerprintTemplate fromFmdBase64(String fmdBase64) {
        if (fmdBase64 == null || fmdBase64.isBlank())
            throw new IllegalArgumentException("fmdBase64 vacío.");
        byte[] bytes = Base64.getDecoder().decode(fmdBase64);
        return FingerprintCompatibility.importTemplate(bytes);
    }
}
