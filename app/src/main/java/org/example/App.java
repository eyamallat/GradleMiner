package org.example;

import org.example.io.EvaluationOutputWriter;
import org.example.pipeline.CommitAnalyzer;

import java.nio.file.Path;

public class App {
    public static void main(String[] args) {
        try {
            if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
                printUsage();
                return;
            }
            runSingleCommit(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runSingleCommit(String[] args) throws Exception {
        boolean explicitCommitMode = "--commit".equals(args[0]);
        int commitIndex = explicitCommitMode ? 1 : 0;
        int outputIndex = explicitCommitMode ? 2 : 1;

        requireArgs(args, commitIndex + 1, "A GitHub commit URL is required");
        if (args.length > outputIndex + 1) {
            throw new IllegalArgumentException("Commit mode accepts only: [--commit] commit-url [output-file]");
        }

        String commitUrl = EvaluationOutputWriter.normalizeCommitUrl(args[commitIndex]);
        CommitAnalyzer analyzer = new CommitAnalyzer();
        String report = analyzer.analyzeCommitReport(commitUrl);
        System.out.print(report);

        if (args.length > outputIndex) {
            Path outputPath = Path.of(args[outputIndex]);
            EvaluationOutputWriter.createParentDirectories(outputPath);
            java.nio.file.Files.writeString(outputPath, report);
            System.out.println();
            System.out.println("Wrote commit analysis report to " + outputPath.toAbsolutePath());
        }
    }

    private static void requireArgs(String[] args, int minimum, String message) {
        if (args.length < minimum) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  ./gradlew :app:run --args="<github-commit-url> [output-file]"
                  ./gradlew :app:run --args="--commit <github-commit-url> [output-file]"

                The report includes:
                  - before-version DSL tree
                  - after-version DSL tree
                  - deleted composite and leaf nodes
                  - added composite and leaf nodes
                  - matched nodes
                  - detected Gradle refactoring types
                """);
    }

}
