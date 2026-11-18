package world;

import entity.EnemyKind;

/** Enemy speed is measured in world pixels per second. */
public record EnemySpawn(String id, int col, int row, int health,
                         double speed, int patrolRadius, EnemyKind kind, String defeatFlag) {
    public EnemySpawn(String id, int col, int row, int health, double speed, int patrolRadius) {
        this(id, col, row, health, speed, patrolRadius, EnemyKind.GREEN_SLIME, "");
    }
}
