package org.commercialtracking;

public final class ParserTest {
    public static void main(String[] args) {
        BarcodeParserChain parser = new BarcodeParserChain();
        expect(parser, "1Z999AA10123456784", "1Z999AA10123456784", "UPS");
        expect(parser, "9400111899223856928499", "9400111899223856928499", "USPS");
        expect(parser, "EC123456789US", "EC123456789US", "USPS");
        expect(parser, "123456789012", "123456789012", "FedEx");
        expect(parser, "TBA123456789012", "TBA123456789012", "Amazon");
        expect(parser, "PKG|UPS|1Z999AA10123456784", "1Z999AA10123456784", "UPS");
        expect(parser, "PKGID-SYNTHETIC123", "SYNTHETIC123", "Application");
        expect(parser, "[)>\u001e01\u001d31Z12345678901234567890\u001d11ZJANE DOE\u001d2.5LB\u001e\u0004",
                "12345678901234567890", "FedEx");
        ParseResult gs1 = parser.parse("(401)ABC123456(420)98431");
        check(gs1.isSuccess(), "GS1 should parse");
        check("ABC123456".equals(gs1.getTrackingNumber()), "GS1 401");
        check("98431".equals(gs1.getMetadata().get("shipToPostalCode")), "GS1 postal");
        ParseResult rawGs1 = parser.parse("]C1401ABC123\u001d42098431");
        check("ABC123".equals(rawGs1.getTrackingNumber()), "Raw GS1 401");
        check("98431".equals(rawGs1.getMetadata().get("shipToPostalCode")), "Raw GS1 postal");
        String framed = "]C1401ABC123\u001d42098431\u001d";
        check(framed.equals(BarcodeParserChain.normalize(framed)), "meaningful trailing separator preserved");
        ParseResult unknown = parser.parse("NOT A VALID SCAN !");
        check(!unknown.isSuccess(), "invalid scan should fail");
        ParseResult evidence = parser.parse("1Z999AA10123456784");
        check("UPS_1Z".equals(evidence.getEvidence().get("trackingNumber").source), "field provenance");
        System.out.println("ParserTest: PASS");
    }

    private static void expect(BarcodeParser parser, String raw, String tracking, String carrier) {
        ParseResult result = parser.parse(raw);
        check(result.isSuccess(), "Expected parse success for " + raw);
        check(tracking.equals(result.getTrackingNumber()), "Tracking mismatch: " + result.getTrackingNumber());
        check(carrier.equals(result.getCarrier()), "Carrier mismatch: " + result.getCarrier());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
