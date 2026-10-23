package org.example.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record MethodCallNode(
        @Nullable ExpressionNode target,
        String name,
        List<ExpressionNode> arguments,
        List<String> typeArguments
) implements ExpressionNode {

    public MethodCallNode {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        typeArguments = typeArguments == null ? List.of() : List.copyOf(typeArguments);
    }

    @Override
    public @NonNull String toString() {
        BlockNode trailingClosure = null;
        List<ExpressionNode> otherArgs = new ArrayList<>();

        for (ExpressionNode arg : arguments) {
            if (arg instanceof BlockNode bn) {
                trailingClosure = bn;
            } else {
                otherArgs.add(arg);
            }
        }

        String argsString = otherArgs.stream().map(Object::toString).collect(Collectors.joining(", "));
        String prefix = target != null ? target + "." : "";
        String typeArgsString = typeArguments.isEmpty() ? "" : "<" + String.join(", ", typeArguments) + ">";

        StringBuilder sb = new StringBuilder();
        sb.append(prefix)
                .append(name)
                .append(typeArgsString)
                .append("(").append(argsString).append(")");

        if (trailingClosure != null) {
            sb.append(" ").append(trailingClosure);
        }

        return sb.toString();
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public String identityKey() {
        return "MethodCall"
                + "|target=" + keyOfIdentity(target)
                + "|name=" + safe(name)
                + "|typeArgs=" + joinTypeArguments(typeArguments)
                + "|arity=" + sizeOf(arguments);
    }

    @Override
    public String contentKey() {
        return "MethodCall"
                + "|target=" + keyOfContent(target)
                + "|name=" + safe(name)
                + "|typeArgs=" + joinTypeArguments(typeArguments)
                + "|args=" + joinArgumentContents(arguments);
    }

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }

    private static String joinArgumentContents(List<ExpressionNode> args) {
        if (args == null || args.isEmpty()) return "";
        return args.stream().map(arg -> arg == null ? "null" : arg.contentKey()).collect(Collectors.joining("||"));
    }

    private static String joinTypeArguments(List<String> args) {
        if (args == null || args.isEmpty()) return "";
        return args.stream().map(MethodCallNode::safe).collect(Collectors.joining(","));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodCallNode(
                ExpressionNode target1, String name1, List<ExpressionNode> arguments1, List<String> typeArguments1
        ))) return false;

        return Objects.equals(target, target1)
                && Objects.equals(name, name1)
                && Objects.equals(arguments, arguments1)
                && Objects.equals(typeArguments, typeArguments1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, name, arguments, typeArguments);
    }
}
