package org.example.matcher;

import org.example.model.DSLNode;
import org.jspecify.annotations.NonNull;

public record MatchPair(DSLNode left, DSLNode right) {

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MatchPair(DSLNode left1, DSLNode right1))) return false;
        return left == left1 && right == right1;
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(left) + System.identityHashCode(right);
    }

    @Override
    public @NonNull String toString() {
        return left + " -> " + right;
    }
}