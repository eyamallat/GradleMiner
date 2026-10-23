package org.example.visitor;

import org.example.model.*;
import org.example.model.ImportNode;
import org.example.model.PropertyNode;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.*;

import java.util.*;

import static org.example.visitor.visitorUtils.*;

public class KotlinVisitor extends KtTreeVisitorVoid {

    private final String relativePath;

    public KotlinVisitor() {
        this.relativePath = null;
    }

    public KotlinVisitor(DSLNode fileNode, String relativePath) {
        this.relativePath = relativePath;
        if (fileNode != null) {
            parentStack.push(fileNode);
        }
    }

    private final Deque<DSLNode> stack = new ArrayDeque<>();
    private final Deque<DSLNode> parentStack = new ArrayDeque<>();
    private final List<DSLNode> roots = new ArrayList<>();


    public List<DSLNode> getRoots() {
        return roots;
    }
    private void addNode(DSLNode node) {
        if (relativePath != null) {
            node.relativePath = relativePath;
        }

        if (!stack.isEmpty()) {
            DSLNode parent = stack.peek();
            parent.children.add(node);
            node.parent = parent;
        } else if (!parentStack.isEmpty()) {
            DSLNode parent = parentStack.peek();
            parent.children.add(node);
            node.parent = parent;
        } else {
            roots.add(node);
            node.parent = null;
        }
    }
    private void pushParent(DSLNode node) {
        parentStack.push(node);
    }
    private void popParent() {
        parentStack.pop();
    }
    private void attachChild(DSLNode parent, DSLNode child) {
        if (parent == null || child == null) return;
        parent.children.add(child);
        child.parent = parent;
    }



    @Override
    public void visitKtFile(KtFile file) {
        super.visitKtFile(file);
    }
    @Override
    public void visitWhenExpression(KtWhenExpression expression) {
        DSLNode node = new DSLNode("when");
        node.type = DSLNode.Type.CONDITION;
        node.line = lineOf(expression);
        node.column = columnOf(expression);

        ExpressionNode subject = convertExpression(expression.getSubjectExpression());
        if (subject != null) {
            node.arguments.add(subject);
        }

        addNode(node);
        stack.push(node);
        try {
            for (KtWhenEntry entry : expression.getEntries()) {
                DSLNode branch = new DSLNode("whenBranch");
                branch.type = DSLNode.Type.WHEN_BRANCH;
                branch.line = lineOf(entry);
                branch.column = columnOf(entry);

                if (entry.isElse()) {
                    branch.arguments.add(new VariableNode("else"));
                } else {
                    for (KtWhenCondition condition : entry.getConditions()) {
                        ExpressionNode conditionExpr = convertWhenCondition(condition, subject);                        if (conditionExpr != null) {
                            branch.arguments.add(conditionExpr);
                        }
                    }
                }

                KtExpression body = entry.getExpression();
                if (body != null) {
                    branch.value = convertExpression(body);
                }

                attachChild(node, branch);
            }
        } finally {
            stack.pop();
        }
    }
    @Override
    public void visitClass(KtClass klass) {
        if (klass.isLocal()) {
            return;
        }

        String name = klass.getName() != null ? klass.getName() : "anonymous";
        DSLNode classNode = new DSLNode(name);
        classNode.type = DSLNode.Type.CLASS_DECL;
        classNode.line = lineOf(klass);
        classNode.column = columnOf(klass);

        addNode(classNode);
        pushParent(classNode);
        try {
            for (KtSecondaryConstructor ctor : klass.getSecondaryConstructors()) {
                visitSecondaryConstructor(ctor);
            }

            for (KtNamedFunction fn : klass.getDeclarations().stream()
                    .filter(d -> d instanceof KtNamedFunction)
                    .map(d -> (KtNamedFunction) d)
                    .toList()) {
                visitNamedFunction(fn);
            }
        } finally {
            popParent();
        }
    }
    @Override
    public void visitNamedFunction(KtNamedFunction function) {
        String name = function.getName();
        if (name == null) {
            return;
        }

        DSLNode methodNode = new DSLNode(name);
        methodNode.type = DSLNode.Type.METHOD_DECL;
        methodNode.line = lineOf(function);
        methodNode.column = columnOf(function);
        for (KtParameter p : function.getValueParameters()) {
            String paramName = p.getName();
            String paramType = p.getTypeReference() == null ? null : p.getTypeReference().getText();

            if (paramName == null) continue;

            String text = paramType == null || paramType.isBlank()
                    ? paramName
                    : paramName + ": " + paramType;

            methodNode.arguments.add(new VariableNode(text));
        }

        addNode(methodNode);

        KtBlockExpression body = function.getBodyBlockExpression();
        if (body != null) {
            stack.push(methodNode);
            try {
                body.acceptChildren(this);
            } finally {
                stack.pop();
            }
        } else if (function.getBodyExpression() != null) {
            pushParent(methodNode);
            try {
                function.getBodyExpression().accept(this);
            } finally {
                popParent();
            }
        }
    }
    @Override
    public void visitSecondaryConstructor(KtSecondaryConstructor constructor) {
        KtClassOrObject owner = constructor.getContainingClassOrObject();
        String ownerName = owner != null && owner.getName() != null ? owner.getName() : "anonymous";

        List<String> paramNames = constructor.getValueParameters().stream()
                .map(KtParameter::getName)
                .filter(Objects::nonNull)
                .toList();

        DSLNode ctorNode = new DSLNode(ownerName);
        ctorNode.type = DSLNode.Type.CONSTRUCTOR_DECL;
        ctorNode.line = lineOf(constructor);
        ctorNode.column = columnOf(constructor);
        ctorNode.semantic = new ConstructorDeclNode(ownerName, paramNames);

        addNode(ctorNode);

        KtBlockExpression body = constructor.getBodyBlockExpression();
        if (body != null) {
            pushParent(ctorNode);
            try {
                body.acceptChildren(this);
            } finally {
                popParent();
            }
        }
    }
    @Override
    public void visitThrowExpression(KtThrowExpression expression) {
        DSLNode node = new DSLNode("throw");
        node.type = DSLNode.Type.THROW;
        node.line = lineOf(expression);
        node.column = columnOf(expression);

        ExpressionNode thrown = convertExpression(expression.getThrownExpression());
        if (thrown != null) {
            node.value = thrown;
            node.semantic = new ThrowNode(thrown);
        }

        addNode(node);
    }
    @Override
    public void visitIfExpression(KtIfExpression expression) {
        DSLNode ifNode = new DSLNode("if");
        ifNode.type = DSLNode.Type.CONDITION;
        ifNode.line = lineOf(expression);
        ifNode.arguments.add(convertExpression(expression.getCondition()));

        addNode(ifNode);
        stack.push(ifNode);
        try {
            KtExpression thenExpr = expression.getThen();
            if (thenExpr != null) {
                DSLNode thenNode = new DSLNode("then");
                thenNode.type = DSLNode.Type.BLOCK;
                thenNode.line = lineOf(thenExpr);
                thenNode.column = columnOf(thenExpr);
                attachChild(ifNode, thenNode);


                stack.push(thenNode);
                try {
                    thenExpr.accept(this);
                } finally {
                    stack.pop();
                }
            }

            KtExpression elseExpr = expression.getElse();
            if (elseExpr != null) {
                DSLNode elseNode = new DSLNode("else");
                elseNode.type = DSLNode.Type.BLOCK;
                elseNode.line = lineOf(elseExpr);
                elseNode.column = columnOf(elseExpr);
                attachChild(ifNode, elseNode);

                stack.push(elseNode);
                try {
                    elseExpr.accept(this);
                } finally {
                    stack.pop();
                }
            }
        } finally {
            stack.pop();
        }
    }
    @Override
    public void visitForExpression(KtForExpression expression) {
        DSLNode node = new DSLNode("for");
        node.type = DSLNode.Type.BLOCK;
        node.line = lineOf(expression);
        node.column = columnOf(expression);

        KtParameter iterator = expression.getLoopParameter();
        if (iterator != null) {
            String iteratorName = iterator.getName();
            node.arguments.add(new VariableNode(
                    iteratorName == null || iteratorName.isBlank()
                            ? iterator.getText()
                            : iteratorName
            ));
        }

        KtExpression range = expression.getLoopRange();
        if (range != null) {
            node.value = convertExpression(range);
        }

        addNode(node);
        stack.push(node);
        try {
            KtExpression body = expression.getBody();
            if (body != null) {
                body.accept(this);
            }
        } finally {
            stack.pop();
        }
    }
    @Override
    public void visitBlockExpression(KtBlockExpression expression) {
        for (KtExpression stmt : expression.getStatements()) {
            stmt.accept(this);
        }
    }
    @Override
    public void visitCallExpression(KtCallExpression call) {
        // Avoid double-visiting when the call is the selector in a qualified expression.
        if (call.getParent() instanceof KtDotQualifiedExpression parent
                && parent.getSelectorExpression() == call) {
            return;
        }

        handleCallExpression(call, null);
    }
    @Override
    public void visitDotQualifiedExpression(KtDotQualifiedExpression expression) {
        KtExpression selector = expression.getSelectorExpression();
        if (selector instanceof KtCallExpression call) {
            handleCallExpression(call, expression.getReceiverExpression());
            return;
        }

        super.visitDotQualifiedExpression(expression);
    }
    @Override
    public void visitSafeQualifiedExpression(KtSafeQualifiedExpression expression) {
        KtExpression selector = expression.getSelectorExpression();

        if (selector instanceof KtCallExpression call) {
            handleCallExpression(call, expression.getReceiverExpression(), true);
            return;
        }

        super.visitSafeQualifiedExpression(expression);
    }
    @Override
    public void visitImportDirective(KtImportDirective imp) {
        FqName fqName = imp.getImportedFqName();
        String qualifiedName = fqName != null ? fqName.asString() : imp.getText();

        ImportNode node = new ImportNode(
                false,
                imp.isAllUnder(),
                qualifiedName,
                imp.getAliasName()
        );

        DSLNode dslNode = new DSLNode("import");
        dslNode.type = DSLNode.Type.IMPORT;
        dslNode.arguments.add(node);
        dslNode.line = lineOf(imp);

        addNode(dslNode);
    }
    @Override
    public void visitProperty(KtProperty property) {
        DSLNode node = new DSLNode(property.getName() != null ? property.getName() : "property");
        node.line = lineOf(property);
        node.column = columnOf(property);
        node.type = DSLNode.Type.DECLARATION;

        if (property.getTypeReference() != null) {
            node.declaredType = property.getTypeReference().getText();
        }

        KtExpression initializer = property.getInitializer();
        KtPropertyDelegate delegate = property.getDelegate();
        KtExpression getterBody = property.getGetter() == null
                ? null
                : property.getGetter().getBodyExpression();

        if (delegate != null && delegate.getExpression() != null) {
            KtExpression expr = delegate.getExpression();
            if (isTaskDelegate(expr)) {
                DSLNode taskNode = createTaskNodeFromDelegate(property, expr);
                addNode(taskNode);

                KtLambdaExpression lambda = extractLambdaFromDelegate(expr);
                if (lambda != null) {
                    stack.push(taskNode);
                    visitLambdaBody(taskNode, lambda);
                    stack.pop();
                }

                return;
            }
        }

        if (initializer != null) {
            node.value = convertExpression(initializer);
        } else if (getterBody != null) {
            node.value = convertExpression(getterBody);
        } else if (delegate != null && delegate.getExpression() != null) {
            node.value = new DelegatedValueNode(
                    convertDelegateWithoutLambda(delegate.getExpression())
            );
        }
        addNode(node);

        if (delegate != null) {
            KtLambdaExpression lambda = extractLambdaFromDelegate(delegate.getExpression());
            if (lambda != null) {
                stack.push(node);
                try {
                    visitLambdaBody(node, lambda);
                } finally {
                    stack.pop();
                }
            }
            return;
        }

        if (initializer != null || getterBody != null) {
            return;
        }


        super.visitProperty(property);

    }
    @Override
    public void visitSimpleNameExpression(KtSimpleNameExpression expression) {
        if (insidePluginApplicationBlock()) {
            String name = expression.getReferencedName();

            if (isPluginAccessorName(name)) {
                DSLNode node = new DSLNode(name);
                node.type = DSLNode.Type.PLUGIN_DECL;
                node.line = lineOf(expression);
                node.column = columnOf(expression);

                PluginModel plugin = new PluginModel();
                plugin.id = name;
                node.semantic = plugin;

                addNode(node);
                return;
            }
        }

        super.visitSimpleNameExpression(expression);
    }
    @Override
    public void visitBinaryExpression(KtBinaryExpression expression) {

        if (insidePluginApplicationBlock() && isPluginInfixExpression(expression)) {

            // Avoid creating duplicate plugin nodes from nested binary expressions.
            if (!isNestedInsidePluginInfixExpression(expression)) {
                PluginModel plugin = extractPluginFromKotlinPluginExpression(expression);

                if (plugin != null && plugin.id != null) {
                    DSLNode node = new DSLNode("id");
                    node.type = DSLNode.Type.PLUGIN_DECL;
                    node.line = lineOf(expression);
                    node.column = columnOf(expression);
                    node.semantic = plugin;

                    addNode(node);
                    return;
                }
            }
            return;
        }

        if (isAssignmentOperator(expression)) {
            KtExpression leftExpr = expression.getLeft();
            KtExpression rightExpr = expression.getRight();

            DSLNode node = new DSLNode(leftExpr != null ? leftExpr.getText() : "assign");
            node.line = lineOf(expression);
            node.column = columnOf(expression);
            node.type = DSLNode.Type.ASSIGNMENT;
            node.operator = expression.getOperationReference().getText();


            if (rightExpr != null) {
                node.value = convertExpression(rightExpr);
            }

            addNode(node);

            if (rightExpr instanceof KtLambdaExpression lambda) {
                visitLambdaBody(node, lambda);
            }

            return;
        }

        super.visitBinaryExpression(expression);
    }


    private String kotlinStringValue(KtStringTemplateExpression se) {
        if (se == null) return null;

        StringBuilder sb = new StringBuilder();
        boolean hasInterpolation = false;

        for (KtStringTemplateEntry entry : se.getEntries()) {
            if (entry instanceof KtLiteralStringTemplateEntry literal) {
                sb.append(literal.getText());
            } else if (entry instanceof KtEscapeStringTemplateEntry escape) {
                sb.append(escape.getUnescapedValue());
            } else {
                hasInterpolation = true;
            }
        }

        if (!hasInterpolation) {
            return sb.toString();
        }

        return stripOuterQuotes(se.getText());
    }
    private void extractArguments(KtCallExpression call, DSLNode node) {
        List<KtValueArgument> args = call.getValueArguments();

        if (!args.isEmpty() && args.stream().allMatch(a -> a.getArgumentName() != null)) {
            Map<String, ExpressionNode> map = new LinkedHashMap<>();
            for (KtValueArgument arg : args) {
                KtExpression expr = arg.getArgumentExpression();
                if (expr instanceof KtLambdaExpression) continue;

                String key = null;
                if (arg.getArgumentName() != null && arg.getArgumentName().getReferenceExpression() != null) {
                    key = arg.getArgumentName().getReferenceExpression().getText();
                }
                if (key != null) {
                    ExpressionNode converted = convertExpression(expr);
                    if (converted != null) {
                        map.put(key, converted);
                    }
                }
            }
            if (!map.isEmpty()) {
                node.arguments.add(new MapNode(map));
            }
        } else {
            for (KtValueArgument arg : args) {
                KtExpression expr = arg.getArgumentExpression();
                if (expr instanceof KtLambdaExpression) continue;

                ExpressionNode converted = convertExpression(expr);
                if (converted != null) {
                    node.arguments.add(converted);
                }
            }
        }
    }
    private KtLambdaExpression extractLambda(KtCallExpression call) {
        List<KtLambdaArgument> lambdaArgs = call.getLambdaArguments();
        if (!lambdaArgs.isEmpty()) {
            return lambdaArgs.getFirst().getLambdaExpression();
        }

        for (KtValueArgument arg : call.getValueArguments()) {
            KtExpression expr = arg.getArgumentExpression();
            if (expr instanceof KtLambdaExpression lambda) {
                return lambda;
            }
        }

        return null;
    }
    private String firstTypeArgumentName(KtCallExpression call) {
        if (call == null || call.getTypeArguments().isEmpty()) return null;

        KtTypeProjection projection = call.getTypeArguments().getFirst();
        if (projection == null || projection.getTypeReference() == null) return null;

        String text = projection.getTypeReference().getText();
        if (text == null || text.isBlank()) return null;

        int dot = text.lastIndexOf('.');
        return dot >= 0 ? text.substring(dot + 1) : text;
    }
    private List<String> typeArgumentTexts(KtCallExpression call) {
        if (call == null || call.getTypeArguments().isEmpty()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();

        for (KtTypeProjection projection : call.getTypeArguments()) {
            if (projection == null || projection.getTypeReference() == null) {
                continue;
            }

            String text = projection.getTypeReference().getText();
            if (text != null && !text.isBlank()) {
                out.add(text.trim());
            }
        }

        return out;
    }
    private DSLNode.Type classify(String name, KtCallExpression call, KtExpression receiver) {
        boolean hasLambda = extractLambda(call) != null;

        if (isTaskRelationCall(name) && (insideTaskLike() || receiver != null)) {
            return DSLNode.Type.TASK_RELATION;
        }
        if (isTaskActionCall(name) && insideTaskLike()) {
            return DSLNode.Type.TASK_ACTION;
        }


        if (isTaskDeclarationCall(call, receiver)
                || isTaskRegistrationCall(call, receiver)
                || isTaskCreateCall(call, receiver)) {
            return DSLNode.Type.TASK_DECLARATION;
        }

        if (isTaskConfigurationCall(call, receiver)) {
            return DSLNode.Type.TASK_CONFIGURATION;
        }
        if (insidePluginApplicationBlock()) {
            return DSLNode.Type.PLUGIN_DECL;
        }


        return switch (name) {
            case "dependencies" -> DSLNode.Type.DEPENDENCIES_BLOCK;
            case "repositories" -> DSLNode.Type.REPOSITORIES_BLOCK;
            case "plugins" -> DSLNode.Type.PLUGINS_BLOCK;
            case "task" -> DSLNode.Type.TASK_DECLARATION;
            case "apply" -> classifyGradleApply(call, receiver);
            case "allprojects" -> DSLNode.Type.ALLPROJECTS;
            case "subprojects" -> DSLNode.Type.SUBPROJECTS;
            case "buildscript" -> DSLNode.Type.BUILDSCRIPT;
            default -> hasLambda ? DSLNode.Type.BLOCK : DSLNode.Type.METHOD_CALL;
        };
    }
    private ExpressionNode convertExpression(KtExpression exp) {
        if (exp == null) {
            return null;
        }

        if (exp instanceof KtConstantExpression ce) {
            String text = ce.getText();
            if (text == null) return new ConstantNode(null);

            if ("true".equals(text) || "false".equals(text)) {
                return new ConstantNode(Boolean.parseBoolean(text));
            }
            if ("null".equals(text)) {
                return new ConstantNode(null);
            }

            try {
                if (text.endsWith("L") || text.endsWith("l")) {
                    return new ConstantNode(Long.parseLong(text.substring(0, text.length() - 1)));
                }
                if (text.contains(".")) {
                    return new ConstantNode(Double.parseDouble(text));
                }
                return new ConstantNode(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return new ConstantNode(text);
            }
        }

        if (exp instanceof KtStringTemplateExpression se) {
            String value = kotlinStringValue(se);

            boolean hasInterpolation = false;
            for (KtStringTemplateEntry entry : se.getEntries()) {
                if (!(entry instanceof KtLiteralStringTemplateEntry)
                        && !(entry instanceof KtEscapeStringTemplateEntry)) {
                    hasInterpolation = true;
                    break;
                }
            }

            return hasInterpolation
                    ? new GStringNode(value)
                    : new ConstantNode(value);
        }

        if (exp instanceof KtNameReferenceExpression ve) {
            return new VariableNode(ve.getReferencedName());
        }

        if (exp instanceof KtCollectionLiteralExpression cle) {
            List<ExpressionNode> list = new ArrayList<>();
            for (KtExpression e : cle.getInnerExpressions()) {
                list.add(convertExpression(e));
            }
            return new ListNode(list);
        }

        if (exp instanceof KtCallExpression call) {
            ExpressionNode target = null;
            if (call.getParent() instanceof KtDotQualifiedExpression dq && dq.getSelectorExpression() == call) {
                target = convertExpression(dq.getReceiverExpression());
            }

            return new MethodCallNode(
                    target,
                    call.getCalleeExpression() != null ? call.getCalleeExpression().getText() : "unknown",
                    extractArgumentsList(call),
                    typeArgumentTexts(call)
            );

        }
        if (exp instanceof KtArrayAccessExpression ae) {
            ExpressionNode target = convertExpression(ae.getArrayExpression());

            List<KtExpression> indices = ae.getIndexExpressions();
            ExpressionNode key = indices.isEmpty() ? null : convertExpression(indices.getFirst());

            return new IndexAccessNode(target, key);
        }
        if (exp instanceof KtBinaryExpression be
                && "?:".equals(be.getOperationReference().getText())) {
            return new ElvisNode(
                    convertExpression(be.getLeft()),
                    convertExpression(be.getRight())
            );
        }
        if (exp instanceof KtBinaryExpression be) {
            return new BinaryExpressionNode(
                    convertExpression(be.getLeft()),
                    be.getOperationReference().getText(),
                    convertExpression(be.getRight())
            );
        }

        if (exp instanceof KtDotQualifiedExpression pe) {
            KtExpression selector = pe.getSelectorExpression();
            if (selector instanceof KtCallExpression call) {
                String rawName = call.getCalleeExpression() != null
                        ? call.getCalleeExpression().getText()
                        : "unknown";
                return new MethodCallNode(
                        convertExpression(pe.getReceiverExpression()),
                        normalizeCallableName(rawName),
                        extractArgumentsList(call),
                        typeArgumentTexts(call)
                );

            }
            return new PropertyNode(
                    convertExpression(pe.getReceiverExpression()),
                    selector instanceof KtExpression se ? convertExpression(se) : new VariableNode(selector != null ? selector.getText() : "unknown")
            );
        }

        if (exp instanceof KtLambdaExpression lambda) {
            List<String> params = new ArrayList<>();
            KtFunctionLiteral literal = lambda.getFunctionLiteral();
            if (literal != null) {
                for (KtParameter p : literal.getValueParameters()) {
                    if (p.getName() != null) {
                        params.add(p.getName());
                    }
                }
            }

            List<ExpressionNode> statements = new ArrayList<>();
            if (literal != null && literal.getBodyExpression() != null) {
                for (KtExpression stmt : literal.getBodyExpression().getStatements()) {
                    statements.add(convertExpression(stmt));
                }
            }

            BlockNode body = new BlockNode(statements);
            return new ClosureNode(params, body);
        }

        if (exp instanceof KtIfExpression ie) {
            List<ExpressionNode> thenBranch = new ArrayList<>();
            List<ExpressionNode> elseBranch = new ArrayList<>();

            if (ie.getThen() instanceof KtBlockExpression tb) {
                for (KtExpression stmt : tb.getStatements()) {
                    thenBranch.add(convertExpression(stmt));
                }
            } else if (ie.getThen() != null) {
                thenBranch.add(convertExpression(ie.getThen()));
            }

            if (ie.getElse() instanceof KtBlockExpression eb) {
                for (KtExpression stmt : eb.getStatements()) {
                    elseBranch.add(convertExpression(stmt));
                }
            } else if (ie.getElse() != null) {
                elseBranch.add(convertExpression(ie.getElse()));
            }

            return new IfNode(
                    convertExpression(ie.getCondition()),
                    thenBranch,
                    elseBranch
            );
        }
        if (exp instanceof KtSafeQualifiedExpression sq) {
            return new SafeCallNode(
                    convertExpression(sq.getReceiverExpression()),
                    convertExpression(sq.getSelectorExpression())
            );
        }

        if (exp instanceof KtThrowExpression te) {
            return new ThrowNode(convertExpression(te.getThrownExpression()));
        }

        return new VariableNode(exp.getText());
    }
    private List<ExpressionNode> extractArgumentsList(KtCallExpression call) {
        List<ExpressionNode> result = new ArrayList<>();
        for (KtValueArgument arg : call.getValueArguments()) {
            result.add(convertExpression(arg.getArgumentExpression()));
        }
        return result;
    }
    private String argumentName(KtValueArgument arg) {
        if (arg == null || arg.getArgumentName() == null) {
            return null;
        }

        KtSimpleNameExpression ref = arg.getArgumentName().getReferenceExpression();
        return ref.getReferencedName();
    }
    private ExpressionNode convertWhenCondition(KtWhenCondition condition, ExpressionNode subject) {
        if (condition == null) return null;

        if (condition instanceof KtWhenConditionIsPattern isPattern) {
            String type = isPattern.getTypeReference() == null
                    ? ""
                    : isPattern.getTypeReference().getText();

            ExpressionNode left = subject != null
                    ? subject
                    : new VariableNode("<when-subject>");

            return new BinaryExpressionNode(
                    left,
                    isPattern.isNegated() ? "!is" : "is",
                    new VariableNode(type)
            );
        }

        if (condition instanceof KtWhenConditionWithExpression withExpr) {
            return convertExpression(withExpr.getExpression());
        }

        if (condition instanceof KtWhenConditionInRange inRange) {
            ExpressionNode left = subject != null
                    ? subject
                    : new VariableNode("<when-subject>");

            return new BinaryExpressionNode(
                    left,
                    inRange.isNegated() ? "!in" : "in",
                    convertExpression(inRange.getRangeExpression())
            );
        }

        return new VariableNode(condition.getText());
    }

    private void handleCallExpression(KtCallExpression call, KtExpression explicitReceiver) {
        handleCallExpression(call, explicitReceiver, false);
    }

    private void handleCallExpression(KtCallExpression call, KtExpression explicitReceiver, boolean safeCall) {

        String rawName = call.getCalleeExpression() != null
                ? call.getCalleeExpression().getText()
                : "unknown";


        String name = normalizeCallableName(rawName);

        DSLNode node = new DSLNode(name);
        node.line = lineOf(call);
        node.safeCall = safeCall;
        node.typeArguments.addAll(typeArgumentTexts(call));

        KtExpression receiverExpr = explicitReceiver;
        if (receiverExpr != null && !"this".equals(receiverExpr.getText())) {
            node.receiver = convertExpression(receiverExpr);
        }

        if (directlyInsideDependenciesBlock() && hasNonLambdaArgument(call)) {
            node.type = DSLNode.Type.DEPENDENCY_DECL;
        } else if (insideRepositoriesBlock()) {
            node.type = DSLNode.Type.REPOSITORIES_DECL;
        } else if (insidePluginApplicationBlock()) {
            node.type = DSLNode.Type.PLUGIN_DECL;
        } else {
            node.type = classify(name, call, explicitReceiver);
        }
        addNode(node);
        stack.push(node);
        try {
            extractArguments(call, node);
            if (node.type == DSLNode.Type.TASK_ACTION) {
                node.semantic = new TaskActionModel(nearestTaskOwnerName(), node.name, List.copyOf(node.arguments));}

            if (node.type == DSLNode.Type.DEPENDENCY_DECL) {
                node.semantic = extractDependency(node);
            }
            if (node.type == DSLNode.Type.TASK_DECLARATION
                    || node.type == DSLNode.Type.TASK_CONFIGURATION) {
                node.semantic = extractTask(call, node, explicitReceiver);
            }
            if (node.type == DSLNode.Type.TASK_RELATION) {
                node.semantic = extractTaskRelation(node);
            }

            if (node.type == DSLNode.Type.PLUGIN_DECL || node.type == DSLNode.Type.APPLY_SCRIPT) {
                node.semantic = extractPlugin(node);
            }

            KtLambdaExpression lambda = extractLambda(call);
            if (lambda != null) {
                visitLambdaBody(node, lambda);
            }
        } finally {
            stack.pop();
        }
    }

    private boolean hasNonLambdaArgument(KtCallExpression call) {
        for (KtValueArgument arg : call.getValueArguments()) {
            KtExpression expr = arg.getArgumentExpression();
            if (expr != null && !(expr instanceof KtLambdaExpression)) {
                return true;
            }
        }
        return false;
    }

    private ExpressionNode convertDelegateWithoutLambda(KtExpression expr) {
        KtCallExpression call = taskDelegateCall(expr);

        if (call == null) {
            return convertExpression(expr);
        }

        List<ExpressionNode> args = new ArrayList<>();
        for (KtValueArgument arg : call.getValueArguments()) {
            KtExpression argExpr = arg.getArgumentExpression();
            if (argExpr instanceof KtLambdaExpression) continue;

            ExpressionNode converted = convertExpression(argExpr);
            if (converted != null) {
                args.add(converted);
            }
        }

        ExpressionNode target = null;
        if (expr instanceof KtDotQualifiedExpression dot) {
            target = convertExpression(dot.getReceiverExpression());
        }

        String name = call.getCalleeExpression() == null
                ? "unknown"
                : normalizeCallableName(call.getCalleeExpression().getText());

        return new MethodCallNode(
                target,
                name,
                args,
                typeArgumentTexts(call)
        );
    }

    private void visitLambdaBody(DSLNode owner, KtLambdaExpression lambda) {
        List<String> params = new ArrayList<>();
        KtFunctionLiteral literal = lambda.getFunctionLiteral();
        if (literal != null) {
            for (KtParameter p : literal.getValueParameters()) {
                if (p.getName() != null) {
                    params.add(p.getName());
                }
            }
        }

        if (!params.isEmpty()) {
            DSLNode closureNode = new DSLNode("");
            closureNode.type = DSLNode.Type.CLOSURE;
            closureNode.line = lineOf(lambda);
            closureNode.arguments.add(new ClosureNode(params, new BlockNode(List.of())));

            attachChild(owner, closureNode);

            stack.push(closureNode);
            try {
                if (literal != null && literal.getBodyExpression() != null) {
                    literal.getBodyExpression().acceptChildren(this);
                }
            } finally {
                stack.pop();
            }
        } else {
            if (literal != null && literal.getBodyExpression() != null) {
                literal.getBodyExpression().acceptChildren(this);
            }
        }
    }

    private boolean isAssignmentOperator(KtBinaryExpression expression) {
        if (expression == null || expression.getOperationReference() == null) return false;
        var token = expression.getOperationToken();

        return token == KtTokens.EQ
                || token == KtTokens.PLUSEQ
                || token == KtTokens.MINUSEQ
                || token == KtTokens.MULTEQ
                || token == KtTokens.DIVEQ
                || token == KtTokens.PERCEQ;
    }

    private boolean isInside(DSLNode.Type type) {
        for (DSLNode n : stack) {
            if (n.type == type) return true;
        }
        return false;
    }
    private boolean insideRepositoriesBlock() {
        return isInside(DSLNode.Type.REPOSITORIES_BLOCK);
    }
    private int lineOf(PsiElement element) {
        if (element == null || element.getContainingFile() == null || element.getTextRange() == null) return -1;
        return element.getContainingFile().getViewProvider().getDocument() != null
                ? element.getContainingFile().getViewProvider().getDocument().getLineNumber(element.getTextOffset()) + 1
                : -1;
    }
    private int columnOf(PsiElement element) {
        if (element == null || element.getContainingFile() == null || element.getTextRange() == null) return -1;
        if (element.getContainingFile().getViewProvider().getDocument() == null) return -1;
        var doc = element.getContainingFile().getViewProvider().getDocument();
        int line = doc.getLineNumber(element.getTextOffset());
        return element.getTextOffset() - doc.getLineStartOffset(line) + 1;
    }
    private String stripOuterQuotes(String text) {
        if (text == null) return null;

        if (text.startsWith("\"\"\"") && text.endsWith("\"\"\"") && text.length() >= 6) {
            return text.substring(3, text.length() - 3);
        }

        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }

        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1);
        }

        return text;
    }
    private Optional<String> resolveString(ExpressionNode node) {
        return switch (node) {
            case null -> Optional.empty();

            case ConstantNode c ->
                    Optional.ofNullable(c.value())
                            .map(Object::toString)
                            .map(this::stripOuterQuotes);

            case GStringNode g ->
                    Optional.ofNullable(g.text())
                            .map(this::stripOuterQuotes);

            case VariableNode v ->
                    Optional.of(v.name());

            case PropertyNode p -> {
                Optional<String> left = resolveString(p.object());
                Optional<String> right = resolveString(p.property());
                if (left.isPresent() && right.isPresent()) {
                    yield Optional.of(left.get() + "." + right.get());
                }
                yield Optional.empty();
            }

            case MethodCallNode call when "file".equals(call.name()) && !call.arguments().isEmpty() ->
                    resolveString(call.arguments().getFirst()).map(s -> "file('" + s + "')");

            default -> Optional.empty();
        };
    }
    private String normalizeCallableName(String name) {
        if (name == null) {
            return "unknown";
        }
        return stripOuterQuotes(name);

    }


    //Dependency Exraction//
    private DependencyModel extractDependency(DSLNode node) {
        DependencyModel dep = new DependencyModel();
        dep.configuration = normalizeCallableName(node.name);

        if (node.arguments.isEmpty()) return dep;

        ExpressionNode arg = node.arguments.getFirst();

        if (arg instanceof ConstantNode c && c.value() != null) {
            parseStringNotation(stripOuterQuotes(c.value().toString()), dep);
            dep.kind = DependencyDeclarationType.EXTERNAL_MODULE;
        } else if (arg instanceof GStringNode g && g.text() != null) {
            parseStringNotation(stripOuterQuotes(g.text()), dep);
            dep.kind = DependencyDeclarationType.EXTERNAL_MODULE;
        } else if (arg instanceof MapNode m) {
            parseMapNotation(m, dep);
            dep.kind = DependencyDeclarationType.EXTERNAL_MODULE;
        } else if (arg instanceof MethodCallNode mce) {
            parseMethodCallNotation(mce, dep);

            if (dep.kind == null
                    && dep.group == null
                    && dep.name == null
                    && dep.projectPath == null
                    && dep.catalogAlias == null) {
                dep.notation = mce;
            }
        } else if (arg instanceof VariableNode || arg instanceof PropertyNode) {
            parseAliasNotation(arg, dep);
        } else {
            dep.notation = arg;
        }


        return dep;
    }
    private void parseStringNotation(String s, DependencyModel dep) {
        String[] extSplit = s.split("@");
        if (extSplit.length == 2) {
            dep.extension = extSplit[1];
        }

        String coords = extSplit[0];
        String[] parts = coords.split(":");

        if (parts.length >= 1) dep.group = parts[0];
        if (parts.length >= 2) dep.name = parts[1];
        if (parts.length >= 3) dep.version = parts[2];
        if (parts.length >= 4) dep.classifier = parts[3];
    }
    private void parseMapNotation(MapNode map, DependencyModel dep) {
        ExpressionNode group = map.entries().get("group");
        ExpressionNode name = map.entries().get("name");
        ExpressionNode version = map.entries().get("version");
        ExpressionNode classifier = map.entries().get("classifier");
        ExpressionNode ext = map.entries().get("ext");

        if (group instanceof ConstantNode c && c.value() != null) dep.group = c.value().toString();
        if (name instanceof ConstantNode c && c.value() != null) dep.name = c.value().toString();
        if (version instanceof ConstantNode c && c.value() != null) dep.version = c.value().toString();
        if (classifier instanceof ConstantNode c && c.value() != null) dep.classifier = c.value().toString();
        if (ext instanceof ConstantNode c && c.value() != null) dep.extension = c.value().toString();
    }
    private void parseAliasNotation(ExpressionNode expr, DependencyModel dep) {
        String chain = flattenReferenceChain(expr);
        if (chain == null || chain.isBlank()) return;
        if (chain.contains(":")) return;

        if (expr instanceof PropertyNode) {
            dep.catalogAlias = chain;
            dep.kind = DependencyDeclarationType.CATALOG_ALIAS;
        } else {
            dep.notation = expr;
        }
    }
    private void parseMethodCallNotation(MethodCallNode mce, DependencyModel dep) {
        String methodName = mce.name();

        if ("project".equals(methodName)) {
            String path = null;

            if (!mce.arguments().isEmpty()) {
                ExpressionNode firstArg = mce.arguments().getFirst();

                if (firstArg instanceof ConstantNode c && c.value() != null) {
                    path = c.value().toString();
                } else if (firstArg instanceof MapNode map) {
                    ExpressionNode pathVal = map.entries().get("path");
                    if (pathVal instanceof ConstantNode c && c.value() != null) {
                        path = c.value().toString();
                    }
                }
            }

            if (path != null && !path.isBlank()) {
                dep.kind = DependencyDeclarationType.PROJECT;
                dep.projectPath = path;
            }

            return;
        } else if ("platform".equals(methodName) || "enforcedPlatform".equals(methodName)) {
            boolean enforced = "enforcedPlatform".equals(methodName);
            if (!mce.arguments().isEmpty()) {
                ExpressionNode platformArg = mce.arguments().getFirst();

                if (platformArg instanceof MethodCallNode innerMce) {
                    parseMethodCallNotation(innerMce, dep);
                    if (dep.kind == DependencyDeclarationType.PROJECT) {
                        dep.kind = DependencyDeclarationType.PLATFORM_PROJECT;
                    }
                } else {
                    resolveString(platformArg).ifPresent(s -> parseStringNotation(s, dep));
                    dep.kind = enforced
                            ? DependencyDeclarationType.ENFORCED_PLATFORM_EXTERNAL
                            : DependencyDeclarationType.PLATFORM_EXTERNAL;
                }
            }
        }
    }
    private DSLNode nearestDependenciesBlock() {
        for (DSLNode n : stack) {
            if (n.type == DSLNode.Type.DEPENDENCIES_BLOCK) {
                return n;
            }
        }
        return null;
    }
    private boolean directlyInsideDependenciesBlock() {
        DSLNode deps = nearestDependenciesBlock();
        return deps != null && !stack.isEmpty() && stack.peek() == deps;
    }



    //Plugin Exraction//
    private DSLNode nearestPluginsBlock() {
        for (DSLNode n : stack) {
            if (n.type == DSLNode.Type.PLUGINS_BLOCK) {
                return n;
            }
        }
        return null;
    }
    private boolean insidePluginApplicationBlock() {
        DSLNode plugins = nearestPluginsBlock();
        return plugins != null && isGradleFileRoot(plugins.parent);
    }
    private DSLNode.Type classifyGradleApply(KtCallExpression call, KtExpression receiver) {
        if (call == null) return DSLNode.Type.METHOD_CALL;

        if (receiver == null) {
            List<KtValueArgument> args = call.getValueArguments();

            // apply("java-library")
            if (args.size() == 1 && args.get(0).getArgumentName() == null) {
                KtExpression arg = args.get(0).getArgumentExpression();
                return arg instanceof KtStringTemplateExpression
                        ? DSLNode.Type.PLUGIN_DECL
                        : DSLNode.Type.METHOD_CALL;
            }

            for (KtValueArgument arg : args) {
                String key = argumentName(arg);
                if ("plugin".equals(key)) return DSLNode.Type.PLUGIN_DECL;
                if ("from".equals(key)) return DSLNode.Type.APPLY_SCRIPT;
            }
        }

        if (receiver != null && "project".equals(receiver.getText())) {
            for (KtValueArgument arg : call.getValueArguments()) {
                String key = argumentName(arg);
                if ("plugin".equals(key)) return DSLNode.Type.PLUGIN_DECL;
                if ("from".equals(key)) return DSLNode.Type.APPLY_SCRIPT;
            }
        }

        return DSLNode.Type.METHOD_CALL;
    }
    private PluginModel extractPlugin(DSLNode node) {
        PluginModel plugin = new PluginModel();

        // id("com.diffplug.spotless")
        if ("id".equals(node.name) && !node.arguments.isEmpty()) {
            resolveString(node.arguments.getFirst())
                    .map(this::stripOuterQuotes)
                    .ifPresent(s -> plugin.id = s);
        }
        // plugins { base }
        if (!"id".equals(node.name)
                && node.arguments.isEmpty()
                && insidePluginsBlock()
                && node.name != null
                && !node.name.isBlank()) {
            plugin.id = stripOuterQuotes(node.name);
        }
        // apply("java-library")
        if ("apply".equals(node.name) && !node.arguments.isEmpty()) {
            resolveString(node.arguments.getFirst()).ifPresent(s -> {
                plugin.id = stripOuterQuotes(s);
                plugin.appliedViaApply = true;
            });
        }
        if ("plugin".equals(node.name) && !node.arguments.isEmpty()) {
            resolveString(node.arguments.getFirst()).ifPresent(s -> {
                plugin.id = stripOuterQuotes(s);
                plugin.appliedViaApply = true;
            });
        }


        for (ExpressionNode arg : node.arguments) {
            if (arg instanceof MethodCallNode call) {
                switch (call.name()) {
                    case "id" -> {
                        if (!call.arguments().isEmpty()) {
                            resolveString(call.arguments().getFirst())
                                    .map(this::stripOuterQuotes)
                                    .ifPresent(s -> plugin.id = s);
                        }
                    }
                    case "version" -> {
                        if (!call.arguments().isEmpty()) {
                            resolveString(call.arguments().getFirst())
                                    .map(this::stripOuterQuotes)
                                    .ifPresent(s -> plugin.version = s);
                        }
                    }
                }
            } else if (arg instanceof MapNode map) {
                if (map.entries().containsKey("plugin")) {
                    resolveString(map.entries().get("plugin"))
                            .map(this::stripOuterQuotes)
                            .ifPresent(s -> {
                                plugin.id = s;
                                plugin.appliedViaApply = true;
                            });
                }
                if (map.entries().containsKey("from")) {
                    plugin.applyTarget = map.entries().get("from");
                    plugin.appliedViaApply = true;
                }
            }
        }

        if ((plugin.id == null || plugin.id.isBlank())
                && insidePluginApplicationBlock()
                && node.name != null
                && !node.name.isBlank()) {

            String base = stripOuterQuotes(node.name);

            if (node.arguments.isEmpty()) {
                plugin.id = base;
            } else {
                String args = node.arguments.stream()
                        .map(this::pluginArgumentText)
                        .filter(s -> s != null && !s.isBlank())
                        .collect(java.util.stream.Collectors.joining(", "));

                plugin.id = base + "(" + args + ")";
            }
        }
        return plugin;
    }
    private String pluginArgumentText(ExpressionNode expr) {
        return resolveString(expr)
                .map(this::stripOuterQuotes)
                .orElseGet(() -> expr == null ? "" : expr.toString());
    }
    private boolean isNestedInsidePluginInfixExpression(KtBinaryExpression expression) {
        PsiElement parent = expression.getParent();

        while (parent != null) {
            if (parent instanceof KtBinaryExpression parentBinary) {
                if (isPluginInfixExpression(parentBinary)) {
                    return true;
                }
            }

            if (parent instanceof KtBlockExpression || parent instanceof KtFile) {
                return false;
            }

            parent = parent.getParent();
        }

        return false;
    }
    private boolean containsPluginIdCall(KtExpression expr) {
        if (expr == null) {
            return false;
        }

        if (expr instanceof KtCallExpression call) {
            String name = call.getCalleeExpression() != null
                    ? normalizeCallableName(call.getCalleeExpression().getText())
                    : null;

            return "id".equals(name);
        }

        if (expr instanceof KtBinaryExpression bin) {
            return containsPluginIdCall(bin.getLeft())
                    || containsPluginIdCall(bin.getRight());
        }

        return false;
    }
    private PluginModel extractPluginFromKotlinPluginExpression(KtExpression expr) {
        PluginModel plugin = new PluginModel();
        collectPluginParts(expr, plugin);
        return plugin.id == null ? null : plugin;
    }
    private void collectPluginParts(KtExpression expr, PluginModel plugin) {
        if (expr == null) return;

        if (expr instanceof KtCallExpression call) {
            String name = call.getCalleeExpression() != null
                    ? normalizeCallableName(call.getCalleeExpression().getText())
                    : null;

            if ("id".equals(name) && !call.getValueArguments().isEmpty()) {
                KtExpression idArg = call.getValueArguments().getFirst().getArgumentExpression();

                resolveString(convertExpression(idArg))
                        .map(this::stripOuterQuotes)
                        .ifPresent(s -> plugin.id = s);
            }

            return;
        }

        if (expr instanceof KtNameReferenceExpression ref) {
            String name = ref.getReferencedName();

            if (isPluginAccessorName(name)) {
                plugin.id = name;
            }

            return;
        }

        if (expr instanceof KtBinaryExpression bin) {
            String op = bin.getOperationReference() != null
                    ? bin.getOperationReference().getText()
                    : null;

            collectPluginParts(bin.getLeft(), plugin);

            if ("version".equals(op)) {
                resolveString(convertExpression(bin.getRight()))
                        .map(this::stripOuterQuotes)
                        .ifPresent(s -> plugin.version = s);
                return;
            }

            if ("apply".equals(op)) {
                String value = resolveString(convertExpression(bin.getRight()))
                        .map(this::stripOuterQuotes)
                        .orElse(null);

                if ("true".equals(value)) {
                    plugin.apply = Boolean.TRUE;
                } else if ("false".equals(value)) {
                    plugin.apply = Boolean.FALSE;
                }

                return;
            }

            collectPluginParts(bin.getRight(), plugin);
        }
    }
    private boolean isPluginInfixExpression(KtBinaryExpression expression) {
        if (expression == null || expression.getOperationReference() == null) {
            return false;
        }

        String op = expression.getOperationReference().getText();

        // Kotlin plugins syntax uses these infix names.
        if ("version".equals(op) || "apply".equals(op)) {
            return containsPluginIdCall(expression);
        }

        return false;
    }
    private boolean isPluginAccessorName(String name) {
        return name != null
                && !name.isBlank()
                && !"id".equals(name)
                && !"version".equals(name)
                && !"apply".equals(name);
    }
    private boolean insidePluginsBlock() {
        return isInside(DSLNode.Type.PLUGINS_BLOCK);
    }



    //Task Exraction//
    private boolean insideTaskLike() {
        for (DSLNode n : stack) {
            if (n.type == DSLNode.Type.TASK_DECLARATION
                    || n.type == DSLNode.Type.TASK_CONFIGURATION) {
                return true;
            }
        }
        return false;
    }
    private boolean isTasksReceiver(KtExpression receiver) {
        if (receiver == null) return false;

        if (receiver instanceof KtNameReferenceExpression ref) {
            return "tasks".equals(ref.getReferencedName());
        }

        if (receiver instanceof KtDotQualifiedExpression dq) {
            KtExpression selector = dq.getSelectorExpression();
            if (selector instanceof KtNameReferenceExpression ref) {
                return "tasks".equals(ref.getReferencedName());
            }
        }

        return "tasks".equals(receiver.getText());
    }
    private boolean isTaskRegistrationCall(KtCallExpression call, KtExpression receiver) {
        String methodName = call.getCalleeExpression() != null ? call.getCalleeExpression().getText() : null;
        return "register".equals(methodName) && isTasksReceiver(receiver);
    }
    private boolean isTaskCreateCall(KtCallExpression call, KtExpression receiver) {
        String methodName = call.getCalleeExpression() != null ? call.getCalleeExpression().getText() : null;
        return "create".equals(methodName) && isTasksReceiver(receiver);
    }
    private boolean isTaskDeclarationCall(KtCallExpression call, KtExpression receiver) {
        String name = call.getCalleeExpression() != null ? call.getCalleeExpression().getText() : null;
        return "task".equals(name);
    }
    private String taskTypeFromDelegate(KtProperty property, KtCallExpression call) {
        if (call != null) {
            for (KtValueArgument arg : call.getValueArguments()) {
                KtExpression e = arg.getArgumentExpression();
                if (e != null && e.getText() != null && e.getText().endsWith("::class")) {
                    return e.getText().replace("::class", "").trim();
                }
            }

            String generic = firstTypeArgumentName(call);
            if (generic != null) return generic;
        }

        if (property.getTypeReference() != null) {
            String t = property.getTypeReference().getText();
            if (t != null && t.contains("<") && t.contains(">")) {
                return t.substring(t.indexOf('<') + 1, t.lastIndexOf('>')).trim();
            }
        }

        return null;
    }
    private String taskDelegateName(KtExpression expr) {
        if (expr instanceof KtDotQualifiedExpression dot) {
            KtExpression selector = dot.getSelectorExpression();

            if (selector instanceof KtCallExpression call) {
                return call.getCalleeExpression() == null ? null : call.getCalleeExpression().getText();
            }

            if (selector instanceof KtNameReferenceExpression ref) {
                return ref.getReferencedName();
            }
        }

        if (expr instanceof KtCallExpression call) {
            return call.getCalleeExpression() == null ? null : call.getCalleeExpression().getText();
        }

        return null;
    }
    private DSLNode createTaskNodeFromDelegate(KtProperty property, KtExpression expr) {
        String bindingName = property.getName() != null ? property.getName() : null;
        String taskName = bindingName;
        String delegateName = taskDelegateName(expr);

        boolean declaration = "registering".equals(delegateName)
                || "creating".equals(delegateName)
                || "register".equals(delegateName);

        DSLNode node = new DSLNode(taskName);
        node.type = declaration
                ? DSLNode.Type.TASK_DECLARATION
                : DSLNode.Type.TASK_CONFIGURATION;
        node.line = lineOf(property);
        node.column = columnOf(property);

        KtCallExpression call = taskDelegateCall(expr);
        if (call != null) {
            List<ExpressionNode> taskArgs = new ArrayList<>();

            for (KtValueArgument arg : call.getValueArguments()) {
                KtExpression argExpression = arg.getArgumentExpression();
                if (argExpression instanceof KtLambdaExpression) continue;

                ExpressionNode converted = convertExpression(argExpression);
                if (converted != null) {
                    taskArgs.add(converted);
                    node.arguments.add(converted);
                }
            }

            if ("register".equals(delegateName) || "named".equals(delegateName)) {
                String explicitName = firstTaskNameFromArgs(taskArgs);
                if (explicitName != null && !explicitName.isBlank()) {
                    taskName = explicitName;
                }
            }
        }

        if (taskName != null && !taskName.isBlank()) {
            node.name = taskName;
        }

        String taskType = taskTypeFromDelegate(property, call);
        String syntaxStyle = "tasks." + delegateName;

        node.semantic = new TaskModel(
                taskName,
                bindingName,
                taskType,
                syntaxStyle,
                new VariableNode("tasks"),
                node.arguments
        );

        return node;
    }
    private KtCallExpression taskDelegateCall(KtExpression expr) {
        if (expr instanceof KtCallExpression call) return call;

        if (expr instanceof KtDotQualifiedExpression dot
                && dot.getSelectorExpression() instanceof KtCallExpression call) {
            return call;
        }

        return null;
    }
    private KtLambdaExpression extractLambdaFromDelegate(KtExpression expr) {
        KtCallExpression call = taskDelegateCall(expr);
        return call == null ? null : extractLambda(call);
    }
    private boolean isTaskConfigurationCall(KtCallExpression call, KtExpression receiver) {
        String methodName = call.getCalleeExpression() != null
                ? call.getCalleeExpression().getText()
                : null;
        if (("named".equals(methodName) || "getByName".equals(methodName))) {
            return isTasksReceiver(receiver) || (receiver == null && insideTasksBlock());
        }

        if (isBulkTaskConfigurationMethod(methodName)) {
            return isTasksReceiver(receiver)
                    || receiver == null && insideTasksBlock()
                    || isTasksWithTypeReceiver(receiver);
        }

        if ("withType".equals(methodName)) {
            return (isTasksReceiver(receiver) || receiver == null && insideTasksBlock())
                    && extractLambda(call) != null;
        }

        // tasks { compileKotlin { ... } }
        if (receiver == null && insideTasksBlock() && extractLambda(call) != null) {
            return true;
        }

        // tasks.compileKotlin { ... }
        if (isTasksReceiver(receiver) && extractLambda(call) != null) {
            return true;
        }

        return false;
    }
    private boolean isTaskDelegate(KtExpression expr) {
        String name = taskDelegateName(expr);

        return isTasksDelegateReceiver(expr)
                && ("registering".equals(name)
                || "creating".equals(name)
                || "existing".equals(name)
                || "getting".equals(name)
                || "register".equals(name)
                || "named".equals(name));
    }
    private boolean isTasksDelegateReceiver(KtExpression expr) {
        if (expr instanceof KtDotQualifiedExpression dot) {
            KtExpression receiver = dot.getReceiverExpression();
            return receiver != null && "tasks".equals(receiver.getText());
        }

        if (expr instanceof KtCallExpression && directlyInsideTasksBlock()) {
            return true;
        }

        return false;
    }
    private TaskModel extractTask(KtCallExpression call, DSLNode node, KtExpression receiver) {
        String methodName = call.getCalleeExpression() != null
                ? call.getCalleeExpression().getText()
                : null;

        String style = methodName;
        String taskName = null;
        String taskTypeName = null;

        ExpressionNode receiverExpr = node.receiver;
        List<ExpressionNode> originalArgs = node.arguments == null
                ? List.of()
                : List.copyOf(node.arguments);
        List<ExpressionNode> remainingArgs = new ArrayList<>(originalArgs);

        if ("task".equals(methodName)) {
            style = "task";
            taskName = firstTaskNameFromArgs(remainingArgs);
            removeTaskNameArg(remainingArgs, taskName);
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        } else if ("create".equals(methodName) && isTasksReceiver(receiver)) {
            style = "tasks.create";
            taskName = firstTaskNameFromArgs(remainingArgs);
            removeTaskNameArg(remainingArgs, taskName);
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        } else if ("register".equals(methodName) && isTasksReceiver(receiver)) {
            style = "tasks.register";
            taskName = firstTaskNameFromArgs(remainingArgs);
            removeTaskNameArg(remainingArgs, taskName);
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);

        } else if (("named".equals(methodName) || "getByName".equals(methodName))
                && isTasksScope(receiver)) {
            style = "named".equals(methodName) ? "named" : "tasks.getByName";
            taskName = firstTaskNameFromArgs(remainingArgs);
            removeTaskNameArg(remainingArgs, taskName);
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);

        } else if (isBulkTaskConfigurationMethod(methodName) && isTasksWithTypeReceiver(receiver)) {
            style = "tasks.withType." + methodName;
            taskTypeName = taskTypeFromWithTypeReceiver(receiver);

        } else if (isBulkTaskConfigurationMethod(methodName)
                && (isTasksReceiver(receiver) || receiver == null && insideTasksBlock())) {
            style = "tasks." + methodName;
            taskTypeName = firstTypeArgumentName(call);
            if (taskTypeName == null || taskTypeName.isBlank()) {
                taskTypeName = extractTaskTypeFromArgs(remainingArgs);
            }

        } else if ("withType".equals(methodName)
                && (isTasksReceiver(receiver) || receiver == null && insideTasksBlock())) {
            style = "tasks.withType";
            taskTypeName = firstTypeArgumentName(call);
            if (taskTypeName == null || taskTypeName.isBlank()) {
                taskTypeName = extractTaskTypeFromArgs(remainingArgs);
            }

        } else if (isTasksReceiver(receiver)) {
            style = "tasks." + methodName;
            taskName = methodName;
            taskTypeName = firstTypeArgumentName(call);
            if (taskTypeName == null || taskTypeName.isBlank()) {
                taskTypeName = extractTaskTypeFromArgs(remainingArgs);
            }

        } else if (receiver == null && insideTasksBlock()) {
            style = "tasks." + methodName;

            if ("withType".equals(methodName)) {
                taskName = null;
                taskTypeName = firstTypeArgumentName(call);
            } else {
                taskName = methodName;
                taskTypeName = firstTypeArgumentName(call);
                if (taskTypeName == null || taskTypeName.isBlank()) {
                    taskTypeName = extractTaskTypeFromArgs(remainingArgs);
                }
            }
        }


        String genericType = firstTypeArgumentName(call);
        if (genericType != null && !genericType.isBlank()) {
            taskTypeName = genericType;
        }

        if ((taskName == null || taskName.isBlank())
                && !isBulkTaskConfigurationStyle(style)
                && node.name != null
                && !"task".equals(node.name)
                && !"create".equals(node.name)
                && !"register".equals(node.name)
                && !"named".equals(node.name)
                && !"withType".equals(node.name)
                && !"getByName".equals(node.name)) {
            taskName = node.name;
        }

        if (taskName != null && !taskName.isBlank()) {
            node.name = taskName;
        }

        return new TaskModel(taskName, null, taskTypeName, style, receiverExpr, remainingArgs);
    }
    private boolean isBulkTaskConfigurationMethod(String methodName) {
        return "configureEach".equals(methodName) || "all".equals(methodName);
    }
    private boolean isBulkTaskConfigurationStyle(String style) {
        return "tasks.configureEach".equals(style)
                || "tasks.all".equals(style)
                || "tasks.withType".equals(style)
                || "tasks.withType.configureEach".equals(style)
                || "tasks.withType.all".equals(style);
    }
    private boolean isTasksWithTypeReceiver(KtExpression receiver) {
        if (receiver instanceof KtDotQualifiedExpression dot
                && dot.getSelectorExpression() instanceof KtCallExpression call) {
            String callee = call.getCalleeExpression() == null ? null : call.getCalleeExpression().getText();
            return "withType".equals(callee) && isTasksReceiver(dot.getReceiverExpression());
        }

        if (receiver instanceof KtCallExpression call) {
            String callee = call.getCalleeExpression() == null ? null : call.getCalleeExpression().getText();
            return "withType".equals(callee) && insideTasksBlock();
        }

        return false;
    }
    private String taskTypeFromWithTypeReceiver(KtExpression receiver) {
        KtCallExpression call = null;

        if (receiver instanceof KtDotQualifiedExpression dot
                && dot.getSelectorExpression() instanceof KtCallExpression selectorCall) {
            call = selectorCall;
        } else if (receiver instanceof KtCallExpression receiverCall) {
            call = receiverCall;
        }

        if (call == null) return null;

        String generic = firstTypeArgumentName(call);
        if (generic != null && !generic.isBlank()) {
            return generic;
        }

        List<ExpressionNode> args = new ArrayList<>();
        for (KtValueArgument arg : call.getValueArguments()) {
            ExpressionNode converted = convertExpression(arg.getArgumentExpression());
            if (converted != null) {
                args.add(converted);
            }
        }

        return extractTaskTypeFromArgs(args);
    }
    private String firstTaskNameFromArgs(List<ExpressionNode> args) {
        for (ExpressionNode arg : args) {
            String candidate = extractTaskName(arg);
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
    private void removeTaskNameArg(List<ExpressionNode> args, String taskName) {
        if (taskName == null || taskName.isBlank()) {
            return;
        }

        Iterator<ExpressionNode> it = args.iterator();
        while (it.hasNext()) {
            ExpressionNode arg = it.next();
            String candidate = extractTaskName(arg);
            if (Objects.equals(candidate, taskName)) {
                it.remove();
                break;
            }
        }
    }
    private boolean insideTasksBlock() {
        for (DSLNode n : stack) {
            if (n.type == DSLNode.Type.BLOCK && "tasks".equals(n.name)) {
                return true;
            }
        }
        return false;
    }
    private boolean directlyInsideTasksBlock() {
        if (stack.isEmpty()) return false;
        DSLNode parent = stack.peek();
        return parent.type == DSLNode.Type.BLOCK && "tasks".equals(parent.name);
    }
    private TaskRelationModel extractTaskRelation(DSLNode node) {
        return new TaskRelationModel(
                taskRelationOwnerName(node),
                node.name,
                List.copyOf(node.arguments)
        );

    }
    private boolean isTasksScope(KtExpression receiver) {
        return isTasksReceiver(receiver) || (receiver == null && insideTasksBlock());
    }
    private String taskRelationOwnerName(DSLNode node) {
        String receiver = referenceText(node.receiver);
        if (receiver != null && !receiver.isBlank()) return receiver;
        return nearestTaskOwnerName();
    }
    private String nearestTaskOwnerName() {
        for (DSLNode n : stack) {
            if (n.type == DSLNode.Type.TASK_DECLARATION
                    || n.type == DSLNode.Type.TASK_CONFIGURATION) {
                return n.name;
            }
        }
        return null;
    }



}
