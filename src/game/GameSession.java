package game;

import core.AudioManager;
import entity.Enemy;
import entity.Player;
import entity.Projectile;
import entity.Weapon;
import main.KeyHandler;
import world.*;

import java.awt.geom.Rectangle2D;
import java.util.*;

import static main.KeyHandler.Action.*;

/** All gameplay mutations live here. Rendering never advances the simulation. */
public final class GameSession implements AutoCloseable {
    public static final int TILE_SIZE = 64;
    private final TileSet tiles = new TileSet();
    private final LevelLoader loader = new LevelLoader(tiles);
    private final LinkedHashMap<String, LevelDefinition> definitions;
    private final Map<String, Level> levels = new LinkedHashMap<>();
    private final Map<String, List<Enemy>> levelEnemies = new HashMap<>();
    private final Map<String, Integer> puzzleProgress = new HashMap<>();
    private final AudioManager audio;
    private final CombatSystem combat;
    private final Player player = new Player();
    private Inventory inventory = new Inventory();
    private GameState state = GameState.TITLE;
    private Level level;
    private String message = "";
    private double messageTime;
    private double playTime;
    private String locationAnnouncement = "";
    private double announcementTime;
    private boolean minimapVisible;
    private double checkpointX;
    private double checkpointY;
    private double interactionTime;
    private PlayerProfile profile = new PlayerProfile("Player");
    private String nameInput = "";
    private String nameError = "";
    private boolean hasStarted;
    private int pauseSelection;
    private GameState controlsReturnState = GameState.PAUSED;
    private boolean fullscreen;
    private boolean fullscreenRequested;
    private boolean quitRequested;

    public GameSession() { this(new AudioManager()); }
    public GameSession(AudioManager audio) {
        this.audio = audio;
        combat = new CombatSystem(audio);
        definitions = loader.loadCatalog();
        resetWorld();
        validatePortals();
    }

    private void resetWorld() {
        levels.clear();
        levelEnemies.clear();
        puzzleProgress.clear();
        combat.reset();
        for (LevelDefinition definition : definitions.values()) {
            levels.put(definition.id(), loader.load(definition));
            levelEnemies.put(definition.id(), definition.enemies().stream()
                    .map(spawn -> new Enemy(spawn, TILE_SIZE)).toList());
        }
        inventory = new Inventory();
        player.reset();
        playTime = 0;
        message = "";
        messageTime = 0;
        interactionTime = 0;
        LevelDefinition first = definitions.values().iterator().next();
        enterLevel(first.id(), first.spawnCol(), first.spawnRow(), false);
    }

    private void validatePortals() {
        for (Level source : levels.values()) {
            for (WorldObject object : source.objects()) {
                if (object.spawn().type() != ObjectType.PORTAL) continue;
                String[] target = object.spawn().argument().split(":");
                try {
                    if (target.length != 3) throw new IllegalArgumentException();
                    Level destination = levels.get(target[0]);
                    int col = Integer.parseInt(target[1]);
                    int row = Integer.parseInt(target[2]);
                    if (destination == null || destination.blocked(playerBoxAt(col, row), tiles, TILE_SIZE))
                        throw new IllegalArgumentException();
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException("Invalid portal " + source.definition().id()
                            + "/" + object.spawn().id() + ": " + object.spawn().argument(), exception);
                }
            }
        }
    }

    private Rectangle2D playerBoxAt(int col, int row) {
        Rectangle2D current = player.bounds();
        return new Rectangle2D.Double(col * TILE_SIZE + current.getX() - player.x(),
                row * TILE_SIZE + current.getY() - player.y(), current.getWidth(), current.getHeight());
    }

    public void startNewRun() {
        resetWorld();
        hasStarted = true;
        state = GameState.PLAYING;
        audio.music(level.definition().music());
        showMessage("Pick up the sword nearby. Press E to use things and M to see the map.");
    }

    private void enterLevel(String id, int col, int row, boolean sound) {
        combat.clearProjectiles();
        level = Objects.requireNonNull(levels.get(id), "Unknown level: " + id);
        player.setPosition(col * TILE_SIZE, row * TILE_SIZE);
        checkpointX = player.x();
        checkpointY = player.y();
        locationAnnouncement = level.definition().name();
        announcementTime = 3;
        interactionTime = .3;
        if (sound) {
            audio.effect("stairs");
            audio.music(level.definition().music());
            showMessage(level.definition().objective());
        }
    }

    public void update(double dt, KeyHandler input) {
        if (input.consume(FULLSCREEN)) fullscreenRequested = true;
        if (state == GameState.TITLE) {
            editName(input.consumeText());
            if (input.consume(START)) submitName();
            if (state == GameState.PLAYING) input.suppressHeldInput();
            else input.endFrame();
            return;
        }
        if (input.consume(MUTE)) audio.toggleMute();
        if (state == GameState.CONTROLS) {
            if (input.consume(PAUSE) || input.consume(HELP) || input.consume(START)) {
                closeControls();
                input.suppressHeldInput();
            } else input.endFrame();
            return;
        }
        if (state == GameState.VICTORY || state == GameState.GAME_OVER) {
            if (input.consume(START)) {
                if (state == GameState.VICTORY) startNewRun();
                else retryCheckpoint();
            }
            input.endFrame();
            return;
        }
        if (state == GameState.PAUSED) {
            if (input.consume(PAUSE)) state = GameState.PLAYING;
            else if (input.consume(INVENTORY)) state = GameState.INVENTORY;
            else if (input.consume(HELP)) openControls();
            else {
                if (input.consume(UP)) selectPauseOption((pauseSelection + pauseOptions().size() - 1) % pauseOptions().size());
                if (input.consume(DOWN)) selectPauseOption((pauseSelection + 1) % pauseOptions().size());
                if (input.consume(START) || input.consume(INTERACT)) activatePauseOption();
            }
            if (state == GameState.PLAYING) input.suppressHeldInput();
            else input.endFrame();
            return;
        }
        if (input.consume(PAUSE)) {
            state = state == GameState.PAUSED || state == GameState.INVENTORY
                    ? GameState.PLAYING : GameState.PAUSED;
            pauseSelection = 0;
        } else if (input.consume(INVENTORY)) {
            if (state == GameState.PLAYING || state == GameState.PAUSED) state = GameState.INVENTORY;
            else if (state == GameState.INVENTORY) state = GameState.PLAYING;
        }
        if (input.consume(HELP)) openControls();
        if (state == GameState.PAUSED && input.consume(START)) state = GameState.PLAYING;
        if (state != GameState.PLAYING) { input.endFrame(); return; }
        if (input.consume(MAP)) minimapVisible = !minimapVisible;
        if (input.consume(CYCLE_TOOL)) cycleTool();
        player.setGuarding(input.isDown(GUARD) && inventory.has(ItemType.SHIELD));
        playTime += dt;
        messageTime = Math.max(0, messageTime - dt);
        announcementTime = Math.max(0, announcementTime - dt);
        interactionTime = Math.max(0, interactionTime - dt);
        int axisX = (input.isDown(RIGHT) ? 1 : 0) - (input.isDown(LEFT) ? 1 : 0);
        int axisY = (input.isDown(DOWN) ? 1 : 0) - (input.isDown(UP) ? 1 : 0);
        player.update(dt, axisX, axisY, inventory.has(ItemType.BOOTS),
                box -> level.blocked(box, tiles, TILE_SIZE));
        collectTouchingObjects();
        if (input.consume(RESET_ROCKS)) resetRocks();
        updatePlates();
        if (input.consume(POTION)) drinkPotion();
        if (input.consume(ATTACK)) {
            if (!ownsWeapon()) showMessage("Pick up the sword nearby first.");
            else if (player.attack()) audio.effect("cuttree");
        }
        if (input.consume(CAST)) {
            if (!spellKnown()) showMessage("Talk to the old man in Ember Village to learn fireball.");
            else combat.cast(player);
        }
        breakObstacles();
        combat.update(dt, player, inventory, enemies(), box -> level.blocked(box, tiles, TILE_SIZE),
                (from, to) -> clearPath(from, to, null), this::showMessage);
        if (!player.alive()) {
            state = GameState.GAME_OVER;
            audio.stopMusic();
            audio.effect("gameover");
        } else if (input.consume(INTERACT) && interactionTime <= 0) {
            WorldObject nearest = nearbyObject();
            if (nearest != null) interact(nearest);
        }
        input.endFrame();
    }

    private boolean ownsWeapon() {
        return inventory.has(ItemType.valueOf(player.weapon().name()));
    }

    private void cycleTool() {
        if (player.attacking()) return;
        Weapon[] weapons = Weapon.values();
        for (int offset = 1; offset <= weapons.length; offset++) {
            Weapon next = weapons[(player.weapon().ordinal() + offset) % weapons.length];
            if (inventory.has(ItemType.valueOf(next.name()))) {
                player.setWeapon(next);
                showMessage(next.name().replace('_', ' ') + " ready. Space uses it. Tab changes tools.");
                return;
            }
        }
        showMessage("Pick up the sword nearby first.");
    }

    private void breakObstacles() {
        if (!player.attacking()) return;
        for (WorldObject object : level.objects()) {
            if (object.removed() || object.active()) continue;
            boolean tree = object.spawn().type() == ObjectType.DRY_TREE;
            boolean wall = object.spawn().type() == ObjectType.CRACKED_WALL;
            if ((!tree && !wall) || !player.attackBox().intersects(object.bounds(TILE_SIZE))
                    || !clearPath(player.bounds(), object.bounds(TILE_SIZE), object)) continue;
            if ((tree && player.weapon() == Weapon.AXE) || (wall && player.weapon() == Weapon.PICKAXE)) {
                if (tree) object.setActive(true);
                else object.setRemoved(true);
                audio.effect(tree ? "cuttree" : "chipwall");
                showMessage(tree ? "Tree cut. You can pass now."
                        : "Wall broken. You can pass now.");
            } else showMessage(tree ? "Press Tab to pick the axe. Press Space to cut the tree."
                    : "Press Tab to pick the pickaxe. Press Space to break the wall.");
        }
    }

    private void updatePlates() {
        for (WorldObject plate : level.objects()) {
            if (plate.spawn().type() != ObjectType.PLATE || plate.active()) continue;
            boolean weighted = level.objects().stream().anyMatch(rock -> rock.spawn().type() == ObjectType.ROCK
                    && !rock.removed() && rock.col() == plate.col() && rock.row() == plate.row());
            if (!weighted) continue;
            plate.setActive(true);
            inventory.grant(plate.spawn().argument());
            audio.effect("unlock");
            showMessage("The plate is pressed. The gate is unlocked.");
        }
    }

    private void resetRocks() {
        List<WorldObject> rocks = level.objects().stream()
                .filter(object -> object.spawn().type() == ObjectType.ROCK).toList();
        if (rocks.isEmpty()) { showMessage("There are no rocks to reset here."); return; }
        for (WorldObject rock : rocks) {
            Rectangle2D home = new Rectangle2D.Double(rock.spawn().col() * TILE_SIZE + 6,
                    rock.spawn().row() * TILE_SIZE + 6, TILE_SIZE - 12, TILE_SIZE - 12);
            if (home.intersects(player.bounds()) || enemies().stream().anyMatch(enemy -> enemy.alive()
                    && home.intersects(enemy.bounds()))) {
                showMessage("Move away from the rock's start, then press Backspace again.");
                return;
            }
        }
        rocks.forEach(rock -> rock.moveTo(rock.spawn().col(), rock.spawn().row()));
        audio.effect("stairs");
        showMessage("Rocks reset. Open gates stay open.");
    }

    private void pushRock(WorldObject rock) {
        double dx = rock.bounds(TILE_SIZE).getCenterX() - player.bounds().getCenterX();
        double dy = rock.bounds(TILE_SIZE).getCenterY() - player.bounds().getCenterY();
        int stepX = Math.abs(dx) > Math.abs(dy) ? (int) Math.signum(dx) : 0;
        int stepY = stepX == 0 ? (int) Math.signum(dy) : 0;
        if (stepX == 0 && stepY == 0) { showMessage("Stand next to the rock. Press E to push it."); return; }
        int col = rock.col() + stepX, row = rock.row() + stepY;
        Rectangle2D destination = new Rectangle2D.Double(col * TILE_SIZE + 6, row * TILE_SIZE + 6,
                TILE_SIZE - 12, TILE_SIZE - 12);
        if (level.blocked(destination, tiles, TILE_SIZE) || enemies().stream().anyMatch(enemy -> enemy.alive()
                && destination.intersects(enemy.bounds()))) {
            showMessage("The rock is blocked. Press Backspace to reset rocks.");
            audio.effect("blocked");
            return;
        }
        rock.moveTo(col, row);
        audio.effect("chipwall");
        showMessage("Push the rock onto the metal plate to open the gate.");
        updatePlates();
    }

    private void drinkPotion() {
        if (player.health() >= player.maxHealth()) { showMessage("Your hearts are full."); return; }
        if (!inventory.usePotion()) { showMessage("No potions left. Look for red bottles."); return; }
        player.heal(4);
        audio.effect("powerup");
        showMessage("Potion used. Hearts restored.");
    }

    private boolean pickup(ObjectType type) {
        return switch (type) {
            case KEY, BOOTS, COIN, POTION, SWORD, SHIELD, LANTERN, HEART, AXE, PICKAXE -> true;
            default -> false;
        };
    }

    private void collectTouchingObjects() {
        for (WorldObject object : level.objects()) {
            if (object.removed() || !pickup(object.spawn().type()) || !requirementsMet(object.spawn().requires())
                    || !object.bounds(TILE_SIZE).intersects(player.bounds())) continue;
            switch (object.spawn().type()) {
                case KEY -> { inventory.addKey(); showMessage("Key found. Press E at a locked door."); }
                case COIN -> { inventory.addCoins(1); showMessage("Coin found."); }
                case POTION -> { inventory.addPotion(); showMessage("Potion found. Press Q to heal."); }
                case HEART -> {
                    if (player.health() == player.maxHealth()) continue;
                    player.heal(2);
                    showMessage("You healed one heart.");
                }
                case SWORD -> { inventory.equip(ItemType.SWORD); player.setWeapon(Weapon.SWORD); showMessage("Sword found. Face a monster and press Space."); }
                case SHIELD -> { inventory.equip(ItemType.SHIELD); showMessage("Shield found. Hold Shift to block hits."); }
                case BOOTS -> { inventory.equip(ItemType.BOOTS); showMessage("Boots found. You can move faster."); }
                case LANTERN -> { inventory.equip(ItemType.LANTERN); showMessage("Lantern found. It lights up dark places."); }
                case AXE -> { inventory.equip(ItemType.AXE); player.setWeapon(Weapon.AXE); showMessage("Axe found. Space cuts dead trees. Tab changes tools."); }
                case PICKAXE -> { inventory.equip(ItemType.PICKAXE); player.setWeapon(Weapon.PICKAXE); showMessage("Pickaxe found. Space breaks cracked walls. Tab changes tools."); }
                default -> { }
            }
            object.setRemoved(true);
            audio.effect(object.spawn().type() == ObjectType.COIN || object.spawn().type() == ObjectType.KEY
                    ? "coin" : "powerup");
        }
    }

    private WorldObject nearbyObject() {
        double best = TILE_SIZE * 1.25;
        WorldObject nearest = null;
        Rectangle2D playerBounds = player.bounds();
        for (WorldObject object : level.objects()) {
            if (object.removed() || pickup(object.spawn().type())) continue;
            if (object.spawn().type() == ObjectType.CHEST && object.active()) continue;
            if (object.spawn().type() == ObjectType.DRY_TREE && object.active()) continue;
            if (object.spawn().type() == ObjectType.SWITCH && inventory.flags().contains(object.spawn().argument())) continue;
            Rectangle2D bounds = object.bounds(TILE_SIZE);
            double dx = bounds.getCenterX() - playerBounds.getCenterX();
            double dy = bounds.getCenterY() - playerBounds.getCenterY();
            double distance = Math.hypot(dx, dy);
            if (distance < best && clearPath(playerBounds, bounds, object)) { best = distance; nearest = object; }
        }
        return nearest;
    }

    private boolean requirementsMet(String requirements) {
        if (requirements.isBlank()) return true;
        return Arrays.stream(requirements.split("\\+")).allMatch(flag -> inventory.flags().contains(flag.trim()));
    }

    /** Interacting and sword swings cannot reach through a wall or a closed gate. */
    private boolean clearPath(Rectangle2D from, Rectangle2D to, WorldObject target) {
        double dx = to.getCenterX() - from.getCenterX();
        double dy = to.getCenterY() - from.getCenterY();
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / 6));
        for (int i = 1; i <= steps; i++) {
            double x = from.getCenterX() + dx * i / steps;
            double y = from.getCenterY() + dy * i / steps;
            if (tiles.solid(level.tileAt((int) Math.floor(x / TILE_SIZE), (int) Math.floor(y / TILE_SIZE)))) return false;
            for (WorldObject object : level.objects()) {
                if (object != target && object.solid() && object.bounds(TILE_SIZE).contains(x, y)) return false;
            }
        }
        return true;
    }

    private void interact(WorldObject object) {
        ObjectSpawn spawn = object.spawn();
        interactionTime = .22;
        if (!requirementsMet(spawn.requires())) {
            showMessage(switch (spawn.type()) {
                case GATE -> "This gate is locked. Read the nearby sign.";
                case PORTAL -> "Find this area's seal first. Look for its chest.";
                default -> "This is locked. Finish this area's task first.";
            });
            audio.effect("blocked");
            return;
        }
        switch (spawn.type()) {
            case DOOR -> {
                if (inventory.useKey()) {
                    object.setRemoved(true);
                    audio.effect("unlock");
                    showMessage("Door opened. You used one key.");
                } else { showMessage("You need a key for this door."); audio.effect("blocked"); }
            }
            case GATE -> {
                object.setRemoved(true);
                audio.effect("dooropen");
                showMessage("The gate is open.");
            }
            case CHEST -> {
                object.setActive(true);
                if (spawn.argument().equals("victory")) {
                    state = GameState.VICTORY;
                    audio.stopMusic();
                    audio.effect("fanfare");
                } else {
                    inventory.grant(spawn.argument());
                    inventory.addCoins(10);
                    player.heal(player.maxHealth());
                    audio.effect("fanfare");
                    showMessage("Seal found! Your hearts are full. Use the blue portal.");
                }
            }
            case PORTAL -> {
                String[] target = spawn.argument().split(":");
                enterLevel(target[0], Integer.parseInt(target[1]), Integer.parseInt(target[2]), true);
            }
            case SIGN -> { showMessage(spawn.argument(), 9); audio.effect("speak"); }
            case SWITCH -> activateSwitch(object);
            case NPC -> {
                String[] dialogue = spawn.argument().split("~", 3);
                if (!dialogue[1].isBlank()) inventory.grant(dialogue[1]);
                showMessage(dialogue[2], 12);
                audio.effect("speak");
            }
            case MERCHANT -> {
                int price = merchantPrice(spawn.argument());
                if (inventory.spendCoins(price)) {
                    inventory.addPotion();
                    audio.effect("coin");
                    showMessage("Potion bought for " + price + " coins. Press Q to heal.");
                } else showMessage("Merchant: A red potion costs " + price + " coins. Your purse holds " + inventory.coins() + ".");
            }
            case ROCK -> pushRock(object);
            case PLATE -> showMessage("Push a rock onto this plate. E pushes. Backspace resets rocks.");
            case DRY_TREE -> showMessage("Use the axe to cut this tree. Tab changes tools. Space uses them.");
            case CRACKED_WALL -> showMessage("Use the pickaxe to break this wall. Tab changes tools.");
            default -> { }
        }
    }

    private void activateSwitch(WorldObject object) {
        String puzzleId = object.spawn().argument();
        PuzzleDefinition puzzle = level.definition().puzzles().stream()
                .filter(candidate -> candidate.id().equals(puzzleId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing puzzle: " + puzzleId));
        String progressKey = level.definition().id() + ":" + puzzleId;
        int step = puzzleProgress.getOrDefault(progressKey, 0);
        if (puzzle.sequence().get(step).equals(object.spawn().id())) {
            object.setActive(true);
            step++;
            puzzleProgress.put(progressKey, step);
            audio.effect("cursor");
            if (step == puzzle.sequence().size()) {
                inventory.grant(puzzleId);
                audio.effect("levelup");
                showMessage(puzzle.solvedMessage());
            } else showMessage("Rune pressed. " + step + " / " + puzzle.sequence().size());
        } else {
            puzzleProgress.put(progressKey, 0);
            level.objects().stream().filter(candidate -> candidate.spawn().type() == ObjectType.SWITCH
                    && candidate.spawn().argument().equals(puzzleId)).forEach(candidate -> candidate.setActive(false));
            audio.effect("blocked");
            showMessage("Wrong order. Read the sign and try again.");
        }
    }

    private int merchantPrice(String argument) {
        return argument.isBlank() ? 8 : Integer.parseInt(argument);
    }

    private void retryCheckpoint() {
        player.revive();
        combat.clearProjectiles();
        player.setPosition(checkpointX, checkpointY);
        for (Enemy enemy : enemies()) {
            if (!enemy.alive()) continue;
            enemy.resetAtSpawn();
        }
        state = GameState.PLAYING;
        interactionTime = .4;
        audio.music(level.definition().music());
        showMessage("You are back at the entrance. Your items are safe.");
    }

    private void editName(String text) {
        for (int codePoint : text.codePoints().toArray()) {
            if (codePoint == '\b') {
                if (!nameInput.isEmpty()) nameInput = nameInput.substring(0, nameInput.offsetByCodePoints(nameInput.length(), -1));
                nameError = "";
            } else if (PlayerProfile.allowed(codePoint)) {
                if (nameInput.codePointCount(0, nameInput.length()) < PlayerProfile.MAX_NAME_LENGTH) {
                    nameInput += new String(Character.toChars(codePoint));
                    nameError = "";
                } else nameError = "Use up to 16 characters.";
            }
        }
    }

    public void submitName() {
        if (state != GameState.TITLE) return;
        try {
            setPlayerName(nameInput);
            startNewRun();
        } catch (IllegalArgumentException exception) {
            nameError = exception.getMessage();
        }
    }

    public void setPlayerName(String value) {
        profile = new PlayerProfile(value);
        nameInput = profile.name();
        nameError = "";
    }

    public String playerName() { return profile.name(); }
    public String nameInput() { return nameInput; }
    public String nameError() { return nameError; }
    public boolean hasStarted() { return hasStarted; }
    public int pauseSelection() { return pauseSelection; }
    public List<String> pauseOptions() {
        return List.of("Resume game", "Inventory", "How to play", fullscreen ? "Windowed mode" : "Fullscreen",
                "Sound: " + (audio.muted() ? "Off" : "On"), "Quit game");
    }
    public void selectPauseOption(int index) {
        if (index >= 0 && index < pauseOptions().size()) pauseSelection = index;
    }
    public void activatePauseOption() {
        if (state != GameState.PAUSED) return;
        switch (pauseSelection) {
            case 0 -> state = GameState.PLAYING;
            case 1 -> state = GameState.INVENTORY;
            case 2 -> openControls();
            case 3 -> fullscreenRequested = true;
            case 4 -> audio.toggleMute();
            case 5 -> quitRequested = true;
            default -> throw new IllegalStateException("Unknown menu option");
        }
    }
    private void openControls() {
        controlsReturnState = state;
        state = GameState.CONTROLS;
    }
    public void closeControls() {
        if (state == GameState.CONTROLS) state = controlsReturnState;
    }
    public void setFullscreen(boolean value) { fullscreen = value; }
    public boolean fullscreen() { return fullscreen; }
    public boolean consumeFullscreenRequest() {
        boolean requested = fullscreenRequested;
        fullscreenRequested = false;
        return requested;
    }
    public boolean consumeQuitRequest() {
        boolean requested = quitRequested;
        quitRequested = false;
        return requested;
    }
    public void pauseForFocusLoss() {
        if (state == GameState.PLAYING) { state = GameState.PAUSED; pauseSelection = 0; }
    }
    public void showMessage(String text) { showMessage(text, 4); }
    private void showMessage(String text, double seconds) { message = text; messageTime = seconds; }
    public GameState state() { return state; }
    public Player player() { return player; }
    public Inventory inventory() { return inventory; }
    public Level level() { return level; }
    public List<Enemy> enemies() { return levelEnemies.get(level.definition().id()); }
    public List<Projectile> projectiles() { return combat.projectiles(); }
    public double spellCooldown() { return combat.spellCooldown(); }
    public boolean spellKnown() { return inventory.flags().contains("fireball"); }
    public TileSet tiles() { return tiles; }
    public double playTime() { return playTime; }
    public String message() { return messageTime > 0 ? message : ""; }
    public boolean minimapVisible() { return minimapVisible; }
    public boolean muted() { return audio.muted(); }
    public int relicGoal() {
        return (int) definitions.values().stream().flatMap(definition -> definition.objects().stream())
                .filter(spawn -> spawn.type() == ObjectType.CHEST && !spawn.argument().equals("victory"))
                .map(ObjectSpawn::argument).distinct().count();
    }
    public int relicCount() {
        return (int) definitions.values().stream().flatMap(definition -> definition.objects().stream())
                .filter(spawn -> spawn.type() == ObjectType.CHEST && !spawn.argument().equals("victory"))
                .map(ObjectSpawn::argument).distinct().filter(inventory.flags()::contains).count();
    }
    public String objective() {
        for (WorldObject chest : level.objects()) {
            if (chest.spawn().type() != ObjectType.CHEST || !chest.active()) continue;
            for (WorldObject portal : level.objects()) {
                if (portal.spawn().type() != ObjectType.PORTAL || portal.spawn().requires().isBlank()) continue;
                if (requirementsMet(portal.spawn().requires())) {
                    String target = portal.spawn().argument().split(":")[0];
                    return "Seal found. Use the blue portal to " + definitions.get(target).name() + ".";
                }
            }
        }
        return level.definition().objective();
    }
    public String locationAnnouncement() { return locationAnnouncement; }
    public double announcementTime() { return announcementTime; }
    public String prompt() {
        if (state != GameState.PLAYING) return "";
        WorldObject object = nearbyObject();
        if (object == null) return "";
        String action = switch (object.spawn().type()) {
            case DOOR -> "Unlock door (1 key)";
            case CHEST -> "Open treasure chest";
            case GATE -> "Open gate";
            case PORTAL -> "Use portal";
            case SIGN -> "Read sign";
            case SWITCH -> "Press " + object.spawn().id().replace("beacon-", "") + " rune";
            case NPC -> "Talk to the old man";
            case MERCHANT -> "Buy potion (" + merchantPrice(object.spawn().argument()) + " coins)";
            case ROCK -> "Push rock";
            case PLATE -> "Check the plate";
            case DRY_TREE -> "Check tree [Axe]";
            case CRACKED_WALL -> "Check wall [Pickaxe]";
            default -> "Interact";
        };
        return "E  " + action;
    }
    @Override public void close() { audio.close(); }
}
