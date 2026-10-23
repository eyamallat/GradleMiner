package org.example.matcher;

import org.example.model.*;

import java.util.List;
import java.util.Objects;

import static org.example.matcher.NodeFormat.*;
import static org.example.matcher.NodeFormat.normalizedSemantic;
import static org.example.matcher.NodeSimilarity.trim;
import static org.example.refactoring.RuleUtils.*;

public class SemanticMatchConditions {

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isBlank();
    }
    static boolean isBindingLike(DSLNode n) {
        if (n == null) return false;

        if (n.type != DSLNode.Type.ASSIGNMENT &&
                n.type != DSLNode.Type.DECLARATION) {
            return false;
        }

        return n.name != null && !n.name.isBlank();
    }
    private static boolean argumentMatchesValue(ExpressionNode value, List<ExpressionNode> args) {
        if (value == null || args == null || args.isEmpty()) return false;

        for (ExpressionNode arg : args) {
            if (sameExpr(value, arg)) return true;

            String vc = exprContent(value);
            String ac = exprContent(arg);

            if (Objects.equals(vc, ac)) {
                return true;
            }
        }

        return false;
    }
    static boolean sameExpr(ExpressionNode a, ExpressionNode b) {
        a = unwrapCasts(a);
        b = unwrapCasts(b);
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a, b);
    }
    static boolean sameName(DSLNode a, DSLNode b) {
        return Objects.equals(trim(a == null ? null : a.name), trim(b == null ? null : b.name));
    }
    static boolean sameReceiver(DSLNode a, DSLNode b) {
        return sameExpr(a == null ? null : a.receiver, b == null ? null : b.receiver);
    }
    static boolean sameArgCount(DSLNode a, DSLNode b) {
        return safeList(a == null ? null : a.arguments).size()
                == safeList(b == null ? null : b.arguments).size();
    }
    private static boolean sameArgumentListContent(DSLNode a, DSLNode b) {
        List<ExpressionNode> ax = safeList(a == null ? null : a.arguments);
        List<ExpressionNode> bx = safeList(b == null ? null : b.arguments);

        if (ax.size() != bx.size()) return false;

        for (int i = 0; i < ax.size(); i++) {
            ExpressionNode x = unwrapCasts(ax.get(i));
            ExpressionNode y = unwrapCasts(bx.get(i));

            if (x == y) continue;
            if (x == null || y == null) return false;

            if (!sameExpr(x, y)
                    && !Objects.equals(exprContent(x), exprContent(y))) {
                return false;
            }
        }

        return true;
    }
    static boolean sameSemanticContent(DSLNode a, DSLNode b) {
        return Objects.equals(normalizedSemantic(a), normalizedSemantic(b));
    }
    private static boolean onlyTaskRelationFriendlyReplacements(List<Replacement> reps) {
        if (reps == null || reps.isEmpty()) return false;

        for (Replacement r : reps) {
            if (r == null) return false;

            String path = r.path();

            if (r.type() == ReplacementType.SEMANTIC
                    && "node.semantic".equals(path)) {
                continue;
            }

            if (r.type() == ReplacementType.ARGUMENT
                    && path != null
                    && path.startsWith("node.arguments")) {
                continue;
            }

            if (r.type() == ReplacementType.NAME
                    && "node.name".equals(path)) {
                continue;
            }

            return false;
        }

        return true;
    }
    private static boolean sameDependencyPrefixInArgument(DSLNode known, DSLNode unknown) {
        if (!(known.semantic instanceof DependencyModel d)) return false;

        String group = trim(d.group);
        String name = trim(d.name);

        if (group == null || group.isBlank()) return false;
        if (name == null || name.isBlank()) return false;

        if (unknown.arguments == null || unknown.arguments.isEmpty()) return false;

        String wanted = group + ":" + name;

        for (ExpressionNode arg : unknown.arguments) {
            String text = exprContent(arg);
            if (text == null) continue;

            text = text.replace("\"", "")
                    .replace("'", "")
                    .replaceAll("\\s+", "");

            if (text.contains(wanted)) {
                return true;
            }
        }

        return false;
    }
    static boolean sameExpressionListContent(List<ExpressionNode> a, List<ExpressionNode> b) {
        List<ExpressionNode> ax = safeList(a);
        List<ExpressionNode> bx = safeList(b);

        if (ax.size() != bx.size()) return false;

        for (int i = 0; i < ax.size(); i++) {
            ExpressionNode x = unwrapCasts(ax.get(i));
            ExpressionNode y = unwrapCasts(bx.get(i));

            if (x == y) continue;
            if (x == null || y == null) return false;

            if (!sameExpr(x, y)
                    && !Objects.equals(exprContent(x), exprContent(y))) {
                return false;
            }
        }

        return true;
    }
    static boolean sameDependencyIdentity(DependencyModel a, DependencyModel b) {
        if (a == null || b == null) return false;

        // project dependency identity
        if (notBlank(a.projectPath) || notBlank(b.projectPath)) {
            return Objects.equals(trim(a.projectPath), trim(b.projectPath));
        }

        // catalog alias identity
        if (notBlank(a.catalogAlias) || notBlank(b.catalogAlias)) {
            return Objects.equals(trim(a.catalogAlias), trim(b.catalogAlias));
        }
        if (a.notation != null || b.notation != null) {
            return sameExpression(a.notation, b.notation);
        }
        // external dependency identity
        if (notBlank(a.group) || notBlank(b.group) || notBlank(a.name) || notBlank(b.name)) {
            return notBlank(a.group)
                    && notBlank(b.group)
                    && notBlank(a.name)
                    && notBlank(b.name)
                    && Objects.equals(trim(a.group), trim(b.group))
                    && Objects.equals(trim(a.name), trim(b.name));
        }
        return false;
    }
    public static boolean parentCompatible(DSLNode a, DSLNode b) {
        DSLNode pa = a == null ? null : a.parent;
        DSLNode pb = b == null ? null : b.parent;

        if (pa == null && pb == null) return true;
        if (pa == null || pb == null) return false;

        if (pa.type != pb.type) return false;

        if (isBlockAnchor(pa.type)) {
            return Objects.equals(trim(pa.name), trim(pb.name));
        }

        return true;
    }
    static boolean isBlockAnchor(DSLNode.Type type) {
        if (type == null) return false;

        return switch (type) {
            case DEPENDENCIES_BLOCK,
                 REPOSITORIES_BLOCK,
                 PLUGINS_BLOCK,
                 ALLPROJECTS,
                 BLOCK,
                 SUBPROJECTS,
                 BUILDSCRIPT,
                 METHOD_CALL,
                 METHOD_DECL-> true;
            default -> false;
        };
    }
    private static ExpressionNode conditionExpression(DSLNode node) {
        if (node == null) return null;

        if (node.semantic instanceof IfNode ifNode) {
            return ifNode.condition();
        }

        if (node.arguments != null && !node.arguments.isEmpty()) {
            return node.arguments.getFirst();
        }

        return null;
    }
    private static boolean argumentsContentEqual(DSLNode a, DSLNode b) {
        List<ExpressionNode> ax = safeList(a == null ? null : a.arguments);
        List<ExpressionNode> bx = safeList(b == null ? null : b.arguments);

        if (ax.size() != bx.size()) return false;

        for (int i = 0; i < ax.size(); i++) {
            ExpressionNode x = unwrapCasts(ax.get(i));
            ExpressionNode y = unwrapCasts(bx.get(i));

            if (x == y) continue;
            if (x == null || y == null) return false;

            if (!Objects.equals(x.contentKey(), y.contentKey())) {
                return false;
            }
        }

        return true;
    }
    private static boolean onlyNodeNameChanged(List<Replacement> reps) {
        if (reps == null || reps.isEmpty()) return false;

        for (Replacement r : reps) {
            if (r == null) return false;

            if (r.type() == ReplacementType.NAME && "node.name".equals(r.path())) {
                continue;
            }

            return false;
        }

        return true;
    }
    private static boolean hasReplacement(List<Replacement> reps, ReplacementType type, String path) {
        for (Replacement r : reps) {
            if (r == null) continue;
            if (r.type() == type && r.path() != null && r.path().startsWith(path)) {
                return true;
            }
        }
        return false;
    }
    private static boolean hasSemanticReplacement(List<Replacement> reps) {
        for (Replacement r : reps) {
            if (r != null && r.type() == ReplacementType.SEMANTIC && Objects.equals("node.semantic.taskName", r.path())) {
                return true;
            }
        }
        return false;
    }
    private static boolean hasReplacementUnder(List<Replacement> reps) {
        if (reps == null) return false;

        for (Replacement r : reps) {
            if (r == null) continue;
            if (r.type() != ReplacementType.SEMANTIC) continue;
            if (r.path() == null) continue;

            if (r.path().equals("node.semantic") || r.path().startsWith("node.semantic" + ".")) {
                return true;
            }
        }

        return false;
    }
    static boolean validConditionLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (a.type != DSLNode.Type.CONDITION || b.type != DSLNode.Type.CONDITION) return false;

        String an = trim(a.name);
        String bn = trim(b.name);

        if (!Objects.equals(an, bn)) {
            return false;
        }

        if ("if".equals(an)) {
            return validIfConditionLeafMatch(a, b, reps);
        }

        if ("when".equals(an)) {
            return validWhenConditionLeafMatch(a, b, reps);
        }

        return validGenericConditionLeafMatch(a, b, reps);
    }
    private static boolean validGenericConditionLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (a.type != DSLNode.Type.CONDITION || b.type != DSLNode.Type.CONDITION) return false;
        if (!sameName(a, b)) return false;
        if (!parentCompatible(a, b)) return false;

        boolean sameArguments = sameArgumentListContent(a, b);
        boolean sameValue =
                sameExpr(a.value, b.value)
                        || Objects.equals(exprContent(a.value), exprContent(b.value));

        if (sameArguments || sameValue) {
            return onlyLeafFriendlyReplacements(reps);
        }

        return false;
    }
    static boolean validTaskLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        TaskModel ta = (TaskModel) a.semantic;
        TaskModel tb = (TaskModel) b.semantic;

        boolean sameTaskName = Objects.equals(trim(ta.taskName()), trim(tb.taskName()));
        boolean sameBindingName = Objects.equals(trim(ta.bindingName()), trim(tb.bindingName()));
        boolean renamedTask = hasSemanticReplacement(reps);

        if (!sameTaskName && !sameBindingName && !renamedTask) return false;

        return onlyLeafFriendlyReplacements(reps);
    }
    private static boolean onlyLeafFriendlyReplacements(List<Replacement> reps) {
        if (reps == null || reps.isEmpty()) return false;

        for (Replacement r : reps) {
            if (r == null || !r.isLeafFriendly()) return false;
        }
        return true;
    }
    private static ExpressionNode firstArgument(DSLNode node) {
        if (node == null || node.arguments == null || node.arguments.isEmpty()) {
            return null;
        }
        return node.arguments.getFirst();
    }
    private static boolean validWhenConditionLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        ExpressionNode as = firstArgument(a);
        ExpressionNode bs = firstArgument(b);

        boolean bothHaveSubject = as != null && bs != null;

        if (bothHaveSubject) {
            boolean sameSubject =
                    sameExpr(as, bs)
                            || Objects.equals(exprContent(as), exprContent(bs));

            if (!sameSubject) return false;
        }

        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validPluginLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        PluginModel pa = (PluginModel) a.semantic;
        PluginModel pb = (PluginModel) b.semantic;

        boolean samePluginId = Objects.equals(trim(pa.id), trim(pb.id));
        boolean hasTargets = pa.applyTarget != null && pb.applyTarget != null;
        boolean sameTarget = hasTargets && (
                sameExpr(pa.applyTarget, pb.applyTarget) ||
                        Objects.equals(exprContent(pa.applyTarget), exprContent(pb.applyTarget))
        );

        if (!samePluginId && !sameTarget) return false;

        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validGenericLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (!parentCompatible(a, b)) return false;
        boolean sameNodeName = sameName(a, b);
        boolean sameNodeValue = sameExpr(a.value, b.value)
                || Objects.equals(exprContent(a.value), exprContent(b.value));

        if (!sameNodeName && !sameNodeValue) return false;

        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validDependencyLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps){

        if (a == null || b == null) return false;
        if (!parentCompatible(a, b)) return false;

        if (!(a.semantic instanceof DependencyModel da) ||
                !(b.semantic instanceof DependencyModel db)) {
            return false;
        }
        if (!sameDependencyIdentity(da, db)
                && !sameDependencyPrefixInArgument(a, b)
                && !sameDependencyPrefixInArgument(b, a)) {
            return false;
        }


        return onlyLeafFriendlyReplacements(reps)
                || hasReplacementUnder(reps)
                || hasReplacement(reps, ReplacementType.VALUE, "node.value")
                || hasReplacement(reps, ReplacementType.ARGUMENT, "node.arguments")
                || hasReplacement(reps, ReplacementType.NAME, "node.name");

    }
    static boolean validMethodCallLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (a.type != DSLNode.Type.METHOD_CALL || b.type != DSLNode.Type.METHOD_CALL) return false;

        if (!sameArgCount(a, b)) return false;
        if (!parentCompatible(a, b)) return false;

        boolean sameCallName = sameName(a, b);

        if (sameCallName) {
            if (!sameReceiver(a, b)) return false;
            return onlyLeafFriendlyReplacements(reps);
        }

        if (!sameReceiver(a, b)) return false;
        if (!argumentsContentEqual(a, b)) return false;
        return onlyNodeNameChanged(reps);
    }
    private static boolean validIfConditionLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        ExpressionNode ac = conditionExpression(a);
        ExpressionNode bc = conditionExpression(b);

        if (ac == null || bc == null) return false;

        boolean sameCondition =
                sameExpr(ac, bc)
                        || Objects.equals(exprContent(ac), exprContent(bc));

        if (!sameCondition) return false;

        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validBindingLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (!isBindingLike(a) || !isBindingLike(b)) return false;
        if (reps == null || reps.isEmpty()) return false;

        boolean sameBindingName = sameName(a, b);
        boolean sameBindingValue = sameExpr(a.value, b.value) || Objects.equals(exprContent(a.value), exprContent(b.value));
        if (!sameBindingName && !sameBindingValue) return false;
        if (sameBindingName) {
            return !sameBindingValue;
        }
        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validRepositoryLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (!parentCompatible(a, b)) return false;

        // Built-in repos must match by exact name.
        if (!Objects.equals(norm(a.name), norm(b.name))) {
            return false;
        }

        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validBindingMethodCallLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;
        if (reps == null || reps.isEmpty()) return false;

        DSLNode binding = isBindingLike(a) ? a : b;
        DSLNode call = binding == a ? b : a;

        if (!isBindingLike(binding)) return false;
        if (call.type != DSLNode.Type.METHOD_CALL) return false;

        if (!sameName(binding, call)) {
            return false;
        }
        ExpressionNode bindingValue = binding.value;

        boolean valueMatchesCall = argumentMatchesValue(bindingValue, call.arguments);

        boolean semanticLink = sameSemanticContent(binding, call);

        if (!valueMatchesCall) {
            if (!semanticLink) {
                return false;
            }
        }

        return onlyLeafFriendlyReplacements(reps);
    }
    static boolean validTaskRelationLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;

        if (!(a.semantic instanceof TaskRelationModel(String task, String kind, List<ExpressionNode> targets1))
                || !(b.semantic instanceof TaskRelationModel(
                String ownerTask, String relationKind, List<ExpressionNode> targets
        ))) {
            return false;
        }

        if (!parentCompatible(a, b)) return false;

        boolean sameOwner =
                Objects.equals(trim(task), trim(ownerTask));

        boolean sameRelationKind =
                Objects.equals(kind, relationKind);

        boolean sameTargets =
                sameExpressionListContent(targets1, targets);

        if (!sameRelationKind) {
            return false;
        }

        if (sameOwner && sameTargets) {
            return true;
        }

        return onlyTaskRelationFriendlyReplacements(reps);
    }
    static boolean validTaskActionLeafMatch(DSLNode a, DSLNode b, List<Replacement> reps) {
        if (a == null || b == null) return false;

        if (!(a.semantic instanceof TaskActionModel(String task, String kind, List<ExpressionNode> arguments1))
                || !(b.semantic instanceof TaskActionModel(
                String ownerTask, String actionKind, List<ExpressionNode> arguments
        ))) {
            return false;
        }

        if (a.type != DSLNode.Type.TASK_ACTION || b.type != DSLNode.Type.TASK_ACTION) {
            return false;
        }

        if (!parentCompatible(a, b)) return false;

        boolean sameOwner =
                Objects.equals(trim(task), trim(ownerTask));

        boolean sameActionKind =
                Objects.equals(kind, actionKind);

        boolean sameArguments =
                sameExpressionListContent(arguments1, arguments);

        if (!sameActionKind) {
            return false;
        }

        if (sameOwner && sameArguments) {
            return true;
        }

        return onlyTaskActionFriendlyReplacements(reps);
    }
    private static boolean onlyTaskActionFriendlyReplacements(List<Replacement> reps) {
        if (reps == null || reps.isEmpty()) return false;

        for (Replacement r : reps) {
            if (r == null) return false;

            String path = r.path();

            if (r.type() == ReplacementType.SEMANTIC
                    && path != null
                    && path.startsWith("node.semantic")) {
                continue;
            }

            if (r.type() == ReplacementType.ARGUMENT
                    && path != null
                    && path.startsWith("node.arguments")) {
                continue;
            }

            if (r.type() == ReplacementType.ARG_COUNT
                    && path != null
                    && path.startsWith("node.arguments")) {
                continue;
            }

            return false;
        }

        return true;
    }


}
