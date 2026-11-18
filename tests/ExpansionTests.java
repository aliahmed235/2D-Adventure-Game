package tests;

import entity.Direction;
import entity.Enemy;
import entity.EnemyKind;
import entity.Weapon;
import game.GameSession;
import game.GameState;
import game.ItemType;
import main.KeyHandler;
import main.UI;
import render.WorldRenderer;
import world.WorldObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Continues the real campaign through tools, trading, weight puzzle, and the final boss. */
public final class ExpansionTests {
    private static final double TICK = 1.0 / 60;
    private ExpansionTests() { }

    public static void continueCampaign(GameSession session, KeyHandler input) throws Exception {
        check(session.level().definition().id().equals("vault")
                && session.inventory().flags().contains("vault-seal"), "Expansion begins after the Vault Seal");
        use(session, input, "emberwood-portal");
        check(session.level().definition().id().equals("emberwood"), "Vault passage reaches Emberwood");
        removeOrdinaryMonsters(session);
        render(session, "emberwood");

        check(!session.spellKnown(), "Fire rune should be learned from its NPC");
        use(session, input, "fire-sage");
        check(session.spellKnown(), "Sage interaction did not grant fireball magic");

        // Public inventory mutations arrange a poor/rich purse without bypassing the actual trade action.
        session.inventory().spendCoins(session.inventory().coins());
        int potions = session.inventory().potions();
        use(session, input, "camp-merchant");
        check(session.inventory().coins() == 0 && session.inventory().potions() == potions,
                "Merchant traded without enough coins");
        session.inventory().addCoins(8);
        use(session, input, "camp-merchant");
        check(session.inventory().coins() == 0 && session.inventory().potions() == potions + 1,
                "Merchant did not exchange exactly eight coins for one potion");

        collect(session, input, "woodcutters-axe");
        check(session.inventory().has(ItemType.AXE) && session.player().weapon() == Weapon.AXE,
                "Axe pickup did not equip its tool");
        select(session, input, Weapon.SWORD);
        strikeFrom(session, input, 13, 20, Direction.RIGHT);
        check(!object(session, "dry-crossing").active(), "Sword incorrectly chopped the dry tree");
        select(session, input, Weapon.AXE);
        strikeFrom(session, input, 13, 20, Direction.RIGHT);
        check(object(session, "dry-crossing").active() && !object(session, "dry-crossing").solid(),
                "Axe did not leave a walkable stump");

        collect(session, input, "miners-pick");
        check(session.inventory().has(ItemType.PICKAXE), "Pickaxe pickup failed");
        select(session, input, Weapon.AXE);
        strikeFrom(session, input, 25, 17, Direction.RIGHT);
        check(!object(session, "stone-crossing").removed(), "Axe incorrectly broke the stone wall");
        select(session, input, Weapon.PICKAXE);
        strikeFrom(session, input, 25, 17, Direction.RIGHT);
        check(object(session, "stone-crossing").removed(), "Pickaxe did not open the cracked wall");

        WorldObject rock = object(session, "iron-weight");
        for (int push = 0; push < 3; push++) {
            stand(session, rock.col(), rock.row() + 1);
            settle(session, input);
            int before = rock.row();
            tap(session, input, KeyEvent.VK_E);
            check(rock.row() == before - 1, "E did not push the weight north from below");
        }
        check(rock.row() == 17 && object(session, "iron-plate").active()
                && session.inventory().flags().contains("ember-weight"), "Rock did not latch the iron plate");
        tap(session, input, KeyEvent.VK_BACK_SPACE);
        check(rock.col() == rock.spawn().col() && rock.row() == rock.spawn().row(), "Backspace did not reset the rock");
        check(object(session, "iron-plate").active() && session.inventory().flags().contains("ember-weight"),
                "Resetting a rock lost the released lock");
        use(session, input, "ember-gate");
        check(object(session, "ember-gate").removed(), "Weighted gate did not open");
        use(session, input, "ember-chest");
        check(session.inventory().flags().contains("ember-seal") && session.relicCount() == 4,
                "Ember chest did not grant the fourth seal");

        use(session, input, "vault-portal");
        use(session, input, "emberwood-portal");
        check(object(session, "dry-crossing").active() && object(session, "stone-crossing").removed()
                && object(session, "iron-plate").active(), "Returning reset new-level discoveries");
        use(session, input, "crypt-portal");
        check(session.level().definition().id().equals("crypt"), "Ember Seal passage did not reach the crypt");
        removeOrdinaryMonsters(session);
        render(session, "crypt");

        collect(session, input, "crypt-key");
        use(session, input, "west-door");
        check(object(session, "west-door").removed(), "Crypt key did not open the west wing");
        use(session, input, "beacon-south");
        use(session, input, "beacon-west");
        use(session, input, "beacon-east");
        check(session.inventory().flags().contains("crown-runes"), "Crypt rune sequence did not solve");
        use(session, input, "arena-gate");
        check(object(session, "arena-gate").removed(), "Crown runes did not open the arena");
        use(session, input, "final-treasure");
        check(session.state() == GameState.PLAYING && !object(session, "final-treasure").active(),
                "Treasure allowed victory before defeating the boss");

        Enemy boss = session.enemies().stream().filter(enemy -> enemy.kind() == EnemyKind.SKELETON_LORD)
                .findFirst().orElseThrow();
        check(!session.inventory().flags().contains("crown-defeated"), "Boss flag granted before a final blow");
        // Arrange phase two, then exercise the real projectile collision/reward path for the final blow.
        check(boss.damage(boss.health() - 2, Long.MAX_VALUE - 12, null), "Could not arrange phase-two boss");
        check(boss.phaseTwo() && boss.alive(), "Boss did not enter phase two at low health");
        boss.resetAtSpawn();
        session.player().revive();
        stand(session, 22, 14);
        session.player().face(Direction.UP);
        render(session, "boss-phase-two");
        int coinsBeforeBoss = session.inventory().coins();
        tap(session, input, KeyEvent.VK_R);
        check(session.projectiles().stream().anyMatch(projectile -> projectile.friendly()),
                "R did not create the learned fireball");
        input.press(KeyEvent.VK_SHIFT);
        for (int tick = 0; tick < 180 && boss.alive(); tick++) session.update(TICK, input);
        input.release(KeyEvent.VK_SHIFT);
        check(!boss.alive() && session.inventory().flags().contains("crown-defeated"),
                "CombatSystem did not grant the boss defeat flag after fireball damage");
        check(session.inventory().coins() == coinsBeforeBoss + 25, "Boss reward was not granted exactly once");
        check(session.state() == GameState.PLAYING, "Boss kill should leave the final chest to be opened");
        use(session, input, "final-treasure");
        check(session.state() == GameState.VICTORY, "Five-level campaign did not end at the final treasure");
        System.out.println("Expansion tests passed: NPCs, trade, tools, destructibles, weight/reset, return-state and boss victory.");
    }

    private static void removeOrdinaryMonsters(GameSession session) {
        session.enemies().stream().filter(enemy -> enemy.kind() != EnemyKind.SKELETON_LORD)
                .forEach(enemy -> enemy.damage(100, Long.MAX_VALUE, null));
    }
    private static void tap(GameSession session, KeyHandler input, int key) {
        input.press(key);
        session.update(TICK, input);
        input.release(key);
    }
    private static void settle(GameSession session, KeyHandler input) {
        for (int tick = 0; tick < 36; tick++) session.update(TICK, input);
    }
    private static void stand(GameSession session, int col, int row) { session.player().setPosition(col * 64, row * 64); }
    private static WorldObject object(GameSession session, String id) {
        return session.level().objects().stream().filter(object -> object.spawn().id().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError("Missing object " + id));
    }
    private static void use(GameSession session, KeyHandler input, String id) {
        WorldObject object = object(session, id);
        stand(session, object.col(), object.row());
        settle(session, input);
        tap(session, input, KeyEvent.VK_E);
    }
    private static void collect(GameSession session, KeyHandler input, String id) {
        WorldObject object = object(session, id);
        stand(session, object.col(), object.row());
        session.update(TICK, input);
        check(object.removed(), "Pickup did not disappear: " + id);
    }
    private static void select(GameSession session, KeyHandler input, Weapon weapon) {
        settle(session, input);
        for (int attempt = 0; attempt < Weapon.values().length && session.player().weapon() != weapon; attempt++)
            tap(session, input, KeyEvent.VK_TAB);
        check(session.player().weapon() == weapon, "Tab could not select " + weapon);
    }
    private static void strikeFrom(GameSession session, KeyHandler input, int col, int row, Direction direction) {
        settle(session, input);
        stand(session, col, row);
        session.player().face(direction);
        tap(session, input, KeyEvent.VK_SPACE);
    }
    private static void render(GameSession session, String name) throws Exception {
        Path directory = Path.of("build", "screenshots");
        Files.createDirectories(directory);
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        new WorldRenderer().draw(g, session, 1024, 768);
        new UI(session).draw(g, 1024, 768);
        g.dispose();
        ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
