package org.example.refactoring.module;

import org.example.refactoring.BuildRefactoring;
import org.example.refactoring.BuildRefactoringType;
import org.example.refactoring.RefactoringContext;
import org.example.refactoring.RefactoringRule;
import org.example.refactoring.RuleUtils;

import java.util.List;

public class ExtractAndPushDownModule implements RefactoringRule {

    @Override
    public List<BuildRefactoring> detect(RefactoringContext context) {
        return ExtractModule.detectByDirection(
                context,
                BuildRefactoringType.EXTRACT_AND_PUSH_DOWN_MODULE,
                RuleUtils::isPushDown,
                (s, t) -> "Extract and push down module logic from '" + s + "' to '" + t + "'"
        );
    }
}
