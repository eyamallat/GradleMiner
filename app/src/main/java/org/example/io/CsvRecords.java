package org.example.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvRecords {
    private CsvRecords() {
    }

    public static List<Map<String, String>> read(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();

            List<String> headers = parseRecord(headerLine);
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).isBlank()) {
                    headers.set(i, "H" + (i + 1));
                }
            }

            List<Map<String, String>> rows = new ArrayList<>();
            StringBuilder record = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (!record.isEmpty()) {
                    record.append('\n');
                }
                record.append(line);

                if (!hasBalancedQuotes(record)) {
                    continue;
                }

                List<String> values = parseRecord(record.toString());
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                rows.add(row);
                record.setLength(0);
            }

            return rows;
        }
    }

    private static List<String> parseRecord(String record) {
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }

        values.add(field.toString());
        return values;
    }

    private static boolean hasBalancedQuotes(CharSequence text) {
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '"') continue;

            if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        }
        return !inQuotes;
    }
}
