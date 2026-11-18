package world;

import java.awt.geom.Rectangle2D;

/** Per-run state; the authored spawn remains unchanged when a rock is pushed. */
public final class WorldObject {
    private final ObjectSpawn spawn;
    private int col;
    private int row;
    private boolean removed;
    private boolean active;

    public WorldObject(ObjectSpawn spawn) {
        this.spawn = spawn;
        col = spawn.col();
        row = spawn.row();
    }
    public ObjectSpawn spawn() { return spawn; }
    public int col() { return col; }
    public int row() { return row; }
    public void moveTo(int col, int row) { this.col = col; this.row = row; }
    public boolean removed() { return removed; }
    public void setRemoved(boolean value) { removed = value; }
    public boolean active() { return active; }
    public void setActive(boolean value) { active = value; }

    public Rectangle2D.Double bounds(int tileSize) {
        double inset = tileSize * (6.0 / 64.0);
        return new Rectangle2D.Double(col * tileSize + inset,
                row * tileSize + inset, tileSize - inset * 2, tileSize - inset * 2);
    }

    public boolean solid() {
        if (removed) return false;
        return switch (spawn.type()) {
            case DOOR, GATE, ROCK, CRACKED_WALL -> true;
            case DRY_TREE -> !active;
            default -> false;
        };
    }
}
