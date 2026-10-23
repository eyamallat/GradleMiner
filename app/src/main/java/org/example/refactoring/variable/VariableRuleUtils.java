package org.example.refactoring.variable;

import org.example.model.*;
import org.example.refactoring.RefactoringContext;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public final class VariableRuleUtils {
    private VariableRuleUtils() {
    }

    public static boolean sameVariableIdentity(DSLNode a, DSLNode b) {
        if (!isVariableLikeBinding(a) || !isVariableLikeBinding(b)) return false;

        String aName = variableLikeName(a);
        String bName = variableLikeName(b);

        ExpressionNode aValue = variableLikeValue(a);
        ExpressionNode bValue = variableLikeValue(b);

        return sameLogicalVariable(aName, bName)
                || equivalent(aValue, bValue);
    }

    public static boolean isStandaloneVariableCandidate(DSLNode removed, DSLNode added) {
        if (!isVariableNode(removed) || !isVariableNode(added)) return false;
        return !isMethodLikeAncestor(removed.parent) && !isMethodLikeAncestor(added.parent);
    }

    public static boolean alreadyExistedInOldTargetFile(DSLNode target, DSLNode oldRoot) {
        if (target == null || oldRoot == null) return false;
        for (DSLNode existing : flatten(oldRoot)) {
            if (!sameFile(existing, target)) continue;
            if (!isVariableNode(existing)) continue;
            if (sameVariableName(existing, target)) return true;
        }
        return false;
    }

    public static boolean isSettingsGradleNode(DSLNode node) {
        DSLNode file = fileRootOf(node);
        if (file == null) return false;

        String path = normalizePath(file.relativePath);
        return path.equals("settings.gradle")
                || path.equals("settings.gradle.kts")
                || path.endsWith("/settings.gradle")
                || path.endsWith("/settings.gradle.kts");
    }

    public static boolean nodeUsesVariableReference(DSLNode node, String variableName) {
        String target = normalizeReferenceName(variableName);
        if (node == null || safe(target).isBlank()) return false;
        for (ExpressionNode expr : comparableExpressions(node)) {
            if (expr != null && usesInExpr(expr, target)) return true;
        }
        return false;
    }

    public record ValueProvider(String referenceName, ExpressionNode value, DSLNode node) {
    }

    public static List<ValueProvider> valueProvidersFor(DSLNode addedVariable) {
        List<ValueProvider> out = new ArrayList<>();

        String base = normalizeReferenceName(addedVariable.name);
        ExpressionNode init = extractInitializer(addedVariable);

        if (safe(base).isBlank() || init == null) return out;

        out.add(new ValueProvider(base, init, addedVariable));

        if (init instanceof MapNode(Map<String, ExpressionNode> entries)) {
            for (Map.Entry<String, ExpressionNode> e : entries.entrySet()) {
                String key = e.getKey();
                ExpressionNode value = e.getValue();

                if (safe(key).isBlank() || value == null) continue;

                out.add(new ValueProvider(base + "." + key, value, addedVariable));
            }
        }

        return out;
    }

    public static boolean isVariableExtractionEvidence(DSLNode before, DSLNode after, DSLNode addedVariable, RefactoringContext context) {
        return isVariableExtractionEvidence(before, after, addedVariable, context, false);
    }

    public static boolean isVariableExtractionEvidence(DSLNode before, DSLNode after, DSLNode addedVariable, RefactoringContext context, boolean rejectRemovedMethodSource) {
        if (before == null || after == null || !isVariableEntity(addedVariable)) {
            return false;
        }

        for (ValueProvider provider : valueProvidersFor(addedVariable)) {
            String ref = provider.referenceName();

            if (!nodeUsesVariableReference(after, ref)) continue;
            if (nodeUsesVariableReference(before, ref)) continue;

            if (replacedByVariableInAlignedSlots(before, after, provider.value(), ref, provider.node(), context, rejectRemovedMethodSource)) {
                return true;
            }
        }
        return false;
    }

    private static List<ExpressionNode> comparableExpressions(DSLNode node) {
        LinkedHashSet<ExpressionNode> out = new LinkedHashSet<>();
        if (node == null) return List.of();

        if (node.value != null) out.add(node.value);
        if (node.receiver != null) out.add(node.receiver);
        out.addAll(safeList(node.arguments));

        for (ExpressionNode s : semanticExpressionSlots(node).values()) {
            if (s != null) out.add(s);
        }

        List<ExpressionNode> expanded = new ArrayList<>();
        for (ExpressionNode e : out) {
            collectExprTree(e, expanded);
        }
        return expanded;
    }

    private static void collectExprTree(ExpressionNode expr, List<ExpressionNode> out) {
        if (expr == null) return;
        out.add(expr);
        for (ExpressionNode c : childExpressions(expr)) {
            collectExprTree(c, out);
        }
    }
}
