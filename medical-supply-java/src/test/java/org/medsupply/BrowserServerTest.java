package org.medsupply;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class BrowserServerTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-http");
        System.setProperty("medsupply.localBase", base.toString());
        System.setProperty("medsupply.noDesktop", "true");
        AppConfig config = AppConfig.load();
        AppService svc = new AppService(config, null);
        Path selected = base.resolve("shared");
        BrowserServer server = new BrowserServer(svc, config, initial -> selected);
        String origin = server.start();
        try {
            Map<String, Object> configure = post(
                    origin, "/api/choose-folder", server.token(), "{}");
            check(configure.containsKey("message"), "configure ok");
            check(selected.toAbsolutePath().normalize().toString().equals(
                    Json.str(configure, "sharedRoot")), "selected path returned");

            post(origin, "/api/register", server.token(),
                    "{\"gtin\":\"00380740000010\",\"name\":\"Stent\",\"manufacturer\":\"Abbott\","
                    + "\"category\":\"Coronary stent\",\"unitPrice\":\"10\",\"par\":\"4\","
                    + "\"notes\":\"\",\"source\":\"MANUAL\"}");
            post(origin, "/api/receive", server.token(),
                    "{\"raw\":\"010038074000001017261130" + "10L1\",\"quantity\":\"5\",\"force\":\"false\"}");

            Map<String, Object> state = get(origin, "/api/state", server.token());
            check(Json.asList(state.get("stock")).size() == 1, "one stock line via api");

            // Missing token is rejected.
            int status = statusOf(origin, "/api/state", null);
            check(status == 400 || status == 403, "missing token rejected: " + status);
        } finally {
            server.stop();
        }
        System.out.println("BrowserServerTest: PASS");
    }

    private static Map<String, Object> get(String origin, String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + path).openConnection();
        c.setRequestMethod("GET");
        if (token != null) c.setRequestProperty("X-Session-Token", token);
        return Json.asMap(Json.parse(read(c)));
    }

    private static Map<String, Object> post(String origin, String path, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + path).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("X-Session-Token", token);
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
        return Json.asMap(Json.parse(read(c)));
    }

    private static int statusOf(String origin, String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(origin + path).openConnection();
        c.setRequestMethod("GET");
        if (token != null) c.setRequestProperty("X-Session-Token", token);
        return c.getResponseCode();
    }

    private static String read(HttpURLConnection c) throws Exception {
        java.io.InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
