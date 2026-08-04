package org.medsupply;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class GudidClient {
    public interface Fetcher {
        String fetch(String url) throws IOException;
    }

    private final String endpoint;
    private final Fetcher fetcher;

    public GudidClient(String endpoint, Fetcher fetcher) {
        this.endpoint = endpoint;
        this.fetcher = fetcher;
    }

    public GudidResult lookup(String gtin) {
        GudidResult result = new GudidResult();
        result.gtin = gtin == null ? "" : gtin;
        try {
            String body = fetcher.fetch(endpoint + "?di=" + result.gtin);
            Map<String, Object> device = locateDevice(Json.asMap(Json.parse(body)));
            if (device.isEmpty()) return result;
            result.brandName = Json.str(device, "brandName");
            result.companyName = Json.str(device, "companyName");
            result.deviceDescription = Json.str(device, "deviceDescription");
            result.versionModelNumber = Json.str(device, "versionModelNumber");
            result.catalogNumber = Json.str(device, "catalogNumber");
            result.gmdnTerms = gmdnTerms(device.get("gmdnTerms"));
            result.found = result.brandName.length() > 0 || result.deviceDescription.length() > 0
                    || result.companyName.length() > 0;
        } catch (Exception ex) {
            result.found = false;
        }
        return result;
    }

    private static Map<String, Object> locateDevice(Map<String, Object> root) {
        Map<String, Object> gudid = Json.asMap(root.get("gudid"));
        Map<String, Object> device = Json.asMap(gudid.get("device"));
        if (!device.isEmpty()) return device;
        device = Json.asMap(root.get("device"));
        if (!device.isEmpty()) return device;
        return root;
    }

    private static java.util.List<String> gmdnTerms(Object node) {
        java.util.List<String> terms = new java.util.ArrayList<String>();
        List<Object> list;
        if (node instanceof Map) {
            list = Json.asList(Json.asMap(node).get("gmdn"));
        } else {
            list = Json.asList(node);
        }
        for (Object item : list) {
            String name = Json.str(Json.asMap(item), "gmdnPTName");
            if (name.length() > 0) terms.add(name);
        }
        return terms;
    }
}
