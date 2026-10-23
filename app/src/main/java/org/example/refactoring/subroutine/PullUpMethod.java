package org.example.refactoring.subroutine;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class PullUpMethod implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        for (DSLNode removed : context.matchResult.removedComposite) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (!isMethod(removed)) continue;
            for (DSLNode added : context.matchResult.addedComposite) {
                if (!isMethod(added)) continue;
                if (!sameFileNature(removed, added)) continue;
                if (!isPullUp(removed.relativePath, added.relativePath)) continue;
                if (!sameMethod(removed, added)) continue;
                if (alreadyExistedMethodInOldTargetFile(added, context.oldRoots)) continue;
                String key = normalizePath(added.relativePath) + "|" + added.type + "|" + norm(added.name) + "|" + added.line;
                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> new BuildRefactoring(
                                BuildRefactoringType.PULL_UP_METHOD,
                                "Pull up method '" + added.name + "' to '" + nodeLocationName(added) + "'"
                        )
                );

                addIfAbsent(ref.removedNodes, removed);
                addIfAbsent(ref.addedNodes, added);
            }
        }
        return deduplicate(new ArrayList<>(out.values()));
    }
    public static boolean alreadyExistedMethodInOldTargetFile(DSLNode target, DSLNode oldRoot) {
        if (oldRoot == null || !isMethod(target)) return false;
        for (DSLNode existing : flatten(oldRoot)) {
            if (!sameFile(existing, target)) continue;
            if (!isMethod(existing)) continue;
            if (sameMethod(existing, target)) return true;
        }
        return false;
    }

}
