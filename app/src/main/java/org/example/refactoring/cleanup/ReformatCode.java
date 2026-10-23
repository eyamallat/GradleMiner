package org.example.refactoring.cleanup;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.model.DependencyModel;
import org.example.model.PluginModel;
import org.example.model.TaskModel;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class ReformatCode implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        Set<MatchPair> evidence = new LinkedHashSet<>();
        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.left() == null || p.right() == null) continue;
            if (!sameFile(p.left(), p.right())) continue;
            if (!hasSameContentKeyWithDifferentSyntax(p.left(), p.right())) continue;
            evidence.add(p);
        }

        evidence.addAll(findMovedUnchangedRegions(context.matchResult.matches));
        evidence.addAll(findBindingWrapperReformats(context));
        if (evidence.isEmpty()) return List.of();

        List<MatchPair> pairs = new ArrayList<>(evidence);
        BuildRefactoring ref = new BuildRefactoring(
                BuildRefactoringType.REFORMAT_CODE,
                "Reformat code (no semantic change)"
        );
        ref.relatedMatches.addAll(pairs);
        return List.of(ref);
    }


    private static boolean hasSameContentKeyWithDifferentSyntax(DSLNode left, DSLNode right) {
        if (left == null || right == null || left.type != right.type) return false;

        if (left.semantic instanceof DependencyModel before
                && right.semantic instanceof DependencyModel after) {
            return Objects.equals(before.contentKey(), after.contentKey())
                    && hasDifferentSyntax(left, right);
        }

        if (left.semantic instanceof PluginModel before
                && right.semantic instanceof PluginModel after) {
            return Objects.equals(before.id, after.id)
                    && before.appliedViaApply != after.appliedViaApply;
        }

        if (left.semantic instanceof TaskModel before
                && right.semantic instanceof TaskModel after) {
            return isEquivalentTaskSyntaxRewrite(before, after)
                    && sameSubtreeChildren(left, right);
        }

        return false;
    }
    private static boolean isEquivalentTaskSyntaxRewrite(TaskModel before, TaskModel after) {
        if (!Objects.equals(norm(before.taskName()), norm(after.taskName()))) return false;
        if (!Objects.equals(norm(before.taskType()), norm(after.taskType()))) return false;
        if (!Objects.equals(before.arguments(), after.arguments())) return false;

        return isEquivalentTaskStyle(before.syntaxStyle(), after.syntaxStyle());
    }

    private static boolean isEquivalentTaskStyle(String before, String after) {
        Set<String> configurationStyles = Set.of(
                "tasks.existing",
                "tasks.getting",
                "named",
                "tasks.named",
                "tasks.getbyname"
        );

        return !Objects.equals(norm(before), norm(after))
                && configurationStyles.contains(norm(before))
                && configurationStyles.contains(norm(after));
    }
    private static boolean hasDifferentSyntax(DSLNode left, DSLNode right) {
        return !Objects.equals(left.receiver, right.receiver)
                || !Objects.equals(left.value, right.value)
                || !Objects.equals(left.arguments, right.arguments)
                || !Objects.equals(left.operator, right.operator);
    }

    private static List<MatchPair> findBindingWrapperReformats(RefactoringContext context) {
        List<MatchPair> out = new ArrayList<>();
        out.addAll(findWrappedBindings(context.oldRoots, context.newRoots, false));
        out.addAll(findWrappedBindings(context.newRoots, context.oldRoots, true));
        return out;
    }

    private static List<MatchPair> findWrappedBindings(
            DSLNode flatRoot,
            DSLNode wrappedRoot,
            boolean reverse
    ) {
        List<MatchPair> out = new ArrayList<>();
        List<DSLNode> flatNodes = flatten(flatRoot);

        for (DSLNode wrapper : flatten(wrappedRoot)) {
            if (!isBindingWrapper(wrapper)) continue;

            List<MatchPair> wrapped = new ArrayList<>();

            for (DSLNode child : safeList(wrapper.children)) {
                if (!isSimpleBinding(child)) continue;

                DSLNode flat = findMatchingFlatBinding(flatNodes, child);
                if (flat == null) continue;

                wrapped.add(reverse ? new MatchPair(child, flat) : new MatchPair(flat, child));
            }

            if (!wrapped.isEmpty()) {
                out.addAll(wrapped);
            }
        }

        return out;
    }

    private static DSLNode findMatchingFlatBinding(
            List<DSLNode> flatNodes,
            DSLNode wrapped
    ) {
        String scopedName = scopedBindingName(wrapped);
        if (scopedName.isBlank()) return null;

        DSLNode match = null;
        for (DSLNode candidate : flatNodes) {
            if (!isSimpleBinding(candidate)) continue;
            if (!sameFile(candidate, wrapped)) continue;
            if (Objects.equals(scopedBlockPath(candidate.parent), scopedBlockPath(wrapped.parent))) continue;
            if (!Objects.equals(scopedBindingName(candidate), scopedName)) continue;
            if (candidate.type != wrapped.type) continue;
            if (!Objects.equals(candidate.value, wrapped.value)) continue;
            if (!Objects.equals(candidate.operator, wrapped.operator)) continue;

            if (match != null) return null;
            match = candidate;
        }
        return match;
    }

    private static boolean isBindingWrapper(DSLNode node) {
        return node != null
                && (node.type == DSLNode.Type.BLOCK || node.type == DSLNode.Type.METHOD_CALL)
                && !norm(node.name).isBlank()
                && !safeList(node.children).isEmpty()
                && node.parent != null;
    }

    private static boolean isSimpleBinding(DSLNode node) {
        return node != null
                && (node.type == DSLNode.Type.ASSIGNMENT || node.type == DSLNode.Type.DECLARATION)
                && safeList(node.children).isEmpty();
    }

    private static String scopedBindingName(DSLNode node) {
        if (node == null) return "";
        return joinPath(scopedBlockPath(node.parent), norm(node.name));
    }

    private static String scopedBlockPath(DSLNode node) {
        List<String> parts = new ArrayList<>();
        for (DSLNode current = node; current != null; current = current.parent) {
            if (isBindingWrapper(current)) {
                parts.add(norm(current.name));
            }
        }
        Collections.reverse(parts);
        return String.join(".", parts);
    }

    private static String joinPath(String prefix, String suffix) {
        if (prefix == null || prefix.isBlank()) return suffix;
        if (suffix == null || suffix.isBlank()) return prefix;
        if (suffix.equals(prefix) || suffix.startsWith(prefix + ".")) return suffix;
        return prefix + "." + suffix;
    }

    private static List<MatchPair> findMovedUnchangedRegions(Collection<MatchPair> matches) {
        List<MatchPair> stablePairs = new ArrayList<>();
        for (MatchPair pair : matches) {
            if (isStablePair(pair)) stablePairs.add(pair);
        }

        List<MatchPair> moved = new ArrayList<>();
        for (MatchPair candidate : stablePairs) {
            if (isMovedUnchangedRegion(candidate, stablePairs)) {
                moved.add(candidate);
            }
        }
        return moved;
    }

    private static boolean isStablePair(MatchPair pair) {
        return pair != null
                && pair.left() != null
                && pair.right() != null
                && pair.left().line > 0
                && pair.right().line > 0
                && sameFile(pair.left(), pair.right())
                && sameSubtreeContent(pair.left(), pair.right());
    }

    private static boolean isMovedUnchangedRegion(
            MatchPair candidate,
            List<MatchPair> stablePairs
    ) {
        if (!equivalentScope(candidate.left().parent, candidate.right().parent)) return false;

        for (MatchPair anchor : stablePairs) {
            if (anchor == candidate) continue;
            if (anchor.left().parent != candidate.left().parent) continue;
            if (anchor.right().parent != candidate.right().parent) continue;

            boolean beforeOld = candidate.left().line < anchor.left().line;
            boolean beforeNew = candidate.right().line < anchor.right().line;

            if (beforeOld != beforeNew) return true; // Relative order crossed.
        }

        return false;
    }

    private static boolean sameSubtreeContent(DSLNode left, DSLNode right) {
        if (left == right) return true;
        if (left == null || right == null || left.type != right.type) return false;
        if (!Objects.equals(norm(left.name), norm(right.name))) return false;
        if (!Objects.equals(left.declaredType, right.declaredType)) return false;
        if (!Objects.equals(left.receiver, right.receiver)) return false;
        if (!Objects.equals(left.value, right.value)) return false;
        if (!Objects.equals(left.semantic, right.semantic)) return false;
        if (!Objects.equals(left.operator, right.operator)) return false;
        if (!Objects.equals(left.arguments, right.arguments)) return false;
        if (!Objects.equals(left.typeArguments, right.typeArguments)) return false;
        if (left.safeCall != right.safeCall) return false;
        return sameSubtreeChildren(left, right);
    }

    private static boolean sameSubtreeChildren(DSLNode left, DSLNode right) {
        List<DSLNode> leftChildren = safeList(left == null ? null : left.children);
        List<DSLNode> rightChildren = safeList(right == null ? null : right.children);
        if (leftChildren.size() != rightChildren.size()) return false;

        for (int i = 0; i < leftChildren.size(); i++) {
            if (!sameSubtreeContent(leftChildren.get(i), rightChildren.get(i))) return false;
        }
        return true;
    }

    private static boolean equivalentScope(DSLNode before, DSLNode after) {
        if (before == null || after == null) return before == after;
        return before.type == after.type
                && Objects.equals(norm(before.name), norm(after.name));
    }
}
