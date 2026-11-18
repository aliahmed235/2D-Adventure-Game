package main;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Logical-pixel bounds shared by drawing and mouse input. */
public final class MenuLayout {
    private MenuLayout() { }

    public static Rectangle pauseFrame(int width, int height) {
        return new Rectangle((width - 720) / 2, (height - 440) / 2, 720, 440);
    }

    public static List<Rectangle> pauseRows(int width, int height) {
        Rectangle frame = pauseFrame(width, height);
        List<Rectangle> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) rows.add(new Rectangle(frame.x + 333, frame.y + 76 + i * 50, 350, 43));
        return List.copyOf(rows);
    }

    public static int pauseOptionAt(int x, int y, int width, int height) {
        List<Rectangle> rows = pauseRows(width, height);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).contains(x, y)) return i;
        return -1;
    }

    public static Rectangle startButton(int width, int height) {
        return new Rectangle(width / 2 - 140, height / 2 + 172, 280, 48);
    }

    public static Rectangle controlsFrame(int width, int height) {
        return new Rectangle((width - 760) / 2, (height - 566) / 2, 760, 566);
    }

    public static Rectangle controlsBackButton(int width, int height) {
        Rectangle frame = controlsFrame(width, height);
        return new Rectangle(width / 2 - 70, frame.y + 500, 140, 42);
    }
}
