package org.example.model;

import org.jspecify.annotations.NonNull;

public record VariableNode(String name) implements ExpressionNode{
    @Override
    public @NonNull String toString() {
        return name;
    }
    @Override
    public void print(String indent) {
        System.out.print(indent + this);
    }
    @Override
    public String identityKey() {
        return "Var|name=" + safe(name);
    }

    @Override
    public String contentKey() {
        // same as identity for variables
        return identityKey();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableNode(String name1))) return false;

        return java.util.Objects.equals(name, name1);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(name);
    }

}
