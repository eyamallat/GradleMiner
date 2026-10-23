package org.example.refactoring.variable;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;
import static org.example.refactoring.variable.VariableRuleUtils.*;

public class ExtractAndPullUpVariable implements RefactoringRule {
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
                if (!isPullUp(after.relativePath, added.relativePath)) continue;
                if (!isVariableExtractionEvidence(before, after, added, context)) continue;

                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> {
                            BuildRefactoring r = new BuildRefactoring(
                                    BuildRefactoringType.EXTRACT_AND_PULL_UP_VARIABLE,
                                    "Extract and pull up variable '" + displayLocationName(added) + "'"
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

    private static String displayLocationName(DSLNode node) {
        if (node == null) return "";

        String path = normalizePath(node.relativePath);
        String name = logicalExternalPropertyKey(node.name);

        if (path.isBlank()) return name;
        if (node.line > 0) return path + ":" + name + " (" + node.line + ")";
        return path + ":" + name;
    }

    private static String logicalExternalPropertyKey(String key) {
        String normalized = normalizeReferenceName(key);
        if (safe(normalized).isBlank()) return "";

        String[] parts = normalized.split("\\.");
        if (parts.length == 0) return normalized.trim();

        return parts[parts.length - 1].trim();
    }
}
