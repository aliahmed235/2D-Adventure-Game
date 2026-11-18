package tests;

import world.*;

import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Content and geometry tests. Combat skill is deliberately outside this solver. */
public final class WorldTests {
    private static final int TILE = 64;
    private static final List<String> BASE_CAMPAIGN = List.of("forest", "sanctuary", "vault", "emberwood", "crypt");
    private static final Set<String> BASE_LINKS = Set.of("forest:sanctuary-portal", "sanctuary:forest-portal",
            "sanctuary:vault-portal", "vault:sanctuary-portal", "vault:emberwood-portal",
            "emberwood:vault-portal", "emberwood:crypt-portal", "crypt:emberwood-portal");

    private WorldTests() { }

    public static void run() {
        TileSet tiles = new TileSet();
        LevelLoader loader = new LevelLoader(tiles);
        LinkedHashMap<String, LevelDefinition> catalog = loader.loadCatalog();
        check(catalog.keySet().iterator().next().equals("forest") && catalog.keySet().containsAll(BASE_CAMPAIGN),
                "Campaign starts in forest and includes all five authored areas; optional levels may follow");
        check(!tiles.solid(0) && !tiles.solid(3) && !tiles.solid(5) && !tiles.solid(6),
                "Grass, earth, road and interior floors are walkable");
        check(tiles.solid(1) && tiles.solid(2) && tiles.solid(4),
                "Original forest wall, water and tree IDs remain solid");
        check(tiles.solid(-1) && tiles.solid(10000), "Unknown tile IDs are safe solid boundaries");
        expectFailure(() -> tiles.definition(10000), "Unknown tile ID");
        expectUnsupported(() -> tiles.definitions().clear());

        geometry(tiles);
        malformedMaps(loader, catalog.get("forest"));

        LinkedHashMap<String, Level> levels = new LinkedHashMap<>();
        for (LevelDefinition definition : catalog.values()) levels.put(definition.id(), loader.load(definition));
        check(levels.get("forest").width() == 50 && levels.get("forest").height() == 50,
                "Original forest dimensions are retained");
        check(levels.get("sanctuary").width() == 30 && levels.get("sanctuary").height() == 24,
                "Non-square sanctuary dimensions are read from its map");
        check(levels.get("vault").width() == 34 && levels.get("vault").height() == 26,
                "Non-square vault dimensions are independent of the forest");
        check(!catalog.get("forest").dark() && !catalog.get("sanctuary").dark() && catalog.get("vault").dark(),
                "Darkness is optional level metadata, enabled only for the shipped vault");
        portalLinks(levels, tiles);
        forestDoorOrders(loader, catalog.get("forest"), tiles);
        puzzleGates(loader, catalog, tiles);
        expansionGates(loader, catalog, tiles);
        campaign(levels, tiles);
        System.out.println("World tests passed: geometry, malformed maps, doors, tools, rock pushes, boss gating and five-level campaign.");
    }

    private static void geometry(TileSet tiles) {
        LevelDefinition definition = simpleDefinition("synthetic", "/unused", 0, 0, List.of());
        int[][] input = {{0, 0, 0, 0, 0}, {0, 0, 1, 0, 0}, {0, 0, 0, 0, 0}};
        Level level = new Level(definition, input);
        check(level.width() == 5 && level.height() == 3 && level.tileAt(2, 1) == 1,
                "Rows and columns are not transposed");
        input[1][2] = 0;
        check(level.tileAt(2, 1) == 1, "Runtime level owns a defensive copy of terrain");
        check(level.tileAt(-1, 0) == -1 && level.tileAt(5, 1) == -1
                && level.tileAt(0, 3) == -1 && level.tileAt(0, -1) == -1,
                "Every outside tile lookup returns a boundary sentinel");
        check(!level.blocked(box(0, 0), tiles, TILE), "Walkable tile accepts a player-sized box");
        check(level.blocked(box(2, 1), tiles, TILE), "A wall blocks a player-sized box");
        check(level.blocked(new Rectangle2D.Double(70, 70, 190, 20), tiles, TILE),
                "A broad box cannot span over a wall between its corners");
        check(level.blocked(new Rectangle2D.Double(-1, 0, 10, 10), tiles, TILE), "Left world boundary blocks");
        check(level.blocked(new Rectangle2D.Double(0, -1, 10, 10), tiles, TILE), "Top world boundary blocks");
        check(level.blocked(new Rectangle2D.Double(315, 0, 10, 10), tiles, TILE), "Right world boundary blocks");
        check(level.blocked(new Rectangle2D.Double(0, 187, 10, 10), tiles, TILE), "Bottom world boundary blocks");
        check(!level.blocked(new Rectangle2D.Double(0, 0, 64, 64), tiles, TILE),
                "A box ending on a tile edge does not overlap its neighbour");
        expectFailure(() -> new Level(definition, new int[0][]), "empty");
        expectFailure(() -> new Level(definition, new int[][]{{}}), "empty");
        expectFailure(() -> new Level(definition, new int[][]{{0, 0}, {0}}), "rectangular");

        ObjectSpawn door = new ObjectSpawn("door", ObjectType.DOOR, 1, 0, "", "");
        ObjectSpawn gate = new ObjectSpawn("gate", ObjectType.GATE, 3, 0, "", "lock");
        Level objects = new Level(simpleDefinition("objects", "/unused", 0, 0, List.of(door, gate)),
                new int[][]{{0, 0, 0, 0, 0}, {0, 0, 0, 0, 0}});
        check(objects.blocked(box(1, 0), tiles, TILE) && objects.blocked(box(3, 0), tiles, TILE),
                "Closed doors and rune gates participate in collision");
        objects.objects().get(0).setRemoved(true);
        check(!objects.blocked(box(1, 0), tiles, TILE), "Opening a door removes its collision");
        objects.objects().get(1).setActive(true);
        check(objects.blocked(box(3, 0), tiles, TILE), "An active visual alone does not remove a gate");
        objects.objects().get(1).setRemoved(true);
        check(!objects.blocked(box(3, 0), tiles, TILE), "Opening a rune gate removes its collision");
    }

    private static void malformedMaps(LevelLoader loader, LevelDefinition original) {
        expectFailure(() -> loader.load(withMap(original, "/fixtures/maps/ragged.txt")),
                "/fixtures/maps/ragged.txt:2:");
        expectFailure(() -> loader.load(withMap(original, "/fixtures/maps/unknown-tile.txt")),
                "/fixtures/maps/unknown-tile.txt:1:");
        expectFailure(() -> loader.load(withMap(original, "/fixtures/maps/noninteger.txt")),
                "/fixtures/maps/noninteger.txt:1:");
        expectFailure(() -> loader.load(withMap(original, "/fixtures/maps/empty.txt")),
                "/fixtures/maps/empty.txt:1:");
        expectFailure(() -> loader.load(withMap(original, "/fixtures/maps/does-not-exist.txt")),
                "Resource not found");
        Level nonSquare = loader.load(simpleDefinition("forest", "/fixtures/maps/non-square.txt", 1, 0, List.of()));
        check(nonSquare.width() == 3 && nonSquare.height() == 2, "Whitespace and comment parsing preserves a rectangular map");
        expectFailure(() -> loader.load(simpleDefinition("forest", "/fixtures/maps/non-square.txt", 9, 0, List.of())),
                "Spawn is outside");
    }

    private static void portalLinks(LinkedHashMap<String, Level> levels, TileSet tiles) {
        int basePortalCount = 0;
        for (Level source : levels.values()) {
            boolean returnPortal = source.definition().id().equals("forest");
            for (WorldObject object : source.objects()) {
                if (object.spawn().type() != ObjectType.PORTAL) continue;
                if (BASE_LINKS.contains(source.definition().id() + ":" + object.spawn().id())) basePortalCount++;
                String[] target = object.spawn().argument().split(":");
                Level destination = levels.get(target[0]);
                int col = Integer.parseInt(target[1]);
                int row = Integer.parseInt(target[2]);
                check(destination != null && !destination.blocked(box(col, row), tiles, TILE),
                        "Portal " + object.spawn().id() + " has a safe landing");
                if (BASE_LINKS.contains(source.definition().id() + ":" + object.spawn().id())) {
                    check(destination.objects().stream().noneMatch(o -> o.spawn().type() == ObjectType.PORTAL
                                    && o.spawn().col() == col && o.spawn().row() == row),
                            "Base-campaign arrival does not stand on another portal");
                }
                if (!source.definition().id().equals("forest") && object.spawn().requires().isEmpty()
                        && BASE_LINKS.contains(source.definition().id() + ":" + object.spawn().id())) returnPortal = true;
            }
            if (BASE_CAMPAIGN.contains(source.definition().id())) {
                check(returnPortal, "Every later base-campaign level has an unconditional return portal");
            }
        }
        check(basePortalCount == 8, "The five areas have four bidirectional links");
    }

    private static void forestDoorOrders(LevelLoader loader, LevelDefinition forest, TileSet tiles) {
        List<String> doors = forest.objects().stream().filter(o -> o.type() == ObjectType.DOOR).map(ObjectSpawn::id).toList();
        check(doors.size() == 3, "Original three doors remain in the forest");
        List<List<String>> orders = new ArrayList<>();
        permutations(new ArrayList<>(), doors, orders);
        for (List<String> order : orders) {
            Level level = loader.load(forest);
            Set<String> initial = reachable(level, tiles);
            check(level.objects().stream().filter(o -> o.spawn().type() == ObjectType.KEY
                    && contains(initial, o.spawn())).count() == 3,
                    "All three keys are available before spending a key on any door");
            check(level.objects().stream().filter(o -> o.spawn().type() == ObjectType.CHEST)
                    .noneMatch(o -> contains(initial, o.spawn())), "Woodland chest is initially behind a locked route");
            Set<String> reached = initial;
            int keys = collectKeys(level, reached);
            Set<String> opened = new HashSet<>();
            for (int pass = 0; pass < 5 && opened.size() < doors.size(); pass++) {
                for (String id : order) {
                    WorldObject door = object(level, id);
                    if (door.removed() || !adjacent(reached, door.spawn())) continue;
                    check(keys > 0, "Door order cannot exhaust keys before an accessible locked door");
                    keys--;
                    door.setRemoved(true);
                    opened.add(id);
                    reached = reachable(level, tiles);
                    keys += collectKeys(level, reached);
                }
            }
            check(opened.size() == doors.size(), "All doors can open in reachable preference order " + order);
            final Set<String> complete = reached;
            check(level.objects().stream().filter(o -> o.spawn().type() == ObjectType.CHEST)
                    .allMatch(o -> contains(complete, o.spawn())), "Chest is reachable after door progression " + order);
        }
    }

    private static void puzzleGates(LevelLoader loader, LinkedHashMap<String, LevelDefinition> catalog, TileSet tiles) {
        for (String id : List.of("sanctuary", "vault")) {
            Level level = loader.load(catalog.get(id));
            Set<String> reached = reachable(level, tiles);
            for (WorldObject object : level.objects()) {
                if (object.spawn().type() == ObjectType.CHEST) {
                    check(!contains(reached, object.spawn()), id + " treasure room cannot bypass its gate");
                }
            }
            check(level.objects().stream().anyMatch(o -> o.spawn().type() == ObjectType.SIGN && contains(reached, o.spawn())),
                    id + " puzzle clue is reachable before solving");
            for (PuzzleDefinition puzzle : level.definition().puzzles()) {
                for (String switchId : puzzle.sequence()) {
                    check(contains(reached, object(level, switchId).spawn()),
                            id + " can reach switch " + switchId + " without already solving its puzzle");
                }
                List<WorldObject> gates = level.objects().stream().filter(o -> o.spawn().type() == ObjectType.GATE
                        && List.of(o.spawn().requires().split("\\+")).contains(puzzle.id())).toList();
                check(!gates.isEmpty(), id + " puzzle controls a real blocking gate");
                for (WorldObject gate : gates) gate.setRemoved(true);
            }
            Set<String> unlocked = reachable(level, tiles);
            for (WorldObject object : level.objects()) {
                if (object.spawn().type() == ObjectType.CHEST) {
                    check(contains(unlocked, object.spawn()), id + " solved puzzle opens a route to its treasure");
                }
            }
        }
    }

    private static void expansionGates(LevelLoader loader, LinkedHashMap<String, LevelDefinition> catalog, TileSet tiles) {
        Level grove = loader.load(catalog.get("emberwood"));
        Set<String> reached = reachable(grove, tiles);
        check(contains(reached, object(grove, "woodcutters-axe").spawn()), "Axe is reachable before the dry crossing");
        check(!contains(reached, object(grove, "miners-pick").spawn()), "The dry tree is a real mandatory crossing");
        check(!contains(reached, object(grove, "ember-chest").spawn()), "Ember chest cannot bypass the tool/plate route");
        object(grove, "dry-crossing").setActive(true);
        reached = reachable(grove, tiles);
        check(contains(reached, object(grove, "miners-pick").spawn()), "Axe crossing leads to the pickaxe");
        check(!adjacent(reached, object(grove, "iron-weight").spawn()), "Cracked wall protects the eastern sector");
        object(grove, "stone-crossing").setRemoved(true);
        reached = reachable(grove, tiles);
        check(adjacent(reached, object(grove, "iron-weight").spawn()), "Pickaxe crossing reaches the weight puzzle");
        check(!contains(reached, object(grove, "ember-chest").spawn()), "The plate gate cannot be bypassed");
        WorldObject rock = object(grove, "iron-weight"), plate = object(grove, "iron-plate");
        check(pushToPlate(grove, tiles, rock, plate), "Rock has a legal push route onto the plate");
        check(rock.col() == 30 && rock.row() == 17, "Rock reaches the authored northern plate");
        check(rock.spawn().row() == 20, "Pushing does not mutate immutable authored placement");
        object(grove, "ember-gate").setRemoved(true);
        check(contains(reachable(grove, tiles), object(grove, "ember-chest").spawn()), "Latched gate reaches Ember Seal");

        Level crypt = loader.load(catalog.get("crypt"));
        reached = reachable(crypt, tiles);
        check(!contains(reached, object(crypt, "final-treasure").spawn()), "Boss arena cannot bypass the crown gate");
        check(contains(reached, object(crypt, "crypt-key").spawn()), "Crypt key precedes its door");
        object(crypt, "west-door").setRemoved(true);
        reached = reachable(crypt, tiles);
        final Set<String> wings = reached;
        check(crypt.definition().puzzles().stream().flatMap(p -> p.sequence().stream())
                .allMatch(id -> contains(wings, object(crypt, id).spawn())), "All crown runes are reachable before opening the arena");
        object(crypt, "arena-gate").setRemoved(true);
        reached = reachable(crypt, tiles);
        EnemySpawn boss = crypt.definition().enemies().stream().filter(e -> e.kind() == entity.EnemyKind.SKELETON_LORD).findFirst().orElseThrow();
        check(reached.contains(boss.col() + "," + boss.row()), "Opened arena reaches the Skeleton Lord");
        check(object(crypt, "final-treasure").spawn().requires().contains(boss.defeatFlag()),
                "Final treasure explicitly requires defeating the boss");
        Set<entity.EnemyKind> kinds = new HashSet<>();
        catalog.values().forEach(definition -> definition.enemies().forEach(enemy -> kinds.add(enemy.kind())));
        check(kinds.containsAll(List.of(entity.EnemyKind.values())), "All five imported monster families have actual placements");
    }

    /** Simulates content progression; combat skill is tested separately by the actor/session checks. */
    private static void campaign(LinkedHashMap<String, Level> levels, TileSet tiles) {
        Set<String> flags = new HashSet<>();
        Set<ObjectType> tools = new HashSet<>();
        int keys = 0;
        boolean victory = false;
        for (String levelId : BASE_CAMPAIGN) {
            Level level = levels.get(levelId);
            boolean changed = true;
            for (int pass = 0; pass < 40 && changed; pass++) {
                changed = false;
                Set<String> reached = reachable(level, tiles);
                int found = collectKeys(level, reached);
                keys += found;
                changed |= found > 0;
                for (WorldObject object : level.objects()) {
                    ObjectSpawn spawn = object.spawn();
                    if (object.removed() || !requirements(flags, spawn.requires())) continue;
                    if ((spawn.type() == ObjectType.AXE || spawn.type() == ObjectType.PICKAXE) && contains(reached, spawn)) {
                        tools.add(spawn.type());
                        object.setRemoved(true);
                        changed = true;
                    } else if (spawn.type() == ObjectType.NPC && contains(reached, spawn)) {
                        String flag = spawn.argument().split("~", -1)[1];
                        if (!flag.isBlank()) changed |= flags.add(flag);
                    } else if (spawn.type() == ObjectType.DRY_TREE && !object.active()
                            && tools.contains(ObjectType.AXE) && adjacent(reached, spawn)) {
                        object.setActive(true);
                        changed = true;
                    } else if (spawn.type() == ObjectType.CRACKED_WALL
                            && tools.contains(ObjectType.PICKAXE) && adjacent(reached, spawn)) {
                        object.setRemoved(true);
                        changed = true;
                    }
                }
                for (PuzzleDefinition puzzle : level.definition().puzzles()) {
                    if (!flags.contains(puzzle.id()) && puzzle.sequence().stream()
                            .allMatch(id -> contains(reached, object(level, id).spawn()))) {
                        flags.add(puzzle.id());
                        changed = true;
                    }
                }
                for (WorldObject plate : level.objects()) {
                    if (plate.spawn().type() != ObjectType.PLATE || plate.active()) continue;
                    for (WorldObject rock : level.objects()) {
                        if (rock.spawn().type() == ObjectType.ROCK && pushToPlate(level, tiles, rock, plate)) {
                            plate.setActive(true);
                            flags.add(plate.spawn().argument());
                            changed = true;
                            break;
                        }
                    }
                }
                for (EnemySpawn enemy : level.definition().enemies()) {
                    if (!enemy.defeatFlag().isBlank() && reached.contains(enemy.col() + "," + enemy.row()))
                        changed |= flags.add(enemy.defeatFlag());
                }
                for (WorldObject object : level.objects()) {
                    if (object.removed() || !requirements(flags, object.spawn().requires())) continue;
                    ObjectSpawn spawn = object.spawn();
                    if (spawn.type() == ObjectType.DOOR && keys > 0 && adjacent(reached, spawn)) {
                        keys--;
                        object.setRemoved(true);
                        changed = true;
                    } else if (spawn.type() == ObjectType.GATE && adjacent(reached, spawn)) {
                        object.setRemoved(true);
                        changed = true;
                    } else if (spawn.type() == ObjectType.CHEST && !object.active() && contains(reached, spawn)) {
                        object.setActive(true);
                        if (spawn.argument().equals("victory")) victory = true;
                        else flags.add(spawn.argument());
                        changed = true;
                    }
                }
            }
            Set<String> complete = reachable(level, tiles);
            for (WorldObject object : level.objects()) {
                check(contains(complete, object.spawn()) || (object.spawn().type() == ObjectType.ROCK
                                || object.spawn().type() == ObjectType.PLATE) && adjacent(complete, object.spawn()),
                        "Campaign can reach " + level.definition().id() + "/" + object.spawn().id());
                if (object.spawn().type() == ObjectType.PORTAL && BASE_LINKS.contains(levelId + ":" + object.spawn().id()))
                    check(requirements(flags, object.spawn().requires()), "Completed level unlocks its campaign exits");
            }
        }
        check(flags.containsAll(List.of("forest-seal", "sanctuary-seal", "vault-seal", "ember-seal", "fireball", "crown-defeated")) && victory,
                "A fresh five-level campaign reaches the final treasure with four seals and a defeated crown");
    }

    private record PushState(int rockCol, int rockRow, int playerCol, int playerRow) { }

    /** Sokoban-style push search checks player access behind the rock, not just an empty destination. */
    private static boolean pushToPlate(Level level, TileSet tiles, WorldObject rock, WorldObject plate) {
        int originalCol = rock.col(), originalRow = rock.row();
        ArrayDeque<PushState> queue = new ArrayDeque<>();
        Set<PushState> visited = new HashSet<>();
        queue.add(new PushState(originalCol, originalRow, level.definition().spawnCol(), level.definition().spawnRow()));
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            PushState state = queue.remove();
            if (!visited.add(state)) continue;
            rock.moveTo(state.rockCol(), state.rockRow());
            if (rock.col() == plate.col() && rock.row() == plate.row()) return true;
            Set<String> access = reachable(level, tiles, state.playerCol(), state.playerRow());
            for (int[] direction : directions) {
                int col = rock.col() + direction[0], row = rock.row() + direction[1];
                if (!access.contains((rock.col() - direction[0]) + "," + (rock.row() - direction[1]))
                        || level.blocked(box(col, row), tiles, TILE)) continue;
                queue.add(new PushState(col, row, rock.col(), rock.row()));
            }
        }
        rock.moveTo(originalCol, originalRow);
        return false;
    }

    private static int collectKeys(Level level, Set<String> reached) {
        int keys = 0;
        for (WorldObject object : level.objects()) {
            if (!object.removed() && object.spawn().type() == ObjectType.KEY && contains(reached, object.spawn())) {
                object.setRemoved(true);
                keys++;
            }
        }
        return keys;
    }

    private static boolean requirements(Set<String> flags, String required) {
        return required.isBlank() || flags.containsAll(List.of(required.split("\\+")));
    }

    private static Set<String> reachable(Level level, TileSet tiles) {
        return reachable(level, tiles, level.definition().spawnCol(), level.definition().spawnRow());
    }

    private static Set<String> reachable(Level level, TileSet tiles, int startCol, int startRow) {
        Set<String> reached = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startCol, startRow});
        while (!queue.isEmpty()) {
            int[] cell = queue.remove();
            String key = cell[0] + "," + cell[1];
            if (reached.contains(key) || level.blocked(box(cell[0], cell[1]), tiles, TILE)) continue;
            reached.add(key);
            queue.add(new int[]{cell[0] + 1, cell[1]});
            queue.add(new int[]{cell[0] - 1, cell[1]});
            queue.add(new int[]{cell[0], cell[1] + 1});
            queue.add(new int[]{cell[0], cell[1] - 1});
        }
        return reached;
    }

    private static Rectangle2D box(int col, int row) {
        return new Rectangle2D.Double(col * TILE + 16, row * TILE + 28, 32, 30);
    }

    private static boolean contains(Set<String> reached, ObjectSpawn spawn) {
        return reached.contains(spawn.col() + "," + spawn.row());
    }

    private static boolean adjacent(Set<String> reached, ObjectSpawn spawn) {
        return contains(reached, spawn) || reached.contains((spawn.col() - 1) + "," + spawn.row())
                || reached.contains((spawn.col() + 1) + "," + spawn.row())
                || reached.contains(spawn.col() + "," + (spawn.row() - 1))
                || reached.contains(spawn.col() + "," + (spawn.row() + 1));
    }

    private static WorldObject object(Level level, String id) {
        return level.objects().stream().filter(o -> o.spawn().id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing object " + id));
    }

    private static void permutations(List<String> prefix, List<String> remaining, List<List<String>> result) {
        if (remaining.isEmpty()) { result.add(List.copyOf(prefix)); return; }
        for (String item : remaining) {
            prefix.add(item);
            List<String> next = new ArrayList<>(remaining);
            next.remove(item);
            permutations(prefix, next, result);
            prefix.remove(prefix.size() - 1);
        }
    }

    private static LevelDefinition simpleDefinition(String id, String map, int col, int row, List<ObjectSpawn> objects) {
        return new LevelDefinition(id, "Test", "Test objective", "/sounds/Dungeon.wav", col, row, map,
                objects, List.of(), List.of(), false);
    }

    private static LevelDefinition withMap(LevelDefinition source, String map) {
        return new LevelDefinition(source.id(), source.name(), source.objective(), source.music(),
                source.spawnCol(), source.spawnRow(), map, source.objects(), source.enemies(), source.puzzles(), source.dark());
    }

    private static void expectFailure(Runnable action, String messagePart) {
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(messagePart), "Error includes useful context: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException containing " + messagePart);
    }

    private static void expectUnsupported(Runnable action) {
        try { action.run(); }
        catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Tile palette collection should not allow mutation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
