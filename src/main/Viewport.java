package main;

import java.awt.Graphics2D;
import java.awt.Point;

/** One transform keeps pixel art, interface layout, and mouse hit targets aligned. */
public record Viewport(double scale, double offsetX, double offsetY,
                       int logicalWidth, int logicalHeight) {
    public Viewport {
        if (!Double.isFinite(scale) || scale <= 0 || !Double.isFinite(offsetX)
                || !Double.isFinite(offsetY) || logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("Invalid viewport dimensions");
        }
    }

    public static Viewport fit(int width, int height, int logicalWidth, int logicalHeight) {
        if (width <= 0 || height <= 0 || logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        double scale = Math.min(width / (double) logicalWidth, height / (double) logicalHeight);
        return new Viewport(scale, (width - logicalWidth * scale) / 2,
                (height - logicalHeight * scale) / 2, logicalWidth, logicalHeight);
    }

    public double renderWidth() { return logicalWidth * scale; }
    public double renderHeight() { return logicalHeight * scale; }

    public void apply(Graphics2D graphics) {
        graphics.translate(offsetX, offsetY);
        graphics.scale(scale, scale);
        graphics.clipRect(0, 0, logicalWidth, logicalHeight);
    }

    /** Letterbox bars are outside the game and must never activate a menu item. */
    public Point toLogical(int screenX, int screenY) {
        if (screenX < offsetX || screenY < offsetY
                || screenX >= offsetX + renderWidth() || screenY >= offsetY + renderHeight()) return null;
        return new Point(Math.min(logicalWidth - 1, (int) ((screenX - offsetX) / scale)),
                Math.min(logicalHeight - 1, (int) ((screenY - offsetY) / scale)));
    }
}
