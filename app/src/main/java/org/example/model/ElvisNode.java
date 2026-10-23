package org.example.model;

import org.jspecify.annotations.NonNull;

public record ElvisNode(ExpressionNode primary, ExpressionNode fallback) implements ExpressionNode {
    @Override
    public @NonNull String toString() {
        return primary + " ?: " + fallback;
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public String identityKey() {
        return "Elvis|primary=" + keyOfIdentity(primary)
                + "|fallback=" + keyOfIdentity(fallback);
    }

    @Override
    public String contentKey() {
        return "Elvis|primary=" + keyOfContent(primary)
                + "|fallback=" + keyOfContent(fallback);
    }

    private static String keyOfIdentity(ExpressionNode n) {
        return n == null ? "null" : n.identityKey();
    }

    private static String keyOfContent(ExpressionNode n) {
        return n == null ? "null" : n.contentKey();
    }
}
