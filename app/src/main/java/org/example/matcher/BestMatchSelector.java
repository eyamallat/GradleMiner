package org.example.matcher;

import org.example.model.DSLNode;

import java.util.*;

public class BestMatchSelector {

    public static List<MatchPair> select(List<ScoredMatch> candidates, Comparator<ScoredMatch> comparator) {
        List<ScoredMatch> sorted = new ArrayList<>(candidates);
        sorted.sort(comparator);

        Set<DSLNode> usedLeft = new HashSet<>();
        Set<DSLNode> usedRight = new HashSet<>();
        List<MatchPair> selected = new ArrayList<>();

        for (ScoredMatch m : sorted) {
            MatchPair pair = new MatchPair(m.left, m.right);

            if (usedLeft.contains(m.left) || usedRight.contains(m.right)) {
                continue;
            }

            selected.add(pair);
            usedLeft.add(m.left);
            usedRight.add(m.right);
        }

        return selected;
    }
}
