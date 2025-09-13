package app;

import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUGlobal;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.Arrays;

public final class TemplateStore {
    private TemplateStore() {}

    private static Path dir() throws IOException {
        Path p = Path.of(System.getProperty("user.home"), ".huellitafd", "templates");
        Files.createDirectories(p);
        return p;
    }

    public static Path pathFor(String userId) throws IOException {
        return dir().resolve(userId + ".fmd");
    }

    public static void save(String userId, Fmd fmd) throws IOException {
        Files.write(pathFor(userId), fmd.getData(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static Fmd load(String userId) throws Exception {
        byte[] data = Files.readAllBytes(pathFor(userId));
        Fmd.Format fmt = Fmd.Format.ANSI_378_2004;

        Fmd f = tryStatic("fromBytes", data, fmt);
        if (f != null) return f;

        try {
            Constructor<?> c = Fmd.class.getDeclaredConstructor(byte[].class, Fmd.Format.class);
            c.setAccessible(true);
            return (Fmd) c.newInstance(data, fmt);
        } catch (NoSuchMethodException ignore) {}

        try {
            Constructor<?> c = Fmd.class.getDeclaredConstructor(byte[].class);
            c.setAccessible(true);
            return (Fmd) c.newInstance(data);
        } catch (NoSuchMethodException ignore) {}

        Fmd f3 = tryStatic("Deserialize", data, fmt);
        if (f3 != null) return f3;

        Fmd f4 = tryStatic("Import", data, fmt);
        if (f4 != null) return f4;

        Fmd f5 = tryStatic("ImportFmd", data, fmt);
        if (f5 != null) return f5;

        try {
            Object engine = UareUGlobal.GetEngine();
            Method m = engine.getClass().getMethod("ImportFmd", byte[].class, Fmd.Format.class);
            return (Fmd) m.invoke(engine, data, fmt);
        } catch (NoSuchMethodException ignore) {}

        try {
            Method getImporter = UareUGlobal.class.getMethod("GetImporter");
            Object importer = getImporter.invoke(null);
            Method importFmd = importer.getClass().getMethod("ImportFmd", byte[].class, Fmd.Format.class);
            return (Fmd) importFmd.invoke(importer, data, fmt);
        } catch (NoSuchMethodException ignore) {}

        dumpApiHints();
        throw new UnsupportedOperationException(
                "No encontré método para reconstruir Fmd en esta versión del SDK. " +
                        "Probadas: ctor(byte[],Format), ctor(byte[]), Fmd.Deserialize/Import/ImportFmd, " +
                        "Engine.ImportFmd, Importer.ImportFmd. Revisa consola para métodos disponibles.");
    }

    public static boolean exists(String userId) throws IOException {
        return Files.exists(pathFor(userId));
    }

    private static Fmd tryStatic(String name, byte[] data, Fmd.Format fmt) {
        try {
            Method m = Fmd.class.getMethod(name, byte[].class, Fmd.Format.class);
            return (Fmd) m.invoke(null, data, fmt);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void dumpApiHints() {
        System.out.println("=== Métodos públicos en Fmd ===");
        for (Method m : Fmd.class.getMethods()) {
            System.out.println(m.getName() + " " + Arrays.toString(m.getParameterTypes()));
        }
        try {
            Object eng = UareUGlobal.GetEngine();
            System.out.println("=== Métodos públicos en " + eng.getClass().getName() + " ===");
            for (Method m : eng.getClass().getMethods()) {
                System.out.println(m.getName() + " " + Arrays.toString(m.getParameterTypes()));
            }
        } catch (Throwable t) {
            System.out.println("No pude enumerar métodos de Engine: " + t);
        }
    }
}
