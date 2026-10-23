package org.example.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EvaluationOutputWriter {
    private EvaluationOutputWriter() {
    }

    public static String outputLine(int processed, long total, String commitUrl, List<String> outputs) {
        return processed + "/" + total + " " + commitUrl + " -> " + outputOrNone(outputs);
    }

    public static String outputLine(int processed, long total, String commitUrl, List<String> outputs, String error) {
        return processed + "/" + total + " " + commitUrl + " -> "
                + (safe(error).isBlank() ? outputOrNone(outputs) : "(error: " + safe(error) + ")");
    }

    public static String outputOrError(String output, String error) {
        if (!safe(output).isBlank()) {
            return output;
        }
        if (!safe(error).isBlank()) {
            return "(error: " + safe(error) + ")";
        }
        return "(none)";
    }

    public static String outputOrNone(List<String> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return "(none)";
        }
        return String.join(" | ", outputs);
    }

    public static void createParentDirectories(Path path) throws Exception {
        if (path != null && path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
    }

    public static String normalizeCommitUrl(String url) {
        String value = safe(url);
        if (value.isBlank()) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return "https://" + value;
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
