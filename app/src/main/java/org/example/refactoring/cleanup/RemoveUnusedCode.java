package org.example.refactoring.cleanup;

import org.example.matcher.MatchPair;
import org.example.model.*;
import org.example.refactoring.*;

import java.util.*;

import static org.example.refactoring.RuleUtils.*;


public class RemoveUnusedCode implements RefactoringRule {
    private final Set<DSLNode> explained;

    public RemoveUnusedCode(Set<DSLNode> explained) {
        this.explained = explained == null ? Set.of() : explained;
    }

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        List<BuildRefactoring> result = new ArrayList<>();
        var match = context.matchResult;
        Set<DSLNode> reportedComposites = new LinkedHashSet<>();
        List<DSLNode> removedComposites = new ArrayList<>(match.removedComposite);
        removedComposites.sort(Comparator.comparingInt(RuleUtils::depth));
        Set<String> deletedFiles = deletedFilePaths(context.oldRoots, context.newRoots);

        for (String path : deletedFiles) {
            if (hasExplainedNodeInFile(path)) continue;

            BuildRefactoring ref = new BuildRefactoring(
                    BuildRefactoringType.REMOVE_UNUSED_CODE,
                    "Remove unused file '" + path + "'"
            );

            result.add(ref);
        }
        for (DSLNode composite : removedComposites) {

            if (composite == null) continue;
            if (isExplainedByRefactoring(composite, explained)) continue;
            //if (isSemanticallyStillUsed(composite, context.newRoots)) continue;
            if (insideRemovedCompositeRegion(composite, reportedComposites)) continue;
            if (!allNestedRemovedLeavesAreUnused(composite, match.removedLeaves, match.addedLeaves, match.matches)) continue;
            if (isInDeletedFile(composite, deletedFiles)) continue;

            BuildRefactoring ref = new BuildRefactoring(
                    BuildRefactoringType.REMOVE_UNUSED_CODE,
                    "Remove unused element '" + nodeLocationName(composite) + "'"
            );
            ref.removedNodes.add(composite);
            result.add(ref);
            reportedComposites.add(composite);
        }

        for (DSLNode removed : match.removedLeaves) {
            if (removed == null) continue;
            if (insideRemovedCompositeRegion(removed, reportedComposites)) continue;
            if (hasMatchedRightSide(removed, match.matches)) continue;
            if (existsEquivalent(removed, match.addedLeaves)) continue;
            if (existsEquivalentInMatches(removed, match.matches)) continue;
            //if (isSemanticallyStillUsed(removed, context.newRoots)) continue;
            if (isExplainedByRefactoring(removed, explained)) continue;
            if (isInDeletedFile(removed, deletedFiles)) continue;

            BuildRefactoring ref = new BuildRefactoring(
                    BuildRefactoringType.REMOVE_UNUSED_CODE, "Remove unused element '" + nodeLocationName(removed) + "'"
            );
            ref.removedNodes.add(removed);
            result.add(ref);
        }

        return deduplicate(result);
    }
    private static boolean isInDeletedFile(DSLNode node, Set<String> deletedFiles) {
        if (node == null) return false;
        return deletedFiles.contains(normalizePath(node.relativePath));
    }
    private boolean hasExplainedNodeInFile(String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isBlank()) return false;

        for (DSLNode node : explained) {
            if (node == null) continue;
            if (normalizedPath.equals(normalizePath(node.relativePath))) {
                return true;
            }
        }

        return false;
    }
    private boolean allNestedRemovedLeavesAreUnused(DSLNode composite, Set<DSLNode> removedLeaves, Set<DSLNode> addedLeaves, Collection<MatchPair> matches) {
        boolean hasNestedLeaf = false;

        for (DSLNode leaf : removedLeaves) {
            if (leaf == null) continue;
            if (!RuleUtils.isDescendantOf(leaf, composite)) continue;

            hasNestedLeaf = true;

            if (hasMatchedRightSide(leaf, matches)) return false;
            if (existsEquivalent(leaf, addedLeaves)) return false;
            if (existsEquivalentInMatches(leaf, matches)) return false;
            //if (isSemanticallyStillUsed(leaf, context.newRoots)) return false;
            if (isExplainedByRefactoring(leaf, explained)) return false;
        }

        return hasNestedLeaf;
    }
    private boolean existsEquivalent(DSLNode target, Set<DSLNode> candidates) {
        for (DSLNode c : candidates) {
            if (equivalent(target, c)) return true;
            if (sameLogicalVariableValue(target, c)) return true;
            if (containsEquivalentExpression(target, c)) return true;
        }
        return false;
    }

    private boolean hasMatchedRightSide(DSLNode target, Collection<MatchPair> matches) {
        if (target == null || matches == null) return false;

        for (MatchPair pair : matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (pair.left() == target || sameNodeIdentity(pair.left(), target)) return true;
        }

        return false;
    }

    private boolean existsEquivalentInMatches(DSLNode target, Collection<MatchPair> matches) {
        if (target == null || matches == null) return false;

        for (MatchPair pair : matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (pair.left() != target && !sameNodeIdentity(pair.left(), target)) continue;

            if (equivalent(target, pair.right())) return true;
            if (sameLogicalVariableValue(target, pair.right())) return true;
            if (containsEquivalentExpression(target, pair.right())) return true;
        }

        return false;
    }

    private boolean sameLogicalVariableValue(DSLNode removed, DSLNode added) {
        if (removed == null || added == null) return false;
        if (!isVariableNode(removed) || !isVariableNode(added)) return false;

        String removedName = baseVariableName(removed.name);
        String addedName = baseVariableName(added.name);
        if (removedName.isBlank() || addedName.isBlank()) return false;
        if (!Objects.equals(removedName, addedName)) return false;

        ExpressionNode removedValue = variableLikeValue(removed);
        ExpressionNode addedValue = variableLikeValue(added);
        if (removedValue == null || addedValue == null) return false;

        return sameExpression(removedValue, addedValue);
    }

    private String baseVariableName(String name) {
        String n = safe(name);
        if (n.isBlank()) return "";

        int dot = n.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < n.length()) {
            return norm(n.substring(dot + 1));
        }

        return norm(n);
    }
    private boolean containsEquivalentExpression(DSLNode target, DSLNode candidate) {
        if (target == null || candidate == null) return false;

        List<ExpressionNode> targetExpressions = comparableNodeExpressions(target);
        if (targetExpressions.isEmpty()) return false;

        for (ExpressionNode candidateExpression : comparableNodeExpressions(candidate)) {
            for (ExpressionNode targetExpression : targetExpressions) {
                if (containsEquivalentExpression(target, targetExpression, candidateExpression)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean containsEquivalentExpression(DSLNode target, ExpressionNode targetExpression, ExpressionNode candidateExpression) {
        if (targetExpression == null || candidateExpression == null) return false;
        if (equivalentEmbeddedExpression(target, targetExpression, candidateExpression)) return true;

        for (ExpressionNode child : expressionChildren(candidateExpression)) {
            if (containsEquivalentExpression(target, targetExpression, child)) return true;
        }

        return false;
    }

    private boolean equivalentEmbeddedExpression(DSLNode target, ExpressionNode targetExpression, ExpressionNode candidateExpression) {
        if (target == null || targetExpression == null || candidateExpression == null) return false;

        if (target.type == DSLNode.Type.ASSIGNMENT || target.type == DSLNode.Type.DECLARATION) {
            return equivalentEmbeddedAssignment(target, targetExpression, candidateExpression);
        }

        return sameExpression(targetExpression, candidateExpression);
    }

    private boolean equivalentEmbeddedAssignment(DSLNode target, ExpressionNode targetExpression, ExpressionNode candidateExpression) {
        String targetName = norm(target.name);
        if (targetName.isBlank()) return false;

        ExpressionNode targetValue = target.value;
        if (targetValue == null) {
            targetValue = assignmentRight(targetExpression);
        }
        if (targetValue == null) return false;

        if (candidateExpression instanceof DeclarationNode declaration) {
            return Objects.equals(norm(declaration.name()), targetName)
                    && sameExpression(targetValue, declaration.value());
        }

        if (candidateExpression instanceof BinaryExpressionNode binary && "=".equals(binary.operation())) {
            return Objects.equals(norm(expressionText(binary.left())), targetName)
                    && sameExpression(targetValue, binary.right());
        }

        return false;
    }

    private ExpressionNode assignmentRight(ExpressionNode expression) {
        if (expression instanceof DeclarationNode declaration) return declaration.value();
        if (expression instanceof BinaryExpressionNode binary && "=".equals(binary.operation())) return binary.right();
        return null;
    }

    private List<ExpressionNode> comparableNodeExpressions(DSLNode node) {
        if (node == null) return List.of();

        LinkedHashSet<ExpressionNode> out = new LinkedHashSet<>();
        if (node.semantic instanceof ExpressionNode expression) out.add(expression);
        if (node.value != null) out.add(node.value);
        if (node.receiver != null) out.add(node.receiver);
        out.addAll(safeList(node.arguments));
        out.addAll(semanticExpressionSlots(node).values());
        return new ArrayList<>(out);
    }

    private List<ExpressionNode> expressionChildren(ExpressionNode expression) {
        if (expression == null) return List.of();

        if (expression instanceof BlockNode(List<ExpressionNode> statements)) return safeList(statements);
        if (expression instanceof ClosureNode closure) return closure.body() == null ? List.of() : List.of(closure.body());
        if (expression instanceof DeclarationNode declaration) return declaration.value() == null ? List.of() : List.of(declaration.value());
        if (expression instanceof BinaryExpressionNode binary) return nonNullExpressions(binary.left(), binary.right());
        if (expression instanceof MethodCallNode call) {
            List<ExpressionNode> out = new ArrayList<>();
            if (call.target() != null) out.add(call.target());
            out.addAll(safeList(call.arguments()));
            return out;
        }
        if (expression instanceof PropertyNode(
                ExpressionNode object, ExpressionNode property1
        )) return nonNullExpressions(object, property1);
        if (expression instanceof IndexAccessNode(
                ExpressionNode target, ExpressionNode key
        )) return nonNullExpressions(target, key);
        if (expression instanceof MapNode(
                Map<String, ExpressionNode> entries
        )) return new ArrayList<>(safeMapValues(entries));
        if (expression instanceof ListNode(List<ExpressionNode> values1)) return safeList(values1);
        if (expression instanceof TupleNode(List<ExpressionNode> values)) return safeList(values);
        if (expression instanceof TernaryExpressionNode(
                ExpressionNode condition, ExpressionNode trueExpression, ExpressionNode falseExpression
        )) return nonNullExpressions(condition, trueExpression, falseExpression);
        if (expression instanceof ElvisNode(
                ExpressionNode primary, ExpressionNode fallback
        )) return nonNullExpressions(primary, fallback);
        if (expression instanceof SafeCallNode(
                ExpressionNode receiver, ExpressionNode selector
        )) return nonNullExpressions(receiver, selector);
        if (expression instanceof DelegatedValueNode(
                ExpressionNode delegate
        )) return delegate == null ? List.of() : List.of(delegate);
        if (expression instanceof ThrowNode thrown) return thrown.expression() == null ? List.of() : List.of(thrown.expression());
        if (expression instanceof ConstructorCallNode constructor) return safeList(constructor.arguments());
        if (expression instanceof TaskActionModel action) return safeList(action.arguments());
        if (expression instanceof TaskRelationModel relation) return safeList(relation.targets());
        if (expression instanceof TaskModel task) {
            List<ExpressionNode> out = new ArrayList<>();
            if (task.receiver() != null) out.add(task.receiver());
            out.addAll(safeList(task.arguments()));
            return out;
        }
        if (expression instanceof DependencyModel dependency) {
            List<ExpressionNode> out = new ArrayList<>();
            if (dependency.notation != null) out.add(dependency.notation);
            return out;
        }
        if (expression instanceof RepositoryModel repository) {
            List<ExpressionNode> out = new ArrayList<>();
            if (repository.notation != null) out.add(repository.notation);
            return out;
        }
        if (expression instanceof PluginModel plugin) {
            return plugin.applyTarget == null ? List.of() : List.of(plugin.applyTarget);
        }

        return List.of();
    }

    private Collection<ExpressionNode> safeMapValues(Map<String, ExpressionNode> map) {
        return map == null ? List.of() : map.values();
    }

    private List<ExpressionNode> nonNullExpressions(ExpressionNode... expressions) {
        List<ExpressionNode> out = new ArrayList<>();
        for (ExpressionNode expression : expressions) {
            if (expression != null) out.add(expression);
        }
        return out;
    }
    private boolean isExplainedByRefactoring(DSLNode removed, Set<DSLNode> explained) {
        if (removed == null || explained == null) return false;

        for (DSLNode n : explained) {
            if (n == null) continue;

            if (n == removed) return true;
            if (sameNodeIdentity(n, removed)) return true;
            //if (RuleUtils.isDescendantOf(removed, n)) return true;
        }

        return false;
    }
    private boolean sameNodeIdentity(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;

        return a.type == b.type
                && Objects.equals(normalizePath(a.relativePath), normalizePath(b.relativePath))
                && Objects.equals(norm(a.name), norm(b.name))
                && Objects.equals(a.line, b.line);
    }
    private boolean equivalent(DSLNode a, DSLNode b) {
        return a != null
                && b != null
                && a.type == b.type
                && Objects.equals(norm(a.name), norm(b.name))
                && Objects.equals(a.operator, b.operator)
                && Objects.equals(a.receiver, b.receiver)
                && Objects.equals(a.value, b.value)
                && Objects.equals(a.arguments, b.arguments)
                && Objects.equals(String.valueOf(a.semantic), String.valueOf(b.semantic));
    }
    private boolean insideRemovedCompositeRegion(DSLNode removed, Set<DSLNode> removedComposites) {
        for (DSLNode composite : removedComposites) {
            if (composite != null && composite != removed && RuleUtils.isDescendantOf(removed, composite)) {
                return true;
            }
        }
        return false;
    }
    private static Set<String> deletedFilePaths(DSLNode oldRoot, DSLNode newRoot) {
        Set<String> oldFiles = filePaths(oldRoot);
        Set<String> newFiles = filePaths(newRoot);

        oldFiles.removeAll(newFiles);
        return oldFiles;
    }
    private static Set<String> filePaths(DSLNode root) {
        Set<String> out = new LinkedHashSet<>();

        for (DSLNode node : flatten(root)) {
            if (!isFile(node)) continue;

            String path = normalizePath(node.relativePath);
            if (!path.isBlank()) {
                out.add(path);
            }
        }

        return out;
    }

}
