package org.example.refactoring.subroutine;

import org.example.matcher.MatchPair;
import org.example.model.*;
import org.example.refactoring.*;

import java.util.*;
import java.util.regex.Matcher;

import static org.example.matcher.NodeConditions.compatibleTypes;
import static org.example.refactoring.RuleUtils.*;

public class ExtractMethod implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        var match = context.matchResult;

        for (DSLNode addedMethod : match.addedComposite) {
            if (!isMethod(addedMethod)) continue;
            if (safe(addedMethod.name).isBlank()) continue;

            DSLNode referenceNode = findMethodReferenceNode(addedMethod.name, addedMethod, context);
            if (referenceNode == null) continue;

            MatchPair ownerPair = nearestMatchedAncestorPairOnRight(referenceNode.parent, context);
            if (ownerPair == null) continue;
            //if (!sameFile(ownerPair.right, addedMethod)) continue;
            if (isDescendantOf(referenceNode, addedMethod)) continue;

            Set<MatchPair> evidence = new LinkedHashSet<>();
            for (MatchPair mp : match.matches) {
                if (mp == null || mp.left() == null || mp.right() == null) continue;
                if (!isDescendantOf(mp.right(), addedMethod)) continue;
                if (!isDescendantOf(mp.left(), ownerPair.left())) continue;
                evidence.add(mp);
            }
            DSLNode removedOwner = ownerPair.left();
            if (removedOwner == null) continue;
            if (isDeletedSourceFile(removedOwner, context)) continue;

            boolean hasMatchedEvidence =
                    !evidence.isEmpty()
                            && hasMatchedDescendantContribution(removedOwner, addedMethod, evidence);
            boolean hasBodyEvidence = removedAddedMethodBodyMatch(addedMethod, removedOwner, referenceNode);
            if (!hasMatchedEvidence && !hasBodyEvidence) continue;

            String key = normalizePath(addedMethod.relativePath) + "|" + norm(addedMethod.name);
            BuildRefactoring ref = out.computeIfAbsent(
                    key,
                    k -> new BuildRefactoring(
                            BuildRefactoringType.EXTRACT_METHOD,
                            "Extract method '" + nodeLocationName(addedMethod) + "'"
                    )
            );

            addIfAbsent(ref.addedNodes, addedMethod);
            addIfAbsent(ref.addedNodes, referenceNode);
            addIfAbsent(ref.removedNodes, removedOwner);
            for (MatchPair mp : evidence) {
                if (!ref.relatedMatches.contains(mp)) ref.relatedMatches.add(mp);
                addIfAbsent(ref.removedNodes, mp.left());
            }
            if (!ref.relatedMatches.contains(ownerPair)) ref.relatedMatches.add(ownerPair);
        }

        return new ArrayList<>(out.values());
    }
    public static DSLNode findMethodReferenceNode(String methodName, DSLNode addedMethod, RefactoringContext context) {
        for (DSLNode n : context.matchResult.addedLeaves) {
            DSLNode hit = findReferenceInSubtree(n, x -> directMethodCallHere(x, methodName), addedMethod);
            if (hit != null) return hit;
        }
        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.right() == null) continue;
            DSLNode hit = findReferenceInSubtree(p.right(), n -> directMethodCallHere(n, methodName), addedMethod);
            if (hit != null) return hit;
        }
        for (DSLNode n : context.matchResult.addedComposite) {
            if (n == addedMethod) continue;
            DSLNode hit = findReferenceInSubtree(n, x -> directMethodCallHere(x, methodName), addedMethod);
            if (hit != null) return hit;
        }

        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.right() == null) continue;
            DSLNode hit = findReferenceInSubtree(p.right(), n -> referencesMethodHere(n, methodName), addedMethod);
            if (hit != null) return hit;
        }
        for (DSLNode n : context.matchResult.addedLeaves) {
            DSLNode hit = findReferenceInSubtree(n, x -> referencesMethodHere(x, methodName), addedMethod);
            if (hit != null) return hit;
        }
        for (DSLNode n : context.matchResult.addedComposite) {
            if (n == addedMethod) continue;
            DSLNode hit = findReferenceInSubtree(n, x -> referencesMethodHere(x, methodName), addedMethod);
            if (hit != null) return hit;
        }
        return null;
    }
    private static boolean directMethodCallHere(DSLNode node, String methodName) {
        return node != null
                && node.type == DSLNode.Type.METHOD_CALL
                && Objects.equals(norm(node.name), norm(methodName));
    }
    public static boolean referencesMethodHere(DSLNode node, String methodName) {
        String m = norm(methodName);
        if (node == null || m.isBlank()) return false;

        if (node.type == DSLNode.Type.METHOD_CALL && norm(node.name).equals(m)) return true;
        if (usesMethodInExpr(node.value, m)) return true;
        if (usesMethodInExpr(node.receiver, m)) return true;
        for (ExpressionNode a : safeList(node.arguments)) {
            if (usesMethodInExpr(a, m)) return true;
        }
        return false;
    }
    private static boolean usesMethodInExpr(ExpressionNode expr, String methodName) {
        if (expr == null || methodName.isBlank()) return false;

        if (expr instanceof MethodCallNode m) {
            if (sameLogicalVariable(m.name(), methodName)) return true;
        }
        if (expr instanceof ConstructorCallNode c) {
            if (sameLogicalVariable(c.typeName(), methodName)) return true;
        }

        if (expr instanceof GStringNode(String text)) {
            if (containsEmbeddedMethodReference(text, methodName)) return true;
        }

        if (expr instanceof ConstantNode(Object v)) {
            if (v != null && containsEmbeddedMethodReference(String.valueOf(v), methodName)) {
                return true;
            }
        }

        for (ExpressionNode c : childExpressions(expr)) {
            if (usesMethodInExpr(c, methodName)) return true;
        }

        return false;
    }
    private static boolean containsEmbeddedMethodReference(String text, String methodName) {
        if (text == null || methodName == null || methodName.isBlank()) return false;

        Matcher m = GSTRING_REF_PATTERN.matcher(text);

        while (m.find()) {
            String braced = m.group(1);

            // Method calls in GString interpolation must be braced: ${foo()}
            if (braced == null || !braced.contains("(")) continue;

            String ref = normalizeEmbeddedReference(braced);

            if (sameLogicalVariable(ref, methodName)) {
                return true;
            }
        }

        return false;
    }
    public static boolean hasMatchedDescendantContribution(DSLNode beforeOwner, DSLNode afterCarrier, Collection<MatchPair> matches) {
        if (beforeOwner == null || afterCarrier == null || matches == null) return false;
        for (MatchPair mp : matches) {
            if (mp == null || mp.left() == null || mp.right() == null) continue;
            if (isDescendantOf(mp.left(), beforeOwner) && isDescendantOf(mp.right(), afterCarrier)) return true;
        }
        return false;
    }
    public static MatchPair nearestMatchedAncestorPairOnRight(DSLNode rightNode, RefactoringContext context) {
        for (DSLNode cur = rightNode; cur != null; cur = cur.parent) {
            MatchPair mp = matchedPairForRightNode(cur, context);
            if (mp != null) return mp;
        }
        return null;
    }
    public static boolean removedAddedMethodBodyMatch(DSLNode addedMethod, DSLNode removedOwner, DSLNode referenceNode) {
        List<DSLNode> addedBody = meaningfulExtractedBodyNodes(addedMethod);
        List<DSLNode> removedBody = comparableExtractedBodyNodes(removedOwner);
        if (addedBody.isEmpty() || removedBody.isEmpty()) return false;

        Map<String, ExpressionNode> addedParameterEnv = methodParameterArgumentMap(addedMethod, referenceNode);
        for (DSLNode addedNode : addedBody) {
            boolean matched = false;
            for (DSLNode removedNode : removedBody) {
                if (equivalentBodyNode(removedNode, addedNode, addedParameterEnv)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        return true;
    }
    private static List<DSLNode> comparableExtractedBodyNodes(DSLNode root) {
        List<DSLNode> out = new ArrayList<>();
        for (DSLNode node : flatten(root)) {
            if (node == null || node == root) continue;
            if (isComparableExtractedBodyNode(node)) out.add(node);
        }
        return out;
    }
    private static List<DSLNode> meaningfulExtractedBodyNodes(DSLNode root) {
        List<DSLNode> out = new ArrayList<>();
        for (DSLNode node : flatten(root)) {
            if (node == null || node == root) continue;
            if (isMeaningfulExtractedBodyNode(node)) out.add(node);
        }
        return out;
    }
    private static Map<String, ExpressionNode> methodParameterArgumentMap(DSLNode addedMethod, DSLNode referenceNode) {
        if (addedMethod == null || referenceNode == null) return Map.of();

        List<ExpressionNode> parameters = safeList(addedMethod.arguments);
        List<ExpressionNode> arguments = safeList(referenceNode.arguments);
        if (parameters.isEmpty() || arguments.isEmpty()) return Map.of();

        Map<String, ExpressionNode> env = new LinkedHashMap<>();
        int count = Math.min(parameters.size(), arguments.size());
        for (int i = 0; i < count; i++) {
            String parameterName = methodParameterName(parameters.get(i));
            ExpressionNode argument = arguments.get(i);
            if (!parameterName.isBlank() && argument != null) {
                env.put(parameterName, argument);
            }
        }
        return env;
    }
    private static boolean isComparableExtractedBodyNode(DSLNode node) {
        if (node == null) return false;

        return switch (node.type) {
            case BLOCK,
                 CONDITION,
                 ASSIGNMENT,
                 DECLARATION,
                 METHOD_CALL,
                 TASK_RELATION,
                 THROW -> true;
            default -> false;
        };
    }
    private static boolean isMeaningfulExtractedBodyNode(DSLNode node) {
        if (node == null) return false;

        return switch (node.type) {
            case CONDITION,
                 ASSIGNMENT,
                 DECLARATION,
                 METHOD_CALL,
                 TASK_RELATION,
                 THROW -> true;
            default -> false;
        };
    }
    private static String methodParameterName(ExpressionNode parameter) {
        if (parameter == null) return "";

        String raw;
        if (parameter instanceof VariableNode(String name1)) {
            raw = name1;
        } else if (parameter instanceof DeclarationNode d) {
            raw = d.name();
        } else {
            raw = expressionReferenceText(parameter);
        }

        String name = safe(raw);
        int typeSeparator = name.indexOf(':');
        if (typeSeparator >= 0) {
            name = name.substring(0, typeSeparator);
        }
        return norm(name);
    }
    private static boolean equivalentBodyNode(DSLNode removedNode, DSLNode addedNode, Map<String, ExpressionNode> addedEnv) {
        if (removedNode == null || addedNode == null) return false;
        if (!compatibleTypes(removedNode, addedNode)) return false;

        String removedName = norm(removedNode.name);
        String addedName = norm(addedNode.name);
        if (!removedName.isBlank() || !addedName.isBlank()) {
            if (!equivalentBodyName(removedName, addedName, addedEnv)) return false;
        }

        if (!safe(removedNode.operator).isBlank() || !safe(addedNode.operator).isBlank()) {
            if (!Objects.equals(safe(removedNode.operator), safe(addedNode.operator))) return false;
        }

        List<ExprSlot> removedSlots = expressionSlots(removedNode);
        List<ExprSlot> addedSlots = expressionSlots(addedNode);
        for (ExprSlot addedSlot : addedSlots) {
            boolean matchedSlot = false;
            for (ExprSlot removedSlot : removedSlots) {
                if (!Objects.equals(removedSlot.key(), addedSlot.key())) continue;
                if (equivalentModuloWrapper(removedSlot.expr(), addedSlot.expr(), Map.of(), addedEnv)) {
                    matchedSlot = true;
                    break;
                }
            }
            if (!matchedSlot) return false;
        }

        return !addedSlots.isEmpty() || !addedName.isBlank();
    }
    private static boolean equivalentBodyName(String removedName, String addedName, Map<String, ExpressionNode> addedEnv) {
        if (Objects.equals(removedName, addedName)) return true;
        if (removedName == null || addedName == null || addedEnv == null || addedEnv.isEmpty()) return false;

        String expandedAddedName = addedName;
        for (Map.Entry<String, ExpressionNode> entry : addedEnv.entrySet()) {
            String parameterName = norm(entry.getKey());
            String argumentName = norm(expressionText(entry.getValue()));
            if (parameterName.isBlank() || argumentName.isBlank()) continue;
            expandedAddedName = expandedAddedName.replace(parameterName, argumentName);
        }

        return Objects.equals(removedName, expandedAddedName);
    }

}
