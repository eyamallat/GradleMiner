package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public record ConstructorDeclNode(String ownerType, List<String> parameterNames) implements ExpressionNode {

    public ConstructorDeclNode(String ownerType, List<String> parameterNames) {
        this.ownerType = ownerType == null ? "" : ownerType.trim();
        this.parameterNames = parameterNames == null ? List.of() : List.copyOf(parameterNames);
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public @NonNull String toString() {
        return ownerType + "(" + String.join(", ", parameterNames) + ")";
    }


    @Override
    public String identityKey() {
        return "Constructor|owner=" + normalize(ownerType) + "|arity=" + parameterNames.size();
    }

    @Override
    public String contentKey() {
        return "Constructor|owner=" + normalize(ownerType) +
                "|arity=" + parameterNames.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConstructorDeclNode(String type, List<String> names))) return false;
        return normalize(ownerType).equals(normalize(type))
                && parameterNames.size() == names.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalize(ownerType), parameterNames);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}