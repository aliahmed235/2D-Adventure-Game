package entity;

import core.Assets;
import world.EnemySpawn;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Typed monsters share collision-safe movement while keeping distinct combat patterns. */
public final class Enemy extends Entity {
    private final String id;
    private final EnemyKind kind;
    private final String defeatFlag;
    private final int tileSize;
    private final int maxHealth;
    private final double speed;
    private final double originX;
    private final double originY;
    private final double patrolRadius;
    private final Set<Long> recentHits = new LinkedHashSet<>();
    private int health;
    private int patrolDirection;
    private Direction direction = Direction.DOWN;
    private double patrolTimer;
    private double age;
    private double flashRemaining;
    private double staggerRemaining;
    private double knockbackX;
    private double knockbackY;
    private double attackRemaining;
    private double attackCooldown = .7;
    private double shotCooldown = 1.6;
    private Projectile pendingShot;
    private boolean alert;

    public Enemy(EnemySpawn spawn, int tileSize) {
        if (tileSize <= 0 || spawn.health() <= 0 || !Double.isFinite(spawn.speed()) || spawn.speed() < 0)
            throw new IllegalArgumentException("Invalid enemy spawn: " + spawn.id());
        id = spawn.id();
        kind = spawn.kind();
        defeatFlag = spawn.defeatFlag();
        this.tileSize = tileSize;
        maxHealth = health = spawn.health();
        speed = spawn.speed();
        originX = x = spawn.col() * (double) tileSize;
        originY = y = spawn.row() * (double) tileSize;
        patrolRadius = Math.max(0, spawn.patrolRadius()) * (double) tileSize;
        patrolDirection = Math.floorMod(id.hashCode(), 4);
        age = Math.floorMod(id.hashCode(), 20) / 10.0;
    }

    public String id() { return id; }
    public EnemyKind kind() { return kind; }
    public String defeatFlag() { return defeatFlag; }
    public String displayName() { return kind.displayName; }
    public Direction direction() { return direction; }
    public int health() { return health; }
    public int maxHealth() { return maxHealth; }
    public boolean alive() { return health > 0; }
    public boolean phaseTwo() { return kind == EnemyKind.SKELETON_LORD && health * 2 <= maxHealth; }
    /** The windup is visible but only the final interval can deal melee damage. */
    public boolean attacking() { return kind.melee() && attackRemaining > 0 && attackRemaining <= .28; }
    public int attackDamage() { return kind == EnemyKind.SKELETON_LORD ? 3 : 2; }
    public Projectile pollProjectile() { Projectile shot = pendingShot; pendingShot = null; return shot; }

    @Override public Rectangle2D.Double bounds() {
        return kind == EnemyKind.SKELETON_LORD
                ? new Rectangle2D.Double(x + tileSize * .12, y + tileSize * .12, tileSize * .76, tileSize * .78)
                : new Rectangle2D.Double(x + tileSize * .22, y + tileSize * .23, tileSize * .56, tileSize * .65);
    }

    public Rectangle2D.Double attackBox() {
        if (!attacking()) return new Rectangle2D.Double();
        double reach = kind == EnemyKind.SKELETON_LORD ? tileSize * 1.9 : tileSize * 1.1;
        double breadth = kind == EnemyKind.SKELETON_LORD ? tileSize * 1.15 : tileSize * .78;
        double centerX = x + tileSize * .5, centerY = y + tileSize * .58;
        return switch (direction) {
            case UP -> new Rectangle2D.Double(centerX - breadth / 2, centerY - reach, breadth, reach);
            case DOWN -> new Rectangle2D.Double(centerX - breadth / 2, centerY, breadth, reach);
            case LEFT -> new Rectangle2D.Double(centerX - reach, centerY - breadth / 2, reach, breadth);
            case RIGHT -> new Rectangle2D.Double(centerX, centerY - breadth / 2, reach, breadth);
        };
    }

    public void update(double dt, Player player, Predicate<Rectangle2D> blocked) {
        if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid time step");
        age += dt;
        flashRemaining = Math.max(0, flashRemaining - dt);
        staggerRemaining = Math.max(0, staggerRemaining - dt);
        attackCooldown = Math.max(0, attackCooldown - dt);
        shotCooldown = Math.max(0, shotCooldown - dt);
        if (!alive()) return;
        if (Math.abs(knockbackX) + Math.abs(knockbackY) > 1) {
            move(knockbackX * dt, knockbackY * dt, blocked);
            double decay = Math.exp(-15 * dt);
            knockbackX *= decay;
            knockbackY *= decay;
        }
        if (staggerRemaining > 0 || !player.alive()) return;
        if (attackRemaining > 0) {
            attackRemaining = Math.max(0, attackRemaining - dt);
            return;
        }
        double dx = player.x() - x, dy = player.y() - y;
        double distance = Math.hypot(dx, dy);
        alert = distance < tileSize * (kind == EnemyKind.SKELETON_LORD ? 9 : 5.5);
        if (alert) {
            direction = facing(dx, dy);
            if (kind.melee() && distance < tileSize * (kind == EnemyKind.SKELETON_LORD ? 1.6 : 1.15)
                    && attackCooldown <= 0) {
                attackRemaining = phaseTwo() ? .52 : .73;
                attackCooldown = phaseTwo() ? 1.1 : 1.55;
                return;
            }
            if (shotCooldown <= 0 && distance > tileSize * 2.1
                    && (kind == EnemyKind.ORC || phaseTwo())) {
                pendingShot = new Projectile(kind == EnemyKind.ORC ? Projectile.Kind.ROCK : Projectile.Kind.FIREBALL,
                        false, x + tileSize / 2.0 + direction.dx * 38,
                        y + tileSize / 2.0 + direction.dy * 38, direction);
                shotCooldown = phaseTwo() ? 1.1 : 2.2;
            }
            if (distance > tileSize * .23) {
                double pace = speed * (phaseTwo() ? 1.22 : 1);
                if (kind == EnemyKind.RED_SLIME && age % 2.8 > 2.2) pace *= 1.85;
                move(dx / distance * pace * dt, dy / distance * pace * dt, blocked);
            }
            return;
        }
        patrol(dt, blocked);
    }

    private void patrol(double dt, Predicate<Rectangle2D> blocked) {
        if (patrolRadius == 0 || speed == 0) return;
        patrolTimer -= dt;
        if (patrolTimer <= 0) { patrolDirection = (patrolDirection + 1) % 4; patrolTimer = 1.5; }
        if (Math.abs(x - originX) > patrolRadius || Math.abs(y - originY) > patrolRadius) {
            double dx = originX - x, dy = originY - y, distance = Math.hypot(dx, dy);
            if (distance > 1) {
                direction = facing(dx, dy);
                move(dx / distance * speed * .55 * dt, dy / distance * speed * .55 * dt, blocked);
            }
        } else {
            direction = Direction.values()[patrolDirection];
            if (!move(direction.dx * speed * .55 * dt, direction.dy * speed * .55 * dt, blocked)) {
                patrolDirection = (patrolDirection + 1) % 4;
                patrolTimer = 1;
            }
        }
    }

    private static Direction facing(double dx, double dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? Direction.LEFT : Direction.RIGHT;
        return dy < 0 ? Direction.UP : Direction.DOWN;
    }

    /** Exact IDs permit melee swings and separately identified projectiles to interleave safely. */
    public boolean damage(int amount, long attackId, Direction incoming) {
        if (!alive() || amount <= 0 || !recentHits.add(attackId)) return false;
        if (recentHits.size() > 64) recentHits.remove(recentHits.iterator().next());
        health = Math.max(0, health - amount);
        flashRemaining = .18;
        staggerRemaining = .16;
        attackRemaining = 0;
        if (incoming != null) {
            double impulse = kind == EnemyKind.SKELETON_LORD ? 75 : 240;
            knockbackX = incoming.dx * impulse;
            knockbackY = incoming.dy * impulse;
        }
        if (!alive()) pendingShot = null;
        return true;
    }

    /** Checkpoints keep damage and defeated state but clear stale attacks and knockback. */
    public void resetAtSpawn() {
        x = originX; y = originY;
        flashRemaining = staggerRemaining = knockbackX = knockbackY = attackRemaining = 0;
        attackCooldown = .8; shotCooldown = 1.6; pendingShot = null; alert = false;
    }

    public void draw(Graphics2D graphics, int screenX, int screenY, int tileSize) {
        if (!alive()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(screenX, screenY);
        g.scale(tileSize / 64.0, tileSize / 64.0);
        boolean boss = kind == EnemyKind.SKELETON_LORD;
        int bob = kind == EnemyKind.BAT ? -9 + (int) Math.round(Math.sin(age * 7) * 5) : 0;
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(boss ? -6 : 12, 49, boss ? 76 : 40, 13);
        String prefix = kind.sprite + (phaseTwo() ? "_phase2" : "");
        boolean swing = kind.melee() && attackRemaining > 0;
        String facing = kind.melee() ? direction.sprite : "down";
        int frame = swing ? (attacking() ? 2 : 1) : 1 + ((int) (age / (kind == EnemyKind.BAT ? .1 : .23)) & 1);
        String path = "/Monster/" + prefix + (swing ? "_attack" : "") + "_" + facing + "_" + frame + ".png";
        BufferedImage sprite = Assets.image(path);
        int sizeX = sprite.getWidth() * 4, sizeY = sprite.getHeight() * 4;
        int offsetX = boss ? -32 : 0, offsetY = (boss ? -64 : 0) + bob;
        if (swing && direction == Direction.LEFT) offsetX -= boss ? 128 : 64;
        if (swing && direction == Direction.UP) offsetY -= boss ? 128 : 64;
        if (flashRemaining > 0 && ((int) (flashRemaining * 40) & 1) == 0)
            g.setComposite(AlphaComposite.SrcOver.derive(.55f));
        g.drawImage(sprite, offsetX, offsetY, sizeX, sizeY, null);
        g.setComposite(AlphaComposite.SrcOver);
        if (swing && !attacking()) {
            g.setColor(new Color(255, 181, 89));
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            g.drawString("!", 27, boss ? -70 : -7);
        }
        if (health < maxHealth || alert) {
            int barX = boss ? -16 : 11, barY = boss ? -70 : -6 + bob, barWidth = boss ? 96 : 42;
            g.setColor(new Color(12, 18, 24, 225));
            g.fillRect(barX, barY, barWidth, 7);
            g.setColor(phaseTwo() ? new Color(230, 99, 52) : new Color(190, 75, 67));
            g.fillRect(barX + 1, barY + 1, Math.max(0, (barWidth - 2) * health / maxHealth), 5);
        }
        g.dispose();
    }
}
