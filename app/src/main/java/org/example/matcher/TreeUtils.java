package org.example.matcher;

import org.example.model.DSLNode;

import java.util.*;

public class TreeUtils {

    public static List<DSLNode> leafNodes(DSLNode root) {
        List<DSLNode> result = new ArrayList<>();
        traverse(root, node -> {
            if (isStatementLeaf(node) && !isFileNode(node)) {
                result.add(node);
            }
        });
        return result;
    }

    public static List<DSLNode> compositeNodes(DSLNode root) {
        List<DSLNode> result = new ArrayList<>();
        traverse(root, node -> {
            if (!isStatementLeaf(node) && !isFileNode(node)) {
                result.add(node);
            }
        });
        return result;
    }

    public static void traverse(DSLNode node, java.util.function.Consumer<DSLNode> consumer) {
        consumer.accept(node);
        for (DSLNode child : node.children) {
            traverse(child, consumer);
        }
    }

    private static boolean isStatementLeaf(DSLNode node) {
        if (node == null) return false;

        return node.children == null
                || node.children.isEmpty();
    }

    private static boolean isFileNode(DSLNode node) {
        return node.type == DSLNode.Type.BUILD_GRADLE
                || node.type == DSLNode.Type.BUILD_LOGIC_GRADLE
                || node.type == DSLNode.Type.SETTINGS_GRADLE
                || node.type == DSLNode.Type.PROPERTIES_FILE
                || node.type == DSLNode.Type.INIT_GRADLE
                || node.type == DSLNode.Type.ROOT_PROJECT;
    }

}