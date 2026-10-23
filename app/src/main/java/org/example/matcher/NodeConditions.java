package org.example.matcher;

import org.example.model.*;

import java.util.*;

import static org.example.matcher.NodeFormat.*;
import static org.example.matcher.NodeSimilarity.trim;
import static org.example.matcher.ReplacementAnalyzer.collectNodeReplacements;
import static org.example.matcher.SemanticMatchConditions.*;
import static org.example.refactoring.RuleUtils.*;

public class NodeConditions {

    static boolean condition1(DSLNode a, DSLNode b) {
        return sameDepth(a, b) && deepEquals(a, b);
    }
    static boolean condition2(DSLNode a, DSLNode b) {
        if (!compatibleTypes(a, b)) return false;
        if (!sameName(a, b)) return false;
        if (!parentCompatible(a, b)) return false;
        return sameArgCount(a, b) && atLeastOneArgumentMatches(a, b);


    }
    static boolean condition3(DSLNode before, DSLNode after, Map<String, ExpressionNode> beforeEnv, Map<String, ExpressionNode> afterEnv, Set<MatchPair> existingMatches) {
        if (!compatibleTypes(before, after)) return false;

        List<Replacement> replacements = collectNodeReplacements(before, after);

        if (semanticallyValidLeafMatch(before, after, replacements)) {
            return true;
        }
        if (!parentCompatible(before, after)) return false;
        if (!sameArgumentizedStructuralRole(before, after, existingMatches)) return false;

        return argumentizedEquivalent(before, after, beforeEnv, afterEnv);
    }

    private static boolean sameArgumentizedStructuralRole(DSLNode before, DSLNode after, Set<MatchPair> existingMatches) {
        if (before == null || after == null) return false;

        if (before.type == after.type && sameName(before, after)) {
            return true;
        }

        if (before.type == DSLNode.Type.DEPENDENCY_DECL
                && after.type == DSLNode.Type.DEPENDENCY_DECL) {
            if (!(before.semantic instanceof DependencyModel b)) return false;
            if (!(after.semantic instanceof DependencyModel a)) return false;

            return sameDependencyIdentity(b, a);
        }


        if (isBindingLike(before) && isBindingLike(after)) {
            return consistentNameReplacement(before, after, existingMatches);
        }

        if (isTask(before) && isTask(after)) {
            return consistentNameReplacement(before, after, existingMatches);
        }

        if (isMethod(before) && isMethod(after)) {
            return consistentNameReplacement(before, after, existingMatches);
        }

        return false;
    }
    private static boolean consistentNameReplacement(DSLNode before, DSLNode after, Set<MatchPair> existingMatches) {
        String oldName = norm(before.name);
        String newName = norm(after.name);

        if (oldName.isBlank() || newName.isBlank()) return false;
        if (oldName.equals(newName)) return true;

        if (existingMatches == null || existingMatches.isEmpty()) return false;

        int supporting = 0;

        for (MatchPair pair : existingMatches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (!sameFile(pair.left(), before) || !sameFile(pair.right(), after)) continue;

            if (nodeTextMentions(pair.left(), oldName)
                    && nodeTextMentions(pair.right(), newName)) {
                supporting++;
            }

            if (supporting >= 2) return true;
        }

        return false;
    }
    private static boolean nodeTextMentions(DSLNode node, String token) {
        if (node == null || token == null || token.isBlank()) return false;

        StringBuilder text = new StringBuilder(norm(String.valueOf(node.name))
                + " "
                + norm(String.valueOf(node.value))
                + " "
                + norm(String.valueOf(node.receiver))
                + " "
                + norm(String.valueOf(node.semantic)));

        for (ExpressionNode arg : safeList(node.arguments)) {
            text.append(" ").append(norm(arg == null ? "" : arg.contentKey()));
        }

        return text.toString().contains(token);
    }
    public static Map<String, ExpressionNode> argumentizationEnvironment(DSLNode root) {
        Map<String, ExpressionNode> env = new LinkedHashMap<>();
        if (root == null) return env;

        for (DSLNode node : flatten(root)) {
            if (!isVariableNode(node)) continue;

            String name = normalizeReferenceName(node.name);
            ExpressionNode value = extractInitializer(node);

            if (safe(name).isBlank() || value == null) continue;

            env.putIfAbsent(norm(name), value);

            if (value instanceof MapNode(Map<String, ExpressionNode> entries)) {
                for (Map.Entry<String, ExpressionNode> entry : entries.entrySet()) {
                    String key = entry.getKey();
                    ExpressionNode entryValue = entry.getValue();

                    if (safe(key).isBlank() || entryValue == null) continue;

                    env.putIfAbsent(norm(name + "." + key), entryValue);
                }
            }
        }

        return env;
    }
    public static boolean argumentizedEquivalent(DSLNode before, DSLNode after, Map<String, ExpressionNode> beforeEnv, Map<String, ExpressionNode> afterEnv) {
        if (before == null || after == null) return false;

        Map<String, ExpressionNode> beforeSlots = argumentizedSlots(before);
        Map<String, ExpressionNode> afterSlots = argumentizedSlots(after);

        int checked = 0;
        int matched = 0;

        for (String key : beforeSlots.keySet()) {
            if (!afterSlots.containsKey(key)) continue;

            ExpressionNode b = expandFully(unwrapCasts(beforeSlots.get(key)), beforeEnv);
            ExpressionNode a = expandFully(unwrapCasts(afterSlots.get(key)), afterEnv);

            checked++;

            if (equivalentModuloWrapper(b, a, beforeEnv, afterEnv)) {
                matched++;
            }
        }

        return checked > 0 && matched > 0;
    }
    private static Map<String, ExpressionNode> argumentizedSlots(DSLNode node) {
        Map<String, ExpressionNode> out = new LinkedHashMap<>();
        if (node == null) return out;

        if (node.value != null) {
            out.put("value", node.value);
        }

        if (node.receiver != null) {
            out.put("receiver", node.receiver);
        }

        int i = 0;
        for (ExpressionNode arg : safeList(node.arguments)) {
            out.put("arg[" + i + "]", arg);
            i++;
        }

        out.putAll(semanticExpressionSlots(node));

        return out;
    }



    static boolean conditionC1(DSLNode a, DSLNode b) {return sameDepth(a, b) && deepEquals(a, b);}
    static boolean conditionC2(DSLNode a, DSLNode b, Set<MatchPair> matches) {
        if (!sameType(a, b)) return false;
        if (!compatible(a,b)) return false;

        if (!sameReceiver(a, b)) return false;
        if (stableContainerMatch(a, b)) return true;
        return hasMatchedChildPair(a, b, matches) || partialChildMatch(a, b, matches);

    }
    private static boolean stableContainerMatch(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;
        if (!sameFile(a, b)) return false;
        if (a.type != b.type) return false;
        if (!Objects.equals(norm(a.name), norm(b.name))) return false;
        if (!sameParentRole(a, b)) return false;

        return isContainerNode(a) && isContainerNode(b);
    }
    private static boolean sameParentRole(DSLNode a, DSLNode b) {
        DSLNode ap = a == null ? null : a.parent;
        DSLNode bp = b == null ? null : b.parent;

        if (ap == null || bp == null) return ap == bp;
        if (ap.type != bp.type) return false;

        return Objects.equals(norm(ap.name), norm(bp.name));
    }
    private static boolean isContainerNode(DSLNode node) {
        if (node == null) return false;
        if (node.children == null || node.children.isEmpty()) return false;

        return !isSemanticEntityNode(node);
    }
    private static boolean isSemanticEntityNode(DSLNode node) {
        if (node == null || node.type == null) return false;

        return switch (node.type) {
            case METHOD_DECL,
                 METHOD_CALL,
                 DEPENDENCY_DECL,
                 ASSIGNMENT,
                 DECLARATION,
                 PLUGIN_DECL,
                 APPLY_SCRIPT,
                 REPOSITORIES_DECL,
                 TASK_RELATION,
                 IMPORT,
                 THROW -> true;

            default -> false;
        };
    }
    private static boolean semanticallyValidLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (deepEquals(a, b)) return true;

        if (reps == null || reps.isEmpty()) return false;


        // SEMANTICS
        if (a.semantic instanceof DependencyModel && b.semantic instanceof DependencyModel) {
            return validDependencyLeafMatch(a, b, reps);
        }
        if (a.type == DSLNode.Type.REPOSITORIES_DECL
                && b.type == DSLNode.Type.REPOSITORIES_DECL) {
            return validRepositoryLeafMatch(a, b, reps);
        }
        if (a.semantic instanceof TaskModel && b.semantic instanceof TaskModel) {
            return validTaskLeafMatch(a, b, reps);
        }
        if (a.semantic instanceof TaskActionModel && b.semantic instanceof TaskActionModel) {
            return validTaskActionLeafMatch(a, b, reps);
        }
        if (a.semantic instanceof PluginModel && b.semantic instanceof PluginModel) {
            return validPluginLeafMatch(a, b, reps);
        }

        if (a.semantic instanceof TaskRelationModel && b.semantic instanceof TaskRelationModel) {
            return validTaskRelationLeafMatch(a, b, reps);
        }
        // STRUCTURAL
        if (isBindingLike(a) && isBindingLike(b)) {return validBindingLeafMatch(a, b, reps);}

        if (a.type == DSLNode.Type.CONDITION && b.type == DSLNode.Type.CONDITION) {
            return validConditionLeafMatch(a, b, reps);
        }

        if (a.type == DSLNode.Type.METHOD_CALL && b.type == DSLNode.Type.METHOD_CALL) {
            return validMethodCallLeafMatch(a, b, reps);
        }

        // CROSS TYPE
        if ((isBindingLike(a) && b.type == DSLNode.Type.METHOD_CALL) || (isBindingLike(b) && a.type == DSLNode.Type.METHOD_CALL)) {
            return validBindingMethodCallLeafMatch(a, b, reps);
        }

        return validGenericLeafMatch(a, b, reps);
    }
    private static boolean atLeastOneArgumentMatches(DSLNode a, DSLNode b) {
        List<ExpressionNode> ax = safeList(a == null ? null : a.arguments);
        List<ExpressionNode> bx = safeList(b == null ? null : b.arguments);
        if (ax.isEmpty() && bx.isEmpty()) {
            return true;
        }
        if (ax.isEmpty() || bx.isEmpty()) {
            return false;
        }

        int common = Math.min(ax.size(), bx.size());
        for (int i = 0; i < common; i++) {
            ExpressionNode left = ax.get(i);
            ExpressionNode right = bx.get(i);

            if (sameExpr(left, right)) {
                return true;
            }

            String lc = exprContent(left);
            String rc = exprContent(right);
            if (Objects.equals(lc, rc)) {
                return true;
            }
        }

        return false;
    }
    public static boolean compatibleTypes(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;

        if (a.type == b.type) return true;

        if (isBindingLike(a) && isBindingLike(b)) {
            return true;
        }
        if (isBindingLike(a) && isMethodLike(b)) {
            return true;
        }
        return  (isBindingLike(b) && isMethodLike(a)) ;

    }

    private static boolean isMethodLike(DSLNode n) {
        return n != null && n.type == DSLNode.Type.METHOD_CALL;
    }

    static boolean compatible(DSLNode a, DSLNode b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!compatibleTypes(a, b)) return false;
        if (isBlockAnchor(a.type)) {
            return Objects.equals(trim(a.name), trim(b.name));
        }

        return true;
    }

    static boolean partialChildMatch(DSLNode a, DSLNode b, Set<MatchPair> matches) {
        int matched = 0;

        for (MatchPair pair : matches) {
            if (safeList(a.children).contains(pair.left())
                    && safeList(b.children).contains(pair.right())) {
                matched++;
            }
        }

        int minChildren = Math.min(safeList(a.children).size(), safeList(b.children).size());
        return minChildren > 0 && matched >= 1;
    }

    static boolean hasMatchedChildPair(DSLNode a, DSLNode b, Set<MatchPair> matches) {
        for (DSLNode c1 : safeList(a.children)) {
            for (DSLNode c2 : safeList(b.children)) {
                if (matches.contains(new MatchPair(c1, c2))) {
                    return true;
                }
            }
        }
        return false;
    }
    static boolean sameDepth(DSLNode a, DSLNode b) {
        return a != null && b != null && a.getDepth() == b.getDepth();
    }
    static boolean sameType(DSLNode a, DSLNode b) {
        return a != null && b != null && a.type == b.type;
    }

    static boolean deepEquals(DSLNode a, DSLNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.type != b.type) return false;
        if (!Objects.equals(trim(a.name), trim(b.name))) return false;
        if (!Objects.equals(a.receiver, b.receiver)) return false;
        if (!Objects.equals(a.value, b.value)) return false;
        if (!Objects.equals(a.semantic, b.semantic)) return false;

        List<ExpressionNode> argsA = safeList(a.arguments);
        List<ExpressionNode> argsB = safeList(b.arguments);
        if (argsA.size() != argsB.size()) return false;
        for (int i = 0; i < argsA.size(); i++) {
            if (!Objects.equals(argsA.get(i), argsB.get(i))) return false;
        }

        List<DSLNode> childrenA = safeList(a.children);
        List<DSLNode> childrenB = safeList(b.children);
        if (childrenA.size() != childrenB.size()) return false;
        for (int i = 0; i < childrenA.size(); i++) {
            if (!deepEquals(childrenA.get(i), childrenB.get(i))) return false;
        }

        return true;
    }


}
