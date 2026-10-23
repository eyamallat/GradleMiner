package org.example.visitor;


import org.example.model.*;

import java.util.List;

public class visitorUtils{

    static String expressionTypeName(ExpressionNode node) {
        return switch (node) {
            case VariableNode(String name) -> name;
            default -> node == null ? null : node.toString();
        };
    }

    static String extractTaskTypeFromArgs(List<ExpressionNode> args) {
        if (args == null || args.isEmpty()) return null;

        for (ExpressionNode arg : args) {
            if (arg instanceof MapNode map) {
                ExpressionNode typeExpr = map.entries().get("type");
                if (typeExpr != null) {
                    return expressionTypeName(typeExpr);
                }
            }

            String direct = expressionTypeName(arg);
            if (direct != null
                    && !direct.isBlank()
                    && !(arg instanceof ConstantNode)
                    && !(arg instanceof GStringNode)
                    && !(arg instanceof MapNode)) {
                return direct;
            }
        }

        return null;
    }

    static boolean isTaskRelationCall(String name) {
        return "dependsOn".equals(name)
                || "mustRunAfter".equals(name)
                || "shouldRunAfter".equals(name)
                || "finalizedBy".equals(name);
    }

    static String extractTaskName(ExpressionNode arg) {
        if (arg == null) return null;

        if (arg instanceof ConstantNode c) {
            Object value = c.value();
            return value == null ? null : value.toString().trim();
        }

        if (arg instanceof GStringNode g) {
            return g.text() == null ? null : g.text().trim();
        }

        if (arg instanceof VariableNode v) {
            return v.name() == null ? null : v.name().trim();
        }

        if (arg instanceof MapNode map) {
            ExpressionNode name = map.entries().get("name");
            if (name == null) {
                name = map.entries().get("taskName");
            }
            if (name == null) {
                name = map.entries().get("path");
            }
            return extractTaskName(name);
        }

        if (arg instanceof MethodCallNode call) {
            if (call.name() != null && !call.name().isBlank()) {
                return call.name().trim();
            }
        }

        return null;
    }

    static boolean isGradleFileRoot(DSLNode node) {
        if (node == null) return false;

        return node.type == DSLNode.Type.BUILD_GRADLE
                || node.type == DSLNode.Type.SETTINGS_GRADLE
                || node.type == DSLNode.Type.INIT_GRADLE
                || node.type == DSLNode.Type.BUILD_LOGIC_GRADLE;
    }

    static String referenceText(ExpressionNode node) {
        if (node == null) {
            return null;
        }

        if (node instanceof VariableNode v) {
            return v.name();
        }

        if (node instanceof ConstantNode c) {
            Object value = c.value();
            return value == null ? null : String.valueOf(value);
        }

        if (node instanceof GStringNode g) {
            return g.text();
        }

        if (node instanceof PropertyNode p) {
            String left = referenceText(p.object());
            String right = referenceText(p.property());

            if (left == null || left.isBlank()) {
                return right;
            }

            if (right == null || right.isBlank()) {
                return left;
            }

            return left + "." + right;
        }

        if (node instanceof MethodCallNode m) {
            String target = referenceText(m.target());
            String name = m.name();

            if (target == null || target.isBlank()) {
                return name;
            }

            return target + "." + name;
        }

        return node.toString();
    }

    static String flattenReferenceChain(ExpressionNode node) {
        if (node instanceof VariableNode v) {
            return v.name();
        }
        if (node instanceof PropertyNode p) {
            String left = flattenReferenceChain(p.object());
            String right = flattenReferenceChain(p.property());

            if (left == null || right == null) return null;
            return left + "." + right;
        }
        if (node instanceof ConstantNode c) {
            Object v = c.value();
            return v == null ? null : String.valueOf(v);
        }
        return null;
    }

    static boolean isTaskActionCall(String name) {
        return "doFirst".equals(name) || "doLast".equals(name);
    }

}
