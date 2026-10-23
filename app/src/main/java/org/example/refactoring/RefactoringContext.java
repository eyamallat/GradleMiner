package org.example.refactoring;

import org.example.matcher.MatchResult;
import org.example.model.DSLNode;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.example.refactoring.RuleUtils.*;

public class RefactoringContext {
    public final MatchResult matchResult;
    public final DSLNode oldRoots;
    public final DSLNode newRoots;
    public final Set<String> renamedFileTargets = new LinkedHashSet<>();
    private Set<String> oldFilePaths;
    private Set<String> newFilePaths;


    public RefactoringContext(MatchResult matchResult, DSLNode oldRoots, DSLNode newRoots) {
        this.matchResult = matchResult;
        this.oldRoots = oldRoots;
        this.newRoots = newRoots;
    }

    public Set<String> oldFilePaths() {
        if (oldFilePaths == null) oldFilePaths = collectFilePaths(oldRoots);
        return oldFilePaths;
    }

    public Set<String> newFilePaths() {
        if (newFilePaths == null) newFilePaths = collectFilePaths(newRoots);
        return newFilePaths;
    }

    private static Set<String> collectFilePaths(DSLNode root) {
        Set<String> out = new LinkedHashSet<>();
        collectFilePaths(root, out);
        return out;
    }

    private static void collectFilePaths(DSLNode node, Set<String> out) {
        if (node == null) return;
        if (isFile(node)) {
            String path = normalizePath(node.relativePath);
            if (!path.isBlank()) out.add(path);
        }
        for (DSLNode child : safeList(node.children)) {
            collectFilePaths(child, out);
        }
    }
}
