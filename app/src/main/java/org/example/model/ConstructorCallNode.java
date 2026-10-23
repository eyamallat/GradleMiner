package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.stream.Collectors;

public record ConstructorCallNode(String typeName, List<ExpressionNode> arguments) implements ExpressionNode {

    public ConstructorCallNode(String typeName,
                               List<ExpressionNode> arguments) {
        this.typeName = typeName;
        this.arguments = List.copyOf(arguments);
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public @NonNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ").append(typeName).append("(");

        for (int i = 0; i < arguments.size(); i++) {
            sb.append(arguments.get(i));
            if (i < arguments.size() - 1) sb.append(", ");
        }

        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ConstructorCallNode(String name, List<ExpressionNode> arguments1))) return false;
        return typeName.equals(name)
                && arguments.equals(arguments1);
    }

    @Override
    public String identityKey() {
        return "Ctor" +
                "|type=" + safe(typeName) +
                "|arity=" + arguments.size();
    }

    @Override
    public String contentKey() {
        return "Ctor" +
                "|type=" + safe(typeName) +
                "|args=" + argumentsKey(arguments);
    }

    private static String argumentsKey(List<ExpressionNode> args) {
        return args.stream()
                .map(a -> a == null ? "null" : a.contentKey())
                .collect(Collectors.joining("|"));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

}