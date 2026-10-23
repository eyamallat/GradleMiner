package org.example.refactoring.variable;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;
import static org.example.refactoring.variable.VariableRuleUtils.*;

public class ExtractAndMoveVariable implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        List<DSLNode> addedCandidates = new ArrayList<>();
        addedCandidates.addAll(context.matchResult.addedLeaves);
        addedCandidates.addAll(context.matchResult.addedComposite);

        for (DSLNode added : addedCandidates) {
            if (!isAtomicVariableEntity(added, addedCandidates)) continue;
            if (isExternalBinding(added)) continue;
            if (alreadyExistedInOldTargetFile(added, context.oldRoots)) continue;
            String varName = normalizeReferenceName(added.name);
            String key = normalizePath(added.relativePath) + "|" + norm(varName);

            for (MatchPair p : context.matchResult.matches) {
                if (p == null || p.left() == null || p.right() == null) continue;
                DSLNode before = p.left();
                DSLNode after = p.right();

                if (isDeletedSourceFile(before, context)) continue;
                if (!sameFile(before, after) && !sameLogicalConsumerFile(before, after, context)) continue;
                if (!sameFileNature(after, added)) continue;
                if (!isMove(after.relativePath, added.relativePath)) continue;
                if (!isVariableExtractionEvidence(before, after, added, context, true)) continue;

                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> {
                            BuildRefactoring r = new BuildRefactoring(
                                    BuildRefactoringType.EXTRACT_AND_MOVE_VARIABLE,
                                    "Extract and move variable '" + nodeLocationName(added) + "'"
                            );
                            addIfAbsent(r.addedNodes, added);
                            return r;
                        }
                );
                addIfAbsent(ref.removedNodes, before);
                addIfAbsent(ref.addedNodes, after);
                if (!ref.relatedMatches.contains(p)) ref.relatedMatches.add(p);
            }
        }

        return new ArrayList<>(out.values());
    }
}
