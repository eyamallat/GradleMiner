package org.example.model;

import java.util.Objects;

public non-sealed class BinaryExpressionNode implements ExpressionNode {
    private final ExpressionNode left;
    private final String operation;
    private final ExpressionNode right;

    public BinaryExpressionNode(ExpressionNode left, String operation, ExpressionNode right) {
        this.left = left;
        this.operation = operation;
        this.right = right;
    }

    public ExpressionNode left() {
        return left;
    }

    public String operation() {
        return operation;
    }

    public ExpressionNode right() {
        return right;
    }

    @Override
    public String toString() {
        return left + " " + operation + " " + right;
    }

    @Override
    public void print(String indent) {
        System.out.print(indent + this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryExpressionNode other)) return false;

        return Objects.equals(operation, other.operation)
                && Objects.equals(left, other.left)
                && Objects.equals(right, other.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, operation, right);
    }

    @Override
    public String identityKey() {
        if ("=".equals(operation)) {
            return "Assign|target=" + keyOf(left);
        }

        return "BinaryExpr"
                + "|op=" + safe(operation)
                + "|left=" + keyOf(left)
                + "|right=" + keyOf(right);
    }

    @Override
    public String contentKey() {
        return "BinaryExpr"
                + "|op=" + safe(operation)
                + "|left=" + keyOf(left)
                + "|right=" + keyOf(right);
    }

    private static String keyOf(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
