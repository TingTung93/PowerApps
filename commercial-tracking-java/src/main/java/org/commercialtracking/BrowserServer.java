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
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class BrowserServer {
    private final AppConfig config;
    private final BarcodeParserChain parser = new BarcodeParserChain();
    private final Projection projection = new Projection();
    private final List<TrackingEvent> events = new ArrayList<TrackingEvent>();
    private final List<TrackingEvent> session = new ArrayList<TrackingEvent>();
    private final List<String> errors = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();
    private String sessionId;
    private final String token = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private EventStore store;
    private SharedConfigManager sharedConfig;
    private HttpServer server;
    private String origin;
    private String lastRescanUtc = "";

    public BrowserServer(AppConfig config) throws IOException {
        this.config = config;
        this.sessionId = config.activeSessionId;
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
        startEventMonitor();
        URI uri = URI.create(origin + "/");
        System.out.println("Commercial Tracking UI: " + uri);
        if (Boolean.getBoolean("commercialtracking.noDesktop")) {
            // Qualification and automated smoke tests attach a browser explicitly.
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            throw new IOException("No supported system browser was found. Run with --classic-ui.");
        }
        stopped.await();
    }

    private synchronized void configureStore(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("The folder does not exist.");
        Path probe = root.resolve(".commercial-tracking-write-probe-" + UUID.randomUUID().toString());
        try {
            Files.write(probe, new byte[] { 1 });
        } finally {
            Files.deleteIfExists(probe);
        }
        store = new EventStore(root, config.localRoot);
        sharedConfig = new SharedConfigManager(root);
        config.sharedRoot = root.toAbsolutePath().normalize();
        config.save();
        reload();
    }

    private synchronized void reload() {
        events.clear();
        errors.clear();
        warnings.clear();
        if (store == null) {
            projection.replay(events);
            return;
        }
        EventStore.LoadResult loaded = store.loadAll();
        events.addAll(loaded.events);
        errors.addAll(loaded.errors);
        warnings.addAll(loaded.warnings);
        projection.replay(events);
        lastRescanUtc = Instant.now().toString();
        session.clear();
        for (TrackingEvent event : events) {
            if (sessionId.equals(event.sessionId)) session.add(event);
        }
    }

    private synchronized Map<String, Object> state() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("configured", store != null);
        value.put("sharedRoot", config.sharedRoot == null ? "" : config.sharedRoot.toString());
        value.put("deviceId", config.deviceId);
        value.put("actor", config.actor);
        value.put("eventCount", events.size());
        value.put("refreshedUtc", lastRescanUtc);
        value.put("pendingCount", store == null ? 0 : store.pendingCount());
        Map<String, Object> scanner = new LinkedHashMap<String, Object>();
        scanner.put("completionMode", config.scannerMode);
        scanner.put("terminator", config.scannerTerminator);
        scanner.put("idleDelayMs", config.scannerIdleMs);
        scanner.put("burstThresholdMs", config.scannerBurstMs);
        scanner.put("minimumLength", config.scannerMinimumLength);
        scanner.put("soundEnabled", config.soundEnabled);
        value.put("scannerSettings", scanner);
        value.put("defaultLocation", config.defaultLocation);
        SharedConfigManager.State shared = sharedConfig == null ? null : sharedConfig.reload();
        value.put("sharedSettings", shared == null ? new LinkedHashMap<String, String>() : shared.values);
        value.put("sharedSettingsError", shared == null ? "" : shared.error);
        value.put("session", sessionPackageMaps());
        value.put("sessionActivity", eventMaps(session));
        value.put("activity", eventMaps(events));
        value.put("sessionEventCount", session.size());
        value.put("packages", packageMaps(projection.all()));
        value.put("conflicts", projection.conflicts());
        value.put("errors", new ArrayList<String>(errors));
        value.put("warnings", new ArrayList<String>(warnings));
        List<String> attention = new ArrayList<String>();
        if (store != null && store.pendingCount() > 0)
            attention.add(store.pendingCount() + " locally durable event(s) are awaiting shared-folder finalization.");
        if (shared != null && shared.error.length() > 0) attention.add(shared.error);
        value.put("attention", attention);
        value.put("manifests", manifestMaps());
        return value;
    }

    private List<Map<String, Object>> manifestMaps() {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<String, Map<String, Object>>();
        for (TrackingEvent event : events) {
            if (event.manifestId.length() == 0) continue;
            Map<String, Object> item = grouped.get(event.manifestId);
            if (item == null) {
                item = new LinkedHashMap<String, Object>();
                item.put("manifestId", event.manifestId);
                item.put("type", "custody".equals(event.parserSource) ? "Recipient custody" : "Inbound receiving");
                item.put("location", "custody".equals(event.parserSource) ? event.addressee : event.location);
                item.put("preparedUtc", event.occurredUtc);
                item.put("count", 0);
                item.put("checksum", "");
                item.put("fileName", "");
                grouped.put(event.manifestId, item);
            }
            if ("MANIFEST_PREPARED".equals(event.eventType))
                item.put("count", ((Integer) item.get("count")) + 1);
            if ("MANIFEST_PRINTED".equals(event.eventType)) {
                item.put("checksum", event.notes);
                item.put("fileName", event.address);
            }
        }
        return new ArrayList<Map<String, Object>>(grouped.values());
    }

    private List<Map<String, Object>> sessionPackageMaps() {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        List<String> seen = new ArrayList<String>();
        for (int i = session.size() - 1; i >= 0; i--) {
            String tracking = session.get(i).trackingNumber;
            String key = tracking.toUpperCase();
            if (seen.contains(key)) continue;
            seen.add(key);
            PackageState state = projection.find(tracking);
            if (state == null) continue;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("trackingNumber", state.trackingNumber);
            row.put("carrier", state.carrier);
            row.put("location", state.location);
            row.put("recipient", state.recipient);
            row.put("status", state.status);
            row.put("lastEventUtc", state.lastEventUtc);
            row.put("lastDevice", state.lastDevice);
            row.put("revision", state.revision);
            row.put("lastEventId", state.lastEventId);
            row.put("manifestId", state.manifestId);
            values.add(row);
        }
        return values;
    }

    private synchronized Map<String, Object> scan(Map<String, String> request) throws IOException {
        requireConfigured();
        String raw = request.get("raw");
        String mode = value(request, "mode", "Inbound");
        String location = value(request, "location", "");
        String recipient = value(request, "recipient", "");
        boolean confirmed = "true".equalsIgnoreCase(value(request, "confirmed", "false"));
        String duplicateAction = value(request, "duplicateAction", "");
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
        event.rawBarcodeHash = "sha256:" + sha256(raw == null ? "" : raw);
        SharedConfigManager.State effectiveSettings = sharedConfig == null ? null : sharedConfig.reload();
        if (effectiveSettings != null && "true".equals(effectiveSettings.values.get("retainRawBarcode")))
            event.rawBarcode = raw == null ? "" : raw;
        String kind;
        String message;
        if ("Outbound".equals(mode)) {
            if (!confirmed) throw new BadRequest("Release requires package verification and explicit confirmation.");
            if (current == null || !"READY_FOR_PICKUP".equals(current.status)) {
                throw new BadRequest("No active package awaiting pickup was found in the synchronized event view.");
            }
            int observed = boundedInteger(request, "observedRevision", -1, -1, Integer.MAX_VALUE);
            if (observed != current.revision)
                throw new BadRequest("Package changed after verification. Scan it again and review the latest state.");
            event.eventType = "PACKAGE_RELEASED";
            event.status = "PICKED_UP";
            event.location = current.location;
            if (event.recipient.length() == 0) event.recipient = current.recipient;
            event.notes = "Outbound release";
            kind = "SUCCESS";
            message = "Package released: " + event.trackingNumber;
        } else if (current != null && "READY_FOR_PICKUP".equals(current.status)) {
            if (duplicateAction.length() == 0) {
                Map<String, Object> response = new LinkedHashMap<String, Object>();
                response.put("confirmationRequired", true);
                response.put("confirmationType", "duplicate");
                response.put("trackingNumber", current.trackingNumber);
                response.put("carrier", current.carrier);
                response.put("location", current.location);
                response.put("occurredUtc", current.lastEventUtc);
                return response;
            }
            if ("keep".equals(duplicateAction)) {
                Map<String, Object> response = message("Existing package retained. No new event was created.");
                response.put("confirmationRequired", false);
                response.put("kind", "WARNING");
                response.put("trackingNumber", current.trackingNumber);
                return response;
            }
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
        response.put("trackingNumber", event.trackingNumber);
        response.put("carrier", event.carrier);
        response.put("location", event.location);
        response.put("recipient", event.recipient);
        response.put("occurredUtc", event.occurredUtc);
        return response;
    }

    private synchronized Map<String, Object> lookup(Map<String, String> request) {
        requireConfigured();
        String raw = required(request, "raw");
        ParseResult parsed = parser.parse(raw);
        if (!parsed.isSuccess()) throw new BadRequest("No supported tracking number was found.");
        PackageState current = projection.find(parsed.getTrackingNumber());
        if (current == null) throw new BadRequest("Package was not found.");
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("trackingNumber", current.trackingNumber);
        response.put("carrier", current.carrier);
        response.put("location", current.location);
        response.put("recipient", current.recipient);
        response.put("status", current.status);
        response.put("receivedUtc", current.lastEventUtc);
        response.put("revision", current.revision);
        response.put("canRelease", "READY_FOR_PICKUP".equals(current.status) && current.recipient.length() > 0);
        if (current.recipient.length() == 0) response.put("blockReason", "Assign a recipient before release.");
        else if (!"READY_FOR_PICKUP".equals(current.status)) response.put("blockReason", "This package is not awaiting pickup.");
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
        PackageState current = projection.find(parsed.getTrackingNumber());
        event.observedRevision = current == null ? 0 : current.revision;
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
        TrackingEvent event = manualEvent("PACKAGE_RECIPIENT_ASSIGNED", current, recipient, "Recipient assigned");
        store.append(event);
        session.add(event);
        reload();
        return message("Recipient assignment submitted.");
    }

    private synchronized Map<String, Object> assignRecipients(Map<String, String> request) throws IOException {
        requireConfigured();
        String recipient = required(request, "recipient");
        String[] trackingValues = required(request, "trackingNumbers").split("\\|");
        List<PackageState> targets = new ArrayList<PackageState>();
        for (String tracking : trackingValues) {
            PackageState current = projection.find(tracking);
            if (current == null || !"READY_FOR_PICKUP".equals(current.status))
                throw new BadRequest("Bulk assignment stopped before saving because " + tracking + " is not active.");
            targets.add(current);
        }
        int saved = 0;
        for (PackageState current : targets) {
            try {
                TrackingEvent event = manualEvent("PACKAGE_RECIPIENT_ASSIGNED", current, recipient,
                        "Reviewed bulk recipient assignment");
                store.append(event);
                saved++;
            } catch (IOException ex) {
                reload();
                throw new IOException("Bulk assignment saved " + saved + " of " + targets.size()
                        + " events. Review the remaining packages before retrying.", ex);
            }
        }
        reload();
        Map<String, Object> response = message("Recipient assigned to " + saved + " packages.");
        response.put("savedCount", saved);
        return response;
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

    private synchronized Map<String, Object> correctPackage(Map<String, String> request) throws IOException {
        requireConfigured();
        String tracking = required(request, "trackingNumber");
        String reason = required(request, "reason");
        PackageState current = projection.find(tracking);
        if (current == null) throw new BadRequest("Package was not found.");
        int observed = boundedInteger(request, "observedRevision", current.revision, 0, Integer.MAX_VALUE);
        if (observed != current.revision) throw new BadRequest("Package changed since it was opened. Review the latest history before correcting it.");
        String newRecipient = value(request, "recipient", current.recipient);
        String newLocation = value(request, "location", current.location);
        if (newRecipient.equals(current.recipient) && newLocation.equals(current.location))
            throw new BadRequest("No package fields were changed.");
        TrackingEvent event = manualEvent("PACKAGE_CORRECTED", current, newRecipient,
                "Correction reason: " + reason + "; previous location=" + current.location
                        + "; previous recipient=" + current.recipient);
        event.location = newLocation;
        store.append(event);
        session.add(event);
        reload();
        return message("Package correction recorded.");
    }

    private synchronized Map<String, Object> resolveConflict(Map<String, String> request) throws IOException {
        requireConfigured();
        String tracking = required(request, "trackingNumber");
        String reason = required(request, "reason");
        PackageState current = projection.find(tracking);
        if (current == null || !"CONFLICT".equals(current.status)) throw new BadRequest("No unresolved conflict was found for this package.");
        TrackingEvent event = manualEvent("CONFLICT_RESOLVED", current, current.recipient,
                "Conflict resolution reason: " + reason);
        event.status = value(request, "acceptedStatus", "PICKED_UP");
        store.append(event);
        session.add(event);
        reload();
        return message("Conflict resolution recorded.");
    }

    private synchronized Map<String, Object> manifest(Map<String, String> request) throws IOException {
        requireConfigured();
        String type = value(request, "type", "inbound").toLowerCase();
        if (!"inbound".equals(type) && !"custody".equals(type)) throw new BadRequest("Invalid manifest type.");
        String requested = value(request, "trackingNumbers", "");
        List<PackageState> targets = new ArrayList<PackageState>();
        if (requested.length() > 0) {
            for (String tracking : requested.split("\\|")) {
                PackageState state = projection.find(tracking);
                if (state == null) throw new BadRequest("Package not found: " + tracking);
                targets.add(state);
            }
        } else {
            for (Map<String, Object> row : sessionPackageMaps()) {
                PackageState state = projection.find(String.valueOf(row.get("trackingNumber")));
                if (state != null && state.manifestId.length() == 0) targets.add(state);
            }
        }
        if (targets.isEmpty()) throw new BadRequest("No eligible packages were selected.");
        if (targets.size() > 100) throw new BadRequest("An audited manifest is limited to 100 packages. Split the selection into smaller batches.");
        for (PackageState state : targets) {
            for (TrackingEvent event : events) {
                if ("MANIFEST_PREPARED".equals(event.eventType)
                        && state.trackingNumber.equalsIgnoreCase(event.trackingNumber)
                        && type.equals(event.parserSource))
                    throw new BadRequest(state.trackingNumber + " is already assigned to an audited " + type + " manifest.");
            }
        }
        String scope = "custody".equals(type) ? targets.get(0).recipient : targets.get(0).location;
        if ("inbound".equals(type)) {
            for (PackageState state : targets)
                if (!scope.equals(state.location))
                    throw new BadRequest("Inbound manifests cannot combine locations in this release. Filter the selection to one location.");
        }
        if ("custody".equals(type)) {
            if (scope.length() == 0) throw new BadRequest("Custody manifests require an assigned recipient.");
            for (PackageState state : targets) {
                if (!scope.equals(state.recipient) || !"READY_FOR_PICKUP".equals(state.status))
                    throw new BadRequest("Every custody-manifest package must be active and assigned to the same recipient.");
            }
        }
        String manifestId = value(request, "manifestId", "");
        if (manifestId.length() == 0) manifestId = "MNF-"
                + Instant.now().toString().replaceAll("[^0-9]", "").substring(0, 14)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        if (!manifestId.matches("MNF-[0-9]{14}-[A-Z0-9]{6}")) throw new BadRequest("Invalid proposed manifest ID.");
        for (TrackingEvent event : events)
            if (manifestId.equals(event.manifestId)) throw new BadRequest("The proposed manifest ID is already finalized.");
        List<TrackingEvent> membership = new ArrayList<TrackingEvent>();
        for (PackageState current : targets) {
            TrackingEvent source = findEvent(current.lastEventId);
            if (source == null) throw new BadRequest("Package history is incomplete for " + current.trackingNumber);
            membership.add(source);
            TrackingEvent prepared = manualEvent("MANIFEST_PREPARED", current, current.recipient,
                    "Included package event " + current.lastEventId);
            prepared.manifestId = manifestId;
            prepared.referenceEventId = current.lastEventId;
            prepared.parserSource = type;
            prepared.addressee = scope;
            store.append(prepared);
        }
        String manifestZone = sharedConfig == null ? ZoneId.systemDefault().getId()
                : sharedConfig.reload().values.get("operationalTimeZone");
        ManifestWriter.Result output = new ManifestWriter().write(store.getSharedRoot(), manifestId,
                type, scope, manifestZone, membership);
        PackageState first = targets.get(0);
        TrackingEvent printed = manualEvent("MANIFEST_PRINTED", first, first.recipient, output.checksum);
        printed.manifestId = manifestId;
        printed.referenceEventId = first.lastEventId;
        printed.parserSource = type;
        printed.addressee = scope;
        printed.address = store.getSharedRoot().relativize(output.path).toString();
        store.append(printed);
        reload();
        Map<String, Object> response = message("Manifest created.");
        response.put("manifestId", manifestId);
        response.put("fileName", output.path.getFileName().toString());
        response.put("checksum", output.checksum);
        return response;
    }

    private TrackingEvent findEvent(String eventId) {
        for (TrackingEvent event : events) if (event.eventId.equals(eventId)) return event;
        return null;
    }

    private synchronized Map<String, Object> reprintManifest(Map<String, String> request) throws IOException {
        requireConfigured();
        String manifestId = required(request, "manifestId");
        TrackingEvent printed = null;
        for (TrackingEvent event : events) {
            if ("MANIFEST_PRINTED".equals(event.eventType) && manifestId.equals(event.manifestId)) printed = event;
        }
        if (printed == null || printed.address.length() == 0) throw new BadRequest("Manifest output was not found.");
        Path manifestRoot = store.getSharedRoot().resolve("manifests").toAbsolutePath().normalize();
        Path output = store.getSharedRoot().resolve(printed.address).toAbsolutePath().normalize();
        if (!output.startsWith(manifestRoot)) throw new BadRequest("Invalid manifest output path.");
        String checksum = ManifestWriter.checksum(output);
        if (!checksum.equals(printed.notes)) throw new BadRequest("Manifest checksum does not match the finalized audit record.");
        ManifestWriter.open(output);
        Map<String, Object> response = message("Original manifest opened for reprint.");
        response.put("manifestId", manifestId);
        response.put("checksum", checksum);
        return response;
    }

    private synchronized Map<String, Object> report(Map<String, String> request) throws IOException {
        requireConfigured();
        String period = value(request, "period", "custom").toLowerCase();
        String timeZone = value(request, "timeZone", "UTC");
        Instant[] bounds = reportBounds(request);
        Instant from = bounds[0];
        Instant to = bounds[1];
        if (!from.isBefore(to)) throw new BadRequest("Report start must be before report end.");
        boolean saveCopy = "true".equalsIgnoreCase(value(request, "saveCopy", "false"));
        Path reportRoot = saveCopy ? store.getSharedRoot() : config.localRoot.resolve("temporary-reports");
        ReportWriter.Result output = new ReportWriter().write(reportRoot,
                value(request, "type", "Receiving Activity"), period,
                timeZone, from, to, filterReportEvents(request), projection,
                !"csv".equalsIgnoreCase(value(request, "action", "print")),
                value(request, "columns", "time|tracking|carrier|recipient|location|status|manifest|actor|device"),
                value(request, "groupBy", "location"), value(request, "sortOrder", "occurred-asc"),
                "true".equalsIgnoreCase(value(request, "includeSummary", "true")));
        Map<String, Object> response = message("Reporting extract created. No package records were changed.");
        response.put("htmlFile", output.html.getFileName().toString());
        response.put("pdfFile", output.pdf.getFileName().toString());
        response.put("csvFile", output.csv.getFileName().toString());
        response.put("count", output.count);
        response.put("savedCopy", saveCopy);
        return response;
    }

    private List<TrackingEvent> filterReportEvents(Map<String, String> request) {
        String location = value(request, "location", "");
        String carrier = value(request, "carrier", "");
        String status = value(request, "status", "");
        String recipient = value(request, "recipient", "");
        List<TrackingEvent> filtered = new ArrayList<TrackingEvent>();
        for (TrackingEvent event : events) {
            PackageState state = projection.find(event.trackingNumber);
            if (location.length() > 0 && !location.equals(event.location)) continue;
            if (carrier.length() > 0 && !carrier.equalsIgnoreCase(event.carrier)) continue;
            if (status.length() > 0 && (state == null || !status.equals(state.status))) continue;
            if (recipient.length() > 0 && (state == null || !state.recipient.toLowerCase().contains(recipient.toLowerCase()))) continue;
            filtered.add(event);
        }
        return filtered;
    }

    private Map<String, Object> reportRange(Map<String, String> request) {
        Instant[] bounds = reportBounds(request);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("fromUtc", bounds[0].toString());
        response.put("toUtc", bounds[1].toString());
        return response;
    }

    private Instant[] reportBounds(Map<String, String> request) {
        String period = value(request, "period", "custom").toLowerCase();
        try {
            ZoneId zone = ZoneId.of(value(request, "timeZone", "UTC"));
            Instant from;
            Instant to;
            if ("custom".equals(period)) {
                LocalDate fromDate = LocalDate.parse(required(request, "fromDate"));
                LocalDate toDate = LocalDate.parse(required(request, "toDate"));
                from = fromDate.atStartOfDay(zone).toInstant();
                to = toDate.plusDays(1).atStartOfDay(zone).toInstant();
            } else {
                ZonedDateTime start = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone);
                if ("week".equals(period)) start = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                if ("month".equals(period)) start = start.withDayOfMonth(1);
                from = start.toInstant();
                to = ("day".equals(period) ? start.plusDays(1)
                        : "week".equals(period) ? start.plusWeeks(1) : start.plusMonths(1)).toInstant();
            }
            if (!from.isBefore(to)) throw new BadRequest("Report start must be before report end.");
            return new Instant[] { from, to };
        } catch (BadRequest ex) { throw ex; }
        catch (Exception ex) { throw new BadRequest("Invalid report date range."); }
    }

    private synchronized Map<String, Object> preferences(Map<String, String> request) throws IOException {
        String mode = value(request, "completionMode", config.scannerMode);
        if (!"automatic".equals(mode) && !"terminator".equals(mode) && !"manual".equals(mode))
            throw new BadRequest("Invalid scanner completion mode.");
        config.scannerMode = mode;
        config.scannerTerminator = value(request, "terminator", "Enter");
        config.scannerIdleMs = boundedInteger(request, "idleDelayMs", 120, 80, 2000);
        config.scannerBurstMs = boundedInteger(request, "burstThresholdMs", 50, 10, 500);
        config.scannerMinimumLength = boundedInteger(request, "minimumLength", 6, 4, 100);
        String deviceId = value(request, "deviceId", config.deviceId).toUpperCase();
        if (!deviceId.matches("[A-Z0-9-]{1,40}")) throw new BadRequest("Device ID may contain only uppercase letters, digits, and hyphens.");
        config.deviceId = deviceId;
        config.soundEnabled = "true".equalsIgnoreCase(value(request, "soundEnabled", String.valueOf(config.soundEnabled)));
        config.defaultLocation = value(request, "defaultLocation", config.defaultLocation);
        config.save();
        return message("Workstation scanner settings saved.");
    }

    private synchronized Map<String, Object> saveSharedSettings(Map<String, String> request) throws IOException {
        requireConfigured();
        if (!"true".equalsIgnoreCase(value(request, "confirmed", "false")))
            throw new BadRequest("Shared-setting changes require review and confirmation.");
        Map<String, String> proposed = new LinkedHashMap<String, String>();
        proposed.put("schemaVersion", "1");
        proposed.put("locations", required(request, "locations"));
        proposed.put("operationalTimeZone", required(request, "operationalTimeZone"));
        proposed.put("pendingAttentionMinutes", required(request, "pendingAttentionMinutes"));
        proposed.put("retainRawBarcode", required(request, "retainRawBarcode"));
        sharedConfig.save(proposed);
        store.append(configurationEvent("CONFIGURATION_CHANGED", "Shared operational settings revised"));
        reload();
        return message("Shared operational settings saved and audited.");
    }

    private synchronized Map<String, Object> rollbackSharedSettings() throws IOException {
        requireConfigured();
        sharedConfig.rollback();
        store.append(configurationEvent("CONFIGURATION_CHANGED", "Shared operational settings rolled back"));
        reload();
        return message("Prior shared settings restored and audited.");
    }

    private TrackingEvent configurationEvent(String type, String notes) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = type;
        event.deviceId = config.deviceId;
        event.sessionId = sessionId;
        event.streamId = "CONFIGURATION";
        event.actor = config.actor;
        event.notes = notes;
        return event;
    }

    private synchronized Map<String, Object> retryPending() {
        requireConfigured();
        EventStore.RetryResult result = store.retryPending();
        reload();
        Map<String, Object> response = message(result.errors.isEmpty()
                ? "Pending events submitted to the synchronized folder."
                : "Some pending events still require attention.");
        response.put("recovered", result.recovered);
        response.put("errors", result.errors);
        return response;
    }

    private synchronized Map<String, Object> rebuildProjection() throws IOException {
        store.clearIndex();
        reload();
        Map<String, Object> response = message("Local projection rebuilt from immutable shared and pending events.");
        response.put("events", events.size());
        response.put("packages", projection.all().size());
        return response;
    }

    private synchronized Map<String, Object> exportDiagnostics() throws IOException {
        Path dir = config.localRoot.resolve("recovery");
        Files.createDirectories(dir);
        Path output = dir.resolve("diagnostics-" + Instant.now().toString().replaceAll("[^0-9]", "") + ".txt");
        List<String> lines = new ArrayList<String>();
        lines.add("Commercial Tracking redacted diagnostics");
        lines.add("Generated UTC: " + Instant.now());
        lines.add("Device: " + config.deviceId);
        lines.add("Actor: " + config.actor);
        lines.add("Shared root: " + (config.sharedRoot == null ? "Not configured" : config.sharedRoot));
        lines.add("Events: " + events.size());
        lines.add("Packages: " + projection.all().size());
        lines.add("Pending: " + (store == null ? 0 : store.pendingCount()));
        lines.add("Malformed records: " + errors.size());
        Files.write(output, lines, StandardCharsets.UTF_8);
        Map<String, Object> response = message("Redacted diagnostic export created.");
        response.put("file", output.toString());
        return response;
    }

    private synchronized Map<String, Object> finishSession(Map<String, String> request) throws IOException {
        requireConfigured();
        boolean closeWithoutManifest = "true".equalsIgnoreCase(value(request, "closeWithoutManifest", "false"));
        int unmanifested = 0;
        for (Map<String, Object> item : sessionPackageMaps()) {
            if (String.valueOf(item.get("manifestId")).length() == 0) unmanifested++;
        }
        if (unmanifested > 0 && !closeWithoutManifest) {
            Map<String, Object> response = message("Unmanifested packages require review.");
            response.put("confirmationRequired", true);
            response.put("unmanifestedCount", unmanifested);
            return response;
        }
        sessionId = UUID.randomUUID().toString();
        config.activeSessionId = sessionId;
        config.save();
        session.clear();
        Map<String, Object> response = message("Receiving session closed. Shared events were retained.");
        response.put("confirmationRequired", false);
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
        event.observedRevision = current.revision;
        event.referenceEventId = current.lastEventId;
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
            row.put("lastEventId", state.lastEventId);
            row.put("manifestId", state.manifestId);
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

    private static int boundedInteger(Map<String, String> values, String key, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value(values, key, String.valueOf(fallback)));
            if (parsed < min || parsed > max) throw new BadRequest(key + " is outside the safe range.");
            return parsed;
        } catch (NumberFormatException ex) { throw new BadRequest("Invalid " + key + "."); }
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : hash) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
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
                } else if ("/api/lookup".equals(path) && "POST".equals(method)) {
                    response = lookup(body(exchange));
                } else if ("/api/configure".equals(path) && "POST".equals(method)) {
                    String root = required(body(exchange), "sharedRoot");
                    configureStore(Paths.get(root));
                    response = message("Shared folder configured.");
                } else if ("/api/recipient".equals(path) && "POST".equals(method)) {
                    response = assignRecipient(body(exchange));
                } else if ("/api/recipients".equals(path) && "POST".equals(method)) {
                    response = assignRecipients(body(exchange));
                } else if ("/api/void".equals(path) && "POST".equals(method)) {
                    response = voidPackage(body(exchange));
                } else if ("/api/correct".equals(path) && "POST".equals(method)) {
                    response = correctPackage(body(exchange));
                } else if ("/api/conflict/resolve".equals(path) && "POST".equals(method)) {
                    response = resolveConflict(body(exchange));
                } else if ("/api/manifest".equals(path) && "POST".equals(method)) {
                    response = manifest(body(exchange));
                } else if ("/api/manifest/reprint".equals(path) && "POST".equals(method)) {
                    response = reprintManifest(body(exchange));
                } else if ("/api/report".equals(path) && "POST".equals(method)) {
                    response = report(body(exchange));
                } else if ("/api/report/range".equals(path) && "POST".equals(method)) {
                    response = reportRange(body(exchange));
                } else if ("/api/preferences".equals(path) && "POST".equals(method)) {
                    response = preferences(body(exchange));
                } else if ("/api/settings/shared".equals(path) && "POST".equals(method)) {
                    response = saveSharedSettings(body(exchange));
                } else if ("/api/settings/shared/rollback".equals(path) && "POST".equals(method)) {
                    response = rollbackSharedSettings();
                } else if ("/api/recovery/retry".equals(path) && "POST".equals(method)) {
                    response = retryPending();
                } else if ("/api/recovery/rebuild".equals(path) && "POST".equals(method)) {
                    response = rebuildProjection();
                } else if ("/api/diagnostics/export".equals(path) && "POST".equals(method)) {
                    response = exportDiagnostics();
                } else if ("/api/session/finish".equals(path) && "POST".equals(method)) {
                    response = finishSession(body(exchange));
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
        server = null;
        stopped.countDown();
    }

    private void startEventMonitor() {
        Thread monitor = new Thread(() -> {
            while (server != null) {
                try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                    if (store != null) {
                        Path eventsRoot = store.getSharedRoot().resolve("events");
                        if (Files.isDirectory(eventsRoot)) {
                            try (java.util.stream.Stream<Path> directories = Files.walk(eventsRoot)) {
                                directories.filter(Files::isDirectory).forEach(path -> {
                                    try {
                                        path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                                                StandardWatchEventKinds.ENTRY_MODIFY);
                                    } catch (IOException ignored) { }
                                });
                            }
                        }
                    }
                    watcher.poll(15, TimeUnit.SECONDS);
                    if (store != null && store.pendingCount() > 0) store.retryPending();
                    reload();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    synchronized (BrowserServer.this) { errors.add("Event monitor: " + ex.getMessage()); }
                }
            }
        }, "commercial-tracking-event-monitor");
        monitor.setDaemon(true);
        monitor.start();
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
