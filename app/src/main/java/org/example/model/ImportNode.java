package org.example.model;

import java.util.Objects;

public record ImportNode(boolean isStatic, boolean isStar, String qualifiedName,
                         String alias) implements ExpressionNode {

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isStatic) sb.append("static ");
        sb.append(qualifiedName);
        if (isStar) sb.append("*");
        String simpleName =
                qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);

        if (alias != null && !alias.equals(simpleName)) {
            sb.append(" as ").append(alias);
        }
        return sb.toString();
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImportNode other)) return false;

        return isStatic == other.isStatic
                && isStar == other.isStar
                && Objects.equals(qualifiedName, other.qualifiedName)
                && Objects.equals(alias, other.alias);
    }

    @Override
    public String identityKey() {
        return "Import" +
                "|static=" + isStatic +
                "|star=" + isStar +
                "|qualifiedName=" + safe(qualifiedName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(qualifiedName, alias);
        result = 31 * result + Boolean.hashCode(isStatic);
        result = 31 * result + Boolean.hashCode(isStar);
        return result;
    }

    @Override
    public String contentKey() {
        return identityKey() +
                "|alias=" + safe(alias);
    }


}