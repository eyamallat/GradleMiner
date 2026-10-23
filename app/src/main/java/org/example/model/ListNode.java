package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record ListNode(List<ExpressionNode> values) implements ExpressionNode {
    @Override
    public @NonNull String toString() {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        // Convert each ExpressionNode to string recursively
        String inner = values.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return "[" + inner + "]";
    }
    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListNode other)) return false;
        return Objects.equals(contentKey(), other.contentKey());
    }

    @Override
    public int hashCode() {
        return contentKey().hashCode();
    }

    @Override
    public String identityKey() {
        return "List"
                + "|size=" + sizeOf(values)
                + "|ids=" + joinSortedIdentity(values);
    }

    @Override
    public String contentKey() {
        return "List"
                + "|size=" + sizeOf(values)
                + "|content=" + joinSortedContent(values);
    }

    private static String joinSortedIdentity(List<ExpressionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";
        return nodes.stream()
                .map(n -> n == null ? "null" : n.identityKey())
                .sorted()
                .collect(Collectors.joining("||"));
    }

    private static String joinSortedContent(List<ExpressionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";
        return nodes.stream()
                .map(n -> n == null ? "null" : n.contentKey())
                .sorted()
                .collect(Collectors.joining("||"));
    }
    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

}
