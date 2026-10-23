package org.example.refactoring;

import java.util.List;

public interface RefactoringRule {
    List<BuildRefactoring> detect(RefactoringContext context);
}