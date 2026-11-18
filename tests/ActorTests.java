package tests;

import entity.Direction;
import entity.Enemy;
import entity.Player;
import world.EnemySpawn;

import java.awt.geom.Rectangle2D;
import java.util.function.Predicate;

/** Headless regression checks for collision, damage immunity, and one-hit-per-swing combat. */
public final class ActorTests {
    private static final Predicate<Rectangle2D> OPEN = box -> false;

    private ActorTests() { }

    public static void main(String[] args) {
        run();
        System.out.println("ActorTests passed");
    }

    public static void run() {
        stopsAtWallInEveryDirection();
        fastMovementCannotTunnel();
        attacksHaveDirectionalReachAndCooldown();
        shieldAndImmunityProtectThePlayer();
        enemiesTakeOneHitPerSwingAndCannotBeKnockedThroughWalls();
    }

    private static void stopsAtWallInEveryDirection() {
        for (Direction direction : Direction.values()) {
            Player player = new Player();
            player.setPosition(128, 128);
            Rectangle2D wall = switch (direction) {
                case UP -> new Rectangle2D.Double(0, 64, 512, 64);
                case DOWN -> new Rectangle2D.Double(0, 192, 512, 64);
                case LEFT -> new Rectangle2D.Double(64, 0, 64, 512);
                case RIGHT -> new Rectangle2D.Double(192, 0, 64, 512);
            };
            player.update(1, direction.dx, direction.dy, false, wall::intersects);
            check(!wall.intersects(player.bounds()), "Player crossed the " + direction + " wall");
            check(Math.abs(player.x() - 128) + Math.abs(player.y() - 128) > 0,
                    "Player did not approach the " + direction + " wall");
        }
    }

    private static void fastMovementCannotTunnel() {
        Player player = new Player();
        player.setPosition(0, 64);
        Rectangle2D thinWall = new Rectangle2D.Double(100, 0, 2, 256);
        player.update(1, 1, 0, true, thinWall::intersects);
        check(player.bounds().getMaxX() <= 100, "Boots tunneled through a thin wall");
    }

    private static void attacksHaveDirectionalReachAndCooldown() {
        Player player = new Player();
        player.setPosition(128, 128);
        for (Direction direction : Direction.values()) {
            player.update(0.5, 0, 0, false, OPEN);
            player.face(direction);
            check(player.attack(), "Attack did not start");
            check(!player.attack(), "Attack bypassed its cooldown");
            double forwardX = 160 + direction.dx * 60;
            double forwardY = 171 + direction.dy * 60;
            check(player.attackBox().contains(forwardX, forwardY), "Sword reach missed its facing direction");
            check(!player.attackBox().contains(160 - direction.dx * 60, 171 - direction.dy * 60),
                    "Sword reached behind the player");
            Rectangle2D box = player.attackBox();
            double targetX = 160 + direction.dx * 53;
            double targetY = 165 + direction.dy * 53;
            check(box.contains(targetX, targetY), "Attack reach disagrees with facing " + direction);
        }
        player.update(0.5, 0, 0, false, OPEN);
        check(player.attackBox().isEmpty(), "Expired attack still has collision reach");
    }

    private static void shieldAndImmunityProtectThePlayer() {
        Player player = new Player();
        check(player.takeDamage(2, true), "First hit was ignored");
        check(player.health() == 5, "Shield should reduce two damage to one");
        check(!player.takeDamage(2, false), "Invulnerability did not prevent a repeated contact hit");
        player.update(1, 0, 0, false, OPEN);
        check(player.takeDamage(2, false), "Immunity did not expire");
        check(player.health() == 3, "Unshielded damage was incorrect");
        player.heal(100);
        check(player.health() == player.maxHealth(), "Healing exceeded the health cap");
        player.update(1, 0, 0, false, OPEN);
        player.takeDamage(100, false);
        check(!player.alive(), "Lethal damage did not defeat the player");
        player.revive();
        check(player.alive() && player.health() == player.maxHealth(), "Checkpoint revive did not restore health");
        check(!player.takeDamage(2, false), "Checkpoint revive should grant brief protection");
    }

    private static void enemiesTakeOneHitPerSwingAndCannotBeKnockedThroughWalls() {
        Enemy enemy = new Enemy(new EnemySpawn("test", 1, 1, 6, 0, 0), 64);
        Player player = new Player();
        player.setPosition(64, 64);
        check(enemy.damage(2, 1, Direction.RIGHT), "Enemy ignored a new swing");
        check(!enemy.damage(2, 1, Direction.RIGHT), "One swing damaged an enemy twice");
        check(enemy.health() == 4, "Enemy health after one swing is incorrect");
        Rectangle2D wall = new Rectangle2D.Double(118, 0, 64, 256);
        for (int step = 0; step < 60; step++) enemy.update(1.0 / 60, player, wall::intersects);
        check(enemy.bounds().getMaxX() <= 118 + 0.000001, "Knockback pushed an enemy through a wall");
        check(enemy.damage(2, 2, Direction.RIGHT), "A second swing did not damage the enemy");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
