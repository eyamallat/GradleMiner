package org.example.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

public record ClosureNode(List<String> parameters, BlockNode body) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        if (parameters.isEmpty()) {
            return body.toString();
        }
        return String.join(", ", parameters) + " -> " + body;
    }


    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClosureNode(List<String> parameters1, BlockNode body1))) return false;
        return Objects.equals(parameters, parameters1)
                && Objects.equals(body, body1);


    }
    @Override
    public String identityKey() {
        return "Closure" +
                "|arity=" + parameters.size() +
                "|bodyId=" + keyOfIdentity(body);
    }

    @Override
    public String contentKey() {
        return "Closure" +
                "|params=" + normalizeParams(parameters) +
                "|bodyContent=" + keyOfContent(body);
    }

    private static String normalizeParams(List<String> params) {
        return params.stream()
                .map(ClosureNode::safe)
                .collect(Collectors.joining(","));
    }

    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
    @Override
    public int hashCode() {
        int result = parameters.hashCode();
        result = 31 * result + body.hashCode();
        return result;
    }
}