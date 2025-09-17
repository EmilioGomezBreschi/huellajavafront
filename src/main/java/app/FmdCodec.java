package app;

import com.digitalpersona.uareu.Fmd;

import java.lang.reflect.Method;
import java.util.Base64;

public final class FmdCodec {
    private FmdCodec() {}

    public static final Fmd.Format FORMAT = Fmd.Format.ANSI_378_2004;

    public static String toBase64(Fmd fmd) {
        if (fmd == null) return null;
        try {
            Method getData = Fmd.class.getMethod("getData");
            byte[] data = (byte[]) getData.invoke(fmd);
            return Base64.getEncoder().encodeToString(data);
        } catch (Throwable t) {
            throw new RuntimeException("No pude serializar FMD a Base64", t);
        }
    }

    public static Fmd fromBase64(String b64) {
        if (b64 == null || b64.isBlank()) return null;
        byte[] data = Base64.getDecoder().decode(b64);
        try {
            Method m = Fmd.class.getMethod("Deserialize", byte[].class, Fmd.Format.class);
            return (Fmd) m.invoke(null, data, FORMAT);
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            throw new RuntimeException("Error al deserializar FMD con Fmd.Deserialize(byte[], Format)", t);
        }
        try {
            Method m = Fmd.class.getMethod("Deserialize", byte[].class);
            return (Fmd) m.invoke(null, data);
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            throw new RuntimeException("Error al deserializar FMD con Fmd.Deserialize(byte[])", t);
        }
        throw new UnsupportedOperationException("SDK sin métodos públicos para deserializar FMD.");
    }
}
