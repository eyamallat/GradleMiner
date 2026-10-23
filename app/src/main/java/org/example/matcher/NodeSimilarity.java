package org.example.matcher;

import org.example.model.DSLNode;
import org.example.model.ExpressionNode;

import java.util.*;
import java.util.stream.Collectors;

public class NodeSimilarity {

    public static int statementEditDistance(DSLNode a, DSLNode b) {
        List<String> seqA = statementStrings(a);
        List<String> seqB = statementStrings(b);
        return sequenceEditDistance(seqA, seqB);
    }

    private static List<String> statementStrings(DSLNode node) {
        List<String> result = new ArrayList<>();
        collectStatementStrings(node, result, 1);
        return result;
    }

    private static void collectStatementStrings(DSLNode node, List<String> out, int depth) {
        for (DSLNode child : node.children) {
            out.add("d" + depth + ":" + statementKey(child));
            if (!child.children.isEmpty()) {
                collectStatementStrings(child, out, depth + 1);
            }
        }
    }

    private static String statementKey(DSLNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.type);

        if (node.name != null) {
            sb.append("|name=").append(safe(node.name));
        }

        if (node.semantic instanceof ExpressionNode exprSemantic) {
            sb.append("|semantic=").append(exprSemantic.identityKey());
        } else if (node.semantic != null) {
            sb.append("|semantic=").append(safe(node.semantic));
        }

        if (node.value instanceof ExpressionNode exprValue) {
            sb.append("|value=").append(exprValue.identityKey());
        }

        if (node.arguments != null && !node.arguments.isEmpty()) {
            sb.append("|args=").append(
                    node.arguments.stream()
                            .map(arg -> arg == null ? "null" : arg.identityKey())
                            .collect(Collectors.joining("||"))
            );
        }

        return sb.toString();
    }

    private static String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int sequenceEditDistance(List<String> a, List<String> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];

        for (int i = 0; i <= a.size(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.size(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                int cost = a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.size()][b.size()];
    }

    static int depthDifference(DSLNode a, DSLNode b) {
        return Math.abs(depth(a) - depth(b));
    }

    private static int depth(DSLNode n) {
        int d = 0;
        DSLNode cur = n.parent;
        while (cur != null) {
            d++;
            cur = cur.parent;
        }
        return d;
    }

    static int siblingIndexDifference(DSLNode a, DSLNode b) {
        int ia = siblingIndex(a);
        int ib = siblingIndex(b);

        if (ia < 0 || ib < 0) {
            return Integer.MAX_VALUE / 4;
        }
        return Math.abs(ia - ib);
    }

    private static int siblingIndex(DSLNode n) {
        if (n.parent == null) return -1;
        return n.parent.children.indexOf(n);
    }

    public static int editDistance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";

        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    static String trim(Object o) {
        return o == null ? null : o.toString().trim();
    }

    public static double matchedChildrenRatio(DSLNode a, DSLNode b, Set<MatchPair> matches) {
        int maxChildren = Math.max(a.children.size(), b.children.size());
        if (maxChildren == 0) {
            return 0.0;
        }

        int matched = 0;
        for (MatchPair p : matches) {
            if (a.children.contains(p.left()) && b.children.contains(p.right())) {
                matched++;
            }
        }

        return (double) matched / maxChildren;
    }

    public static int exactStatementMatches(DSLNode a, DSLNode b) {
        if (a.children.isEmpty() || b.children.isEmpty()) return 0;

        Set<DSLNode> usedB = new HashSet<>();
        int exact = 0;

        for (DSLNode childA : a.children) {
            for (DSLNode childB : b.children) {
                if (usedB.contains(childB)) continue;

                if (NodeConditions.deepEquals(childA, childB)) {
                    exact++;
                    usedB.add(childB);
                    break;
                }
            }
        }

        return exact;
    }

    public static int partialChildSimilarity(DSLNode a, DSLNode b, Set<MatchPair> matches) {
        if (a.children.isEmpty() || b.children.isEmpty()) return 0;

        Set<DSLNode> descA = new HashSet<>(allDescendants(a));
        Set<DSLNode> descB = new HashSet<>(allDescendants(b));

        int matched = 0;
        for (MatchPair p : matches) {
            if (descA.contains(p.left()) && descB.contains(p.right())) {
                matched++;
            }
        }

        return matched;
    }

    private static List<DSLNode> allDescendants(DSLNode node) {
        List<DSLNode> result = new ArrayList<>();
        for (DSLNode child : node.children) {
            result.add(child);
            result.addAll(allDescendants(child));
        }
        return result;
    }
}