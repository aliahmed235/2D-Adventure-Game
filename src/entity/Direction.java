package entity;

/** Cardinal directions shared by movement, sprites, sword reach, and knockback. */
public enum Direction {
    UP(0, -1, "up"), DOWN(0, 1, "down"), LEFT(-1, 0, "left"), RIGHT(1, 0, "right");

    public final int dx;
    public final int dy;
    public final String sprite;

    Direction(int dx, int dy, String sprite) {
        this.dx = dx;
        this.dy = dy;
        this.sprite = sprite;
    }
}
