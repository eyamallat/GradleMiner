package org.example.refactoring.dependency;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class PushDownDependency implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        for (DSLNode removed : context.matchResult.removedLeaves) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (!isDependency(removed)) continue;
            for (DSLNode added : context.matchResult.addedLeaves) {
                if (!isDependency(added)) continue;
                if (context.renamedFileTargets.contains(normalizePath(added.relativePath))) continue;
                if (!sameFileNature(removed, added)) continue;
                if (!sameDependencyIdentity(removed, added)) continue;
                if (!isPushDown(removed.relativePath, added.relativePath)) continue;

                String key = BuildRefactoringType.PUSH_DOWN_DEPENDENCY + "|" + nodeLocationName(removed);
                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> new BuildRefactoring(
                                BuildRefactoringType.PUSH_DOWN_DEPENDENCY,
                                "Push down dependency '" + nodeLocationName(removed) + "'"
                        )
                );
                addIfAbsent(ref.removedNodes, removed);
                addIfAbsent(ref.addedNodes, added);
            }
        }
        return new ArrayList<>(out.values());
    }
}
