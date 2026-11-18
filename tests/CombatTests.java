package tests;

import core.AudioManager;
import entity.Direction;
import entity.Enemy;
import entity.EnemyKind;
import entity.Player;
import game.CombatSystem;
import game.Inventory;
import game.ItemType;
import world.EnemySpawn;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Exercises the real combat coordinator with collision queries, actors, and inventory. */
public final class CombatTests {
    private static final Predicate<Rectangle2D> OPEN = box -> false;
    private CombatTests() { }

    public static void main(String[] args) { run(); System.out.println("Combat system tests passed."); }
    public static void run() {
        fireballHitsAndDisappears();
        terrainStopsProjectiles();
        spellCooldownPreventsRapidFire();
        orcWindupDoesNotDealContactDamage();
        raisedShieldBlocksContactAndShots();
        bossDefeatAwardsProgressExactlyOnce();
        mixedAttacksPreserveOneHitPerSwing();
        occludedSwordCannotDamage();
    }

    private static void fireballHitsAndDisappears() {
        try (Fixture f = new Fixture()) {
            Enemy target = enemy("target", 3, 1, 5, EnemyKind.GREEN_SLIME, "");
            check(f.combat.cast(f.player), "Ready fireball did not cast");
            f.update(.4, List.of(target), OPEN);
            check(target.health() == 3, "Friendly fireball did not deal exactly two damage");
            check(f.combat.projectiles().isEmpty(), "Fireball remained after hitting a monster");
        }
    }

    private static void terrainStopsProjectiles() {
        try (Fixture f = new Fixture()) {
            Enemy target = enemy("behind-wall", 3, 1, 5, EnemyKind.GREEN_SLIME, "");
            Rectangle2D wall = new Rectangle2D.Double(160, 0, 1, 512);
            f.combat.cast(f.player);
            f.update(.5, List.of(target), wall::intersects);
            check(target.health() == 5, "Fireball damaged a monster through terrain");
            check(f.combat.projectiles().isEmpty(), "Fireball did not expire at terrain");
        }
    }

    private static void spellCooldownPreventsRapidFire() {
        try (Fixture f = new Fixture()) {
            check(f.combat.cast(f.player), "Initial spell was unavailable");
            check(!f.combat.cast(f.player), "Spell cast twice without a cooldown");
            f.update(.6, List.of(), OPEN);
            check(!f.combat.cast(f.player), "Spell cooldown ended too early");
            f.update(.7, List.of(), OPEN);
            check(f.combat.cast(f.player), "Spell did not become ready again");
            f.combat.reset();
            f.player.takeDamage(100, false);
            check(!f.combat.cast(f.player), "A defeated player could cast");
        }
    }

    private static void orcWindupDoesNotDealContactDamage() {
        try (Fixture f = new Fixture()) {
            f.player.setPosition(128, 64);
            Enemy orc = enemy("raider", 1, 1, 6, EnemyKind.ORC, "");
            f.update(.8, List.of(orc), OPEN);
            check(f.player.health() == 6 && !orc.attacking(), "Orc windup dealt contact damage");
            f.update(.2, List.of(orc), OPEN);
            check(f.player.health() == 6, "Orc dealt damage before its strike");
            f.update(.3, List.of(orc), OPEN);
            check(orc.attacking() && f.player.health() == 4, "Orc strike did not deal its telegraphed damage");
        }
    }

    private static void raisedShieldBlocksContactAndShots() {
        try (Fixture f = new Fixture()) {
            f.inventory.equip(ItemType.SHIELD);
            f.player.setGuarding(true);
            Enemy slime = enemy("contact", 1, 1, 4, EnemyKind.GREEN_SLIME, "");
            f.update(.05, List.of(slime), OPEN);
            check(f.player.health() == 6, "Guard failed against contact damage");
            check(!f.combat.cast(f.player), "Guarding and casting occurred together");
            f.player.setPosition(320, 64);
            Enemy orc = enemy("thrower", 1, 1, 6, EnemyKind.ORC, "");
            f.update(1.7, List.of(orc), OPEN);
            check(f.player.health() == 6, "Guard failed against a thrown rock");
            check(f.combat.projectiles().isEmpty(), "A blocked rock continued through the player");
        }
    }

    private static void bossDefeatAwardsProgressExactlyOnce() {
        try (Fixture f = new Fixture()) {
            Enemy boss = enemy("crown-lord", 2, 1, 14, EnemyKind.SKELETON_LORD, "crown-defeated");
            boss.damage(7, -900, null);
            check(boss.phaseTwo(), "Boss failed to enter its second phase at half health");
            boss.damage(6, -901, null);
            f.player.takeDamage(2, false);
            check(f.player.attack(), "Finishing attack did not begin");
            f.update(0, List.of(boss), OPEN);
            check(!boss.alive(), "Final sword strike did not defeat boss");
            check(f.inventory.flags().contains("crown-defeated"), "Boss defeat flag was not granted");
            check(f.inventory.coins() == 25, "Boss coin reward was incorrect");
            check(f.player.health() == f.player.maxHealth(), "Boss victory did not restore health");
            f.update(.1, List.of(boss), OPEN);
            f.update(.1, List.of(boss), OPEN);
            check(f.inventory.coins() == 25 && f.messages.size() == 1, "Boss reward repeated after death");
        }
    }

    private static void mixedAttacksPreserveOneHitPerSwing() {
        try (Fixture f = new Fixture()) {
            Enemy target = enemy("mixed-hits", 2, 1, 8, EnemyKind.GREEN_SLIME, "");
            check(f.player.attack() && f.combat.cast(f.player), "Combined attacks did not start");
            f.update(.05, List.of(target), OPEN);
            check(target.health() == 5, "Sword plus fireball did not deal one plus two damage");
            f.update(0, List.of(target), OPEN);
            check(target.health() == 5, "Same swing damaged again after a projectile hit");
            f.player.update(.6, 0, 0, false, OPEN);
            target.resetAtSpawn();
            check(f.player.attack(), "Second swing did not start");
            f.update(0, List.of(target), OPEN);
            check(target.health() == 4, "A new swing was incorrectly rejected after projectile damage");
        }
    }

    private static void occludedSwordCannotDamage() {
        try (Fixture f = new Fixture()) {
            Enemy target = enemy("occluded", 2, 1, 5, EnemyKind.GREEN_SLIME, "");
            f.player.attack();
            f.combat.update(.01, f.player, f.inventory, List.of(target), OPEN,
                    (from, to) -> false, f.messages::add);
            check(target.health() == 5, "Sword ignored the world's line-of-sight query");
        }
    }

    private static Enemy enemy(String id, int col, int row, int health, EnemyKind kind, String flag) {
        return new Enemy(new EnemySpawn(id, col, row, health, 0, 0, kind, flag), 64);
    }

    private static final class Fixture implements AutoCloseable {
        final AudioManager audio = new AudioManager(false);
        final CombatSystem combat = new CombatSystem(audio);
        final Inventory inventory = new Inventory();
        final Player player = new Player();
        final List<String> messages = new ArrayList<>();
        Fixture() { player.setPosition(64, 64); player.face(Direction.RIGHT); }
        void update(double dt, List<Enemy> enemies, Predicate<Rectangle2D> blocked) {
            combat.update(dt, player, inventory, enemies, blocked, (from, to) -> true, messages::add);
        }
        @Override public void close() { audio.close(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
