package org.example.refactoring.cleanup;

import org.example.matcher.MatchPair;
import org.example.model.DSLNode;
import org.example.model.ExpressionNode;
import org.example.refactoring.*;

import java.util.*;


import static org.example.matcher.SemanticMatchConditions.parentCompatible;
import static org.example.refactoring.RuleUtils.*;

public class RenameField implements RefactoringRule {
    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        Map<String, BuildRefactoring> out = new LinkedHashMap<>();

        for (MatchPair pair : context.matchResult.matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;

            DSLNode before = pair.left();
            DSLNode after = pair.right();
            //ELEMENT_RENAME
            if (isRenameCandidate(before, after)) {
                String description = "Rename '" + before.name + "' to '" + after.name + "'";
                String key = BuildRefactoringType.RENAME_FIELD + "|" + description;
                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> new BuildRefactoring(BuildRefactoringType.RENAME_FIELD, description)
                );

                addIfAbsent(ref.removedNodes, before);
                addIfAbsent(ref.addedNodes, after);
                if (!ref.relatedMatches.contains(pair)) ref.relatedMatches.add(pair);
            }
            //FILE_RENAME
            if (isRenameFileCandidate(before, after, context)) {
                DSLNode bf = fileRootOf(before);
                DSLNode af = fileRootOf(after);
                if (bf == null || af == null) continue;

                String key = "FILE|" + normalizePath(bf.relativePath) + "->" + normalizePath(af.relativePath);
                BuildRefactoring ref = out.computeIfAbsent(
                        key, k -> new BuildRefactoring(
                                BuildRefactoringType.RENAME_FIELD, "Rename file '" + bf.relativePath + "' to '" + af.relativePath + "'"
                        )
                );

                addIfAbsent(ref.removedNodes, bf);
                addIfAbsent(ref.addedNodes, af);
                if (!ref.relatedMatches.contains(pair)) ref.relatedMatches.add(pair);
            }
        }

        for (DSLNode before : context.matchResult.removedLeaves) {
            if (!isRenameableFieldBinding(before)) continue;

            for (DSLNode after : context.matchResult.addedLeaves) {
                if (!isRenameableFieldBinding(after)) continue;
                boolean normalRename = isRenameCandidate(before, after);

                boolean usageRename =
                        variableRenamePropagatedInUsages(before, after, context);

                if (!normalRename && !usageRename) continue;


                String description = "Rename '" + before.name + "' to '" + after.name + "'";
                String key = BuildRefactoringType.RENAME_FIELD + "|" + description;

                BuildRefactoring ref = out.computeIfAbsent(
                        key,
                        k -> new BuildRefactoring(BuildRefactoringType.RENAME_FIELD, description)
                );

                addIfAbsent(ref.removedNodes, before);
                addIfAbsent(ref.addedNodes, after);
            }
        }


        return new ArrayList<>(out.values());
    }

    private static boolean variableRenamePropagatedInUsages(DSLNode before, DSLNode after, RefactoringContext context) {
        if (before == null || after == null) return false;
        if (!sameFile(before, after)) return false;

        return oldNameReplacedByNewNameInUsages(before.name, after.name, context);
    }
    public static boolean oldNameReplacedByNewNameInUsages(String oldName, String newName, RefactoringContext context) {
        if (context == null || context.matchResult == null) return false;

        oldName = normalizeReferenceName(oldName);
        newName = normalizeReferenceName(newName);

        if (safe(oldName).isBlank() || safe(newName).isBlank()) return false;
        if (sameLogicalVariable(oldName, newName)) return false;

        int oldUsages = 0;
        int replacedUsages = 0;

        for (MatchPair pair : context.matchResult.matches) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;


            Map<String, ExpressionNode> beforeSlots = semanticExpressionSlots(pair.left());
            Map<String, ExpressionNode> afterSlots = semanticExpressionSlots(pair.right());

            beforeSlots.putAll(rawExpressionSlots(pair.left()));
            afterSlots.putAll(rawExpressionSlots(pair.right()));

            for (String slot : beforeSlots.keySet()) {
                ExpressionNode beforeExpr = beforeSlots.get(slot);
                ExpressionNode afterExpr = afterSlots.get(slot);

                if (!usesInExpr(beforeExpr, oldName)) continue;

                oldUsages++;

                if (afterExpr != null && usesInExpr(afterExpr, newName)) {
                    replacedUsages++;
                }
            }
        }

        return oldUsages > 0 && oldUsages == replacedUsages;
    }
    private static Map<String, ExpressionNode> rawExpressionSlots(DSLNode node) {
        Map<String, ExpressionNode> out = new LinkedHashMap<>();
        if (node == null) return out;

        if (node.value != null) {
            out.put("value", node.value);
        }

        if (node.receiver != null) {
            out.put("receiver", node.receiver);
        }

        int i = 0;
        for (ExpressionNode arg : safeList(node.arguments)) {
            out.put("arg[" + i + "]", arg);
            i++;
        }

        return out;
    }
    public boolean isRenameCandidate(DSLNode before, DSLNode after) {
        String beforeName = norm(normalizeReferenceName(before.name));
        String afterName = norm(normalizeReferenceName(after.name));

        if (beforeName.isBlank() || afterName.isBlank()) return false;
        if (beforeName.equals(afterName)) return false;
        if (!sameFile(before, after)) return false;
        if (isVariableNode(before) && isVariableNode(after)) {
            ExpressionNode beforeValue = extractInitializer(before);
            ExpressionNode afterValue = extractInitializer(after);

            if (beforeValue == null || afterValue == null) return false;

            if (!equivalentModuloWrapper(beforeValue, afterValue, Map.of())) {
                return false;
            }
            if (!parentCompatible(before, after)) return false;
        }
        if (before.type != after.type) {
            if (!(isRenameableFieldBinding(before) && isRenameableFieldBinding(after))) return false;
        }
        if (!isSupportedRenameType(before.type)) return false;
        if (sameVariableName(before, after)) return false;
        if (!Objects.equals(String.valueOf(before.receiver), String.valueOf(after.receiver))) return false;
        return (before.arguments == null ? 0 : before.arguments.size())
                == (after.arguments == null ? 0 : after.arguments.size());
    }
    public static boolean isRenameableFieldBinding(DSLNode node) {
        if (node == null) return false;

        if (node.type == DSLNode.Type.ASSIGNMENT
                || node.type == DSLNode.Type.DECLARATION) {
            return true;
        }

        // Gradle DSL setter: versionCode 3, minSdkVersion 21
        return node.type == DSLNode.Type.METHOD_CALL
                && node.receiver == null
                && safeList(node.arguments).size() == 1
                && node.value == null;
    }
    private static boolean isSupportedRenameType(DSLNode.Type t) {
        return t == DSLNode.Type.TASK_DECLARATION
                || t == DSLNode.Type.METHOD_DECL
                || t == DSLNode.Type.METHOD_CALL
                || t == DSLNode.Type.CLASS_DECL
                || t == DSLNode.Type.CONSTRUCTOR_DECL
                || t == DSLNode.Type.DECLARATION
                || t == DSLNode.Type.ASSIGNMENT;
    }
    public static boolean isRenameFileCandidate(DSLNode before, DSLNode after, RefactoringContext context) {
        DSLNode beforeFile = fileRootOf(before);
        DSLNode afterFile = fileRootOf(after);
        if (beforeFile == null || afterFile == null) return false;
        if (!isFile(beforeFile) || !isFile(afterFile)) return false;
        if (normalizePath(beforeFile.relativePath).equals(normalizePath(afterFile.relativePath))) return false;

        DSLNode uniqueTarget = uniqueTargetFileFor(beforeFile, context);
        DSLNode uniqueSource = uniqueSourceFileFor(afterFile, context);

        return uniqueTarget == afterFile && uniqueSource == beforeFile;
    }
    private static DSLNode uniqueTargetFileFor(DSLNode beforeFile, RefactoringContext context) {
        DSLNode target = null;
        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.left() == null || p.right() == null) continue;
            if (!isDescendantOf(p.left(), beforeFile)) continue;
            DSLNode cand = fileRootOf(p.right());
            if (!isFile(cand)) continue;
            if (target == null) target = cand;
            else if (target != cand) return null;
        }
        return target;
    }
    private static DSLNode uniqueSourceFileFor(DSLNode afterFile, RefactoringContext context) {
        DSLNode source = null;
        for (MatchPair p : context.matchResult.matches) {
            if (p == null || p.left() == null || p.right() == null) continue;
            if (!isDescendantOf(p.right(), afterFile)) continue;
            DSLNode cand = fileRootOf(p.left());
            if (!isFile(cand)) continue;
            if (source == null) source = cand;
            else if (source != cand) return null;
        }
        return source;
    }



}
