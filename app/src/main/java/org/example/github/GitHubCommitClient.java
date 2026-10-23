package org.example.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GitHubCommitClient {
    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

    public CommitInfo parseCommitUrl(String commitUrl) {
        if (commitUrl.startsWith("http://")) {
            commitUrl = commitUrl.replace("http://", "https://");
        }
        URI uri = URI.create(commitUrl);
        String[] parts = uri.getPath().split("/");
        if (parts.length < 5 || !"commit".equals(parts[3])) {
            throw new IllegalArgumentException("Invalid GitHub commit URL");
        }
        return new CommitInfo(parts[1], parts[2], parts[4]);
    }

    public List<ChangedFile> fetchChangedFiles(CommitInfo info) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<ChangedFile> files = new ArrayList<>();

        int page = 1;
        int perPage = 100;
        while (true) {
            String apiUrl = String.format(
                    "https://api.github.com/repos/%s/%s/commits/%s?per_page=%d&page=%d",
                    info.owner(), info.repo(), info.sha(), perPage, page
            );

            HttpRequest request = githubRequestBuilder(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed fetching changed files: " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode pageFiles = root.get("files");
            if (pageFiles == null || !pageFiles.isArray() || pageFiles.isEmpty()) {
                break;
            }

            for (JsonNode fileNode : pageFiles) {
                String afterPath = fileNode.get("filename").asText();

                String beforePath = afterPath;
                JsonNode previous = fileNode.get("previous_filename");
                if (previous != null && !previous.isNull()) {
                    beforePath = previous.asText();
                }

                String patch = "";
                JsonNode patchNode = fileNode.get("patch");
                if (patchNode != null && !patchNode.isNull()) {
                    patch = patchNode.asText();
                }

                files.add(new ChangedFile(beforePath, afterPath, patch));
            }

            if (pageFiles.size() < perPage) {
                break;
            }
            page++;
        }

        return files;
    }

    public String fetchParentCommit(CommitInfo info) throws Exception {
        String apiUrl = String.format(
                "https://api.github.com/repos/%s/%s/commits/%s",
                info.owner(), info.repo(), info.sha()
        );
        HttpRequest request = githubRequestBuilder(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed fetching parent commit: HTTP "
                            + response.statusCode()
                            + " - "
                            + githubErrorMessage(response)
            );
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode parents = root.get("parents");
        if (parents == null || !parents.isArray() || parents.isEmpty()) {
            throw new RuntimeException("Failed fetching parent commit: response has no parents");
        }

        JsonNode sha = parents.get(0).get("sha");
        if (sha == null || sha.isNull()) {
            throw new RuntimeException("Failed fetching parent commit: parent has no sha");
        }

        return sha.asText();
    }

    public Optional<String> fetchFileAtCommit(CommitInfo info, String sha, String path) {
        try {
            String rawUrl = String.format(
                    "https://raw.githubusercontent.com/%s/%s/%s/%s",
                    info.owner(), info.repo(), sha, path
            );

            HttpRequest request = githubRequestBuilder(rawUrl)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static HttpRequest.Builder githubRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Gradle-Commit-Parser");

        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }

        return builder;
    }

    private static String githubErrorMessage(HttpResponse<String> response) {
        try {
            JsonNode root = new ObjectMapper().readTree(response.body());
            JsonNode message = root.get("message");
            if (message != null && !message.isNull()) {
                return message.asText();
            }
        } catch (Exception ignored) {
        }
        return response.body();
    }
}
