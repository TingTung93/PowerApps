package org.medsupply;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class BrowserServer {
    private final AppService service;
    private final AppConfig config;
    private final String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private HttpServer server;
    private String origin;

    public BrowserServer(AppService service, AppConfig config) {
        this.service = service;
        this.config = config;
    }

    public String token() {
        return token;
    }

    public String start() throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "medsupply-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return origin;
    }

    public void startAndOpen() throws Exception {
        start();
        URI uri = URI.create(origin + "/");
        System.out.println("Medical Supply UI: " + uri);
        if (Boolean.getBoolean("medsupply.noDesktop")) {
            // Automated/smoke runs attach a browser explicitly.
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            throw new IOException("No supported system browser was found. Run with --classic-ui.");
        }
        stopped.await();
    }

    public void stop() {
        if (server != null) server.stop(0);
        server = null;
        stopped.countDown();
    }

    private Map<String, Object> route(String path, String method, Map<String, String> body) throws IOException {
        Instant now = Instant.now();
        if ("/api/state".equals(path) && "GET".equals(method)) {
            return service.snapshot(now);
        }
        if ("/api/configure".equals(path)) {
            service.configure(Paths.get(required(body, "sharedRoot")));
            return message("Shared folder configured.");
        }
        if ("/api/receive".equals(path)) {
            return service.receive(required(body, "raw"), intValue(body, "quantity", 1),
                    "true".equalsIgnoreCase(value(body, "force", "false")));
        }
        if ("/api/pick".equals(path)) {
            return service.pick(required(body, "gtin"), value(body, "lot", ""),
                    value(body, "expirationIso", ""), intValue(body, "quantity", 1));
        }
        if ("/api/adjust".equals(path)) {
            return service.adjust(required(body, "gtin"), value(body, "lot", ""),
                    value(body, "expirationIso", ""), intValue(body, "quantity", 0));
        }
        if ("/api/archive".equals(path)) {
            return service.archive(required(body, "gtin"), value(body, "lot", ""),
                    value(body, "expirationIso", ""), required(body, "reason"));
        }
        if ("/api/register".equals(path)) {
            return service.registerProduct(required(body, "gtin"), required(body, "name"),
                    value(body, "manufacturer", ""), value(body, "category", ""),
                    doubleValue(body, "unitPrice"), intValue(body, "par", -1),
                    value(body, "notes", ""), value(body, "source", "MANUAL"));
        }
        if ("/api/gudid".equals(path)) {
            return service.lookupGudid(required(body, "gtin"));
        }
        if ("/api/report".equals(path)) {
            if (!service.configured()) throw new AppService.BadRequest("Configure a folder first.");
            ManagementReport.Result result = ManagementReport.write(
                    service.store().getSharedRoot().resolve("reports"),
                    service.dashboard(now), service.reorder(now), service.stock(), now);
            Map<String, Object> response = message("Management report written.");
            response.put("htmlFile", result.html.getFileName().toString());
            response.put("csvFile", result.csv.getFileName().toString());
            response.put("pdfFile", result.pdf.getFileName().toString());
            return response;
        }
        if ("/api/shutdown".equals(path)) {
            new Thread(this::stop, "medsupply-shutdown").start();
            return message("Shutting down.");
        }
        return null;
    }

    private final class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            try {
                secure(exchange);
                Map<String, String> body = "GET".equals(exchange.getRequestMethod())
                        ? new LinkedHashMap<String, String>() : body(exchange);
                Map<String, Object> response = route(exchange.getRequestURI().getPath(),
                        exchange.getRequestMethod(), body);
                if (response == null) { send(exchange, 404, message("Not found.")); return; }
                send(exchange, 200, response);
            } catch (AppService.BadRequest ex) {
                send(exchange, 400, message(ex.getMessage()));
            } catch (Exception ex) {
                send(exchange, 500, message("Operation failed: " + ex.getMessage()));
            }
        }

        private void secure(HttpExchange exchange) {
            String supplied = exchange.getRequestHeaders().getFirst("X-Session-Token");
            if (!token.equals(supplied)) throw new AppService.BadRequest("Invalid local session token.");
            String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
            if (requestOrigin != null && !origin.equals(requestOrigin))
                throw new AppService.BadRequest("Invalid request origin.");
        }
    }

    private final class StaticHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";
            if (path.contains("..")) { exchange.sendResponseHeaders(404, -1); return; }
            byte[] bytes = resource("/web" + path);
            if (bytes == null) { exchange.sendResponseHeaders(404, -1); return; }
            if ("/index.html".equals(path)) {
                bytes = new String(bytes, StandardCharsets.UTF_8)
                        .replace("__SESSION_TOKEN__", token).getBytes(StandardCharsets.UTF_8);
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(path));
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static byte[] resource(String name) throws IOException {
        InputStream in = BrowserServer.class.getResourceAsStream(name);
        if (in == null) return null;
        try { return readBounded(in, 5 * 1024 * 1024); } finally { in.close(); }
    }

    private static Map<String, String> body(HttpExchange exchange) throws IOException {
        byte[] bytes = readBounded(exchange.getRequestBody(), 1024 * 1024);
        Map<String, Object> parsed = Json.asMap(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
        Map<String, String> flat = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) flat.put(entry.getKey(), Json.str(parsed, entry.getKey()));
        return flat;
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> value) throws IOException {
        byte[] bytes = Json.write(value).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static byte[] readBounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Input exceeds limit.");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        return "application/octet-stream";
    }

    private static Map<String, Object> message(String text) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("message", text);
        return response;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim().length() == 0) throw new AppService.BadRequest("Missing " + key + ".");
        return value.trim();
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value.trim();
    }

    private static int intValue(Map<String, String> values, String key, int fallback) {
        try { return Integer.parseInt(value(values, key, String.valueOf(fallback))); }
        catch (NumberFormatException ex) { throw new AppService.BadRequest("Invalid " + key + "."); }
    }

    private static double doubleValue(Map<String, String> values, String key) {
        try { return Double.parseDouble(value(values, key, "0")); }
        catch (NumberFormatException ex) { throw new AppService.BadRequest("Invalid " + key + "."); }
    }
}
