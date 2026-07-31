package org.commercialtracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BarcodeParserChain implements BarcodeParser {
    private static final char GS = 29;
    private static final char RS = 30;
    private static final char EOT = 4;
    private static final Pattern UPS = Pattern.compile("(?i)\\b1Z[0-9A-Z]{16}\\b");
    private static final Pattern USPS_INTL = Pattern.compile("(?i)\\b[A-Z]{2}\\d{9}US\\b");
    private static final Pattern USPS_IMPB = Pattern.compile("\\b9[1-4]\\d{18,22}\\b");
    private static final Pattern AMAZON = Pattern.compile("(?i)\\bTB[ACM][0-9A-Z]{8,}\\b");
    private static final Pattern FEDEX = Pattern.compile("(?<!\\d)(\\d{34}|\\d{22}|\\d{20}|\\d{15}|\\d{12})(?!\\d)");
    private static final Pattern DHL = Pattern.compile("(?<!\\d)\\d{10}(?!\\d)");
    private static final Pattern ANSI_TRACKING = Pattern.compile("31Z(\\d{20,34})");
    private static final Pattern WEIGHT = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)(LB|KG)");
    private static final Pattern PACKAGE_COUNT = Pattern.compile("(?<!\\d)(\\d{1,3}/\\d{1,3})(?!\\d)");
    private static final Pattern ADDRESS_NAME = Pattern.compile("(?:11Z|12Z)([A-Za-z][A-Za-z .,'-]{1,69})");

    public ParseResult parse(String input) {
        String raw = normalize(input);
        if (raw.length() == 0) return empty("EMPTY");

        ParseResult application = parseApplicationReference(raw);
        if (application.isSuccess()) return application;

        ParseResult ansi = parseAnsi(raw);
        if (ansi.isSuccess()) return ansi;

        ParseResult gs1 = parseGs1(raw);
        if (gs1.isSuccess()) return gs1;

        return parseCarrierOrGeneric(raw);
    }

    static String normalize(String input) {
        if (input == null) return "";
        String value = input;
        while (value.endsWith("\r") || value.endsWith("\n")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.trim();
    }

    private ParseResult parseApplicationReference(String raw) {
        if (raw.regionMatches(true, 0, "PKG|", 0, 4)) {
            String[] parts = raw.split("\\|");
            String tracking = parts.length == 0 ? "" : parts[parts.length - 1].trim();
            Map<String, String> meta = new LinkedHashMap<String, String>();
            meta.put("labelType", "Application package reference");
            return classified(tracking, meta, "APPLICATION_REFERENCE");
        }
        return empty("NOT_APPLICATION_REFERENCE");
    }

    private ParseResult parseAnsi(String raw) {
        if (!raw.startsWith("[)>") && raw.indexOf("31Z") < 0) return empty("NOT_ANSI");
        Map<String, String> meta = new LinkedHashMap<String, String>();
        meta.put("labelType", "ANSI MH10 / carrier 2D label");
        String flattened = raw.replace(GS, ' ').replace(RS, ' ').replace(EOT, ' ');
        Matcher weight = WEIGHT.matcher(flattened);
        if (weight.find()) meta.put("weight", weight.group());
        Matcher count = PACKAGE_COUNT.matcher(flattened);
        if (count.find()) meta.put("packageCount", count.group(1));
        Matcher name = ADDRESS_NAME.matcher(flattened);
        if (name.find()) meta.put("addressee", name.group(1).trim());

        Matcher tracking = ANSI_TRACKING.matcher(raw);
        if (tracking.find()) {
            return classified(tracking.group(1), meta, "ANSI_MH10_31Z");
        }
        ParseResult carrier = parseCarrierOrGeneric(flattened);
        if (carrier.isSuccess()) {
            meta.putAll(carrier.getMetadata());
            return new ParseResult(carrier.getTrackingNumber(), carrier.getCarrier(),
                    carrier.getConfidence(), "ANSI_MH10+" + carrier.getSource(),
                    carrier.isConfirmationRequired(), meta);
        }
        return empty("ANSI_WITHOUT_TRACKING");
    }

    private ParseResult parseGs1(String raw) {
        Map<String, String> elements;
        if (raw.indexOf('(') >= 0) {
            elements = parseParenthesizedGs1(raw);
        } else if (raw.indexOf(GS) >= 0 || raw.startsWith("00") || raw.startsWith("01")
                || raw.startsWith("401") || raw.startsWith("402")) {
            elements = parseRawGs1(raw);
        } else {
            return empty("NOT_GS1");
        }
        if (elements.isEmpty()) return empty("UNPARSED_GS1");

        Map<String, String> meta = new LinkedHashMap<String, String>();
        meta.put("labelType", "GS1");
        copy(elements, meta, "00", "sscc");
        copy(elements, meta, "01", "gtin");
        copy(elements, meta, "401", "consignmentNumber");
        copy(elements, meta, "402", "shipmentNumber");
        copy(elements, meta, "403", "routingCode");
        copy(elements, meta, "420", "shipToPostalCode");
        copy(elements, meta, "421", "shipToCountryAndPostalCode");
        copy(elements, meta, "4300", "shipToCompany");
        copy(elements, meta, "4301", "shipToContact");
        copy(elements, meta, "4302", "shipToAddress1");
        copy(elements, meta, "4303", "shipToAddress2");
        copy(elements, meta, "4304", "shipToSuburb");
        copy(elements, meta, "4305", "shipToLocality");
        copy(elements, meta, "4306", "shipToRegion");

        String tracking = first(elements, "401", "402", "00");
        if (tracking.length() == 0) return empty("GS1_WITHOUT_SHIPMENT_ID");
        ParseResult classified = classified(tracking, meta, "GS1");
        ParseResult.Confidence confidence = validGs1CheckDigit(elements, tracking)
                ? ParseResult.Confidence.VERIFIED : ParseResult.Confidence.HIGH;
        return new ParseResult(tracking, classified.getCarrier(), confidence,
                "GS1_" + (elements.containsKey("401") ? "401" : elements.containsKey("402") ? "402" : "00"),
                false, meta);
    }

    private Map<String, String> parseParenthesizedGs1(String raw) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        Matcher matcher = Pattern.compile("\\((\\d{2,4})\\)([^()]*)").matcher(raw);
        while (matcher.find()) values.put(matcher.group(1), matcher.group(2).trim());
        return values;
    }

    private Map<String, String> parseRawGs1(String raw) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        String value = raw;
        if (value.startsWith("]C1") || value.startsWith("]d2")) value = value.substring(3);
        int index = 0;
        while (index < value.length()) {
            if (value.charAt(index) == GS) { index++; continue; }
            AiDef def = identifyAi(value, index);
            if (def == null) break;
            index += def.ai.length();
            int end;
            if (def.fixedLength >= 0) {
                end = Math.min(value.length(), index + def.fixedLength);
                if (end - index < def.fixedLength) break;
            } else {
                int separator = value.indexOf(GS, index);
                end = separator < 0 ? Math.min(value.length(), index + def.maxLength)
                        : Math.min(separator, index + def.maxLength);
            }
            values.put(def.ai, value.substring(index, end));
            index = end;
        }
        return values;
    }

    private AiDef identifyAi(String value, int index) {
        String[] variable = {"4300","4301","4302","4303","4304","4305","4306","401","403","420","421","10","21","30","37"};
        int[] max =          {70,    70,    70,    70,    70,    70,    70,    30,   30,   20,   15,   20,  20,  8,   8};
        for (int i = 0; i < variable.length; i++) {
            if (value.startsWith(variable[i], index)) return new AiDef(variable[i], -1, max[i]);
        }
        String[] fixed = {"402","410","00","01","17"};
        int[] lengths = {17,13,18,14,6};
        for (int i = 0; i < fixed.length; i++) {
            if (value.startsWith(fixed[i], index)) return new AiDef(fixed[i], lengths[i], lengths[i]);
        }
        if (index + 4 <= value.length() && value.substring(index, index + 3).matches("33[0-6]")
                && Character.isDigit(value.charAt(index + 3))) {
            return new AiDef(value.substring(index, index + 4), 6, 6);
        }
        return null;
    }

    private ParseResult parseCarrierOrGeneric(String raw) {
        String upper = raw.toUpperCase(Locale.US).replace(" ", "");
        Matcher matcher = UPS.matcher(upper);
        if (matcher.find()) return result(matcher.group(), "UPS", ParseResult.Confidence.HIGH, "UPS_1Z", false);
        matcher = AMAZON.matcher(upper);
        if (matcher.find()) return result(matcher.group(), "Amazon", ParseResult.Confidence.HIGH, "AMAZON_TBA", false);
        matcher = USPS_INTL.matcher(upper);
        if (matcher.find()) return result(matcher.group(), "USPS", ParseResult.Confidence.HIGH, "USPS_S10", false);
        matcher = USPS_IMPB.matcher(upper);
        if (matcher.find()) return result(matcher.group(), "USPS", ParseResult.Confidence.HIGH, "USPS_IMPB", false);
        matcher = FEDEX.matcher(upper);
        if (matcher.find()) return result(matcher.group(1), "FedEx", ParseResult.Confidence.MEDIUM, "FEDEX_LENGTH", false);
        matcher = DHL.matcher(upper);
        if (matcher.find()) return result(matcher.group(), "DHL", ParseResult.Confidence.MEDIUM, "DHL_10_DIGIT", true);
        if (upper.matches("[A-Z0-9-]{6,40}")) {
            return result(upper, "Other", ParseResult.Confidence.LOW, "GENERIC", true);
        }
        return empty("NO_SUPPORTED_IDENTIFIER");
    }

    private ParseResult classified(String tracking, Map<String, String> metadata, String source) {
        ParseResult base = parseCarrierOrGeneric(tracking);
        if (!base.isSuccess()) return empty(source + "_UNCLASSIFIED");
        Map<String, String> merged = new LinkedHashMap<String, String>(metadata);
        merged.putAll(base.getMetadata());
        return new ParseResult(base.getTrackingNumber(), base.getCarrier(), base.getConfidence(),
                source + "+" + base.getSource(), base.isConfirmationRequired(), merged);
    }

    private ParseResult result(String tracking, String carrier, ParseResult.Confidence confidence,
                               String source, boolean confirmation) {
        return new ParseResult(tracking, carrier, confidence, source, confirmation,
                Collections.<String, String>emptyMap());
    }

    private ParseResult empty(String source) {
        return result("", "", ParseResult.Confidence.NONE, source, false);
    }

    private static void copy(Map<String, String> from, Map<String, String> to, String key, String name) {
        if (from.containsKey(key)) to.put(name, from.get(key));
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) if (values.containsKey(key)) return values.get(key);
        return "";
    }

    private static boolean validGs1CheckDigit(Map<String, String> values, String selected) {
        if (!(values.containsKey("00") || values.containsKey("01") || values.containsKey("402"))) return false;
        if (!selected.matches("\\d+")) return false;
        int sum = 0;
        boolean triple = true;
        for (int i = selected.length() - 2; i >= 0; i--) {
            int digit = selected.charAt(i) - '0';
            sum += digit * (triple ? 3 : 1);
            triple = !triple;
        }
        return ((10 - (sum % 10)) % 10) == selected.charAt(selected.length() - 1) - '0';
    }

    private static final class AiDef {
        final String ai;
        final int fixedLength;
        final int maxLength;
        AiDef(String ai, int fixedLength, int maxLength) {
            this.ai = ai;
            this.fixedLength = fixedLength;
            this.maxLength = maxLength;
        }
    }
}
