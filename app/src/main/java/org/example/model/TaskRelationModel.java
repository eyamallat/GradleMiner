package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.stream.Collectors;

public record TaskRelationModel(
        String ownerTask,
        String relationKind,
        List<ExpressionNode> targets
) implements ExpressionNode {

    public TaskRelationModel {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    @Override
    public String identityKey() {
        return "TaskRelation"
                + "|owner=" + safe(ownerTask)
                + "|kind=" + safe(relationKind)
                + "|targets=" + targets.stream()
                .map(TaskRelationModel::expr)
                .collect(Collectors.joining(","));
    }

    @Override
    public String contentKey() {
        return identityKey();
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + this);
    }

    @Override
    public @NonNull String toString() {
        return contentKey();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String expr(ExpressionNode e) {
        return e == null ? "" : e.identityKey();
    }
}
