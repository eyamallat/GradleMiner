package org.example.model;


import io.micrometer.core.lang.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record MapNode(Map<String, ExpressionNode> entries) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        if (entries.isEmpty()) {
            return "[:]";
        }

        String content = entries.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue().toString())
                .collect(Collectors.joining(", "));

        return "[" + content + "]";
    }
    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapNode(Map<String, ExpressionNode> entries1))) return false;

        if (entries.size() != entries1.size()) return false;

        for (Map.Entry<String, ExpressionNode> e : entries.entrySet()) {
            ExpressionNode otherValue = entries1.get(e.getKey());

            if (otherValue == null) return false;

            if (!Objects.equals(e.getValue(), otherValue)) {
                return false;
            }
        }
        return true;
    }
    @Override
    public String identityKey() {
        return "Map" +
                "|size=" + sizeOf(entries) +
                "|ids=" + joinIdentity(entries);
    }
    @Override
    public int hashCode() {
        int result = 0;

        for (Map.Entry<String, ExpressionNode> e : entries.entrySet()) {
            int keyHash = e.getKey() != null ? e.getKey().hashCode() : 0;
            int valueHash = e.getValue() != null ? e.getValue().hashCode() : 0;

            result += keyHash ^ valueHash; // order-independent combination
        }
        return result;
    }
    @Override
    public String contentKey() {
        return "Map" +
                "|size=" + sizeOf(entries) +
                "|content=" + joinContent(entries);
    }


    private static int sizeOf(Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }

    private static String joinIdentity(Map<String, ExpressionNode> map) {
        if (map == null || map.isEmpty()) return "";

        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> safe(e.getKey()) + "=" + (e.getValue() == null ? "null" : e.getValue().identityKey()))
                .collect(Collectors.joining("||"));
    }

    private static String joinContent(Map<String, ExpressionNode> map) {
        if (map == null || map.isEmpty()) return "";

        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> safe(e.getKey()) + "=" + (e.getValue() == null ? "null" : e.getValue().contentKey()))
                .collect(Collectors.joining("||"));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }


}
