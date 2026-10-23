package org.example.refactoring.variable;

import org.example.matcher.MatchPair;
import org.example.matcher.MatchResult;
import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.cleanup.RenameField.oldNameReplacedByNewNameInUsages;
import static org.example.refactoring.RuleUtils.*;
import static org.example.refactoring.variable.VariableRuleUtils.*;

public class ExtractVariable implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();


        MatchResult match = context.matchResult;
        List<DSLNode> variableCandidates = new ArrayList<>();
        variableCandidates.addAll(match.addedLeaves);
        variableCandidates.addAll(match.addedComposite);

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (DSLNode added : variableCandidates) {
            if (!isAtomicVariableEntity(added, variableCandidates)) continue;
            if (isExternalBinding(added)) continue;
            if (isRenamedVariableTarget(added, context)) continue;

            String varName = normalizeReferenceName(added.name);
            String key = normalizePath(added.relativePath) + "|" + norm(varName);

            for (MatchPair p : match.matches) {
                if (p == null || p.left() == null || p.right() == null) continue;
                DSLNode before = p.left();
                DSLNode after = p.right();

                if (isDeletedSourceFile(before, context)) continue;
                if (!sameFile(added, before) || !sameFile(added, after)) continue;
                if (!isVariableExtractionEvidence(before, after, added, context)) continue;

                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> {
                            BuildRefactoring r = new BuildRefactoring(
                                    BuildRefactoringType.EXTRACT_VARIABLE, "Extract variable '" + nodeLocationName(added) + "'");
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

    private static boolean isRenamedVariableTarget(DSLNode added, RefactoringContext context) {
        if (added == null || context == null || context.matchResult == null) return false;
        if (!isVariableEntity(added)) return false;

        for (DSLNode before : context.matchResult.removedLeaves) {
            if (isDeletedSourceFile(before, context)) continue;
            if (isRenamedVariablePair(before, added, context)) return true;
        }

        for (MatchPair pair : context.matchResult.matches) {
            if (pair == null || pair.left() == null || pair.right() != added) continue;
            if (isDeletedSourceFile(pair.left(), context)) continue;
            if (isRenamedVariablePair(pair.left(), added, context)) return true;
        }

        return false;
    }

    private static boolean isRenamedVariablePair(DSLNode before, DSLNode after, RefactoringContext context) {
        if (before == null || after == null) return false;
        if (!isVariableEntity(before) || !isVariableEntity(after)) return false;
        if (!sameFile(before, after)) return false;
        if (sameVariableName(before, after)) return false;

        if (!equivalentModuloWrapper(extractInitializer(before), extractInitializer(after), Map.of())) {
            return false;
        }

        return oldNameReplacedByNewNameInUsages(before.name, after.name, context);
    }
}
