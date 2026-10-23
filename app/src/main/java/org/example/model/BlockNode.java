package org.example.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;


public record BlockNode(List<ExpressionNode> statements) implements ExpressionNode {

    @Override
    public @NonNull String toString() {
        String content = statements.stream()
                .map(Object::toString)
                .collect(Collectors.joining("; "));

        return "{ " + content + " }";
    }
    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockNode(List<ExpressionNode> statements1))) return false;

        if (statements.size() != statements1.size()) return false;

        for (int i = 0; i < statements.size(); i++) {
            if (!Objects.equals(statements.get(i), statements1.get(i))) {
                return false;
            }

        }

        return true;
    }
    @Override
    public String identityKey() {
        String ids = statements.stream()
                .map(stmt -> stmt == null ? "null" : stmt.identityKey())
                .collect(Collectors.joining("||"));

        return "Block" +
                "|size=" + statements.size() +
                "|ids=" + ids;
    }

    @Override
    public String contentKey() {
        String contents = statements.stream()
                .map(stmt -> stmt == null ? "null" : stmt.contentKey())
                .collect(Collectors.joining("||"));

        return "Block" +
                "|size=" + statements.size() +
                "|content=" + contents;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (ExpressionNode stmt : statements) {
            result = 31 * result + (stmt != null ? stmt.hashCode() : 0);
        }
        return result;
    }
}