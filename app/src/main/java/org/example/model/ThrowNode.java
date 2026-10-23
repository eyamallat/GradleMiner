package org.example.model;

import java.util.Objects;

public record ThrowNode(ExpressionNode expression) implements ExpressionNode {

    @Override
    public String toString() {
        return "throw " + expression;
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThrowNode other)) return false;

        return Objects.equals(expression, other.expression());
    }

    @Override
    public String identityKey() {
        return "Throw" +
                "|expr=" + keyOfIdentity(expression);
    }

    @Override
    public String contentKey() {
        return "Throw" +
                "|expr=" + keyOfContent(expression);
    }

    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }


}