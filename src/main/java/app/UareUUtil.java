package app;

import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.Fid;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.ReaderCollection;
import com.digitalpersona.uareu.UareUGlobal;
import com.digitalpersona.uareu.UareUException;

public final class UareUUtil {
    private UareUUtil() {}

    /** Abre el primer lector disponible */
    public static Reader openFirstReader() throws UareUException {
        ReaderCollection readers = UareUGlobal.GetReaderCollection();
        readers.GetReaders();
        if (readers == null || readers.isEmpty()) {
            throw new IllegalStateException("No hay lector conectado");
        }
        Reader r = readers.get(0);
        r.Open(Reader.Priority.EXCLUSIVE);
        return r;
        // Nota: si prefieres PRIORITY.EXCLUSIVE, cámbialo aquí.
    }

    /** Captura una imagen de huella (FID). timeoutMs: -1 = infinito */
    public static Fid capture(Reader r, int timeoutMs) throws UareUException {
        // Si te pasan -1, pon un timeout razonable (p.ej. 15s)
        int effectiveTimeout = (timeoutMs <= 0) ? 15000 : timeoutMs;

        // Descubre una resolución soportada por el lector
        Reader.Capabilities caps = r.GetCapabilities();
        int res = 500; // fallback
        if (caps != null && caps.resolutions != null && caps.resolutions.length > 0) {
            res = caps.resolutions[0]; // toma la primera soportada por el device
        }

        // Asegura que no hay captura previa pendiente
        try { r.CancelCapture(); } catch (Throwable ignore) {}

        Reader.CaptureResult cr = r.Capture(
                Fid.Format.ANSI_381_2004,
                Reader.ImageProcessing.IMG_PROC_DEFAULT,
                res,
                effectiveTimeout
        );

        if (cr == null || cr.image == null) {
            throw new IllegalStateException("Captura nula o cancelada");
        }
        return cr.image;
    }


    /** Genera plantilla (FMD) a partir de una imagen FID */
    public static Fmd toFmd(Fid fid) throws UareUException {
        Engine engine = UareUGlobal.GetEngine();
        return engine.CreateFmd(fid, Fmd.Format.ANSI_378_2004);
    }

    /** Verificación 1:1: true si coincide */
    public static boolean verify(Fmd probe, Fmd reference) throws UareUException {
        Engine engine = UareUGlobal.GetEngine();
        int farTarget = 21474; // ~1/100000
        Engine.Candidate[] cands = engine.Identify(
                probe,                 // FMD a probar
                0,                     // finger position (0 si no lo usas)
                new Fmd[]{ reference },// “galería” (aquí N=1)
                1,                     // candidatos a devolver
                farTarget              // umbral FAR
        );
        return cands != null && cands.length > 0;
    }



    /** Cierra el lector sin lanzar excepción */
    public static void closeQuiet(Reader r) {
        try { if (r != null) r.Close(); } catch (Exception ignore) {}
    }
}
