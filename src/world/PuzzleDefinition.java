package world;

import java.util.List;

/** A sequence of switch object IDs; the ID becomes a flag when solved. */
public record PuzzleDefinition(String id, List<String> sequence, String solvedMessage) {
    public PuzzleDefinition {
        sequence = List.copyOf(sequence);
    }
}
