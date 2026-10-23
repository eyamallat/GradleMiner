package org.example.refactoring.variable;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;
import static org.example.refactoring.variable.PullUpVariable.isLikelyMovedFilePair;
import static org.example.refactoring.variable.VariableRuleUtils.*;

public class PushDownVariable implements RefactoringRule {
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
                if (!isPushDown(removed.relativePath, added.relativePath)) continue;
                if (alreadyExistedInOldTargetFile(added, context.oldRoots)) continue;
                if (!isStandaloneVariableCandidate(removed, added)) continue;
                if (!sameVariableName(removed, added)) continue;
                if (isLikelyMovedFilePair(removed.relativePath, added.relativePath)) continue;

                String key = pushDownVariableKey(removed, added);
                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> new BuildRefactoring(BuildRefactoringType.PUSH_DOWN_VARIABLE,
                                "Push down variable '" + nodeLocationName(removed) + "'"
                        )
                );
                addIfAbsent(ref.removedNodes, removed);
                addIfAbsent(ref.addedNodes, added);
            }
        }

        return new ArrayList<>(out.values());
    }

    private static String pushDownVariableKey(DSLNode removed, DSLNode added) {
        return BuildRefactoringType.PUSH_DOWN_VARIABLE + "|" + normalizePath(removed.relativePath) + "|" + norm(removed.name)
                + "|" + removed.line + "->" + normalizePath(added.relativePath) + "|" + norm(added.name) + "|" + added.line;
    }
}
