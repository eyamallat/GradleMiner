package org.example.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record IfNode(
        ExpressionNode condition,
        List<ExpressionNode> thenBranch,
        List<ExpressionNode> elseBranch) implements ExpressionNode {

    @Override
    public void print(String indent) {
        String thenContent = (thenBranch == null || thenBranch.isEmpty()) ? "" :
                thenBranch.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("; "));

        String elseContent = (elseBranch == null || elseBranch.isEmpty()) ? "" :
                elseBranch.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("; "));

        StringBuilder sb = new StringBuilder(indent);
        sb.append("if").append(condition);

        if (!thenContent.isEmpty()) {
            sb.append("{ ").append(thenContent).append(" } ");
        }

        if (!elseContent.isEmpty()) {
            sb.append("else { ").append(elseContent).append(" }");
        }

        System.out.println(sb);
    }
    @Override
    public String identityKey() {
        return "If" +
                "|cond=" + keyOfIdentity(condition) +
                "|then=" + branchIdentityKey(thenBranch) +
                "|else=" + branchIdentityKey(elseBranch);
        }
    @Override
    public String contentKey() {
        return "If" +
                "|cond=" + keyOfContent(condition) +
                "|then=" + branchContentKey(thenBranch) +
                "|else=" + branchContentKey(elseBranch);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IfNode(
                ExpressionNode condition1, List<ExpressionNode> branch, List<ExpressionNode> elseBranch1
        ))) return false;

        if (!Objects.equals(condition, condition1)) return false;

        if (thenBranch.size() != branch.size()) return false;
        for (int i = 0; i < thenBranch.size(); i++) {
            if (!Objects.equals(thenBranch.get(i), branch.get(i))) {
                return false;
            }
        }

        // Compare ELSE branch
        if (elseBranch.size() != elseBranch1.size()) return false;
        for (int i = 0; i < elseBranch.size(); i++) {
            if (!Objects.equals(elseBranch.get(i), elseBranch1.get(i))) {
                return false;
            }
        }

        return true;
    }
    @Override
    public int hashCode() {
        int result = (condition != null ? condition.hashCode() : 0);

        for (ExpressionNode stmt : thenBranch) {
            result = 31 * result + (stmt != null ? stmt.hashCode() : 0);
        }

        for (ExpressionNode stmt : elseBranch) {
            result = 31 * result + (stmt != null ? stmt.hashCode() : 0);
        }

        return result;
    }


    private static String branchIdentityKey(List<ExpressionNode> branch) {
        if (branch == null || branch.isEmpty()) {
            return "";
        }
        return branch.stream()
                .map(n -> n == null ? "null" : n.identityKey())
                .collect(Collectors.joining("||"));
    }
    private static String keyOfIdentity(ExpressionNode node) {
        return node == null ? "null" : node.identityKey();
    }
    private static String keyOfContent(ExpressionNode node) {
        return node == null ? "null" : node.contentKey();
    }
    private static String branchContentKey(List<ExpressionNode> branch) {
        if (branch == null || branch.isEmpty()) {
            return "";
        }
        return branch.stream()
                .map(n -> n == null ? "null" : n.contentKey())
                .collect(Collectors.joining("||"));
    }


}
