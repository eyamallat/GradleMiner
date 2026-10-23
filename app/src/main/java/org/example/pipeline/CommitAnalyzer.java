package org.example.pipeline;

import org.example.LLM.LLMSummary;
import org.example.LLM.OneShotLLM;
import org.example.github.ChangedFile;
import org.example.github.CommitInfo;
import org.example.github.GitHubCommitClient;
import org.example.matcher.MatchPair;
import org.example.matcher.MatchResult;
import org.example.matcher.NodeConditions;
import org.example.matcher.NodeFormat;
import org.example.matcher.PropertiesMatcher;
import org.example.matcher.StatementMatcher;
import org.example.model.DSLNode;
import org.example.model.ExpressionNode;
import org.example.printer.DSLPrinter;
import org.example.refactoring.BuildRefactoring;
import org.example.refactoring.BuildRefactoringDetector;
import org.example.refactoring.RefactoringContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.io.EvaluationOutputWriter.safe;

public class CommitAnalyzer {
    private static final Set<String> REQUIRED_FILES = Set.of(
            "settings.gradle",
            "settings.gradle.kts",
            "build.gradle",
            "build.gradle.kts",
            "gradle.properties",
            "init.gradle",
            "init.gradle.kts"
    );

    private final GitHubCommitClient github;
    private final GradleTreeBuilder treeBuilder;

    public CommitAnalyzer() {
        this(new GitHubCommitClient());
    }

    public CommitAnalyzer(GitHubCommitClient github) {
        this.github = github;
        this.treeBuilder = new GradleTreeBuilder(github);
    }

    public List<BuildRefactoring> analyzeCommit(String commitUrl) throws Exception {
        return analyzeCommit(commitUrl, false);
    }

    public List<BuildRefactoring> analyzeCommitDeterministic(String commitUrl) throws Exception {
        return detect(analyzeContext(commitUrl));
    }

    public String analyzeCommitReport(String commitUrl) throws Exception {
        AnalysisContext analysis = analyzeContext(commitUrl);
        List<BuildRefactoring> refactorings = detect(analysis);

        StringBuilder report = new StringBuilder();
        appendSection(report, "COMMIT");
        report.append(commitUrl).append(System.lineSeparator());

        appendChangedFiles(report, analysis.changedFiles());

        appendSection(report, "BEFORE VERSION DSL TREE");
        report.append(DSLPrinter.format(analysis.beforeTree()));

        appendSection(report, "AFTER VERSION DSL TREE");
        report.append(DSLPrinter.format(analysis.afterTree()));

        MatchResult result = analysis.matchResult();
        appendNodes(report, "DELETED COMPOSITE NODES", result.removedComposite);
        appendNodes(report, "DELETED LEAF NODES", result.removedLeaves);
        appendNodes(report, "ADDED COMPOSITE NODES", result.addedComposite);
        appendNodes(report, "ADDED LEAF NODES", result.addedLeaves);
        appendMatches(report, "MATCHED NODES", result.matches);

        appendSection(report, "DETECTED REFACTORINGS");
        if (refactorings.isEmpty()) {
            report.append("(none)").append(System.lineSeparator());
        } else {
            for (BuildRefactoring refactoring : refactorings) {
                report.append(refactoring.type)
                        .append(": ")
                        .append(refactoring.description)
                        .append(System.lineSeparator());
            }
        }

        return report.toString();
    }

    public List<BuildRefactoring> analyzeCommit(String commitUrl, boolean printDetails) throws Exception {
        AnalysisContext analysis = analyzeContext(commitUrl);

        if (printDetails) {
            printTrees(analysis.beforeTree(), analysis.afterTree());
            printMatchResult(analysis.matchResult());
        }

        List<BuildRefactoring> refactorings = detect(analysis);
        String llmPrompt = LLMSummary.toPrompt(analysis.matchResult(), commitPatches(analysis.changedFiles()));
        printLlmResult(llmPrompt);

        if (printDetails) {
            printRefactorings(refactorings);
        }

        return refactorings;
    }

    public AnalysisOutputs analyzeCommitForOutputs(String commitUrl) throws Exception {
        AnalysisContext analysis = analyzeContext(commitUrl);
        List<BuildRefactoring> refactorings = detect(analysis);

        List<String> detectorOutputs = refactorings.stream()
                .map(ref -> ref.type + ": " + ref.description)
                .collect(Collectors.toList());

        List<String> llmOutputs = List.of();
        List<String> suggestedOutputs = List.of();
        List<String> oneShotOutputs = List.of();
        String prompt = null;
        Map<String, String> patches = commitPatches(analysis.changedFiles());
        if (LLMSummary.apiEnabled()) {
            prompt = LLMSummary.toPrompt(
                    analysis.matchResult(),
                    patches
            );
        }
        if (LLMSummary.apiEnabled()) {
            try {
                String llmJson = LLMSummary.callApi(prompt);
                llmOutputs = LLMSummary.detectedOutputs(llmJson);
                suggestedOutputs = LLMSummary.suggestedOutputs(llmJson);
            } catch (Exception e) {
                System.out.println("  LLM summary error: " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            }
        }
        if (OneShotLLM.apiEnabled()) {
            try {
                String oneShotJson = OneShotLLM.callApi(OneShotLLM.promptFor(patches));
                oneShotOutputs = LLMSummary.detectedOutputs(oneShotJson);
            } catch (Exception e) {
                System.out.println("  one-shot LLM error: " + e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
            }
        }

        return new AnalysisOutputs(detectorOutputs, llmOutputs, suggestedOutputs, oneShotOutputs);
    }

    public LlmOnlyOutputs analyzeCommitForLlmOnlyOutputs(String commitUrl) throws Exception {
        AnalysisContext analysis = analyzeContext(commitUrl);
        Map<String, String> patches = commitPatches(analysis.changedFiles());

        String prompt = LLMSummary.toPrompt(
                analysis.matchResult(),
                patches
        );
        String llmJson = LLMSummary.callApi(prompt);
        List<String> llmOutputs = LLMSummary.detectedOutputs(llmJson);
        List<String> suggestedOutputs = LLMSummary.suggestedOutputs(llmJson);

        return new LlmOnlyOutputs(llmOutputs, suggestedOutputs);
    }

    public List<String> analyzeCommitForOneShotOutputs(String commitUrl) throws Exception {
        CommitInfo info = github.parseCommitUrl(commitUrl);

        List<ChangedFile> changedFiles = github.fetchChangedFiles(info);
        List<ChangedFile> requiredChangedFiles = filterRelevantFiles(changedFiles);
        Map<String, String> patches = commitPatches(requiredChangedFiles);

        String oneShotJson = OneShotLLM.callApi(OneShotLLM.promptFor(patches));
        return LLMSummary.detectedOutputs(oneShotJson);
    }

    private AnalysisContext analyzeContext(String commitUrl) throws Exception {
        CommitInfo info = github.parseCommitUrl(commitUrl);
        String parentSha = github.fetchParentCommit(info);

        List<ChangedFile> changedFiles = github.fetchChangedFiles(info);
        List<ChangedFile> requiredChangedFiles = filterRelevantFiles(changedFiles);

        List<String> beforePaths = requiredChangedFiles.stream()
                .map(ChangedFile::beforePath)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<String> afterPaths = requiredChangedFiles.stream()
                .map(ChangedFile::afterPath)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        DSLNode beforeTree = treeBuilder.buildTree(info, parentSha, beforePaths);
        DSLNode afterTree = treeBuilder.buildTree(info, info.sha(), afterPaths);
        MatchResult result = matchTreesPerFile(beforeTree, afterTree, requiredChangedFiles);

        return new AnalysisContext(requiredChangedFiles, beforeTree, afterTree, result);
    }

    private List<BuildRefactoring> detect(AnalysisContext analysis) {
        RefactoringContext context = new RefactoringContext(
                analysis.matchResult(),
                analysis.beforeTree(),
                analysis.afterTree()
        );
        BuildRefactoringDetector detector = new BuildRefactoringDetector();
        return detector.detect(context);
    }

    private static List<ChangedFile> filterRelevantFiles(List<ChangedFile> changedFiles) {
        return changedFiles.stream()
                .filter(f -> isRelevantGradleFile(f.beforePath()) || isRelevantGradleFile(f.afterPath()))
                .collect(Collectors.toList());
    }

    private static Map<String, String> commitPatches(List<ChangedFile> changedFiles) {
        Map<String, String> patches = new LinkedHashMap<>();

        for (ChangedFile file : changedFiles) {
            if (file == null) continue;
            String path = file.afterPath() != null ? file.afterPath() : file.beforePath();
            if (path == null || path.isBlank()) continue;
            if (file.patch() == null || file.patch().isBlank()) continue;

            patches.put(path, file.patch());
        }

        return patches;
    }

    private static boolean isRelevantGradleFile(String path) {
        if (path == null || path.isBlank()) return false;

        return REQUIRED_FILES.stream().anyMatch(path::endsWith)
                || path.endsWith(".gradle")
                || path.endsWith(".gradle.kts")
                || path.endsWith(".properties");
    }

    private static MatchResult matchTreesPerFile(DSLNode beforeTree, DSLNode afterTree, List<ChangedFile> changedFiles) {
        MatchResult result = new MatchResult();
        Map<String, ExpressionNode> beforeArgumentizationEnv = NodeConditions.argumentizationEnvironment(beforeTree);
        Map<String, ExpressionNode> afterArgumentizationEnv = NodeConditions.argumentizationEnvironment(afterTree);

        Map<String, DSLNode> beforeFiles = indexFilesByPath(beforeTree);
        Map<String, DSLNode> afterFiles = indexFilesByPath(afterTree);

        Set<String> consumedBefore = new LinkedHashSet<>();
        Set<String> consumedAfter = new LinkedHashSet<>();

        for (ChangedFile changed : changedFiles) {
            DSLNode beforeFile = beforeFiles.get(changed.beforePath());
            DSLNode afterFile = afterFiles.get(changed.afterPath());

            MatchResult fileResult = matchSingleFile(beforeFile, afterFile, beforeArgumentizationEnv, afterArgumentizationEnv);
            mergeResults(result, fileResult);

            if (changed.beforePath() != null) {
                consumedBefore.add(changed.beforePath());
            }
            if (changed.afterPath() != null) {
                consumedAfter.add(changed.afterPath());
            }
        }

        for (Map.Entry<String, DSLNode> entry : beforeFiles.entrySet()) {
            if (consumedBefore.contains(entry.getKey())) continue;

            MatchResult fileResult = matchSingleFile(entry.getValue(), null, beforeArgumentizationEnv, afterArgumentizationEnv);
            mergeResults(result, fileResult);
        }

        for (Map.Entry<String, DSLNode> entry : afterFiles.entrySet()) {
            if (consumedAfter.contains(entry.getKey())) continue;

            MatchResult fileResult = matchSingleFile(null, entry.getValue(), beforeArgumentizationEnv, afterArgumentizationEnv);
            mergeResults(result, fileResult);
        }

        return result;
    }

    private static Map<String, DSLNode> indexFilesByPath(DSLNode root) {
        Map<String, DSLNode> files = new LinkedHashMap<>();

        for (DSLNode fileNode : root.children) {
            if (fileNode.relativePath != null) {
                files.put(fileNode.relativePath, fileNode);
            }
        }

        return files;
    }

    private static MatchResult matchSingleFile(
            DSLNode beforeFile,
            DSLNode afterFile,
            Map<String, ExpressionNode> beforeArgumentizationEnv,
            Map<String, ExpressionNode> afterArgumentizationEnv
    ) {
        if (beforeFile == null && afterFile == null) {
            return new MatchResult();
        }

        if (beforeFile == null) {
            return StatementMatcher.matchTrees(
                    GradleTreeBuilder.emptyFileNodeLike(afterFile),
                    afterFile,
                    beforeArgumentizationEnv,
                    afterArgumentizationEnv
            );
        }

        if (afterFile == null) {
            return StatementMatcher.matchTrees(
                    beforeFile,
                    GradleTreeBuilder.emptyFileNodeLike(beforeFile),
                    beforeArgumentizationEnv,
                    afterArgumentizationEnv
            );
        }

        if (beforeFile.type == DSLNode.Type.PROPERTIES_FILE
                && afterFile.type == DSLNode.Type.PROPERTIES_FILE) {
            return PropertiesMatcher.matchPropertiesFile(beforeFile, afterFile);
        }

        return StatementMatcher.matchTrees(beforeFile, afterFile, beforeArgumentizationEnv, afterArgumentizationEnv);
    }

    private static void mergeResults(MatchResult target, MatchResult source) {
        target.matches.addAll(source.matches);

        target.addedComposite.addAll(source.addedComposite);
        target.removedComposite.addAll(source.removedComposite);

        target.addedLeaves.addAll(source.addedLeaves);
        target.removedLeaves.addAll(source.removedLeaves);

        target.leafRound1Matches.addAll(source.leafRound1Matches);
        target.leafRound2Matches.addAll(source.leafRound2Matches);
        target.leafRound3Matches.addAll(source.leafRound3Matches);

        target.compositeRound1Matches.addAll(source.compositeRound1Matches);
        target.compositeRound2Matches.addAll(source.compositeRound2Matches);
    }

    private static void printLlmResult(String llmPrompt) {
        if (!LLMSummary.apiEnabled()) {
            return;
        }

        try {
            String result = LLMSummary.callApi(llmPrompt);

            System.out.println("\n=== LLM RESULT ===");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("\n=== LLM RESULT ERROR ===");
            System.out.println(e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
        }
    }

    private static void printTrees(DSLNode beforeTree, DSLNode afterTree) {
        System.out.println("\n=== BEFORE TREE ===");
        DSLPrinter.print(beforeTree, 0);
        System.out.println("\n=== AFTER TREE ===");
        DSLPrinter.print(afterTree, 0);
    }

    private static void printRefactorings(List<BuildRefactoring> refactorings) {
        System.out.println("\n=== DETECTED REFACTORINGS ===");
        if (refactorings == null || refactorings.isEmpty()) {
            System.out.println("(none)");
            return;
        }

        for (BuildRefactoring refactoring : refactorings) {
            System.out.println(refactoring.type + ": " + refactoring.description);
        }
    }

    private static void printMatchResult(MatchResult result) {
        printNodes("REMOVED COMPOSITE NODES", result.removedComposite);
        printNodes("REMOVED LEAF NODES", result.removedLeaves);
        printNodes("ADDED COMPOSITE NODES", result.addedComposite);
        printNodes("ADDED LEAF NODES", result.addedLeaves);
        printMatches(result.matches);
    }

    private static void printNodes(String title, Collection<DSLNode> nodes) {
        System.out.println("\n=== " + title + " ===");
        if (nodes == null || nodes.isEmpty()) {
            System.out.println("(none)");
            return;
        }

        nodes.stream()
                .map(NodeFormat::formatNode)
                .sorted()
                .forEach(System.out::println);
    }

    private static void printMatches(Collection<MatchPair> matches) {
        System.out.println("\n=== MATCHED NODES ===");
        if (matches == null || matches.isEmpty()) {
            System.out.println("(none)");
            return;
        }

        matches.stream()
                .map(pair -> NodeFormat.formatNode(pair.left()) + " -> " + NodeFormat.formatNode(pair.right()))
                .sorted()
                .forEach(System.out::println);
    }

    private static void appendChangedFiles(StringBuilder report, List<ChangedFile> changedFiles) {
        appendSection(report, "CHANGED GRADLE FILES");
        if (changedFiles == null || changedFiles.isEmpty()) {
            report.append("(none)").append(System.lineSeparator());
            return;
        }

        for (ChangedFile file : changedFiles) {
            report.append(safe(file.beforePath()))
                    .append(" -> ")
                    .append(safe(file.afterPath()))
                    .append(System.lineSeparator());
        }
    }

    private static void appendNodes(StringBuilder report, String title, Collection<DSLNode> nodes) {
        appendSection(report, title);
        if (nodes == null || nodes.isEmpty()) {
            report.append("(none)").append(System.lineSeparator());
            return;
        }

        nodes.stream()
                .map(NodeFormat::formatNode)
                .sorted()
                .forEach(line -> report.append(line).append(System.lineSeparator()));
    }

    private static void appendMatches(StringBuilder report, String title, Collection<MatchPair> matches) {
        appendSection(report, title);
        if (matches == null || matches.isEmpty()) {
            report.append("(none)").append(System.lineSeparator());
            return;
        }

        matches.stream()
                .map(pair -> NodeFormat.formatNode(pair.left()) + " -> " + NodeFormat.formatNode(pair.right()))
                .sorted()
                .forEach(line -> report.append(line).append(System.lineSeparator()));
    }

    private static void appendSection(StringBuilder report, String title) {
        report.append(System.lineSeparator())
                .append("=== ")
                .append(title)
                .append(" ===")
                .append(System.lineSeparator());
    }

    private record AnalysisContext(
            List<ChangedFile> changedFiles,
            DSLNode beforeTree,
            DSLNode afterTree,
            MatchResult matchResult
    ) {
    }
}
