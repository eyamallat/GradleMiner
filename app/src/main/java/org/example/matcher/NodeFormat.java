package org.example.matcher;

import org.example.model.BinaryExpressionNode;
import org.example.model.DSLNode;
import org.example.model.ExpressionNode;
import org.example.model.MethodCallNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.matcher.NodeSimilarity.trim;
import static org.example.refactoring.RuleUtils.safeList;

public class NodeFormat{

    static <K, V> Map<K, V> safeMap(Map<K, V> map) {
        return map == null ? Collections.emptyMap() : map;
    }
    static List<String> safeStringList(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
    static String safe(Object o) {
        return o == null ? null : o.toString().trim();
    }

    public static String exprContent(ExpressionNode e) {
        e = unwrapCasts(e);
        return e == null ? "" : e.contentKey();
    }
    public static String formatNode(DSLNode n) {
        String value = n.value != null ? " = " + n.value : "";
        String parent = n.parent != null ? " parent=" + n.parent.type + ":" + n.parent.name : "";
        return n.type + ":" + n.name + value + " (line " + n.line + "," + parent + ")";
    }

    private static String argsContent(List<ExpressionNode> args) {
        return safeList(args).stream()
                .map(NodeFormat::exprContent)
                .collect(Collectors.joining("|"));
    }
    public static String normalizedSemantic(DSLNode n) {
        if (n == null || n.semantic == null) return null;
        if (n.semantic instanceof ExpressionNode expr) {
            return expr.contentKey();
        }
        return trim(n.semantic.toString());
    }
    public static ExpressionNode unwrapCasts(ExpressionNode expr) {
        if (expr == null) return null;

        // adapt this branch to your actual AST shape for "as int"
        if (expr instanceof BinaryExpressionNode b) {
            if ("as".equals(b.operation())) {
                return unwrapCasts(b.left());
            }
        }

        if (expr instanceof MethodCallNode m) {
            // only if your parser models cast/coercion this way
            if ("asType".equals(m.name()) && m.target() != null) {
                return unwrapCasts(m.target());
            }
        }

        return expr;
    }

    //REFACTOR
    public static String nodeContentKey(DSLNode n) {
        if (n == null) return "null";
        return "DSL"
            + "|type=" + n.type
            + "|name=" + safe(n.name)
            + "|receiver=" + exprContent(n.receiver)
            + "|value=" + exprContent(n.value)
            + "|args=" + argsContent(n.arguments)
            + "|semantic=" + normalizedSemantic(n);
    }

}
