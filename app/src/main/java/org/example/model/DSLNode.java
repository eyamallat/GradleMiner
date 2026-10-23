package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class DSLNode {

    public enum Type {
        TASK_DECLARATION,
        TASK_CONFIGURATION,
        TASK_RELATION,
        TASK_ACTION,
        WHEN_BRANCH,
        //Structural types
        METHOD_CALL,
        BLOCK,
        ASSIGNMENT,
        DECLARATION,
        CLOSURE,
        CONDITION,
        METHOD_DECL,
        IMPORT,
        PROPERTY,
        CLASS_DECL,
        //Semantic types
        TASK_DECL,
        CONSTRUCTOR_DECL,
        DEPENDENCIES_BLOCK,
        DEPENDENCY_DECL,
        ALLPROJECTS,
        REPOSITORIES_BLOCK,
        REPOSITORIES_DECL,
        PLUGINS_BLOCK,
        BUILDSCRIPT,
        PLUGIN_DECL,
        APPLY_SCRIPT,
        THROW,
        SUBPROJECTS,
        //File types
        ROOT_PROJECT,
        SETTINGS_GRADLE,
        BUILD_GRADLE,
        PROPERTIES_FILE,
        BUILD_LOGIC_GRADLE,
        INIT_GRADLE,
        UNKNOWN
    }
    public ExpressionNode value;
    public ExpressionNode receiver;
    public Object semantic;
    public Type type = Type.UNKNOWN;
    public String name;
    public String declaredType;
    public DSLNode parent;
    public String relativePath;
    public String operator;
    public List<ExpressionNode> arguments = new ArrayList<>();
    public List<String> typeArguments = new ArrayList<>();
    public List<DSLNode> children = new ArrayList<>();
    public int line;
    public int column;
    public boolean safeCall;

    public DSLNode(String name) {
        this.name = name;
    }
    public int getDepth() {
        int depth = 0;
        DSLNode current = this.parent;

        while (current != null) {
            depth++;
            current = current.parent;
        }

        return depth;
    }
}
