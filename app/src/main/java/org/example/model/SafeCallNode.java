package org.example.model;

import org.jspecify.annotations.NonNull;

public record SafeCallNode(ExpressionNode receiver, ExpressionNode selector) implements ExpressionNode {
    @Override
    public @NonNull String toString() {
        return receiver + "?." + selector;
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public String identityKey() {
        return "SafeCall|receiver=" + keyOfIdentity(receiver)
                + "|selector=" + keyOfIdentity(selector);
    }

    @Override
    public String contentKey() {
        return "SafeCall|receiver=" + keyOfContent(receiver)
                + "|selector=" + keyOfContent(selector);
    }

    private static String keyOfIdentity(ExpressionNode n) {
        return n == null ? "null" : n.identityKey();
    }

    private static String keyOfContent(ExpressionNode n) {
        return n == null ? "null" : n.contentKey();
    }
}

