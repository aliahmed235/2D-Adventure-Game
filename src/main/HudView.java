package main;

import entity.Enemy;
import game.GameSession;
import game.Inventory;
import game.ItemType;
import world.Level;
import world.WorldObject;
import java.awt.*;

import static main.UiTheme.*;

/** Compact field instruments leave the center of the world clear. */
public final class HudView {
    private static final Color LEATHER = new Color(25, 38, 30, 245);
    private static final Font OBJECTIVE = BODY.deriveFont(13f);
    private HudView() { }

    public static void draw(Graphics2D g, GameSession session, int width, int height) {
        Inventory bag = session.inventory();
        Color healthEdge = session.player().health() <= 2 ? new Color(183, 99, 76) : BRASS;
        cutPanel(g, 16, 16, 252, 78, LEATHER, healthEdge);
        cutPanel(g, 23, 23, 48, 62, new Color(48, 59, 41), GOLD);
        g.setColor(new Color(125, 141, 94, 55));
        g.drawOval(27, 28, 40, 40);
        image(g, "/Player/boy_down_1.png", 27, 31, 40, 40);
        text(g, fit(g, session.playerName(), LABEL, 125), 82, 35, LABEL, GOLD);
        text(g, time(session.playTime()), 222, 35, TINY, MUTED);
        for (int i = 0; i < (session.player().maxHealth() + 1) / 2; i++) {
            int remaining = session.player().health() - i * 2;
            int x = 83 + i * 28;
            g.setColor(new Color(12, 24, 18));
            g.fillRect(x - 1, 42, 24, 24);
            icon(g, remaining >= 2 ? "heart_full" : remaining == 1 ? "heart_half" : "heart_blank", x, 43, 22);
        }
        rule(g, 82, 69, 173, new Color(102, 103, 65));
        counter(g, "key", bag.keys(), 83, 74);
        counter(g, "coin_bronze", bag.coins(), 141, 74);
        icon(g, "blueheart", 204, 74, 14);
        text(g, session.relicCount() + "/" + session.relicGoal(), 223, 85, TINY, GOLD);

        int right = width - 268;
        cutPanel(g, right, 16, 252, noteHeight(g, session), PAPER, BRASS);
        text(g, fit(g, session.level().definition().name(), new Font(Font.SERIF, Font.BOLD, 18), 205),
                right + 12, 41, new Font(Font.SERIF, Font.BOLD, 18), INK);
        compass(g, right + 231, 33, 8, BRASS);
        rule(g, right + 12, 50, 228, new Color(159, 140, 93));
        wrap(g, session.objective(), right + 12, 70, 228, 17, OBJECTIVE, INK);
        drawBelt(g, session, width, height);
        drawBoss(g, session, width, height);
    }

    private static int noteHeight(Graphics2D g, GameSession session) {
        g.setFont(OBJECTIVE);
        return 52 + lines(g, session.objective(), 228).size() * 17;
    }

    private static void counter(Graphics2D g, String texture, int quantity, int x, int y) {
        icon(g, texture, x, y, 14);
        String count = quantity > 9999 ? quantity / 1000 + "k" : quantity < 10 ? "0" + quantity : Integer.toString(quantity);
        text(g, count, x + 18, y + 11, TINY, CREAM);
    }

    private static void drawBelt(Graphics2D g, GameSession session, int width, int height) {
        int x = width / 2 - 180, y = height - 70;
        cutPanel(g, x, y, 360, 58, LEATHER, BRASS);
        for (int offset : new int[]{102, 168, 234, 297}) {
            g.setColor(new Color(113, 112, 71));
            g.drawLine(x + offset, y + 7, x + offset, y + 49);
        }
        String weaponTexture = session.player().weapon().texture();
        boolean owned = session.inventory().has(ItemType.valueOf(session.player().weapon().name()));
        Graphics2D weapon = (Graphics2D) g.create();
        if (!owned) weapon.setComposite(AlphaComposite.SrcOver.derive(.35f));
        icon(weapon, weaponTexture, x + 10, y + 5, 24);
        weapon.dispose();
        text(g, session.player().weapon().name(), x + 38, y + 22, TINY, owned ? CREAM : MUTED);
        keycap(g, "SPACE", x + 7, y + 32);
        keycap(g, "TAB", x + 60, y + 32);
        Graphics2D spell = (Graphics2D) g.create();
        if (!session.spellKnown() || session.spellCooldown() > 0) spell.setComposite(AlphaComposite.SrcOver.derive(.32f));
        image(spell, "/Projectile/fireball_right_1.png", x + 122, y + 4, 24, 24);
        spell.dispose();
        keycap(g, "R", x + 110, y + 32);
        if (!session.spellKnown()) {
            g.setColor(MUTED);
            g.drawArc(x + 143, y + 36, 6, 7, 0, 180);
            g.fillRect(x + 142, y + 40, 9, 7);
        }
        else if (session.spellCooldown() > 0)
            text(g, String.format(java.util.Locale.ROOT, "%.1f", session.spellCooldown()), x + 138, y + 47, TINY, GOLD);
        Graphics2D potion = (Graphics2D) g.create();
        if (session.inventory().potions() == 0) potion.setComposite(AlphaComposite.SrcOver.derive(.35f));
        icon(potion, "potion_red", x + 180, y + 4, 24);
        potion.dispose();
        text(g, fit(g, Integer.toString(session.inventory().potions()), LABEL, 23), x + 208, y + 23, LABEL,
                session.inventory().potions() == 0 ? MUTED : CREAM);
        keycap(g, "Q", x + 174, y + 32);
        text(g, "Heal", x + 201, y + 47, SMALL, MUTED);
        g.setColor(PAPER_DARK);
        g.fillRect(x + 254, y + 8, 22, 16);
        g.setColor(BRASS);
        g.drawRect(x + 252, y + 6, 26, 20);
        g.drawLine(x + 265, y + 7, x + 265, y + 25);
        keycap(g, "I", x + 240, y + 32);
        text(g, "Bag", x + 267, y + 47, SMALL, CREAM);
        compass(g, x + 330, y + 17, 10, GOLD);
        keycap(g, "M", x + 303, y + 32);
        text(g, "Map", x + 330, y + 47, SMALL, CREAM);

        g.setColor(new Color(18, 28, 22, 215));
        g.fillRect(16, height - 31, 142, 21);
        text(g, "Esc Menu  H Help", 24, height - 17, TINY, MUTED);
    }

    public static boolean bossVisible(GameSession session) { return nearbyBoss(session) != null; }

    private static Enemy nearbyBoss(GameSession session) {
        return session.enemies().stream().filter(enemy -> enemy.alive()
                && enemy.kind() == entity.EnemyKind.SKELETON_LORD
                && Math.hypot(enemy.x() - session.player().x(), enemy.y() - session.player().y()) <= 8 * 64)
                .findFirst().orElse(null);
    }

    private static void drawBoss(Graphics2D g, GameSession session, int width, int height) {
        Enemy boss = nearbyBoss(session);
        if (boss == null) return;
        int x = width / 2 - 180, y = height - 128;
        cutPanel(g, x, y, 360, 40, LEATHER, boss.phaseTwo() ? new Color(177, 82, 61) : BRASS);
        text(g, fit(g, boss.displayName(), LABEL, 235), x + 12, y + 17, LABEL, GOLD);
        text(g, boss.phaseTwo() ? "Phase 2" : "Phase 1", x + 291, y + 17, TINY, MUTED);
        g.setColor(new Color(13, 22, 18));
        g.fillRect(x + 12, y + 25, 336, 7);
        g.setColor(boss.phaseTwo() ? new Color(214, 99, 62) : new Color(151, 65, 56));
        g.fillRect(x + 13, y + 26, Math.max(0, 334 * boss.health() / boss.maxHealth()), 5);
    }

    public static void drawMap(Graphics2D g, GameSession session, int width) {
        Level level = session.level();
        double scale = Math.min(2.5, 125.0 / Math.max(level.width(), level.height()));
        int mapWidth = (int) Math.ceil(level.width() * scale);
        int mapHeight = (int) Math.ceil(level.height() * scale);
        int panelWidth = Math.max(149, mapWidth + 24);
        int panelX = width - panelWidth - 16;
        int x = panelX + (panelWidth - mapWidth) / 2, y = 16 + noteHeight(g, session) + 38;
        cutPanel(g, panelX, y - 23, panelWidth, mapHeight + 45, LEATHER, BRASS);
        text(g, "MAP  [M]", panelX + 12, y - 7, TINY, GOLD);
        for (int row = 0; row < level.height(); row++) {
            for (int col = 0; col < level.width(); col++) {
                int tile = level.tileAt(col, row);
                g.setColor(tile == 2 ? new Color(52, 91, 104)
                        : session.tiles().solid(tile) ? new Color(48, 61, 43) : new Color(172, 170, 121));
                g.fillRect(x + (int) (col * scale), y + (int) (row * scale), (int) Math.ceil(scale), (int) Math.ceil(scale));
            }
        }
        for (WorldObject object : level.objects()) {
            if (object.removed()) continue;
            Color color = switch (object.spawn().type()) {
                case PORTAL -> new Color(147, 204, 137);
                case CHEST -> GOLD;
                case SWITCH -> object.active() ? new Color(147, 204, 137) : new Color(166, 127, 183);
                default -> null;
            };
            if (color == null) continue;
            g.setColor(color);
            g.fillRect(x + (int) ((object.col() + .5) * scale) - 2,
                    y + (int) ((object.row() + .5) * scale) - 2, 4, 4);
        }
        for (Enemy enemy : session.enemies()) {
            if (!enemy.alive()) continue;
            g.setColor(new Color(213, 102, 107));
            g.fillRect(x + (int) ((enemy.x() + 32) / 64 * scale) - 1,
                    y + (int) ((enemy.y() + 32) / 64 * scale) - 1, 3, 3);
        }
        g.setColor(CREAM);
        g.fillOval(x + (int) ((session.player().x() + 32) / 64 * scale) - 3,
                y + (int) ((session.player().y() + 32) / 64 * scale) - 3, 6, 6);
        text(g, "Gold: seal  Green: exit", panelX + 10, y + mapHeight + 15,
                new Font(Font.SANS_SERIF, Font.PLAIN, 10), MUTED);
    }

    private static void compass(Graphics2D g, int x, int y, int radius, Color color) {
        g.setColor(color);
        g.drawOval(x - radius + 3, y - radius + 3, radius * 2 - 6, radius * 2 - 6);
        g.fillPolygon(new int[]{x, x + 3, x, x - 3}, new int[]{y - radius, y, y + radius, y}, 4);
        g.drawLine(x - radius, y, x + radius, y);
    }
}
