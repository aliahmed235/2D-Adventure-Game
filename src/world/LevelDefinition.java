package world;

import java.util.List;

/** Level content is immutable, so a new run can create fresh runtime state. */
public record LevelDefinition(String id, String name, String objective, String music,
                              int spawnCol, int spawnRow, String mapPath,
                              List<ObjectSpawn> objects, List<EnemySpawn> enemies,
                              List<PuzzleDefinition> puzzles, boolean dark) {
    public LevelDefinition {
        objects = List.copyOf(objects);
        enemies = List.copyOf(enemies);
        puzzles = List.copyOf(puzzles);
    }
}
