package core;

/** Actors use world coordinates; the camera alone converts them to screen coordinates. */
public final class Camera {
    private double x;
    private double y;
    public void follow(double px, double py, int worldWidth, int worldHeight,
                       int screenWidth, int screenHeight, int tileSize) {
        x = worldWidth < screenWidth ? (worldWidth - screenWidth) / 2.0
                : Math.max(0, Math.min(px + tileSize / 2.0 - screenWidth / 2.0, worldWidth - screenWidth));
        y = worldHeight < screenHeight ? (worldHeight - screenHeight) / 2.0
                : Math.max(0, Math.min(py + tileSize / 2.0 - screenHeight / 2.0, worldHeight - screenHeight));
    }
    public int screenX(double wx) { return (int) Math.round(wx - x); }
    public int screenY(double wy) { return (int) Math.round(wy - y); }
    public double x() { return x; }
    public double y() { return y; }
}
