package org.example.LLM;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.matcher.MatchPair;
import org.example.matcher.MatchResult;
import org.example.model.DSLNode;
import org.example.refactoring.BuildRefactoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.example.LLM.Taxonomy.REFACTORING_TYPES_TAXONOMY_AND_OUTPUT_FORMAT;
import static org.example.matcher.NodeFormat.formatNode;
import static org.example.refactoring.RuleUtils.isDescendantOf;
import static org.example.refactoring.RuleUtils.nodeKey;
import static org.example.refactoring.RuleUtils.safeList;

public final class LLMSummary {
    public record Summary(List<DSLNode> added, List<DSLNode> removed, List<MatchPair> modified) { }
    private static final URI OPENROUTER_URI = URI.create("https://openrouter.ai/api/v1/chat/completions");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DELIMITER = "####";

    static String taskPrompt = """
        Task:
        Given the commit changes, Added nodes, Removed nodes, and Modified matches below, identify any occurrences of the listed refactoring types.

        A refactoring is a structural change to Gradle build files that improves internal structure WITHOUT changing external behavior.

        Evidence priority:
        - Use the raw commit patch as the primary source of truth.
        - Use Added nodes, Removed nodes, and Modified matches as supporting evidence.
        - Do not infer a refactoring from matcher evidence alone when the raw patch does not support it.

        Detection rules:
        - Include behavior-preserving structural refactorings.
        - Return one item per distinct refactoring instance.
        - Multiple items with the same RefactoringType are allowed when they represent different concrete changes.
        - Do not report contradictory types for the same transformation; choose the most specific behavior-preserving explanation.
        - Each transformation should have at most one primary refactoring type.
        - If the exact source, target, name, or location required by the output format cannot be inferred from the raw patch or matcher evidence, omit the instance.

        Location handling:
        - When Added nodes, Removed nodes, or Modified matches provide an exact location in the form 'path:name (line)', copy that location exactly in the Output.
        - Prefer matcher-provided node locations over locations inferred from the raw patch.
        - Do not invent, adjust, or renumber line numbers.
        - Do not replace a specific matcher location with a more general name.

        Granular suppression:
        Module extraction is a coarse-grained refactoring. If EXTRACT_MODULE, EXTRACT_AND_PUSH_DOWN_MODULE, or EXTRACT_AND_PULL_UP_MODULE explains a larger source-to-target movement of Gradle build logic, do not also report smaller refactorings whose evidence is contained inside that same movement.
        Suppress granular MOVE_VARIABLE, MOVE_DEPENDENCY, PUSH_DOWN_VARIABLE, PUSH_DOWN_DEPENDENCY, PULL_UP_VARIABLE, PULL_UP_DEPENDENCY, PUSH_DOWN_TASK, DRY, PULL_UP_METHOD and REMOVE_UNUSED_CODE when their removed nodes, added nodes, or before/after matches are internal pieces of the extracted module.
        Keep the granular refactoring only when it has independent evidence outside the module extraction boundary.

        After identifying all other refactoring types, review the removed Gradle files and removed Gradle build elements one by one. For every deleted item that is not explained by another reported refactoring,is not matched to an after-version node and does not still exist elsewhere in the after-version build files, output one REMOVE_UNUSED_CODE instance.
        
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

        """;
    // LLM_PROMPT
    public static String toPrompt(MatchResult matchResult, Map<String, String> commitPatches) {
        return tPrompt(matchResult, commitPatches);
    }
    public static String tPrompt(MatchResult matchResult, Map<String, String> commitPatches) {

        Summary summary = matcherSummary(matchResult);
        StringBuilder out = new StringBuilder();

        out.append(taskPrompt);
        appendTaxonomyAndExpectedOutputFormat(out);
        appendOneShotExamples(out);
        out.append("Now analyze the following evidence:\n");
        appendPatchSection(out, commitPatches);
        appendNodeSection(out, "Added nodes", summary.added());
        appendNodeSection(out, "Removed nodes", summary.removed());
        appendMatchSection(out, summary.modified());
        return out.toString();
    }

    private static void appendPatchSection(StringBuilder out, Map<String, String> commitPatches) {
        if (commitPatches == null || commitPatches.isEmpty()) {
            out.append(DELIMITER).append("Commit Changes:\n");
            out.append("No relevant file changes\n");
            out.append(DELIMITER).append("\n\n");
            return;
        }

        out.append(DELIMITER).append("Commit Changes:\n");
        for (Map.Entry<String, String> entry : commitPatches.entrySet()) {
            if (entry == null) continue;

            String path = safe(entry.getKey());
            String patch = safe(entry.getValue());
            if (path.isBlank() || patch.isBlank()) continue;

            out.append(path).append(":\n");
            out.append(patch).append("\n\n");
        }
        out.append(DELIMITER).append("\n\n");
    }
    private static void appendTaxonomyAndExpectedOutputFormat(StringBuilder out) {
        out.append("This is a list of refactoring types in build files:\n\n").append(REFACTORING_TYPES_TAXONOMY_AND_OUTPUT_FORMAT).append("\n\n");
    }
    private static void appendOneShotExamples(StringBuilder out) {
        out.append("One-shot examples:\n");
        out.append(OneShotExamples.EXAMPLES).append("\n\n");
    }
    private static void appendNodeSection(StringBuilder out, String title, List<DSLNode> nodes) {
        out.append(title).append(":\n");
        if (nodes == null || nodes.isEmpty()) {
            out.append("- none\n\n");
            return;
        }

        for (DSLNode node : nodes) {
            out.append("- ").append(formatNode(node)).append('\n');
        }
        out.append('\n');
    }
    private static void appendMatchSection(StringBuilder out, List<MatchPair> matches) {

        out.append("Modified matches").append(":\n");
        if (matches == null || matches.isEmpty()) {
            out.append("- none\n\n");
            return;
        }

        for (MatchPair pair : matches) {
            out.append("- before: ").append(formatNode(pair.left())).append('\n');
            out.append("  after:  ").append(formatNode(pair.right())).append('\n');
        }
        out.append('\n');
    }
    //MATCHER_SUMMARY
    private static Summary matcherSummary(MatchResult matchResult) {
        if (matchResult == null) return new Summary(List.of(), List.of(), List.of());


        Set<DSLNode> allAdded =   changedNodes(matchResult.addedComposite, matchResult.addedLeaves);
        Set<DSLNode> allRemoved = changedNodes(matchResult.removedComposite, matchResult.removedLeaves);

        return new Summary(removeNestedNodes(new ArrayList<>(allAdded)), removeNestedNodes(new ArrayList<>(allRemoved)),
                modifiedUnexplainedMatches(matchResult.matches, List.of())
        );
    }
    private static Set<DSLNode> changedNodes(Collection<DSLNode> first, Collection<DSLNode> second) {
        Set<DSLNode> out = new LinkedHashSet<>();
        if (first != null) out.addAll(first);
        if (second != null) out.addAll(second);
        return out;
    }
    private static List<DSLNode> removeNestedNodes(List<DSLNode> nodes) {
        List<DSLNode> out = new ArrayList<>();

        for (DSLNode node : safeList(nodes)) {
            if (node == null) continue;

            boolean nested = false;
            for (DSLNode other : safeList(nodes)) {
                if (other == null || other == node) continue;
                if (sameFile(node, other) && isDescendantOf(node, other)) {
                    nested = true;
                    break;
                }
            }

            if (!nested) out.add(node);
        }

        return out;
    }
    private static List<MatchPair> modifiedUnexplainedMatches(Collection<MatchPair> matches, Collection<MatchPair> explainedMatches) {
        List<MatchPair> out = new ArrayList<>();

        for (MatchPair pair : safeCollection(matches)) {
            if (pair == null || pair.left() == null || pair.right() == null) continue;
            if (explainedMatches != null && explainedMatches.contains(pair)) continue;
            if (!Objects.equals(nodeKey(pair.left()), nodeKey(pair.right()))) {
                out.add(pair);
            }
        }

        return out;
    }
    //THE_MATCHER_RESULTS
    public static Summary from(MatchResult matchResult, List<BuildRefactoring> refactorings) {
        if (matchResult == null) return new Summary(List.of(), List.of(), List.of());


        Set<DSLNode> allAdded   = changedNodes(matchResult.addedComposite, matchResult.addedLeaves);
        Set<DSLNode> allRemoved = changedNodes(matchResult.removedComposite, matchResult.removedLeaves);

        Set<DSLNode> ExplainedAdded = new LinkedHashSet<>();
        Set<DSLNode> ExplainedRemoved = new LinkedHashSet<>();
        Set<MatchPair> explainedMatches = new LinkedHashSet<>();

        for (BuildRefactoring ref : safeList(refactorings)) {
            if (ref == null) continue;

            explainedMatches.addAll(safeList(ref.relatedMatches));
            ExplainedAdded.addAll(safeList(ref.addedNodes));
            ExplainedRemoved.addAll(safeList(ref.removedNodes));

        }

        List<DSLNode> unexplainedAdded = nodesNotCoveredBy(allAdded, ExplainedAdded);
        List<DSLNode> unexplainedRemoved = nodesNotCoveredBy(allRemoved, ExplainedRemoved);
        List<MatchPair> modifiedMatches = modifiedUnexplainedMatches(matchResult.matches, explainedMatches);

        return new Summary(removeNestedNodes(unexplainedAdded), removeNestedNodes(unexplainedRemoved), modifiedMatches);
    }
    private static List<DSLNode> nodesNotCoveredBy(Collection<DSLNode> nodes, Collection<DSLNode> coveringRoots) {
        List<DSLNode> out = new ArrayList<>();
        for (DSLNode node : safeCollection(nodes)) {
            if (!isCoveredBy(node, coveringRoots)) {
                out.add(node);
            }
        }
        return out;
    }
    private static boolean isCoveredBy(DSLNode node, Collection<DSLNode> coveringRoots) {
        if (node == null || coveringRoots == null) return false;

        for (DSLNode root : coveringRoots) {
            if (root == null) continue;
            if (node == root) return true;
        }

        return false;
    }
    private static boolean sameFile(DSLNode a, DSLNode b) {
        return a != null
                && b != null
                && Objects.equals(a.relativePath, b.relativePath);
    }
    // API_CALL
    public static boolean apiEnabled() {
        return "true".equalsIgnoreCase(safe(System.getenv("REFMINER_CALL_LLM")));
    }
    public static String callApi(String prompt) throws Exception {
        String apiKey = safe(System.getenv("OPENROUTER_API_KEY"));
        String model = safe(System.getenv("OPENROUTER_MODEL"));

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
                .uri(OPENROUTER_URI).header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8)).build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("OpenRouter API failed: HTTP " + response.statusCode() + " - " + response.body());
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
    public static List<String> detectedOutputs(String llmJson) throws Exception {
        String json = safe(llmJson);
        if (json.isBlank()) return List.of();

        JsonNode root = MAPPER.readTree(json);
        JsonNode detected = root.path("DetectedRefactoringTypes");
        if (!detected.isArray()) return List.of();

        List<String> outputs = new ArrayList<>();
        for (JsonNode item : detected) {
            String output = safe(item.path("Output").asText());
            if (!output.isBlank()) {
                outputs.add(output);
            }
        }
        return outputs;
    }
    public static List<String> suggestedOutputs(String llmJson) throws Exception {
        String json = safe(llmJson);
        if (json.isBlank()) return List.of();

        JsonNode root = MAPPER.readTree(json);
        JsonNode suggested = root.path("SuggestedNewRefactorings");
        if (!suggested.isArray()) return List.of();

        List<String> outputs = new ArrayList<>();
        for (JsonNode item : suggested) {
            String type = safe(item.path("ProposedType").asText());
            String definition = safe(item.path("Definition").asText());

            if (!type.isBlank()) {
                outputs.add(definition.isBlank() ? type : type + ": " + definition);
            }
        }
        return outputs;
    }
    //SAFE
    private static <T> Collection<T> safeCollection(Collection<T> values) {
        return values == null ? List.of() : values;
    }
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
