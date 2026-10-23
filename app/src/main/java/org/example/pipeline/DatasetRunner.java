package org.example.pipeline;

import org.example.LLM.LLMSummary;
import org.example.LLM.OneShotLLM;
import org.example.io.CsvRecords;
import org.example.refactoring.BuildRefactoring;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.io.EvaluationOutputWriter.createParentDirectories;
import static org.example.io.EvaluationOutputWriter.normalizeCommitUrl;
import static org.example.io.EvaluationOutputWriter.outputLine;
import static org.example.io.EvaluationOutputWriter.outputOrError;
import static org.example.io.EvaluationOutputWriter.safe;

public class DatasetRunner {
    private final CommitAnalyzer analyzer;

    public DatasetRunner() {
        this(new CommitAnalyzer());
    }

    public DatasetRunner(CommitAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void runDatasetDetections(Path datasetPath, Path outputPath) throws Exception {
        List<Map<String, String>> rows = CsvRecords.read(datasetPath);
        long total = countCommitRows(rows);

        createParentDirectories(outputPath);
        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            int processed = 0;
            for (Map<String, String> row : rows) {
                String commitUrl = normalizeCommitUrl(row.get("url"));
                if (commitUrl.isBlank()) {
                    continue;
                }

                processed++;
                String detected = "";
                String error = "";

                try {
                    List<BuildRefactoring> refactorings = analyzer.analyzeCommitDeterministic(commitUrl);
                    detected = refactorings.stream()
                            .map(ref -> ref.type + ": " + ref.description)
                            .collect(Collectors.joining(" | "));
                } catch (Exception e) {
                    error = e.getClass().getSimpleName() + ": " + safe(e.getMessage());
                }

                String outputLine = processed + "/" + total + " " + commitUrl + " -> "
                        + outputOrError(detected, error);

                writer.write(outputLine);
                writer.newLine();
                writer.flush();

                System.out.println(outputLine);

                if (!error.isBlank()) {
                    System.out.println("  error: " + error);
                }
            }
        }

        System.out.println("Wrote detected refactorings to " + outputPath.toAbsolutePath());
    }

    public void runDatasetDetections(
            Path datasetPath,
            Path detectorOutputPath,
            Path llmOutputPath
    ) throws Exception {
        runDatasetDetections(
                datasetPath,
                detectorOutputPath,
                llmOutputPath,
                defaultSuggestionsPath(llmOutputPath),
                defaultOneShotPath(llmOutputPath)
        );
    }

    public void runDatasetDetections(
            Path datasetPath,
            Path detectorOutputPath,
            Path llmOutputPath,
            Path suggestionsOutputPath
    ) throws Exception {
        runDatasetDetections(
                datasetPath,
                detectorOutputPath,
                llmOutputPath,
                suggestionsOutputPath,
                defaultOneShotPath(llmOutputPath)
        );
    }

    public void runDatasetDetections(
            Path datasetPath,
            Path detectorOutputPath,
            Path llmOutputPath,
            Path suggestionsOutputPath,
            Path oneShotOutputPath
    ) throws Exception {
        List<Map<String, String>> rows = CsvRecords.read(datasetPath);
        long total = countCommitRows(rows);

        createParentDirectories(detectorOutputPath);
        createParentDirectories(llmOutputPath);
        createParentDirectories(suggestionsOutputPath);
        createParentDirectories(oneShotOutputPath);

        try (
                var detectorWriter = Files.newBufferedWriter(detectorOutputPath, StandardCharsets.UTF_8);
                var llmWriter = Files.newBufferedWriter(llmOutputPath, StandardCharsets.UTF_8);
                var suggestionsWriter = Files.newBufferedWriter(suggestionsOutputPath, StandardCharsets.UTF_8);
                var oneShotWriter = Files.newBufferedWriter(oneShotOutputPath, StandardCharsets.UTF_8)
        ) {
            int processed = 0;
            for (Map<String, String> row : rows) {
                String commitUrl = normalizeCommitUrl(row.get("url"));
                if (commitUrl.isBlank()) {
                    continue;
                }

                processed++;

                List<String> detectorOutputs = List.of();
                List<String> llmOutputs = List.of();
                List<String> suggestedOutputs = List.of();
                List<String> oneShotOutputs = List.of();
                String detectorError = "";

                try {
                    AnalysisOutputs outputs = analyzer.analyzeCommitForOutputs(commitUrl);
                    detectorOutputs = outputs.detectorOutputs();
                    llmOutputs = outputs.llmOutputs();
                    suggestedOutputs = outputs.suggestedOutputs();
                    oneShotOutputs = outputs.oneShotOutputs();
                } catch (Exception e) {
                    detectorError = e.getClass().getSimpleName() + ": " + safe(e.getMessage());
                }

                String detectorLine = outputLine(processed, total, commitUrl, detectorOutputs, detectorError);
                String llmLine = outputLine(processed, total, commitUrl, llmOutputs);
                String suggestionsLine = outputLine(processed, total, commitUrl, suggestedOutputs);
                String oneShotLine = outputLine(processed, total, commitUrl, oneShotOutputs);

                detectorWriter.write(detectorLine);
                detectorWriter.newLine();
                detectorWriter.flush();

                llmWriter.write(llmLine);
                llmWriter.newLine();
                llmWriter.flush();

                suggestionsWriter.write(suggestionsLine);
                suggestionsWriter.newLine();
                suggestionsWriter.flush();

                oneShotWriter.write(oneShotLine);
                oneShotWriter.newLine();
                oneShotWriter.flush();

                System.out.println(detectorLine);
                System.out.println("LLM " + llmLine);
                System.out.println("LLM suggestions " + suggestionsLine);
                System.out.println("One-shot LLM " + oneShotLine);

                if (!detectorError.isBlank()) {
                    System.out.println("  detector/summary error: " + detectorError);
                }
            }
        }

        System.out.println("Wrote detected refactorings to " + detectorOutputPath.toAbsolutePath());
        System.out.println("Wrote LLM summary outputs to " + llmOutputPath.toAbsolutePath());
        System.out.println("Wrote LLM suggested refactorings to " + suggestionsOutputPath.toAbsolutePath());
        System.out.println("Wrote one-shot LLM outputs to " + oneShotOutputPath.toAbsolutePath());
    }

    public void runDatasetLlmOnly(
            Path datasetPath,
            Path llmOutputPath,
            Path suggestionsOutputPath
    ) throws Exception {
        if (!LLMSummary.apiEnabled()) {
            throw new IllegalStateException(
                    "LLM-only mode requires REFMINER_CALL_LLM=true, OPENROUTER_API_KEY, and OPENROUTER_MODEL"
            );
        }

        List<Map<String, String>> rows = CsvRecords.read(datasetPath);
        long total = countCommitRows(rows);

        createParentDirectories(llmOutputPath);
        createParentDirectories(suggestionsOutputPath);

        try (
                var llmWriter = Files.newBufferedWriter(llmOutputPath, StandardCharsets.UTF_8);
                var suggestionsWriter = Files.newBufferedWriter(suggestionsOutputPath, StandardCharsets.UTF_8)
        ) {
            int processed = 0;
            for (Map<String, String> row : rows) {
                String commitUrl = normalizeCommitUrl(row.get("url"));
                if (commitUrl.isBlank()) {
                    continue;
                }

                processed++;

                List<String> llmOutputs = List.of();
                List<String> suggestedOutputs = List.of();
                String llmError = "";

                try {
                    LlmOnlyOutputs outputs = analyzer.analyzeCommitForLlmOnlyOutputs(commitUrl);
                    llmOutputs = outputs.llmOutputs();
                    suggestedOutputs = outputs.suggestedOutputs();
                } catch (Exception e) {
                    llmError = e.getClass().getSimpleName() + ": " + safe(e.getMessage());
                }

                String llmLine = outputLine(processed, total, commitUrl, llmOutputs, llmError);
                String suggestionsLine = outputLine(processed, total, commitUrl, suggestedOutputs);

                llmWriter.write(llmLine);
                llmWriter.newLine();
                llmWriter.flush();

                suggestionsWriter.write(suggestionsLine);
                suggestionsWriter.newLine();
                suggestionsWriter.flush();

                System.out.println("LLM " + llmLine);
                System.out.println("LLM suggestions " + suggestionsLine);
                if (!llmError.isBlank()) {
                    System.out.println("  LLM summary error: " + llmError);
                }
            }
        }

        System.out.println("Wrote LLM summary outputs to " + llmOutputPath.toAbsolutePath());
        System.out.println("Wrote LLM suggested refactorings to " + suggestionsOutputPath.toAbsolutePath());
    }

    public void runDatasetOneShotOnly(
            Path datasetPath,
            Path oneShotOutputPath
    ) throws Exception {
        if (!OneShotLLM.apiEnabled()) {
            throw new IllegalStateException(
                    "One-shot LLM mode requires REFMINER_CALL_ONESHOT=true or REFMINER_CALL_LLM=true, plus OPENROUTER_API_KEY and OPENROUTER_ONESHOT_MODEL or OPENROUTER_MODEL"
            );
        }

        List<Map<String, String>> rows = CsvRecords.read(datasetPath);
        long total = countCommitRows(rows);

        createParentDirectories(oneShotOutputPath);

        try (var oneShotWriter = Files.newBufferedWriter(oneShotOutputPath, StandardCharsets.UTF_8)) {
            int processed = 0;
            for (Map<String, String> row : rows) {
                String commitUrl = normalizeCommitUrl(row.get("url"));
                if (commitUrl.isBlank()) {
                    continue;
                }

                processed++;

                List<String> oneShotOutputs = List.of();
                String oneShotError = "";

                try {
                    oneShotOutputs = analyzer.analyzeCommitForOneShotOutputs(commitUrl);
                } catch (Exception e) {
                    oneShotError = e.getClass().getSimpleName() + ": " + safe(e.getMessage());
                }

                String oneShotLine = outputLine(processed, total, commitUrl, oneShotOutputs, oneShotError);

                oneShotWriter.write(oneShotLine);
                oneShotWriter.newLine();
                oneShotWriter.flush();

                System.out.println("One-shot LLM " + oneShotLine);
                if (!oneShotError.isBlank()) {
                    System.out.println("  one-shot LLM error: " + oneShotError);
                }
            }
        }

        System.out.println("Wrote one-shot LLM outputs to " + oneShotOutputPath.toAbsolutePath());
    }

    public static Path defaultSuggestionsPath(Path llmOutputPath) {
        Path parent = llmOutputPath == null ? null : llmOutputPath.getParent();
        if (parent == null) {
            return Path.of("llm-suggestions.txt");
        }
        return parent.resolve("llm-suggestions.txt");
    }

    public static Path defaultOneShotPath(Path llmOutputPath) {
        Path parent = llmOutputPath == null ? null : llmOutputPath.getParent();
        if (parent == null) {
            return Path.of("oneshot-llm-output.txt");
        }
        return parent.resolve("oneshot-llm-output.txt");
    }

    private static long countCommitRows(List<Map<String, String>> rows) {
        return rows.stream()
                .map(row -> normalizeCommitUrl(row.get("url")))
                .filter(url -> !url.isBlank())
                .count();
    }
}
