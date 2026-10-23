package org.example.refactoring.module;

import org.example.model.DSLNode;
import org.example.model.ExpressionNode;
import org.example.model.PluginModel;
import org.example.refactoring.BuildRefactoring;
import org.example.refactoring.BuildRefactoringType;
import org.example.refactoring.RefactoringContext;
import org.example.refactoring.RefactoringRule;
import org.example.refactoring.RuleUtils;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import static org.example.refactoring.RuleUtils.*;


public class ExtractModule implements RefactoringRule {

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        return detectByDirection(context, BuildRefactoringType.EXTRACT_MODULE,
                RuleUtils::isMove, (s, t) -> "Extract module logic from '" + s + "' to '" + t + "'"
        );
    }

    static List<BuildRefactoring> detectByDirection(RefactoringContext context, BuildRefactoringType type, BiPredicate<String, String> direction, BiFunction<String, String, String> description) {
        if (context == null || context.matchResult == null) return List.of();

        var match = context.matchResult;

        List<DSLNode> removedRoots = new ArrayList<>();
        removedRoots.addAll(safeCollection(match.removedComposite));
        removedRoots.addAll(safeCollection(match.removedLeaves));

        List<DSLNode> addedRoots = new ArrayList<>();
        addedRoots.addAll(safeCollection(match.addedComposite));
        addedRoots.addAll(safeCollection(match.addedLeaves));

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (DSLNode removed : removedRoots) {
            if (isDeletedSourceFile(removed, context)) continue;
            String source = normalizePath(removed.relativePath);
            if (source.isBlank()) continue;

            for (DSLNode added : addedRoots) {
                String target = normalizePath(added.relativePath);
                if (target.isBlank() || target.equals(source)) continue;
                if (!direction.test(source, target)) continue;
                if (isMovedFilePair(source, target, context)) continue;
                if (context.renamedFileTargets.contains(target)) continue;
                if (!isNewTargetFile(target, context)) continue;
                if (!moduleEvidencePair(removed, added)) continue;

                String key = source + "->" + target + "|" + type;
                BuildRefactoring ref = out.computeIfAbsent(key, k -> new BuildRefactoring(type, description.apply(source, target)));

                addIfAbsent(ref.removedNodes, removed);
                addIfAbsent(ref.addedNodes, added);


            }
        }

        List<BuildRefactoring> filtered = new ArrayList<>();
        for (BuildRefactoring ref : out.values()) {
            if (hasCompositeExtractionEvidence(ref) || hasExtractionLink(ref, context)) {
                filtered.add(ref);
            }
        }

        return deduplicate(filtered);

    }

    //verify if it is just an entire file moved
    private static boolean isMovedFilePair(String source, String target, RefactoringContext context) {
        source = normalizePath(source);
        target = normalizePath(target);

        if (source.isBlank() || target.isBlank()) return false;
        if (source.equals(target)) return true;
        if (!Objects.equals(fileName(source), fileName(target))) return false;
        if (!isRelocatedSamePathSuffix(source, target)) return false;

        boolean sourceGone = treeHasFile(context.oldRoots, source) && !treeHasFile(context.newRoots, source);

        boolean targetNew = !treeHasFile(context.oldRoots, target) && treeHasFile(context.newRoots, target);

        return sourceGone && targetNew;
    }
    private static boolean isRelocatedSamePathSuffix(String source, String target) {
        List<String> sourceParts = pathParts(source);
        List<String> targetParts = pathParts(target);

        if (sourceParts.isEmpty() || targetParts.isEmpty()) return false;
        if (targetParts.size() <= sourceParts.size()) return false;

        int offset = targetParts.size() - sourceParts.size();
        for (int i = 0; i < sourceParts.size(); i++) {
            if (!Objects.equals(sourceParts.get(i), targetParts.get(i + offset))) {
                return false;
            }
        }

        return true;
    }
    private static boolean treeHasFile(DSLNode root, String path) {
        if (root == null || path == null || path.isBlank()) return false;

        if (path.equals(normalizePath(root.relativePath))) {
            return true;
        }

        for (DSLNode child : safeList(root.children)) {
            if (treeHasFile(child, path)) {
                return true;
            }
        }

        return false;
    }
    private static String fileName(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }
    private static List<String> pathParts(String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) return List.of();

        List<String> out = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (!part.isBlank()) out.add(part);
        }
        return out;
    }
    //verify if the nodes were move to an entirely new file
    private static boolean isNewTargetFile(String target, RefactoringContext context) {
        if (target == null || context == null || context.oldRoots == null || context.newRoots == null) {
            return false;
        }

        String normalizedTarget = normalizePath(target);
        if (normalizedTarget.isBlank()) return false;
        if (!isGradleScriptFile(normalizedTarget)) return false;
        return !treeHasFile(context.oldRoots, normalizedTarget)
                && treeHasFile(context.newRoots, normalizedTarget);
    }
    private static boolean isGradleScriptFile(String path) {
        String p = normalizePath(path).toLowerCase(Locale.ROOT);
        return p.endsWith(".gradle") || p.endsWith(".gradle.kts");
    }
    private static boolean hasCompositeExtractionEvidence(BuildRefactoring ref) {
        if (ref == null) return false;

        for (DSLNode removed : safeList(ref.removedNodes)) {
            for (DSLNode added : safeList(ref.addedNodes)) {
                if (sameCompositeBuildLogicEntity(removed, added)) {
                    return true;
                }
            }
        }

        return false;
    }
    private static boolean sameCompositeBuildLogicEntity(DSLNode removed, DSLNode added) {
        if (removed == null || added == null) return false;
        if (!isContainerNode(removed) || !isContainerNode(added)) return false;
        return sameAtomicBuildLogicEntity(removed, added);
    }
    private static boolean hasExtractionLink(BuildRefactoring ref, RefactoringContext context) {
        if (ref == null || context == null || context.matchResult == null) return false;
        String source = sourceFile(ref);
        String target = targetFile(ref);
        if (source.isBlank() || target.isBlank()) return false;

        for (DSLNode node : changedAddedNodes(context)) {
            if (node == null) continue;
            if (!Objects.equals(normalizePath(node.relativePath), source)) continue;
            if (appliesGradleScript(node, source, target)) {
                addIfAbsent(ref.addedNodes, node);
                return true;
            }
        }

        return false;
    }
    private static String sourceFile(BuildRefactoring ref) {
        for (DSLNode node : safeList(ref.removedNodes)) {
            String path = normalizePath(node == null ? null : node.relativePath);
            if (!path.isBlank()) return path;
        }
        return "";
    }
    private static String targetFile(BuildRefactoring ref) {
        for (DSLNode node : safeList(ref.addedNodes)) {
            String path = normalizePath(node == null ? null : node.relativePath);
            if (!path.isBlank()) return path;
        }
        return "";
    }
    private static List<DSLNode> changedAddedNodes(RefactoringContext context) {
        List<DSLNode> out = new ArrayList<>();
        out.addAll(safeCollection(context.matchResult.addedComposite));
        out.addAll(safeCollection(context.matchResult.addedLeaves));
        return out;
    }
    private static boolean appliesGradleScript(DSLNode node, String source, String target) {
        if (node == null || !(node.semantic instanceof PluginModel plugin)) return false;
        if (plugin.applyTarget == null) return false;

        String applied = appliedScriptPath(plugin.applyTarget);
        if (applied.isBlank()) return false;

        String rootRelative = normalizePath(applied);
        String sourceRelative = resolveRelativePath(source, applied);
        String normalizedTarget = normalizePath(target);

        return Objects.equals(rootRelative, normalizedTarget)
                || Objects.equals(sourceRelative, normalizedTarget);
    }
    private static String appliedScriptPath(ExpressionNode expr) {
        String ref = expressionReferenceText(expr);
        if (safe(ref).isBlank()) ref = expressionText(expr);
        return normalizePath(ref);
    }
    private static String resolveRelativePath(String source, String applied) {
        String normalizedApplied = normalizePath(applied);
        if (normalizedApplied.isBlank()) return "";

        List<String> parts = new ArrayList<>(pathParts(parentDir(source)));
        for (String part : pathParts(normalizedApplied)) {
            if (Objects.equals(part, ".")) continue;
            if (Objects.equals(part, "..")) {
                if (!parts.isEmpty()) parts.removeLast();
            } else {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }
    private static String parentDir(String path) {
        String p = normalizePath(path);
        int i = p.lastIndexOf('/');
        return i < 0 ? "" : p.substring(0, i);
    }
    //verify the added and removed are the same entities //
    private static boolean moduleEvidencePair(DSLNode removed, DSLNode added) {
        if (removed == null || added == null) return false;
        return sameAtomicBuildLogicEntity(removed, added);
    }
    private static boolean sameAtomicBuildLogicEntity(DSLNode a, DSLNode b) {

        if (a == null || b == null) return false;
        if (a.type != b.type) return false;
        if (isTask(a) && isTask(b)) return sameTask(a, b);
        if (isDependency(a) && isDependency(b)) return sameDependencyIdentity(a, b);
        if (isMethod(a) && isMethod(b)) return sameMethod(a, b);

        if (isContainerNode(a) || isContainerNode(b)) {
            return sameContainerWithChildEvidence(a, b);
        }

        return Objects.equals(nodeContentKey(a), nodeContentKey(b));
    }
    private static boolean isContainerNode(DSLNode n) {
        return n != null && !safeList(n.children).isEmpty();
    }
    private static boolean sameContainerWithChildEvidence(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;
        if (a.type != b.type) return false;
        if (!Objects.equals(norm(a.name), norm(b.name))) return false;

        List<DSLNode> aChildren = meaningfulChildren(a);
        List<DSLNode> bChildren = meaningfulChildren(b);

        if (aChildren.isEmpty() && bChildren.isEmpty()) {
            return Objects.equals(nodeContentKey(a), nodeContentKey(b));
        }

        if (aChildren.isEmpty() || bChildren.isEmpty()) return false;

        int matched = 0;

        for (DSLNode ca : aChildren) {
            for (DSLNode cb : bChildren) {
                if (sameAtomicBuildLogicEntity(ca, cb)) {
                    matched++;
                    break;
                }
            }
        }

        int smaller = Math.min(aChildren.size(), bChildren.size());

        return matched == smaller;
    }
    private static List<DSLNode> meaningfulChildren(DSLNode n) {
        List<DSLNode> out = new ArrayList<>();

        for (DSLNode child : safeList(n.children)) {
            if (child == null) continue;
            out.add(child);
        }

        return out;
    }
    private static String nodeContentKey(DSLNode n) {
        return n.type + "|"
                + norm(n.name) + "|"
                + norm(n.operator) + "|"
                + exprKey(n.receiver) + "|"
                + exprKey(n.value) + "|"
                + argumentKey(n.arguments) + "|"
                + semanticKey(n.semantic);
    }
    private static String exprKey(ExpressionNode expression) {
        return expression == null ? "" : norm(expression.contentKey());
    }
    private static String argumentKey(List<ExpressionNode> arguments) {
        List<String> keys = new ArrayList<>();

        for (ExpressionNode argument : safeList(arguments)) {
            keys.add(exprKey(argument));
        }

        return String.join(",", keys);
    }
    private static String semanticKey(Object semantic) {
        if (semantic == null) return "";
        if (semantic instanceof ExpressionNode expression) return exprKey(expression);
        return norm(String.valueOf(semantic));
    }

    private static <T> Collection<T> safeCollection(Collection<T> collection) {
        return collection == null ? Collections.emptyList() : collection;
    }


}
