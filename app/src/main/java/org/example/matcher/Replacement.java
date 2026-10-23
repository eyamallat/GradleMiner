package org.example.matcher;

import java.util.Objects;

public record Replacement(
        ReplacementType type,
        String path,
        String before,
        String after
) {

    public Replacement {
        Objects.requireNonNull(type, "type");
        path = path == null ? "" : path.trim();
        before = normalize(before);
        after = normalize(after);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }


    public boolean isLeafFriendly() {
        return type.isLeafFriendly();
    }



}
