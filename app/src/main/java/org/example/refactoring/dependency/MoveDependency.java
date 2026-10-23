package org.example.refactoring.dependency;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class MoveDependency implements RefactoringRule {
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
                if (!isMove(removed.relativePath, added.relativePath)) continue;

                BuildRefactoring ref = new BuildRefactoring(
                        BuildRefactoringType.MOVE_DEPENDENCY,
                        "Move dependency '" + nodeLocationName(removed) + "'"
                );
                ref.removedNodes.add(removed);
                ref.addedNodes.add(added);
                out.putIfAbsent(nodeKey(removed) + "->" + nodeKey(added), ref);
            }
        }

        return new ArrayList<>(out.values());

    }
}
