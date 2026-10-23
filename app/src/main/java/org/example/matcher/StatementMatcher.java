package org.example.matcher;

import org.example.model.DSLNode;
import org.example.model.ExpressionNode;

import java.util.*;

public class StatementMatcher {

    enum MatchPhase {
        LEAF,
        COMPOSITE
    }
    @FunctionalInterface
    interface MatchCondition {
        boolean test(DSLNode left, DSLNode right, Set<MatchPair> existingMatches);
    }

    static class UnmatchedResult {
        List<DSLNode> left;
        List<DSLNode> right;
        Set<MatchPair> matchedPairs;
    }

    public static MatchResult matchTrees(
            DSLNode t1,
            DSLNode t2,
            Map<String, ExpressionNode> beforeArgumentizationEnv,
            Map<String, ExpressionNode> afterArgumentizationEnv
    ) {
        MatchResult result = new MatchResult();

        List<DSLNode> leavesA = TreeUtils.leafNodes(t1);
        List<DSLNode> leavesB = TreeUtils.leafNodes(t2);

        List<DSLNode> compositesA = TreeUtils.compositeNodes(t1);
        List<DSLNode> compositesB = TreeUtils.compositeNodes(t2);
        Map<String, ExpressionNode> beforeEnv = beforeArgumentizationEnv == null ? Map.of() : beforeArgumentizationEnv;
        Map<String, ExpressionNode> afterEnv = afterArgumentizationEnv == null ? Map.of() : afterArgumentizationEnv;

        UnmatchedResult leaves1 = matchNodes(leavesA, leavesB, (n1, n2, ignored) -> NodeConditions.condition1(n1, n2), result.matches, MatchPhase.LEAF);
        result.leafRound1Matches.addAll(leaves1.matchedPairs);

        UnmatchedResult leaves2 = matchNodes(leaves1.left, leaves1.right, (n1, n2, ignored) -> NodeConditions.condition2(n1, n2), result.matches, MatchPhase.LEAF);
        result.leafRound2Matches.addAll(leaves2.matchedPairs);

        UnmatchedResult leaves3 = matchNodes(leaves2.left, leaves2.right, (n1, n2, existing) -> NodeConditions.condition3(n1, n2, beforeEnv, afterEnv, existing), result.matches, MatchPhase.LEAF);
        result.leafRound3Matches.addAll(leaves3.matchedPairs);

        UnmatchedResult comp1 = matchNodes(compositesA, compositesB, (a, b, matches) -> NodeConditions.conditionC1(a, b), result.matches, MatchPhase.COMPOSITE);
        result.compositeRound1Matches.addAll(comp1.matchedPairs);

        UnmatchedResult comp2 = matchNodes(comp1.left, comp1.right, NodeConditions::conditionC2, result.matches, MatchPhase.COMPOSITE);
        result.compositeRound2Matches.addAll(comp2.matchedPairs);


        result.removedLeaves.addAll(leaves3.left);
        result.addedLeaves.addAll(leaves3.right);

        result.removedComposite.addAll(comp2.left);
        result.addedComposite.addAll(comp2.right);


        return result;
    }



    private static UnmatchedResult matchNodes(List<DSLNode> leftNodes, List<DSLNode> rightNodes, MatchCondition matchCondition, Set<MatchPair> existingMatches, MatchPhase phase) {
        List<DSLNode> left = new ArrayList<>(leftNodes);
        List<DSLNode> right = new ArrayList<>(rightNodes);

        List<ScoredMatch> candidates = new ArrayList<>();

        List<DSLNode> outer = left.size() <= right.size() ? left : right;
        List<DSLNode> inner = left.size() <= right.size() ? right : left;
        boolean leftIsOuter = outer == left;

        for (DSLNode o : outer) {
            for (DSLNode i : inner) {
                DSLNode n1 = leftIsOuter ? o : i;
                DSLNode n2 = leftIsOuter ? i : o;

                if (!matchCondition.test(n1, n2, existingMatches)) {
                    continue;
                }

                if (phase == MatchPhase.LEAF) {
                    candidates.add(new ScoredMatch(n1, n2, MatchScorer.compute(n1, n2)));
                } else {
                    candidates.add(new ScoredMatch(n1, n2, MatchScorer.compute(n1, n2, existingMatches)));
                }
            }
        }

        MatchScoreComparator comparator = new MatchScoreComparator(
                phase == MatchPhase.LEAF ? MatchScoreComparator.Mode.LEAF : MatchScoreComparator.Mode.COMPOSITE
        );
        List<MatchPair> selectedPairs = BestMatchSelector.select(candidates, comparator);

        Set<MatchPair> matchedPairs = new HashSet<>();

        for (MatchPair pair : selectedPairs) {
            matchedPairs.add(pair);
            existingMatches.add(pair);

            left.remove(pair.left());
            right.remove(pair.right());
        }

        UnmatchedResult res = new UnmatchedResult();
        res.left = left;
        res.right = right;
        res.matchedPairs = matchedPairs;
        return res;
    }
}
