package world;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Runtime map state. Coordinates are column, row; storage is map[row][column]. */
public final class Level {
    private final LevelDefinition definition;
    private final int[][] map;
    private final List<WorldObject> objects = new ArrayList<>();

    public Level(LevelDefinition definition, int[][] map) {
        if (map.length == 0 || map[0].length == 0) {
            throw new IllegalArgumentException("Level map cannot be empty");
        }
        this.definition = definition;
        this.map = new int[map.length][];
        for (int row = 0; row < map.length; row++) {
            if (map[row].length != map[0].length) {
                throw new IllegalArgumentException("Level map must be rectangular");
            }
            this.map[row] = map[row].clone();
        }
        for (ObjectSpawn spawn : definition.objects()) objects.add(new WorldObject(spawn));
    }

    public LevelDefinition definition() { return definition; }
    public int width() { return map[0].length; }
    public int height() { return map.length; }
    public List<WorldObject> objects() { return objects; }

    public int tileAt(int col, int row) {
        return col < 0 || row < 0 || col >= width() || row >= height() ? -1 : map[row][col];
    }

    /** Predict movement without mutating hitboxes. The outside of a map is solid. */
    public boolean blocked(Rectangle2D box, TileSet tiles, int tileSize) {
        if (box.getMinX() < 0 || box.getMinY() < 0
                || box.getMaxX() > width() * tileSize || box.getMaxY() > height() * tileSize) {
            return true;
        }
        int left = (int) Math.floor(box.getMinX() / tileSize);
        int right = (int) Math.floor(Math.nextDown(box.getMaxX()) / tileSize);
        int top = (int) Math.floor(box.getMinY() / tileSize);
        int bottom = (int) Math.floor(Math.nextDown(box.getMaxY()) / tileSize);
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                if (tiles.solid(tileAt(col, row))) return true;
            }
        }
        for (WorldObject object : objects) {
            if (object.solid() && object.bounds(tileSize).intersects(box)) return true;
        }
        return false;
    }
}
