package app;

import com.digitalpersona.uareu.Fid;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.Reader;
import com.digitalpersona.uareu.ReaderCollection;
import com.digitalpersona.uareu.UareUGlobal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class UareUUtil {
    private UareUUtil() {}

    public static final Fmd.Format FMD_FORMAT = Fmd.Format.ANSI_378_2004;
    public static final Fid.Format FID_FORMAT = Fid.Format.ANSI_381_2004;
    public static final Reader.ImageProcessing IMG_PROC = Reader.ImageProcessing.IMG_PROC_DEFAULT;
    public static final int DPI = 500;
    public static final int DEFAULT_THRESHOLD = 50000;

    public static Reader openFirstReader() throws Exception {
        ReaderCollection readers = UareUGlobal.GetReaderCollection();
        readers.GetReaders();
        if (readers.size() == 0) {
            throw new IllegalStateException("No hay lectores conectados");
        }
        Reader r = readers.get(0);
        r.Open(Reader.Priority.COOPERATIVE);
        return r;
    }

    public static void closeQuietly(Reader r) {
        if (r == null) return;
        try { r.CancelCapture(); } catch (Throwable ignore) {}
        try { r.Close(); } catch (Throwable ignore) {}
    }

    private static Fid extractFid(Object ret) throws Exception {
        if (ret == null) return null;
        try { return (Fid) ret.getClass().getField("image").get(ret); }
        catch (NoSuchFieldException nf) { return (Fid) ret; }
    }

    public static Fid capture(Reader r, int timeoutMs) throws Exception {
        Reader.ImageProcessing piv = null;
        try { piv = Reader.ImageProcessing.valueOf("IMG_PROC_PIV"); } catch (Throwable ignore) {}

        Reader.ImageProcessing[] ipCandidates = (piv == null)
                ? new Reader.ImageProcessing[]{ IMG_PROC }
                : new Reader.ImageProcessing[]{ IMG_PROC, piv };

        int[] dpiCandidates = new int[]{ DPI, 0, 508 };
        int[] toCandidates  = new int[]{ timeoutMs, Math.max(timeoutMs, 10_000), -1 };
        boolean[] fakeCandidates = new boolean[]{ false, true };

        Exception last = null;

        try {
            Method m5 = Reader.class.getMethod("Capture",
                    Fid.Format.class, Reader.ImageProcessing.class, int.class, int.class, boolean.class);
            for (Reader.ImageProcessing ip : ipCandidates) {
                for (int dpi : dpiCandidates) {
                    for (int to : toCandidates) {
                        for (boolean fk : fakeCandidates) {
                            try {
                                Object ret = m5.invoke(r, FID_FORMAT, ip, dpi, to, fk);
                                Fid fid = extractFid(ret);
                                if (fid != null) return fid;
                            } catch (InvocationTargetException ite) {
                                last = ite.getCause() instanceof Exception ? (Exception) ite.getCause() : ite;
                            } catch (Throwable t) { last = t instanceof Exception ? (Exception) t : new Exception(t); }
                        }
                    }
                }
            }
        } catch (NoSuchMethodException ignore) {}

        try {
            Method m4 = Reader.class.getMethod("Capture",
                    Fid.Format.class, Reader.ImageProcessing.class, int.class, int.class);
            for (Reader.ImageProcessing ip : ipCandidates) {
                for (int dpi : dpiCandidates) {
                    for (int to : toCandidates) {
                        try {
                            Object ret = m4.invoke(r, FID_FORMAT, ip, dpi, to);
                            Fid fid = extractFid(ret);
                            if (fid != null) return fid;
                        } catch (InvocationTargetException ite) {
                            last = ite.getCause() instanceof Exception ? (Exception) ite.getCause() : ite;
                        } catch (Throwable t) { last = t instanceof Exception ? (Exception) t : new Exception(t); }
                    }
                }
            }
        } catch (NoSuchMethodException ignore) {}

        try {
            Method m3 = Reader.class.getMethod("Capture",
                    Fid.Format.class, Reader.ImageProcessing.class, int.class);
            for (Reader.ImageProcessing ip : ipCandidates) {
                for (int dpi : dpiCandidates) {
                    try {
                        Object ret = m3.invoke(r, FID_FORMAT, ip, dpi);
                        Fid fid = extractFid(ret);
                        if (fid != null) return fid;
                    } catch (InvocationTargetException ite) {
                        last = ite.getCause() instanceof Exception ? (Exception) ite.getCause() : ite;
                    } catch (Throwable t) { last = t instanceof Exception ? (Exception) t : new Exception(t); }
                }
            }
        } catch (NoSuchMethodException ignore) {}

        String msg = (last != null && last.getMessage() != null) ? last.getMessage() : "sin detalle";
        throw new IllegalStateException("No pude capturar huella; último error del SDK: " + msg);
    }

    public static Fmd createFmd(Fid fid) throws Exception {
        Object engine = UareUGlobal.GetEngine();
        try {
            Method m = engine.getClass().getMethod("CreateFmd", Fid.class, Fmd.Format.class);
            return (Fmd) m.invoke(engine, fid, FMD_FORMAT);
        } catch (NoSuchMethodException ignore) {}
        try {
            Method m = engine.getClass().getMethod("CreateFmd", Fid.class, Fmd.Format.class, int.class);
            return (Fmd) m.invoke(engine, fid, FMD_FORMAT, 0);
        } catch (NoSuchMethodException ignore) {}
        throw new UnsupportedOperationException("No hay método compatible para crear FMD desde FID en este SDK.");
    }

    public static Fmd createEnrollmentFmd(List<Fmd> samples) throws Exception {
        if (samples == null || samples.isEmpty()) throw new IllegalArgumentException("samples vacío");
        if (samples.size() == 1) return samples.get(0);

        Object engine = UareUGlobal.GetEngine();
        try {
            Method m = engine.getClass().getMethod("CreateEnrollmentFmd", Fmd.Format.class, Fmd[].class);
            return (Fmd) m.invoke(engine, FMD_FORMAT, samples.toArray(new Fmd[0]));
        } catch (NoSuchMethodException ignore) {}
        try {
            Method m = engine.getClass().getMethod("CreateEnrollmentFmd", Fmd[].class);
            return (Fmd) m.invoke(engine, (Object) samples.toArray(new Fmd[0]));
        } catch (NoSuchMethodException ignore) {}
        return samples.get(0);
    }

    public static Integer bestScoreAllViews(Fmd a, Fmd b) throws Exception {
        Object engine = UareUGlobal.GetEngine();
        Method cmp;
        try { cmp = engine.getClass().getMethod("Compare", Fmd.class, int.class, Fmd.class, int.class); }
        catch (NoSuchMethodException e) { return null; }
        int va = getViewCount(a);
        int vb = getViewCount(b);
        Integer best = null;
        for (int i = 0; i < va; i++) {
            for (int j = 0; j < vb; j++) {
                int score = (int) cmp.invoke(engine, a, i, b, j);
                if (best == null || score < best) best = score;
            }
        }
        return best;
    }

    public static int getViewCount(Fmd f) {
        try { Method m = Fmd.class.getMethod("getViews"); Object arr = m.invoke(f);
            if (arr != null && arr.getClass().isArray()) return java.lang.reflect.Array.getLength(arr); } catch (Throwable ignore) {}
        try { Method m = Fmd.class.getMethod("getViewCount"); Object ret = m.invoke(f);
            if (ret instanceof Integer) return (Integer) ret; } catch (Throwable ignore) {}
        return 1;
    }

    public static Integer compareScore(Fmd a, Fmd b) throws Exception {
        Object engine = UareUGlobal.GetEngine();
        try {
            Method cmp = engine.getClass().getMethod("Compare", Fmd.class, int.class, Fmd.class, int.class);
            return (int) cmp.invoke(engine, a, 0, b, 0);
        } catch (NoSuchMethodException ignore) {}
        return null;
    }

    public static boolean matches(Fmd probe, Fmd reference, int threshold) throws Exception {
        Integer score = bestScoreAllViews(probe, reference);
        if (score != null) return score < threshold;
        Integer score0 = compareScore(probe, reference);
        if (score0 != null) return score0 < threshold;
        Object engine = UareUGlobal.GetEngine();
        try {
            Method id = engine.getClass().getMethod("Identify", Fmd.class, int.class, Fmd[].class, int.class, int.class);
            Object res = id.invoke(engine, probe, 0, new Fmd[]{ reference }, 1, threshold);
            if (res == null) return false;
            if (res.getClass().isArray()) {
                return java.lang.reflect.Array.getLength(res) > 0;
            }
            return true;
        } catch (NoSuchMethodException ignore) {}
        throw new UnsupportedOperationException("No encontré métodos Compare/Identify compatibles en este SDK.");
    }

    public static List<Fmd> captureSamples(Reader r, int samples, java.util.function.Consumer<String> status) throws Exception {
        List<Fmd> out = new ArrayList<>();
        for (int i = 1; i <= samples; i++) {
            if (status != null) status.accept("Coloca tu dedo (" + i + "/" + samples + ")...");
            Fid fid = capture(r, 10_000);
            if (status != null) status.accept("Imagen #" + i + " capturada. Levanta el dedo...");
            Thread.sleep(800);
            out.add(createFmd(fid));
            if (status != null) status.accept("Muestra #" + i + " lista.");
            Thread.sleep(400);
        }
        return out;
    }
}
