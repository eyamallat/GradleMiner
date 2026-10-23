package org.example.refactoring.subroutine;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.model.ExpressionNode;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;

public class DRY implements RefactoringRule {
    private record Contributor(DSLNode before, DSLNode after) {}
    private record Replacement(String before, String after) {}

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        List<DSLNode> carriers = carrierCandidates(context);
        List<List<Contributor>> groups = duplicateContributorGroups(context);

        for (List<Contributor> rawGroup : groups) {
            List<Contributor> contributors = distinctContributors(removeNestedContributors(rawGroup));

            if (contributors.size() < 2) {
                continue;
            }

            if (!pairwiseIndependent(contributors)) {
                continue;
            }


            DSLNode carrier = bestCarrierForGroup(carriers, contributors);
            if (carrier == null) {
                continue;
            }


            String key = normalizePath(carrier.relativePath) + "|DRY|" + carrier.type + "|" + norm(carrier.name) + "|" + carrier.line;

            BuildRefactoring ref = out.computeIfAbsent(
                    key, k -> new BuildRefactoring(BuildRefactoringType.DRY, "Consolidate duplicated logic into '" + nodeLocationName(carrier) + "'"));

            addIfAbsent(ref.addedNodes, carrier);

            for (Contributor contributor : contributors) {
                addContributorNodeIfAbsent(ref.removedNodes, contributor.before());
            }
        }

        return deduplicate(new ArrayList<>(out.values()));
    }



    private static List<DSLNode> carrierCandidates(RefactoringContext context) {
        LinkedHashSet<DSLNode> out = new LinkedHashSet<>();

        for (DSLNode added : safeCollection(context.matchResult.addedComposite)) {
            if (isCompositeCandidate(added) ) {
                out.add(added);
            }
        }
        for (MatchPair pair : safeCollection(context.matchResult.matches)) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (!isCompositeCandidate(pair.right())) continue;

            out.add(pair.right());
        }

        return new ArrayList<>(out);
    }
    private static boolean isCompositeCandidate(DSLNode node) {
        if (node == null || node.children == null || node.children.isEmpty()) return false;
        if (isFile(node)) return false;

        return node.type == DSLNode.Type.BLOCK
                || node.type == DSLNode.Type.TASK_DECLARATION
                || node.type == DSLNode.Type.TASK_CONFIGURATION
                || node.type == DSLNode.Type.METHOD_DECL
                || node.type == DSLNode.Type.CLOSURE;
    }

    private static List<List<Contributor>> duplicateContributorGroups(RefactoringContext context) {
        List<Contributor> candidates = new ArrayList<>();

        for (DSLNode removed : safeCollection(context.matchResult.removedComposite)) {
            if (isDeletedSourceFile(removed, context)) continue;
            addContributor(candidates, new Contributor(removed, null));
        }

        for (MatchPair pair : safeCollection(context.matchResult.matches)) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (isDeletedSourceFile(pair.left(), context)) continue;
            if (!isCompositeCandidate(pair.left())) continue;

            if (!matchedContributorCandidate(pair.left(), pair.right())) continue;

            addContributor(candidates, new Contributor(pair.left(), pair.right()));
        }

        return cloneGroups(candidates);
    }

    private static void addContributor(List<Contributor> candidates, Contributor contributor) {
        if (contributor == null || contributor.before() == null) return;
        if (!isCompositeCandidate(contributor.before())) return;
        if (skeleton(contributor.before()).isEmpty()) return;

        candidates.add(contributor);
    }

    private static boolean matchedContributorCandidate(DSLNode before, DSLNode after) {
        if (before == null || after == null) return false;

        List<String> beforeBody = skeleton(before);
        List<String> afterBody = skeleton(after);

        if (beforeBody.isEmpty()) return false;
        if (afterBody.size() < beforeBody.size()) return true;

        return hasGeneralizedValueReplacement(before, after);
    }

    private static boolean hasGeneralizedValueReplacement(DSLNode before, DSLNode after) {
        List<DSLNode> beforeBody = bodyStatements(before);
        List<DSLNode> afterBody = bodyStatements(after);

        List<DSLNode> mappedBefore = new ArrayList<>();
        List<DSLNode> mappedAfter = new ArrayList<>();
        List<Replacement> replacements = new ArrayList<>();

        mapCommonStatementSubsequence(beforeBody, afterBody, mappedBefore, mappedAfter, replacements);

        for (Replacement replacement : replacements) {
            if (replacement == null) continue;

            String beforeText = norm(replacement.before());
            String afterText = norm(replacement.after());

            if (beforeText.isBlank() || afterText.isBlank()) continue;
            if (Objects.equals(beforeText, afterText)) continue;

            return true;
        }

        return false;
    }

    private static DSLNode bestCarrierForGroup(List<DSLNode> carriers, List<Contributor> contributors) {
        List<DSLNode> valid = new ArrayList<>();

        for (DSLNode carrier : safeList(carriers)) {
            if (carrier == null) continue;
            if (!isDryCarrier(carrier)) continue;
            if (isOneOfContributors(carrier, contributors)) continue;
            if (!carrierExplainsGroup(carrier, contributors)) continue;

            valid.add(carrier);
        }

        if (valid.isEmpty()) return null;
        valid = mostSpecificCarriers(valid);
        return valid.size() == 1 ? valid.getFirst() : null;
    }

    private static boolean carrierExplainsGroup(DSLNode carrier, List<Contributor> contributors) {
        if (carrier == null || contributors == null || contributors.isEmpty()) return false;

        boolean hasGeneralization = false;
        for (Contributor contributor : safeList(contributors)) {
            FragmentMapping mapping = mapFragmentToCarrier(contributor == null ? null : contributor.before(), carrier);
            if (mapping == null) return false;
            hasGeneralization |= !mapping.replacements().isEmpty();
        }

        return hasGeneralization;
    }

    private record FragmentMapping(List<DSLNode> mappedOld, List<DSLNode> mappedNew, List<Replacement> replacements) {}

    private static FragmentMapping mapFragmentToCarrier(DSLNode contributor, DSLNode carrier) {
        List<DSLNode> oldBody = bodyStatements(contributor);
        List<DSLNode> carrierBody = bodyStatements(carrier);

        if (oldBody.isEmpty() || carrierBody.isEmpty()) return null;

        List<DSLNode> mappedOld = new ArrayList<>();
        List<DSLNode> mappedNew = new ArrayList<>();
        List<Replacement> replacements = new ArrayList<>();

        mapCommonStatementSubsequence(oldBody, carrierBody, mappedOld, mappedNew, replacements);

        if (!new HashSet<>(mappedOld).containsAll(oldBody)) return null;

        return new FragmentMapping(mappedOld, mappedNew, replacements);
    }

    private static List<DSLNode> mostSpecificCarriers(List<DSLNode> carriers) {
        List<DSLNode> out = new ArrayList<>();

        for (DSLNode candidate : safeList(carriers)) {
            if (candidate == null) continue;

            boolean containsAnotherCarrier = false;
            for (DSLNode other : safeList(carriers)) {
                if (other == null || other == candidate) continue;

                if (isDescendantOf(other, candidate)) {
                    containsAnotherCarrier = true;
                    break;
                }
            }

            if (!containsAnotherCarrier) {
                out.add(candidate);
            }
        }

        return out;
    }


    private static boolean hasIteratorEvidence(DSLNode node) {
        if (node == null || node.type != DSLNode.Type.BLOCK) return false;

        if ("for".equals(norm(node.name))) {
            return !safeList(node.arguments).isEmpty();
        }

        if ("each".equals(norm(node.name))) {
            return hasClosureParameter(node);
        }

        return false;
    }

    private static boolean hasClosureParameter(DSLNode node) {
        for (DSLNode child : safeList(node == null ? null : node.children)) {
            if (child == null || child.type != DSLNode.Type.CLOSURE) continue;
            if (!safeList(child.arguments).isEmpty()) return true;
        }

        return false;
    }

    private static boolean isDryCarrier(DSLNode carrier) {
        if (carrier == null) return false;

        return hasIteratorEvidence(carrier)
                || carrier.type == DSLNode.Type.METHOD_DECL;
    }


    private static void mapCommonStatementSubsequence(List<DSLNode> oldBody, List<DSLNode> newBody, List<DSLNode> mappedOld, List<DSLNode> mappedNew, List<Replacement> replacements) {
        int[][] dp = new int[oldBody.size() + 1][newBody.size() + 1];

        for (int i = oldBody.size() - 1; i >= 0; i--) {
            for (int j = newBody.size() - 1; j >= 0; j--) {
                if (sameStatementRole(oldBody.get(i), newBody.get(j))) {
                    dp[i][j] = 1 + dp[i + 1][j + 1];
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        int i = 0;
        int j = 0;
        while (i < oldBody.size() && j < newBody.size()) {
            DSLNode oldNode = oldBody.get(i);
            DSLNode newNode = newBody.get(j);

            if (sameStatementRole(oldNode, newNode)) {
                mappedOld.add(oldNode);
                mappedNew.add(newNode);
                replacements.addAll(statementReplacements(oldNode, newNode));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
    }

    private static boolean sameStatementRole(DSLNode before, DSLNode after) {
        if (before == null || after == null) return false;

        if (sameBindingRole(before, after)) return true;
        return (sameContainerRole(before, after)) ;
    }

    private static boolean sameBindingRole(DSLNode a, DSLNode b) {
        if (!isBindingLike(a) || !isBindingLike(b)) return false;

        String left = a.name;
        String right = b.name;

        return !left.isBlank() && Objects.equals(left, right);
    }

    private static boolean isBindingLike(DSLNode node) {
        if (node == null) return false;

        return node.type == DSLNode.Type.ASSIGNMENT
                || node.type == DSLNode.Type.DECLARATION
                || node.type == DSLNode.Type.METHOD_CALL
                || node.type == DSLNode.Type.TASK_RELATION;
    }

    private static boolean sameContainerRole(DSLNode a, DSLNode b) {
        if (!isDryContainer(a) || !isDryContainer(b)) return false;

        return Objects.equals(a.name, b.name);
    }

    private static boolean isDryContainer(DSLNode node) {
        if (node == null) return false;

        return node.type == DSLNode.Type.BLOCK
                || node.type == DSLNode.Type.CLOSURE
                || node.type == DSLNode.Type.TASK_DECLARATION
                || node.type == DSLNode.Type.TASK_CONFIGURATION
                || node.type == DSLNode.Type.METHOD_DECL;
    }


    private static List<Replacement> statementReplacements(DSLNode before, DSLNode after) {
        List<Replacement> out = new ArrayList<>();

        addReplacement(out, expressionText(before == null ? null : before.value), expressionText(after == null ? null : after.value));
        addReplacement(out, expressionText(before == null ? null : before.receiver), expressionText(after == null ? null : after.receiver));

        List<ExpressionNode> beforeArgs = safeList(before == null ? null : before.arguments);
        List<ExpressionNode> afterArgs = safeList(after == null ? null : after.arguments);

        int count = Math.min(beforeArgs.size(), afterArgs.size());
        for (int i = 0; i < count; i++) {
            addReplacement(out, expressionText(beforeArgs.get(i)), expressionText(afterArgs.get(i)));
        }

        return out;
    }

    private static void addReplacement(List<Replacement> out, String before, String after) {
        String left = norm(before);
        String right = norm(after);

        if (left.isBlank() || right.isBlank()) return;
        if (Objects.equals(left, right)) return;

        out.add(new Replacement(left, right));
    }

    private static List<List<Contributor>> cloneGroups(List<Contributor> candidates) {
        List<List<Contributor>> groups = new ArrayList<>();
        boolean[] used = new boolean[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;

            List<Contributor> group = new ArrayList<>();
            Contributor seed = candidates.get(i);
            group.add(seed);

            for (int j = i + 1; j < candidates.size(); j++) {
                if (used[j]) continue;
                Contributor candidate = candidates.get(j);

                if (duplicatedOldFragments(seed.before(), candidate.before())) {
                    group.add(candidate);
                    used[j] = true;
                }
            }

            if (group.size() >= 2) {
                groups.add(group);
            }
        }

        return groups;
    }

    private static boolean duplicatedOldFragments(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;
        if (a.parent != b.parent) return false;
        if (a.type != b.type) return false;

        return skeleton(a).equals(skeleton(b));
    }

    private static List<String> skeleton(DSLNode node) {
        if (node == null) return List.of();

        List<String> out = new ArrayList<>();
        for (DSLNode statement : bodyStatements(node)) {
            out.add(statementRole(statement));
        }

        return out;
    }

    private static List<DSLNode> bodyStatements(DSLNode root) {
        List<DSLNode> out = new ArrayList<>();
        Deque<DSLNode> stack = new ArrayDeque<>();

        List<DSLNode> children = safeList(root == null ? null : root.children);
        for (int i = children.size() - 1; i >= 0; i--) {
            DSLNode child = children.get(i);
            if (child != null) stack.push(child);
        }

        while (!stack.isEmpty()) {
            DSLNode node = stack.pop();

            if (!isFile(node)) {
                out.add(node);
            }

            List<DSLNode> nested = safeList(node.children);
            for (int i = nested.size() - 1; i >= 0; i--) {
                DSLNode child = nested.get(i);
                if (child != null) stack.push(child);
            }
        }

        return out;
    }

    private static String statementRole(DSLNode node) {
        if (node == null) return "";

        if (isBindingLike(node)) {
            return "binding|" + node.name;
        }

        if (isDryContainer(node)) {
            return "container|" + node.type + "|" + node.name;
        }

        return node.type + "|" +node.name + "|arity=" + safeList(node.arguments).size();
    }

    private static List<Contributor> removeNestedContributors(List<Contributor> contributors) {
        List<Contributor> out = new ArrayList<>();

        for (Contributor c : safeList(contributors)) {
            if (c == null || c.before() == null) continue;

            boolean nested = false;
            for (Contributor other : safeList(contributors)) {
                if (other == null || other.before() == null || c == other) continue;

                if (isDescendantOf(c.before(), other.before())) {
                    nested = true;
                    break;
                }
            }

            if (!nested) out.add(c);
        }

        return out;
    }

    private static List<Contributor> distinctContributors(List<Contributor> contributors) {
        Map<String, Contributor> out = new LinkedHashMap<>();

        for (Contributor contributor : safeList(contributors)) {
            if (contributor == null || contributor.before() == null) continue;
            out.putIfAbsent(contributorKey(contributor.before()), contributor);
        }

        return new ArrayList<>(out.values());
    }

    private static void addContributorNodeIfAbsent(List<DSLNode> nodes, DSLNode node) {
        if (node == null) return;

        String key = contributorKey(node);
        for (DSLNode existing : safeList(nodes)) {
            if (Objects.equals(contributorKey(existing), key)) return;
        }

        nodes.add(node);
    }

    private static String contributorKey(DSLNode node) {
        if (node == null) return "";

        DSLNode parent = node.parent;
        String parentKey = parent == null
                ? ""
                : parent.type + "|" + norm(parent.name) + "|" + parent.line;

        return normalizePath(node.relativePath)
                + "|" + node.type
                + "|" + norm(node.name)
                + "|" + node.line
                + "|" + parentKey;
    }

    private static boolean pairwiseIndependent(List<Contributor> contributors) {
        for (int i = 0; i < contributors.size(); i++) {
            for (int j = i + 1; j < contributors.size(); j++) {
                DSLNode a = contributors.get(i).before();
                DSLNode b = contributors.get(j).before();

                if (a == null || b == null) return false;
                if (isDescendantOf(a, b) || isDescendantOf(b, a)) return false;
            }
        }

        return true;
    }

    private static boolean isOneOfContributors(DSLNode carrier, List<Contributor> contributors) {
        for (Contributor contributor : safeList(contributors)) {
            if (contributor == null) continue;
            if (carrier == contributor.before()) return true;
        }

        return false;
    }

    private static String expressionText(ExpressionNode expr) {
        return expr == null ? "" : safe(expr.contentKey());
    }

    private static <T> Collection<T> safeCollection(Collection<T> values) {
        return values == null ? List.of() : values;
    }
}
