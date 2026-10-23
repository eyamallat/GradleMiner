package org.example.refactoring.variable;

import org.example.matcher.MatchPair;
import org.example.matcher.MatchResult;
import org.example.model.*;
import org.example.refactoring.*;

import java.util.*;
import java.util.regex.Matcher;

import static org.example.matcher.NodeFormat.unwrapCasts;
import static org.example.refactoring.RuleUtils.*;

public class InlineVariable implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        MatchResult match = context.matchResult;
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (MatchPair p : match.matches) {
            if (p == null || p.left() == null || p.right() == null) continue;

            DSLNode before = p.left();
            DSLNode after = p.right();

            if (isDeletedSourceFile(before, context)) continue;
            List<String> refNames = inlineReferenceNames(before, after, context);
            if (refNames.isEmpty()) continue;

            for (String refName : refNames) {
                if (safe(refName).isBlank()) continue;

                String key = normalizePath(before.relativePath) + "|" + norm(refName) + "|" + nodeKey(before) + "->" + nodeKey(after);

                BuildRefactoring ref = out.computeIfAbsent(
                        key, k -> new BuildRefactoring(BuildRefactoringType.INLINE_VARIABLE, "Inline variable '" + refName + "' in '" + nodeLocationName(before) + "'"
                        )
                );

                if (!ref.relatedMatches.contains(p)) {
                    ref.relatedMatches.add(p);
                }

                addIfAbsent(ref.removedNodes, before);
                addIfAbsent(ref.addedNodes, after);
                for (DSLNode removedDef : removedVariableDefinitions(refName, context)) {
                    addIfAbsent(ref.removedNodes, removedDef);
                }
            }
        }

        return new ArrayList<>(out.values());
    }
    private static List<DSLNode> removedVariableDefinitions(String refName, RefactoringContext context) {
        if (safe(refName).isBlank() || context == null || context.matchResult == null) {
            return List.of();
        }

        List<DSLNode> out = new ArrayList<>();

        for (DSLNode n : context.matchResult.removedLeaves) {
            if (isRemovedVariableDefinition(refName, n)) {
                out.add(n);
            }
        }

        for (DSLNode n : context.matchResult.removedComposite) {
            if (isRemovedVariableDefinition(refName, n)) {
                out.add(n);
            }
        }

        return out;
    }

    private static boolean isRemovedVariableDefinition(String refName, DSLNode node) {
        if (node == null) return false;
        if (!isVariableNode(node)) return false;
        if (extractInitializer(node) == null) return false;

        String nodeName = normalizeReferenceName(node.name);

        return sameLogicalVariable(nodeName, refName);
    }
    public static List<String> inlineReferenceNames(DSLNode before, DSLNode after, RefactoringContext context) {
        if (before == null || after == null) return List.of();
        if (!sameConsumerRole(before, after)) return List.of();
        List<ExprSlot> beforeSlots = expressionSlots(before);
        List<ExprSlot> afterSlots = expressionSlots(after);
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (ExprSlot bs : beforeSlots) {
            for (ExprSlot as : afterSlots) {
                if (!Objects.equals(bs.key(), as.key())) continue;

                ExpressionNode beforeExpr = bs.expr();
                ExpressionNode afterExpr = as.expr();


                if (beforeExpr == null || afterExpr == null) continue;

                for (String ref : referenceNamesInExpr(beforeExpr)) {
                    if (nodeUsesReference(after, ref)) continue;
                    if (inlinesRemovedVariableValue(ref, afterExpr, context, before)) {
                        out.add(ref);
                        continue;
                    }
                    if (!isConcreteInlineValue(afterExpr)) continue;
                    if (slotAlreadyUsesVariableReference(afterExpr)) continue;
                    out.add(ref);


                }
            }}
        return new ArrayList<>(out);
    }
    private static boolean nodeUsesReference(DSLNode node, String ref) {
        if (node == null || safe(ref).isBlank()) return false;

        if (usesInExpr(node.value, ref)) return true;
        if (usesInExpr(node.receiver, ref)) return true;

        for (ExpressionNode arg : safeList(node.arguments)) {
            if (usesInExpr(arg, ref)) return true;
        }

        for (Map.Entry<String, ExpressionNode> e : semanticExpressionSlots(node).entrySet()) {
            if (usesInExpr(e.getValue(), ref)) return true;
        }

        for (DSLNode child : safeList(node.children)) {
            if (nodeUsesReference(child, ref)) return true;
        }

        return false;
    }
    private static boolean inlinesRemovedVariableValue(String refName, ExpressionNode afterExpr, RefactoringContext context, DSLNode consumerBefore) {
        if (safe(refName).isBlank() || afterExpr == null || context == null || context.matchResult == null) {
            return false;
        }

        for (DSLNode removed : removedVariableDefinitions(refName, context, consumerBefore)) {
            ExpressionNode initializer = extractInitializer(removed);
            if (initializer == null) continue;

            if (equivalentModuloWrapper(initializer, afterExpr, Map.of())) {
                return true;
            }
        }

        return false;
    }
    private static List<DSLNode> removedVariableDefinitions(String refName, RefactoringContext context, DSLNode consumerBefore) {
        List<DSLNode> out = new ArrayList<>();
        collectRemovedVariableDefinitions(out, refName, context.matchResult.removedLeaves, consumerBefore);
        collectRemovedVariableDefinitions(out, refName, context.matchResult.removedComposite, consumerBefore);
        return out;
    }
    private static void collectRemovedVariableDefinitions(List<DSLNode> out, String refName, Collection<DSLNode> nodes, DSLNode consumerBefore) {
        for (DSLNode node : safeCollection(nodes)) {
            if (node == null) continue;
            if (!isVariableNode(node)) continue;
            if (extractInitializer(node) == null) continue;
            if (consumerBefore != null && !sameFile(node, consumerBefore)) continue;

            String nodeName = normalizeReferenceName(node.name);
            if (sameLogicalVariable(nodeName, refName)) {
                out.add(node);
            }
        }
    }
    private static boolean slotAlreadyUsesVariableReference(ExpressionNode expr) {
        if (expr == null) return false;

        if (expr instanceof VariableNode) return true;
        if (expr instanceof PropertyNode) return true;

        if (expr instanceof GStringNode(String text)) {
            return GSTRING_REF_PATTERN.matcher(text).find();
        }

        if (expr instanceof ConstantNode(Object v)) {
            return v != null && GSTRING_REF_PATTERN.matcher(String.valueOf(v)).find();
        }

        for (ExpressionNode child : childExpressions(expr)) {
            if (slotAlreadyUsesVariableReference(child)) return true;
        }

        return false;
    }
    private static List<String> referenceNamesInExpr(ExpressionNode expr) {
        if (expr == null) return List.of();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectReferenceNames(expr, out);
        return new ArrayList<>(out);
    }
    private static void collectReferenceNames(ExpressionNode expr, Set<String> out) {
        if (expr == null) return;

        String direct = singleReferenceName(expr);
        if (!safe(direct).isBlank()) {
            out.add(direct);

            if (expr instanceof PropertyNode || expr instanceof IndexAccessNode || expr instanceof SafeCallNode) {
                return;
            }
        }

        collectEmbeddedReferenceNames(expr, out);

        for (ExpressionNode child : childExpressions(expr)) {
            collectReferenceNames(child, out);
        }
    }
    private static void collectEmbeddedReferenceNames(ExpressionNode expr, Set<String> out) {
        if (expr == null) return;

        if (expr instanceof GStringNode(String text)) {
            collectEmbeddedReferenceNames(text, out);
            return;
        }

        if (expr instanceof ConstantNode(Object value)) {
            if (value != null) {
                collectEmbeddedReferenceNames(String.valueOf(value), out);
            }
            return;
        }

        for (ExpressionNode child : childExpressions(expr)) {
            collectEmbeddedReferenceNames(child, out);
        }
    }
    private static void collectEmbeddedReferenceNames(String text, Set<String> out) {
        if (text == null || text.isBlank()) return;

        Matcher m = GSTRING_REF_PATTERN.matcher(text);

        while (m.find()) {
            String braced = m.group(1);
            String plain = m.group(2);

            String ref = normalizeEmbeddedReference(braced != null ? braced : plain);
            if (!safe(ref).isBlank()) {
                out.add(ref);
            }
        }
    }
    private static String singleReferenceName(ExpressionNode expr) {
        if (expr == null) return "";

        expr = unwrapCasts(expr);

        if (expr instanceof VariableNode(String name)) {
            return normalizeReferenceName(name);
        }

        if (expr instanceof PropertyNode p) {
            return normalizeReferenceName(expressionReferenceText(p));
        }
        return "";
    }


}
