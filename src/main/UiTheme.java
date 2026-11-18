package main;

import core.Assets;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared materials and type for the wayfarer's instruments and field journal. */
public final class UiTheme {
    public static final Color INK = new Color(37, 43, 34);
    public static final Color PAPER = new Color(225, 211, 172);
    public static final Color PAPER_DARK = new Color(193, 170, 121);
    public static final Color BRASS = new Color(156, 119, 64);
    public static final Color GOLD = new Color(232, 194, 112);
    public static final Color CREAM = new Color(244, 232, 198);
    public static final Color MUTED = new Color(166, 173, 145);
    public static final Color MOSS = new Color(98, 126, 85);
    public static final Color RED = new Color(189, 76, 64);
    public static final Font TINY = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font LABEL = new Font(Font.MONOSPACED, Font.BOLD, 12);
    public static final Font HEADING = new Font(Font.SERIF, Font.BOLD, 28);
    public static final Font TITLE = new Font(Font.SERIF, Font.BOLD, 38);
    private UiTheme() { }

    public static void text(Graphics2D g, String text, int x, int baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    public static void centered(Graphics2D g, String text, int centerX, int baseline, Font font, Color color) {
        g.setFont(font);
        text(g, text, centerX - g.getFontMetrics().stringWidth(text) / 2, baseline, font, color);
    }

    public static void image(Graphics2D g, String path, int x, int y, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(Assets.image(path), x, y, width, height, null);
    }

    public static void icon(Graphics2D g, String texture, int x, int y, int size) {
        image(g, "/objects/" + texture + ".png", x, y, size, size);
    }

    public static int keycap(Graphics2D g, String key, int x, int y) {
        g.setFont(LABEL);
        int width = Math.max(22, g.getFontMetrics().stringWidth(key) + 14);
        g.setColor(new Color(13, 22, 18, 200));
        g.fillRect(x, y + 2, width, 22);
        g.setColor(new Color(77, 88, 64));
        g.fillRect(x, y, width, 20);
        g.setColor(new Color(180, 167, 120));
        g.drawRect(x, y, width - 1, 19);
        g.setColor(new Color(111, 115, 80));
        g.drawLine(x + 2, y + 17, x + width - 3, y + 17);
        centered(g, key, x + width / 2, y + 14, LABEL, CREAM);
        return width;
    }

    public static int wrap(Graphics2D g, String text, int x, int baseline, int maxWidth,
                           int lineHeight, Font font, Color color) {
        g.setFont(font);
        for (String line : lines(g, text, maxWidth)) {
            text(g, line, x, baseline, font, color);
            baseline += lineHeight;
        }
        return baseline - lineHeight;
    }

    public static List<String> lines(Graphics2D g, String value, int width) {
        List<String> result = new ArrayList<>();
        FontMetrics metrics = g.getFontMetrics();
        for (String paragraph : value.split("\\R", -1)) {
            String line = "";
            for (String word : paragraph.split("\\s+")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && metrics.stringWidth(next) > width) {
                    result.add(line);
                    line = word;
                } else line = next;
            }
            result.add(line);
        }
        return result;
    }

    public static void cutPanel(Graphics2D g, int x, int y, int width, int height, Color fill, Color edge) {
        Polygon shape = cutShape(x, y, width, height, 6);
        g.setColor(new Color(0, 0, 0, 75));
        g.fill(cutShape(x + 3, y + 5, width, height, 6));
        g.setColor(fill);
        g.fill(shape);
        g.setStroke(new BasicStroke(2));
        g.setColor(edge);
        g.draw(shape);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 55));
        g.draw(cutShape(x + 4, y + 4, width - 8, height - 8, 4));
    }

    private static Polygon cutShape(int x, int y, int w, int h, int cut) {
        return new Polygon(new int[]{x + cut, x + w - cut, x + w, x + w, x + w - cut, x + cut, x, x},
                new int[]{y, y, y + cut, y + h - cut, y + h, y + h, y + h - cut, y + cut}, 8);
    }

    public static void paper(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(PAPER);
        g.fillRect(x, y, width, height);
        g.setColor(new Color(109, 80, 43, 13));
        // Fixed fibers keep screenshots deterministic and text free of noisy animation.
        for (int row = 9; row < height - 4; row += 13) {
            int offset = (row * 17) % 47;
            for (int col = 8 + offset; col < width - 8; col += 61) g.fillRect(x + col, y + row, 7, 1);
        }
        g.setColor(new Color(111, 82, 40, 30));
        g.drawRect(x + 1, y + 1, width - 3, height - 3);
    }

    public static void rule(Graphics2D g, int x, int y, int width, Color color) {
        g.setColor(color);
        g.drawLine(x, y, x + width, y);
        g.fillRect(x, y - 2, 3, 5);
        g.fillRect(x + width - 2, y - 2, 3, 5);
    }

    public static String time(double seconds) {
        long total = Math.max(0, (long) seconds);
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }

    public static Font fitFont(Graphics2D g, String text, Font font, int width, float minimum) {
        Font fitted = font;
        while (g.getFontMetrics(fitted).stringWidth(text) > width && fitted.getSize2D() > minimum)
            fitted = fitted.deriveFont(Math.max(minimum, fitted.getSize2D() - 1));
        return fitted;
    }

    public static String fit(Graphics2D g, String value, Font font, int width) {
        g.setFont(font);
        if (g.getFontMetrics().stringWidth(value) <= width) return value;
        String shortened = value;
        while (!shortened.isEmpty() && g.getFontMetrics().stringWidth(shortened + "…") > width)
            shortened = shortened.substring(0, shortened.offsetByCodePoints(shortened.length(), -1));
        return shortened + "…";
    }
}
