package org.example.matcher;

import java.util.Comparator;
import java.util.Objects;

public class MatchScoreComparator implements Comparator<ScoredMatch> {
    public enum Mode { LEAF, COMPOSITE }

    private final Mode mode;

    public MatchScoreComparator(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public int compare(ScoredMatch a, ScoredMatch b) {
        return mode == Mode.LEAF ? compareLeaves(a, b) : compareComposites(a, b);
    }

    private int compareLeaves(ScoredMatch a, ScoredMatch b) {
        int cmp;

        cmp = Integer.compare(a.score.statementEditDistance(), b.score.statementEditDistance());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(a.score.nameEditDistance(), b.score.nameEditDistance());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(a.score.depthDifference(), b.score.depthDifference());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(a.score.siblingIndexDifference(), b.score.siblingIndexDifference());
        if (cmp != 0) return cmp;

        return stableTieBreak(a, b);
    }
    private int compareComposites(ScoredMatch a, ScoredMatch b) {
        int cmp;

        cmp = Integer.compare(a.score.statementEditDistance(), b.score.statementEditDistance());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(a.score.nameEditDistance(), b.score.nameEditDistance());
        if (cmp != 0) return cmp;

        cmp = Double.compare(b.score.matchedChildrenRatio(), a.score.matchedChildrenRatio());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(b.score.exactMatches(), a.score.exactMatches());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(b.score.matchedStatements(), a.score.matchedStatements());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(a.score.depthDifference(), b.score.depthDifference());
        if (cmp != 0) return cmp;

        cmp = Integer.compare(a.score.siblingIndexDifference(), b.score.siblingIndexDifference());
        if (cmp != 0) return cmp;


        return stableTieBreak(a, b);
    }

    private int stableTieBreak(ScoredMatch a, ScoredMatch b) {
        int cmp;

        cmp = nullSafe(a.left.relativePath).compareTo(nullSafe(b.left.relativePath));
        if (cmp != 0) return cmp;
        cmp = nullSafe(a.left.name).compareTo(nullSafe(b.left.name));
        if (cmp != 0) return cmp;
        cmp = nullSafe(a.right.name).compareTo(nullSafe(b.right.name));
        if (cmp != 0) return cmp;
        cmp = Integer.compare(a.left.line, b.left.line);
        if (cmp != 0) return cmp;
        cmp = nullSafe(a.right.relativePath).compareTo(nullSafe(b.right.relativePath));
        if (cmp != 0) return cmp;
        return Integer.compare(a.right.line, b.right.line);

    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
