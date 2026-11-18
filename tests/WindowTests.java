package tests;

import main.Viewport;
import java.awt.Point;

/** Headless checks that fullscreen keeps the game aspect ratio and menu hit targets. */
public final class WindowTests {
    private WindowTests() { }

    public static void run() {
        Viewport nativeSize = Viewport.fit(1024, 768, 1024, 768);
        check(nativeSize.scale() == 1 && nativeSize.offsetX() == 0 && nativeSize.offsetY() == 0,
                "Native resolution should have no scale or offset");
        Viewport wide = Viewport.fit(1920, 1080, 1024, 768);
        close(wide.scale(), 1.40625);
        close(wide.offsetX(), 240);
        close(wide.offsetY(), 0);
        close(wide.renderWidth(), 1440);
        close(wide.renderHeight(), 1080);
        check(wide.toLogical(239, 100) == null && wide.toLogical(1680, 100) == null,
                "Side bars must not accept game clicks");
        check(wide.toLogical(960, 540).equals(new Point(512, 384)),
                "Fullscreen mouse position must map to the center of the game");
        check(wide.toLogical(240, 0).equals(new Point(0, 0)), "Top-left mapping changed");
        Viewport tall = Viewport.fit(600, 1000, 1024, 768);
        close(tall.scale(), 600.0 / 1024);
        close(tall.offsetY(), 275);
        check(tall.toLogical(300, 274) == null && tall.toLogical(300, 725) == null,
                "Top and bottom bars must not accept game clicks");
        check(tall.toLogical(300, 500).equals(new Point(512, 384)),
                "Downscaled mouse mapping must match the scene");
        Viewport oddSize = Viewport.fit(1365, 767, 1024, 768);
        Point logical = oddSize.toLogical((int) Math.ceil(oddSize.offsetX() + 700 * oddSize.scale()),
                (int) Math.ceil(oddSize.offsetY() + 500 * oddSize.scale()));
        check(logical != null && Math.abs(logical.x - 700) <= 1 && Math.abs(logical.y - 500) <= 1,
                "Fractional fullscreen scales must preserve click accuracy");
        expectInvalid(() -> Viewport.fit(0, 768, 1024, 768));
        expectInvalid(() -> new Viewport(Double.NaN, 0, 0, 1024, 768));
    }

    public static void main(String[] args) { run(); System.out.println("Viewport checks passed."); }
    private static void close(double value, double expected) {
        check(Math.abs(value - expected) < 0.00001, "Viewport dimension mismatch: " + value);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid viewport should fail clearly");
    }
}
