package entity;

import java.awt.geom.Rectangle2D;
import java.util.function.Predicate;

/** Shared world position and collision-safe motion; actors do not know about the game panel. */
public abstract class Entity {
    protected double x;
    protected double y;

    public final double x() { return x; }
    public final double y() { return y; }

    public final void setPosition(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Actor position must be finite");
        }
        this.x = x;
        this.y = y;
    }

    public abstract Rectangle2D.Double bounds();

    /** Resolve each axis in small steps, so faster boots and knockback cannot cross a wall. */
    protected final boolean move(double dx, double dy, Predicate<Rectangle2D> blocked) {
        if (dx == 0 && dy == 0) return false;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 4.0));
        double stepX = dx / steps;
        double stepY = dy / steps;
        boolean moved = false;
        for (int step = 0; step < steps; step++) {
            if (stepX != 0) {
                x += stepX;
                if (blocked.test(bounds())) x -= stepX;
                else moved = true;
            }
            if (stepY != 0) {
                y += stepY;
                if (blocked.test(bounds())) y -= stepY;
                else moved = true;
            }
        }
        return moved;
    }
}
