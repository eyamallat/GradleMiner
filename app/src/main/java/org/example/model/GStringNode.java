package org.example.model;

import org.jspecify.annotations.NonNull;

public record GStringNode(String text) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        return text;
    }
    @Override
    public void print(String indent) {
        System.out.println(indent + this.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GStringNode(String text1))) return false;

        return java.util.Objects.equals(text, text1);
    }
    @Override
    public String identityKey() {
        return "GString|text=" + normalize(text);
    }

    @Override
    public String contentKey() {
        return identityKey(); // same for strings
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
    @Override
    public int hashCode() {return java.util.Objects.hashCode(text);
    }}
