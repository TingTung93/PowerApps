package org.medsupply;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AppService {
    private final AppConfig config;
    private GudidClient gudid;
    private EventStore store;
    private Projection projection = Projection.replay(new ArrayList<SupplyEvent>());
    private final List<SupplyEvent> events = new ArrayList<SupplyEvent>();
    private final List<String> errors = new ArrayList<String>();
    private String refreshedUtc = "";

    public AppService(AppConfig config, GudidClient gudid) {
        this.config = config;
        this.gudid = gudid;
        if (config.sharedRoot != null && Files.isDirectory(config.sharedRoot)) {
            try { openStore(config.sharedRoot); } catch (IOException ignored) { }
        }
    }

    public boolean configured() {
        return store != null;
    }

    public synchronized void configure(Path root) throws IOException {
        Files.createDirectories(root);
        Path probe = root.resolve(".medsupply-write-probe-" + UUID.randomUUID());
        try { Files.write(probe, new byte[] {1}); } finally { Files.deleteIfExists(probe); }
        openStore(root);
        config.sharedRoot = root.toAbsolutePath().normalize();
        config.save();
    }

    private void openStore(Path root) throws IOException {
        store = new EventStore(root, config.localRoot);
        reload();
    }

    public synchronized void reload() {
        events.clear();
        errors.clear();
        if (store == null) {
            projection = Projection.replay(events);
            return;
        }
        EventStore.RetryResult retried = store.retryPending();
        errors.addAll(retried.errors);
        EventStore.LoadResult loaded = store.loadAll();
        events.addAll(loaded.events);
        errors.addAll(loaded.errors);
        projection = Projection.replay(events);
        for (StockLine line : projection.stock()) {
            if (line.quantity < 0)
                errors.add("Negative inventory detected for " + line.itemKey
                        + "; concurrent removals require audit review.");
        }
        refreshedUtc = Instant.now().toString();
    }

    public List<StockLine> stock() {
        return projection.stock();
    }

    public Map<String, CatalogProduct> catalog() {
        return projection.catalog();
    }

    public List<String> errors() {
        return new ArrayList<String>(errors);
    }

    public List<SupplyEvent> events() {
        return new ArrayList<SupplyEvent>(events);
    }

    public DashboardMetrics dashboard(Instant now) {
        return InventoryAnalytics.compute(projection.stock(), events, now, config.staleDays);
    }

    public List<ReorderSuggestion> reorder(Instant now) {
        return ReorderAdvisor.advise(projection.catalog(), projection.stock(), events, now,
                new ReorderAdvisor.Params(config.reorderWindowDays, config.reorderLeadDays,
                        config.reorderSafetyDays, config.reorderCoverageDays));
    }

    public synchronized ManagementReport.Result writeManagementReport(Instant now) throws IOException {
        requireConfigured();
        reload();
        if (!errors.isEmpty() || store.pendingCount() > 0)
            throw new BadRequest("Report blocked: the audit trail is incomplete or has pending events.");
        return ManagementReport.write(store.getSharedRoot().resolve("reports"),
                dashboard(now), reorder(now), stock(), now);
    }

    public SupplyEvents.Identity identity() {
        return new SupplyEvents.Identity(config.deviceId, config.actor, config.activeSessionId);
    }

    EventStore store() {
        return store;
    }

    GudidClient gudid() {
        return gudid;
    }

    AppConfig config() {
        return config;
    }

    public synchronized Map<String, Object> snapshot(Instant now) {
        reload();
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("configured", Boolean.valueOf(store != null));
        value.put("sharedRoot", config.sharedRoot == null ? "" : config.sharedRoot.toString());
        value.put("deviceId", config.deviceId);
        value.put("actor", config.actor);
        value.put("gudidEnabled", Boolean.valueOf(config.gudidEnabled && gudid != null));
        value.put("eventCount", Integer.valueOf(events.size()));
        value.put("pendingCount", Integer.valueOf(store == null ? 0 : store.pendingCount()));
        value.put("trailComplete", Boolean.valueOf(errors.isEmpty()
                && (store == null || store.pendingCount() == 0)));
        value.put("unreadableEventCount", Integer.valueOf(errors.size()));
        value.put("refreshedUtc", refreshedUtc);
        value.put("dashboard", metricsMap(dashboard(now)));
        value.put("stock", stockMaps());
        value.put("catalog", catalogMaps());
        value.put("reorder", reorderMaps(now));
        value.put("settings", settingsMap());
        value.put("errors", new ArrayList<String>(errors));
        value.put("distro", distro());
        return value;
    }

    private List<String> distro() {
        String members = "";
        for (SupplyEvent event : events)
            if (SupplyEvents.DISTRO_UPDATED.equals(event.eventType))
                members = event.payload(SupplyEvents.K_MEMBERS);
        List<String> result = new ArrayList<String>();
        for (String member : members.split("\\n")) if (member.trim().length() > 0) result.add(member.trim());
        return result;
    }

    public synchronized Map<String, Object> updateDistro(String members) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        List<String> cleaned = new ArrayList<String>();
        for (String member : (members == null ? "" : members).split("[\\r\\n,;]+")) {
            String value = member.trim();
            if (value.length() == 0) continue;
            if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                throw new BadRequest("Invalid distribution address: " + value);
            if (!cleaned.contains(value)) cleaned.add(value);
        }
        StringBuilder serialized = new StringBuilder();
        for (String value : cleaned) {
            if (serialized.length() > 0) serialized.append('\n');
            serialized.append(value);
        }
        boolean queued = appendEvent(SupplyEvents.distroUpdated(identity(), nowIso(), serialized.toString()));
        reload();
        return ok("Distribution list saved.", queued);
    }

    private Map<String, Object> settingsMap() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("deviceId", config.deviceId);
        value.put("actor", config.actor);
        value.put("gudidEnabled", Boolean.valueOf(config.gudidEnabled));
        value.put("gudidEndpoint", config.gudidEndpoint);
        value.put("reorderWindowDays", Integer.valueOf(config.reorderWindowDays));
        value.put("reorderLeadDays", Integer.valueOf(config.reorderLeadDays));
        value.put("reorderSafetyDays", Integer.valueOf(config.reorderSafetyDays));
        value.put("reorderCoverageDays", Integer.valueOf(config.reorderCoverageDays));
        value.put("staleDays", Integer.valueOf(config.staleDays));
        value.put("scannerMinimumLength", Integer.valueOf(config.scannerMinimumLength));
        value.put("scannerAutoFocus", Boolean.valueOf(config.scannerAutoFocus));
        value.put("scannerSound", Boolean.valueOf(config.scannerSound));
        value.put("scannerAutoSubmit", Boolean.valueOf(config.scannerAutoSubmit));
        value.put("scannerDefaultQuantity", Integer.valueOf(config.scannerDefaultQuantity));
        return value;
    }

    public synchronized Map<String, Object> updateSettings(Map<String, String> values)
            throws IOException {
        String deviceId = requiredSetting(values, "deviceId");
        boolean gudidEnabled = Boolean.parseBoolean(setting(values, "gudidEnabled", "true"));
        int reorderWindowDays = intSetting(values, "reorderWindowDays", 7, 365);
        int reorderLeadDays = intSetting(values, "reorderLeadDays", 0, 120);
        int reorderSafetyDays = intSetting(values, "reorderSafetyDays", 0, 120);
        int reorderCoverageDays = intSetting(values, "reorderCoverageDays", 1, 365);
        int staleDays = intSetting(values, "staleDays", 1, 365);
        int scannerMinimumLength = intSetting(values, "scannerMinimumLength", 4, 100);
        boolean scannerAutoFocus = Boolean.parseBoolean(setting(values, "scannerAutoFocus", "true"));
        boolean scannerSound = Boolean.parseBoolean(setting(values, "scannerSound", "true"));
        boolean scannerAutoSubmit = Boolean.parseBoolean(setting(values, "scannerAutoSubmit", "false"));
        int scannerDefaultQuantity = values.containsKey("scannerDefaultQuantity")
                ? intSetting(values, "scannerDefaultQuantity", 1, 9999)
                : config.scannerDefaultQuantity;

        config.deviceId = deviceId;
        config.gudidEnabled = gudidEnabled;
        config.reorderWindowDays = reorderWindowDays;
        config.reorderLeadDays = reorderLeadDays;
        config.reorderSafetyDays = reorderSafetyDays;
        config.reorderCoverageDays = reorderCoverageDays;
        config.staleDays = staleDays;
        config.scannerMinimumLength = scannerMinimumLength;
        config.scannerAutoFocus = scannerAutoFocus;
        config.scannerSound = scannerSound;
        config.scannerAutoSubmit = scannerAutoSubmit;
        config.scannerDefaultQuantity = scannerDefaultQuantity;
        if (config.gudidEnabled && gudid == null) {
            gudid = new GudidClient(config.gudidEndpoint, new HttpsFetcher());
        }
        config.save();
        Map<String, Object> response = ok("Settings saved.");
        response.put("settings", settingsMap());
        return response;
    }

    private static String requiredSetting(Map<String, String> values, String key) {
        String value = setting(values, key, "");
        if (value.length() == 0) throw new BadRequest("Missing " + key + ".");
        return value;
    }

    private static String setting(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value.trim();
    }

    private static int intSetting(Map<String, String> values, String key, int min, int max) {
        try {
            int value = Integer.parseInt(setting(values, key, ""));
            if (value < min || value > max)
                throw new BadRequest(key + " must be between " + min + " and " + max + ".");
            return value;
        } catch (NumberFormatException ex) {
            throw new BadRequest("Invalid " + key + ".");
        }
    }

    static Map<String, Object> metricsMap(DashboardMetrics m) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("expired", Integer.valueOf(m.expired));
        value.put("expiring7", Integer.valueOf(m.expiring7));
        value.put("expiring30", Integer.valueOf(m.expiring30));
        value.put("outOfStock", Integer.valueOf(m.outOfStock));
        value.put("stale", Integer.valueOf(m.stale));
        value.put("distinctSkus", Integer.valueOf(m.distinctSkus));
        value.put("totalUnits", Integer.valueOf(m.totalUnits));
        value.put("onHandValue", Double.valueOf(m.onHandValue));
        value.put("activeEventsLast7", Integer.valueOf(m.activeEventsLast7));
        return value;
    }

    private List<Object> stockMaps() {
        List<Object> rows = new ArrayList<Object>();
        for (StockLine line : projection.stock()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("itemKey", line.itemKey);
            row.put("gtin", line.gtin);
            row.put("lot", line.lot);
            row.put("expirationIso", line.expirationIso);
            row.put("barcode", line.barcode);
            row.put("quantity", Integer.valueOf(line.quantity));
            row.put("active", Boolean.valueOf(line.active));
            row.put("name", line.name);
            row.put("manufacturer", line.manufacturer);
            row.put("category", line.category);
            row.put("unitPrice", Double.valueOf(line.unitPrice));
            row.put("par", Integer.valueOf(line.par));
            row.put("lastEventUtc", line.lastEventUtc);
            rows.add(row);
        }
        return rows;
    }

    private List<Object> catalogMaps() {
        List<Object> rows = new ArrayList<Object>();
        for (CatalogProduct p : projection.catalog().values()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("gtin", p.gtin);
            row.put("name", p.name);
            row.put("manufacturer", p.manufacturer);
            row.put("category", p.category);
            row.put("unitPrice", Double.valueOf(p.unitPrice));
            row.put("par", Integer.valueOf(p.par));
            row.put("notes", p.notes);
            row.put("source", p.source);
            row.put("active", Boolean.valueOf(p.active));
            rows.add(row);
        }
        return rows;
    }

    private List<Object> reorderMaps(Instant now) {
        List<Object> rows = new ArrayList<Object>();
        for (ReorderSuggestion s : reorder(now)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("gtin", s.gtin);
            row.put("name", s.name);
            row.put("onHand", Integer.valueOf(s.onHand));
            row.put("parProvided", Boolean.valueOf(s.parProvided));
            row.put("par", Integer.valueOf(s.par));
            row.put("avgDailyUsage", Double.valueOf(s.avgDailyUsage));
            row.put("reorderPoint", Integer.valueOf(s.reorderPoint));
            row.put("suggestedPar", Integer.valueOf(s.suggestedPar));
            row.put("suggestedOrderQty", Integer.valueOf(s.suggestedOrderQty));
            row.put("estimatedCost", Double.valueOf(s.estimatedCost));
            row.put("needsReorder", Boolean.valueOf(s.needsReorder));
            row.put("insufficientHistory", Boolean.valueOf(s.insufficientHistory));
            rows.add(row);
        }
        return rows;
    }

    public static final class BadRequest extends RuntimeException {
        public BadRequest(String message) {
            super(message);
        }
    }

    private void requireConfigured() {
        if (store == null) throw new BadRequest("Select a synchronized folder first.");
    }

    private static String nowIso() {
        return Instant.now().toString();
    }

    public Gs1Scan scan(String raw) {
        return Gs1Parser.parse(raw);
    }

    public synchronized Map<String, Object> receive(String raw, int quantity, boolean force)
            throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        if (quantity <= 0) throw new BadRequest("Quantity must be greater than zero.");
        if (raw == null || raw.trim().length() < config.scannerMinimumLength)
            throw new BadRequest("Barcode must be at least " + config.scannerMinimumLength
                    + " characters.");
        Gs1Scan parsed = Gs1Parser.parse(raw);
        if (!parsed.success) throw new BadRequest("No GTIN was found in the barcode.");
        CatalogProduct receivingProduct = projection.catalog().get(parsed.gtin);
        if (receivingProduct != null && !receivingProduct.active)
            throw new BadRequest("This catalog product is retired and cannot receive stock.");
        if (!force && receivingProduct == null) {
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("needsRegistration", Boolean.TRUE);
            response.put("gtin", parsed.gtin);
            response.put("lot", parsed.lot);
            response.put("expirationIso", parsed.expirationIso);
            response.put("barcode", parsed.raw);
            response.put("suggestion", gudidSuggestion(parsed.gtin));
            return response;
        }
        boolean queued = appendEvent(SupplyEvents.stockReceived(identity(), nowIso(),
                parsed.gtin, parsed.lot, parsed.expirationIso, parsed.raw, quantity));
        reload();
        Map<String, Object> response = ok("Received " + quantity + " of " + parsed.gtin + ".");
        delivery(response, queued);
        response.put("itemKey", parsed.itemKey());
        return response;
    }

    public synchronized Map<String, Object> previewReceive(String raw) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        if (raw == null || raw.trim().length() < config.scannerMinimumLength)
            throw new BadRequest("Barcode must be at least " + config.scannerMinimumLength
                    + " characters.");
        Gs1Scan parsed = Gs1Parser.parse(raw);
        if (!parsed.success) throw new BadRequest("No GTIN was found in the barcode.");

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("gtin", parsed.gtin);
        response.put("lot", parsed.lot);
        response.put("expirationIso", parsed.expirationIso);
        response.put("barcode", parsed.raw);
        CatalogProduct product = projection.catalog().get(parsed.gtin);
        if (product != null && !product.active)
            throw new BadRequest("This catalog product is retired and cannot receive stock.");
        response.put("registered", Boolean.valueOf(product != null));
        if (product != null) {
            Map<String, Object> catalog = new LinkedHashMap<String, Object>();
            catalog.put("name", product.name);
            catalog.put("manufacturer", product.manufacturer);
            catalog.put("category", product.category);
            catalog.put("source", product.source);
            response.put("catalog", catalog);
        }
        response.put("gudid", gudidSuggestion(parsed.gtin));
        return response;
    }

    public synchronized Map<String, Object> itemHistory(String gtin, String lot,
            String expirationIso) throws IOException {
        requireConfigured();
        String itemKey = ItemKey.of(gtin, lot, expirationIso);
        List<Object> history = new ArrayList<Object>();
        int balance = 0;
        for (SupplyEvent event : events) {
            if (!itemKey.equals(event.payload(SupplyEvents.K_ITEM_KEY))) continue;
            String quantity = event.payload(SupplyEvents.K_QUANTITY);
            String change = "";
            if (SupplyEvents.STOCK_RECEIVED.equals(event.eventType)) {
                int amount = parseInt(quantity, 0);
                balance += amount;
                change = "+" + amount;
            } else if (SupplyEvents.STOCK_PICKED.equals(event.eventType)) {
                int amount = parseInt(quantity, 0);
                balance -= amount;
                change = "-" + amount;
            } else if (SupplyEvents.STOCK_ADJUSTED.equals(event.eventType)) {
                balance = parseInt(quantity, balance);
                change = "Set to " + balance;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("eventType", event.eventType);
            row.put("occurredUtc", event.occurredUtc);
            row.put("actor", event.actor);
            row.put("deviceId", event.deviceId);
            row.put("change", change);
            row.put("balance", Integer.valueOf(balance));
            row.put("reason", event.payload(SupplyEvents.K_REASON));
            row.put("barcode", event.payload(SupplyEvents.K_BARCODE));
            history.add(row);
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("itemKey", itemKey);
        response.put("events", history);
        return response;
    }

    public synchronized Map<String, Object> pick(String gtin, String lot,
            String expirationIso, int quantity) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        if (quantity <= 0) throw new BadRequest("Quantity must be greater than zero.");
        StockLine line = findStock(gtin, lot, expirationIso);
        if (line == null || !line.active) throw new BadRequest("This lot is not active inventory.");
        if (quantity > line.quantity)
            throw new BadRequest("Cannot pick " + quantity + "; only " + line.quantity + " on hand.");
        boolean queued = appendEvent(SupplyEvents.stockPicked(identity(), nowIso(), gtin, lot, expirationIso,
                quantity, quantity == line.quantity));
        reload();
        return ok("Picked " + quantity + ".", queued);
    }

    private void requireCompleteTrail() {
        if (!errors.isEmpty() || (store != null && store.pendingCount() > 0))
            throw new BadRequest("Inventory write blocked: the audit trail is incomplete or has pending events.");
    }

    private StockLine findStock(String gtin, String lot, String expirationIso) {
        String key = ItemKey.of(gtin, lot, expirationIso);
        for (StockLine line : projection.stock()) if (key.equals(line.itemKey)) return line;
        return null;
    }

    public synchronized Map<String, Object> adjust(String gtin, String lot,
            String expirationIso, int absoluteQuantity) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        if (absoluteQuantity < 0) throw new BadRequest("Quantity cannot be negative.");
        StockLine line = findStock(gtin, lot, expirationIso);
        if (line == null || !line.active) throw new BadRequest("This lot is not active inventory.");
        boolean queued = appendEvent(SupplyEvents.stockAdjusted(
                identity(), nowIso(), gtin, lot, expirationIso, absoluteQuantity));
        reload();
        return ok("Quantity set to " + absoluteQuantity + ".", queued);
    }

    public synchronized Map<String, Object> archive(String gtin, String lot,
            String expirationIso, String reason) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        StockLine line = findStock(gtin, lot, expirationIso);
        if (line == null || !line.active) throw new BadRequest("This lot is not active inventory.");
        if (reason == null || reason.trim().length() == 0)
            throw new BadRequest("A reason is required.");
        boolean queued = appendEvent(SupplyEvents.stockArchived(
                identity(), nowIso(), gtin, lot, expirationIso, reason.trim()));
        reload();
        return ok("Item archived.", queued);
    }

    public synchronized Map<String, Object> restore(String gtin, String lot,
            String expirationIso, String reason) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        StockLine line = findStock(gtin, lot, expirationIso);
        if (line == null) throw new BadRequest("Inventory lot was not found.");
        if (line.active) throw new BadRequest("Inventory lot is already active.");
        boolean queued = appendEvent(SupplyEvents.stockRestored(identity(), nowIso(), gtin, lot,
                expirationIso, reason == null ? "" : reason.trim()));
        reload();
        return ok("Inventory lot restored.", queued);
    }

    public synchronized Map<String, Object> archiveExpired(Instant today) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        int count = 0;
        boolean queued = false;
        String date = today.toString().substring(0, 10);
        List<StockLine> candidates = new ArrayList<StockLine>(projection.stock());
        try {
            for (StockLine line : candidates) {
                if (line.active && line.expirationIso.length() > 0 && line.expirationIso.compareTo(date) < 0) {
                    queued |= appendEvent(SupplyEvents.stockArchived(identity(), nowIso(), line.gtin, line.lot,
                            line.expirationIso, "Bulk archive: expired inventory"));
                    count++;
                }
            }
        } finally {
            // Reflect every durable prefix even when a later event cannot be accepted locally.
            reload();
        }
        return ok("Archived " + count + " expired inventory lots.", queued);
    }

    public synchronized Map<String, Object> retireProduct(String gtin, String reason) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        CatalogProduct product = projection.catalog().get(gtin);
        if (product == null) throw new BadRequest("Catalog product was not found.");
        if (!product.active) throw new BadRequest("Catalog product is already retired.");
        if (reason == null || reason.trim().length() == 0) throw new BadRequest("A reason is required.");
        boolean queued = appendEvent(SupplyEvents.productRetired(identity(), nowIso(), gtin, reason.trim()));
        reload();
        return ok("Product retired.", queued);
    }

    public synchronized Map<String, Object> registerProduct(String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes,
            String source) throws IOException {
        requireConfigured();
        reload();
        requireCompleteTrail();
        if (gtin == null || gtin.trim().length() == 0) throw new BadRequest("GTIN is required.");
        if (name == null || name.trim().length() == 0)
            throw new BadRequest("Product name is required.");
        boolean update = projection.catalog().containsKey(gtin);
        if (update && !projection.catalog().get(gtin).active)
            throw new BadRequest("This product is retired. RC1 does not permit silent reactivation.");
        SupplyEvent event = update
                ? SupplyEvents.productUpdated(identity(), nowIso(), gtin, name, manufacturer,
                        category, unitPrice, par, notes, source)
                : SupplyEvents.productRegistered(identity(), nowIso(), gtin, name, manufacturer,
                        category, unitPrice, par, notes, source);
        boolean queued = appendEvent(event);
        reload();
        return ok(update ? "Product updated." : "Product registered.", queued);
    }

    private Map<String, Object> gudidSuggestion(String gtin) {
        if (gudid == null || !config.gudidEnabled) return new LinkedHashMap<String, Object>();
        GudidResult result = gudid.lookup(gtin);
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("found", Boolean.valueOf(result.found));
        value.put("lookupFailed", Boolean.valueOf(result.lookupFailed));
        value.put("error", result.error);
        value.put("name", result.suggestedName());
        value.put("manufacturer", result.companyName);
        value.put("category", result.suggestedCategory());
        value.put("brandName", result.brandName);
        value.put("companyName", result.companyName);
        value.put("deviceDescription", result.deviceDescription);
        value.put("versionModelNumber", result.versionModelNumber);
        value.put("catalogNumber", result.catalogNumber);
        value.put("gmdnTerms", new java.util.ArrayList<String>(result.gmdnTerms));
        return value;
    }

    public Map<String, Object> lookupGudid(String gtin) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        if (gudid == null || !config.gudidEnabled) {
            response.put("enabled", Boolean.FALSE);
            return response;
        }
        GudidResult result = gudid.lookup(gtin);
        response.put("enabled", Boolean.TRUE);
        response.put("found", Boolean.valueOf(result.found));
        response.put("lookupFailed", Boolean.valueOf(result.lookupFailed));
        response.put("error", result.error);
        response.put("name", result.suggestedName());
        response.put("manufacturer", result.companyName);
        response.put("category", result.suggestedCategory());
        response.put("brandName", result.brandName);
        response.put("companyName", result.companyName);
        response.put("deviceDescription", result.deviceDescription);
        response.put("versionModelNumber", result.versionModelNumber);
        response.put("catalogNumber", result.catalogNumber);
        response.put("gmdnTerms", new java.util.ArrayList<String>(result.gmdnTerms));
        return response;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("ok", Boolean.TRUE);
        response.put("message", message);
        return response;
    }

    private static Map<String, Object> ok(String message, boolean queued) {
        Map<String, Object> response = ok(message);
        delivery(response, queued);
        return response;
    }

    private static void delivery(Map<String, Object> response, boolean queued) {
        response.put("queued", Boolean.valueOf(queued));
        if (queued) response.put("message", response.get("message")
                + " Saved locally and queued for synchronized publication.");
    }

    private boolean appendEvent(SupplyEvent event) throws IOException {
        Path result = store.append(event);
        return result.getFileName().toString().endsWith(".tmp");
    }
}
