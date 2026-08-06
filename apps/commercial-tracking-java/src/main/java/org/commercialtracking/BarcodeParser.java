package org.commercialtracking;

public interface BarcodeParser {
    ParseResult parse(String raw);
}
