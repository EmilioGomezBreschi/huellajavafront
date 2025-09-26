package app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cliente para tu backend. Enrolamiento + listado de huellas + creación de operador. */
public final class FingerApi {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Ajusta si tu dominio/base cambia. */
    private static final String BASE = "https://lector-hxgfchbqfec8fxcd.canadacentral-01.azurewebsites.net";
    private static final String PATH_TEMPLATE = "/api/huella/%d/huella";
    private static final String PATH_LIST = "/api/huella";
    private static final String PATH_OPERADORES = "/api/operadores";

    private FingerApi() {}

    public static class FingerprintRecord {
        public final int idOperador;
        public final String fmdBase64;  // puede venir en 'fmd' en tu JSON
        public final String fmdFormat;
        public FingerprintRecord(int idOperador, String fmdBase64, String fmdFormat) {
            this.idOperador = idOperador;
            this.fmdBase64 = fmdBase64;
            this.fmdFormat = fmdFormat;
        }
    }

    /** POST /api/operadores → crea el operador; el backend asigna idOperador (autoincrement). */
    public static int createOperador(String nombre, String apellidoPaterno, String apellidoMaterno, String numeroOperador) throws Exception {
        // Construimos JSON sin idOperador ni fingerId (los maneja el backend)
        String json = "{"
                + "\"nombre\":\"" + jsonEsc(nombre) + "\","
                + "\"apellidoPaterno\":\"" + jsonEsc(apellidoPaterno) + "\","
                + "\"apellidoMaterno\":\"" + jsonEsc(apellidoMaterno) + "\","
                + "\"numeroOperador\":\"" + jsonEsc(numeroOperador) + "\""
                + "}";

        HttpResponse<String> resp = sendJson("POST", BASE + PATH_OPERADORES, json);
        if (resp.statusCode() / 100 != 2 && resp.statusCode() != 201) {
            throw new IllegalStateException("POST /api/operadores -> " + resp.statusCode() + " " + acorta(resp.body()));
        }
        // Intentamos leer idOperador del body (en cualquiera de estos nombres)
        Integer id = extractIntJson(resp.body(), "idOperador");
        if (id == null) id = extractIntJson(resp.body(), "id_operador");
        if (id == null) id = extractIntJson(resp.body(), "operadorId");
        if (id == null) throw new IllegalStateException("No se encontró idOperador en la respuesta: " + acorta(resp.body()));
        return id;
    }

    /** Enrolamiento: envía la huella del operador (fingerId lo asigna el backend). */
    public static String enroll(int idOperador, String fmdBase64, String fmdFormat) throws Exception {
        String path = String.format(PATH_TEMPLATE, idOperador);
        String json = "{"
                + "\"fmdBase64\":\"" + jsonEsc(fmdBase64) + "\","
                + "\"fmdFormat\":\"" + jsonEsc(fmdFormat) + "\""
                + "}";
        HttpResponse<String> resp = sendJson("POST", BASE + path, json);
        int sc = resp.statusCode();
        if (sc == 404 || sc == 405) {
            HttpResponse<String> resp2 = sendJson("PUT", BASE + path, json);
            return "[POST->" + sc + "] [PUT->" + resp2.statusCode() + "] " + resp2.body();
        }
        return sc + " " + resp.body();
    }

    /** GET /api/huella/{id}/huella (opcional para debug). */
    public static String getHuella(int idOperador) throws Exception {
        String path = String.format(PATH_TEMPLATE, idOperador);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET().header("Accept", "application/json")
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return resp.statusCode() + " " + resp.body();
    }

    /** GET /api/huella -> lista con (idOperador, fmd|fmdBase64, fmdFormat). */
    public static List<FingerprintRecord> listAll() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + PATH_LIST))
                .GET().header("Accept", "application/json")
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) throw new IllegalStateException("GET /api/huella -> " + resp.statusCode());
        return parseFingerprintArray(resp.body());
    }

    // ---------- Helpers ----------
    private static HttpResponse<String> sendJson(String method, String url, String json) throws Exception {
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8");
        if ("POST".equalsIgnoreCase(method)) b = b.POST(body);
        else if ("PUT".equalsIgnoreCase(method)) b = b.PUT(body);
        else throw new IllegalArgumentException("Método no soportado: " + method);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String acorta(String s) {
        if (s == null) return "—";
        s = s.replaceAll("\\s+", " ");
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /** Parser robusto para tu JSON (acepta 'fmd' o 'fmdBase64'; 'fmdFormat' opcional). */
    private static List<FingerprintRecord> parseFingerprintArray(String json) {
        List<FingerprintRecord> out = new ArrayList<>();
        if (json == null) return out;

        int i = json.indexOf('[');
        int j = json.lastIndexOf(']');
        String array = (i >= 0 && j > i) ? json.substring(i + 1, j) : json;

        List<String> objs = splitTopLevelObjects(array);
        for (String obj : objs) {
            Integer idOperador = extractInt(obj, "idOperador");
            if (idOperador == null) idOperador = extractInt(obj, "id_operador");
            if (idOperador == null) idOperador = extractInt(obj, "operadorId");
            if (idOperador == null) continue;

            String fmd = extractString(obj, "fmdBase64");
            if (fmd == null) fmd = extractString(obj, "fmd");
            if (fmd == null || fmd.isEmpty()) continue;

            String fmt = extractString(obj, "fmdFormat");
            if (fmt == null || fmt.isEmpty()) fmt = "ANSI_378_2004";

            out.add(new FingerprintRecord(idOperador, fmd, fmt));
        }
        return out;
    }

    private static List<String> splitTopLevelObjects(String array) {
        List<String> out = new ArrayList<>();
        if (array == null) return out;
        int level = 0; boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        for (int k = 0; k < array.length(); k++) {
            char c = array.charAt(k);
            if (inStr) {
                cur.append(c);
                if (c == '\\' && k + 1 < array.length()) { cur.append(array.charAt(++k)); }
                else if (c == '\"') inStr = false;
                continue;
            }
            if (c == '\"') { inStr = true; cur.append(c); continue; }
            if (c == '{')  { level++; cur.append(c); continue; }
            if (c == '}')  { level--; cur.append(c); if (level == 0) { out.add(cur.toString()); cur.setLength(0); } continue; }
            if (level > 0) cur.append(c);
        }
        return out;
    }

    private static Integer extractInt(String obj, String key) {
        String s = extractString(obj, key);
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException nfe) { return null; }
    }

    private static String extractString(String obj, String key) {
        String k = "\"" + key.toLowerCase(Locale.ROOT) + "\"";
        int i = obj.toLowerCase(Locale.ROOT).indexOf(k);
        if (i < 0) return null;
        int colon = obj.indexOf(':', i + k.length());
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;

        if (p < obj.length() && obj.charAt(p) == '\"') {
            StringBuilder sb = new StringBuilder();
            p++;
            while (p < obj.length()) {
                char c = obj.charAt(p++);
                if (c == '\\') {
                    if (p < obj.length()) {
                        char n = obj.charAt(p++);
                        switch (n) {
                            case '\"': sb.append('\"'); break;
                            case '\\': sb.append('\\'); break;
                            case '/':  sb.append('/');  break;
                            case 'b':  sb.append('\b'); break;
                            case 'f':  sb.append('\f'); break;
                            case 'n':  sb.append('\n'); break;
                            case 'r':  sb.append('\r'); break;
                            case 't':  sb.append('\t'); break;
                            case 'u':
                                if (p + 4 <= obj.length()) {
                                    String hex = obj.substring(p, p + 4);
                                    try { sb.append((char) Integer.parseInt(hex, 16)); } catch (Exception ignore) {}
                                    p += 4;
                                }
                                break;
                            default: sb.append(n);
                        }
                    }
                } else if (c == '\"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            int end = p;
            while (end < obj.length()) {
                char c = obj.charAt(end);
                if (c == ',' || c == '}' || c == ']') break;
                end++;
            }
            return obj.substring(p, end).trim();
        }
    }

    private static Integer extractIntJson(String json, String key) {
        if (json == null) return null;
        String lower = json.toLowerCase(Locale.ROOT);
        String k = "\"" + key.toLowerCase(Locale.ROOT) + "\"";
        int i = lower.indexOf(k);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + k.length());
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        int end = p;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ']') break;
            end++;
        }
        try { return Integer.parseInt(json.substring(p, end).trim()); } catch (Exception e) { return null; }
    }
}
