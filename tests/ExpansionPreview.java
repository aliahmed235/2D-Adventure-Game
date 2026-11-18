package tests;

import core.AudioManager;
import entity.Direction;
import entity.Enemy;
import entity.EnemyKind;
import entity.Player;
import entity.Weapon;
import game.GameSession;
import game.GameState;
import game.ItemType;
import main.KeyHandler;
import main.UI;
import main.UiTheme;
import render.WorldRenderer;
import world.EnemySpawn;
import world.WorldObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Optional deterministic visual QA: uses real sessions and ordinary portal interactions. */
public final class ExpansionPreview {
    private static final Path OUTPUT = Path.of("build", "expansion-preview");
    private static final double TICK = 1.0 / 60;
    private ExpansionPreview() { }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        KeyHandler input = new KeyHandler();
        try (GameSession session = new GameSession(new AudioManager(false))) {
            session.setPlayerName("Alex");
            tap(session, input, KeyEvent.VK_ENTER);
            for (ItemType item : ItemType.values()) session.inventory().equip(item);
            for (String flag : new String[]{"forest-seal", "sanctuary-seal", "vault-seal", "ember-seal", "fireball"})
                session.inventory().grant(flag);
            session.inventory().addCoins(120);
            for (int i = 0; i < 8; i++) session.inventory().addPotion();
            input.press(KeyEvent.VK_SHIFT);
            usePortal(session, input, "sanctuary-portal", "sanctuary");
            usePortal(session, input, "vault-portal", "vault");
            usePortal(session, input, "emberwood-portal", "emberwood");

            stand(session, 6, 18);
            settle(session, input, 4.2);
            render(session, "emberwood-camp");
            session.player().revive();
            settle(session, input, 1.1);
            session.player().face(Direction.LEFT);
            render(session, "guarding");

            input.release(KeyEvent.VK_SHIFT);
            session.update(TICK, input);
            session.player().setWeapon(Weapon.AXE);
            session.player().face(Direction.RIGHT);
            tap(session, input, KeyEvent.VK_SPACE);
            settle(session, input, .18);
            render(session, "axe-strike");
            settle(session, input, .5);

            stand(session, 30, 18);
            input.press(KeyEvent.VK_SHIFT);
            settle(session, input, .3);
            render(session, "emberwood-pressure-plate");
            input.release(KeyEvent.VK_SHIFT);
            session.update(TICK, input);
            session.player().setWeapon(Weapon.PICKAXE);
            session.player().face(Direction.UP);
            tap(session, input, KeyEvent.VK_SPACE);
            settle(session, input, .16);
            render(session, "pickaxe-strike");
            settle(session, input, .5);

            stand(session, 30, 18);
            session.player().face(Direction.RIGHT);
            tap(session, input, KeyEvent.VK_R);
            settle(session, input, .1);
            render(session, "fireball");
            input.press(KeyEvent.VK_SHIFT);
            for (int row = 21; row >= 19; row--) {
                stand(session, 30, row);
                settle(session, input, .3);
                tap(session, input, KeyEvent.VK_E);
            }
            settle(session, input, .1);
            if (!session.inventory().flags().contains("ember-weight"))
                throw new AssertionError("The pressure-plate preview did not latch its mechanism");
            render(session, "emberwood-plate-latched");
            usePortal(session, input, "crypt-portal", "crypt");
            Enemy boss = session.enemies().stream().filter(enemy -> enemy.kind() == EnemyKind.SKELETON_LORD)
                    .findFirst().orElseThrow();
            session.player().setPosition(boss.x() - 150, boss.y());
            session.player().revive();
            settle(session, input, 4.2);
            session.player().setPosition(boss.x() - 160, boss.y() + 64);
            session.player().face(Direction.RIGHT);
            session.update(TICK, input);
            render(session, "crypt-phase-one");
            boss.damage(7, -900_001, Direction.RIGHT);
            settle(session, input, .25);
            render(session, "crypt-phase-two");
            tap(session, input, KeyEvent.VK_I);
            render(session, "journal-complete");
        }
        monsterSheet();
        System.out.println("Expansion previews written to " + OUTPUT.toAbsolutePath());
    }

    private static void stand(GameSession session, int col, int row) {
        session.player().setPosition(col * 64, row * 64);
    }

    private static void usePortal(GameSession session, KeyHandler input, String id, String destination) {
        WorldObject portal = session.level().objects().stream().filter(object -> object.spawn().id().equals(id))
                .findFirst().orElseThrow();
        stand(session, portal.col(), portal.row());
        session.player().revive();
        settle(session, input, .45);
        tap(session, input, KeyEvent.VK_E);
        if (!session.level().definition().id().equals(destination))
            throw new AssertionError("Portal " + id + " did not enter " + destination);
    }

    private static void settle(GameSession session, KeyHandler input, double seconds) {
        int ticks = Math.max(1, (int) Math.ceil(seconds / TICK));
        for (int i = 0; i < ticks; i++) session.update(seconds / ticks, input);
        if (session.state() != GameState.PLAYING) throw new AssertionError("Preview left active play: " + session.state());
    }

    private static void tap(GameSession session, KeyHandler input, int key) {
        input.press(key);
        session.update(TICK, input);
        input.release(key);
    }

    private static void render(GameSession session, String name) throws Exception {
        double before = session.playTime();
        int health = session.player().health();
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        new WorldRenderer().draw(g, session, 1024, 768);
        new UI(session).draw(g, 1024, 768);
        g.dispose();
        if (before != session.playTime() || health != session.player().health())
            throw new AssertionError("Rendering changed gameplay");
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void monsterSheet() throws Exception {
        BufferedImage image = new BufferedImage(960, 510, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(22, 36, 29));
        g.fillRect(0, 0, 960, 510);
        UiTheme.centered(g, "MONSTERS OF THE FIVE LANDS", 480, 40, UiTheme.HEADING, UiTheme.GOLD);
        EnemyKind[] kinds = {EnemyKind.GREEN_SLIME, EnemyKind.RED_SLIME, EnemyKind.BAT,
                EnemyKind.ORC, EnemyKind.SKELETON_LORD, EnemyKind.SKELETON_LORD};
        Player distant = new Player();
        distant.setPosition(2000, 2000);
        for (int i = 0; i < kinds.length; i++) {
            int x = 24 + i % 3 * 312, y = 67 + i / 3 * 212;
            UiTheme.cutPanel(g, x, y, 288, 190, new Color(31, 46, 36), UiTheme.BRASS);
            Enemy monster = new Enemy(new EnemySpawn("preview-" + i, 0, 0, 14, 0, 0, kinds[i], ""), 64);
            if (i == 5) monster.damage(7, -500, Direction.DOWN);
            monster.update(.3, distant, box -> false);
            monster.draw(g, x + 112, y + 96, 64);
            String name = kinds[i].displayName + (i == 4 ? " I" : i == 5 ? " II" : "");
            UiTheme.centered(g, name, x + 144, y + 169, UiTheme.LABEL, UiTheme.CREAM);
        }
        UiTheme.centered(g, "Authored sprites, directional attacks, and a second boss phase", 480, 495,
                UiTheme.TINY, UiTheme.MUTED);
        g.dispose();
        ImageIO.write(image, "png", OUTPUT.resolve("monster-roster.png").toFile());
    }
}
