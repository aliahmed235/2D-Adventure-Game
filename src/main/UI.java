package main;

import game.GameSession;
import game.GameState;
import java.awt.*;
import java.util.List;

import static main.UiTheme.*;

/** Views only read state; keyboard and mouse actions are handled by the session/controller. */
public final class UI {
    private static final Color PANEL = new Color(25, 38, 30, 245);
    private static final Font STRONG = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private final GameSession session;

    public UI(GameSession session) { this.session = session; }

    public void draw(Graphics2D graphics, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        GameState state = session.state();
        if (state == GameState.TITLE) drawTitle(g, width, height);
        else {
            HudView.draw(g, session, width, height);
            if (session.minimapVisible()) HudView.drawMap(g, session, width);
            if (state == GameState.PLAYING) {
                drawNotifications(g, width, height);
                drawLocation(g, width, height);
            }
            switch (state) {
                case PAUSED -> drawPause(g, width, height);
                case CONTROLS -> drawControls(g, width, height);
                case INVENTORY -> InventoryView.draw(g, session, width, height);
                case GAME_OVER -> drawEnd(g, width, height, false);
                case VICTORY -> drawEnd(g, width, height, true);
                default -> { }
            }
        }
        g.dispose();
    }

    private void drawNotifications(Graphics2D g, int width, int height) {
        int bottom = height - (HudView.bossVisible(session) ? 147 : 88);
        String prompt = session.prompt(), message = session.message();
        if (prompt != null && !prompt.isBlank()) {
            String action = prompt.replaceFirst("^E\\s+", "");
            g.setFont(STRONG);
            int boxWidth = Math.min(width - 96, g.getFontMetrics().stringWidth(action) + 66);
            List<String> promptLines = lines(g, action, boxWidth - 58);
            int boxHeight = 20 + promptLines.size() * 18;
            int x = (width - boxWidth) / 2, y = bottom - boxHeight;
            panel(g, x, y, boxWidth, boxHeight);
            keycap(g, "E", x + 9, y + 8);
            for (int i = 0; i < promptLines.size(); i++)
                text(g, promptLines.get(i), x + 41, y + 24 + i * 18, STRONG, GOLD);
            bottom -= boxHeight + 10;
        }
        if (message != null && !message.isBlank()) {
            int boxWidth = Math.min(560, width - 96);
            g.setFont(BODY);
            List<String> messageLines = lines(g, message, boxWidth - 32);
            int boxHeight = messageLines.size() * 19 + 22;
            int x = (width - boxWidth) / 2, y = bottom - boxHeight;
            cutPanel(g, x, y, boxWidth, boxHeight, PAPER, BRASS);
            for (int i = 0; i < messageLines.size(); i++)
                text(g, messageLines.get(i), x + 16, y + 26 + i * 19, BODY, INK);
        }
    }

    private void drawLocation(Graphics2D g, int width, int height) {
        String name = session.locationAnnouncement();
        if (session.announcementTime() <= 0 || name == null || name.isBlank()) return;
        Graphics2D fade = (Graphics2D) g.create();
        fade.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, session.announcementTime())));
        Font heading = HEADING.deriveFont(24f);
        fade.setFont(heading);
        int boxWidth = Math.min(width - 100, fade.getFontMetrics().stringWidth(name) + 60);
        int x = (width - boxWidth) / 2, y = height / 4 + 12;
        panel(fade, x, y, boxWidth, 57);
        centered(fade, "ENTERING", width / 2, y + 19, TINY, MUTED);
        centered(fade, name, width / 2, y + 43, heading, GOLD);
        fade.dispose();
    }

    private void drawTitle(Graphics2D g, int width, int height) {
        shade(g, width, height, 188);
        int x = width / 2 - 320, y = height / 2 - 250;
        panel(g, x, y, 640, 500);
        centered(g, "A SMALL ADVENTURE", width / 2, y + 44, LABEL, MUTED);
        centered(g, "TRUE ADVENTURE", width / 2, y + 105, TITLE.deriveFont(43f), GOLD);
        centered(g, "Find four seals. Defeat the king.", width / 2, y + 138, BODY, CREAM);
        rule(g, x + 52, y + 164, 536, BRASS);

        cutPanel(g, x + 52, y + 196, 78, 91, new Color(48, 59, 41), GOLD);
        image(g, "/Player/boy_down_1.png", x + 60, y + 208, 62, 62);
        text(g, "What is your name?", x + 156, y + 214, HEADING.deriveFont(25f), CREAM);
        int fieldX = x + 156, fieldY = y + 231;
        cutPanel(g, fieldX, fieldY, 388, 48, new Color(16, 28, 21),
                session.nameError().isBlank() ? GOLD : RED);
        String input = session.nameInput();
        String visible = input.isEmpty() ? "Your name" : input;
        Font entry = fitFont(g, visible, new Font(Font.SANS_SERIF, Font.PLAIN, 23), 358, 18);
        text(g, visible, fieldX + 15, fieldY + 32, entry, input.isEmpty() ? MUTED : CREAM);
        if (!input.isEmpty()) {
            g.setFont(entry);
            int caretX = Math.min(fieldX + 370, fieldX + 17 + g.getFontMetrics().stringWidth(input));
            g.setColor(GOLD);
            g.fillRect(caretX, fieldY + 12, 2, 25);
        }
        text(g, session.nameError().isBlank() ? "Up to 16 characters." : session.nameError(),
                fieldX + 2, y + 303, BODY, session.nameError().isBlank() ? MUTED : new Color(249, 159, 132));
        centered(g, "Type your name, then press Enter.", width / 2, y + 349, BODY, CREAM);
        centered(g, "WASD Move    E Use    Space Attack    H Help", width / 2, y + 382, SMALL, MUTED);
        centered(g, "F11 Fullscreen", width / 2, y + 406, TINY, GOLD);
        Rectangle start = MenuLayout.startButton(width, height);
        button(g, start, session.hasStarted() ? "START AGAIN  ·  ENTER" : "START GAME  ·  ENTER");
    }

    private void drawPause(Graphics2D g, int width, int height) {
        shade(g, width, height, 185);
        Rectangle frame = MenuLayout.pauseFrame(width, height);
        panel(g, frame.x, frame.y, frame.width, frame.height);
        text(g, "Paused", frame.x + 28, frame.y + 44, TITLE.deriveFont(33f), GOLD);
        rule(g, frame.x + 28, frame.y + 60, 654, BRASS);

        cutPanel(g, frame.x + 28, frame.y + 80, 245, 285, PAPER, BRASS);
        cutPanel(g, frame.x + 111, frame.y + 99, 78, 87, new Color(194, 184, 143), BRASS);
        image(g, "/Player/boy_down_1.png", frame.x + 119, frame.y + 110, 62, 62);
        Font playerFont = fitFont(g, session.playerName(), HEADING, 215, 19);
        centered(g, fit(g, session.playerName(), playerFont, 215), frame.x + 150, frame.y + 221, playerFont, INK);
        centered(g, fit(g, session.level().definition().name(), new Font(Font.SERIF, Font.BOLD, 19), 217),
                frame.x + 150, frame.y + 253, new Font(Font.SERIF, Font.BOLD, 19), INK);
        rule(g, frame.x + 48, frame.y + 272, 205, new Color(163, 144, 98));
        centered(g, "Time  " + time(session.playTime()), frame.x + 150, frame.y + 301, LABEL, INK);
        centered(g, "The game is paused.", frame.x + 150, frame.y + 339, BODY, new Color(111, 104, 80));

        List<String> options = session.pauseOptions();
        List<Rectangle> rows = MenuLayout.pauseRows(width, height);
        for (int i = 0; i < rows.size() && i < options.size(); i++) {
            Rectangle row = rows.get(i);
            boolean selected = session.pauseSelection() == i;
            cutPanel(g, row.x, row.y, row.width, row.height,
                    selected ? new Color(63, 77, 49) : new Color(22, 35, 27),
                    selected ? GOLD : new Color(84, 91, 59));
            if (selected) {
                g.setColor(GOLD);
                g.fillPolygon(new int[]{row.x + 13, row.x + 20, row.x + 13},
                        new int[]{row.y + 15, row.y + 21, row.y + 27}, 3);
            }
            text(g, options.get(i), row.x + 34, row.y + 27, STRONG, selected ? GOLD : CREAM);
        }
        centered(g, "↑ / ↓ Choose    Enter Select    Esc Resume", width / 2, frame.y + 411, SMALL, MUTED);
    }

    private void drawControls(Graphics2D g, int width, int height) {
        shade(g, width, height, 190);
        Rectangle frame = MenuLayout.controlsFrame(width, height);
        panel(g, frame.x, frame.y, frame.width, frame.height);
        text(g, "How to play", frame.x + 30, frame.y + 48, TITLE.deriveFont(31f), GOLD);
        text(g, "Explore, find seals, and beat the king.", frame.x + 30, frame.y + 78, BODY, CREAM);
        rule(g, frame.x + 30, frame.y + 94, 700, BRASS);
        String[][] left = {{"WASD", "Move (or use arrows)"}, {"E", "Talk, open, or push"}, {"SPACE", "Attack or use your tool"},
                {"TAB", "Change tool"}, {"SHIFT", "Hold your shield"}, {"R", "Cast a fireball"}, {"Q", "Drink a potion"}};
        String[][] right = {{"I", "Open inventory"}, {"M", "Show map"}, {"ESC / P", "Pause or resume"},
                {"F11", "Fullscreen on / off"}, {"F", "Sound on / off"}, {"BACKSPACE", "Reset rocks"}, {"H", "Open help"}};
        drawControlColumn(g, left, frame.x + 32, frame.y + 118);
        drawControlColumn(g, right, frame.x + 397, frame.y + 118);
        centered(g, "Pick up tools to use them. The old man teaches fireball.", width / 2, frame.y + 463, BODY, MUTED);
        button(g, MenuLayout.controlsBackButton(width, height), "BACK  ·  ESC");
    }

    private static void drawControlColumn(Graphics2D g, String[][] controls, int x, int y) {
        for (int i = 0; i < controls.length; i++) {
            keycap(g, controls[i][0], x, y + i * 44);
            text(g, controls[i][1], x + 110, y + 15 + i * 44, BODY, CREAM);
        }
    }

    private void drawEnd(Graphics2D g, int width, int height, boolean victory) {
        shade(g, width, height, 190);
        int x = (width - 620) / 2, y = (height - 422) / 2;
        panel(g, x, y, 620, 422);
        centered(g, victory ? "THE KING IS DEFEATED" : "YOU CAN TRY AGAIN", width / 2, y + 47, TINY, MUTED);
        centered(g, victory ? "You found the treasure!" : "You were defeated", width / 2, y + 112, HEADING,
                victory ? GOLD : CREAM);
        rule(g, x + 64, y + 145, 492, BRASS);
        centered(g, fit(g, session.playerName(), TITLE.deriveFont(24f), 500), width / 2, y + 186,
                TITLE.deriveFont(24f), GOLD);
        centered(g, "Time " + time(session.playTime()) + "     Coins " + session.inventory().coins()
                + "     Seals " + session.relicCount() + " / " + session.relicGoal(), width / 2, y + 222, STRONG, CREAM);
        button(g, new Rectangle(width / 2 - 174, y + 279, 348, 48),
                victory ? "ENTER  ·  PLAY AGAIN" : "ENTER  ·  TRY AGAIN");
        centered(g, victory ? "Thanks for playing." : "Return to this area's entrance. Keep your items.", width / 2, y + 381, SMALL, MUTED);
    }

    private static void button(Graphics2D g, Rectangle bounds, String value) {
        cutPanel(g, bounds.x, bounds.y, bounds.width, bounds.height, new Color(50, 64, 42), GOLD);
        centered(g, value, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2 + 5, STRONG, GOLD);
    }

    private static void panel(Graphics2D g, int x, int y, int width, int height) {
        cutPanel(g, x, y, width, height, PANEL, BRASS);
    }

    private static void shade(Graphics2D g, int width, int height, int alpha) {
        g.setColor(new Color(9, 20, 17, alpha));
        g.fillRect(0, 0, width, height);
    }
}
