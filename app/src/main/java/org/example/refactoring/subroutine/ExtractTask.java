package org.example.refactoring.subroutine;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.model.ExpressionNode;
import org.example.model.TaskRelationModel;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class ExtractTask implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        var match = context.matchResult;

        for (DSLNode addedTask : match.addedComposite) {
            if (!isTask(addedTask)) continue;
            if (safe(addedTask.name).isBlank()) continue;

            DSLNode referenceNode = findTaskReferenceNode(addedTask.name, addedTask, context);
            if (referenceNode == null) continue;

            MatchPair ownerPair = nearestMatchedExecutableOwnerOnRight(referenceNode.parent, context);
            if (isDescendantOf(referenceNode, addedTask)) continue;

            DSLNode removedOwner = null;
            if (ownerPair != null && sameFile(ownerPair.right(), addedTask)) {
                removedOwner = ownerPair.left();
            }

            if (removedOwner == null) {
                removedOwner = findRemovedSourceOwner(addedTask, referenceNode, match.removedComposite, match.matches);
            }

            if (removedOwner == null) continue;
            if (isDeletedSourceFile(removedOwner, context)) continue;

            Set<MatchPair> evidence = new LinkedHashSet<>();
            for (MatchPair mp : match.matches) {
                if (mp == null || mp.left() == null || mp.right() == null) continue;
                if (!isDescendantOf(mp.right(), addedTask)) continue;
                if (!isDescendantOf(mp.left(), removedOwner)) continue;
                evidence.add(mp);
            }
            if (evidence.isEmpty()) continue;

            if (!carrierDirectChildrenCoveredByOldOwner(addedTask, removedOwner, match.matches)) continue;

            String key = normalizePath(addedTask.relativePath) + "|" + norm(addedTask.name);
            BuildRefactoring ref = out.computeIfAbsent(
                    key,
                    k -> new BuildRefactoring(BuildRefactoringType.EXTRACT_TASK, "Extract task '" + nodeLocationName(addedTask) + "'")
            );

            addIfAbsent(ref.addedNodes, addedTask);
            addIfAbsent(ref.addedNodes, referenceNode);
            addIfAbsent(ref.removedNodes, removedOwner);
            for (MatchPair mp : evidence) {
                if (!ref.relatedMatches.contains(mp)) ref.relatedMatches.add(mp);
                addIfAbsent(ref.removedNodes, mp.left());
            }
            if (ownerPair != null && !ref.relatedMatches.contains(ownerPair)) ref.relatedMatches.add(ownerPair);
        }

        return new ArrayList<>(out.values());
    }

    private static DSLNode findRemovedSourceOwner(DSLNode addedTask, DSLNode referenceNode, Collection<DSLNode> removedComposites, Collection<MatchPair> matches) {
        DSLNode best = null;

        for (DSLNode candidate : removedComposites) {
            if (candidate == null) continue;
            if (!sameFile(candidate, addedTask)) continue;
            if (!sameReferenceSiteRole(candidate, referenceNode)) continue;
            if (!carrierDirectChildrenCoveredByOldOwner(addedTask, candidate, matches)) continue;

            if (best == null || isDescendantOf(candidate, best)) {
                best = candidate;
            }
        }

        return best;
    }

    private static boolean sameReferenceSiteRole(DSLNode removedOwner, DSLNode referenceNode) {
        if (removedOwner == null || referenceNode == null) return false;
        if (removedOwner.type != referenceNode.type) return false;

        String removedName = norm(removedOwner.name);
        String referenceName = norm(referenceNode.name);

        return !removedName.isBlank() && Objects.equals(removedName, referenceName);
    }
    public static DSLNode findTaskReferenceNode(String taskName, DSLNode addedTask, RefactoringContext context) {
        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.right() == null) continue;
            DSLNode hit = findReferenceInSubtree(p.right(), n -> referencesTaskHere(n, taskName), addedTask);
            if (hit != null) return hit;
        }
        for (DSLNode n : context.matchResult.addedLeaves) {
            if (findReferenceInSubtree(n, x -> referencesTaskHere(x, taskName), addedTask) != null) return n;
        }
        for (DSLNode n : context.matchResult.addedComposite) {
            if (n == addedTask) continue;
            DSLNode hit = findReferenceInSubtree(n, x -> referencesTaskHere(x, taskName), addedTask);
            if (hit != null) return hit;
        }
        return null;
    }
    public static boolean referencesTaskHere(DSLNode node, String taskName) {
        String t = norm(taskName);
        if (node == null || t.isBlank()) return false;

        if (node.semantic instanceof TaskRelationModel relation) {
            for (ExpressionNode target : safeList(relation.targets())) {
                if (Objects.equals(norm(expressionText(target)), t)) return true;
            }
        }

        if (node.value != null && usesInExpr(node.value, t)) return true;
        if (node.receiver != null && usesInExpr(node.receiver, t)) return true;
        for (ExpressionNode a : safeList(node.arguments)) {
            if (usesInExpr(a, t)) return true;
        }
        return false;
    }
    public static MatchPair nearestMatchedExecutableOwnerOnRight(DSLNode rightNode, RefactoringContext context) {
        for (DSLNode cur = rightNode; cur != null; cur = cur.parent) {

            MatchPair mp = matchedPairForRightNode(cur, context);
            if (mp != null) return mp;
        }
        return null;
    }
    public static boolean carrierDirectChildrenCoveredByOldOwner(DSLNode afterCarrier, DSLNode beforeOwner, Collection<MatchPair> matches) {
        if (afterCarrier == null || beforeOwner == null || matches == null) return false;

        List<DSLNode> children = safeList(afterCarrier.children);
        if (children.isEmpty()) return false;

        for (DSLNode child : children) {
            if (child == null) continue;
            if (!subtreeCoveredByOldOwner(child, beforeOwner, matches)) return false;
        }

        return true;
    }
    private static boolean subtreeCoveredByOldOwner(DSLNode afterSubtree, DSLNode beforeOwner, Collection<MatchPair> matches) {
        if (afterSubtree == null || beforeOwner == null || matches == null) return false;

        for (MatchPair mp : matches) {
            if (mp == null || mp.left() == null || mp.right() == null) continue;
            if (!isDescendantOf(mp.left(), beforeOwner)) continue;

            if (mp.right() == afterSubtree) return true;
            if (isDescendantOf(mp.right(), afterSubtree)) return true;
            if (isDescendantOf(afterSubtree, mp.right())) return true;
        }

        return false;
    }


}
