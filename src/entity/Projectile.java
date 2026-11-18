package entity;

import core.Assets;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/** A constructor center becomes a 32px sprite's top-left x/y. IDs are separate from melee swings. */
public final class Projectile extends Entity {
    public enum Kind { FIREBALL, ROCK }
    private static final AtomicLong IDS = new AtomicLong();
    private final long attackId = -IDS.incrementAndGet();
    private final Kind kind;
    private final boolean friendly;
    private final Direction direction;
    private boolean alive = true;
    private double remaining = 2.2;
    private double age;

    public Projectile(Kind kind, boolean friendly, double centerX, double centerY, Direction direction) {
        this.kind = java.util.Objects.requireNonNull(kind);
        this.friendly = friendly;
        this.direction = java.util.Objects.requireNonNull(direction);
        setPosition(centerX - 16, centerY - 16);
    }
    public Kind kind() { return kind; }
    public boolean friendly() { return friendly; }
    public boolean alive() { return alive; }
    public void expire() { alive = false; }
    public int damage() { return 2; }
    public Direction direction() { return direction; }
    public long attackId() { return attackId; }
    @Override public Rectangle2D.Double bounds() { return new Rectangle2D.Double(x + 5, y + 5, 22, 22); }

    public void update(double dt, Predicate<Rectangle2D> blocked) {
        if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid time step");
        if (!alive) return;
        age += dt;
        remaining -= dt;
        if (remaining <= 0) { expire(); return; }
        double distance = (kind == Kind.FIREBALL ? 360 : 240) * dt;
        int steps = Math.max(1, (int) Math.ceil(distance / 6));
        for (int step = 0; step < steps; step++) {
            x += direction.dx * distance / steps;
            y += direction.dy * distance / steps;
            if (blocked.test(bounds())) { expire(); break; }
        }
    }

    public void draw(Graphics2D g, int screenX, int screenY, int tileSize) {
        if (!alive) return;
        String path = kind == Kind.ROCK ? "/Projectile/rock_down_1.png"
                : "/Projectile/fireball_" + direction.sprite + "_" + (1 + ((int) (age / .1) & 1)) + ".png";
        int size = tileSize / 2;
        g.drawImage(Assets.image(path), screenX, screenY, size, size, null);
    }
}
