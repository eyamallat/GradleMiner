// app/src/main/java/org/example/refactoring/subroutine/PushDownTask.java
package org.example.refactoring.subroutine;

import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class PushDownTask implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        List<BuildRefactoring> out = new ArrayList<>();
        for (DSLNode removed : context.matchResult.removedComposite) {
            if (isDeletedSourceFile(removed, context)) continue;
            if (!isTask(removed)) continue;
            for (DSLNode added : context.matchResult.addedComposite) {
                if (!isTask(added)) continue;
                if (!sameFileNature(removed, added)) continue;
                if (!isPushDown(removed.relativePath, added.relativePath)) continue;
                if (!sameTask(removed, added)) continue;
                if (alreadyExistedTaskInOldTargetFile(added, context.oldRoots)) continue;

                BuildRefactoring ref = new BuildRefactoring(BuildRefactoringType.PUSH_DOWN_TASK, "Push down task '" + nodeLocationName(removed) + "' to '" + nodeLocationName(added) + "'");
                ref.removedNodes.add(removed);
                ref.addedNodes.add(added);
                out.add(ref);
            }
        }
        return deduplicate(out);
    }
    public static boolean alreadyExistedTaskInOldTargetFile(DSLNode target, DSLNode oldRoot) {
        if (oldRoot == null || !isTask(target)) return false;
        for (DSLNode existing : flatten(oldRoot)) {
            if (!sameFile(existing, target)) continue;
            if (!isTask(existing)) continue;
            if (sameTask(existing, target)) return true;
        }
        return false;
    }

}
