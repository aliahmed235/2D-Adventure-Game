package game;

import core.AudioManager;
import entity.Enemy;
import entity.EnemyKind;
import entity.Player;
import entity.Projectile;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Damage, rewards, and projectile lifetimes, independent of windows and level authoring. */
public final class CombatSystem {
    private final AudioManager audio;
    private final List<Projectile> projectiles = new ArrayList<>();
    private double spellCooldown;

    public CombatSystem(AudioManager audio) { this.audio = audio; }
    public List<Projectile> projectiles() { return Collections.unmodifiableList(projectiles); }
    public double spellCooldown() { return spellCooldown; }
    public void clearProjectiles() { projectiles.clear(); }
    public void reset() { projectiles.clear(); spellCooldown = 0; }

    public boolean cast(Player player) {
        if (!player.alive() || spellCooldown > 0 || player.guarding()) return false;
        Rectangle2D body = player.bounds();
        projectiles.add(new Projectile(Projectile.Kind.FIREBALL, true,
                body.getCenterX() + player.direction().dx * 30,
                body.getCenterY() + player.direction().dy * 30, player.direction()));
        spellCooldown = 1.2;
        audio.effect("burning");
        return true;
    }

    public void update(double dt, Player player, Inventory inventory, List<Enemy> enemies,
                       Predicate<Rectangle2D> blocked,
                       BiPredicate<Rectangle2D, Rectangle2D> clearPath,
                       Consumer<String> message) {
        spellCooldown = Math.max(0, spellCooldown - dt);
        for (Enemy enemy : enemies) {
            if (!enemy.alive()) continue;
            enemy.update(dt, player, blocked);
            if (player.attacking() && player.attackBox().intersects(enemy.bounds())
                    && clearPath.test(player.bounds(), enemy.bounds())) {
                damageEnemy(enemy, 1, player.attackId(), player.direction(), player, inventory, message);
            }
            if (!enemy.alive()) continue;
            Projectile shot = enemy.pollProjectile();
            if (shot != null && clearPath.test(enemy.bounds(), player.bounds())) projectiles.add(shot);
            boolean armed = enemy.kind() == EnemyKind.ORC || enemy.kind() == EnemyKind.SKELETON_LORD;
            Rectangle2D danger = armed ? enemy.attackBox() : enemy.bounds();
            if ((!armed || enemy.attacking()) && danger.intersects(player.bounds())
                    && clearPath.test(enemy.bounds(), player.bounds())) {
                damagePlayer(player, inventory, enemy.attackDamage());
            }
        }
        for (Projectile projectile : projectiles) {
            // Check actors along the flight path, including slow frames and test time jumps.
            int steps = Math.max(1, (int) Math.ceil(dt / .016));
            for (int step = 0; step < steps && projectile.alive(); step++) {
                projectile.update(dt / steps, blocked);
                if (!projectile.alive()) break;
                if (projectile.friendly()) {
                    for (Enemy enemy : enemies) {
                        if (!enemy.alive() || !projectile.bounds().intersects(enemy.bounds())) continue;
                        damageEnemy(enemy, projectile.damage(), projectile.attackId(), projectile.direction(),
                                player, inventory, message);
                        projectile.expire();
                        break;
                    }
                } else if (projectile.bounds().intersects(player.bounds())) {
                    damagePlayer(player, inventory, projectile.damage());
                    projectile.expire();
                }
            }
        }
        projectiles.removeIf(projectile -> !projectile.alive());
    }

    private void damageEnemy(Enemy enemy, int damage, long attackId, entity.Direction direction,
                             Player player, Inventory inventory, Consumer<String> message) {
        if (!enemy.damage(damage, attackId, direction)) return;
        audio.effect("hitmonster");
        if (enemy.alive()) return;
        boolean boss = enemy.kind() == EnemyKind.SKELETON_LORD;
        int reward = boss ? 25 : 3;
        inventory.addCoins(reward);
        if (!enemy.defeatFlag().isBlank()) inventory.grant(enemy.defeatFlag());
        audio.effect(boss ? "fanfare" : "coin");
        if (boss) {
            player.heal(player.maxHealth());
            message.accept("You beat the boss! Open the treasure chest.");
        } else message.accept(enemy.displayName() + " defeated. +" + reward + " coins");
    }

    private void damagePlayer(Player player, Inventory inventory, int amount) {
        if (player.takeDamage(amount, inventory.has(ItemType.SHIELD))) audio.effect("receivedamage");
        if (player.consumeBlock()) audio.effect("parry");
    }
}
