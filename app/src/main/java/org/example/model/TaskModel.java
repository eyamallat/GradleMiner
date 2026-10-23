package org.example.model;

import org.jspecify.annotations.NonNull;

import java.util.List;


public record TaskModel(
        String taskName,
        String bindingName,
        String taskType,
        String syntaxStyle,
        ExpressionNode receiver,
        List<ExpressionNode> arguments
) implements ExpressionNode {

    public TaskModel {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    @Override
    public String identityKey() {
        return "Task"
                + "|name=" + safe(taskName)
                + "|binding=" + safe(bindingName);
    }

    @Override
    public String contentKey() {
        return identityKey()
                + "|type=" + safe(taskType)
                + "|style=" + safe(syntaxStyle)
                + "|receiver=" + expr(receiver)
                + "|args=" + arguments.stream().map(TaskModel::expr).toList();
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