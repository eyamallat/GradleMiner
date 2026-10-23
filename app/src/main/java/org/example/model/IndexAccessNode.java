package org.example.model;

import org.jspecify.annotations.NonNull;

public record IndexAccessNode(ExpressionNode target, ExpressionNode key) implements ExpressionNode {
    @Override
    public @NonNull String toString() {
        return target + "[" + key + "]";
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public String identityKey() {
        return "IndexAccess|target=" + keyOfIdentity(target)
                + "|key=" + keyOfIdentity(key);
    }

    @Override
    public String contentKey() {
        return "IndexAccess|target=" + keyOfContent(target)
                + "|key=" + keyOfContent(key);
    }

    private static String keyOfIdentity(ExpressionNode n) {
        return n == null ? "null" : n.identityKey();
    }

    private static String keyOfContent(ExpressionNode n) {
        return n == null ? "null" : n.contentKey();
    }
}
