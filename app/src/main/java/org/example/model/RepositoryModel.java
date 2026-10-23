package org.example.model;

import java.util.Objects;

public final class RepositoryModel implements ExpressionNode {
    public String kind;
    public String name;
    public String url;
    public ExpressionNode notation;

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public String identityKey() {
        return "Repository"
                + "|kind=" + safe(kind)
                + "|name=" + safe(name)
                + "|url=" + safe(url);
    }

    @Override
    public String contentKey() {
        return identityKey()
                + "|notation=" + keyOfContent(notation);
    }

    @Override
    public String toString() {
        return contentKey();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepositoryModel other)) return false;
        return Objects.equals(kind, other.kind)
                && Objects.equals(name, other.name)
                && Objects.equals(url, other.url)
                && Objects.equals(notation, other.notation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, url, notation);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }
}
