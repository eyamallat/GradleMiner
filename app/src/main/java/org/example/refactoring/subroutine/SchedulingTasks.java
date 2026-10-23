package org.example.refactoring.subroutine;

import org.example.model.*;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class SchedulingTasks implements RefactoringRule {
    private static final Set<String> EXECUTION_KEYS =
            Set.of("parallel", "threadcount", "maxparallelforks", "forkevery");
    private static final Set<String> RELATION_KEYS =
            Set.of("dependson", "mustrunafter", "shouldrunafter", "finalizedby");

    private record SchedulingEdge(String owner, String kind, String target, DSLNode node, DSLNode ownerNode) {
        String fullKey() {
            return owner + "|" + kind + "|" + target;
        }

        String relationKey() {
            return owner + "|" + kind;
        }
    }

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();
        Map<String, String> ownerAliases = ownerAliases(context);

        List<SchedulingEdge> oldEdges = collectSchedulingEdges(context.oldRoots, Map.of(), context);
        List<SchedulingEdge> newEdges = collectSchedulingEdges(context.newRoots, ownerAliases);

        Set<String> oldFullKeys = fullKeys(oldEdges);
        Map<String, Set<String>> oldTargetsByRelation = targetsByRelation(oldEdges);
        Map<String, Set<String>> newTargetsByRelation = targetsByRelation(newEdges);

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (SchedulingEdge edge : newEdges) {
            if (oldFullKeys.contains(edge.fullKey())) continue;
            if (ignoreAddedEdge(edge, context)) continue;
            if (!isTargetForExistingRelation(edge, oldTargetsByRelation, newTargetsByRelation)) {
                continue;
            }

            String key = edge.fullKey() + "|" + nodeLocationName(edge.node());
            BuildRefactoring ref = out.computeIfAbsent(
                    key, k -> new BuildRefactoring(BuildRefactoringType.SCHEDULING_TASKS, schedulingMessage(edge)
                    )
            );

            addIfAbsent(ref.addedNodes, edge.node());
            if (edge.ownerNode() != null) {
                addIfAbsent(ref.addedNodes, edge.ownerNode());
            }
        }

        return deduplicate(new ArrayList<>(out.values()));
    }
    private static Map<String, String> ownerAliases(RefactoringContext context) {
        Map<String, String> aliases = new HashMap<>();
        if (context == null || context.matchResult == null) return aliases;

        for (var pair : context.matchResult.matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (isDeletedSourceFile(pair.left(), context)) continue;
            if (!sameFile(pair.left(), pair.right())) continue;
            if (!isSchedulingOwner(pair.left()) || !isSchedulingOwner(pair.right())) continue;

            String oldName = norm(pair.left().name);
            String newName = norm(pair.right().name);

            if (oldName.isBlank() || newName.isBlank()) continue;
            if (oldName.equals(newName)) continue;

            aliases.put(newName, oldName);
        }

        return aliases;
    }
    private static boolean isSchedulingOwner(DSLNode node) {
        return node != null && (isTaskNode(node) || node.type == DSLNode.Type.BLOCK);
    }
    private static boolean ignoreAddedEdge(SchedulingEdge edge, RefactoringContext context) {
        if (edge == null || context == null || context.matchResult == null) return true;
        if (removedEdgeWithSameOwnerAndKind(edge, context.matchResult.removedLeaves, context.matchResult.removedComposite, context)) {return true;}

        boolean ownerExistedBefore = ownerNameExistsInRemoved(
                edge,
                context.matchResult.removedLeaves,
                context.matchResult.removedComposite,
                context
        );

        boolean relationBelongsToNewOwner = !ownerExistedBefore
                && (isInsideAddedComposite(edge.node(), context.matchResult.addedComposite)
                || ownerIsNewlyAdded(edge, context.matchResult.addedComposite, context));

        return relationBelongsToNewOwner
                || targetIsNewlyAdded(edge, context.matchResult.addedComposite, context);
    }
    private static boolean removedEdgeWithSameOwnerAndKind(SchedulingEdge edge, Collection<DSLNode> removedLeaves, Collection<DSLNode> removedComposite, RefactoringContext context) {
        if (edge == null) return false;

        for (DSLNode removed : safeCollection(removedLeaves)) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (sameOwnerAndKind(edge, removed)) {
                return true;
            }
        }

        for (DSLNode removed : safeCollection(removedComposite)) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (sameOwnerAndKind(edge, removed)) {
                return true;
            }
        }

        return false;
    }
    private static boolean sameOwnerAndKind(SchedulingEdge edge, DSLNode removed) {
        if (removed == null) return false;
        if (!sameFile(edge.node(), removed)) return false;
        if (!(removed.semantic instanceof TaskRelationModel relation)) return false;

        String removedOwner = norm(relation.ownerTask());
        String removedKind = norm(relation.relationKind());

        return Objects.equals(edge.owner(), removedOwner)
                && Objects.equals(edge.kind(), removedKind);
    }
    //extract scheduling edges
    private static List<SchedulingEdge> collectSchedulingEdges(DSLNode root, Map<String, String> ownerAliases) {
        List<SchedulingEdge> out = new ArrayList<>();
        if (root == null) return out;

        Deque<DSLNode> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            DSLNode node = stack.pop();

            if (node.semantic instanceof TaskRelationModel(
                    String ownerTask, String relationKind, List<ExpressionNode> targets
            )) {
                DSLNode ownerNode = nearestSchedulingOwner(node);
                String owner = normalizedOwner(ownerTask, ownerNode);
                owner = ownerAliases.getOrDefault(owner, owner);
                String kind = norm(relationKind);

                for (ExpressionNode target : safeList(targets)) {
                    String targetText = norm(expressionText(target));
                    if (owner.isBlank() || kind.isBlank() || targetText.isBlank()) continue;
                    out.add(new SchedulingEdge(owner, kind, targetText, node, ownerNode));
                }
            } else if (node.semantic instanceof TaskModel task) {
                collectTaskArgumentSchedulingEdges(task, node, ownerAliases, out);
            } else if (isExecutionSchedulingAssignment(node)) {
                DSLNode ownerNode = nearestSchedulingOwner(node);
                String owner = normalizedOwner(null, ownerNode);
                String kind = "assign:" + norm(node.name);
                String target = norm(expressionText(extractInitializer(node)));

                if (!owner.isBlank() && !target.isBlank()) {
                    out.add(new SchedulingEdge(owner, kind, target, node, ownerNode));
                }
            }

            for (DSLNode child : safeList(node.children)) {
                stack.push(child);
            }
        }

        return out;
    }
    private static void collectTaskArgumentSchedulingEdges(
            TaskModel task,
            DSLNode taskNode,
            Map<String, String> ownerAliases,
            List<SchedulingEdge> out
    ) {
        if (task == null || taskNode == null || out == null) return;

        String owner = norm(task.taskName());
        if (owner.isBlank()) {
            owner = norm(taskNode.name);
        }
        owner = ownerAliases.getOrDefault(owner, owner);
        if (owner.isBlank()) return;

        for (ExpressionNode arg : safeList(task.arguments())) {
            if (!(arg instanceof MapNode(Map<String, ExpressionNode> entries)) || entries == null) continue;

            for (Map.Entry<String, ExpressionNode> entry : entries.entrySet()) {
                String kind = norm(entry.getKey());
                if (!RELATION_KEYS.contains(kind)) continue;

                for (ExpressionNode target : schedulingTargets(entry.getValue())) {
                    String targetText = norm(expressionText(target));
                    if (targetText.isBlank()) continue;
                    out.add(new SchedulingEdge(owner, kind, targetText, taskNode, taskNode));
                }
            }
        }
    }
    private static List<ExpressionNode> schedulingTargets(ExpressionNode value) {
        if (value == null) return List.of();
        if (value instanceof ListNode(List<ExpressionNode> values1)) {
            return safeList(values1);
        }
        if (value instanceof TupleNode(List<ExpressionNode> values)) {
            return safeList(values);
        }
        return List.of(value);
    }
    private static List<SchedulingEdge> collectSchedulingEdges(DSLNode root, Map<String, String> ownerAliases, RefactoringContext context) {
        List<SchedulingEdge> edges = collectSchedulingEdges(root, ownerAliases);
        edges.removeIf(edge -> edge != null && isDeletedSourceFile(edge.node(), context));
        return edges;
    }
    //managing the leaf<->composite mismatch that have the same name
    private static boolean ownerNameExistsInRemoved(SchedulingEdge edge, Collection<DSLNode> removedLeaves, Collection<DSLNode> removedComposite, RefactoringContext context) {
        if (edge == null || edge.owner().isBlank()) return false;

        for (DSLNode removed : safeCollection(removedLeaves)) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (sameRemovedOwnerName(edge, removed)) {
                return true;
            }
        }
        for (DSLNode removed : safeCollection(removedComposite)) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (sameRemovedOwnerName(edge, removed)) {
                return true;
            }
        }

        return false;
    }
    private static boolean sameRemovedOwnerName(SchedulingEdge edge, DSLNode removed) {
        return removed != null && sameFile(edge.node(), removed) && Objects.equals(edge.owner(), norm(removed.name));
    }
    private static boolean ownerIsNewlyAdded(SchedulingEdge edge, Collection<DSLNode> addedComposite, RefactoringContext context) {
        if (edge == null || edge.owner().isBlank()) return false;

        for (DSLNode added : safeCollection(addedComposite)) {
            if (isDeletedSourceFile(added, context)) continue;
            if (!sameFile(edge.node(), added)) continue;
            if (!isSchedulingOwner(added)) continue;
            if (sameOwnerName(edge.owner(), added)) return true;
        }

        return false;
    }
    private static boolean sameOwnerName(String owner, DSLNode node) {
        String normalizedOwner = norm(owner);
        String nodeName = norm(node == null ? "" : node.name);

        if (normalizedOwner.isBlank() || nodeName.isBlank()) return false;
        return Objects.equals(normalizedOwner, nodeName)
                || Objects.equals(normalizedOwner, "tasks." + nodeName);
    }
    private static boolean targetIsNewlyAdded(SchedulingEdge edge, Collection<DSLNode> addedComposite, RefactoringContext context) {
        if (edge == null || edge.target().isBlank()) return false;

        for (DSLNode added : safeCollection(addedComposite)) {
            if (isDeletedSourceFile(added, context)) continue;
            if (!sameFile(edge.node(), added)) continue;
            if (!isSchedulingOwner(added)) continue;
            if (sameTaskReferenceName(edge.target(), added)) return true;
        }

        return false;
    }
    private static boolean sameTaskReferenceName(String reference, DSLNode node) {
        String normalizedReference = taskReferenceName(reference);
        String nodeName = taskReferenceName(node == null ? "" : node.name);

        return !normalizedReference.isBlank()
                && !nodeName.isBlank()
                && Objects.equals(normalizedReference, nodeName);
    }
    private static String taskReferenceName(String value) {
        String out = norm(value);
        if (out.startsWith("tasks.")) {
            out = out.substring("tasks.".length());
        }
        while (out.startsWith(":")) {
            out = out.substring(1);
        }
        int lastColon = out.lastIndexOf(':');
        if (lastColon >= 0 && lastColon + 1 < out.length()) {
            out = out.substring(lastColon + 1);
        }
        return out;
    }
    //detect newly added and modified relations
    private static boolean isTargetForExistingRelation(SchedulingEdge edge, Map<String, Set<String>> oldTargetsByRelation, Map<String, Set<String>> newTargetsByRelation) {
        Set<String> oldTargets = oldTargetsByRelation.get(edge.relationKey());

        if (oldTargets == null || oldTargets.isEmpty()) {
            return true;
        }
        Set<String> newTargets = newTargetsByRelation.getOrDefault(edge.relationKey(), Set.of());
        return newTargets.containsAll(oldTargets) && !oldTargets.contains(edge.target());
    }
    //Used to skip unchanged scheduling
    private static Set<String> fullKeys(List<SchedulingEdge> edges) {
        Set<String> out = new HashSet<>();
        for (SchedulingEdge edge : safeList(edges)) {
            out.add(edge.fullKey());
        }
        return out;
    }
    //owner|kind-> { target }
    private static Map<String, Set<String>> targetsByRelation(List<SchedulingEdge> edges) {
        Map<String, Set<String>> out = new HashMap<>();
        for (SchedulingEdge edge : safeList(edges)) {
            out.computeIfAbsent(edge.relationKey(), k -> new HashSet<>()).add(edge.target());
        }
        return out;
    }
    private static String schedulingMessage(SchedulingEdge edge) {
        String target = displayText(edge.target());
        if (edge.kind().startsWith("assign:")) {
            return "Introduce scheduling assignment '" + edge.kind().substring("assign:".length())
                    + "' for '" + edge.owner()
                    + "' in '" + nodeLocationName(edge.node()) + "'";
        }

        return "Introduce task relation '" + edge.kind()
                + "' from '" + edge.owner()
                + "' to '" + target
                + "' in '" + nodeLocationName(edge.node()) + "'";
    }
    private static String displayText(String text) {
        return safe(text)
                .replace('|', '/')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }
    private static boolean isExecutionSchedulingAssignment(DSLNode node) {
        if (node == null) return false;
        if (node.type != DSLNode.Type.ASSIGNMENT && node.type != DSLNode.Type.DECLARATION) return false;
        if (!EXECUTION_KEYS.contains(norm(node.name))) return false;
        return extractInitializer(node) != null;
    }
    private static DSLNode nearestSchedulingOwner(DSLNode node) {
        for (DSLNode cur = node == null ? null : node.parent; cur != null; cur = cur.parent) {
            if (isTaskNode(cur)) return cur;
            if (cur.type == DSLNode.Type.BLOCK && !safe(cur.name).isBlank()) return cur;
            if (isFile(cur)) return cur;
        }
        return null;
    }
    private static String normalizedOwner(String semanticOwner, DSLNode ownerNode) {
        String owner = norm(semanticOwner);
        if (!owner.isBlank()) return owner;
        return ownerNode == null ? "" : norm(ownerNode.name);
    }
    public static boolean isInsideAddedComposite(DSLNode node, Collection<DSLNode> addedComposites) {
        if (node == null || addedComposites == null) return false;
        for (DSLNode c : addedComposites) {
            if (c == null || c == node) continue;
            if (sameFile(node, c) && isDescendantOf(node, c)) return true;
        }
        return false;
    }


}
