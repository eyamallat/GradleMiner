package org.example.matcher;

import org.example.model.DSLNode;

import java.util.*;

public class PropertiesMatcher {

    public static MatchResult matchPropertiesFile(DSLNode beforeFile, DSLNode afterFile) {
        MatchResult result = new MatchResult();

        Map<String, Deque<DSLNode>> beforeMap = new LinkedHashMap<>();
        Map<String, Deque<DSLNode>> afterMap = new LinkedHashMap<>();

        for (DSLNode child : beforeFile.children) {
            if (child.type == DSLNode.Type.ASSIGNMENT) {
                beforeMap.computeIfAbsent(child.name, k -> new ArrayDeque<>()).addLast(child);
            }
        }

        for (DSLNode child : afterFile.children) {
            if (child.type == DSLNode.Type.ASSIGNMENT) {
                afterMap.computeIfAbsent(child.name, k -> new ArrayDeque<>()).addLast(child);
            }
        }

        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(beforeMap.keySet());
        allKeys.addAll(afterMap.keySet());

        for (String key : allKeys) {
            Deque<DSLNode> leftQueue = beforeMap.getOrDefault(key, new ArrayDeque<>());
            Deque<DSLNode> rightQueue = afterMap.getOrDefault(key, new ArrayDeque<>());

            while (!leftQueue.isEmpty() || !rightQueue.isEmpty()) {
                DSLNode left = leftQueue.isEmpty() ? null : leftQueue.removeFirst();
                DSLNode right = rightQueue.isEmpty() ? null : rightQueue.removeFirst();

                if (left == null && right != null) {
                    result.addedLeaves.add(right);
                    continue;
                }
                if (left != null && right == null) {
                    result.removedLeaves.add(left);
                    continue;
                }
                if (left == null) {
                    continue;
                }

                MatchPair pair = new MatchPair(left, right);
                result.leafRound1Matches.add(pair);
                result.matches.add(pair);
            }
        }
        matchSameValueRenamedProperties(result);


        return result;
    }

    private static void matchSameValueRenamedProperties(MatchResult result) {
        List<DSLNode> removed = new ArrayList<>(result.removedLeaves);
        List<DSLNode> added = new ArrayList<>(result.addedLeaves);

        Set<DSLNode> usedAdded = new HashSet<>();

        for (DSLNode left : removed) {
            if (left == null || left.type != DSLNode.Type.ASSIGNMENT) continue;

            DSLNode match = null;

            for (DSLNode right : added) {
                if (right == null || right.type != DSLNode.Type.ASSIGNMENT) continue;
                if (usedAdded.contains(right)) continue;

                if (Objects.equals(norm(left.name), norm(right.name))) continue;
                if (!sameValue(left, right)) continue;

                // Avoid unsafe match if more than one added node has same value.
                if (match != null) {
                    match = null;
                    break;
                }

                match = right;
            }

            if (match == null) continue;

            result.removedLeaves.remove(left);
            result.addedLeaves.remove(match);
            usedAdded.add(match);

            MatchPair pair = new MatchPair(left, match);
            result.leafRound1Matches.add(pair);
            result.matches.add(pair);
        }
    }

    private static boolean sameValue(DSLNode a, DSLNode b) {
        if (a.value == b.value) return true;
        if (a.value == null || b.value == null) return false;
        return Objects.equals(a.value.contentKey(), b.value.contentKey());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
