package org.example.LLM;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.example.LLM.Taxonomy.REFACTORING_TYPES_TAXONOMY_AND_OUTPUT_FORMAT;

public final class OneShotLLM {
    private static final URI OPENROUTER_URI = URI.create("https://openrouter.ai/api/v1/chat/completions");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DELIMITER = "####";

    public static final String INSTRUCTIONS = """
        Task:
        Given the commit changes below that are applied on Gradle build scripts and delimited with %s, identify any occurrences of the listed refactoring types.

        A refactoring is a structural change to Gradle build files that improves internal structure WITHOUT changing external behavior.

        Detection rules:
        - Include behavior-preserving structural refactorings.
        - Return one item per distinct refactoring instance.
        - Multiple items with the same RefactoringType are allowed when they represent different concrete changes.
        - Do not report contradictory types for the same transformation; choose the most specific behavior-preserving explanation.
        - Each transformation should have at most one primary refactoring type.
        - If the exact source, target, or name required by the output format cannot be inferred from the commit patch, omit the instance.

        Location handling:
        - Use the most specific file path and entity name visible in the commit patch.
        - Use line numbers only when they are directly available from the patch hunk context; otherwise do not invent them.
        - Do not use placeholder line numbers such as 1 or 0.

        Granular suppression:
        - Module extraction is a coarse-grained refactoring. If EXTRACT_MODULE, EXTRACT_AND_PUSH_DOWN_MODULE, or EXTRACT_AND_PULL_UP_MODULE explains a larger source-to-target movement of Gradle build logic, do not also report smaller refactorings whose evidence is contained inside that same movement.
        - Suppress granular MOVE_VARIABLE, MOVE_DEPENDENCY, PUSH_DOWN_VARIABLE, PUSH_DOWN_DEPENDENCY, PULL_UP_VARIABLE, PULL_UP_DEPENDENCY, PUSH_DOWN_TASK, DRY, PULL_UP_METHOD, and REMOVE_UNUSED_CODE when the removed and added patch hunks are internal pieces of the same larger module extraction.
        - Keep the granular refactoring only when it has independent evidence outside the module extraction boundary.

        After identifying all other refactoring types, review the removed Gradle files and removed Gradle build elements one by one. For every deleted item that is not explained by another reported refactoring and does not still exist elsewhere in the after-version build files, output one REMOVE_UNUSED_CODE instance.

        Return valid JSON only.
        The RefactoringType value must exactly match one taxonomy heading before the colon.
        The Output value must be one single-line JSON string and must follow one of the taxonomy output patterns exactly.
        Preserve entity names as shown in the evidence unless the taxonomy output convention requires normalization.
        Use exactly this schema:
        {
          "DetectedRefactoringTypes": [
            {
              "RefactoringType": "string",
              "Output": "single-line detector output string"
            }
          ]
        }
        If no refactorings are detected, return {"DetectedRefactoringTypes":[]}.
        """.formatted(DELIMITER);

    private OneShotLLM() {
    }

    public static boolean apiEnabled() {
        return "true".equalsIgnoreCase(safe(System.getenv("REFMINER_CALL_ONESHOT")))
                || "true".equalsIgnoreCase(safe(System.getenv("REFMINER_CALL_LLM")));
    }

    public static String promptFor(Map<String, String> commitPatches) {
        String refactoringTypesPrompt = "This is a list of refactoring types in build files:\n\n"
                + REFACTORING_TYPES_TAXONOMY_AND_OUTPUT_FORMAT
                + "\n\n";

        return INSTRUCTIONS
                + "\n\n"
                + refactoringTypesPrompt
                + "One-shot examples:\n"
                + OneShotExamples.EXAMPLES
                + "\n\n"
                + "Now analyze ONLY the following commit changes:\n"
                + formatCommitChanges(commitPatches);
    }

    private static String formatCommitChanges(Map<String, String> commitPatches) {
        StringBuilder out = new StringBuilder();
        out.append(DELIMITER).append("Commit Changes:\n");

        if (commitPatches != null) {
            for (Map.Entry<String, String> entry : commitPatches.entrySet()) {
                String path = safe(entry.getKey());
                String patch = safe(entry.getValue());
                if (path.isBlank() || patch.isBlank()) continue;

                out.append(path).append(":\n");
                out.append(patch).append("\n\n");
            }
        }

        out.append(DELIMITER);
        return out.toString();
    }

    public static String callApi(String prompt) throws Exception {
        String apiKey = safe(System.getenv("OPENROUTER_API_KEY"));
        String model = safe(System.getenv("OPENROUTER_ONESHOT_MODEL"));
        if (model.isBlank()) {
            model = safe(System.getenv("OPENROUTER_MODEL"));
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not set");
        }
        if (model.isBlank()) {
            throw new IllegalStateException("OPENROUTER_ONESHOT_MODEL or OPENROUTER_MODEL is not set");
        }

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 8192);
        ObjectNode reasoning = MAPPER.createObjectNode();
        reasoning.put("effort", "medium");
        body.set("reasoning", reasoning);
        ObjectNode responseFormat = MAPPER.createObjectNode();
        responseFormat.put("type", "json_object");
        body.set("response_format", responseFormat);

        ObjectNode message = MAPPER.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);
        body.putArray("messages").add(message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(OPENROUTER_URI)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("OpenRouter one-shot API failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode() && !usage.isNull()) {
            System.out.println("LLM token usage:");
            System.out.println("prompt_tokens = " + usage.path("prompt_tokens").asLong());
            System.out.println("completion_tokens = " + usage.path("completion_tokens").asLong());
            System.out.println("total_tokens = " + usage.path("total_tokens").asLong());
        }
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            String finishReason = choice.path("finish_reason").asText();
            JsonNode content = choice.path("message").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                String json = content.asText();

                if ("length".equals(finishReason)) {
                    throw new RuntimeException("LLM output was truncated. Increase max_tokens or shorten the prompt.");
                }

                try {
                    MAPPER.readTree(json);
                } catch (Exception e) {
                    throw new RuntimeException("LLM returned invalid or incomplete JSON: " + json);
                }

                return json;
            }
        }

        return response.body();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
