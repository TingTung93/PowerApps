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
    private final GudidClient gudid;
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
        EventStore.LoadResult loaded = store.loadAll();
        events.addAll(loaded.events);
        errors.addAll(loaded.errors);
        projection = Projection.replay(events);
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
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("configured", Boolean.valueOf(store != null));
        value.put("sharedRoot", config.sharedRoot == null ? "" : config.sharedRoot.toString());
        value.put("deviceId", config.deviceId);
        value.put("actor", config.actor);
        value.put("gudidEnabled", Boolean.valueOf(config.gudidEnabled && gudid != null));
        value.put("eventCount", Integer.valueOf(events.size()));
        value.put("pendingCount", Integer.valueOf(store == null ? 0 : store.pendingCount()));
        value.put("refreshedUtc", refreshedUtc);
        value.put("dashboard", metricsMap(dashboard(now)));
        value.put("stock", stockMaps());
        value.put("catalog", catalogMaps());
        value.put("reorder", reorderMaps(now));
        value.put("errors", new ArrayList<String>(errors));
        return value;
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
        if (quantity <= 0) throw new BadRequest("Quantity must be greater than zero.");
        Gs1Scan parsed = Gs1Parser.parse(raw);
        if (!parsed.success) throw new BadRequest("No GTIN was found in the barcode.");
        if (!force && !projection.catalog().containsKey(parsed.gtin)) {
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("needsRegistration", Boolean.TRUE);
            response.put("gtin", parsed.gtin);
            response.put("lot", parsed.lot);
            response.put("expirationIso", parsed.expirationIso);
            response.put("barcode", parsed.raw);
            response.put("suggestion", gudidSuggestion(parsed.gtin));
            return response;
        }
        store.append(SupplyEvents.stockReceived(identity(), nowIso(),
                parsed.gtin, parsed.lot, parsed.expirationIso, parsed.raw, quantity));
        reload();
        Map<String, Object> response = ok("Received " + quantity + " of " + parsed.gtin + ".");
        response.put("itemKey", parsed.itemKey());
        return response;
    }

    public synchronized Map<String, Object> pick(String gtin, String lot,
            String expirationIso, int quantity) throws IOException {
        requireConfigured();
        if (quantity <= 0) throw new BadRequest("Quantity must be greater than zero.");
        store.append(SupplyEvents.stockPicked(
                identity(), nowIso(), gtin, lot, expirationIso, quantity));
        reload();
        return ok("Picked " + quantity + ".");
    }

    public synchronized Map<String, Object> adjust(String gtin, String lot,
            String expirationIso, int absoluteQuantity) throws IOException {
        requireConfigured();
        if (absoluteQuantity < 0) throw new BadRequest("Quantity cannot be negative.");
        store.append(SupplyEvents.stockAdjusted(
                identity(), nowIso(), gtin, lot, expirationIso, absoluteQuantity));
        reload();
        return ok("Quantity set to " + absoluteQuantity + ".");
    }

    public synchronized Map<String, Object> archive(String gtin, String lot,
            String expirationIso, String reason) throws IOException {
        requireConfigured();
        if (reason == null || reason.trim().length() == 0)
            throw new BadRequest("A reason is required.");
        store.append(SupplyEvents.stockArchived(
                identity(), nowIso(), gtin, lot, expirationIso, reason.trim()));
        reload();
        return ok("Item archived.");
    }

    public synchronized Map<String, Object> registerProduct(String gtin, String name,
            String manufacturer, String category, double unitPrice, int par, String notes,
            String source) throws IOException {
        requireConfigured();
        if (gtin == null || gtin.trim().length() == 0) throw new BadRequest("GTIN is required.");
        if (name == null || name.trim().length() == 0)
            throw new BadRequest("Product name is required.");
        boolean update = projection.catalog().containsKey(gtin);
        SupplyEvent event = update
                ? SupplyEvents.productUpdated(identity(), nowIso(), gtin, name, manufacturer,
                        category, unitPrice, par, notes, source)
                : SupplyEvents.productRegistered(identity(), nowIso(), gtin, name, manufacturer,
                        category, unitPrice, par, notes, source);
        store.append(event);
        reload();
        return ok(update ? "Product updated." : "Product registered.");
    }

    private Object gudidSuggestion(String gtin) {
        if (gudid == null || !config.gudidEnabled) return new LinkedHashMap<String, Object>();
        GudidResult result = gudid.lookup(gtin);
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("found", Boolean.valueOf(result.found));
        value.put("name", result.suggestedName());
        value.put("manufacturer", result.companyName);
        value.put("category", result.suggestedCategory());
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
        response.put("name", result.suggestedName());
        response.put("manufacturer", result.companyName);
        response.put("category", result.suggestedCategory());
        return response;
    }

    private static Map<String, Object> ok(String message) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("ok", Boolean.TRUE);
        response.put("message", message);
        return response;
    }
}
