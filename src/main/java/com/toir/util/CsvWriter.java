package com.toir.util;

import java.util.List;
import java.util.function.Function;

/**
 * Tiny CSV builder. Handles quoting, escaping embedded quotes/newlines/semicolons.
 * Uses comma as separator and BOM for Excel compatibility with Cyrillic.
 */
public final class CsvWriter {

    private CsvWriter() {}

    public static <T> String build(List<String> headers, List<T> rows, List<Function<T, Object>> extractors) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM for Excel
        sb.append(String.join(",", headers.stream().map(CsvWriter::escape).toList())).append('\n');
        for (T row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < extractors.size(); i++) {
                if (i > 0) line.append(',');
                Object v = extractors.get(i).apply(row);
                line.append(escape(v == null ? "" : v.toString()));
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String out = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + out + "\"" : out;
    }
}
