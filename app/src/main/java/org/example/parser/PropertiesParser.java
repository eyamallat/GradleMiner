package org.example.parser;

import org.example.model.ConstantNode;
import org.example.model.DSLNode;

public class PropertiesParser {
    public static DSLNode parseProperties(String content, String relativePath) {
        DSLNode root = new DSLNode(relativePath);
        root.type = DSLNode.Type.PROPERTIES_FILE;
        root.relativePath = relativePath;

        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;

            String key = null;
            String value = null;

            int eqIndex = line.indexOf('=');
            if (eqIndex != -1) {
                key = line.substring(0, eqIndex).trim();
                value = line.substring(eqIndex + 1).trim();
            } else {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    key = parts[0].trim();
                    value = parts[1].trim();
                }
            }

            if (key == null) continue;

            value = value.replaceAll("^['\"]|['\"]$", "").trim();

            DSLNode assignment = new DSLNode(key);
            assignment.type = DSLNode.Type.ASSIGNMENT;
            assignment.line = i + 1;
            assignment.value = new ConstantNode(value);

            assignment.parent = root;
            root.children.add(assignment);
        }
        for (DSLNode child : root.children) {
            attachParentAndPathRecursively(child, root, relativePath);
        }



        return root;
    }
    public static void attachParentAndPathRecursively(DSLNode node, DSLNode parent, String relativePath) {
        if (node == null) return;

        node.parent = parent;
        node.relativePath = relativePath;

        for (DSLNode child : node.children) {
            attachParentAndPathRecursively(child, node, relativePath);
        }
    }

}
