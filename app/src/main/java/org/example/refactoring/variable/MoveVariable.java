package org.example.refactoring.variable;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;
import static org.example.refactoring.variable.VariableRuleUtils.*;

public class MoveVariable implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        List<BuildRefactoring> out = new ArrayList<>();
        List<DSLNode> removedCandidates = new ArrayList<>(context.matchResult.removedLeaves);
        List<DSLNode> addedCandidates = new ArrayList<>(context.matchResult.addedLeaves);

        for (DSLNode removed : removedCandidates) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (!isAtomicVariableEntity(removed, removedCandidates)) continue;
            if (isExternalBinding(removed)) continue;
            if (isInsideComposite(removed, context.matchResult.removedComposite)) continue;
            if (isSettingsGradleNode(removed)) continue;

            for (DSLNode added : addedCandidates) {
                if (!isAtomicVariableEntity(added, addedCandidates)) continue;
                if (isExternalBinding(added)) continue;
                if (!sameFileNature(removed, added)) continue;
                if (!sameVariableIdentity(removed, added)) continue;
                if (isSettingsGradleNode(added)) continue;
                if (isInsideComposite(added, context.matchResult.addedComposite)) continue;
                if (!isMove(removed.relativePath, added.relativePath)) continue;
                if (alreadyExistedInOldTargetFile(added, context.oldRoots)) continue;
                if (!isStandaloneVariableCandidate(removed, added)) continue;

                BuildRefactoring ref = new BuildRefactoring(
                        BuildRefactoringType.MOVE_VARIABLE,
                        "Move variable '" + nodeLocationName(removed) + "' to '" + nodeLocationName(added) + "'"

                );
                addIfAbsent(ref.removedNodes, removed);
                addIfAbsent(ref.addedNodes, added);
                out.add(ref);
            }
        }

        return deduplicate(out);
    }

    public static boolean isInsideComposite(DSLNode node, Collection<DSLNode> composites) {
        if (node == null || composites == null) return false;
        for (DSLNode c : composites) {
            if (c == null || c == node) continue;
            if (sameFile(node, c) && isDescendantOf(node, c)) return true;
        }
        return false;
    }
}
