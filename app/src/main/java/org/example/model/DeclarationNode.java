package org.example.model;

import java.util.Objects;

public non-sealed class DeclarationNode implements ExpressionNode {

    private final String name;
    private final ExpressionNode value;

    public DeclarationNode(String name, ExpressionNode value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public ExpressionNode value() {
        return value;
    }

    @Override
    public void print(String indent) {
        if (value != null) {
            System.out.println(indent + "def " + name + " = " + value);
        } else {
            System.out.println(indent + "def " + name);
        }
    }

    @Override
    public String identityKey() {
        return "Decl" +
                "|name=" + safe(name);
    }

    @Override
    public String contentKey() {
        return "Decl" +
                "|name=" + safe(name) +
                "|value=" + keyOf(value);
    }

    private static String keyOf(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeclarationNode other)) return false;

        return Objects.equals(name, other.name)
                && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}