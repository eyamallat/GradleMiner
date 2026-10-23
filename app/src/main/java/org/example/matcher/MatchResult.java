package org.example.matcher;

import org.example.model.DSLNode;

import java.util.HashSet;
import java.util.Set;

public class MatchResult {

    public final Set<MatchPair> matches = new HashSet<>();
    public final Set<DSLNode> removedComposite = new HashSet<>();
    public final Set<DSLNode> addedComposite = new HashSet<>();
    public final Set<DSLNode> removedLeaves = new HashSet<>();
    public final Set<DSLNode> addedLeaves = new HashSet<>();
    // matched per round
    public final Set<MatchPair> leafRound1Matches = new HashSet<>();
    public final Set<MatchPair> leafRound2Matches = new HashSet<>();
    public final Set<MatchPair> leafRound3Matches = new HashSet<>();

    public final Set<MatchPair> compositeRound1Matches = new HashSet<>();
    public final Set<MatchPair> compositeRound2Matches = new HashSet<>();

}
