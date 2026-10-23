# GradleMiner: Gradle Build Refactoring Detection Tool

GradleMiner is a Java-based tool for analyzing refactoring changes in Gradle build scripts. Given a GitHub commit, the tool fetches the parent and child revisions, parses the changed Gradle-related files, builds language-agnostic Gradle DSL trees, matches nodes between the two revisions, and reports detected build refactorings.

The repository also includes a scoring script for comparing GradleMiner output against a manually labeled ground truth dataset.

## Features

- **GitHub commit analysis:** Fetches changed files, parent commit contents, and child commit contents from GitHub.
- **Gradle DSL parsing:** Supports Groovy Gradle files, Kotlin Gradle files, and `.properties` files.
- **DSL tree construction:** Converts build scripts into typed DSL trees with parent-child relationships.
- **Node matching:** Matches leaf and composite DSL nodes across the parent and child revisions.
- **Change evidence reporting:** Reports added/deleted leaf nodes, added/deleted composite nodes, and matched node pairs.
- **Refactoring detection:** Detects Gradle build refactoring types using deterministic rules.
- **Evaluation support:** Provides one scoring script for instance-level precision, recall, and F1-score calculation.

## Repository Structure

```text
app/src/main/java/org/example/
├── App.java                         # Command-line entry point
├── github/                          # GitHub commit/file fetching
├── io/                              # CSV and output formatting utilities
├── pipeline/                        # Commit analysis and tree-building pipeline
├── parser/                          # Groovy, Kotlin, and properties parsers
├── visitor/                         # Groovy/Kotlin AST visitors
├── model/                           # Gradle DSL node and semantic models
├── matcher/                         # Leaf/composite matching logic
├── refactoring/                     # Refactoring detection rules
│   ├── cleanup/
│   ├── dependency/
│   ├── module/
│   ├── properties/
│   ├── subroutine/
│   └── variable/
└── LLM/                             # BuildRefMiner and Guided BuildRefMiner logic

scripts/score-run-labels.ps1         # Instance-level scoring script
```

## Requirements

### Software

- Java 21
- Gradle wrapper included in the repository
- PowerShell (`pwsh`) for the scoring script

### Environment Variables

GitHub API access works without a token for public repositories, but GitHub rate limits unauthenticated requests. For larger runs, set:

```bash
export GITHUB_TOKEN="your-github-token"
```

The deterministic GradleMiner path does **not** require an LLM API key.

LLM components use OpenRouter-compatible environment variables:

```bash
export OPENROUTER_API_KEY="your-openrouter-key"
export OPENROUTER_MODEL="model-name"
```

## Installation

Clone the repository and enter the project directory:

```bash
git clone <repository-url>
cd REFMiner
```

Compile the project:

```bash
./gradlew :app:test
```

A successful build should end with:

```text
BUILD SUCCESSFUL
```

## Usage

### Analyze One GitHub Commit

Run GradleMiner on a single commit:

```bash
./gradlew :app:run --args="https://github.com/owner/repository/commit/<sha>"
```

Example:

```bash
./gradlew :app:run --args="https://github.com/igor-rubis/irubis-framework/commit/d537ce83a0dd4ef6e32c7bc0b24c03b5cea90db7"
```

### Save the Analysis Report

To write the output to a file:

```bash
./gradlew :app:run --args="https://github.com/owner/repository/commit/<sha> build/evaluation/commit-report.txt"
```

## Output Format

For each analyzed commit, GradleMiner outputs a report with the following sections:

```text
COMMIT
CHANGED GRADLE FILES
BEFORE VERSION DSL TREE
AFTER VERSION DSL TREE
DELETED COMPOSITE NODES
DELETED LEAF NODES
ADDED COMPOSITE NODES
ADDED LEAF NODES
MATCHED NODES
DETECTED REFACTORINGS
```

The final section lists refactorings in the following format:

```text
REFACTORING_TYPE: Description
```

Example:

```text
EXTERNALIZE_PROPERTIES: Externalize property 'compileSdk'
REFORMAT_CODE: Reformat code (no semantic change)
SCHEDULING_TASKS: Introduce task relation 'dependsOn' ...
```

## Supported Refactoring Types

GradleMiner detects the following refactoring types:

```text
DRY
EXTERNALIZE_PROPERTIES
EXTRACT_AND_PULL_UP_MODULE
EXTRACT_AND_PULL_UP_VARIABLE
EXTRACT_AND_PUSH_DOWN_MODULE
EXTRACT_METHOD
EXTRACT_MODULE
EXTRACT_TASK
EXTRACT_VARIABLE
INLINE_VARIABLE
MOVE_DEPENDENCY
MOVE_VARIABLE
PULL_UP_DEPENDENCY
PULL_UP_METHOD
PULL_UP_VARIABLE
PUSH_DOWN_DEPENDENCY
PUSH_DOWN_TASK
PUSH_DOWN_VARIABLE
REFORMAT_CODE
REMOVE_UNUSED_CODE
RENAME_FIELD
SCHEDULING_TASKS
```

## Scoring

The repository provides one scoring script:

```text
scripts/score-run-labels.ps1
```

Use it to compare an expected label file with a GradleMiner prediction file:

```bash
pwsh scripts/score-run-labels.ps1 \
  -Expected <expected-labels.txt> \
  -Predicted <gradleminer-output.txt> \
  -Scores <scores-output.txt>
```

The scorer reports:

```text
TP
FP
FN
Precision
Recall
F1
```

It also reports per-refactoring-type scores.

## Example Evaluation Output

An evaluation summary has the following shape:

```text
Instance-level multiset scores:
TP=<true positives> FP=<false positives> FN=<false negatives>
Precision=<precision> Recall=<recall> F1=<f1>

Per-type instance-level scores:
REFORMAT_CODE TP=... FP=... FN=... Precision=... Recall=... F1=...
REMOVE_UNUSED_CODE TP=... FP=... FN=... Precision=... Recall=... F1=...
...
```

## Troubleshooting

### GitHub Rate Limits

If GitHub returns rate-limit errors, set a GitHub token:

```bash
export GITHUB_TOKEN="your-github-token"
```

### Invalid Commit URL

The commit URL must have the form:

```text
https://github.com/<owner>/<repository>/commit/<sha>
```

### Missing or Empty Output

If a commit does not modify Gradle-related files, the DSL trees may be empty and no refactorings may be reported. GradleMiner focuses on:

```text
build.gradle
build.gradle.kts
settings.gradle
settings.gradle.kts
init.gradle
init.gradle.kts
gradle.properties
*.gradle
*.gradle.kts
*.properties
```

## License

This project is licensed under the MIT License.
