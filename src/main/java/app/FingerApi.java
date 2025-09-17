package app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cliente HTTP para enrolar y verificar huellas, con strings JSON sin escapes raros. */
public final class FingerApi {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // Cambia esto si tu dominio/base cambia
    private static final String BASE = "https://lector-hxgfchbqfec8fxcd.canadacentral-01.azurewebsites.net";
    private static final String PATH_TEMPLATE = "/api/huella/%d/huella";
    private static final String PATH_LIST = "/api/huella";

    private FingerApi() {}

    // ===== Tipos =====
    public static class FingerprintRecord {
        public final int idOperador;
        public final String fmdBase64;
        public final String fmdFormat;
        public FingerprintRecord(int idOperador, String fmdBase64, String fmdFormat) {
            this.idOperador = idOperador;
            this.fmdBase64 = fmdBase64;
            this.fmdFormat = fmdFormat;
        }
    }

    // ===== Enrolar =====
    public static String enroll(int idOperador, String fmdBase64, String fmdFormat) throws Exception {
        String path = String.format(PATH_TEMPLATE, idOperador);
        String json = buildEnrollJson(fmdBase64, fmdFormat);

        // Intento POST
        HttpResponse<String> resp = sendJson("POST", BASE + path, json);
        int sc = resp.statusCode();
        if (sc == 404 || sc == 405) {
            // fallback PUT
            HttpResponse<String> resp2 = sendJson("PUT", BASE + path, json);
            return "[POST->" + sc + "] [PUT->" + resp2.statusCode() + "] " + resp2.body();
        }
        return sc + " " + resp.body();
    }

    // ===== GET uno =====
    public static String getHuella(int idOperador) throws Exception {
        String path = String.format(PATH_TEMPLATE, idOperador);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return resp.statusCode() + " " + resp.body();
    }

    // ===== GET lista =====
    public static List<FingerprintRecord> listAll() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + PATH_LIST))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("GET /api/huella -> " + resp.statusCode() + " " + resp.body());
        }
        return parseFingerprintArray(resp.body());
    }

    // ===== Helpers =====
    private static HttpResponse<String> sendJson(String method, String url, String json) throws Exception {
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8");
        if ("POST".equalsIgnoreCase(method)) {
            b = b.POST(body);
        } else if ("PUT".equalsIgnoreCase(method)) {
            b = b.PUT(body);
        } else {
            throw new IllegalArgumentException("Método no soportado: " + method);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String buildEnrollJson(String fmdBase64, String fmdFormat) {
        return "{"
                + "\"fmdBase64\":\"" + jsonEsc(fmdBase64) + "\","
                + "\"fmdFormat\":\"" + jsonEsc(fmdFormat) + "\""
                + "}";
    }

    /** Escapado JSON mínimo para strings. */
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // ===== Parser simple del array JSON =====
    private static final Pattern OBJ = Pattern.compile("\\{[^\\{\\}]*\\}");
    private static final Pattern P_ID = Pattern.compile("\"idOperador\"\\s*:\\s*(\\d+)");
    private static final Pattern P_FMD = Pattern.compile("\"(?:fmdBase64|fmd)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern P_FMT = Pattern.compile("\"fmdFormat\"\\s*:\\s*\"([^\"]*)\"");

    private static List<FingerprintRecord> parseFingerprintArray(String json) {
        List<FingerprintRecord> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;

        Matcher m = OBJ.matcher(json);
        while (m.find()) {
            String obj = m.group();
            Integer id = extractInt(obj, P_ID);
            if (id == null) continue;
            String b64 = extractString(obj, P_FMD);
            if (b64 == null || b64.isEmpty()) continue;
            String fmt = extractString(obj, P_FMT);
            if (fmt == null || fmt.isEmpty()) fmt = "ANSI_378_2004";
            out.add(new FingerprintRecord(id, b64, fmt));
        }
        return out;
    }

    private static Integer extractInt(String s, Pattern p) {
        Matcher m = p.matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignore) {}
        }
        return null;
    }

    private static String extractString(String s, Pattern p) {
        Matcher m = p.matcher(s);
        if (m.find()) {
            return unescapeJsonString(m.group(1));
        }
        return null;
    }

    /** Desescapa las secuencias básicas de un string JSON. */
    private static String unescapeJsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                if (i + 1 >= s.length()) break;
                char n = s.charAt(i + 1);
                switch (n) {
                    case '\"': sb.append('\"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case '/':  sb.append('/');  i++; break;
                    case 'b':  sb.append('\b'); i++; break;
                    case 'f':  sb.append('\f'); i++; break;
                    case 'n':  sb.append('\n'); i++; break;
                    case 'r':  sb.append('\r'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                                break;
                            } catch (Exception ignore) { /* cae a default */ }
                        }
                        // si falló, dejamos tal cual
                        sb.append('\\');
                        break;
                    default:
                        sb.append(n);
                        i++;
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
