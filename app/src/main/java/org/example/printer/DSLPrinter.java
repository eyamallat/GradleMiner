package org.example.printer;

import org.example.model.*;
import org.jspecify.annotations.NonNull;

import java.util.stream.Collectors;

public class DSLPrinter {

    public static void print(DSLNode node, int depth) {
        System.out.print(format(node, depth));
    }

    public static String format(DSLNode node) {
        return format(node, 0);
    }

    public static String format(DSLNode node, int depth) {
        StringBuilder builder = new StringBuilder();
        append(node, depth, builder);
        return builder.toString();
    }

    private static void append(DSLNode node, int depth, StringBuilder builder) {
        String indent = "  ".repeat(depth);
        String line;

        switch (node.type) {
            case DEPENDENCY_DECL -> {
                if (node.semantic instanceof DependencyModel dep) {
                    line = indent + node.type + " : "
                            + dep.toStructuredString()
                            + " (line " + node.line + ")";
                } else {
                    line = indent + node.type + " : " + node.name
                            + " (line " + node.line + ")";
                }
            }

            case PLUGIN_DECL, APPLY_SCRIPT -> {
                if (node.semantic instanceof PluginModel plugin) {
                    line = indent + node.type + " : "
                            + plugin.toStructuredString("").trim()
                            + " (line " + node.line + ")";
                } else {
                    line = indent + node.type + " : " + node.name
                            + " (line " + node.line + ")";
                }
            }
            case TASK_DECLARATION, TASK_CONFIGURATION -> {
                if (node.semantic instanceof TaskModel task) {
                    String details = getString(task);

                    line = indent + node.type + " : " + details
                            + " (line " + node.line + ")";
                } else {
                    line = indent + node.type + " : taskName=" + safe(node.name)
                            + " (line " + node.line + ")";
                }
            }
            case TASK_ACTION -> {
                if (node.semantic instanceof TaskActionModel action) {
                    String details = "action=" + safe(action.actionKind());

                    if (!safe(action.ownerTask()).isBlank()) {
                        details += ", owner=" + safe(action.ownerTask());
                    }

                    line = indent + node.type + " : " + details
                            + " (line " + node.line + ")";
                } else {
                    line = indent + node.type + " : action=" + safe(node.name)
                            + " (line " + node.line + ")";
                }
            }


            case TASK_RELATION -> {
                if (node.semantic instanceof TaskRelationModel(
                        String ownerTask, String relationKind, java.util.List<ExpressionNode> targets1
                )) {
                    String details = "relation=" + safe(relationKind);

                    if (!safe(ownerTask).isBlank()) {
                        details += ", owner=" + safe(ownerTask);
                    }

                    String targets = targets1.stream()
                            .map(DSLPrinter::renderExpr)
                            .collect(Collectors.joining(", "));

                    if (!targets.isBlank()) {
                        details += ", targets=" + targets;
                    }

                    line = indent + node.type + " : " + details
                            + " (line " + node.line + ")";
                } else {
                    line = indent + node.type + " : relation=" + safe(node.name)
                            + " (line " + node.line + ")";
                }
            }

            case ASSIGNMENT, DECLARATION -> {
                if (node.value instanceof DelegatedValueNode(ExpressionNode delegate)) {
                    String declaredType = !safe(node.declaredType).isBlank() ? ", type: " + safe(node.declaredType) : "";
                    line = indent + node.type + " : name: " + safe(node.name)
                            + declaredType
                            + ", delegatedBy: " + renderExpr(delegate)
                            + " (line " + node.line + ")";
                } else {
                    String valueStr = node.value != null ? renderExpr(node.value) : "";

                    String operator = node.type == DSLNode.Type.ASSIGNMENT
                            && node.operator != null
                            && !node.operator.isBlank()
                            ? ", operator: " + node.operator
                            : "";
                    String declaredType = !safe(node.declaredType).isBlank() ? ", type: " + safe(node.declaredType) : "";

                    line = indent + node.type + " : name: " + safe(node.name)
                            + declaredType
                            + operator
                            + (valueStr.isBlank() ? "" : ", value: " + valueStr)
                            + " (line " + node.line + ")";
                }
            }


            case WHEN_BRANCH -> {
                String condition = node.arguments.stream()
                        .map(DSLPrinter::renderExpr)
                        .collect(Collectors.joining(", "));

                if (condition.isBlank()) {
                    condition = "else";
                }

                String result = renderExpr(node.value);

                line = indent + node.type + " : condition=" + condition
                        + ", result=" + result
                        + " (line " + node.line + ")";
            }
            case THROW -> {
                String thrown = node.value != null ? renderExpr(node.value) : "";
                line = indent + node.type + " : " + thrown
                        + " (line " + node.line + ")";
            }
            case BLOCK -> {
                if ("for".equals(node.name)) {
                    String iterator = node.arguments.stream()
                            .map(DSLPrinter::renderExpr)
                            .collect(Collectors.joining(", "));
                    String collection = node.value == null ? "" : renderExpr(node.value);

                    String details = "for";
                    if (!iterator.isBlank() || !collection.isBlank()) {
                        details += " (" + iterator
                                + (collection.isBlank() ? "" : " in " + collection)
                                + ")";
                    }

                    line = indent + node.type + " : " + details
                            + " (line " + node.line + ")";
                } else {
                    String args = node.arguments.stream()
                            .map(DSLPrinter::renderExpr)
                            .collect(Collectors.joining(", "));

                    String typeArgs = node.typeArguments == null || node.typeArguments.isEmpty()
                            ? ""
                            : "<" + String.join(", ", node.typeArguments) + ">";
                    String receiver = node.receiver == null
                            ? ""
                            : renderExpr(node.receiver) + (node.safeCall ? "?." : ".");


                    line = indent + node.type + " : "
                            + receiver
                            + node.name
                            + typeArgs
                            + (args.isEmpty() ? "" : "(" + args + ")")
                            + " (line " + node.line + ")";
                }
            }


            default -> {
                String args = node.arguments.stream()
                        .map(DSLPrinter::renderExpr)
                        .collect(Collectors.joining(", "));

                String typeArgs = node.typeArguments == null || node.typeArguments.isEmpty()
                        ? ""
                        : "<" + String.join(", ", node.typeArguments) + ">";
                String receiver = node.receiver == null
                        ? ""
                        : renderExpr(node.receiver) + (node.safeCall ? "?." : ".");


                line = indent + node.type + " : "
                        + receiver
                        + node.name
                        + typeArgs
                        + (args.isEmpty() ? "" : "(" + args + ")")
                        + " (line " + node.line + ")";
            }
        }

        builder.append(line).append(System.lineSeparator());

        for (DSLNode child : node.children) {
            append(child, depth + 1, builder);
        }
    }

    private static @NonNull String getString(TaskModel task) {
        String details = "";

        if (!safe(task.taskName()).isBlank()) {
            details += "taskName=" + safe(task.taskName());
        }
        if (!safe(task.bindingName()).isBlank()) {
            details += ", bindingName=" + safe(task.bindingName());
        }

        if (!safe(task.taskType()).isBlank()) {
            details += ", type=" + safe(task.taskType());
        }

        if (!safe(task.syntaxStyle()).isBlank()) {
            details += ", style=" + safe(task.syntaxStyle());
        }
        return details;
    }

    private static String renderExpr(ExpressionNode expr) {
        if (expr == null) return "";

        if (expr instanceof ConstantNode(Object v)) {
            if (v instanceof String s) {
                return "\"" + escapeString(s) + "\"";
            }
            return String.valueOf(v);
        }

        if (expr instanceof VariableNode(String name1)) {
            return name1;
        }

        if (expr instanceof GStringNode(String text)) {
            return "\"" + escapeString(text) + "\"";
        }

        if (expr instanceof DelegatedValueNode(ExpressionNode delegate)) {
            return "by " + renderExpr(delegate);
        }

        if (expr instanceof MethodCallNode(
                ExpressionNode target1, String name, java.util.List<ExpressionNode> arguments,
                java.util.List<String> typeArguments
        )) {
            String target = target1 == null ? "" : renderExpr(target1) + ".";
            String typeArgs = typeArguments.isEmpty()
                    ? ""
                    : "<" + String.join(", ", typeArguments) + ">";
            String args = arguments.stream()
                    .map(DSLPrinter::renderExpr)
                    .collect(Collectors.joining(", "));

            return target + name + typeArgs + "(" + args + ")";
        }

        if (expr instanceof ConstructorCallNode c) {
            String args = c.arguments().stream()
                    .map(DSLPrinter::renderExpr)
                    .collect(Collectors.joining(", "));

            return c.typeName() + "(" + args + ")";
        }

        if (expr instanceof PropertyNode(ExpressionNode object, ExpressionNode property)) {
            return renderExpr(object) + "." + renderExpr(property);
        }

        if (expr instanceof SafeCallNode(ExpressionNode receiver, ExpressionNode selector)) {
            return renderExpr(receiver) + "?." + renderExpr(selector);
        }

        if (expr instanceof IndexAccessNode(ExpressionNode target, ExpressionNode key)) {
            return renderExpr(target) + "[" + renderExpr(key) + "]";
        }

        if (expr instanceof ListNode(java.util.List<ExpressionNode> values)) {
            return values.stream()
                    .map(DSLPrinter::renderExpr)
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        if (expr instanceof MapNode(java.util.Map<String, ExpressionNode> entries)) {
            return entries.entrySet().stream()
                    .map(e -> e.getKey() + ": " + renderExpr(e.getValue()))
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        if (expr instanceof BinaryExpressionNode b) {
            return renderExpr(b.left()) + " " + b.operation() + " " + renderExpr(b.right());
        }

        if (expr instanceof ElvisNode(ExpressionNode primary, ExpressionNode fallback)) {
            return renderExpr(primary) + " ?: " + renderExpr(fallback);
        }

        return expr.toString();
    }

    private static String escapeString(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }


}
