package org.commercialtracking;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class BrowserServer {
    private final AppConfig config;
    private final BarcodeParserChain parser = new BarcodeParserChain();
    private final Projection projection = new Projection();
    private final List<TrackingEvent> events = new ArrayList<TrackingEvent>();
    private final List<TrackingEvent> session = new ArrayList<TrackingEvent>();
    private final List<String> errors = new ArrayList<String>();
    private final String sessionId = UUID.randomUUID().toString();
    private final String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private EventStore store;
    private HttpServer server;
    private String origin;

    public BrowserServer(AppConfig config) throws IOException {
        this.config = config;
        if (config.sharedRoot != null && Files.isDirectory(config.sharedRoot)) {
            configureStore(config.sharedRoot);
        }
    }

    public void startAndOpen() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        int port = server.getAddress().getPort();
        origin = "http://127.0.0.1:" + port;
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "commercial-tracking-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        URI uri = URI.create(origin + "/");
        System.out.println("Commercial Tracking UI: " + uri);
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            throw new IOException("No supported system browser was found. Run with --classic-ui.");
        }
        stopped.await();
    }

    private synchronized void configureStore(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("The folder does not exist.");
        store = new EventStore(root, config.localRoot);
        config.sharedRoot = root.toAbsolutePath().normalize();
        config.save();
        reload();
    }

    private synchronized void reload() {
        events.clear();
        errors.clear();
        if (store == null) {
            projection.replay(events);
            return;
        }
        EventStore.LoadResult loaded = store.loadAll();
        events.addAll(loaded.events);
        errors.addAll(loaded.errors);
        projection.replay(events);
    }

    private synchronized Map<String, Object> state() {
        reload();
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("configured", store != null);
        value.put("sharedRoot", config.sharedRoot == null ? "" : config.sharedRoot.toString());
        value.put("deviceId", config.deviceId);
        value.put("actor", config.actor);
        value.put("eventCount", events.size());
        value.put("refreshedUtc", Instant.now().toString());
        value.put("session", eventMaps(session));
        value.put("packages", packageMaps(projection.all()));
        value.put("conflicts", projection.conflicts());
        value.put("errors", new ArrayList<String>(errors));
        return value;
    }

    private synchronized Map<String, Object> scan(Map<String, String> request) throws IOException {
        requireConfigured();
        String raw = request.get("raw");
        String mode = value(request, "mode", "Inbound");
        String location = value(request, "location", "");
        String recipient = value(request, "recipient", "");
        boolean confirmed = "true".equalsIgnoreCase(value(request, "confirmed", "false"));
        if ("Inbound".equals(mode) && location.length() == 0) throw new BadRequest("Select a receiving location.");

        ParseResult parsed = parser.parse(raw);
        if (!parsed.isSuccess()) throw new BadRequest("No supported tracking number was found.");
        if (parsed.isConfirmationRequired() && !confirmed) {
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("confirmationRequired", true);
            response.put("trackingNumber", parsed.getTrackingNumber());
            response.put("carrier", parsed.getCarrier());
            response.put("confidence", parsed.getConfidence().name());
            response.put("source", parsed.getSource());
            return response;
        }

        PackageState current = projection.find(parsed.getTrackingNumber());
        TrackingEvent event = baseEvent(parsed, location, recipient);
        String kind;
        String message;
        if ("Outbound".equals(mode)) {
            if (current == null || !"READY_FOR_PICKUP".equals(current.status)) {
                throw new BadRequest("No active package awaiting pickup was found in the synchronized event view.");
            }
            event.eventType = "PACKAGE_RELEASED";
            event.status = "PICKED_UP";
            event.location = current.location;
            if (event.recipient.length() == 0) event.recipient = current.recipient;
            event.notes = "Outbound release";
            kind = "SUCCESS";
            message = "Package released: " + event.trackingNumber;
        } else if (current != null && "READY_FOR_PICKUP".equals(current.status)) {
            event.eventType = "PACKAGE_LOCATION_CHANGED";
            event.status = "READY_FOR_PICKUP";
            event.notes = "Repeat inbound scan; active package retained";
            kind = "WARNING";
            message = "Already active. Location update submitted for " + event.trackingNumber + ".";
        } else {
            event.eventType = "PACKAGE_RECEIVED";
            event.status = "READY_FOR_PICKUP";
            event.notes = value(parsed.getMetadata(), "labelType", "");
            kind = "SUCCESS";
            message = "Package received: " + event.trackingNumber;
        }
        store.append(event);
        session.add(event);
        reload();
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("confirmationRequired", false);
        response.put("kind", kind);
        response.put("message", message);
        response.put("eventId", event.eventId);
        return response;
    }

    private TrackingEvent baseEvent(ParseResult parsed, String location, String recipient) {
        TrackingEvent event = new TrackingEvent();
        event.deviceId = config.deviceId;
        event.sessionId = sessionId;
        event.actor = config.actor;
        event.trackingNumber = parsed.getTrackingNumber();
        event.carrier = parsed.getCarrier();
        event.location = location;
        event.streamId = location.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        event.recipient = recipient;
        event.parserSource = parsed.getSource();
        event.parserConfidence = parsed.getConfidence().name();
        event.weight = value(parsed.getMetadata(), "weight", "");
        event.packageCount = value(parsed.getMetadata(), "packageCount", "");
        event.addressee = value(parsed.getMetadata(), "addressee", "");
        event.address = value(parsed.getMetadata(), "shipToAddress1", "");
        return event;
    }

    private synchronized Map<String, Object> assignRecipient(Map<String, String> request) throws IOException {
        requireConfigured();
        String tracking = required(request, "trackingNumber");
        String recipient = required(request, "recipient");
        PackageState current = projection.find(tracking);
        if (current == null) throw new BadRequest("Package was not found.");
        TrackingEvent event = manualEvent("RECIPIENT_ASSIGNED", current, recipient, "Recipient assigned");
        store.append(event);
        session.add(event);
        reload();
        return message("Recipient assignment submitted.");
    }

    private synchronized Map<String, Object> voidPackage(Map<String, String> request) throws IOException {
        requireConfigured();
        String tracking = required(request, "trackingNumber");
        String reason = required(request, "reason");
        PackageState current = projection.find(tracking);
        if (current == null || "VOIDED".equals(current.status)) throw new BadRequest("Package is not active or is already voided.");
        TrackingEvent event = manualEvent("PACKAGE_VOIDED", current, current.recipient, "Void reason: " + reason);
        event.status = "VOIDED";
        store.append(event);
        session.add(event);
        reload();
        return message("Package void submitted.");
    }

    private synchronized Map<String, Object> manifest(Map<String, String> request) throws IOException {
        requireConfigured();
        List<TrackingEvent> inbound = new ArrayList<TrackingEvent>();
        for (TrackingEvent event : session) {
            if ("PACKAGE_RECEIVED".equals(event.eventType) || "PACKAGE_LOCATION_CHANGED".equals(event.eventType)) {
                inbound.add(event);
            }
        }
        if (inbound.isEmpty()) throw new BadRequest("No inbound events are present in this session.");
        Path output = new ManifestWriter().write(store.getSharedRoot(), value(request, "location", ""), inbound);
        Map<String, Object> response = message("Manifest created.");
        response.put("fileName", output.getFileName().toString());
        return response;
    }

    private TrackingEvent manualEvent(String type, PackageState current, String recipient, String notes) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = type;
        event.deviceId = config.deviceId;
        event.sessionId = sessionId;
        event.actor = config.actor;
        event.trackingNumber = current.trackingNumber;
        event.carrier = current.carrier;
        event.location = current.location;
        event.streamId = current.location.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        event.recipient = recipient;
        event.status = current.status;
        event.notes = notes;
        event.parserSource = "MANUAL_WORKFLOW";
        event.parserConfidence = "VERIFIED";
        return event;
    }

    private void requireConfigured() {
        if (store == null) throw new BadRequest("Configure an empty synchronized pilot folder first.");
    }

    private static List<Map<String, Object>> eventMaps(List<TrackingEvent> source) {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (int i = source.size() - 1; i >= 0; i--) {
            TrackingEvent event = source.get(i);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.putAll(event.fields());
            values.add(row);
        }
        return values;
    }

    private static List<Map<String, Object>> packageMaps(List<PackageState> source) {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (PackageState state : source) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("trackingNumber", state.trackingNumber);
            row.put("carrier", state.carrier);
            row.put("location", state.location);
            row.put("recipient", state.recipient);
            row.put("status", state.status);
            row.put("lastEventUtc", state.lastEventUtc);
            row.put("lastDevice", state.lastDevice);
            row.put("revision", state.revision);
            values.add(row);
        }
        return values;
    }

    private static Map<String, Object> message(String text) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("message", text);
        return response;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim().length() == 0) throw new BadRequest("Missing " + key + ".");
        return value.trim();
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value.trim();
    }

    private final class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            try {
                secure(exchange);
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                Map<String, Object> response;
                if ("/api/state".equals(path) && "GET".equals(method)) {
                    response = state();
                } else if ("/api/scan".equals(path) && "POST".equals(method)) {
                    response = scan(body(exchange));
                } else if ("/api/configure".equals(path) && "POST".equals(method)) {
                    String root = required(body(exchange), "sharedRoot");
                    configureStore(Paths.get(root));
                    response = message("Shared folder configured.");
                } else if ("/api/recipient".equals(path) && "POST".equals(method)) {
                    response = assignRecipient(body(exchange));
                } else if ("/api/void".equals(path) && "POST".equals(method)) {
                    response = voidPackage(body(exchange));
                } else if ("/api/manifest".equals(path) && "POST".equals(method)) {
                    response = manifest(body(exchange));
                } else if ("/api/shutdown".equals(path) && "POST".equals(method)) {
                    response = message("Shutting down.");
                    send(exchange, 200, response);
                    new Thread(() -> stop(), "commercial-tracking-shutdown").start();
                    return;
                } else {
                    send(exchange, 404, message("Not found."));
                    return;
                }
                send(exchange, 200, response);
            } catch (BadRequest ex) {
                send(exchange, 400, message(ex.getMessage()));
            } catch (Exception ex) {
                send(exchange, 500, message("Operation failed: " + ex.getMessage()));
            }
        }

        private void secure(HttpExchange exchange) {
            String supplied = exchange.getRequestHeaders().getFirst("X-Session-Token");
            if (!token.equals(supplied)) throw new BadRequest("Invalid local session token.");
            String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
            if (requestOrigin != null && !origin.equals(requestOrigin)) throw new BadRequest("Invalid request origin.");
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
                String html = new String(bytes, StandardCharsets.UTF_8).replace("__SESSION_TOKEN__", token);
                bytes = html.getBytes(StandardCharsets.UTF_8);
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(path));
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Cache-Control", path.startsWith("/assets/") ? "public, max-age=31536000, immutable" : "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private void stop() {
        if (server != null) server.stop(0);
        stopped.countDown();
    }

    private static Map<String, String> body(HttpExchange exchange) throws IOException {
        byte[] bytes = readBounded(exchange.getRequestBody(), 1024 * 1024);
        return JsonFlat.read(new String(bytes, StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, Map<String, Object> value) throws IOException {
        byte[] bytes = JsonOutput.write(value).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static byte[] resource(String name) throws IOException {
        InputStream in = BrowserServer.class.getResourceAsStream(name);
        if (in == null) return null;
        try { return readBounded(in, 5 * 1024 * 1024); }
        finally { in.close(); }
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
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) { super(message); }
    }
}
