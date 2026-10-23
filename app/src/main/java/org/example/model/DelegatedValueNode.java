package org.example.model;

import org.jspecify.annotations.NonNull;

public record DelegatedValueNode(ExpressionNode delegate) implements ExpressionNode {
    @Override
    public @NonNull String toString() {
        return "by " + delegate;
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public String identityKey() {
        return "DelegatedValue|delegate=" + keyOfIdentity(delegate);
    }

    @Override
    public String contentKey() {
        return "DelegatedValue|delegate=" + keyOfContent(delegate);
    }

    private static String keyOfIdentity(ExpressionNode n) {
        return n == null ? "null" : n.identityKey();
    }

    private static String keyOfContent(ExpressionNode n) {
        return n == null ? "null" : n.contentKey();
    }
}
