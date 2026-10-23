package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record TupleNode(List<ExpressionNode> values) implements ExpressionNode {
    @Override
    public @NonNull String toString() {
        if (values == null || values.isEmpty()) return "()";
        return values.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TupleNode(List<ExpressionNode> values1))) return false;

        if (values.size() != values1.size()) return false;

        for (int i = 0; i < values.size(); i++) {
            if (!Objects.equals(values.get(i), values1.get(i))) {
                return false;
            }
        }

        return true;
    }
    @Override
    public String identityKey() {
        return "Tuple" +
                "|size=" + sizeOf(values) +
                "|ids=" + joinIdentity(values);
    }

    @Override
    public String contentKey() {
        return "Tuple" +
                "|size=" + sizeOf(values) +
                "|content=" + joinContent(values);
    }

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String joinIdentity(List<ExpressionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";

        return nodes.stream()
                .map(n -> n == null ? "null" : n.identityKey())
                .collect(Collectors.joining("||"));
    }

    private static String joinContent(List<ExpressionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return "";

        return nodes.stream()
                .map(n -> n == null ? "null" : n.contentKey())
                .collect(Collectors.joining("||"));
    }
    @Override
    public int hashCode() {
        int result = 1;
        for (ExpressionNode v : values) {
            result = 31 * result + (v != null ? v.hashCode() : 0);
        }
        return result;
    }
}