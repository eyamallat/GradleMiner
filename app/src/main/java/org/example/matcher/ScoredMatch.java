package org.example.matcher;

import org.example.model.DSLNode;

public class ScoredMatch {

    public DSLNode left;
    public DSLNode right;
    public MatchScore score;

    public ScoredMatch(DSLNode left, DSLNode right, MatchScore score) {
        this.left = left;
        this.right = right;
        this.score = score;
    }
}