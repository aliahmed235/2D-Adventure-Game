package entity;

import core.Assets;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Predicate;

/** Player movement, health, and attack timing. Interactions and inventory belong to the session. */
public final class Player extends Entity {
    public static final int TILE_SIZE = 64;
    public static final int MAX_HEALTH = 6;
    private static final double ATTACK_COOLDOWN = 0.36;
    private static final double DAMAGE_IMMUNITY = 0.90;

    private Direction direction = Direction.DOWN;
    private boolean moving;
    private int health = MAX_HEALTH;
    private double animationTime;
    private double attackRemaining;
    private double attackCooldown;
    private double immunityRemaining;
    private long attackId;
    private Weapon weapon = Weapon.SWORD;
    private boolean guarding;
    private boolean blockPending;
    private double blockCooldown;

    public Player() { }

    public Direction direction() { return direction; }
    public boolean moving() { return moving; }
    public int frame() { return moving && ((int) (animationTime / 0.14) & 1) == 1 ? 2 : 1; }
    public int health() { return health; }
    public int maxHealth() { return MAX_HEALTH; }
    public boolean alive() { return health > 0; }
    public boolean attacking() { return attackRemaining > 0; }
    public long attackId() { return attackId; }
    public Weapon weapon() { return weapon; }
    public void setWeapon(Weapon value) { if (!attacking()) weapon = java.util.Objects.requireNonNull(value); }
    public boolean guarding() { return guarding; }
    public void setGuarding(boolean value) { guarding = value && alive() && !attacking(); }
    public boolean consumeBlock() { boolean pending = blockPending; blockPending = false; return pending; }

    @Override
    public Rectangle2D.Double bounds() {
        // The lower body is solid; the sprite's transparent margin and head are not.
        return new Rectangle2D.Double(x + 16, y + 28, 32, 30);
    }

    public void reset() {
        x = y = 0;
        health = MAX_HEALTH;
        direction = Direction.DOWN;
        moving = false;
        animationTime = attackRemaining = attackCooldown = immunityRemaining = 0;
        weapon = Weapon.SWORD;
        guarding = blockPending = false;
        blockCooldown = 0;
        // Keep swing IDs monotonic if a checkpoint restores the player while enemies survive.
    }

    public void face(Direction direction) {
        if (direction != null && !attacking()) this.direction = direction;
    }

    public void update(double dt, int axisX, int axisY, boolean boots,
                       Predicate<Rectangle2D> blocked) {
        if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid time step");
        attackRemaining = Math.max(0, attackRemaining - dt);
        attackCooldown = Math.max(0, attackCooldown - dt);
        immunityRemaining = Math.max(0, immunityRemaining - dt);
        blockCooldown = Math.max(0, blockCooldown - dt);
        moving = false;
        if (!alive()) return;

        // Keep the original four-direction feel, even when two movement keys are held.
        int dx = Integer.signum(axisX);
        int dy = Integer.signum(axisY);
        if (dy != 0) dx = 0;
        if (!attacking()) {
            if (dy < 0) direction = Direction.UP;
            else if (dy > 0) direction = Direction.DOWN;
            else if (dx < 0) direction = Direction.LEFT;
            else if (dx > 0) direction = Direction.RIGHT;
        }
        double speed = boots ? 300 : 220;
        if (attacking()) speed *= 0.55;
        if (guarding) speed *= .38;
        moving = move(dx * speed * dt, dy * speed * dt, blocked);
        if (moving) animationTime += dt;
        else animationTime = 0;
    }

    public boolean attack() {
        if (!alive() || guarding || attackCooldown > 0) return false;
        attackCooldown = Math.max(ATTACK_COOLDOWN, weapon.duration() + .14);
        attackRemaining = weapon.duration();
        attackId++;
        return true;
    }

    /** Reach is tied to the same facing direction used by the visible sword. */
    public Rectangle2D.Double attackBox() {
        if (!attacking()) return new Rectangle2D.Double();
        return switch (direction) {
            case UP -> new Rectangle2D.Double(x + 7, y - 35, 50, 68);
            case DOWN -> new Rectangle2D.Double(x + 7, y + 37, 50, 68);
            case LEFT -> new Rectangle2D.Double(x - 40, y + 15, 67, 50);
            case RIGHT -> new Rectangle2D.Double(x + 37, y + 15, 67, 50);
        };
    }

    public boolean takeDamage(int amount, boolean shield) {
        if (!alive() || amount <= 0 || immunityRemaining > 0) return false;
        if (guarding && shield) {
            if (blockCooldown <= 0) { blockPending = true; blockCooldown = .25; }
            return false;
        }
        int damage = shield ? Math.max(1, amount - 1) : amount;
        health = Math.max(0, health - damage);
        immunityRemaining = DAMAGE_IMMUNITY;
        return true;
    }

    public void heal(int amount) {
        if (amount > 0 && alive()) health = Math.min(MAX_HEALTH, health + amount);
    }

    /** Checkpoint recovery keeps world position and swing IDs while restoring a safe live state. */
    public void revive() {
        health = MAX_HEALTH;
        moving = false;
        animationTime = attackRemaining = attackCooldown = 0;
        immunityRemaining = DAMAGE_IMMUNITY;
        guarding = blockPending = false;
        blockCooldown = 0;
    }

    public void draw(Graphics2D graphics, int screenX, int screenY, int tileSize,
                     boolean hasWeapon, boolean hasShield) {
        Graphics2D g = (Graphics2D) graphics.create();
        double scale = tileSize / (double) TILE_SIZE;
        g.translate(screenX, screenY);
        g.scale(scale, scale);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillOval(13, 49, 38, 13);
        if (immunityRemaining > 0 && ((int) (immunityRemaining * 16) & 1) == 0) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        }
        boolean swing = hasWeapon && attacking();
        String path = guarding && hasShield ? "/Player/Guarding sprites/boy_guard_" + direction.sprite + ".png"
                : swing ? "/Player/Attacking sprites/boy_" + weapon.spritePrefix() + "_" + direction.sprite
                + "_" + (attackRemaining > weapon.duration() * .52 ? 1 : 2) + ".png"
                : "/Player/boy_" + direction.sprite + "_" + frame() + ".png";
        BufferedImage sprite = Assets.image(path);
        int spriteWidth = sprite.getWidth() * 4, spriteHeight = sprite.getHeight() * 4;
        int offsetX = swing && direction == Direction.LEFT ? -TILE_SIZE : 0;
        int offsetY = swing && direction == Direction.UP ? -TILE_SIZE : 0;
        g.drawImage(sprite, offsetX, offsetY, spriteWidth, spriteHeight, null);
        if (hasShield && !guarding && !swing && direction != Direction.UP) {
            g.drawImage(Assets.image("/objects/shield_wood.png"), 1, 33, 23, 23, null);
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.dispose();
    }
}
