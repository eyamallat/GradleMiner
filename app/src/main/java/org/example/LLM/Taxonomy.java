package org.example.LLM;

public class Taxonomy {
    public static final String REFACTORING_TYPES_TAXONOMY_AND_OUTPUT_FORMAT = """
        Refactoring Types Definition and expected detected refactoring output format:
        1. RENAME_FIELD: Renames an existing Gradle build entity while preserving its identity, role, and associated value or body. This includes build script file paths, task names, method names, variables, and property/field bindings when the old and new names denote the same underlying entity. This category excludes Gradle API migrations, dependency configuration migrations, qualifier-only rewrites, property-access rewrites, and substitutions where one setting is replaced by a different setting.
           Output: RENAME_FIELD: Rename 'oldName' to 'newName'
           Output: RENAME_FIELD: Rename file 'oldPath' to 'newPath'
        2. REMOVE_UNUSED_CODE: Reports Gradle build files or Gradle build elements that are deleted by the commit and are not better explained by another refactoring type. This type is used as a residual cleanup category after more specific refactorings have been identified. Do not report it when the removed item is matched to an after-version node. Report each deleted file or deleted build element as a separate instance rather than summarizing multiple deletions together.
           Output: REMOVE_UNUSED_CODE: Remove unused file 'path'
           Output: REMOVE_UNUSED_CODE: Remove unused element 'path:name (123)'
        3. SCHEDULING_TASKS: Changes explicit Gradle task scheduling through task-to-task relationships or parallelism/forking settings. Includes dependsOn, mustRunAfter, shouldRunAfter, finalizedBy, parallel, threadCount, maxParallelForks, and forkEvery. Report only newly introduced task scheduling relations or scheduling assignments. The location must be the scheduling statement node itself, such as path:dependsOn, not the enclosing task block. Do not report scheduling applied for newly added tasks.
           Output: SCHEDULING_TASKS: Introduce scheduling assignment 'property' for 'owner' in 'path:name (123)'
           Output: SCHEDULING_TASKS: Introduce task relation 'kind' from 'owner' to 'target' in 'path:name (123)'
        4. REFORMAT_CODE: Detects behavior-preserving Gradle rewrites where the same build logic is preserved and no Gradle entity, dependency, property, task, plugin, repository, or scheduling relation is added, removed, moved, renamed, extracted, inlined, externalized, pulled up, or pushed down. This includes whitespace changes, statement reordering with no semantic effect, quote style changes, and equivalent notation changes that do not introduce or remove any named build entity. Report at most one REFORMAT_CODE instance per commit. Do not use REFORMAT_CODE when a more specific taxonomy type explains the change.
            Output: REFORMAT_CODE: Reformat code (no semantic change)
        5. EXTRACT_AND_PULL_UP_MODULE: Extracts a coherent unit of existing Gradle build logic from a deeper or more specialized Gradle build file into a newly added parent, root, or common Gradle script file.
            Output: EXTRACT_AND_PULL_UP_MODULE: Extract and pull up module logic from 'sourcePath' to 'targetPath'
        6. EXTRACT_AND_PUSH_DOWN_MODULE: Extracts a coherent unit of existing Gradle build logic from a parent, root, or common Gradle build file into a newly added deeper or more specialized Gradle script file.
            Output: EXTRACT_AND_PUSH_DOWN_MODULE: Extract and push down module logic from 'sourcePath' to 'targetPath'
        7. EXTRACT_MODULE: Extracts a coherent unit of existing Gradle build logic from a Gradle build file into a newly added Gradle script file at the same hierarchy level.
           Output: EXTRACT_MODULE: Extract module logic from 'sourcePath' to 'targetPath'
        8. EXTRACT_METHOD: Move a code fragment into a new method/function and replace the old fragment with a call/reference to that method.
           Output: EXTRACT_METHOD: Extract method 'path:name (123)'
        9. PULL_UP_METHOD: Moves an existing Gradle method/function from a deeper/specialized build file to a parent/root/common Gradle file without changing the method identity.
            Output: PULL_UP_METHOD: Pull up method 'methodName' to 'path:name (123)'
        10. EXTRACT_TASK: Extracts existing build logic into a new Gradle task and leaves a reference to that task from the original owner/location. Do not classify a newly added standalone task as extraction unless its body is explained by old build logic.
            Output: EXTRACT_TASK: Extract task 'path:name (123)'
        11. PUSH_DOWN_TASK: Moves an existing Gradle task downward without changing the task identity. Do not use for a newly added task or for whole-module extraction alone.
            Output: PUSH_DOWN_TASK: Push down task 'oldPath:oldName (123)' to 'newPath:newName (456)'
        12. DRY: Consolidates equivalent Gradle build logic from two or more previous locations into one generalized shared representation, preserving behavior while reducing duplication. Report DRY only when the commit evidence shows repeated old logic being removed and generalized by the shared representation.Don't report general script blocks or configuration blocks.
            Output: DRY: Consolidate duplicated logic into 'path:name (123)'
        13. PUSH_DOWN_DEPENDENCY: Move a dependency from a parent build file to subproject build files.
            Output: PUSH_DOWN_DEPENDENCY: Push down dependency 'path:name (123)'
        14. PULL_UP_DEPENDENCY: Move a dependency from one or more subproject build files to the root build file.
            Output: PULL_UP_DEPENDENCY: Pull up dependency 'dependencyDisplayName'
        15. MOVE_DEPENDENCY: Move a dependency to another build file at the same hierarchy level.
            Output: MOVE_DEPENDENCY: Move dependency 'path:name (123)'
        16. EXTERNALIZE_PROPERTIES: Moves a Gradle value out of build logic into an external property source. This can happen by moving a variable definition used by build logic into an external file, by replacing a hardcoded/literal value with an external property reference, or by adding the referenced external property definition in the same commit. An external source is either a `.properties` file or a pure Gradle file containing only reusable variable/property declarations. Ordinary variable extraction or movement between regular build.gradle files is not externalization.
            Output: EXTERNALIZE_PROPERTIES: Externalize property 'keyName'
        17. EXTRACT_VARIABLE: Extract a direct value or expression and assign it to a new variable within the same build file, replacing the original direct usage with that variable.
            Output: EXTRACT_VARIABLE: Extract variable 'path:name (123)'
        18. INLINE_VARIABLE: Replaces a before-version variable/property reference at a consumer node with its concrete value or a literal value in the after-version. The variable/property declaration may be removed or retained. Report one instance per replaced use site. The location must be the exact consumer node where the reference appeared before replacement, not the variable definition and not the enclosing block. Do not classify the removed variable/property as REMOVE_UNUSED_CODE when its value appears directly in matched or added build logic.
            Output: INLINE_VARIABLE: Inline variable 'refName' in 'path:name (123)'
        19. MOVE_VARIABLE: Move a variable from one build file to another at the same hierarchy level without changing its value or expression.
            Output: MOVE_VARIABLE: Move variable 'oldPath:oldName (123)' to 'newPath:newName (456)'
        20. PULL_UP_VARIABLE: Moves an existing variable/property definition upward without changing its identity or value. Do not use when a literal is newly extracted into a variable.
            Output: PULL_UP_VARIABLE: Pull up variable to 'path:name (123)'
        21. PUSH_DOWN_VARIABLE: Moves an existing variable/property definition downward without changing its identity or value.
            Output: PUSH_DOWN_VARIABLE: Push down variable 'oldPath:oldName (123)'
        22. EXTRACT_AND_MOVE_VARIABLE: Extract a direct value/expression into a variable, then move that variable to another build file at the same hierarchy level.
            Output: EXTRACT_AND_MOVE_VARIABLE: Extract and move variable 'path:name (123)'
        23. EXTRACT_AND_PULL_UP_VARIABLE: Introduces a new variable or property from a concrete value or expression that previously appeared in one or more deeper/specialized Gradle build files, and places that variable/property in a parent, root, or common Gradle build file. References in the original locations are replaced by the introduced variable/property. Report one instance per introduced variable/property.
            Output: EXTRACT_AND_PULL_UP_VARIABLE: Extract and pull up variable 'path:name (123)'
        
        In DetectedRefactoringTypes, RefactoringType must be one of these exact enum names.
        Output must follow the corresponding detector output pattern exactly.
        A node location is exactly path + ":" + name + " (" + 123 + ")".
        
        Directional move rule:
            Use PULL_UP_* or PUSH_DOWN_* when an existing dependency, variable, method, or task is removed from one Gradle build file and the same entity appears in another Gradle build file at a different hierarchy level. Pull up means moved from a deeper/specialized file to a parent/root/common file. Push down means moved from a parent/root/common file to a deeper/specialized file. Do not use EXTRACT_AND_* when the entity already existed before; EXTRACT_AND_* is for values or logic newly extracted into a new entity.
        Module extraction boundary:
            EXTRACT_*_MODULE applies only when a coherent unit of existing Gradle build logic is factored into a newly added Gradle script file. The added script must contain logic that can be traced to the source file, or the source file must delegate to the added script. The mere addition of a Gradle script, file relocation, file rename, or movement of isolated variables, dependencies, methods, or tasks is not sufficient.
            Direction:
            - EXTRACT_AND_PULL_UP_MODULE: source path is deeper or more specialized than target path.
            - EXTRACT_AND_PUSH_DOWN_MODULE: source path is parent/root/common and target path is deeper or more specialized.
            - EXTRACT_MODULE: source and target are at the same hierarchy level.
        """;

}
