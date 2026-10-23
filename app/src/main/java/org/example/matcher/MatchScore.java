package org.example.matcher;

public record MatchScore(double matchedChildrenRatio,
                         int exactMatches,
                         int matchedStatements,
                         int statementEditDistance,
                         int depthDifference,
                         int siblingIndexDifference,
                         int nameEditDistance
                         ) {

}