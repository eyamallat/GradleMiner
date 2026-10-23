package org.example.pipeline;

import java.util.List;

public record AnalysisOutputs(
        List<String> detectorOutputs,
        List<String> llmOutputs,
        List<String> suggestedOutputs,
        List<String> oneShotOutputs
) {
}
