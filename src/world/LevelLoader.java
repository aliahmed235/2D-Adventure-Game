package world;

import entity.EnemyKind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads small UTF-8 data files and reports content errors at their source. */
public final class LevelLoader {
    private final TileSet tiles;
    record Entry(String key, String value, int line) { }

    public LevelLoader(TileSet tiles) { this.tiles = tiles; }

    public LinkedHashMap<String, LevelDefinition> loadCatalog() {
        String resource = "/levels/catalog.txt";
        LinkedHashMap<String, LevelDefinition> levels = new LinkedHashMap<>();
        List<String> lines = readLines(resource);
        for (int i = 0; i < lines.size(); i++) {
            String id = clean(lines.get(i));
            if (id.isEmpty()) continue;
            if (!id.matches("[a-zA-Z0-9_-]+")) throw failure(resource, i + 1, "Invalid level ID " + id);
            if (levels.containsKey(id)) throw failure(resource, i + 1, "Duplicate level ID " + id);
            levels.put(id, readDefinition(id));
        }
        if (levels.isEmpty()) throw failure(resource, 1, "No levels in catalog");

        // Validate all map resources and links immediately, rather than crashing on entry.
        Map<String, Level> maps = new LinkedHashMap<>();
        for (LevelDefinition definition : levels.values()) maps.put(definition.id(), load(definition));
        Set<String> flags = new HashSet<>();
        for (LevelDefinition definition : levels.values()) {
            for (ObjectSpawn object : definition.objects()) {
                if (object.type() == ObjectType.CHEST && !object.argument().isBlank()) flags.add(object.argument());
                if (object.type() == ObjectType.PLATE) flags.add(object.argument());
                if (object.type() == ObjectType.NPC) {
                    String grant = object.argument().split("~", -1)[1];
                    if (!grant.isBlank()) flags.add(grant);
                }
            }
            for (PuzzleDefinition puzzle : definition.puzzles()) flags.add(puzzle.id());
            for (EnemySpawn enemy : definition.enemies()) if (!enemy.defeatFlag().isBlank()) flags.add(enemy.defeatFlag());
        }
        for (LevelDefinition definition : levels.values()) {
            for (ObjectSpawn object : definition.objects()) {
                String source = "/levels/" + definition.id() + ".properties";
                Entry entry = readEntries(source).get("object." + object.id());
                if (!object.requires().isBlank()) {
                    for (String flag : object.requires().split("\\+", -1)) {
                        if (!flags.contains(flag)) throw failure(source, entry.line(), "Unknown required flag " + flag);
                    }
                }
                if (object.type() != ObjectType.PORTAL) continue;
                String[] target = object.argument().split(":", -1);
                try {
                    if (target.length != 3 || !maps.containsKey(target[0])) {
                        throw new IllegalArgumentException("Portal needs a catalog target:col:row");
                    }
                    int col = Integer.parseInt(target[1]);
                    int row = Integer.parseInt(target[2]);
                    checkWalkable(maps.get(target[0]), col, row, "Portal landing");
                    for (WorldObject landingObject : maps.get(target[0]).objects()) {
                        if (landingObject.solid() && landingObject.spawn().col() == col
                                && landingObject.spawn().row() == row) {
                            throw new IllegalArgumentException("Portal landing is blocked by an object");
                        }
                    }
                } catch (RuntimeException e) {
                    throw failure(source, entry.line(), e.getMessage());
                }
            }
        }
        return levels;
    }

    private LevelDefinition readDefinition(String id) {
        String resource = "/levels/" + id + ".properties";
        Map<String, Entry> entries = readEntries(resource);
        String name = required(entries, "name", resource);
        String objective = required(entries, "objective", resource);
        String music = required(entries, "music", resource);
        if (LevelLoader.class.getResource(music) == null) {
            throw failure(resource, entries.get("music").line(), "Missing music " + music);
        }
        String map = required(entries, "map", resource);
        boolean dark = false;
        if (entries.containsKey("dark")) {
            Entry entry = entries.get("dark");
            if (!entry.value().equals("true") && !entry.value().equals("false")) {
                throw failure(resource, entry.line(), "Expected dark=true or dark=false");
            }
            dark = Boolean.parseBoolean(entry.value());
        }
        String[] spawn = required(entries, "spawn", resource).split("\\|", -1);
        int spawnCol;
        int spawnRow;
        try {
            if (spawn.length != 2) throw new IllegalArgumentException("Expected spawn=col|row");
            spawnCol = Integer.parseInt(spawn[0]);
            spawnRow = Integer.parseInt(spawn[1]);
        } catch (RuntimeException e) {
            throw failure(resource, entries.get("spawn").line(), "Invalid spawn: " + e.getMessage());
        }
        List<ObjectSpawn> objects = new ArrayList<>();
        List<EnemySpawn> enemies = new ArrayList<>();
        List<PuzzleDefinition> puzzles = new ArrayList<>();
        for (Entry entry : entries.values()) {
            try {
                String key = entry.key();
                if (key.startsWith("object.")) {
                    String[] fields = entry.value().split("\\|", -1);
                    if (fields.length != 5) throw new IllegalArgumentException("Expected TYPE|col|row|argument|requires");
                    ObjectSpawn object = new ObjectSpawn(key.substring(7), ObjectType.valueOf(fields[0]),
                            Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), fields[3], fields[4]);
                    validateObject(object);
                    objects.add(object);
                } else if (key.startsWith("enemy.")) {
                    String[] fields = entry.value().split("\\|", -1);
                    if (fields.length < 5 || fields.length > 7) throw new IllegalArgumentException("Expected col|row|health|speed|patrolRadius[|KIND|defeatFlag]");
                    EnemySpawn enemy = new EnemySpawn(key.substring(6), Integer.parseInt(fields[0]),
                            Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                            Double.parseDouble(fields[3]), Integer.parseInt(fields[4]),
                            fields.length >= 6 ? EnemyKind.valueOf(fields[5]) : EnemyKind.GREEN_SLIME,
                            fields.length == 7 ? fields[6] : "");
                    if (enemy.id().isBlank() || enemy.health() < 1 || !Double.isFinite(enemy.speed())
                            || enemy.speed() <= 0 || enemy.patrolRadius() < 0) {
                        throw new IllegalArgumentException("Enemy needs ID, health>0, speed>0, radius>=0");
                    }
                    enemies.add(enemy);
                } else if (key.startsWith("puzzle.")) {
                    String[] fields = entry.value().split("\\|", -1);
                    if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()) {
                        throw new IllegalArgumentException("Expected switch-id+switch-id|solved message");
                    }
                    String puzzleId = key.substring(7);
                    if (puzzleId.isBlank()) throw new IllegalArgumentException("Puzzle needs an ID");
                    puzzles.add(new PuzzleDefinition(puzzleId, List.of(fields[0].split("\\+", -1)), fields[1]));
                } else if (!Set.of("name", "objective", "music", "map", "spawn", "dark").contains(key)) {
                    throw new IllegalArgumentException("Unknown level property " + key);
                }
            } catch (RuntimeException e) {
                throw failure(resource, entry.line(), e.getMessage());
            }
        }
        Map<String, ObjectSpawn> objectById = new LinkedHashMap<>();
        for (ObjectSpawn object : objects) objectById.put(object.id(), object);
        for (PuzzleDefinition puzzle : puzzles) {
            Set<String> sequenceIds = new HashSet<>();
            for (String switchId : puzzle.sequence()) {
                ObjectSpawn object = objectById.get(switchId);
                if (!sequenceIds.add(switchId) || object == null || object.type() != ObjectType.SWITCH
                        || !object.argument().equals(puzzle.id())) {
                    throw failure(resource, entries.get("puzzle." + puzzle.id()).line(),
                            "Puzzle sequence needs unique SWITCH IDs with argument " + puzzle.id());
                }
            }
        }
        for (ObjectSpawn object : objects) {
            if (object.type() == ObjectType.SWITCH && puzzles.stream().noneMatch(p -> p.id().equals(object.argument())
                    && p.sequence().contains(object.id()))) {
                throw failure(resource, entries.get("object." + object.id()).line(),
                        "Switch must appear in puzzle " + object.argument());
            }
        }
        return new LevelDefinition(id, name, objective, music, spawnCol, spawnRow, map, objects, enemies, puzzles, dark);
    }

    public Level load(LevelDefinition definition) {
        String resource = definition.mapPath();
        List<int[]> rows = new ArrayList<>();
        List<String> lines = readLines(resource);
        int width = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = clean(lines.get(i));
            if (line.isEmpty()) continue;
            String[] fields = line.split("\\s+");
            if (width < 0) width = fields.length;
            if (fields.length != width) throw failure(resource, i + 1, "Expected " + width + " columns, found " + fields.length);
            int[] row = new int[width];
            for (int col = 0; col < width; col++) {
                try {
                    row[col] = Integer.parseInt(fields[col]);
                    tiles.definition(row[col]);
                } catch (RuntimeException e) {
                    throw failure(resource, i + 1, "Column " + (col + 1) + ": " + e.getMessage());
                }
            }
            rows.add(row);
        }
        if (rows.isEmpty()) throw failure(resource, 1, "Map is empty");
        Level level = new Level(definition, rows.toArray(int[][]::new));
        String source = "/levels/" + definition.id() + ".properties";
        Map<String, Entry> entries = readEntries(source);
        try {
            checkWalkable(level, definition.spawnCol(), definition.spawnRow(), "Spawn");
        } catch (RuntimeException e) {
            throw failure(source, entries.get("spawn").line(), e.getMessage());
        }
        Set<String> occupied = new HashSet<>();
        for (ObjectSpawn object : definition.objects()) {
            try {
                checkWalkable(level, object.col(), object.row(), "Object " + object.id());
                if (!occupied.add(object.col() + "," + object.row())) {
                    throw new IllegalArgumentException("Two objects share column " + object.col() + ", row " + object.row());
                }
                if (new WorldObject(object).solid()
                        && object.col() == definition.spawnCol() && object.row() == definition.spawnRow()) {
                    throw new IllegalArgumentException("The level spawn is blocked by " + object.id());
                }
            } catch (RuntimeException e) {
                throw failure(source, entries.get("object." + object.id()).line(), e.getMessage());
            }
        }
        for (EnemySpawn enemy : definition.enemies()) {
            try {
                checkWalkable(level, enemy.col(), enemy.row(), "Enemy " + enemy.id());
                for (WorldObject object : level.objects()) {
                    if (object.solid() && object.spawn().col() == enemy.col() && object.spawn().row() == enemy.row()) {
                        throw new IllegalArgumentException("Enemy " + enemy.id() + " spawns inside a blocking object");
                    }
                }
            } catch (RuntimeException e) {
                throw failure(source, entries.get("enemy." + enemy.id()).line(), e.getMessage());
            }
        }
        return level;
    }

    private void checkWalkable(Level level, int col, int row, String label) {
        if (col < 0 || row < 0 || col >= level.width() || row >= level.height()) {
            throw new IllegalArgumentException(label + " is outside the " + level.width() + "x" + level.height() + " map");
        }
        if (tiles.solid(level.tileAt(col, row))) {
            throw new IllegalArgumentException(label + " is on solid terrain at " + col + "," + row);
        }
    }

    private static void validateObject(ObjectSpawn object) {
        if (object.id().isBlank()) throw new IllegalArgumentException("Object needs an ID");
        if ((object.type() == ObjectType.CHEST || object.type() == ObjectType.PLATE) && object.argument().isBlank()) {
            throw new IllegalArgumentException(object.type() + " needs a flag argument");
        }
        if (object.type() == ObjectType.NPC) {
            String[] fields = object.argument().split("~", -1);
            if (fields.length != 3 || !fields[0].equals("oldman") || fields[2].isBlank()) {
                throw new IllegalArgumentException("NPC needs oldman~grantFlag~dialogue (grantFlag may be empty)");
            }
        }
        if (object.type() == ObjectType.MERCHANT) {
            try {
                if (!object.argument().isBlank() && Integer.parseInt(object.argument()) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Merchant price must be a positive integer or blank for 8 coins");
            }
        }
    }

    private static String required(Map<String, Entry> entries, String key, String resource) {
        Entry entry = entries.get(key);
        if (entry == null || entry.value().isBlank()) throw failure(resource, entry == null ? 1 : entry.line(), "Missing " + key);
        return entry.value();
    }

    /* The helpers below deliberately implement a small subset of properties:
       literal UTF-8 text, no continuation lines, and only whole-line # comments. */
    static LinkedHashMap<String, Entry> readEntries(String resource) {
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        List<String> lines = readLines(resource);
        for (int i = 0; i < lines.size(); i++) {
            String line = clean(lines.get(i));
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals < 1) throw failure(resource, i + 1, "Expected key=value");
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (entries.putIfAbsent(key, new Entry(key, value, i + 1)) != null) {
                throw failure(resource, i + 1, "Duplicate property " + key);
            }
        }
        return entries;
    }

    static List<String> readLines(String resource) {
        InputStream input = LevelLoader.class.getResourceAsStream(resource);
        if (input == null) throw failure(resource, 1, "Resource not found");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw failure(resource, 1, e.getMessage());
        }
    }

    private static String clean(String line) {
        String trimmed = line.replace("\uFEFF", "").trim();
        return trimmed.startsWith("#") ? "" : trimmed;
    }

    static IllegalArgumentException failure(String resource, int line, String message) {
        return new IllegalArgumentException(resource + ":" + line + ": " + message);
    }
}
