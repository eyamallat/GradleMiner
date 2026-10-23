package org.example.refactoring;

import org.example.model.DSLNode;
import org.example.matcher.MatchPair;

import java.util.ArrayList;
import java.util.List;

public class BuildRefactoring {
    public final BuildRefactoringType type;
    public final String description;
    public final List<DSLNode> addedNodes = new ArrayList<>();
    public final List<DSLNode> removedNodes = new ArrayList<>();
    public final List<MatchPair> relatedMatches = new ArrayList<>();

    public BuildRefactoring(BuildRefactoringType type, String description) {
        this.type = type;
        this.description = description;
    }
}