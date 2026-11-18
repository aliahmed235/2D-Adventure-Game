package tests;

import core.AudioManager;
import entity.Direction;
import game.GameSession;
import game.Inventory;
import game.ItemType;
import main.KeyHandler;
import main.UI;
import render.WorldRenderer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Optional visual QA utility. Run after compiling with build.ps1 -Test. */
public final class UiPreview {
    private static final double TICK = 1.0 / 60;
    private static final Path OUTPUT = Path.of("build", "ui-preview");

    private UiPreview() { }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        KeyHandler input = new KeyHandler();
        try (GameSession session = fresh(input)) {
            render(session, "hud-initial");
            tap(session, input, KeyEvent.VK_M);
            render(session, "hud-map");
            tap(session, input, KeyEvent.VK_I);
            render(session, "inventory-empty");
        }
        input.clear();
        try (GameSession session = fresh(input)) {
            equip(session);
            render(session, "hud-equipped");
            tap(session, input, KeyEvent.VK_I);
            render(session, "inventory-equipped");
            tap(session, input, KeyEvent.VK_ESCAPE);
            session.showMessage("The sun rises in the EAST, climbs to the SOUTH, and rests in the WEST. "
                    + "Light the beacons in that order. A wrong beacon resets the sequence.");
            render(session, "hud-long-message");
            tap(session, input, KeyEvent.VK_ESCAPE);
            render(session, "paused");
        }
        input.clear();
        try (GameSession session = fresh(input)) {
            session.player().takeDamage(5, false);
            render(session, "hud-low-health");
        }
        input.clear();
        try (GameSession session = fresh(input)) {
            session.player().takeDamage(5, false);
            render(session, "hud-low-health");
        }
        System.out.println("UI previews written to " + OUTPUT.toAbsolutePath()
                + "; rendering left gameplay state unchanged.");
    }

    private static GameSession fresh(KeyHandler input) {
        GameSession session = new GameSession(new AudioManager(false));
        session.setPlayerName("Alex");
        tap(session, input, KeyEvent.VK_ENTER);
        session.enemies().forEach(enemy -> enemy.damage(100, Long.MAX_VALUE, Direction.UP));
        // Let the location announcement and opening message clear, leaving an unobscured scene.
        for (int tick = 0; tick < 300; tick++) session.update(TICK, input);
        return session;
    }

    private static void equip(GameSession session) {
        Inventory inventory = session.inventory();
        for (ItemType type : ItemType.values()) inventory.equip(type);
        for (int i = 0; i < 12; i++) inventory.addKey();
        inventory.addCoins(345);
        for (int i = 0; i < 7; i++) inventory.addPotion();
        inventory.grant("forest-seal");
        inventory.grant("sanctuary-seal");
    }

    private static void tap(GameSession session, KeyHandler input, int key) {
        input.press(key);
        session.update(TICK, input);
        input.release(key);
    }

    private static String snapshot(GameSession session) {
        Inventory inventory = session.inventory();
        String gear = Arrays.stream(ItemType.values()).map(type -> type + ":" + inventory.has(type))
                .collect(Collectors.joining(","));
        String objects = session.level().objects().stream()
                .map(object -> object.spawn().id() + ":" + object.removed() + ":" + object.active())
                .collect(Collectors.joining(","));
        String enemies = session.enemies().stream()
                .map(enemy -> enemy.id() + ":" + enemy.x() + ":" + enemy.y() + ":" + enemy.health())
                .collect(Collectors.joining(","));
        return session.state() + "|" + session.playTime() + "|" + session.player().health()
                + "|" + session.player().x() + "|" + session.player().y()
                + "|" + inventory.keys() + "|" + inventory.coins() + "|" + inventory.potions()
                + "|" + inventory.flags() + "|" + gear + "|" + objects + "|" + enemies;
    }

    private static void render(GameSession session, String name) throws Exception {
        String before = snapshot(session);
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        new WorldRenderer().draw(g, session, 1024, 768);
        new UI(session).draw(g, 1024, 768);
        g.dispose();
        if (!before.equals(snapshot(session))) throw new AssertionError("Rendering mutated gameplay in " + name);
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }
}
