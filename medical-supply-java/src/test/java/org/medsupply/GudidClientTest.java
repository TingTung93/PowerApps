package org.medsupply;

public final class GudidClientTest {
    private static final String FIXTURE =
            "{\"gudid\":{\"device\":{"
            + "\"brandName\":\"XIENCE ALPINE\","
            + "\"companyName\":\"ABBOTT VASCULAR INC.\","
            + "\"deviceDescription\":\"Coronary stent system\","
            + "\"versionModelNumber\":\"1234\","
            + "\"catalogNumber\":\"CAT-9\","
            + "\"gmdnTerms\":{\"gmdn\":[{\"gmdnPTName\":\"Coronary artery stent, drug-eluting\"}]}"
            + "}}}";

    public static void main(String[] args) {
        parsesFixture();
        notFoundOnError();
        System.out.println("GudidClientTest: PASS");
    }

    private static void parsesFixture() {
        GudidClient client = new GudidClient("https://example/api", new GudidClient.Fetcher() {
            public String fetch(String url) {
                check(url.equals("https://example/api?di=00380740000010"), "url: " + url);
                return FIXTURE;
            }
        });
        GudidResult r = client.lookup("00380740000010");
        check(r.found, "found");
        check("XIENCE ALPINE".equals(r.brandName), "brand");
        check("ABBOTT VASCULAR INC.".equals(r.companyName), "company");
        check("XIENCE ALPINE".equals(r.suggestedName()), "suggested name");
        check(r.gmdnTerms.size() == 1, "one gmdn");
        check("Coronary artery stent, drug-eluting".equals(r.suggestedCategory()), "category");
    }

    private static void notFoundOnError() {
        GudidClient client = new GudidClient("https://example/api", new GudidClient.Fetcher() {
            public String fetch(String url) throws java.io.IOException {
                throw new java.io.IOException("offline");
            }
        });
        GudidResult r = client.lookup("00380740000010");
        check(!r.found, "offline -> not found, no throw");
        System.out.println("");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}
