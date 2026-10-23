package org.example.matcher;

import org.example.model.*;

import java.util.*;

import static org.example.matcher.NodeFormat.*;
import static org.example.matcher.NodeFormat.exprContent;
import static org.example.matcher.NodeFormat.safe;
import static org.example.matcher.NodeFormat.safeMap;
import static org.example.matcher.NodeFormat.safeStringList;
import static org.example.matcher.NodeFormat.unwrapCasts;
import static org.example.matcher.NodeSimilarity.trim;
import static org.example.matcher.SemanticMatchConditions.sameExpr;
import static org.example.refactoring.RuleUtils.*;

public class ReplacementAnalyzer {

    public static List<Replacement> collectNodeReplacements(DSLNode a, DSLNode b) {
        List<Replacement> reps = new ArrayList<>();

        if (a == null || b == null) {
            return reps;
        }

        if (a.type != b.type) {
            reps.add(new Replacement(
                    ReplacementType.TYPE,
                    "node.type",
                    String.valueOf(a.type),
                    String.valueOf(b.type)
            ));
        }

        if (!Objects.equals(trim(a.name), trim(b.name))) {
            reps.add(new Replacement(
                    ReplacementType.NAME,
                    "node.name",
                    safe(a.name),
                    safe(b.name)
            ));
        }

        collectExprReplacement(
                "node.receiver",
                ReplacementType.RECEIVER,
                a.receiver,
                b.receiver,
                reps
        );

        collectExprReplacement(
                "node.value",
                ReplacementType.VALUE,
                a.value,
                b.value,
                reps
        );

        List<ExpressionNode> argsA = safeList(a.arguments);
        List<ExpressionNode> argsB = safeList(b.arguments);

        if (argsA.size() != argsB.size()) {
            reps.add(new Replacement(
                    ReplacementType.ARG_COUNT,
                    "node.arguments",
                    String.valueOf(argsA.size()),
                    String.valueOf(argsB.size())
            ));
        }

        int common = Math.min(argsA.size(), argsB.size());
        for (int i = 0; i < common; i++) {
            collectExprReplacement(
                    "node.arguments[" + i + "]",
                    ReplacementType.ARGUMENT,
                    argsA.get(i),
                    argsB.get(i),
                    reps
            );
        }

        if (!Objects.equals(normalizedSemantic(a), normalizedSemantic(b))) {
            reps.add(new Replacement(
                    ReplacementType.SEMANTIC,
                    "node.semantic",
                    normalizedSemantic(a),
                    normalizedSemantic(b)
            ));
        }

        return reps;
    }
    static void collectExprReplacement(String path, ReplacementType defaultType, ExpressionNode x, ExpressionNode y, List<Replacement> reps) {
        //Decompose the difference structurally
        List<Replacement> nested = collectExprReplacements(x, y, path);

        if (nested.isEmpty()) return;
        //failed to structurally decompose the difference
        if (nested.size() == 1 && nested.getFirst().type() == ReplacementType.WHOLE_EXPRESSION) {
            reps.add(new Replacement(
                    defaultType,
                    path,
                    exprContent(x),
                    exprContent(y)
            ));
            return;
        }

        reps.addAll(nested);
    }
    static List<Replacement> collectCompatibleCrossClassReplacements(ExpressionNode x, ExpressionNode y, String path) {
        List<Replacement> reps = new ArrayList<>();

        if (x == null || y == null) {
            reps.add(new Replacement(
                    ReplacementType.WHOLE_EXPRESSION,
                    path,
                    exprContent(x),
                    exprContent(y)
            ));
            return reps;
        }

        // Variable <-> Property
        if ((x instanceof VariableNode || x instanceof PropertyNode) &&
                (y instanceof VariableNode || y instanceof PropertyNode)) {
            reps.add(new Replacement(
                    ReplacementType.TARGET,
                    path,
                    exprContent(x),
                    exprContent(y)
            ));
            return reps;
        }

        // Literal / GString / concatenated value expression
        if ((x instanceof ConstantNode || x instanceof GStringNode || x instanceof BinaryExpressionNode) &&
                (y instanceof ConstantNode || y instanceof GStringNode || y instanceof BinaryExpressionNode)) {
            reps.add(new Replacement(
                    ReplacementType.VALUE,
                    path,
                    exprContent(x),
                    exprContent(y)
            ));
            return reps;
        }

        reps.add(new Replacement(
                ReplacementType.WHOLE_EXPRESSION,
                path,
                exprContent(x),
                exprContent(y)
        ));
        return reps;
    }
    static List<Replacement> collectExprReplacements(ExpressionNode x, ExpressionNode y, String path){
        x = unwrapCasts(x);
        y = unwrapCasts(y);
        List<Replacement> reps = new ArrayList<>();

        if (sameExpr(x, y)) {
            return reps;
        }

        if (x == null || y == null) {
            reps.add(new Replacement(
                    ReplacementType.WHOLE_EXPRESSION,
                    path,
                    exprContent(x),
                    exprContent(y)
            ));
            return reps;
        }

        if (x.getClass() != y.getClass()) {
            return collectCompatibleCrossClassReplacements(x, y, path);
        }

        if (x instanceof ConstantNode || x instanceof GStringNode) {
            reps.add(new Replacement(
                    ReplacementType.VALUE,
                    path,
                    exprContent(x),
                    exprContent(y)
            ));
            return reps;
        }

        if (x instanceof VariableNode(String name) && y instanceof VariableNode(String name1)) {
            if (!Objects.equals(trim(name), trim(name1))) {
                reps.add(new Replacement(
                        ReplacementType.NAME,
                        path + ".name",
                        safe(name),
                        safe(name1)
                ));
            }
            return reps;
        }

        if (x instanceof PropertyNode(ExpressionNode object, ExpressionNode property) && y instanceof PropertyNode(
                ExpressionNode object1, ExpressionNode property1
        )) {
            collectExprReplacement(
                    path + ".object",
                    ReplacementType.TARGET,
                    object,
                    object1,
                    reps
            );

            collectExprReplacement(
                    path + ".property",
                    ReplacementType.ELEMENT,
                    property,
                    property1,
                    reps
            );
            return reps;
        }
        if (x instanceof ListNode lx && y instanceof ListNode ly) {
            collectUnorderedListReplacements(lx, ly, path, reps);
            return reps;
        }
        if (x instanceof TaskRelationModel tx && y instanceof TaskRelationModel ty) {
            collectTaskRelationReplacements(tx, ty, path, reps);
            return reps;
        }

        if (x instanceof BinaryExpressionNode bx && y instanceof BinaryExpressionNode by) {
            if (!Objects.equals(trim(bx.operation()), trim(by.operation()))) {
                reps.add(new Replacement(
                        ReplacementType.OPERATOR,
                        path + ".operation",
                        safe(bx.operation()),
                        safe(by.operation())
                ));
            }

            collectExprReplacement(path + ".left", ReplacementType.ELEMENT, bx.left(), by.left(), reps);
            collectExprReplacement(path + ".right", ReplacementType.ELEMENT, bx.right(), by.right(), reps);
            return reps;
        }

        if (x instanceof ConstructorCallNode cx && y instanceof ConstructorCallNode cy) {
            if (!Objects.equals(trim(cx.typeName()), trim(cy.typeName()))) {
                reps.add(new Replacement(
                        ReplacementType.TYPE,
                        path + ".typeName",
                        safe(cx.typeName()),
                        safe(cy.typeName())
                ));
            }

            List<ExpressionNode> argsX = safeList(cx.arguments());
            List<ExpressionNode> argsY = safeList(cy.arguments());

            if (argsX.size() != argsY.size()) {
                reps.add(new Replacement(
                        ReplacementType.ARG_COUNT,
                        path + ".arguments",
                        String.valueOf(argsX.size()),
                        String.valueOf(argsY.size())
                ));
            }

            int common = Math.min(argsX.size(), argsY.size());
            for (int i = 0; i < common; i++) {
                collectExprReplacement(
                        path + ".arguments[" + i + "]",
                        ReplacementType.ARGUMENT,
                        argsX.get(i),
                        argsY.get(i),
                        reps
                );
            }
            return reps;
        }

        if (x instanceof ClosureNode(List<String> parameters, BlockNode body) && y instanceof ClosureNode(
                List<String> parameters1, BlockNode body1
        )) {
            List<String> px = safeStringList(parameters);
            List<String> py = safeStringList(parameters1);

            if (px.size() != py.size()) {
                reps.add(new Replacement(
                        ReplacementType.PARAM_COUNT,
                        path + ".parameters",
                        String.valueOf(px.size()),
                        String.valueOf(py.size())
                ));
            }

            int common = Math.min(px.size(), py.size());
            for (int i = 0; i < common; i++) {
                if (!Objects.equals(trim(px.get(i)), trim(py.get(i)))) {
                    reps.add(new Replacement(
                            ReplacementType.PARAMETER,
                            path + ".parameters[" + i + "]",
                            safe(px.get(i)),
                            safe(py.get(i))
                    ));
                }
            }

            collectExprReplacement(
                    path + ".body",
                    ReplacementType.BODY,
                    body,
                    body1,
                    reps
            );
            return reps;
        }

        if (x instanceof BlockNode(List<ExpressionNode> statements) && y instanceof BlockNode(
                List<ExpressionNode> statements1
        )) {
            List<ExpressionNode> stmtsX = safeList(statements);
            List<ExpressionNode> stmtsY = safeList(statements1);

            if (stmtsX.size() != stmtsY.size()) {
                reps.add(new Replacement(
                        ReplacementType.SIZE,
                        path,
                        String.valueOf(stmtsX.size()),
                        String.valueOf(stmtsY.size())
                ));
            }

            int common = Math.min(stmtsX.size(), stmtsY.size());
            for (int i = 0; i < common; i++) {
                collectExprReplacement(
                        path + "[" + i + "]",
                        ReplacementType.ELEMENT,
                        stmtsX.get(i),
                        stmtsY.get(i),
                        reps
                );
            }
            return reps;
        }

        if (x instanceof IfNode(
                ExpressionNode condition1, List<ExpressionNode> branch, List<ExpressionNode> elseBranch1
        ) && y instanceof IfNode(
                ExpressionNode condition, List<ExpressionNode> thenBranch, List<ExpressionNode> elseBranch
        )) {
            collectExprReplacement(path + ".condition", ReplacementType.VALUE, condition1, condition, reps);

            List<ExpressionNode> thenX = safeList(branch);
            List<ExpressionNode> thenY = safeList(thenBranch);
            if (thenX.size() != thenY.size()) {
                reps.add(new Replacement(
                        ReplacementType.SIZE,
                        path + ".thenBranch",
                        String.valueOf(thenX.size()),
                        String.valueOf(thenY.size())
                ));
            }
            int thenCommon = Math.min(thenX.size(), thenY.size());
            for (int i = 0; i < thenCommon; i++) {
                collectExprReplacement(
                        path + ".thenBranch[" + i + "]",
                        ReplacementType.ELEMENT,
                        thenX.get(i),
                        thenY.get(i),
                        reps
                );
            }

            List<ExpressionNode> elseX = safeList(elseBranch1);
            List<ExpressionNode> elseY = safeList(elseBranch);
            if (elseX.size() != elseY.size()) {
                reps.add(new Replacement(
                        ReplacementType.SIZE,
                        path + ".elseBranch",
                        String.valueOf(elseX.size()),
                        String.valueOf(elseY.size())
                ));
            }
            int elseCommon = Math.min(elseX.size(), elseY.size());
            for (int i = 0; i < elseCommon; i++) {
                collectExprReplacement(
                        path + ".elseBranch[" + i + "]",
                        ReplacementType.ELEMENT,
                        elseX.get(i),
                        elseY.get(i),
                        reps
                );
            }
            return reps;
        }

        if (x instanceof TernaryExpressionNode(
                ExpressionNode condition, ExpressionNode trueExpression, ExpressionNode falseExpression
        ) && y instanceof TernaryExpressionNode(
                ExpressionNode condition1, ExpressionNode expression, ExpressionNode falseExpression1
        )) {
            collectExprReplacement(path + ".condition", ReplacementType.VALUE, condition, condition1, reps);
            collectExprReplacement(path + ".trueExpression", ReplacementType.ELEMENT, trueExpression, expression, reps);
            collectExprReplacement(path + ".falseExpression", ReplacementType.ELEMENT, falseExpression, falseExpression1, reps);
            return reps;
        }

        if (x instanceof ThrowNode tx && y instanceof ThrowNode ty) {
            collectExprReplacement(path + ".expression", ReplacementType.VALUE, tx.expression(), ty.expression(), reps);
            return reps;
        }

        if (x instanceof ImportNode ix && y instanceof ImportNode iy) {
            if (ix.isStatic() != iy.isStatic()) {
                reps.add(new Replacement(
                        ReplacementType.SEMANTIC,
                        path + ".isStatic",
                        String.valueOf(ix.isStatic()),
                        String.valueOf(iy.isStatic())
                ));
            }
            if (ix.isStar() != iy.isStar()) {
                reps.add(new Replacement(
                        ReplacementType.SEMANTIC,
                        path + ".isStar",
                        String.valueOf(ix.isStar()),
                        String.valueOf(iy.isStar())
                ));
            }
            if (!Objects.equals(trim(ix.qualifiedName()), trim(iy.qualifiedName()))) {
                reps.add(new Replacement(
                        ReplacementType.NAME,
                        path + ".qualifiedName",
                        safe(ix.qualifiedName()),
                        safe(iy.qualifiedName())
                ));
            }
            if (!Objects.equals(trim(ix.alias()), trim(iy.alias()))) {
                reps.add(new Replacement(
                        ReplacementType.NAME,
                        path + ".alias",
                        safe(ix.alias()),
                        safe(iy.alias())
                ));
            }
            return reps;
        }

        if (x instanceof TupleNode(List<ExpressionNode> values) && y instanceof TupleNode(List<ExpressionNode> values1)) {
            List<ExpressionNode> valsX = safeList(values);
            List<ExpressionNode> valsY = safeList(values1);

            if (valsX.size() != valsY.size()) {
                reps.add(new Replacement(
                        ReplacementType.SIZE,
                        path,
                        String.valueOf(valsX.size()),
                        String.valueOf(valsY.size())
                ));
            }

            int common = Math.min(valsX.size(), valsY.size());
            for (int i = 0; i < common; i++) {
                collectExprReplacement(
                        path + "[" + i + "]",
                        ReplacementType.ELEMENT,
                        valsX.get(i),
                        valsY.get(i),
                        reps
                );
            }
            return reps;
        }

        if (x instanceof MapNode(Map<String, ExpressionNode> entries) && y instanceof MapNode(
                Map<String, ExpressionNode> entries1
        )) {
            Map<String, ExpressionNode> ex = safeMap(entries);
            Map<String, ExpressionNode> ey = safeMap(entries1);

            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(ex.keySet());
            allKeys.addAll(ey.keySet());

            for (String key : allKeys) {
                boolean inX = ex.containsKey(key);
                boolean inY = ey.containsKey(key);

                if (inX && !inY) {
                    reps.add(new Replacement(
                            ReplacementType.ENTRY_REMOVE,
                            path + "[" + key + "]",
                            exprContent(ex.get(key)),
                            ""
                    ));
                } else if (!inX && inY) {
                    reps.add(new Replacement(
                            ReplacementType.ENTRY_ADD,
                            path + "[" + key + "]",
                            "",
                            exprContent(ey.get(key))
                    ));
                } else {
                    collectExprReplacement(
                            path + "[" + key + "]",
                            ReplacementType.ELEMENT,
                            ex.get(key),
                            ey.get(key),
                            reps
                    );
                }
            }
            return reps;
        }

        if (x instanceof MethodCallNode(
                ExpressionNode target1, String name1, List<ExpressionNode> arguments1, List<String> typeArguments1
        ) && y instanceof MethodCallNode(
                ExpressionNode target, String name, List<ExpressionNode> arguments, List<String> typeArguments
        )) {
            collectExprReplacement(path + ".target", ReplacementType.TARGET, target1, target, reps);

            if (!Objects.equals(trim(name1), trim(name))) {
                reps.add(new Replacement(
                        ReplacementType.NAME,
                        path + ".name",
                        safe(name1),
                        safe(name)
                ));
            }
            List<String> typeArgsX = safeStringList(typeArguments1);
            List<String> typeArgsY = safeStringList(typeArguments);

            if (typeArgsX.size() != typeArgsY.size()) {
                reps.add(new Replacement(
                        ReplacementType.ARG_COUNT,
                        path + ".typeArguments",
                        String.valueOf(typeArgsX.size()),
                        String.valueOf(typeArgsY.size())
                ));
            }

            int typeCommon = Math.min(typeArgsX.size(), typeArgsY.size());
            for (int i = 0; i < typeCommon; i++) {
                if (!Objects.equals(trim(typeArgsX.get(i)), trim(typeArgsY.get(i)))) {
                    reps.add(new Replacement(
                            ReplacementType.TYPE,
                            path + ".typeArguments[" + i + "]",
                            safe(typeArgsX.get(i)),
                            safe(typeArgsY.get(i))
                    ));
                }
            }

            List<ExpressionNode> argsX = safeList(arguments1);
            List<ExpressionNode> argsY = safeList(arguments);

            if (argsX.size() != argsY.size()) {
                reps.add(new Replacement(
                        ReplacementType.ARG_COUNT,
                        path + ".arguments",
                        String.valueOf(argsX.size()),
                        String.valueOf(argsY.size())
                ));
            }

            int common = Math.min(argsX.size(), argsY.size());
            for (int i = 0; i < common; i++) {
                collectExprReplacement(
                        path + ".arguments[" + i + "]",
                        ReplacementType.ARGUMENT,
                        argsX.get(i),
                        argsY.get(i),
                        reps
                );
            }
            return reps;
        }

        if (x instanceof DeclarationNode dx && y instanceof DeclarationNode dy) {

            if (!Objects.equals(trim(dx.name()), trim(dy.name()))) {
                reps.add(new Replacement(
                        ReplacementType.NAME,
                        path + ".name",
                        safe(dx.name()),
                        safe(dy.name())
                ));
            }

            collectExprReplacement(
                    path + ".value",
                    ReplacementType.VALUE,
                    dx.value(),
                    dy.value(),
                    reps
            );

            return reps;
        }
        if (x instanceof TaskActionModel tx && y instanceof TaskActionModel ty) {
            collectTaskActionReplacements(tx, ty, path, reps);
            return reps;
        }
        if (x instanceof DependencyModel dx && y instanceof DependencyModel dy) {
            collectDependencyReplacements(dx, dy, path, reps);
            return reps;
        }

        if (x instanceof PluginModel px && y instanceof PluginModel py) {
            collectPluginReplacements(px, py, path, reps);
            return reps;
        }

        if (x instanceof TaskModel tx && y instanceof TaskModel ty) {
            collectTaskReplacements(tx, ty, path, reps);
            return reps;
        }

        reps.add(new Replacement(
                ReplacementType.WHOLE_EXPRESSION,
                path,
                exprContent(x),
                exprContent(y)
        ));
        return reps;
    }
    private static void collectTaskActionReplacements(TaskActionModel a, TaskActionModel b, String path, List<Replacement> reps) {
        if (!Objects.equals(trim(a.ownerTask()), trim(b.ownerTask()))) {
            reps.add(new Replacement(
                    ReplacementType.SEMANTIC,
                    path + ".ownerTask",
                    safe(a.ownerTask()),
                    safe(b.ownerTask())
            ));
        }

        if (!Objects.equals(a.actionKind(), b.actionKind())) {
            reps.add(new Replacement(
                    ReplacementType.SEMANTIC,
                    path + ".actionKind",
                    safe(a.actionKind()),
                    safe(b.actionKind())
            ));
        }

        List<ExpressionNode> ax = safeList(a.arguments());
        List<ExpressionNode> bx = safeList(b.arguments());

        if (ax.size() != bx.size()) {
            reps.add(new Replacement(
                    ReplacementType.ARG_COUNT,
                    path + ".arguments",
                    String.valueOf(ax.size()),
                    String.valueOf(bx.size())
            ));
        }

        int common = Math.min(ax.size(), bx.size());
        for (int i = 0; i < common; i++) {
            collectExprReplacement(
                    path + ".arguments[" + i + "]",
                    ReplacementType.ARGUMENT,
                    ax.get(i),
                    bx.get(i),
                    reps
            );
        }
    }
    private static void collectUnorderedListReplacements(ListNode x, ListNode y, String path, List<Replacement> reps) {
        List<ExpressionNode> left = new ArrayList<>(safeList(x.values()));
        List<ExpressionNode> right = new ArrayList<>(safeList(y.values()));

        if (left.size() != right.size()) {
            reps.add(new Replacement(
                    ReplacementType.SIZE,
                    path,
                    String.valueOf(left.size()),
                    String.valueOf(right.size())
            ));
        }

        for (ExpressionNode oldValue : left) {
            int match = indexOfSameExpression(right, oldValue);

            if (match >= 0) {
                right.remove(match);
                continue;
            }

            reps.add(new Replacement(
                    ReplacementType.ELEMENT,
                    path + ".removed",
                    exprContent(oldValue),
                    ""
            ));
        }

        for (ExpressionNode newValue : right) {
            reps.add(new Replacement(
                    ReplacementType.ELEMENT,
                    path + ".added",
                    "",
                    exprContent(newValue)
            ));
        }
    }
    private static void collectDependencyReplacements(DependencyModel x, DependencyModel y, String path, List<Replacement> reps) {
        if (!Objects.equals(trim(x.configuration), trim(y.configuration))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".configuration", safe(x.configuration), safe(y.configuration)));
        }

        if (!Objects.equals(trim(x.group), trim(y.group))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".group", safe(x.group), safe(y.group)));
        }

        if (!Objects.equals(trim(x.name), trim(y.name))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".name", safe(x.name), safe(y.name)));
        }

        if (!Objects.equals(trim(x.version), trim(y.version))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".version", safe(x.version), safe(y.version)));
        }

        if (!Objects.equals(trim(x.classifier), trim(y.classifier))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".classifier", safe(x.classifier), safe(y.classifier)));
        }

        if (!Objects.equals(trim(x.extension), trim(y.extension))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".extension", safe(x.extension), safe(y.extension)));
        }

        if (!Objects.equals(trim(x.projectPath), trim(y.projectPath))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".projectPath", safe(x.projectPath), safe(y.projectPath)));
        }

        if (!Objects.equals(trim(x.catalogAlias), trim(y.catalogAlias))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".catalogAlias", safe(x.catalogAlias), safe(y.catalogAlias)));
        }

        if (!sameExpression(x.notation, y.notation)) {
            collectExprReplacement(path + ".notation", ReplacementType.SEMANTIC, x.notation, y.notation, reps);
        }

        if (x.isPlatform != y.isPlatform) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".isPlatform", String.valueOf(x.isPlatform), String.valueOf(y.isPlatform)));
        }

        if (x.isProject != y.isProject) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".isProject", String.valueOf(x.isProject), String.valueOf(y.isProject)));
        }

        if (x.isEnforcedPlatform != y.isEnforcedPlatform) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".isEnforcedPlatform", String.valueOf(x.isEnforcedPlatform), String.valueOf(y.isEnforcedPlatform)));
        }

        if (!Objects.equals(x.kind, y.kind)) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".kind", safe(x.kind), safe(y.kind)));
        }
    }
    private static void collectTaskRelationReplacements(TaskRelationModel a, TaskRelationModel b, String path, List<Replacement> reps) {
        if (!Objects.equals(trim(a.ownerTask()), trim(b.ownerTask()))) {
            reps.add(new Replacement(
                    ReplacementType.SEMANTIC,
                    path + ".ownerTask",
                    safe(a.ownerTask()),
                    safe(b.ownerTask())
            ));
        }

        if (!Objects.equals(a.relationKind(),b.relationKind())) {
            reps.add(new Replacement(
                    ReplacementType.SEMANTIC,
                    path + ".relationKind",
                    safe(a.relationKind()),
                    safe(b.relationKind())
            ));
        }

        List<ExpressionNode> ax = safeList(a.targets());
        List<ExpressionNode> bx = safeList(b.targets());

        if (ax.size() != bx.size()) {
            reps.add(new Replacement(
                    ReplacementType.ARG_COUNT,
                    path + ".targets",
                    String.valueOf(ax.size()),
                    String.valueOf(bx.size())
            ));
        }

        int common = Math.min(ax.size(), bx.size());
        for (int i = 0; i < common; i++) {
            collectExprReplacement(
                    path + ".targets[" + i + "]",
                    ReplacementType.ARGUMENT,
                    ax.get(i),
                    bx.get(i),
                    reps
            );
        }
    }
    private static void collectPluginReplacements(PluginModel x, PluginModel y, String path, List<Replacement> reps) {
        if (!Objects.equals(trim(x.id), trim(y.id))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".id", safe(x.id), safe(y.id)));
        }
        if (!Objects.equals(trim(x.version), trim(y.version))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".version", safe(x.version), safe(y.version)));
        }
        if (x.appliedViaApply != y.appliedViaApply) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".appliedViaApply", String.valueOf(x.appliedViaApply), String.valueOf(y.appliedViaApply)));
        }
        collectExprReplacement(path + ".applyTarget", ReplacementType.TARGET, x.applyTarget, y.applyTarget, reps);
    }
    private static void collectTaskReplacements(TaskModel x, TaskModel y, String path, List<Replacement> reps) {
        if (!Objects.equals(trim(x.taskName()), trim(y.taskName()))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".taskName", safe(x.taskName()), safe(y.taskName())));
        }
        if (!Objects.equals(trim(x.syntaxStyle()), trim(y.syntaxStyle()))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".style", safe(x.syntaxStyle()), safe(y.syntaxStyle())));
        }
        if (!Objects.equals(trim(x.bindingName()), trim(y.bindingName()))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".bindingName", safe(x.bindingName()), safe(y.bindingName())));
        }

        if (!Objects.equals(trim(x.taskType()), trim(y.taskType()))) {
            reps.add(new Replacement(ReplacementType.SEMANTIC, path + ".taskType", safe(x.taskType()), safe(y.taskType())));
        }

        collectExprReplacement(path + ".receiver", ReplacementType.RECEIVER, x.receiver(), y.receiver(), reps);

        List<ExpressionNode> argsX = safeList(x.arguments());
        List<ExpressionNode> argsY = safeList(y.arguments());

        if (argsX.size() != argsY.size()) {
            reps.add(new Replacement(
                    ReplacementType.ARG_COUNT,
                    path + ".arguments",
                    String.valueOf(argsX.size()),
                    String.valueOf(argsY.size())
            ));
        }

        int common = Math.min(argsX.size(), argsY.size());
        for (int i = 0; i < common; i++) {
            collectExprReplacement(
                    path + ".arguments[" + i + "]",
                    ReplacementType.ARGUMENT,
                    argsX.get(i),
                    argsY.get(i),
                    reps
            );
        }
    }
    private static int indexOfSameExpression(
            List<ExpressionNode> values,
            ExpressionNode target
    ) {
        for (int i = 0; i < values.size(); i++) {
            ExpressionNode value = values.get(i);

            if (sameExpr(target, value)
                    || Objects.equals(exprContent(target), exprContent(value))) {
                return i;
            }
        }

        return -1;
    }
}
