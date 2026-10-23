package org.example.matcher;

import org.example.model.DSLNode;

import java.util.*;

import static org.example.matcher.NodeSimilarity.*;

public class MatchScorer {

    public static MatchScore compute(DSLNode a, DSLNode b, Set<MatchPair> existingMatches) {
        int exactMatches = exactStatementMatches(a, b);
        int matchedStatements = partialChildSimilarity(a, b, existingMatches);
        double matchedChildrenRatio = matchedChildrenRatio(a, b, existingMatches);

        int stmtEdit = statementEditDistance(a, b);
        int nameEdit = editDistance(a.name, b.name);

        int depthDiff = depthDifference(a, b);
        int siblingDiff = isInsideUnorderedContainer(a, b) ? 0 : siblingIndexDifference(a, b);

        return new MatchScore(
                matchedChildrenRatio,
                exactMatches,
                matchedStatements,
                stmtEdit,
                depthDiff,
                siblingDiff,
                nameEdit
        );
    }
    public static MatchScore compute(DSLNode a, DSLNode b) {
        int stmtEdit = statementEditDistance(a, b);
        int nameEdit = editDistance(a.name, b.name);

        int depthDiff = depthDifference(a, b);
        int siblingDiff = isInsideUnorderedContainer(a, b) ? 0 : siblingIndexDifference(a, b);

        return new MatchScore(
                0.0,
                0,
                0,
                stmtEdit,
                depthDiff,
                siblingDiff,
                nameEdit
        );
    }
    private static boolean isInsideUnorderedContainer(DSLNode a, DSLNode b) {
        if (a == null || b == null || a.parent == null || b.parent == null) {
            return false;
        }

        if (a.parent.type != b.parent.type) {
            return false;
        }

        DSLNode.Type t = a.parent.type;
        return t == DSLNode.Type.REPOSITORIES_BLOCK
                || t == DSLNode.Type.DEPENDENCIES_BLOCK
                || t == DSLNode.Type.PLUGINS_BLOCK;
    }


}
