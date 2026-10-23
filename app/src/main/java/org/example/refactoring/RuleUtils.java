package org.example.refactoring;

import org.example.matcher.MatchPair;
import org.example.model.*;
import org.example.refactoring.dependency.DependencyUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.matcher.NodeConditions.*;
import static org.example.matcher.NodeFormat.unwrapCasts;

public final class RuleUtils {
    public record ExprSlot(String key, ExpressionNode expr) {}
    private enum FileNature {
        BUILD_SCRIPT,
        SETTINGS_SCRIPT,
        INIT_SCRIPT,
        PROPERTIES,
        UNKNOWN
    }
    public static final Pattern GSTRING_REF_PATTERN = Pattern.compile("\\$\\{([^}]+)}|\\$([A-Za-z_][A-Za-z0-9_]*)");
    public static boolean isVariableNode(DSLNode n) {
        return n != null && (n.type == DSLNode.Type.ASSIGNMENT || n.type == DSLNode.Type.DECLARATION || n.type == DSLNode.Type.METHOD_CALL);
    }
    public static boolean isFile(DSLNode n) {
        return DslTreeUtils.isFile(n);
    }
    public static boolean isTaskDeclaration(DSLNode n) {
        return n != null && (
                n.type == DSLNode.Type.TASK_DECLARATION || n.type == DSLNode.Type.TASK_CONFIGURATION
        );
    }
    public static boolean isTaskConfiguration(DSLNode n) {
        return n != null && n.type == DSLNode.Type.TASK_CONFIGURATION;
    }
    public static boolean isTaskNode(DSLNode n) {
        return isTaskDeclaration(n) || isTaskConfiguration(n);
    }
    public static boolean isAtomicVariableEntity(DSLNode candidate, Collection<DSLNode> universe) {
        if (!isVariableEntity(candidate)) return false;
        for (DSLNode n : safeList(universe == null ? null : new ArrayList<>(universe))) {
            if (n == null || n == candidate) continue;
            if (!isVariableNode(n)) continue;
            if (!sameFile(candidate, n)) continue;
            if (isDescendantOf(n, candidate)) return false;
        }
        return true;
    }
    public static String expressionText(ExpressionNode expr) {
        if (expr == null) return "";

        if (expr instanceof VariableNode(String name)) {
            return safe(name);
        }

        if (expr instanceof ConstantNode(Object value)) {
            return safe(String.valueOf(value));
        }

        if (expr instanceof PropertyNode(ExpressionNode object1, ExpressionNode property1)) {
            String object = expressionText(object1);
            String property = expressionText(property1);

            if (!object.isBlank() && !property.isBlank()) {
                return object + "." + property;
            }
        }

        return safe(expr.contentKey());
    }
    public static Collection<DSLNode> safeCollection(Collection<DSLNode> nodes) {
        return nodes == null ? List.of() : nodes;
    }
    public static ExpressionNode extractInitializer(DSLNode variableNode) {
        if (variableNode == null) return null;
        if (variableNode.value != null) return variableNode.value;

        for (ExpressionNode arg : safeList(variableNode.arguments)) {
            if (arg instanceof DeclarationNode d && d.value() != null) return d.value();
            if (arg instanceof BinaryExpressionNode b && b.right() != null) return b.right();
        }
        return null;
    }
    public static boolean isDescendantOf(DSLNode node, DSLNode ancestor) {
        return DslTreeUtils.isDescendantOf(node, ancestor);
    }
    public static boolean sameFile(DSLNode a, DSLNode b) {
        return DslTreeUtils.sameFile(a, b);
    }
    public static MatchPair matchedPairForRightNode(DSLNode rightNode, RefactoringContext context) {
        if (rightNode == null || context == null || context.matchResult == null) return null;
        for (MatchPair mp : new ArrayList<>(context.matchResult.matches)) {
            if (mp != null && mp.right() == rightNode && mp.left() != null) return mp;
        }
        return null;
    }
    public static String nodeLocationName(DSLNode node) {
        return DslTreeUtils.nodeLocationName(node);
    }
    public static String safe(String s) {
        return s == null ? "" : s.trim();
    }
    public static String expressionReferenceText(ExpressionNode expr) {
        if (expr == null) return null;
        if (expr instanceof VariableNode(String name)) return name;
        if (expr instanceof PropertyNode(ExpressionNode object, ExpressionNode property)) {
            String l = expressionReferenceText(object);
            String r = expressionReferenceText(property);
            if (l == null || r == null) return null;
            return l + "." + r;
        }
        if (expr instanceof IndexAccessNode(ExpressionNode target, ExpressionNode key)) {
            String left = expressionReferenceText(target);
            String right = expressionReferenceText(key);
            if (left == null || right == null) return null;
            return left + "." + right;
        }
        if (expr instanceof SafeCallNode(ExpressionNode receiver, ExpressionNode selector)) {
            String left = expressionReferenceText(receiver);
            String right = expressionReferenceText(selector);
            if (left == null || right == null) return null;
            return left + "." + right;
        }

        if (expr instanceof DelegatedValueNode(ExpressionNode delegate)) {
            return expressionReferenceText(delegate);
        }


        if (expr instanceof ConstantNode(Object value)) return String.valueOf(value);
        return null;
    }
    public static List<ExprSlot> expressionSlots(DSLNode node) {
        if (node == null) return List.of();

        List<ExprSlot> out = new ArrayList<>();

        if (node.value != null) {
            out.add(new ExprSlot("value", node.value));
        }

        if (node.receiver != null) {
            out.add(new ExprSlot("receiver", node.receiver));
        }

        int i = 0;
        for (ExpressionNode arg : safeList(node.arguments)) {
            out.add(new ExprSlot("arg[" + i + "]", arg));
            i++;
        }

        for (Map.Entry<String, ExpressionNode> e : semanticExpressionSlots(node).entrySet()) {
            if (e.getValue() != null) {
                out.add(new ExprSlot("semantic:" + e.getKey(), e.getValue()));
            }
        }

        return out;
    }
    public static Map<String, ExpressionNode> semanticExpressionSlots(DSLNode node) {
        Map<String, ExpressionNode> out = new LinkedHashMap<>();
        if (node == null || node.semantic == null) return out;

        if (node.semantic instanceof DependencyModel d) {
            putExpr(out, "dependency.value", dependencyValueExpression(d));
            putLiteral(out, "dependency.configuration", d.configuration);
            putLiteral(out, "dependency.group", d.group);
            putLiteral(out, "dependency.name", d.name);
            putVersionLike(out, "dependency.version", d.version);
            putExpr(out, "dependency.notation", d.notation);
            putLiteral(out, "dependency.projectPath", d.projectPath);
            putReference(out, d.catalogAlias);
            putLiteral(out, "dependency.kind", d.kind == null ? null : String.valueOf(d.kind));
            return out;
        }

        if (node.semantic instanceof PluginModel p) {
            putLiteral(out, "plugin.id", p.id);
            putVersionLike(out, "plugin.version", p.version);
            putExpr(out, "plugin.applyTarget", p.applyTarget);
            return out;
        }

        if (node.semantic instanceof TaskModel(
                String taskName, String bindingName, String taskType, String syntaxStyle, ExpressionNode receiver,
                List<ExpressionNode> arguments
        )) {
            putLiteral(out, "task.taskName", taskName);
            putLiteral(out, "task.bindingName", bindingName);
            putLiteral(out, "task.taskType", taskType);
            putLiteral(out, "task.style", syntaxStyle);
            putExpr(out, "task.receiver", receiver);

            int i = 0;
            for (ExpressionNode a : safeList(arguments)) {
                putExpr(out, "task.arguments[" + i + "]", a);
                i++;
            }
            return out;
        }

        if (node.semantic instanceof ExpressionNode e) {
            out.put("semantic", e);
        }

        return out;
    }
    public static String normalizeReferenceName(String rawName) {
        return safe(rawName);
    }
    public static ExpressionNode dependencyValueExpression(DependencyModel d) {
        return DependencyUtils.dependencyValueExpression(d);
    }
    private static void putExpr(Map<String, ExpressionNode> out, String key, ExpressionNode value) {
        if (value != null) out.put(key, value);
    }
    private static void putLiteral(Map<String, ExpressionNode> out, String key, Object value) {
        if (value == null) return;
        out.put(key, new ConstantNode(String.valueOf(value)));
    }
    private static void putVersionLike(Map<String, ExpressionNode> out, String key, String value) {
        if (value == null || value.isBlank()) return;

        if (value.contains("$")) {
            out.put(key, new GStringNode(value));
        } else {
            out.put(key, new ConstantNode(value));
        }
    }
    private static void putReference(Map<String, ExpressionNode> out, String ref) {
        ExpressionNode expr = referenceExpression(ref);
        if (expr != null) out.put("dependency.catalogAlias", expr);
    }
    private static ExpressionNode referenceExpression(String ref) {
        if (ref == null || ref.isBlank()) return null;
        if (ref.contains(":") || ref.contains("$")) return null;

        String[] parts = ref.split("\\.");
        ExpressionNode expr = new VariableNode(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            expr = new PropertyNode(expr, new VariableNode(parts[i]));
        }

        return expr;
    }
    public static boolean isVariableEntity(DSLNode node) {
        return isVariableLikeBinding(node)
                && !safe(variableLikeName(node)).isBlank()
                && variableLikeValue(node) != null;
    }
    public static boolean sameLogicalVariable(String declaredName, String referencedName) {
        String a = norm(normalizeReferenceName(declaredName));
        String b = norm(normalizeReferenceName(referencedName));

        if (a.isBlank() || b.isBlank()) return false;
        if (a.equals(b)) return true;

        return b.endsWith("." + a) || a.endsWith("." + b);
    }
    private static ExpressionNode unwrapCallable(ExpressionNode expr) {
        if (expr instanceof ConstructorCallNode c && safeList(c.arguments()).size() == 1) {
            return c.arguments().getFirst();
        }

        if (expr instanceof MethodCallNode m && safeList(m.arguments()).size() == 1) {
            return m.arguments().getFirst();
        }

        return expr;
    }
    public static boolean equivalentModuloWrapper(ExpressionNode before, ExpressionNode after, Map<String, ExpressionNode> env) {
        return equivalentModuloWrapper(before, after, env, env);
    }
    public static boolean equivalentModuloWrapper(ExpressionNode before, ExpressionNode after, Map<String, ExpressionNode> beforeEnv, Map<String, ExpressionNode> afterEnv) {
        if (before == null || after == null) return false;
        ExpressionNode b = unwrapCasts(expandFully(before, beforeEnv));
        ExpressionNode a = unwrapCasts(expandFully(after, afterEnv));

        if (equivalent(b, a)
                || equivalentAfterExpansion(b, a)
                || equivalentPath(b, a, beforeEnv, afterEnv)) {
            return true;
        }

        ExpressionNode ub = unwrapCallable(b);
        ExpressionNode ua = unwrapCallable(a);

        return equivalent(ub, ua)
                || equivalentAfterExpansion(ub, ua)
                || equivalentPath(ub, ua, beforeEnv, afterEnv);
    }
    private static ExpressionNode expandAddedVariables(ExpressionNode expr, Map<String, ExpressionNode> env) {
        if (expr == null || env == null || env.isEmpty()) return expr;

        ExpressionNode current = expr;
        Set<String> seen = new HashSet<>();

        final int MAX_PASSES = 20;
        final int MAX_KEY_LENGTH = 100_000;

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String key = current.contentKey();

            if (key.length() > MAX_KEY_LENGTH) {
                return current;
            }

            if (!seen.add(key)) {
                return current;
            }

            ExpressionNode next = current;

            for (Map.Entry<String, ExpressionNode> e : env.entrySet()) {
                String varName = e.getKey();
                ExpressionNode replacement = e.getValue();

                if (replacement == null) continue;

                // Very important: avoid direct self-expansion
                if (usesInExpr(replacement, varName)) {
                    continue;
                }

                if (usesInExpr(next, varName)) {
                    next = replaceVariable(next, varName, replacement);
                }

                if (next == null) {
                    return current;
                }

                if (next.contentKey().length() > MAX_KEY_LENGTH) {
                    return current;
                }
            }

            String nextKey = next.contentKey();

            if (Objects.equals(nextKey, key)) {
                return current;
            }

            current = next;
        }

        return current;
    }
    public static ExpressionNode expandFully(ExpressionNode expr, Map<String, ExpressionNode> env) {
        ExpressionNode current = expr;
        Set<String> seen = new HashSet<>();

        while (true) {
            String key = current.contentKey();
            if (!seen.add(key)) return current;

            ExpressionNode next = expandAddedVariables(current, env);

            if (Objects.equals(next.contentKey(), current.contentKey())) {
                return current;
            }

            current = next;
        }
    }
    public static boolean usesInExpr(ExpressionNode expr, String name) {
        String target = norm(name);
        if (expr == null || target.isBlank()) return false;

        if (expr instanceof VariableNode(String name1)) {
            return sameLogicalVariable(name1, target);
        }
        if (expr instanceof PropertyNode p) {
            String full = expressionReferenceText(p);
            if (sameLogicalVariable(full, target)) return true;
        }
        if (expr instanceof GStringNode(String text)) {
            Matcher m = GSTRING_REF_PATTERN.matcher(text);

            while (m.find()) {
                String braced = m.group(1);
                String plain = m.group(2);
                String ref = normalizeEmbeddedReference(braced != null ? braced : plain);

                if (sameLogicalVariable(ref, target)) {
                    return true;
                }

            }
        }
        if (expr instanceof ConstantNode(Object v)) {
            if (v == null) return false;

            Matcher m = GSTRING_REF_PATTERN.matcher(String.valueOf(v));
            while (m.find()) {
                String braced = m.group(1);
                String plain = m.group(2);
                String ref = normalizeEmbeddedReference(braced != null ? braced : plain);

                if (sameLogicalVariable(ref, target)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof MethodCallNode m) {
            if (sameLogicalVariable(m.name(), target)) return true;
        }

        for (ExpressionNode c : childExpressions(expr)) {
            if (usesInExpr(c, target)) return true;
        }
        return false;
    }
    public static String normalizeEmbeddedReference(String ref) {
        if (ref == null) return null;

        String r = ref.trim();

        // ${isCiBuild()} -> isCiBuild
        r = r.replaceFirst("\\(\\s*\\)$", "");

        // ${rootProject.isCiBuild()} -> rootProject.isCiBuild
        r = r.replaceFirst("\\(\\s*\\)$", "");

        return r.trim();
    }
    public static boolean equivalent(ExpressionNode a, ExpressionNode b) {
        if (a == null || b == null) return false;
        if (Objects.equals(a, b)) return true;
        if (Objects.equals(a.contentKey(), b.contentKey())) return true;

        String ra = renderedExprText(a);
        String rb = renderedExprText(b);
        return ra != null && rb != null && Objects.equals(normLiteralText(ra), normLiteralText(rb));
    }
    private static String renderedExprText(ExpressionNode expr) {
        if (expr == null) return null;

        if (expr instanceof ConstantNode(Object v)) {
            return v == null ? null : String.valueOf(v);
        }
        if (expr instanceof GStringNode(String text)) {
            return text;
        }
        if (expr instanceof VariableNode(String name)) {
            return name;
        }

        return expr.contentKey();
    }
    private static boolean equivalentAfterExpansion(ExpressionNode beforeExpr, ExpressionNode expandedAfter) {
        String a = canonicalText(beforeExpr);
        String b = canonicalText(expandedAfter);

        return a != null && b != null && Objects.equals(a, b);
    }
    private static String canonicalText(ExpressionNode expr) {
        if (expr == null) return null;

        if (expr instanceof ConstantNode(Object v)) {
            return normalizeText(v == null ? "" : String.valueOf(v));
        }

        if (expr instanceof GStringNode(String text)) {
            return normalizeText(text);
        }

        if (expr instanceof VariableNode(String name)) {
            return normalizeText(name);
        }

        if (expr instanceof PropertyNode p) {
            String ref = expressionReferenceText(p);
            return ref == null ? null : normalizeText(ref);
        }

        if (expr instanceof BinaryExpressionNode b && "+".equals(b.operation())) {
            String left = canonicalText(b.left());
            String right = canonicalText(b.right());
            if (left == null || right == null) return null;
            return normalizeText(left + right);
        }

        if (expr instanceof MethodCallNode m) {
            List<String> args = new ArrayList<>();
            for (ExpressionNode arg : safeList(m.arguments())) {
                String ca = canonicalText(arg);
                if (ca == null) return null;
                args.add(ca);
            }
            return normalizeText(m.name() + "(" + String.join(",", args) + ")");
        }

        return null;
    }
    private static String normalizeText(String s) {
        if (s == null) return null;

        String t = s.trim();

        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\""))
                || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1);
        }

        return t
                .replace("\\", "/")
                .replaceAll("\\s+", "")
                .replaceAll("/+", "/")
                .toLowerCase(Locale.ROOT);
    }
    private static boolean equivalentPath(ExpressionNode beforeExpr, ExpressionNode expandedAfter, Map<String, ExpressionNode> beforeEnv, Map<String, ExpressionNode> afterEnv) {
        ExpressionNode before = unwrapTransparentWrapper(beforeExpr);
        ExpressionNode after = unwrapTransparentWrapper(expandedAfter);

        List<String> beforeParts = mergeFilenamePieces(structuralPathParts(before, beforeEnv));
        List<String> afterParts  = mergeFilenamePieces(structuralPathParts(after, afterEnv));

        if (beforeParts.isEmpty() || afterParts.isEmpty()) return false;
        if (beforeParts.size() != afterParts.size()) return false;

        for (int i = 0; i < beforeParts.size(); i++) {
            if (!Objects.equals(beforeParts.get(i), afterParts.get(i))) return false;
        }

        return true;
    }
    private static boolean isTransparentSingleArgumentWrapper(ExpressionNode expr) {
        return expr instanceof ConstructorCallNode c
                && safeList(c.arguments()).size() == 1;
    }
    private static ExpressionNode unwrapTransparentWrapper(ExpressionNode expr) {
        ExpressionNode cur = expr;

        while (isTransparentSingleArgumentWrapper(cur)) {
            ConstructorCallNode c = (ConstructorCallNode) cur;
            cur = c.arguments().getFirst();
        }


        return cur;
    }
    private static List<String> mergeFilenamePieces(List<String> parts) {
        if (parts == null || parts.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();

        for (String p : parts) {
            if (p == null || p.isBlank()) continue;

            if (p.startsWith(".") && !out.isEmpty()) {
                int last = out.size() - 1;
                out.set(last, out.get(last) + p);
            } else {
                out.add(p);
            }
        }

        return out;
    }
    public static List<BuildRefactoring> deduplicateByDescription(List<BuildRefactoring> refs) {
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (BuildRefactoring r : safeList(refs)) {
            if (r == null) continue;

            String key = r.type + "|" + r.description;

            BuildRefactoring existing = out.get(key);
            if (existing == null) {
                out.put(key, r);
                continue;
            }

            for (DSLNode n : safeList(r.removedNodes)) addIfAbsent(existing.removedNodes, n);
            for (DSLNode n : safeList(r.addedNodes)) addIfAbsent(existing.addedNodes, n);
            for (MatchPair p : safeList(r.relatedMatches)) {
                if (!existing.relatedMatches.contains(p)) existing.relatedMatches.add(p);
            }
        }

        return new ArrayList<>(out.values());
    }
    public static boolean sameConsumerRole(DSLNode before, DSLNode after) {
        if (before == null || after == null) return false;
        if (!compatibleTypes(before, after)) return false;
        if (!Objects.equals(norm(before.name), norm(after.name))) return false;

        DSLNode bp = before.parent;
        DSLNode ap = after.parent;

        if (bp == null && ap == null) return true;
        if (bp == null || ap == null) return false;
        if (bp.type != ap.type) return false;

        return Objects.equals(norm(bp.name), norm(ap.name));
    }
    public static boolean isConcreteInlineValue(ExpressionNode expr) {
        if (expr == null) return false;

        expr = unwrapCasts(expr);

        if (expr instanceof ConstantNode
                || expr instanceof GStringNode
                || expr instanceof ListNode
                || expr instanceof MapNode) {
            return true;
        }

        if (expr instanceof MethodCallNode m && safeList(m.arguments()).size() == 1) {
            return isConcreteInlineValue(m.arguments().getFirst());
        }

        if (expr instanceof ConstructorCallNode c && safeList(c.arguments()).size() == 1) {
            return isConcreteInlineValue(c.arguments().getFirst());
        }

        return false;
    }
    public static String norm(String s) {
        return safe(s).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
    public static String normalizePath(String path) {return DslTreeUtils.normalizePath(path);}
    public static String directoryOf(String path) {
        return DslTreeUtils.directoryOf(path);
    }
    public static List<String> pathSegments(String path) {
        return DslTreeUtils.pathSegments(path);
    }

    public static int directoryDepth(String path) {
        return DslTreeUtils.directoryDepth(path);
    }

    public static boolean isParentBuildFileOf(String parentPath, String childPath) {
        return DslTreeUtils.isParentBuildFileOf(parentPath, childPath);
    }

    public static boolean isPullUp(String sourcePath, String targetPath) {
        return DslTreeUtils.isPullUp(sourcePath, targetPath);
    }
    public static boolean isPushDown(String sourcePath, String targetPath) {
        return DslTreeUtils.isPushDown(sourcePath, targetPath);
    }
    public static boolean isMove(String sourcePath, String targetPath) {
        return DslTreeUtils.isMove(sourcePath, targetPath);
    }

    public static boolean isGradleFile(DSLNode n) {
        return DslTreeUtils.isGradleFile(n);
    }

    public static DSLNode fileRootOf(DSLNode n) {
        return DslTreeUtils.fileRootOf(n);
    }

    public static boolean sameLogicalConsumerFile(DSLNode before, DSLNode after, RefactoringContext context) {
        return DslTreeUtils.sameLogicalConsumerFile(before, after, context);
    }

    public static boolean isGradlePath(String path) {
        return DslTreeUtils.isGradlePath(path);
    }

    public static boolean isGradleNode(DSLNode node) {
        return DslTreeUtils.isGradleNode(node);
    }

    public static boolean isVariableLikeBinding(DSLNode n) {
        if (n == null) return false;

        return isVariableNode(n)
                || isPropertySetterCall(n);
    }

    private static boolean isPropertySetterCall(DSLNode n) {
        if (n == null || n.type != DSLNode.Type.METHOD_CALL) return false;
        if (safe(n.name).isBlank()) return false;

        // Gradle setter-like calls: versionName "1.0", minSdkVersion 21
        if (n.receiver != null) return false;
        if (safeList(n.arguments).size() != 1) return false;

        return n.value == null;
    }

    public static String variableLikeName(DSLNode node) {
        if (node == null) return "";

        if (isVariableNode(node)) {
            return normalizeReferenceName(node.name);
        }

        if (node.type == DSLNode.Type.METHOD_CALL) {
            return normalizeReferenceName(node.name);
        }

        return "";
    }

    public static ExpressionNode variableLikeValue(DSLNode node) {
        if (node == null) return null;

        if (node.type == DSLNode.Type.METHOD_CALL) {
            List<ExpressionNode> args = safeList(node.arguments);
            if (args.size() == 1) return args.getFirst();
            return node.value;
        }

        return extractInitializer(node);
    }

    public static boolean isDependency(DSLNode n) {
        return DependencyUtils.isDependency(n);
    }

    public static boolean isTask(DSLNode n) {
        return isTaskDeclaration(n);
    }

    public static boolean isMethod(DSLNode n) {
        return n != null && n.type == DSLNode.Type.METHOD_DECL;
    }

    public static boolean sameValue(DSLNode a, DSLNode b) {
        ExpressionNode av = variableLikeValue(a);
        ExpressionNode bv = variableLikeValue(b);

        if (av == bv) return true;
        if (av == null || bv == null) return false;

        return equivalentModuloWrapper(av, bv, Map.of());
    }

    public static boolean sameSemantic(DSLNode a, DSLNode b) {
        if (a == null || b == null) return false;
        if (a.semantic == b.semantic) return true;
        if (a.semantic == null || b.semantic == null) return false;
        return Objects.equals(String.valueOf(a.semantic), String.valueOf(b.semantic));
    }

    public static boolean sameVariableName(DSLNode a, DSLNode b) {
        return sameLogicalVariable(variableLikeName(a), variableLikeName(b));
    }

    public static boolean sameDependencyIdentity(DSLNode a, DSLNode b) {
        return DependencyUtils.sameDependencyIdentity(a, b);
    }

    public static boolean sameTask(DSLNode a, DSLNode b) {
        if (!isTask(a) || !isTask(b)) return false;
        if (!Objects.equals(norm(a.name), norm(b.name))) return false;
        return sameSemantic(a, b) || sameValue(a, b);
    }

    public static boolean sameMethod(DSLNode a, DSLNode b) {
        if (!isMethod(a) || !isMethod(b)) return false;
        if (!Objects.equals(norm(a.name), norm(b.name))) return false;
        return sameSemantic(a, b) || sameValue(a, b);
    }

    public static List<DSLNode> flatten(DSLNode root) {
        return DslTreeUtils.flatten(root);
    }

    public static boolean sameFileNature(DSLNode before, DSLNode after) {
        FileNature a = fileNature(before);
        FileNature b = fileNature(after);
        return a != FileNature.UNKNOWN && a == b;
    }

    private static FileNature fileNature(DSLNode node) {
        DSLNode file = fileRootOf(node);
        if (file == null) return FileNature.UNKNOWN;

        String path = normalizePath(file.relativePath).toLowerCase(Locale.ROOT);
        if (file.type == DSLNode.Type.PROPERTIES_FILE || path.endsWith(".properties")) return FileNature.PROPERTIES;
        if (file.type == DSLNode.Type.SETTINGS_GRADLE || isSettingsScriptPath(path)) return FileNature.SETTINGS_SCRIPT;
        if (file.type == DSLNode.Type.INIT_GRADLE || path.endsWith("init.gradle") || path.endsWith("init.gradle.kts")) {
            return FileNature.INIT_SCRIPT;
        }
        if (file.type == DSLNode.Type.BUILD_GRADLE || file.type == DSLNode.Type.BUILD_LOGIC_GRADLE || isGradlePath(path)) {
            return FileNature.BUILD_SCRIPT;
        }

        return FileNature.UNKNOWN;
    }

    private static boolean isSettingsScriptPath(String path) {
        String p = normalizePath(path).toLowerCase(Locale.ROOT);
        return p.equals("settings.gradle")
                || p.equals("settings.gradle.kts")
                || p.endsWith("/settings.gradle")
                || p.endsWith("/settings.gradle.kts")
                || p.endsWith(".settings.gradle")
                || p.endsWith(".settings.gradle.kts");
    }

    public static void walk(DSLNode node, List<DSLNode> out) {
        DslTreeUtils.walk(node, out);
    }

    public static boolean isMethodLikeAncestor(DSLNode node) {
        for (DSLNode cur = node; cur != null; cur = cur.parent) {
            if (cur.type == DSLNode.Type.METHOD_DECL
                    || cur.type == DSLNode.Type.CLOSURE
                    || cur.type == DSLNode.Type.TASK_DECLARATION) {
                return true;
            }
        }
        return false;
    }

    public static int depth(DSLNode node) {
        return DslTreeUtils.depth(node);
    }

    public static void addIfAbsent(List<DSLNode> nodes, DSLNode node) {
        DslTreeUtils.addIfAbsent(nodes, node);
    }

    public static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    public static List<BuildRefactoring> deduplicate(List<BuildRefactoring> refs) {
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();
        for (BuildRefactoring r : refs) {
            String key = r.type + "|" + r.description + "|" + stableNodeKey(r.removedNodes) + "|" + stableNodeKey(r.addedNodes);
            out.putIfAbsent(key, r);
        }
        return new ArrayList<>(out.values());
    }

    private static String stableNodeKey(List<DSLNode> nodes) {
        List<String> keys = new ArrayList<>();
        for (DSLNode n : safeList(nodes)) {
            keys.add(nodeKey(n));
        }
        Collections.sort(keys);
        return String.join(";", keys);
    }

    public static String nodeKey(DSLNode n) {
        return DslTreeUtils.nodeKey(n);
    }

    private static String normLiteralText(String s) {
        if (s == null) return null;
        String t = s.trim();

        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1);
        }

        return t;
    }

    private static boolean directValueReference(ExpressionNode expr, String var) {
        expr = unwrapCasts(expr);
        if (expr == null || var == null || var.isBlank()) return false;

        // x
        if (expr instanceof VariableNode(String name)) {
            return sameLogicalVariable(name, var);
        }

        // rootProject.ext.x or ext.x can still be the value reference
        if (expr instanceof PropertyNode p) {
            String full = expressionReferenceText(p);
            return sameLogicalVariable(full, var);
        }

        // "abc:$x" or "${x}"
        if (expr instanceof GStringNode || expr instanceof ConstantNode) {
            return usesInExpr(expr, var);
        }

        // "abc:" + x is okay only if x appears as a value piece,
        // not as the receiver of a lookup/call.
        if (expr instanceof BinaryExpressionNode b && "+".equals(b.operation())) {
            return directValueReference(b.left(), var)
                    || directValueReference(b.right(), var);
        }

        // f(x) can be okay, because x is an argument value.
        // x.f(...) is NOT okay, because x is just a receiver/container.
        if (expr instanceof MethodCallNode m) {
            for (ExpressionNode arg : safeList(m.arguments())) {
                if (directValueReference(arg, var)) return true;
            }
            return false;
        }

        return false;
    }

    public static ExpressionNode replaceVariable(ExpressionNode expr, String varName, ExpressionNode initializer) {
        String target = normalizeReferenceName(varName);
        if (expr == null || safe(target).isBlank() || initializer == null) return expr;

        if (matchesReference(expr, target)) {
            return initializer;
        }
        if (expr instanceof ConstantNode(Object v)) {
            if (v == null) return expr;

            String text = String.valueOf(v);
            String replaced = replaceInGStringText(text, target, initializer);

            if (Objects.equals(replaced, text)) return expr;
            return replaced.contains("$") ? new GStringNode(replaced) : new ConstantNode(replaced);
        }
        if (expr instanceof GStringNode(String text)) {
            String replaced = replaceInGStringText(text, target, initializer);
            if (Objects.equals(replaced, text)) return expr;
            return replaced.contains("$") ? new GStringNode(replaced) : new ConstantNode(replaced);
        }

        if (expr instanceof PropertyNode(ExpressionNode object, ExpressionNode property)) {
            ExpressionNode o = replaceVariable(object, target, initializer);
            ExpressionNode pr = replaceVariable(property, target, initializer);
            return (o == object && pr == property) ? expr : new PropertyNode(o, pr);
        }
        if (expr instanceof MethodCallNode(
                ExpressionNode target1, String name, List<ExpressionNode> arguments, List<String> typeArguments
        )) {
            ExpressionNode t = replaceVariable(target1, target, initializer);
            List<ExpressionNode> args = new ArrayList<>();
            boolean changed = t != target1;
            for (ExpressionNode a : safeList(arguments)) {
                ExpressionNode na = replaceVariable(a, target, initializer);
                args.add(na);
                if (na != a) changed = true;
            }
            return changed ? new MethodCallNode(t, name, args, typeArguments) : expr;
        }
        if (expr instanceof BinaryExpressionNode b) {
            ExpressionNode l = replaceVariable(b.left(), target, initializer);
            ExpressionNode r = replaceVariable(b.right(), target, initializer);
            return (l == b.left() && r == b.right()) ? expr : new BinaryExpressionNode(l, b.operation(), r);
        }
        if (expr instanceof DeclarationNode d) {
            ExpressionNode v = replaceVariable(d.value(), target, initializer);
            return v == d.value() ? expr : new DeclarationNode(d.name(), v);
        }
        if (expr instanceof MapNode(Map<String, ExpressionNode> entries)) {
            Map<String, ExpressionNode> out = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<String, ExpressionNode> e : entries.entrySet()) {
                ExpressionNode nv = replaceVariable(e.getValue(), target, initializer);
                out.put(e.getKey(), nv);
                if (nv != e.getValue()) changed = true;
            }
            return changed ? new MapNode(out) : expr;
        }
        if (expr instanceof ListNode(List<ExpressionNode> values)) {
            List<ExpressionNode> out = new ArrayList<>();
            boolean changed = false;
            for (ExpressionNode v : safeList(values)) {
                ExpressionNode nv = replaceVariable(v, target, initializer);
                out.add(nv);
                if (nv != v) changed = true;
            }
            return changed ? new ListNode(out) : expr;
        }
        if (expr instanceof TupleNode(List<ExpressionNode> values)) {
            List<ExpressionNode> out = new ArrayList<>();
            boolean changed = false;
            for (ExpressionNode v : safeList(values)) {
                ExpressionNode nv = replaceVariable(v, target, initializer);
                out.add(nv);
                if (nv != v) changed = true;
            }
            return changed ? new TupleNode(out) : expr;
        }
        if (expr instanceof ClosureNode(List<String> parameters, BlockNode body1)) {
            ExpressionNode body = replaceVariable(body1, target, initializer);
            if (body == body1) return expr;
            if (body instanceof BlockNode bn) return new ClosureNode(parameters, bn);
            return expr;
        }
        if (expr instanceof BlockNode(List<ExpressionNode> statements)) {
            List<ExpressionNode> out = new ArrayList<>();
            boolean changed = false;
            for (ExpressionNode s : safeList(statements)) {
                ExpressionNode ns = replaceVariable(s, target, initializer);
                out.add(ns);
                if (ns != s) changed = true;
            }
            return changed ? new BlockNode(out) : expr;
        }
        if (expr instanceof TernaryExpressionNode(
                ExpressionNode condition, ExpressionNode trueExpression, ExpressionNode falseExpression
        )) {
            ExpressionNode c = replaceVariable(condition, target, initializer);
            ExpressionNode tr = replaceVariable(trueExpression, target, initializer);
            ExpressionNode fl = replaceVariable(falseExpression, target, initializer);
            if (c == condition && tr == trueExpression && fl == falseExpression) return expr;
            return new TernaryExpressionNode(c, tr, fl);
        }
        if (expr instanceof ThrowNode t) {
            ExpressionNode e = replaceVariable(t.expression(), target, initializer);
            return e == t.expression() ? expr : new ThrowNode(e);
        }
        if (expr instanceof ConstructorCallNode c) {
            List<ExpressionNode> out = new ArrayList<>();
            boolean changed = false;
            for (ExpressionNode a : safeList(c.arguments())) {
                ExpressionNode na = replaceVariable(a, target, initializer);
                out.add(na);
                if (na != a) changed = true;
            }
            return changed ? new ConstructorCallNode(c.typeName(), out) : expr;
        }

        return expr;
    }

    private static boolean matchesReference(ExpressionNode expr, String target) {
        if (expr == null || safe(target).isBlank()) return false;

        if (expr instanceof VariableNode(String name)) {
            return sameLogicalVariable(name, target);
        }

        if (expr instanceof PropertyNode p) {
            String full = expressionReferenceText(p);
            return full != null && sameLogicalVariable(full, target);
        }

        return false;
    }

    public static List<ExpressionNode> childExpressions(ExpressionNode expr) {
        if (expr == null) return List.of();

        if (expr instanceof PropertyNode(
                ExpressionNode object, ExpressionNode property
        )) return listOfNonNull(object, property);
        if (expr instanceof MethodCallNode m) {
            List<ExpressionNode> out = new ArrayList<>();
            if (m.target() != null) out.add(m.target());
            out.addAll(safeList(m.arguments()));
            return out;
        }
        if (expr instanceof BinaryExpressionNode b) return listOfNonNull(b.left(), b.right());
        if (expr instanceof DeclarationNode d) return listOfNonNull(d.value());
        if (expr instanceof MapNode(Map<String, ExpressionNode> entries)) return new ArrayList<>(entries.values());
        if (expr instanceof ListNode(List<ExpressionNode> values1)) return safeList(values1);
        if (expr instanceof TupleNode(List<ExpressionNode> values)) return safeList(values);
        if (expr instanceof ClosureNode c) return listOfNonNull(c.body());
        if (expr instanceof BlockNode(List<ExpressionNode> statements)) return safeList(statements);
        if (expr instanceof DelegatedValueNode(ExpressionNode delegate)) {
            return listOfNonNull(delegate);
        }
        if (expr instanceof TernaryExpressionNode(
                ExpressionNode condition, ExpressionNode trueExpression, ExpressionNode falseExpression
        ))
            return listOfNonNull(condition, trueExpression, falseExpression);
        if (expr instanceof ThrowNode t) return listOfNonNull(t.expression());
        if (expr instanceof ConstructorCallNode c) return safeList(c.arguments());

        return List.of();
    }

    private static List<ExpressionNode> listOfNonNull(ExpressionNode... nodes) {
        List<ExpressionNode> out = new ArrayList<>();
        for (ExpressionNode n : nodes) {
            if (n != null) out.add(n);
        }
        return out;
    }

    public static Map<String, ExpressionNode> extractionEnvironment(DSLNode added, RefactoringContext context) {
        Map<String, ExpressionNode> env = new LinkedHashMap<>();

        for (DSLNode n : context.matchResult.addedLeaves) {
            if (!sameFile(n, added)) continue;
            if (!isVariableEntity(n)) continue;

            String name = normalizeReferenceName(n.name);
            ExpressionNode init = extractInitializer(n);

            if (!safe(name).isBlank() && init != null) {
                env.put(norm(name), init);
            }
        }

        for (DSLNode n : context.matchResult.addedComposite) {
            if (!sameFile(n, added)) continue;
            if (!isVariableEntity(n)) continue;

            String name = normalizeReferenceName(n.name);
            ExpressionNode init = extractInitializer(n);

            if (!safe(name).isBlank() && init != null) {
                env.put(norm(name), init);
            }
        }

        return env;
    }

    private static boolean valuesEquivalentToAddedVariable(ExpressionNode beforeExpr, ExpressionNode initializer, Map<String, ExpressionNode> env) {
        ExpressionNode before = unwrapCasts(expandFully(beforeExpr, env));
        ExpressionNode init = unwrapCasts(expandFully(initializer, env));

        return equivalentModuloWrapper(before, init, env);
    }

    private static boolean usesVariableAsLookupKey(ExpressionNode expr, String var) {
        expr = unwrapCasts(expr);
        if (expr == null || var == null || var.isBlank()) return false;

        if (expr instanceof MethodCallNode m) {
            for (ExpressionNode arg : safeList(m.arguments())) {
                if (arg instanceof ConstantNode(Object value)) {
                    if (sameLogicalVariable(String.valueOf(value), var)) return true;
                }
                if (arg instanceof VariableNode(String name)) {
                    if (sameLogicalVariable(name, var)) return true;
                }
                if (arg instanceof PropertyNode p) {
                    String full = expressionReferenceText(p);
                    if (sameLogicalVariable(full, var)) return true;
                }
            }
            return false;
        }

        if (expr instanceof PropertyNode p) {
            String full = expressionReferenceText(p);
            return sameLogicalVariable(full, var);
        }

        return false;
    }

    public static boolean replacedByVariableInAlignedSlots(DSLNode before, DSLNode after, ExpressionNode initializer, String variableName, DSLNode added, RefactoringContext context, boolean rejectRemovedMethodSource) {
        String var = normalizeReferenceName(variableName);
        if (before == null || after == null || initializer == null || safe(var).isBlank()) {
            return false;
        }

        List<ExprSlot> beforeSlots = expressionSlots(before);
        List<ExprSlot> afterSlots = expressionSlots(after);

        Map<String, ExpressionNode> env = extractionEnvironment(added, context);
        env.putAll(matchedBeforeAliases(context));
        ExpressionNode normalizedInitializer = unwrapCallable(unwrapCasts(initializer));

        for (ExprSlot bs : beforeSlots) {
            for (ExprSlot as : afterSlots) {
                if (!Objects.equals(bs.key(), as.key())) continue;
                ExpressionNode rawAfter = as.expr();
                ExpressionNode normalizedAfter = unwrapCasts(rawAfter);
                ExprSlot normalizedAs = new ExprSlot(as.key(), normalizedAfter);
                boolean directUse =
                        usesInExpr(normalizedAfter, var) || usesInExpr(rawAfter, var);

                boolean lookupKeyUse =
                        usesVariableAsLookupKey(normalizedAfter, var)
                                || usesVariableAsLookupKey(rawAfter, var);

                boolean beforeDirectUse =
                        usesInExpr(bs.expr(), var);

                boolean beforeLookupKeyUse =
                        usesVariableAsLookupKey(bs.expr(), var);

                if (beforeDirectUse || beforeLookupKeyUse) {
                    continue;
                }

                if (!directUse && !lookupKeyUse) continue;
                if (rejectRemovedMethodSource && !concreteSourceSlot(bs.expr())) continue;

                if (structuralExternalizationSlot(before, after, bs, normalizedAs, var)) {
                    return true;
                }

                ExpressionNode expandedAfter = replaceVariable(normalizedAfter, var, normalizedInitializer);
                expandedAfter = expandFully(expandedAfter, env);
                expandedAfter = unwrapCasts(expandedAfter);

                ExpressionNode expandedBefore = expandFully(bs.expr(), env);
                expandedBefore = unwrapCasts(expandedBefore);
                if (lookupKeyUse && valuesEquivalentToAddedVariable(expandedBefore, normalizedInitializer, env)) {
                    return true;
                }

                if (equivalentModuloWrapper(expandedBefore, expandedAfter, env)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean structuralExternalizationSlot(DSLNode before, DSLNode after, ExprSlot bs, ExprSlot as, String var) {
        if (before == null || after == null || bs == null || as == null) return false;

        if (!sameConsumerRole(before, after)) return false;
        if (!concreteSourceSlot(bs.expr())) return false;
        if (!directValueReference(as.expr(), var)) return false;
        return dependencyArtifactRolePreserved(before, after);
    }

    private static boolean concreteSourceSlot(ExpressionNode e) {
        if (e == null) return false;

        if (e instanceof ConstantNode) return true;
        return e instanceof GStringNode;
    }

    private static boolean dependencyArtifactRolePreserved(DSLNode before, DSLNode after) {
        return DependencyUtils.dependencyArtifactRolePreserved(before, after);
    }

    public static Map<String, ExpressionNode> matchedBeforeAliases(RefactoringContext context) {
        Map<String, ExpressionNode> env = new LinkedHashMap<>();
        if (context == null || context.matchResult == null) return env;

        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.left() == null) continue;
            if (!isVariableEntity(p.left())) continue;

            String name = normalizeReferenceName(p.left().name);
            ExpressionNode init = extractInitializer(p.left());

            if (!safe(name).isBlank() && init != null) {
                env.put(norm(name), unwrapCallable(init));
            }
        }

        return env;
    }

    private static List<String> structuralPathParts(ExpressionNode expr, Map<String, ExpressionNode> env) {
        if (expr == null) return List.of();

        if (expr instanceof ConstantNode(Object v)) {
            if (v == null) return List.of();

            String raw = String.valueOf(v).trim();

            if (raw.length() >= 2
                    && ((raw.startsWith("\"") && raw.endsWith("\"")) ||
                    (raw.startsWith("'") && raw.endsWith("'")))) {
                raw = raw.substring(1, raw.length() - 1);
            }

            return splitLiteralPath(raw);
        }

        if (expr instanceof GStringNode(String text)) {
            return splitLiteralPath(text);
        }

        if (expr instanceof VariableNode(String name1)) {
            String name = norm(name1);

            if (env != null) {
                ExpressionNode resolved = env.get(name);
                if (resolved != null && resolved != expr) {
                    return structuralPathParts(resolved, env);
                }
            }

            return List.of(name1);
        }

        if (expr instanceof PropertyNode p) {
            String ref = expressionReferenceText(p);
            return ref == null ? List.of() : List.of(ref);
        }

        if (expr instanceof BinaryExpressionNode b && "+".equals(b.operation())) {
            List<String> out = new ArrayList<>();
            out.addAll(structuralPathParts(b.left(), env));
            out.addAll(structuralPathParts(b.right(), env));
            return mergeFilenamePieces(out);
        }

        if (expr instanceof ConstructorCallNode c) {
            if (safeList(c.arguments()).size() == 1) {
                return structuralPathParts(c.arguments().getFirst(), env);
            }

            List<String> out = new ArrayList<>();
            for (ExpressionNode arg : safeList(c.arguments())) {
                out.addAll(structuralPathParts(arg, env));
            }
            return out;
        }

        if (expr instanceof MethodCallNode m) {
            if (safeList(m.arguments()).size() == 1) {
                return structuralPathParts(m.arguments().getFirst(), env);
            }

            List<String> out = new ArrayList<>();
            for (ExpressionNode arg : safeList(m.arguments())) {
                out.addAll(structuralPathParts(arg, env));
            }
            return out;
        }

        return List.of();
    }

    private static List<String> splitLiteralPath(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        String t = raw.trim().replace("\\", "/");

        List<String> out = new ArrayList<>();
        for (String part : t.split("/")) {
            String p = part.trim();
            if (p.isBlank()) continue;

            if (p.equals(".")) continue;
            if (p.startsWith("$")) continue;

            out.add(p);
        }

        return out;
    }

    public static boolean sameExpression(ExpressionNode a, ExpressionNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.contentKey(), b.contentKey());
    }


    public static DSLNode findReferenceInSubtree(DSLNode node, java.util.function.Predicate<DSLNode> pred, DSLNode excludeSubtreeRoot) {
        if (node == null) return null;

        if (!(sameFile(node, excludeSubtreeRoot) && isDescendantOf(node, excludeSubtreeRoot))) {
            if (pred.test(node)) return node;
        }

        for (DSLNode c : safeList(node.children)) {
            DSLNode hit = findReferenceInSubtree(c, pred, excludeSubtreeRoot);
            if (hit != null) return hit;
        }
        return null;
    }

    private static String initializerLiteral(ExpressionNode initializer) {
        if (initializer == null) return "";
        if (initializer instanceof ConstantNode(Object v)) {
            return v == null ? "" : String.valueOf(v);
        }
        if (initializer instanceof GStringNode(String text)) {
            return safe(text);
        }
        String ref = expressionReferenceText(initializer);
        if (ref != null) return ref;
        return safe(initializer.contentKey());
    }
    private static String replaceInGStringText(String text, String target, ExpressionNode initializer) {
        if (text == null || text.isBlank() || safe(target).isBlank() || initializer == null) return text;

        String replacement = initializerLiteral(initializer);
        Matcher m = GSTRING_REF_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (m.find()) {
            String braced = m.group(1);
            String plain = m.group(2);
            String ref = braced != null ? braced : plain;

            if (sameLogicalVariable(ref, target)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
    public static boolean isDeletedSourceFile(DSLNode node, RefactoringContext context) {
        if (node == null || context == null) return false;

        DSLNode file = fileRootOf(node);
        if (file == null) return false;

        String path = normalizePath(file.relativePath);
        if (path.isBlank()) return false;

        if (!context.oldFilePaths().contains(path)) return false;
        if (context.newFilePaths().contains(path)) return false;

        return !hasMatchedAfterFile(file, context);
    }
    private static boolean hasMatchedAfterFile(DSLNode oldFile, RefactoringContext context) {
        if (oldFile == null || context == null || context.matchResult == null) return false;

        for (MatchPair pair : context.matchResult.matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;

            DSLNode leftFile = fileRootOf(pair.left());
            DSLNode rightFile = fileRootOf(pair.right());

            if (leftFile == oldFile && rightFile != null) {
                return true;
            }
        }

        return false;
    }
    public static boolean isExternalBinding(DSLNode node) {
        DSLNode file = fileRootOf(node);
        if (file == null) return false;
        if (file.type == DSLNode.Type.PROPERTIES_FILE) return true;
        return isGradleFile(file) && isPureBindingFile(file);
    }
    public static boolean isPureBindingFile(DSLNode root) {
        if (!isGradleFile(root)) return false;

        for (DSLNode child : safeList(root.children)) {
            if (!isPureBindingNode(child)) return false;
        }
        return true;
    }
    private static boolean isPureBindingNode(DSLNode node) {
        if (node == null) return false;

        if (node.type == DSLNode.Type.ASSIGNMENT || node.type == DSLNode.Type.DECLARATION) {
            return true;
        }

        if (node.type == DSLNode.Type.BLOCK && "ext".equals(norm(node.name))) {
            for (DSLNode child : safeList(node.children)) {
                if (!isPureBindingNode(child)) return false;
            }
            return true;
        }

        return false;
    }

}
