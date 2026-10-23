package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

public record TernaryExpressionNode(ExpressionNode condition, ExpressionNode trueExpression,
                                    ExpressionNode falseExpression) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        return condition + " ? " + trueExpression + " : " + falseExpression;
    }
    @Override
    public void print(String indent) {
        System.out.println(indent + this.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TernaryExpressionNode(
                ExpressionNode condition1, ExpressionNode expression, ExpressionNode falseExpression1
        ))) return false;

        return Objects.equals(condition, condition1)
                && Objects.equals(trueExpression, expression)
                && Objects.equals(falseExpression, falseExpression1);
    }

    @Override
    public int hashCode() {
        int result = (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (trueExpression != null ? trueExpression.hashCode() : 0);
        result = 31 * result + (falseExpression != null ? falseExpression.hashCode() : 0);
        return result;
    }


    @Override
    public String identityKey() {
        return "Ternary" +
                "|cond=" + keyOfIdentity(condition) +
                "|true=" + keyOfIdentity(trueExpression) +
                "|false=" + keyOfIdentity(falseExpression);
    }

    @Override
    public String contentKey() {
        return "Ternary" +
                "|cond=" + keyOfContent(condition) +
                "|true=" + keyOfContent(trueExpression) +
                "|false=" + keyOfContent(falseExpression);
    }

    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }


}
