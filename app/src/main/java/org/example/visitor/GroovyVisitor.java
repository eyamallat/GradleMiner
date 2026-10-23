package org.example.visitor;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.example.model.*;
import org.example.model.ImportNode;
import org.example.model.PropertyNode;

import java.util.*;

import static org.example.visitor.visitorUtils.*;

public class GroovyVisitor extends CodeVisitorSupport {

    private final Deque<DSLNode> stack = new ArrayDeque<>();
    private final Deque<DSLNode> parentStack = new ArrayDeque<>();
    private final List<DSLNode> roots = new ArrayList<>();

    public List<DSLNode> getRoots() {
        return roots;
    }
    private void addNode(DSLNode node) {
        if (!stack.isEmpty()) {
            DSLNode parent = stack.peek();
            parent.children.add(node);
            node.parent = parent;       // assign parent
        } else if (!parentStack.isEmpty()) {
            DSLNode parent = parentStack.peek();
            parent.children.add(node);
            node.parent = parent;       // assign parent
        } else {
            roots.add(node);
            node.parent = null;         // top-level node has no parent
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
    public void visitThrowStatement(ThrowStatement statement) {
        DSLNode node = new DSLNode("throw");
        node.type = DSLNode.Type.THROW;
        node.line = statement.getLineNumber();
        node.column = statement.getColumnNumber();

        ExpressionNode thrown = convertExpression(statement.getExpression());
        if (thrown != null) {
            node.value = thrown;
            node.semantic = new ThrowNode(thrown);
        }

        addNode(node);
    }
    @Override
    public void visitIfElse(IfStatement ifElse) {
        DSLNode node = new DSLNode("if");
        node.type = DSLNode.Type.CONDITION;
        node.line = ifElse.getLineNumber();
        node.arguments.add(convertExpression(ifElse.getBooleanExpression()));

        addNode(node);
        stack.push(node);
        if (ifElse.getIfBlock() != null) {
            ifElse.getIfBlock().visit(this);
        }
        if (ifElse.getElseBlock() != null) {
            ifElse.getElseBlock().visit(this);
        }

        stack.pop();
    }
    @Override
    public void visitForLoop(ForStatement forLoop) {
        DSLNode node = new DSLNode("for");
        node.type = DSLNode.Type.BLOCK;
        node.line = forLoop.getLineNumber();

        Parameter iterator = forLoop.getVariable();
        Expression collection = forLoop.getCollectionExpression();

        if (iterator != null) {
            node.arguments.add(new VariableNode(iterator.getName()));
        }

        if (collection != null && !(collection instanceof EmptyExpression)) {
            node.value = convertExpression(collection);
        }

        addNode(node);

        stack.push(node);
        try {
            if (forLoop.getLoopBlock() != null) {
                forLoop.getLoopBlock().visit(this);
            }
        } finally {
            stack.pop();
        }
    }
    @Override
    public void visitBlockStatement(BlockStatement block) {
        for (Statement stmt : block.getStatements()) {
            stmt.visit(this);
        }
    }
    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        String name = call.getMethodAsString();
        if (name == null) name = call.getMethod().getText();

        Expression receiver = call.getObjectExpression();

        DSLNode node = new DSLNode(name);
        node.line = call.getLineNumber();

        if (receiver != null && !(receiver instanceof EmptyExpression) && !"this".equals(receiver.getText())) {
            node.receiver = convertExpression(receiver);
        }

        if (directlyInsideDependenciesBlock() && hasNonClosureArgument(call)) {
            node.type = DSLNode.Type.DEPENDENCY_DECL;
        } else if (insideRepositoriesBlock()) {
            node.type = DSLNode.Type.REPOSITORIES_DECL;
        } else if (isPluginDeclarationCall(call)) {
            node.type = DSLNode.Type.PLUGIN_DECL;
        }else {
            node.type = classify(name, call);
        }

        addNode(node);
        stack.push(node);
        try {
            extractArguments(call, node);

            if (node.type == DSLNode.Type.DEPENDENCY_DECL) {
                node.semantic = extractDependency(node);
            }
            if (node.type == DSLNode.Type.TASK_DECLARATION
                    || node.type == DSLNode.Type.TASK_CONFIGURATION) {
                node.semantic = extractTask(call, node);
            }
            if (node.type == DSLNode.Type.TASK_RELATION) {
                node.semantic = new TaskRelationModel(
                        taskRelationOwnerName(node),
                        node.name,
                        List.copyOf(node.arguments)
                );

            }

            if (node.type == DSLNode.Type.PLUGIN_DECL || node.type == DSLNode.Type.APPLY_SCRIPT) {
                node.semantic = extractPlugin(node);
            }

            ClosureExpression closure = extractClosure(call);
            if (closure != null && closure.getCode() != null) {
                visitClosureBody(node, closure);
            }
        } finally {
            stack.pop();
        }
    }
    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        if (!(expression instanceof DeclarationExpression) && !isAssignmentOperator(expression)) {
            super.visitBinaryExpression(expression);
            return;
        }

        DSLNode node = new DSLNode(expression.getLeftExpression().getText());
        node.line = expression.getLineNumber();
        node.type = (expression instanceof DeclarationExpression)
                ? DSLNode.Type.DECLARATION
                : DSLNode.Type.ASSIGNMENT;
        node.operator = expression.getOperation() == null ? null : expression.getOperation().getText();

        Expression right = expression.getRightExpression();
        if (!(right instanceof EmptyExpression)) {
            node.value = convertExpression(right);
        }

        addNode(node);

        if (right instanceof MapExpression me) {
            stack.push(node);
            try {
                visitMapLiteralAsChildren(me);
            } finally {
                stack.pop();
            }
        }
    }
    @Override
    public void visitMapExpression(MapExpression expression) {
        Map<String, ExpressionNode> entries = new LinkedHashMap<>();

        for (MapEntryExpression entry : expression.getMapEntryExpressions()) {
            entries.put(
                    entry.getKeyExpression().getText(),
                    convertExpression(entry.getValueExpression())
            );
        }

        if (!stack.isEmpty()) {
            stack.peek().arguments.add(new MapNode(entries));
        }

        super.visitMapExpression(expression);
    }



    public void visitModule(ModuleNode module) {

        module.getImports().forEach(this::visitImport);
        module.getStarImports().forEach(this::visitImport);
        module.getStaticImports().values().forEach(this::visitImport);
        module.getStaticStarImports().values().forEach(this::visitImport);
        for (MethodNode method : module.getMethods()) {
            visitMethodNode(method, false);
        }

        for (ClassNode clazz : module.getClasses()) {
            if (clazz.isScript()) {
                for (MethodNode method : clazz.getMethods()) {
                    visitMethodNode(method, false);
                }
            } else {
                visitClass(clazz);
            }
        }


        if (module.getStatementBlock() != null) {
            for (Statement stmt : module.getStatementBlock().getStatements()) {
                stmt.visit(this);
            }
        }}
    private void visitImport(org.codehaus.groovy.ast.ImportNode imp) {

        String qualifiedName;

        if (imp.isStar()) {
            qualifiedName = imp.getPackageName();
        } else {
            qualifiedName = imp.getClassName();
        }

        ImportNode node = new ImportNode(
                imp.isStatic(),
                imp.isStar(),
                qualifiedName,
                imp.getAlias()
        );

        DSLNode dslNode = new DSLNode("import");
        dslNode.type = DSLNode.Type.IMPORT;
        dslNode.arguments.add(node);
        dslNode.line = imp.getLineNumber();

        addNode(dslNode);
    }
    private void visitMethodNode(MethodNode method, boolean attachToCurrentParent) {
        if (method == null) return;
        if (method.isSynthetic()) return;
        if (method.getLineNumber() <= 0) return;

        String name = method.getName();
        if ("run".equals(name)) return;
        if ("main".equals(name)) return;

        DSLNode methodNode = new DSLNode(name);
        methodNode.type = DSLNode.Type.METHOD_DECL;
        methodNode.line = method.getLineNumber();
        methodNode.column = method.getColumnNumber();
        for (Parameter p : method.getParameters()) {
            String paramName = p.getName();
            String paramType = p.getType() == null ? null : p.getType().getNameWithoutPackage();

            if (paramName == null) continue;

            String text = paramType == null || paramType.isBlank() || "Object".equals(paramType)
                    ? paramName
                    : paramName + ": " + paramType;

            methodNode.arguments.add(new VariableNode(text));
        }

        addNode(methodNode);

        Statement code = method.getCode();
        if (code != null) {
            pushParent(methodNode);
            try {
                code.visit(this);
            } finally {
                popParent();
            }
        }
    }
    private void visitClass(ClassNode clazz) {
        if (clazz.isScript()) {
            return;
        }
        DSLNode classNode = new DSLNode(clazz.getNameWithoutPackage());
        classNode.type = DSLNode.Type.CLASS_DECL;
        classNode.line = clazz.getLineNumber();
        classNode.column = clazz.getColumnNumber();

        addNode(classNode);
        pushParent(classNode);
        try {
            for (ConstructorNode ctor : clazz.getDeclaredConstructors()) {
                visitConstructor(ctor, clazz);
            }
            for (MethodNode method : clazz.getMethods()) {
                visitMethodNode(method, true);
            }

        } finally {
            popParent();
        }
    }
    private void visitClosureBody(DSLNode owner, ClosureExpression closure) {
        org.codehaus.groovy.ast.Parameter[] params = closure.getParameters();

        if (params != null && params.length > 0) {
            List<String> paramNames = Arrays.stream(params)
                    .map(Parameter::getName)
                    .toList();

            DSLNode closureNode = new DSLNode("");
            closureNode.type = DSLNode.Type.CLOSURE;
            closureNode.line = closure.getLineNumber();
            closureNode.arguments.add(new ClosureNode(paramNames, new BlockNode(List.of())));

            attachChild(owner, closureNode);

            stack.push(closureNode);
            try {
                closure.getCode().visit(this);
            } finally {
                stack.pop();
            }
        } else {
            closure.getCode().visit(this);
        }
    }
    private void visitConstructor(ConstructorNode ctor, ClassNode ownerClass) {
        if (ctor == null) return;
        if (ctor.isSynthetic()) return;
        if (ctor.getLineNumber() <= 0) return;

        List<String> paramNames = Arrays.stream(ctor.getParameters())
                .map(Parameter::getName)
                .toList();

        DSLNode ctorNode = new DSLNode(ownerClass.getNameWithoutPackage());
        ctorNode.type = DSLNode.Type.CONSTRUCTOR_DECL;
        ctorNode.line = ctor.getLineNumber();
        ctorNode.column = ctor.getColumnNumber();
        ctorNode.semantic = new ConstructorDeclNode(
                ownerClass.getNameWithoutPackage(),
                paramNames
        );

        addNode(ctorNode);

        Statement code = ctor.getCode();
        if (code != null) {
            pushParent(ctorNode);
            try {
                code.visit(this);
            } finally {
                popParent();
            }
        }
    }
    private void visitMapLiteralAsChildren(MapExpression expression) {
        for (MapEntryExpression entry : expression.getMapEntryExpressions()) {
            String key = entry.getKeyExpression().getText();

            DSLNode entryNode = new DSLNode(key);
            entryNode.type = DSLNode.Type.ASSIGNMENT;
            entryNode.line = entry.getLineNumber();

            Expression valueExpr = entry.getValueExpression();
            entryNode.value = convertExpression(valueExpr);

            addNode(entryNode);

            if (valueExpr instanceof MapExpression nestedMap) {
                stack.push(entryNode);
                visitMapLiteralAsChildren(nestedMap);
                stack.pop();
            }
        }
    }



    private DSLNode.Type classify(String name, MethodCallExpression call) {
        boolean hasClosure = extractClosure(call) != null;

        if (isTaskRelationCall(name) && (insideTaskLike() || call.getObjectExpression() != null)) {
            return DSLNode.Type.TASK_RELATION;
        }
        if (isTaskActionCall(name) && insideTaskLike()) {
            return DSLNode.Type.TASK_ACTION;
        }

        if (isTaskDeclarationCall(call) || isTaskRegistrationCall(call) || isTaskCreateCall(call)) {
            return DSLNode.Type.TASK_DECLARATION;
        }

        if (isTaskConfigurationCall(call)) {
            return DSLNode.Type.TASK_CONFIGURATION;
        }

        return switch (name) {
            case "dependencies" -> DSLNode.Type.DEPENDENCIES_BLOCK;
            case "repositories" -> DSLNode.Type.REPOSITORIES_BLOCK;
            case "plugins" -> DSLNode.Type.PLUGINS_BLOCK;
            case "task" -> DSLNode.Type.TASK_DECLARATION;
            case "apply" -> classifyGradleApply(call);
            case "allprojects" -> DSLNode.Type.ALLPROJECTS;
            case "subprojects" -> DSLNode.Type.SUBPROJECTS;
            case "buildscript" -> DSLNode.Type.BUILDSCRIPT;
            case "import" -> DSLNode.Type.IMPORT;
            default -> hasClosure ? DSLNode.Type.BLOCK : DSLNode.Type.METHOD_CALL;
        };
    }
    private ExpressionNode convertExpression(Expression exp) {
        if (exp instanceof EmptyExpression) {
            return null;
        }
        if (exp instanceof ConstantExpression ce) {
            return new ConstantNode(ce.getValue());
        }
        if (exp instanceof TupleExpression te) {

            List<ExpressionNode> values = new ArrayList<>();

            for (Expression e : te.getExpressions()) {
                values.add(convertExpression(e));
            }

            return new TupleNode(values);
        }
        if (exp instanceof GStringExpression gs) {
            return new GStringNode(gs.getText());
        }
        if (exp instanceof VariableExpression ve) {
            return new VariableNode(ve.getName());
        }
        if (exp instanceof MapExpression me) {
            Map<String, ExpressionNode> map = new LinkedHashMap<>();
            for (MapEntryExpression e : me.getMapEntryExpressions()) {
                map.put(
                        e.getKeyExpression().getText(),
                        convertExpression(e.getValueExpression())
                );
            }
            return new MapNode(map);
        }
        if (exp instanceof ListExpression le) {
            List<ExpressionNode> list = new ArrayList<>();
            for (Expression e : le.getExpressions()) {
                list.add(convertExpression(e));
            }
            return new ListNode(list);
        }
        if (exp instanceof MethodCallExpression mce) {
            Expression receiverExpr = mce.getObjectExpression();
            ExpressionNode target = null;

            // 2. Only capture the target if it's not the implicit 'this'
            if (receiverExpr instanceof VariableExpression ve) {
                if (!ve.getName().equals("this")) {
                    target = convertExpression(ve);
                }
            } else if (!(receiverExpr instanceof EmptyExpression)) {
                target = convertExpression(receiverExpr);
            }
            return new MethodCallNode(
                    target,
                    mce.getMethodAsString(),
                    extractArgumentsList(mce),
                    List.of()
            );


        }
        if (exp instanceof BinaryExpression be) {
            return new BinaryExpressionNode(
                    convertExpression(be.getLeftExpression()),
                    be.getOperation().getText(),
                    convertExpression(be.getRightExpression())
            );
        }
        if (exp instanceof CastExpression ce) {
            return new BinaryExpressionNode(
                    convertExpression(ce.getExpression()),
                    "as",
                    new VariableNode(ce.getType().getNameWithoutPackage())
            );
        }
        if (exp instanceof PropertyExpression pe) {
            return new PropertyNode(
                    convertExpression(pe.getObjectExpression()),
                    convertExpression(pe.getProperty())
            );
        }
        if (exp instanceof ClosureExpression ce) {
            List<String> params = new ArrayList<>();
            org.codehaus.groovy.ast.Parameter[] closureParams = ce.getParameters();
            if (closureParams != null) {
                for (org.codehaus.groovy.ast.Parameter p : closureParams) {
                    params.add(p.getName());
                }
            }
            List<ExpressionNode> statements = new ArrayList<>();

            if (ce.getCode() instanceof BlockStatement block) {
                for (Statement stat : block.getStatements()) {
                    if (stat instanceof ExpressionStatement es) {
                        statements.add(convertExpression(es.getExpression()));
                    }
                }
            }
            BlockNode body = new BlockNode(statements);
            return new ClosureNode(params, body);
        }
        if (exp instanceof TernaryExpression te) {
            return new TernaryExpressionNode(
                    convertExpression(te.getBooleanExpression().getExpression()),
                    convertExpression(te.getTrueExpression()),
                    convertExpression(te.getFalseExpression())
            );
        }
        if (exp instanceof ConstructorCallExpression cce) {

            List<ExpressionNode> args = new ArrayList<>();

            if (cce.getArguments() instanceof ArgumentListExpression ale) {
                for (Expression arg : ale.getExpressions()) {
                    args.add(convertExpression(arg));
                }
            }

            return new ConstructorCallNode(
                    cce.getType().getNameWithoutPackage(),
                    args
            );
        }


        return new VariableNode(exp.getText());
    }





    private List<ExpressionNode> extractArgumentsList(MethodCallExpression mce) {
        List<ExpressionNode> result = new ArrayList<>();
        Expression arguments = mce.getArguments();

        if (arguments instanceof ArgumentListExpression ale) {
            for (Expression exp : ale.getExpressions()) {
                result.add(convertExpression(exp));
            }
        }
        return result;
    }
    private boolean isAssignmentOperator(BinaryExpression expression) {
        if (expression == null || expression.getOperation() == null) return false;

        String op = expression.getOperation().getText();
        return "=".equals(op)
                || "+=".equals(op)
                || "-=".equals(op)
                || "*=".equals(op)
                || "/=".equals(op)
                || "%=".equals(op)
                || "&=".equals(op)
                || "|=".equals(op)
                || "^=".equals(op)
                || "<<=".equals(op)
                || "<<".equals(op)
                || ">>".equals(op)
                || ">>=".equals(op)
                || ">>>=".equals(op);
    }
    private boolean isInside(DSLNode.Type type) {
        for (DSLNode n : stack) {
            if (n.type == type) return true;
        }
        return false;
    }
    private boolean hasNonClosureArgument(MethodCallExpression call) {
        if (call == null || call.getArguments() == null) return false;

        Expression args = call.getArguments();
        if (args instanceof TupleExpression tuple) {
            for (Expression expr : tuple.getExpressions()) {
                if (!(expr instanceof ClosureExpression)) {
                    return true;
                }
            }
            return false;
        }

        return !(args instanceof ClosureExpression)
                && !(args instanceof EmptyExpression);
    }
    private boolean insideRepositoriesBlock() {
        return isInside(DSLNode.Type.REPOSITORIES_BLOCK);
    }
    private void extractArguments(MethodCallExpression call, DSLNode node) {
        Expression args = call.getArguments();

        if (args instanceof NamedArgumentListExpression nale) {
            node.arguments.add(convertExpression(nale));
            return;
        }

        if (args instanceof TupleExpression tuple) {
            for (Expression exp : tuple.getExpressions()) {
                if (exp instanceof ClosureExpression) continue;

                if (node.type == DSLNode.Type.TASK_DECLARATION) {
                    if (exp instanceof MethodCallExpression mce) {
                        extractArguments(mce, node);

                        ClosureExpression innerClosure = extractClosure(mce);
                        if (innerClosure != null && innerClosure.getCode() != null) {
                            visitClosureBody(node, innerClosure);
                        }
                        continue;
                    }

                    if (exp instanceof BinaryExpression be) {
                        Expression left = be.getLeftExpression();
                        Expression right = be.getRightExpression();

                        if (left instanceof MethodCallExpression mce) {
                            extractArguments(mce, node);

                            if (right instanceof ClosureExpression ce && ce.getCode() != null) {
                                visitClosureBody(node, ce);
                            }
                            continue;
                        }
                    }
                }

                node.arguments.add(convertExpression(exp));
            }
        }
    }
    private ClosureExpression extractClosure(MethodCallExpression call) {
        Expression args = call.getArguments();

        if (args instanceof ArgumentListExpression list) {
            for (Expression exp : list.getExpressions()) {
                if (exp instanceof ClosureExpression ce) {
                    return ce;
                }
                if (exp instanceof BinaryExpression be
                        && be.getRightExpression() instanceof ClosureExpression ce) {
                    return ce;
                }
            }
        }

        if (args instanceof TupleExpression tuple) {
            for (Expression exp : tuple.getExpressions()) {
                if (exp instanceof ClosureExpression ce) {
                    return ce;
                }
                if (exp instanceof BinaryExpression be
                        && be.getRightExpression() instanceof ClosureExpression ce) {
                    return ce;
                }
            }
        }

        return null;
    }
    private Optional<String> resolveString(ExpressionNode node) {
        return switch (node) {
            case null -> Optional.empty();
            case ConstantNode(Object v) -> Optional.ofNullable(v).map(Object::toString);
            case GStringNode(String s) -> Optional.of(s);
            case VariableNode(String name) -> Optional.of(name);

            case PropertyNode(ExpressionNode obj, ExpressionNode prop) -> {
                Optional<String> left = resolveString(obj);
                Optional<String> right = resolveString(prop);
                if (left.isPresent() && right.isPresent()) {
                    yield Optional.of(left.get() + "." + right.get());
                }
                yield Optional.empty();
            }



            case MethodCallNode(ExpressionNode target, String name, List<ExpressionNode> args, List<String> typeArgs)
                    when name.equals("file") && !args.isEmpty() ->
                    resolveString(args.getFirst()).map(s -> "file('" + s + "')");

            default -> Optional.empty();
        };

    }




    //Dependency Extraction//
    private DependencyModel extractDependency(DSLNode node) {
        DependencyModel dep = new DependencyModel();
        dep.configuration = node.name;

        if (node.arguments.isEmpty()) return dep;

        ExpressionNode arg = node.arguments.getFirst();

        if (arg instanceof ConstantNode(Object value)) {
            parseStringNotation(value.toString(), dep);
            dep.kind = DependencyDeclarationType.EXTERNAL_MODULE;
        } else if (arg instanceof GStringNode(String text)) {
            parseStringNotation(text, dep);
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
        if (mce.name().equals("project")) {
            dep.kind = DependencyDeclarationType.PROJECT;

            if (!mce.arguments().isEmpty()) {
                ExpressionNode firstArg = mce.arguments().getFirst();

                // Handle project(':path') or project(path: ':path')
                if (firstArg instanceof ConstantNode(Object value)) {
                    dep.projectPath = value.toString();
                } else if (firstArg instanceof MapNode(Map<String, ExpressionNode> entries)) {
                    // Handle project(path: ':project:pcComponents:API')
                    ExpressionNode pathVal = entries.get("path");
                    if (pathVal instanceof ConstantNode(Object value)) {
                        dep.projectPath = value.toString();
                    }
                }
            }
        }
        else if (methodName.equals("platform") || methodName.equals("enforcedPlatform")) {
            boolean enforced = methodName.equals("enforcedPlatform");
            if (!mce.arguments().isEmpty()) {
                ExpressionNode platformArg = mce.arguments().getFirst();

                // Platform can wrap a String OR another MethodCall (like project)
                if (platformArg instanceof MethodCallNode innerMce) {
                    parseMethodCallNotation(innerMce, dep);
                    if (dep.kind == DependencyDeclarationType.PROJECT) {
                        dep.kind = DependencyDeclarationType.PLATFORM_PROJECT;
                    }// Recursive call to handle project inside platform
                } else {
                    resolveString(platformArg).ifPresent(s -> parseStringNotation(s, dep));
                    dep.kind = enforced
                            ? DependencyDeclarationType.ENFORCED_PLATFORM_EXTERNAL
                            : DependencyDeclarationType.PLATFORM_EXTERNAL;
                }
            }
        }
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

        if (group instanceof ConstantNode(Object value))
            dep.group = value.toString();

        if (name instanceof ConstantNode(Object value))
            dep.name = value.toString();

        if (version instanceof ConstantNode(Object value))
            dep.version = value.toString();

        if (classifier instanceof ConstantNode(Object v))
            dep.classifier = v.toString();

        if (ext instanceof ConstantNode(Object v))
            dep.extension = v.toString();
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
        DSLNode dependencies = nearestDependenciesBlock();
        return dependencies != null && !stack.isEmpty() && stack.peek() == dependencies;
    }


    //Plugin Exraction//
    private PluginModel extractPlugin(DSLNode node) {
        PluginModel plugin = new PluginModel();

        for (ExpressionNode arg : node.arguments) {
            // apply plugin: x  |  apply from: y
            if (arg instanceof MapNode(Map<String, ExpressionNode> entries)) {
                if (entries.containsKey("plugin")) {
                    resolveString(entries.get("plugin")).ifPresent(s -> {
                        plugin.id = s;
                        plugin.appliedViaApply = true;
                    });
                }

                if (entries.containsKey("from")) {
                    plugin.applyTarget = entries.get("from");
                    plugin.appliedViaApply = true;
                }

                continue;
            }
            // plugins { id "x" version "y" }
            if ("id".equals(node.name) && !node.arguments.isEmpty()) {
                resolveString(node.arguments.getFirst()).ifPresent(s -> plugin.id = s);
            }

            if ("version".equals(node.name) && !node.arguments.isEmpty()) {
                resolveString(node.arguments.getFirst()).ifPresent(s -> plugin.version = s);

                if (node.receiver instanceof MethodCallNode idCall
                        && "id".equals(idCall.name())
                        && !idCall.arguments().isEmpty()) {
                    resolveString(idCall.arguments().getFirst()).ifPresent(s -> plugin.id = s);
                }
            }

            if ("apply".equals(node.name) && !node.arguments.isEmpty()) {
                resolveString(node.arguments.getFirst()).ifPresent(s -> {
                    plugin.apply = Boolean.parseBoolean(s);
                });

                if (node.receiver instanceof MethodCallNode idCall
                        && "id".equals(idCall.name())
                        && !idCall.arguments().isEmpty()) {
                    resolveString(idCall.arguments().getFirst()).ifPresent(v -> plugin.id = v);
                }
            }
        }

        return plugin;
    }
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
        return plugins != null ;
    }

    private DSLNode.Type classifyGradleApply(MethodCallExpression call) {
        if (hasApplyKey(call, "plugin")) return DSLNode.Type.PLUGIN_DECL;
        if (hasApplyKey(call, "from")) return DSLNode.Type.APPLY_SCRIPT;
        return DSLNode.Type.METHOD_CALL;
    }

    private boolean hasApplyKey(MethodCallExpression call, String expectedKey) {
        if (call == null || expectedKey == null) return false;
        String name = call.getMethodAsString();
        if (name == null && call.getMethod() != null) {
            name = call.getMethod().getText();
        }
        if (!"apply".equals(name)) return false;

        Expression args = call.getArguments();
        if (!(args instanceof TupleExpression tuple)) return false;

        for (Expression exp : tuple.getExpressions()) {
            if (exp instanceof NamedArgumentListExpression namedArgs
                    && hasMapKey(namedArgs.getMapEntryExpressions(), expectedKey)) {
                return true;
            }
            if (exp instanceof MapExpression map
                    && hasMapKey(map.getMapEntryExpressions(), expectedKey)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasMapKey(List<MapEntryExpression> entries, String expectedKey) {
        for (MapEntryExpression entry : entries) {
            String key = entry.getKeyExpression() == null
                    ? ""
                    : entry.getKeyExpression().getText();
            if (expectedKey.equals(key)) return true;
        }
        return false;
    }

    private boolean isPluginDeclarationCall(MethodCallExpression call) {
        if (!insidePluginApplicationBlock() || call == null) return false;

        String name = call.getMethodAsString();
        if (name == null && call.getMethod() != null) {
            name = call.getMethod().getText();
        }

        if ("id".equals(name)) return true;

        return containsPluginIdCall(call.getObjectExpression());
    }

    private boolean containsPluginIdCall(Expression expr) {
        if (expr instanceof MethodCallExpression m) {
            String name = m.getMethodAsString();
            if (name == null && m.getMethod() != null) {
                name = m.getMethod().getText();
            }

            if ("id".equals(name)) return true;
            return containsPluginIdCall(m.getObjectExpression());
        }

        return false;
    }



    //Task Exraction//
    private boolean isTasksReceiver(Expression receiver) {
        if (receiver == null || receiver instanceof EmptyExpression) return false;

        if (receiver instanceof VariableExpression ve) {
            return "tasks".equals(ve.getName());
        }

        if (receiver instanceof PropertyExpression pe) {
            Expression property = pe.getProperty();
            return property != null && "tasks".equals(property.getText());
        }

        return "tasks".equals(receiver.getText());
    }
    private boolean isTaskRegistrationCall(MethodCallExpression call) {
        String methodName = call.getMethodAsString();
        if (methodName == null) return false;

        return "register".equals(methodName)
                && isTasksReceiver(call.getObjectExpression());
    }
    private boolean isTaskCreateCall(MethodCallExpression call) {
        String methodName = call.getMethodAsString();
        if (methodName == null) return false;

        return "create".equals(methodName)
                && isTasksReceiver(call.getObjectExpression());
    }
    private boolean isTaskDeclarationCall(MethodCallExpression call) {
        if (call == null) return false;

        if ("task".equals(call.getMethodAsString())) {
            return true;
        }
        if (taskNameFromOldTaskSyntax(call) != null) {
            return true;
        }

        if (call.getArguments() instanceof TupleExpression tuple) {
            for (Expression exp : tuple.getExpressions()) {
                if (exp instanceof MethodCallExpression nested
                        && "task".equals(nested.getMethodAsString())) {
                    return true;
                }
            }
        }

        return false;
    }
    private TaskModel extractTask(MethodCallExpression call, DSLNode node) {
        String methodName = call.getMethodAsString();
        String style = methodName;
        String taskName = null;
        String taskTypeName = null;

        Expression receiver = call.getObjectExpression();
        String receiverText = receiver == null ? null : receiver.getText();
        ExpressionNode receiverExpr = node.receiver;

        List<ExpressionNode> originalArgs =
                node.arguments == null ? List.of() : List.copyOf(node.arguments);
        List<ExpressionNode> remainingArgs = new ArrayList<>(originalArgs);
        String oldSyntaxName = taskNameFromOldTaskSyntax(call);
        if (oldSyntaxName != null && !oldSyntaxName.isBlank()) {
            taskName = oldSyntaxName.trim();
            style = "task";
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);

            node.name = taskName;

            return new TaskModel(taskName, null, taskTypeName, style, receiverExpr, remainingArgs);
        }
        if ("task".equals(methodName)) {
            style = "task";
            taskName = taskNameFromTaskCallArguments(call);

            // old syntax: task foo(...)
            if (taskName == null && !remainingArgs.isEmpty()) {
                ExpressionNode first = remainingArgs.get(0);

                if (first instanceof MethodCallNode innerCall) {
                    taskName = innerCall.name();
                    remainingArgs = new ArrayList<>(innerCall.arguments());
                } else if (first instanceof BinaryExpressionNode bin) {
                    ExpressionNode left = bin.left();
                    if (left instanceof MethodCallNode innerCall) {
                        taskName = innerCall.name();
                        remainingArgs = new ArrayList<>(innerCall.arguments());
                    }
                }
            }

            // normal syntax: task('Name', type: JavaExec)
            if (taskName == null) {
                for (ExpressionNode arg : remainingArgs) {
                    String candidate = extractTaskName(arg);
                    if (candidate != null && !candidate.isBlank()) {
                        taskName = candidate;
                        break;
                    }
                }
            }

            // remove only the argument that served as the task name
            if (taskName != null) {
                Iterator<ExpressionNode> it = remainingArgs.iterator();
                while (it.hasNext()) {
                    ExpressionNode arg = it.next();
                    String candidate = extractTaskName(arg);
                    if (Objects.equals(candidate, taskName)) {
                        it.remove();
                        break;
                    }
                }
            }

            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        }
        else if ("create".equals(methodName) && isTasksReceiver(receiver)){
            style = "tasks.create";

            for (ExpressionNode arg : remainingArgs) {
                String candidate = extractTaskName(arg);
                if (candidate != null && !candidate.isBlank()) {
                    taskName = candidate;
                    break;
                }
            }

            if (taskName != null) {
                Iterator<ExpressionNode> it = remainingArgs.iterator();
                while (it.hasNext()) {
                    ExpressionNode arg = it.next();
                    String candidate = extractTaskName(arg);
                    if (Objects.equals(candidate, taskName)) {
                        it.remove();
                        break;
                    }
                }
            }

            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        }
        else if ("register".equals(methodName) && isTasksReceiver(receiver)) {
            style = "tasks.register";

            for (ExpressionNode arg : remainingArgs) {
                String candidate = extractTaskName(arg);
                if (candidate != null && !candidate.isBlank()) {
                    taskName = candidate;
                    break;
                }
            }

            if (taskName != null) {
                Iterator<ExpressionNode> it = remainingArgs.iterator();
                while (it.hasNext()) {
                    ExpressionNode arg = it.next();
                    String candidate = extractTaskName(arg);
                    if (Objects.equals(candidate, taskName)) {
                        it.remove();
                        break;
                    }
                }
            }

            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        }else if (("named".equals(methodName) || "getByName".equals(methodName))
                && isTasksReceiver(receiver)) {
            style = "named".equals(methodName) ? "tasks.named" : "tasks.getByName";

            for (ExpressionNode arg : remainingArgs) {
                String candidate = extractTaskName(arg);
                if (candidate != null && !candidate.isBlank()) {
                    taskName = candidate;
                    break;
                }
            }

            if (taskName != null) {
                Iterator<ExpressionNode> it = remainingArgs.iterator();
                while (it.hasNext()) {
                    ExpressionNode arg = it.next();
                    String candidate = extractTaskName(arg);
                    if (Objects.equals(candidate, taskName)) {
                        it.remove();
                        break;
                    }
                }
            }

            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        }
        else if (isBulkTaskConfigurationMethod(methodName) && isTasksReceiver(receiver)) {
            style = "tasks." + methodName;
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        }
        else if ("withType".equals(methodName) && isTasksReceiver(receiver)) {
            style = "tasks.withType";
            taskTypeName = extractTaskTypeFromArgs(remainingArgs);
        }
        else if (isBulkTaskConfigurationMethod(methodName)
                && receiver instanceof MethodCallExpression receiverCall
                && "withType".equals(receiverCall.getMethodAsString())
                && isTasksReceiver(receiverCall.getObjectExpression())) {
            style = "tasks.withType." + methodName;
            List<ExpressionNode> receiverArgs = extractArgumentsList(receiverCall);
            taskTypeName = extractTaskTypeFromArgs(receiverArgs);
            if (taskTypeName == null && !receiverArgs.isEmpty()) {
                taskTypeName = expressionTypeName(receiverArgs.get(0));
            }
        }



        if ((taskName == null || taskName.isBlank())
                && !isBulkTaskConfigurationStyle(style)
                && node.name != null
                && !"task".equals(node.name)) {
            taskName = node.name;
        }
        if (taskName != null && !taskName.isBlank()) {
            node.name = taskName;
        }


        return new TaskModel(taskName, null, taskTypeName, style, receiverExpr, remainingArgs);
    }
    private boolean isTaskConfigurationCall(MethodCallExpression call) {
        String methodName = call.getMethodAsString();
        Expression receiver = call.getObjectExpression();

        if (("named".equals(methodName) || "getByName".equals(methodName))
                && isTasksReceiver(receiver)) {
            return true;
        }

        if (isBulkTaskConfigurationMethod(methodName) && isTasksReceiver(receiver)) {
            return true;
        }

        if ("withType".equals(methodName)
                && isTasksReceiver(receiver)
                && extractClosure(call) != null) {
            return true;
        }

        return isBulkTaskConfigurationMethod(methodName)
                && receiver instanceof MethodCallExpression receiverCall
                && "withType".equals(receiverCall.getMethodAsString())
                && isTasksReceiver(receiverCall.getObjectExpression());
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
    private String taskNameFromOldTaskSyntax(MethodCallExpression call) {
        if (call == null) return null;

        String methodName = call.getMethodAsString();
        Expression receiver = call.getObjectExpression();

        if (methodName == null || methodName.isBlank()) return null;
        if (receiver == null || receiver instanceof EmptyExpression) return null;

        if ("task".equals(receiver.getText()) && !"task".equals(methodName)) {
            return methodName.trim();
        }

        return null;
    }
    private String taskNameFromTaskCallArguments(MethodCallExpression call) {
        if (call == null || !"task".equals(call.getMethodAsString())) return null;

        Expression args = call.getArguments();
        if (!(args instanceof TupleExpression tuple)) return null;

        for (Expression exp : tuple.getExpressions()) {
            if (exp instanceof MethodCallExpression nested) {
                String nestedName = nested.getMethodAsString();
                if (nestedName != null && !nestedName.isBlank() && !"task".equals(nestedName)) {
                    return nestedName.trim();
                }
            }

            if (exp instanceof BinaryExpression be && be.getLeftExpression() instanceof MethodCallExpression nested) {
                String nestedName = nested.getMethodAsString();
                if (nestedName != null && !nestedName.isBlank() && !"task".equals(nestedName)) {
                    return nestedName.trim();
                }
            }
        }

        return null;
    }
    private boolean insideTaskLike() {
        for (DSLNode n : stack) {
            if (n.type == DSLNode.Type.TASK_DECLARATION
                    || n.type == DSLNode.Type.TASK_CONFIGURATION) {
                return true;
            }
        }
        return false;
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
