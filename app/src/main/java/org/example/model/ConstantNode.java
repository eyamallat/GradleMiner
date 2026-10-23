package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record ConstantNode(Object value) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        return value == null ? "null" : value.toString();
    }

    @Override
    public void print(String indent) {
        System.out.print(indent + this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConstantNode(Object value1))) return false;

        return Objects.equals(value, value1);
    }
    @Override
    public String identityKey() {
        return "Const|v=" + normalize(value);
    }
    private static String normalize(Object v) {
        if (v == null) return "null";

        if (v instanceof Number n) {
            return n.toString();
        }

        // Normalize booleans
        if (v instanceof Boolean b) {
            return b.toString();
        }

        return v.toString().trim();
    }

    @Override
    public String contentKey() {
        return identityKey();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}