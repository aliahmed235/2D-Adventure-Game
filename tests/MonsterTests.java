package tests;

import core.Assets;
import entity.*;
import world.EnemySpawn;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Predicate;

/** Combat behavior and complete authored-sprite checks, without opening a native window. */
public final class MonsterTests {
    private static final Predicate<Rectangle2D> OPEN = box -> false;
    private MonsterTests() { }

    public static void main(String[] args) { run(); System.out.println("Monster actor tests passed."); }
    public static void run() {
        authoredSpriteSetsAreComplete();
        guardingAndToolAttacks();
        projectilesRespectTerrainAndLifetime();
        meleeWindupAndBossPhase();
        mixedAttackIdsCannotRepeatDamage();
        rangedMonstersQueueRealProjectiles();
    }

    private static void authoredSpriteSetsAreComplete() {
        for (Weapon weapon : Weapon.values()) {
            for (Direction direction : Direction.values()) {
                for (int frame = 1; frame <= 2; frame++) {
                    BufferedImage image = Assets.image("/Player/Attacking sprites/boy_" + weapon.spritePrefix()
                            + "_" + direction.sprite + "_" + frame + ".png");
                    check(image.getWidth() * image.getHeight() == 512, "Weapon animation lost its two-tile dimensions");
                }
                check(Assets.image("/Player/Guarding sprites/boy_guard_" + direction.sprite + ".png") != null,
                        "Missing guard direction");
            }
        }
        for (EnemyKind kind : EnemyKind.values()) {
            String[] directions = kind.melee() ? new String[]{"up", "down", "left", "right"} : new String[]{"down"};
            int phases = kind == EnemyKind.SKELETON_LORD ? 2 : 1;
            for (int phase = 0; phase < phases; phase++) {
                String prefix = kind.sprite + (phase == 1 ? "_phase2" : "");
                for (String direction : directions) {
                    for (int frame = 1; frame <= 2; frame++) {
                        check(Assets.image("/Monster/" + prefix + "_" + direction + "_" + frame + ".png") != null,
                                "Missing monster walk frame");
                        if (kind.melee()) check(Assets.image("/Monster/" + prefix + "_attack_" + direction
                                + "_" + frame + ".png") != null, "Missing monster attack frame");
                    }
                }
            }
        }
        for (Direction direction : Direction.values())
            for (int frame = 1; frame <= 2; frame++)
                check(Assets.image("/Projectile/fireball_" + direction.sprite + "_" + frame + ".png") != null,
                        "Missing fireball animation");
        check(Assets.image("/Projectile/rock_down_1.png") != null, "Missing thrown rock");
    }

    private static void guardingAndToolAttacks() {
        Player player = new Player();
        player.setGuarding(true);
        check(!player.takeDamage(3, true) && player.health() == 6, "Raised shield did not block");
        check(player.consumeBlock() && !player.consumeBlock(), "Block feedback must be consumed once");
        check(!player.attack(), "Guarding player could attack simultaneously");
        player.update(.1, 1, 0, false, OPEN);
        check(player.x() > 0 && player.x() < 12, "Guard movement should be deliberately slower");
        player.setGuarding(false);
        check(player.takeDamage(2, true) && player.health() == 5, "Passive shield reduction changed");
        BufferedImage canvas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        for (Weapon weapon : Weapon.values()) {
            for (Direction direction : Direction.values()) {
                player.update(.6, 0, 0, false, OPEN);
                player.setWeapon(weapon);
                player.face(direction);
                check(player.attack(), "Equipped tool did not attack");
                player.draw(g, 96, 96, 64, true, true);
                player.update(weapon.duration() * .6, 0, 0, false, OPEN);
                player.draw(g, 96, 96, 64, true, true);
            }
        }
        g.dispose();
        player.revive();
        check(!player.guarding(), "Reviving retained a raised shield");
    }

    private static void projectilesRespectTerrainAndLifetime() {
        for (Projectile.Kind kind : Projectile.Kind.values()) {
            for (Direction direction : Direction.values()) {
                Projectile projectile = new Projectile(kind, true, 128, 128, direction);
                Rectangle2D wall = switch (direction) {
                    case UP -> new Rectangle2D.Double(0, 70, 256, 1);
                    case DOWN -> new Rectangle2D.Double(0, 185, 256, 1);
                    case LEFT -> new Rectangle2D.Double(70, 0, 1, 256);
                    case RIGHT -> new Rectangle2D.Double(185, 0, 1, 256);
                };
                projectile.update(.6, wall::intersects);
                check(!projectile.alive(), "Projectile tunneled through thin " + direction + " wall");
            }
        }
        Projectile fireball = new Projectile(Projectile.Kind.FIREBALL, true, 128, 128, Direction.RIGHT);
        Projectile rock = new Projectile(Projectile.Kind.ROCK, false, 128, 128, Direction.RIGHT);
        check(fireball.attackId() < 0 && fireball.attackId() != rock.attackId(), "Projectile IDs overlap");
        check(fireball.friendly() && !rock.friendly(), "Projectile ownership changed");
        fireball.update(2.3, OPEN);
        check(!fireball.alive(), "Expired projectile stayed alive");
    }

    private static void meleeWindupAndBossPhase() {
        Player player = new Player();
        player.setPosition(128, 64);
        Enemy orc = new Enemy(new EnemySpawn("orc-test", 1, 1, 5, 0, 0, EnemyKind.ORC, ""), 64);
        orc.update(.8, player, OPEN);
        check(!orc.attacking() && orc.attackBox().isEmpty(), "Orc windup dealt immediate damage");
        orc.update(.5, player, OPEN);
        check(orc.attacking() && !orc.attackBox().isEmpty(), "Orc did not reach its visible strike");
        Enemy boss = new Enemy(new EnemySpawn("boss-test", 1, 1, 14, 0, 0,
                EnemyKind.SKELETON_LORD, "crown-defeated"), 64);
        check(!boss.phaseTwo(), "Boss began in second phase");
        boss.damage(7, 1, null);
        check(boss.phaseTwo() && boss.defeatFlag().equals("crown-defeated"), "Boss phase threshold or flag failed");
        boss.update(.8, player, OPEN);
        boss.update(.3, player, OPEN);
        check(boss.attacking() && boss.attackDamage() == 3, "Phase-two boss did not attack");
        boss.resetAtSpawn();
        check(!boss.attacking() && boss.health() == 7, "Checkpoint reset damage or retained a strike");
    }

    private static void mixedAttackIdsCannotRepeatDamage() {
        Enemy enemy = new Enemy(new EnemySpawn("mixed", 0, 0, 8, 0, 0), 64);
        check(enemy.damage(1, 1, null), "Melee ID rejected");
        check(enemy.damage(2, -100, null), "Projectile ID rejected after melee");
        check(!enemy.damage(1, 1, null), "Old swing hit again after an intervening projectile");
        check(enemy.damage(1, 2, null) && enemy.health() == 4, "New melee ID rejected after projectile");
    }

    private static void rangedMonstersQueueRealProjectiles() {
        Player player = new Player();
        player.setPosition(64 * 5, 64);
        Enemy orc = new Enemy(new EnemySpawn("ranged", 1, 1, 6, 0, 0, EnemyKind.ORC, ""), 64);
        orc.update(1.7, player, OPEN);
        Projectile shot = orc.pollProjectile();
        check(shot != null && shot.kind() == Projectile.Kind.ROCK && !shot.friendly(), "Orc did not throw a rock");
        check(orc.pollProjectile() == null, "Projectile queue returned the same shot twice");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
