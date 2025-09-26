package app;

import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUGlobal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;

/** Codec robusto para FMD con awareness de formato y fallback vía Engine.* */
public final class FmdCodec {
    private FmdCodec() {}

    /** Intentamos respetar el formato que venga desde DB; si es null, usamos ANSI_378_2004. */
    public static Fmd fromBase64(String b64) { return fromBase64(b64, "ANSI_378_2004"); }

    public static Fmd fromBase64(String b64, String fmtName) {
        if (b64 == null || b64.isBlank()) return null;
        byte[] data = Base64.getDecoder().decode(b64);
        Fmd.Format fmt = parseFormat(fmtName);

        // 0) Si existe Fmd.Deserialize(byte[], Fmd.Format)
        try {
            Method m = Fmd.class.getMethod("Deserialize", byte[].class, Fmd.Format.class);
            return (Fmd) m.invoke(null, data, fmt);
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            throw new RuntimeException("Error Fmd.Deserialize(byte[], Format): " + t.getMessage(), t);
        }

        // 0.1) Fmd.Deserialize(byte[])
        try {
            Method m = Fmd.class.getMethod("Deserialize", byte[].class);
            return (Fmd) m.invoke(null, data);
        } catch (NoSuchMethodException ignore) {
        } catch (Throwable t) {
            throw new RuntimeException("Error Fmd.Deserialize(byte[]): " + t.getMessage(), t);
        }

        // 1) Fallbacks vía Engine: busca métodos que devuelvan Fmd y acepten (byte[][, Fmd.Format])
        try {
            Object engine = UareUGlobal.GetEngine();
            for (Method m : engine.getClass().getMethods()) {
                if (!Fmd.class.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] pt = m.getParameterTypes();
                try {
                    if (pt.length == 2 && pt[0] == byte[].class && pt[1] == Fmd.Format.class) {
                        return (Fmd) m.invoke(engine, data, fmt);
                    }
                    if (pt.length == 1 && pt[0] == byte[].class) {
                        return (Fmd) m.invoke(engine, data);
                    }
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}

        // 2) Constructores no públicos
        try {
            for (Constructor<?> ctor : Fmd.class.getDeclaredConstructors()) {
                Class<?>[] pt = ctor.getParameterTypes();
                try {
                    if (pt.length == 2 && pt[0] == byte[].class && pt[1] == Fmd.Format.class) {
                        ctor.setAccessible(true);
                        return (Fmd) ctor.newInstance(data, fmt);
                    }
                    if (pt.length == 3 && pt[0] == byte[].class && pt[1] == Fmd.Format.class && pt[2] == int.class) {
                        ctor.setAccessible(true);
                        return (Fmd) ctor.newInstance(data, fmt, 0);
                    }
                    if (pt.length == 1 && pt[0] == byte[].class) {
                        ctor.setAccessible(true);
                        return (Fmd) ctor.newInstance(data);
                    }
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}

        // 3) Inyección directa por campos (very last resort)
        try {
            Fmd obj = allocateFmd();
            Field[] fs = Fmd.class.getDeclaredFields();
            Field dataField = null;
            Field fmtField = null;
            for (Field f : fs) {
                if (f.getType() == byte[].class && dataField == null) dataField = f;
                if (f.getType().getName().endsWith("Fmd$Format") && fmtField == null) fmtField = f;
                if (f.getType() == Fmd.Format.class && fmtField == null) fmtField = f;
            }
            if (dataField != null) { dataField.setAccessible(true); dataField.set(obj, data); }
            if (fmtField  != null) { fmtField.setAccessible(true);  fmtField.set(obj, fmt); }
            if (dataField != null || fmtField != null) return obj;
        } catch (Throwable ignore) {}

        throw new UnsupportedOperationException("SDK sin métodos públicos para deserializar FMD.");
    }

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

    private static Fmd.Format parseFormat(String name) {
        if (name == null || name.isBlank()) name = "ANSI_378_2004";
        try {
            return Fmd.Format.valueOf(name);
        } catch (IllegalArgumentException iae) {
            // intenta mapeos comunes
            String n = name.trim().toUpperCase();
            if (n.contains("ANSI") && n.contains("378")) return Fmd.Format.ANSI_378_2004;
            return Fmd.Format.ANSI_378_2004;
        }
    }

    /** Intenta instanciar Fmd sin llamar a constructor (Unsafe-style); si falla, usa ctor vacío si existiera. */
    private static Fmd allocateFmd() throws Exception {
        try {
            Class<?> unsafeClz = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClz.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method allocate = unsafeClz.getMethod("allocateInstance", Class.class);
            return (Fmd) allocate.invoke(unsafe, Fmd.class);
        } catch (Throwable t) {
            try {
                Constructor<Fmd> c = Fmd.class.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (Throwable t2) {
                throw new UnsupportedOperationException("No se pudo instanciar Fmd por reflexión.", t2);
            }
        }
    }
}
