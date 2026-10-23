package org.example.refactoring.variable;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;
import static org.example.refactoring.variable.VariableRuleUtils.*;

public class PullUpVariable implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        List<DSLNode> removedCandidates = new ArrayList<>(context.matchResult.removedLeaves);
        List<DSLNode> addedCandidates = new ArrayList<>(context.matchResult.addedLeaves);

        for (DSLNode removed : removedCandidates) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (!isAtomicVariableEntity(removed, removedCandidates)) continue;
            if (isExternalBinding(removed)) continue;
            if (isSettingsGradleNode(removed)) continue;

            for (DSLNode added : addedCandidates) {
                if (!isAtomicVariableEntity(added, addedCandidates)) continue;
                if (isExternalBinding(added)) continue;
                if (!sameFileNature(removed, added)) continue;
                if (!sameVariableIdentity(removed, added)) continue;
                if (isSettingsGradleNode(added)) continue;
                if (!isPullUp(removed.relativePath, added.relativePath)) continue;
                if (alreadyExistedInOldTargetFile(added, context.oldRoots)) continue;
                if (!isStandaloneVariableCandidate(removed, added)) continue;
                if (!sameVariableName(removed, added)) continue;
                if (isLikelyMovedFilePair(removed.relativePath, added.relativePath)) continue;

                String key = pullUpVariableKey(added);
                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k-> new BuildRefactoring(BuildRefactoringType.PULL_UP_VARIABLE, "Pull up variable to" + "'" + nodeLocationName(added) + "'")
                );
                addIfAbsent(ref.removedNodes, removed);
                addIfAbsent(ref.addedNodes, added);
            }
        }

        return new ArrayList<>(out.values());
    }
    private static String pullUpVariableKey(DSLNode added) {
        return BuildRefactoringType.PULL_UP_VARIABLE
                + "|"
                + normalizePath(added.relativePath)
                + "|"
                + norm(added.name)
                + "|"
                + added.line;
    }

    static boolean isLikelyMovedFilePair(String source, String target) {
        if (source == null || target == null) return false;
        if (source.equals(target)) return true;

        String sourceName = fileName(source);
        String targetName = fileName(target);

        if (!Objects.equals(sourceName, targetName)) return false;

        String sourceParent = parentPath(source);
        String targetParent = parentPath(target);

        if (sourceParent.isBlank() || targetParent.isBlank()) return false;

        return sourceParent.endsWith("/" + lastPathSegment(targetParent))
                || targetParent.endsWith("/" + lastPathSegment(sourceParent))
                || sourceParent.equals(targetParent);
    }

    private static String fileName(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static String parentPath(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    private static String lastPathSegment(String path) {
        if (path == null || path.isBlank()) return "";
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }
}
