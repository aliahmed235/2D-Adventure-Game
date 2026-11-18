package world;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit tile IDs keep existing maps compatible when the palette grows. */
public final class TileSet {
    private final Map<Integer, TileDefinition> definitions = new LinkedHashMap<>();

    public TileSet() {
        String resource = "/levels/tiles.properties";
        for (LevelLoader.Entry entry : LevelLoader.readEntries(resource).values()) {
            try {
                int id = Integer.parseInt(entry.key());
                String[] fields = entry.value().split("\\|", -1);
                if (id < 0 || fields.length != 2
                        || !(fields[1].equals("true") || fields[1].equals("false"))) {
                    throw new IllegalArgumentException("Expected nonnegative ID=texture|true/false");
                }
                String texture = fields[0].trim();
                if (!texture.startsWith("/") || TileSet.class.getResource(texture) == null) {
                    throw new IllegalArgumentException("Missing texture " + texture);
                }
                definitions.put(id, new TileDefinition(id, texture, Boolean.parseBoolean(fields[1])));
            } catch (RuntimeException e) {
                throw LevelLoader.failure(resource, entry.line(), e.getMessage());
            }
        }
        if (definitions.isEmpty()) throw LevelLoader.failure(resource, 1, "Tile palette is empty");
    }

    public TileDefinition definition(int id) {
        TileDefinition tile = definitions.get(id);
        if (tile == null) throw new IllegalArgumentException("Unknown tile ID " + id);
        return tile;
    }
    public Collection<TileDefinition> definitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }
    public boolean solid(int id) {
        TileDefinition tile = definitions.get(id);
        return tile == null || tile.solid();
    }
}
