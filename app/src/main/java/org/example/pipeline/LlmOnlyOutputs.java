package org.example.pipeline;

import java.util.List;

public record LlmOnlyOutputs(
        List<String> llmOutputs,
        List<String> suggestedOutputs
) {
}
