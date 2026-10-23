package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record TaskActionModel(String ownerTask, String actionKind, List<ExpressionNode> arguments) implements ExpressionNode {

    public TaskActionModel {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    @Override
    public String identityKey() {
        return "TaskAction"
                + "|owner=" + safe(ownerTask)
                + "|kind=" + safe(actionKind);
    }

    @Override
    public String contentKey() {
        return identityKey()
                + "|args=" + arguments.stream().map(TaskActionModel::expr).toList();
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
        return e == null ? "" : e.contentKey();
    }
}
