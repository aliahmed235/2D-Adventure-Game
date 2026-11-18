package tests;

import core.AudioManager;
import entity.Direction;
import game.GameSession;
import game.GameState;
import game.ItemType;
import main.KeyHandler;
import main.UI;
import render.WorldRenderer;
import world.ObjectType;
import world.WorldObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real-session regression checks; no mocking of interactions or game-state transitions. */
public final class GameTests {
    private static final double TICK = 1.0 / 60;
    private GameTests() { }

    public static void main(String[] args) throws Exception {
        ActorTests.run();
        MonsterTests.run();
        CombatTests.run();
        WorldTests.run();
        WindowTests.run();
        ProfileMenuTests.run();
        smallRoomCamera();
        inputAndPause();
        healthAndCheckpoint();
        combatAndOcclusion();
        campaignAndRendering();
        System.out.println("All actor, world, session, campaign and rendering checks passed.");
    }

    private static GameSession session() {
        GameSession session = new GameSession(new AudioManager(false));
        session.setPlayerName("Test Player");
        return session;
    }
    private static void smallRoomCamera() {
        core.Camera camera = new core.Camera();
        camera.follow(192, 320, 448, 448, 1024, 768, 64);
        check(camera.screenX(0) == 288 && camera.screenY(0) == 160, "Small maps were not centered");
        camera.follow(0, 0, 3200, 3200, 1024, 768, 64);
        check(camera.screenX(0) == 0 && camera.screenY(0) == 0, "Camera crossed the top-left edge");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void tap(GameSession session, KeyHandler input, int key) {
        input.press(key);
        session.update(TICK, input);
        input.release(key);
    }
    private static void waitForInteraction(GameSession session, KeyHandler input) {
        for (int i = 0; i < 30; i++) session.update(TICK, input);
    }
    private static WorldObject object(GameSession session, String id) {
        return session.level().objects().stream().filter(object -> object.spawn().id().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError("Missing object " + id));
    }
    private static void stand(GameSession session, int col, int row) {
        session.player().setPosition(col * GameSession.TILE_SIZE, row * GameSession.TILE_SIZE);
    }
    private static void use(GameSession session, KeyHandler input, String id) {
        WorldObject object = object(session, id);
        stand(session, object.spawn().col(), object.spawn().row());
        waitForInteraction(session, input);
        tap(session, input, KeyEvent.VK_E);
    }
    private static void removeThreats(GameSession session) {
        session.enemies().forEach(enemy -> enemy.damage(100, Long.MAX_VALUE, Direction.UP));
    }

    private static void inputAndPause() {
        KeyHandler input = new KeyHandler();
        input.press(KeyEvent.VK_W);
        input.press(KeyEvent.VK_UP);
        input.release(KeyEvent.VK_W);
        check(input.isDown(KeyHandler.Action.UP), "Releasing W canceled a held arrow");
        input.clear();
        input.press(KeyEvent.VK_I);
        check(input.consume(KeyHandler.Action.INVENTORY), "Missing first press");
        input.press(KeyEvent.VK_I);
        check(!input.consume(KeyHandler.Action.INVENTORY), "OS repeat generated a second toggle");
        input.clear();
        try (GameSession session = session()) {
            session.update(1, input);
            check(session.playTime() == 0 && session.state() == GameState.TITLE, "Title advanced gameplay");
            tap(session, input, KeyEvent.VK_ENTER);
            removeThreats(session);
            session.update(.25, input);
            double before = session.playTime();
            tap(session, input, KeyEvent.VK_I);
            session.update(1, input);
            check(session.state() == GameState.INVENTORY && session.playTime() == before, "Inventory did not pause");
            tap(session, input, KeyEvent.VK_ESCAPE);
            check(session.state() == GameState.PLAYING, "Escape did not close inventory");
            session.pauseForFocusLoss();
            before = session.playTime();
            session.update(1, input);
            check(session.state() == GameState.PAUSED && session.playTime() == before, "Focus loss did not pause");
        }
    }

    private static void healthAndCheckpoint() {
        KeyHandler input = new KeyHandler();
        try (GameSession session = session()) {
            tap(session, input, KeyEvent.VK_ENTER);
            removeThreats(session);
            session.inventory().equip(ItemType.BOOTS);
            session.inventory().addPotion();
            session.player().takeDamage(2, false);
            tap(session, input, KeyEvent.VK_Q);
            check(session.player().health() == 6 && session.inventory().potions() == 0, "Potion did not heal");
            session.inventory().addPotion();
            tap(session, input, KeyEvent.VK_Q);
            check(session.inventory().potions() == 1, "Full-health potion was wasted");
            session.update(1, input);
            session.player().takeDamage(6, false);
            session.update(TICK, input);
            check(session.state() == GameState.GAME_OVER, "Lethal hit did not end play");
            tap(session, input, KeyEvent.VK_ENTER);
            check(session.state() == GameState.PLAYING && session.player().health() == 6, "Checkpoint did not revive");
            check(session.player().x() == 23 * 64 && session.player().y() == 21 * 64, "Wrong checkpoint");
            check(session.inventory().has(ItemType.BOOTS) && session.inventory().potions() == 1,
                    "Checkpoint lost discoveries");
            check(session.enemies().stream().noneMatch(enemy -> enemy.alive()), "Checkpoint respawned defeated enemies");
        }
    }

    private static void combatAndOcclusion() {
        KeyHandler input = new KeyHandler();
        try (GameSession session = session()) {
            tap(session, input, KeyEvent.VK_ENTER);
            session.inventory().equip(ItemType.SWORD);
            var enemy = session.enemies().get(0);
            // A clear horizontal path near the starting trail.
            stand(session, 23, 21);
            enemy.setPosition(session.player().x() + 65, session.player().y());
            session.player().face(Direction.RIGHT);
            tap(session, input, KeyEvent.VK_SPACE);
            check(enemy.health() == enemy.maxHealth() - 1, "Sword did not damage sentinel");
            session.update(TICK, input);
            check(enemy.health() == enemy.maxHealth() - 1, "One swing damaged sentinel twice");
        }
        input.clear();
        try (GameSession session = session()) {
            tap(session, input, KeyEvent.VK_ENTER);
            removeThreats(session);
            // Standing immediately above a solid forest tile must not allow an E action through it.
            check(session.level().blocked(new java.awt.geom.Rectangle2D.Double(-1, 0, 32, 32),
                    session.tiles(), 64), "World edge was not blocked");
        }
    }

    private static void campaignAndRendering() throws Exception {
        KeyHandler input = new KeyHandler();
        try (GameSession session = session()) {
            render(session, "title");
            tap(session, input, KeyEvent.VK_ENTER);
            removeThreats(session);
            tap(session, input, KeyEvent.VK_M);
            render(session, "forest");
            use(session, input, "sanctuary-portal");
            check(session.level().definition().id().equals("forest"), "Locked portal allowed travel");
            stand(session, 22, 21);
            session.update(TICK, input);
            check(session.inventory().has(ItemType.SWORD), "Sword pickup failed");
            stand(session, 24, 21);
            session.update(TICK, input);
            check(session.inventory().has(ItemType.SHIELD), "Shield pickup failed");
            use(session, input, "north-key");
            check(session.inventory().keys() == 1 && object(session, "north-key").removed(), "Key pickup failed");
            // Use the actual interaction from below the door, not while embedded in it.
            stand(session, 10, 12);
            waitForInteraction(session, input);
            tap(session, input, KeyEvent.VK_E);
            check(object(session, "treasure-door").removed() && session.inventory().keys() == 0,
                    "Door did not consume exactly one key");
            use(session, input, "woodland-chest");
            check(session.inventory().flags().contains("forest-seal") && session.relicCount() == 1,
                    "Forest seal not recovered");
            use(session, input, "sanctuary-portal");
            check(session.level().definition().id().equals("sanctuary"), "Forest portal failed");
            removeThreats(session);
            use(session, input, "lantern");
            check(session.inventory().has(ItemType.LANTERN), "Lantern pickup failed");
            render(session, "sanctuary");
            use(session, input, "beacon-west");
            check(!object(session, "beacon-west").active(), "Wrong first beacon did not reset");
            use(session, input, "solar-gate");
            check(!object(session, "solar-gate").removed(), "Unsolved gate opened");
            use(session, input, "beacon-east");
            use(session, input, "beacon-south");
            use(session, input, "beacon-west");
            check(session.inventory().flags().contains("solar"), "Solar sequence not solved");
            use(session, input, "solar-gate");
            check(object(session, "solar-gate").removed(), "Solved gate did not open");
            use(session, input, "sanctuary-chest");
            check(session.relicCount() == 2 && session.relicGoal() >= 2, "Seal progress incorrect");
            use(session, input, "forest-portal");
            check(session.level().definition().id().equals("forest"), "Return portal failed");
            check(object(session, "north-key").removed() && object(session, "woodland-chest").active(),
                    "Returning reset forest discoveries");
            use(session, input, "sanctuary-portal");
            check(object(session, "solar-gate").removed(), "Returning reset puzzle gate");
            use(session, input, "vault-portal");
            check(session.level().definition().id().equals("vault"), "Vault portal failed");
            removeThreats(session);
            use(session, input, "beacon-west");
            use(session, input, "beacon-east");
            use(session, input, "beacon-north");
            check(session.inventory().flags().contains("vault-runes"), "Tide sequence not solved");
            use(session, input, "treasure-gate");
            stand(session, 17, 17);
            render(session, "vault");
            tap(session, input, KeyEvent.VK_I);
            render(session, "inventory");
            tap(session, input, KeyEvent.VK_I);
            use(session, input, "final-treasure");
            check(session.state() == GameState.PLAYING && session.inventory().flags().contains("vault-seal"),
                    "The former ending should grant the Vault Seal for the expanded campaign");
            ExpansionTests.continueCampaign(session, input);
            render(session, "victory");
            double finishedTime = session.playTime();
            session.update(1, input);
            check(session.playTime() == finishedTime, "Victory timer continued");
            tap(session, input, KeyEvent.VK_ENTER);
            check(session.relicCount() == 0 && !session.inventory().has(ItemType.SWORD)
                    && !object(session, "north-key").removed(), "New run did not reset world");
        }
    }

    private static void render(GameSession session, String name) throws Exception {
        Path directory = Path.of("build", "screenshots");
        Files.createDirectories(directory);
        double before = session.playTime();
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        new WorldRenderer().draw(g, session, 1024, 768);
        new UI(session).draw(g, 1024, 768);
        g.dispose();
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        check(session.playTime() == before, "Rendering changed game time");
    }
}
