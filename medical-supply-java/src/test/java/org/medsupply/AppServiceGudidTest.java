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

        AppService disabled = new AppService(config, null);
        check(Boolean.FALSE.equals(disabled.lookupGudid("x").get("enabled")), "disabled when null");
        System.out.println("AppServiceGudidTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
