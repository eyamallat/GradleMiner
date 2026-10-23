package org.example.github;

public record ChangedFile(String beforePath, String afterPath, String patch) {
}
