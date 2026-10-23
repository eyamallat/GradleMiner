// app/src/main/java/org/example/refactoring/dependency/PullUpDependency.java
package org.example.refactoring.dependency;

import org.example.model.DSLNode;
import org.example.model.DependencyModel;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class PullUpDependency implements RefactoringRule {
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
                    if (!isPullUp(removed.relativePath, added.relativePath)) continue;

                    String key = dependencyAddedKey(added);

                    BuildRefactoring ref = out.computeIfAbsent(
                            key,
                            k -> new BuildRefactoring(
                                    BuildRefactoringType.PULL_UP_DEPENDENCY,
                                    "Pull up dependency '" + dependencyDisplayName(added) + "'"
                            )
                    );

                    addIfAbsent(ref.removedNodes, removed);
                    addIfAbsent(ref.addedNodes, added);
                }
            }

            return new ArrayList<>(out.values());
        }

        private static String dependencyAddedKey(DSLNode added) {
            return BuildRefactoringType.PULL_UP_DEPENDENCY
                    + "|"
                    + normalizePath(added.relativePath)
                    + "|"
                    + dependencyDisplayName(added);
        }
    public static String dependencyDisplayName(DSLNode node) {
        if (node == null) return "";

        if (node.semantic instanceof DependencyModel d) {
            if (d.notation != null) return d.notation.toString();

            if (!safe(d.catalogAlias).isBlank()) return d.catalogAlias;

            if (!safe(d.projectPath).isBlank()) {
                return "project(" + d.projectPath + ")";
            }

            if (!safe(d.group).isBlank() || !safe(d.name).isBlank()) {
                StringBuilder s = new StringBuilder();
                s.append(safe(d.group)).append(":").append(safe(d.name));

                if (!safe(d.version).isBlank()) {
                    s.append(":").append(safe(d.version));
                }

                return s.toString();
            }
        }

        if (node.value != null) return safe(node.value.contentKey());

        return safe(node.name);
    }

}


