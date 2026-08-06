package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AppServiceGudidTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-gudid");
        System.setProperty("medsupply.localBase", base.toString());
        AppConfig config = AppConfig.load();

        GudidClient client = new GudidClient("https://x/api", new GudidClient.Fetcher() {
            public String fetch(String url) {
                return "{\"gudid\":{\"device\":{\"brandName\":\"Stent\",\"companyName\":\"Abbott\","
                        + "\"deviceDescription\":\"Drug-eluting coronary stent\","
                        + "\"versionModelNumber\":\"MODEL-1\",\"catalogNumber\":\"CAT-9\","
                        + "\"gmdnTerms\":{\"gmdn\":[{\"gmdnPTName\":\"Coronary stent\"}]}}}}";
            }
        });
        AppService svc = new AppService(config, client);
        Map<String, Object> r = svc.lookupGudid("00380740000010");
        check(Boolean.TRUE.equals(r.get("enabled")), "enabled");
        check(Boolean.TRUE.equals(r.get("found")), "found");
        check("Stent".equals(Json.str(r, "name")), "name");
        check("Abbott".equals(Json.str(r, "manufacturer")), "manufacturer");
        check("Coronary stent".equals(Json.str(r, "category")), "category");
        check("Stent".equals(Json.str(r, "brandName")), "brand name");
        check("Drug-eluting coronary stent".equals(Json.str(r, "deviceDescription")),
                "device description");
        check("MODEL-1".equals(Json.str(r, "versionModelNumber")), "model");
        check("CAT-9".equals(Json.str(r, "catalogNumber")), "catalog number");
        check(Json.asList(r.get("gmdnTerms")).size() == 1, "generic names");

        svc.configure(base.resolve("shared"));
        Map<String, Object> preview = svc.previewReceive("01003807400000101726113010LOT1");
        check("00380740000010".equals(Json.str(preview, "gtin")), "preview GTIN");
        check("LOT1".equals(Json.str(preview, "lot")), "preview lot");
        check(!Boolean.TRUE.equals(preview.get("registered")), "preview unknown product");
        check("Stent".equals(Json.str(Json.asMap(preview.get("gudid")), "brandName")),
                "preview GUDID metadata");

        AppService disabled = new AppService(config, null);
        check(Boolean.FALSE.equals(disabled.lookupGudid("x").get("enabled")), "disabled when null");
        System.out.println("AppServiceGudidTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
