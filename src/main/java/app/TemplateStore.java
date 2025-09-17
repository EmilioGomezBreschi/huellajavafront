package app;

import com.digitalpersona.uareu.Fmd;

/** API simple para "normalizar" el formato usado en todo el sistema. */
public final class TemplateStore {
    private TemplateStore() {}

    /** Siempre que hables con el backend, usa este nombre de formato. */
    public static String formatName() {
        return Fmd.Format.ANSI_378_2004.name();
    }
}
