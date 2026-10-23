package org.example.refactoring.module;

import org.example.refactoring.BuildRefactoring;
import org.example.refactoring.BuildRefactoringType;
import org.example.refactoring.RefactoringContext;
import org.example.refactoring.RefactoringRule;
import org.example.refactoring.RuleUtils;

import java.util.List;

public class ExtractAndPullUpModule implements RefactoringRule {

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        return ExtractModule.detectByDirection(
                context,
                BuildRefactoringType.EXTRACT_AND_PULL_UP_MODULE,
                RuleUtils::isPullUp,
                (s, t) -> "Extract and pull up module logic from '" + s + "' to '" + t + "'"
        );
    }
}
