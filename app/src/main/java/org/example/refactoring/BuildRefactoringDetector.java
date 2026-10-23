package org.example.refactoring;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.refactoring.cleanup.*;
import org.example.refactoring.dependency.*;
import org.example.refactoring.module.*;
import org.example.refactoring.properties.*;
import org.example.refactoring.subroutine.*;
import org.example.refactoring.variable.*;

import java.util.*;

public class BuildRefactoringDetector {
    private final List<RefactoringRule> primaryRules = List.of(
            new ReformatCode(),
            new RenameField(),
            new ExtractModule(),
            new ExtractAndPullUpModule(),
            new ExtractAndPushDownModule(),
            new ExtractMethod(),
            new PullUpMethod(),
            new ExtractTask(),
            new PushDownTask(),
            new SchedulingTasks(),
            new DRY(),
            new MoveDependency(),
            new PullUpDependency(),
            new PushDownDependency(),
            new ExternalizeProperties(),
            new ExtractVariable(),
            new InlineVariable(),
            new MoveVariable(),
            new PullUpVariable(),
            new PushDownVariable(),
            new ExtractAndMoveVariable(),
            new ExtractAndPullUpVariable()
            );

    public List<BuildRefactoring> detect(RefactoringContext context) {
        List<BuildRefactoring> primary = new ArrayList<>();

        for (RefactoringRule rule : primaryRules) {
            List<BuildRefactoring> detected = rule.detect(context);

            if (rule instanceof RenameField) {
                rememberRenamedFileTargets(context, detected);
            }

            primary.addAll(detected);
        }

        primary = applyPrioritySuppression(primary);

        Set<DSLNode> explained = explainedNodes(primary);

        List<BuildRefactoring> cleanup = new RemoveUnusedCode(explained).detect(context);

        List<BuildRefactoring> all = new ArrayList<>();
        all.addAll(primary);
        all.addAll(cleanup);

        return collapseFinalDuplicateInstances(applyPrioritySuppression(all));
    }

    private void rememberRenamedFileTargets(RefactoringContext context, List<BuildRefactoring> refs) {
        if (context == null || refs == null) return;

        for (BuildRefactoring ref : refs) {
            if (ref == null || ref.type != BuildRefactoringType.RENAME_FIELD) continue;
            if (ref.description == null || !ref.description.startsWith("Rename file '")) continue;

            for (DSLNode added : ref.addedNodes) {
                if (added == null) continue;
                String path = RuleUtils.normalizePath(added.relativePath);
                if (!path.isBlank()) {
                    context.renamedFileTargets.add(path);
                }
            }
        }
    }
    private Set<DSLNode> explainedNodes(List<BuildRefactoring> refs) {
        Set<DSLNode> explained = new LinkedHashSet<>();

        for (BuildRefactoring ref : refs) {
            if (ref == null) continue;

            explained.addAll(ref.removedNodes);

            explained.addAll(ref.addedNodes);

            for (MatchPair p : ref.relatedMatches) {
                if (p == null) continue;
                if (p.left() != null) explained.add(p.left());
                if (p.right() != null) explained.add(p.right());
            }
        }

        return explained;
    }

    private List<BuildRefactoring> applyPrioritySuppression(List<BuildRefactoring> refs) {
        List<BuildRefactoring> kept = new ArrayList<>();
        for (BuildRefactoring candidate : refs) {
            if (!isSuppressed(candidate, refs)) {
                kept.add(candidate);
            }
        }
        return RuleUtils.deduplicate(kept);
    }

    private List<BuildRefactoring> collapseFinalDuplicateInstances(List<BuildRefactoring> refs) {
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (BuildRefactoring ref : refs) {
            if (ref == null) continue;

            String key = ref.type + "|" + ref.description;
            BuildRefactoring existing = out.get(key);
            if (existing == null) {
                out.put(key, ref);
                continue;
            }

            mergeEvidence(existing, ref);
        }

        return new ArrayList<>(out.values());
    }

    private void mergeEvidence(BuildRefactoring target, BuildRefactoring source) {
        for (DSLNode node : RuleUtils.safeList(source.removedNodes)) {
            RuleUtils.addIfAbsent(target.removedNodes, node);
        }

        for (DSLNode node : RuleUtils.safeList(source.addedNodes)) {
            RuleUtils.addIfAbsent(target.addedNodes, node);
        }

        for (MatchPair match : RuleUtils.safeList(source.relatedMatches)) {
            if (match != null && !target.relatedMatches.contains(match)) {
                target.relatedMatches.add(match);
            }
        }
    }

    private boolean isSuppressed(BuildRefactoring candidate, List<BuildRefactoring> all) {
        for (BuildRefactoring major : all) {
            if (major == candidate) continue;
            if (!hasPriorityOver(major.type, candidate.type)) continue;
            if (!compatibleDirection(major.type, candidate.type)) continue;
            if (covers(major, candidate)) return true;
        }
        return false;
    }

    private enum Direction { NONE, MOVE, PULL_UP, PUSH_DOWN }

    private Direction directionOf(BuildRefactoringType t) {
        return switch (t) {
            case EXTRACT_AND_PULL_UP_MODULE, PULL_UP_VARIABLE, PULL_UP_DEPENDENCY, PULL_UP_METHOD, EXTRACT_AND_PULL_UP_VARIABLE ->
                    Direction.PULL_UP;
            case EXTRACT_AND_PUSH_DOWN_MODULE, PUSH_DOWN_VARIABLE, PUSH_DOWN_DEPENDENCY, PUSH_DOWN_TASK ->
                    Direction.PUSH_DOWN;
            case EXTRACT_MODULE, MOVE_VARIABLE, MOVE_DEPENDENCY, EXTRACT_AND_MOVE_VARIABLE ->
                    Direction.MOVE;

            default -> Direction.NONE;
        };
    }

    private boolean compatibleDirection(BuildRefactoringType high, BuildRefactoringType low) {
        Direction hd = directionOf(high);
        Direction ld = directionOf(low);
        if (hd == Direction.NONE || ld == Direction.NONE) return true;
        return hd == ld;
    }

    private boolean hasPriorityOver(BuildRefactoringType high, BuildRefactoringType low) {
        return switch (high) {
            case EXTRACT_MODULE ->
            low == BuildRefactoringType.REMOVE_UNUSED_CODE ||
                             low == BuildRefactoringType.MOVE_VARIABLE
                            || low == BuildRefactoringType.MOVE_DEPENDENCY
                            || low == BuildRefactoringType.DRY;


            case EXTRACT_AND_PULL_UP_MODULE ->
                    low == BuildRefactoringType.REMOVE_UNUSED_CODE ||
                             low == BuildRefactoringType.PULL_UP_VARIABLE
                            || low == BuildRefactoringType.PULL_UP_DEPENDENCY
                            || low == BuildRefactoringType.PULL_UP_METHOD
                            || low == BuildRefactoringType.DRY;

            case EXTRACT_AND_PUSH_DOWN_MODULE ->
            low == BuildRefactoringType.REMOVE_UNUSED_CODE ||
                             low == BuildRefactoringType.PUSH_DOWN_VARIABLE
                            || low == BuildRefactoringType.PUSH_DOWN_DEPENDENCY
                            || low == BuildRefactoringType.PUSH_DOWN_TASK
                            || low == BuildRefactoringType.DRY;

            case EXTERNALIZE_PROPERTIES,RENAME_FIELD, INLINE_VARIABLE, DRY, SCHEDULING_TASKS -> low == BuildRefactoringType.REMOVE_UNUSED_CODE;
            case MOVE_DEPENDENCY, PULL_UP_DEPENDENCY, PUSH_DOWN_DEPENDENCY, EXTRACT_METHOD, PULL_UP_METHOD, EXTRACT_TASK, PUSH_DOWN_TASK, EXTRACT_VARIABLE, MOVE_VARIABLE, PULL_UP_VARIABLE, PUSH_DOWN_VARIABLE, EXTRACT_AND_MOVE_VARIABLE, EXTRACT_AND_PULL_UP_VARIABLE-> low == BuildRefactoringType.REMOVE_UNUSED_CODE || low == BuildRefactoringType.DRY;



            default -> false;
        };
    }

    private boolean covers(BuildRefactoring high, BuildRefactoring low) {
        Set<DSLNode> highRemoved = effectiveRemoved(high);
        Set<DSLNode> highAdded = effectiveAdded(high);

        Set<DSLNode> lowRemoved = effectiveRemoved(low);
        Set<DSLNode> lowAdded = effectiveAdded(low);

        if (isModuleLevelMove(high.type)
                && low.type == BuildRefactoringType.DRY
                && sameTargetFile(highAdded, lowAdded)
                && nodesOverlapByContainment(highRemoved, lowRemoved)) {
            return true;
        }

        if (isModuleLevelMove(high.type)
                && low.type == BuildRefactoringType.REMOVE_UNUSED_CODE
                && moduleExtractionCoversCleanupRemoval(highRemoved, highAdded, lowRemoved)) {
            return true;
        }

        if (isModuleLevelMove(high.type)
                && moduleMoveCoversLowLevelMove(highRemoved, highAdded, lowRemoved, lowAdded)) {
            return true;
        }

        boolean lowHasNodes = !lowRemoved.isEmpty() || !lowAdded.isEmpty();

        boolean nodeCovered =
                (lowRemoved.isEmpty() || nodesCovered(lowRemoved, highRemoved))
                        && (lowAdded.isEmpty() || nodesCovered(lowAdded, highAdded));

        boolean pairCovered = pairsCovered(low.relatedMatches, high.relatedMatches);

        if (lowHasNodes && nodeCovered) return true;
        return pairCovered;
    }

    private boolean moduleMoveCoversLowLevelMove(
            Set<DSLNode> highRemoved,
            Set<DSLNode> highAdded,
            Set<DSLNode> lowRemoved,
            Set<DSLNode> lowAdded
    ) {
        Set<String> highRemovedFiles = nodeFiles(highRemoved);
        Set<String> highAddedFiles = nodeFiles(highAdded);
        Set<String> lowRemovedFiles = nodeFiles(lowRemoved);
        Set<String> lowAddedFiles = nodeFiles(lowAdded);

        if (highRemovedFiles.isEmpty() || highAddedFiles.isEmpty()) return false;
        if (lowRemovedFiles.isEmpty() || lowAddedFiles.isEmpty()) return false;

        return highRemovedFiles.containsAll(lowRemovedFiles)
                && highAddedFiles.containsAll(lowAddedFiles);
    }

    private boolean moduleExtractionCoversCleanupRemoval(
            Set<DSLNode> highRemoved,
            Set<DSLNode> highAdded,
            Set<DSLNode> lowRemoved
    ) {
        Set<String> highRemovedFiles = nodeFiles(highRemoved);
        Set<String> highAddedFiles = nodeFiles(highAdded);
        Set<String> lowRemovedFiles = nodeFiles(lowRemoved);

        if (highRemovedFiles.isEmpty() || highAddedFiles.isEmpty()) return false;
        if (lowRemovedFiles.isEmpty()) return false;

        return highRemovedFiles.containsAll(lowRemovedFiles);
    }

    private boolean isModuleLevelMove(BuildRefactoringType type) {
        return type == BuildRefactoringType.EXTRACT_MODULE
                || type == BuildRefactoringType.EXTRACT_AND_PULL_UP_MODULE
                || type == BuildRefactoringType.EXTRACT_AND_PUSH_DOWN_MODULE;
    }

    private boolean sameTargetFile(Set<DSLNode> highAdded, Set<DSLNode> lowAdded) {
        Set<String> highFiles = nodeFiles(highAdded);
        Set<String> lowFiles = nodeFiles(lowAdded);
        if (highFiles.isEmpty() || lowFiles.isEmpty()) return false;

        for (String file : lowFiles) {
            if (highFiles.contains(file)) return true;
        }

        return false;
    }

    private Set<String> nodeFiles(Set<DSLNode> nodes) {
        Set<String> files = new LinkedHashSet<>();
        if (nodes == null) return files;

        for (DSLNode node : nodes) {
            if (node == null) continue;
            String path = RuleUtils.normalizePath(node.relativePath);
            if (!path.isBlank()) files.add(path);
        }

        return files;
    }

    private boolean nodesOverlapByContainment(Set<DSLNode> left, Set<DSLNode> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return false;

        for (DSLNode a : left) {
            for (DSLNode b : right) {
                if (containsNode(a, b) || containsNode(b, a)) {
                    return true;
                }
            }
        }

        return false;
    }



    private Set<DSLNode> effectiveRemoved(BuildRefactoring ref) {
        Set<DSLNode> out = new LinkedHashSet<>();
        if (ref == null) return out;

        for (DSLNode n : ref.removedNodes) if (n != null) out.add(n);

        if (out.isEmpty()) {
            for (MatchPair p : ref.relatedMatches) {
                if (p != null && p.left() != null) out.add(p.left());
            }
        }

        return out;
    }

    private Set<DSLNode> effectiveAdded(BuildRefactoring ref) {
        Set<DSLNode> out = new LinkedHashSet<>();
        if (ref == null) return out;

        for (DSLNode n : ref.addedNodes) if (n != null) out.add(n);

        if (out.isEmpty()) {
            for (MatchPair p : ref.relatedMatches) {
                if (p != null && p.right() != null) out.add(p.right());
            }
        }

        return out;
    }

    private boolean nodesCovered(Set<DSLNode> small, Set<DSLNode> big) {
        if (small == null || small.isEmpty()) return true;
        if (big == null || big.isEmpty()) return false;

        for (DSLNode s : small) {
            boolean covered = false;
            for (DSLNode b : big) {
                if (containsNode(b, s)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return false;
        }
        return true;
    }

    private boolean containsNode(DSLNode container, DSLNode node) {
        return container != null
                && node != null
                && (container == node || RuleUtils.isDescendantOf(node, container));
    }

    private boolean pairsCovered(List<MatchPair> lowPairs, List<MatchPair> highPairs) {
        if (lowPairs == null || lowPairs.isEmpty()) return false;
        if (highPairs == null || highPairs.isEmpty()) return false;

        for (MatchPair lp : lowPairs) {
            if (lp == null || lp.left() == null || lp.right() == null) continue;

            boolean covered = false;
            for (MatchPair hp : highPairs) {
                if (hp == null || hp.left() == null || hp.right() == null) continue;
                if (containsNode(hp.left(), lp.left()) && containsNode(hp.right(), lp.right())) {
                    covered = true;
                    break;
                }
            }

            if (!covered) return false;
        }

        return true;
    }
}
