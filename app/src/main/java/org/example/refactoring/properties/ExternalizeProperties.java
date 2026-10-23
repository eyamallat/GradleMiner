package org.example.refactoring.properties;

import org.example.matcher.MatchPair;
import org.example.matcher.MatchResult;
import org.example.model.*;
import org.example.refactoring.*;
import org.example.refactoring.RuleUtils.ExprSlot;

import java.util.*;
import java.util.regex.Pattern;

import static org.example.matcher.NodeFormat.unwrapCasts;
import static org.example.refactoring.RuleUtils.*;

public class ExternalizeProperties implements RefactoringRule {

    private static final Pattern SIMPLE_REFERENCE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final Pattern BRACED_GSTRING_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SIMPLE_GSTRING_REFERENCE = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_.]*)");

    private record ExternalizationReplacement(ExpressionNode oldValue, String symbol) {}

    private record ProviderInfo(
            String key,
            String qualifiedKey,
            ExpressionNode value,
            String file,
            DSLNode node
    ) {}

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        if (context == null || context.matchResult == null) return List.of();

        MatchResult match = context.matchResult;
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        Map<String, ProviderInfo> addedProviders = visibleAddedProviders(context);
        Map<String, ProviderInfo> removedProviders = visibleRemovedProviders(context);
        Set<String> visibleNonExternalSymbols = visibleNonExternalSymbols(context);

        detectNewExternalProviders(context, match, out);
        detectMovedBuildVariablesToExternalProviders(context, addedProviders, out);
        detectConsumerReplacements(context, match, addedProviders, removedProviders, visibleNonExternalSymbols, out);

        for (BuildRefactoring ref : out.values()) {
            dedupeNodesInRef(ref);
            dedupeMatchesInRef(ref);
        }

        return deduplicateByDescription(new ArrayList<>(out.values()));
    }

    private static void detectNewExternalProviders(
            RefactoringContext context,
            MatchResult match,
            Map<String, BuildRefactoring> out
    ) {
        for (DSLNode added : externalBindingCandidates(match)) {
            ProviderInfo provider = providerInfo(added);
            if (provider == null) continue;
            if (providerKeyExistedInOldExternalFile(context.oldRoots, provider)) continue;

            for (MatchPair pair : match.matches) {
                if (pair == null || pair.left() == null || pair.right() == null) continue;
                if (isDeletedSourceFile(pair.left(), context)) continue;
                if (!isConsumerCandidate(pair.left(), pair.right(), provider.node)) continue;
                if (!isExternalizationEvidence(pair.left(), pair.right(), provider, context)) continue;

                emitExternalization(out, provider.key, provider.node, pair);
            }
        }
    }

    private static void detectConsumerReplacements(RefactoringContext context, MatchResult match, Map<String, ProviderInfo> addedProviders,
            Map<String, ProviderInfo> removedProviders, Set<String> visibleNonExternalSymbols, Map<String, BuildRefactoring> out) {

        Map<String, ProviderInfo> allProviders = new LinkedHashMap<>();
        allProviders.putAll(removedProviders);
        allProviders.putAll(addedProviders);

        for (MatchPair pair : match.matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (isDeletedSourceFile(pair.left(), context)) continue;

            ExternalizationReplacement replacement = slotExternalization(pair.left(), pair.right(), allProviders);
            if (replacement == null) {
                replacement = literalToExternalReferenceReplacement(pair.left(), pair.right());
            }
            if (replacement == null) continue;

            ProviderInfo added = addedProviders.get(norm(replacement.symbol));
            if (added != null && externalizedValueEquivalent(replacement.oldValue, added.value, context)) {
                emitExternalization(out, added.key, added.node, pair);
                continue;
            }

            ProviderInfo removed = removedProviders.get(norm(replacement.symbol));
            if (removed != null && externalizedValueEquivalent(replacement.oldValue, removed.value, context)) {
                emitExternalization(out, removed.key, removed.node, pair);
                continue;
            }

            if (containsLogicalSymbol(visibleNonExternalSymbols, replacement.symbol)) continue;

            emitExternalization(out, replacement.symbol, null, pair);
        }
    }

    private static void detectMovedBuildVariablesToExternalProviders(
            RefactoringContext context,
            Map<String, ProviderInfo> addedProviders,
            Map<String, BuildRefactoring> out
    ) {
        if (context == null || context.matchResult == null || addedProviders == null || addedProviders.isEmpty()) {
            return;
        }

        List<DSLNode> removedVariables = variableNodesIn(
                context.matchResult.removedLeaves,
                context.matchResult.removedComposite
        );

        Set<DSLNode> providers = new LinkedHashSet<>();
        for (ProviderInfo provider : addedProviders.values()) {
            if (provider != null && provider.node != null) {
                providers.add(provider.node);
            }
        }

        for (DSLNode removed : removedVariables) {
            if (removed == null) continue;
            if (isDeletedSourceFile(removed, context)) continue;
            if (isExternalBinding(removed)) continue;
            if (!isVariableEntity(removed)) continue;

            ExpressionNode oldValue = extractInitializer(removed);
            if (oldValue == null || isContainerValue(oldValue)) continue;

            for (DSLNode providerNode : providers) {
                ProviderInfo provider = providerInfo(providerNode);
                if (provider == null) continue;
                if (providerKeyExistedInOldExternalFile(context.oldRoots, provider)) continue;
                if (!sameLogicalVariable(removed.name, provider.key)) continue;
                if (!externalizedValueEquivalent(oldValue, provider.value, context)) continue;

                emitExternalization(out, provider.key, provider.node, removed);
            }
        }
    }

    private static ExternalizationReplacement slotExternalization(DSLNode before, DSLNode after, Map<String, ProviderInfo> providers) {
        if (before == null || after == null) return null;
        if (!sameConsumerRole(before, after)) return null;

        for (ExprSlot beforeSlot : expressionSlots(before)) {
            for (ExprSlot afterSlot : expressionSlots(after)) {
                if (!Objects.equals(beforeSlot.key(), afterSlot.key())) continue;

                ExpressionNode oldExpr = unwrapCasts(beforeSlot.expr());
                ExpressionNode newExpr = unwrapCasts(afterSlot.expr());
                if (!concreteExternalizedSource(oldExpr)) continue;

                ExternalizationReplacement replacement = replacementUsingProvider(oldExpr, newExpr, providers);
                if (replacement != null) return replacement;
            }
        }

        return null;
    }

    private static ExternalizationReplacement replacementUsingProvider(ExpressionNode oldExpr, ExpressionNode newExpr, Map<String, ProviderInfo> providers) {
        for (ProviderInfo provider : providers.values()) {
            if (provider == null) continue;
            if (usesInExpr(oldExpr, provider.key) || usesInExpr(oldExpr, provider.qualifiedKey)) continue;

            if (usesInExpr(newExpr, provider.qualifiedKey)) {
                return new ExternalizationReplacement(oldExpr, provider.qualifiedKey);
            }

            if (usesInExpr(newExpr, provider.key)) {
                return new ExternalizationReplacement(oldExpr, provider.key);
            }
        }

        return null;
    }

    private static ExternalizationReplacement literalToExternalReferenceReplacement(DSLNode before, DSLNode after) {
        if (before == null || after == null) return null;
        if (!sameFile(before, after)) return null;
        if (!isGradleNode(after)) return null;
        if (isExternalBinding(after)) return null;
        if (before.type != after.type) return null;
        if (!Objects.equals(norm(before.name), norm(after.name))) return null;

        List<ExpressionNode> beforeExprs = comparableConsumerExpressions(before);
        List<ExpressionNode> afterExprs = comparableConsumerExpressions(after);
        if (beforeExprs.size() != afterExprs.size()) return null;

        for (int i = 0; i < beforeExprs.size(); i++) {
            ExpressionNode oldValue = beforeExprs.get(i);
            ExpressionNode newValue = afterExprs.get(i);

            if (sameExpression(oldValue, newValue)) continue;
            if (!isConcreteInlineValue(oldValue)) continue;

            String symbol = externalReferenceKey(newValue);
            if (!safe(symbol).isBlank()) {
                return new ExternalizationReplacement(oldValue, symbol);
            }
        }

        return null;
    }

    private static boolean isExternalizationEvidence(DSLNode before, DSLNode after, ProviderInfo provider, RefactoringContext context) {
        if (before == null || after == null || provider == null) return false;

        return replacedByVariableInAlignedSlots(
                before,
                after,
                provider.value,
                provider.key,
                provider.node,
                context
        );
    }

    public static boolean externalizedValueEquivalent(ExpressionNode consumerValue, ExpressionNode providerValue, RefactoringContext context) {
        if (consumerValue == null || providerValue == null) return false;

        Map<String, ExpressionNode> env = new LinkedHashMap<>();
        if (context != null) {
            env.putAll(matchedBeforeAliases(context));
            env.putAll(propagatedRenameAliases(context));
        }

        ExpressionNode before = unwrapCasts(expandFully(consumerValue, env));
        ExpressionNode provider = unwrapCasts(expandFully(providerValue, env));

        return equivalentModuloWrapper(before, provider, env);
    }

    private static Map<String, ExpressionNode> propagatedRenameAliases(RefactoringContext context) {
        Map<String, ExpressionNode> out = new LinkedHashMap<>();
        if (context == null || context.matchResult == null) return out;

        List<DSLNode> removed = variableNodesIn(context.matchResult.removedLeaves, context.matchResult.removedComposite);
        List<DSLNode> added = variableNodesIn(context.matchResult.addedLeaves, context.matchResult.addedComposite);

        for (DSLNode before : removed) {
            String oldName = normalizeReferenceName(before.name);
            if (safe(oldName).isBlank()) continue;

            for (DSLNode after : added) {
                String newName = bindingReferenceName(after);
                if (safe(newName).isBlank()) continue;
                if (sameLogicalVariable(oldName, newName)) continue;
                if (!namePropagatedInMatchedExpressions(oldName, newName, context)) continue;

                ExpressionNode alias = referenceExpression(newName);
                if (alias != null) {
                    out.putIfAbsent(norm(oldName), alias);
                }
            }
        }

        return out;
    }

    @SafeVarargs
    private static List<DSLNode> variableNodesIn(Collection<DSLNode>... groups) {
        LinkedHashSet<DSLNode> out = new LinkedHashSet<>();
        for (Collection<DSLNode> group : groups) {
            for (DSLNode node : safeCollection(group)) {
                collectVariableNodes(node, out);
            }
        }
        return new ArrayList<>(out);
    }

    private static void collectVariableNodes(DSLNode node, Set<DSLNode> out) {
        if (node == null) return;
        if (isVariableNode(node) && extractInitializer(node) != null) {
            out.add(node);
        }
        for (DSLNode child : safeList(node.children)) {
            collectVariableNodes(child, out);
        }
    }

    private static boolean namePropagatedInMatchedExpressions(String oldName, String newName, RefactoringContext context) {
        if (context == null || context.matchResult == null) return false;

        oldName = normalizeReferenceName(oldName);
        newName = normalizeReferenceName(newName);
        if (safe(oldName).isBlank() || safe(newName).isBlank()) return false;

        int oldUsages = 0;
        int replacedUsages = 0;

        for (MatchPair pair : context.matchResult.matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;

            if (isVariableNode(pair.left())
                    && isVariableNode(pair.right())
                    && sameLogicalVariable(pair.left().name, oldName)) {
                oldUsages++;

                if (sameLogicalVariable(pair.right().name, newName)) {
                    replacedUsages++;
                }
            }

            boolean afterUsesNewName = usesAnyComparableExpression(pair.right(), newName);
            for (ExpressionNode beforeExpr : comparableConsumerExpressions(pair.left())) {
                if (!usesInExpr(beforeExpr, oldName)) continue;

                oldUsages++;
                if (afterUsesNewName) {
                    replacedUsages++;
                }
            }
        }

        return oldUsages > 0 && oldUsages == replacedUsages;
    }

    private static boolean usesAnyComparableExpression(DSLNode node, String name) {
        for (ExpressionNode expr : comparableConsumerExpressions(node)) {
            if (usesInExpr(expr, name)) return true;
        }
        return false;
    }

    private static Map<String, ProviderInfo> visibleAddedProviders(RefactoringContext context) {
        Map<String, ProviderInfo> out = new LinkedHashMap<>();
        if (context == null || context.matchResult == null) return out;

        for (DSLNode node : externalBindingCandidates(context.matchResult)) {
            addProvider(out, providerInfo(node));
        }

        return out;
    }

    private static Map<String, ProviderInfo> visibleRemovedProviders(RefactoringContext context) {
        Map<String, ProviderInfo> out = new LinkedHashMap<>();
        if (context == null || context.matchResult == null) return out;

        for (DSLNode node : context.matchResult.removedLeaves) {
            addProvider(out, providerInfoFromRemovedExternalBinding(node));
        }

        for (DSLNode node : context.matchResult.removedComposite) {
            addProvider(out, providerInfoFromRemovedExternalBinding(node));
        }

        return out;
    }

    private static List<DSLNode> externalBindingCandidates(MatchResult match) {
        List<DSLNode> out = new ArrayList<>();
        if (match == null) return out;

        for (DSLNode node : match.addedLeaves) {
            addExternalBindingCandidate(out, node);
        }

        for (DSLNode node : match.addedComposite) {
            addExternalBindingCandidate(out, node);
        }

        return out;
    }

    private static void addExternalBindingCandidate(List<DSLNode> out, DSLNode node) {
        if (isExternalBindingVariableEntity(node) && !out.contains(node)) {
            out.add(node);
        }
    }

    private static ProviderInfo providerInfo(DSLNode node) {
        if (!isExternalBindingVariableEntity(node)) return null;
        return providerInfoFromBinding(node);
    }

    private static ProviderInfo providerInfoFromRemovedExternalBinding(DSLNode node) {
        if (!isExternalBindingVariableEntity(node)) return null;
        return providerInfoFromBinding(node);
    }

    private static ProviderInfo providerInfoFromBinding(DSLNode node) {
        String key = normalizeReferenceName(node.name);
        ExpressionNode value = extractInitializer(node);

        if (safe(key).isBlank() || value == null) return null;
        if (isContainerValue(value)) return null;

        return new ProviderInfo(
                key,
                buildQualifiedKey(node),
                value,
                normalizePath(node.relativePath),
                node
        );
    }

    private static void addProvider(Map<String, ProviderInfo> out, ProviderInfo provider) {
        if (provider == null) return;

        if (!safe(provider.key).isBlank()) {
            out.putIfAbsent(norm(provider.key), provider);
        }

        if (!safe(provider.qualifiedKey).isBlank()) {
            out.putIfAbsent(norm(provider.qualifiedKey), provider);
        }
    }

    private static boolean isExternalBindingVariableEntity(DSLNode node) {
        ExpressionNode initializer = extractInitializer(node);

        return isVariableNode(node)
                && isExternalBinding(node)
                && initializer != null
                && !isContainerValue(initializer)
                && !safe(normalizeReferenceName(node.name)).isBlank();
    }

    private static boolean providerKeyExistedInOldExternalFile(DSLNode oldRoots, ProviderInfo current) {
        if (oldRoots == null || current == null) return false;

        String file = normalizePath(current.file);
        if (file.isBlank() || safe(current.key).isBlank()) return false;

        for (DSLNode node : flatten(oldRoots)) {
            ProviderInfo old = providerInfo(node);
            if (old == null) continue;
            if (!Objects.equals(normalizePath(old.file), file)) continue;
            if (sameLogicalVariable(old.key, current.key)) return true;
        }

        return false;
    }

    private static boolean isConsumerCandidate(DSLNode before, DSLNode after, DSLNode providerNode) {
        if (before == null || after == null || providerNode == null) return false;
        if (!sameFile(before, after)) return false;
        if (!isGradleNode(after)) return false;
        if (isExternalBinding(after)) return false;

        return !sameFile(after, providerNode);
    }

    private static Set<String> visibleNonExternalSymbols(RefactoringContext context) {
        Set<String> out = new LinkedHashSet<>();
        collectVisibleNonExternalSymbols(out, context == null ? null : context.oldRoots);
        collectVisibleNonExternalSymbols(out, context == null ? null : context.newRoots);
        return out;
    }

    private static void collectVisibleNonExternalSymbols(Set<String> out, DSLNode root) {
        if (root == null) return;

        for (DSLNode node : flatten(root)) {
            if (node == null || isExternalBinding(node)) continue;

            String name = norm(normalizeReferenceName(node.name));
            if (!name.isBlank()) {
                out.add(name);
            }
        }
    }

    private static boolean containsLogicalSymbol(Set<String> visibleSymbols, String symbol) {
        if (visibleSymbols == null || safe(symbol).isBlank()) return false;

        for (String visible : visibleSymbols) {
            if (sameLogicalVariable(visible, symbol)) return true;
        }

        return false;
    }

    private static List<ExpressionNode> comparableConsumerExpressions(DSLNode node) {
        List<ExpressionNode> out = new ArrayList<>();
        if (node == null) return out;

        if (node.value != null) out.add(node.value);
        if (node.receiver != null) out.add(node.receiver);

        for (ExpressionNode arg : safeList(node.arguments)) {
            if (arg != null) out.add(arg);
        }

        for (ExpressionNode semanticExpr : semanticExpressionSlots(node).values()) {
            if (semanticExpr != null) out.add(semanticExpr);
        }

        return out;
    }

    private static boolean concreteExternalizedSource(ExpressionNode expr) {
        expr = unwrapCasts(expr);
        return expr instanceof ConstantNode || expr instanceof GStringNode;
    }

    private static boolean isContainerValue(ExpressionNode value) {
        value = unwrapCasts(value);
        return value instanceof MapNode || value instanceof ListNode;
    }

    private static String externalReferenceKey(ExpressionNode expr) {
        if (expr == null) return "";

        expr = unwrapCasts(expr);

        if (expr instanceof VariableNode(String name)) {
            return normalizeReferenceName(name);
        }

        if (expr instanceof GStringNode(String text)) {
            return gStringReferenceKey(text);
        }

        if (expr instanceof PropertyNode property) {
            String ref = expressionReferenceText(property);
            return isSimpleReference(ref) ? normalizeReferenceName(ref) : "";
        }

        if (expr instanceof BinaryExpressionNode binary) {
            String indexed = indexedLookupKey(binary);
            if (!safe(indexed).isBlank()) return indexed;

            String left = externalReferenceKey(binary.left());
            if (!safe(left).isBlank()) return left;

            String right = externalReferenceKey(binary.right());
            if (!safe(right).isBlank()) return right;
        }

        if (expr instanceof MethodCallNode call) {
            for (ExpressionNode arg : safeList(call.arguments())) {
                String key = externalReferenceKey(arg);
                if (!safe(key).isBlank()) return key;
            }

            return externalReferenceKey(call.target());
        }

        return "";
    }

    private static String indexedLookupKey(BinaryExpressionNode binary) {
        if (binary == null || !Objects.equals(safe(binary.operation()), "[")) return "";

        String key = expressionReferenceText(binary.right());
        return safe(key).isBlank() ? "" : normalizeReferenceName(key);
    }

    private static String gStringReferenceKey(String text) {
        if (safe(text).isBlank()) return "";

        LinkedHashSet<String> refs = new LinkedHashSet<>();

        java.util.regex.Matcher braced = BRACED_GSTRING_REFERENCE.matcher(text);
        while (braced.find()) {
            addSimpleReference(refs, braced.group(1));
        }

        java.util.regex.Matcher simple = SIMPLE_GSTRING_REFERENCE.matcher(text);
        while (simple.find()) {
            addSimpleReference(refs, simple.group(1));
        }

        return refs.size() == 1 ? refs.getFirst() : "";
    }

    private static void addSimpleReference(Set<String> refs, String raw) {
        if (!isSimpleReference(raw)) return;

        String ref = normalizeReferenceName(raw.trim());
        if (!safe(ref).isBlank()) {
            refs.add(ref);
        }
    }

    private static boolean isSimpleReference(String text) {
        return !safe(text).isBlank() && SIMPLE_REFERENCE.matcher(text.trim()).matches();
    }

    private static String bindingReferenceName(DSLNode node) {
        if (node == null || safe(node.name).isBlank()) return "";

        DSLNode parent = node.parent;
        if (isVariableNode(parent) && !safe(parent.name).isBlank()) {
            return normalizeReferenceName(parent.name) + "." + normalizeReferenceName(node.name);
        }

        return normalizeReferenceName(node.name);
    }

    private static String buildQualifiedKey(DSLNode node) {
        List<String> parts = new ArrayList<>();

        for (DSLNode cur = node; cur != null; cur = cur.parent) {
            if (!safe(cur.name).isBlank()) {
                parts.add(norm(cur.name));
            }
        }

        Collections.reverse(parts);
        return String.join(".", parts);
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

    private static String logicalExternalPropertyKey(String key) {
        String normalized = normalizeReferenceName(key);
        if (safe(normalized).isBlank()) return "";

        String[] parts = normalized.split("\\.");
        return parts.length == 0 ? normalized.trim() : parts[parts.length - 1].trim();
    }

    private static void emitExternalization(
            Map<String, BuildRefactoring> out,
            String key,
            DSLNode providerNode,
            MatchPair pair
    ) {
        String logicalKey = logicalExternalPropertyKey(key);
        if (logicalKey.isBlank()) return;

        BuildRefactoring ref = out.computeIfAbsent(
                norm(logicalKey),
                ignored -> new BuildRefactoring(
                        BuildRefactoringType.EXTERNALIZE_PROPERTIES,
                        "Externalize property '" + logicalKey + "'"
                )
        );

        addIfAbsent(ref.addedNodes, providerNode);
        addIfAbsent(ref.removedNodes, pair.left());
        addIfAbsent(ref.addedNodes, pair.right());

        if (!ref.relatedMatches.contains(pair)) {
            ref.relatedMatches.add(pair);
        }
    }

    private static void emitExternalization(
            Map<String, BuildRefactoring> out,
            String key,
            DSLNode providerNode,
            DSLNode removedNode
    ) {
        String logicalKey = logicalExternalPropertyKey(key);
        if (logicalKey.isBlank()) return;

        BuildRefactoring ref = out.computeIfAbsent(
                norm(logicalKey),
                ignored -> new BuildRefactoring(
                        BuildRefactoringType.EXTERNALIZE_PROPERTIES,
                        "Externalize property '" + logicalKey + "'"
                )
        );

        addIfAbsent(ref.addedNodes, providerNode);
        addIfAbsent(ref.removedNodes, removedNode);
    }

    private static void dedupeNodesInRef(BuildRefactoring ref) {
        if (ref == null) return;

        Map<String, DSLNode> removed = new LinkedHashMap<>();
        for (DSLNode node : safeList(ref.removedNodes)) {
            if (node != null) removed.putIfAbsent(nodeKey(node), node);
        }

        Map<String, DSLNode> added = new LinkedHashMap<>();
        for (DSLNode node : safeList(ref.addedNodes)) {
            if (node != null) added.putIfAbsent(nodeKey(node), node);
        }

        ref.removedNodes.clear();
        ref.removedNodes.addAll(removed.values());

        ref.addedNodes.clear();
        ref.addedNodes.addAll(added.values());
    }

    private static void dedupeMatchesInRef(BuildRefactoring ref) {
        if (ref == null) return;

        Map<String, MatchPair> out = new LinkedHashMap<>();
        for (MatchPair pair : safeList(ref.relatedMatches)) {
            if (pair == null) continue;

            String key = nodeKey(pair.left()) + "->" + nodeKey(pair.right());
            out.putIfAbsent(key, pair);
        }

        ref.relatedMatches.clear();
        ref.relatedMatches.addAll(out.values());
    }

    public static boolean replacedByVariableInAlignedSlots(DSLNode before, DSLNode after, ExpressionNode initializer, String variableName, DSLNode added, RefactoringContext context) {
        return RuleUtils.replacedByVariableInAlignedSlots(before, after, initializer, variableName, added, context, false);
    }

}
