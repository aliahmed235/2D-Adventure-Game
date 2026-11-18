package main;

import game.GameSession;
import game.Inventory;
import game.ItemType;

import java.awt.*;

import static main.UiTheme.*;

/** The satchel is a field journal: a read-only view of the current journey. */
public final class InventoryView {
    private static final int BOOK_WIDTH = 908;
    private static final int BOOK_HEIGHT = 650;
    private static final Color LEATHER = new Color(33, 47, 40);
    private static final Color STITCH = new Color(119, 116, 79);
    private static final Color FADED_INK = new Color(115, 111, 90);
    private static final Color LINE = new Color(106, 91, 60, 80);

    private InventoryView() { }

    public static void draw(Graphics2D graphics, GameSession session, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(new Color(7, 17, 17, 207));
        g.fillRect(0, 0, width, height);
        double scale = Math.min(1, Math.min((width - 32) / (double) BOOK_WIDTH,
                (height - 32) / (double) BOOK_HEIGHT));
        g.translate((width - BOOK_WIDTH * scale) / 2, (height - BOOK_HEIGHT * scale) / 2);
        g.scale(scale, scale);
        drawBook(g);
        drawWayfarer(g, session);
        drawSatchel(g, session);
        text(g, "THE GAME IS PAUSED", 27, 634, TINY, CREAM);
        int closeX = 724;
        closeX += keycap(g, "I", closeX, 617) + 5;
        text(g, "/", closeX, 633, SMALL, CREAM);
        closeX += 13;
        closeX += keycap(g, "ESC", closeX, 617) + 9;
        text(g, "Close", closeX, 633, SMALL, CREAM);
        g.dispose();
    }

    private static void drawBook(Graphics2D g) {
        cutPanel(g, 6, 9, BOOK_WIDTH, BOOK_HEIGHT, new Color(0, 0, 0, 100), new Color(0, 0, 0, 0));
        cutPanel(g, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, LEATHER, BRASS);
        cutPanel(g, 8, 8, BOOK_WIDTH - 16, BOOK_HEIGHT - 16, LEATHER, new Color(78, 91, 67));
        for (int x = 20; x < BOOK_WIDTH - 20; x += 11) {
            g.setColor(STITCH);
            g.drawLine(x, 12, x + 4, 12);
            g.drawLine(x, BOOK_HEIGHT - 12, x + 4, BOOK_HEIGHT - 12);
        }
        // The red cloth bookmark tucks under the right page.
        g.setColor(new Color(114, 52, 40));
        g.fillPolygon(new int[]{788, 812, 812, 800, 788}, new int[]{0, 0, 82, 71, 82}, 5);
        paper(g, 20, 20, 422, 590);
        paper(g, 466, 20, 422, 590);
        g.setPaint(new GradientPaint(423, 0, new Color(96, 70, 42, 0), 446, 0,
                new Color(71, 51, 32, 90)));
        g.fillRect(417, 20, 25, 590);
        g.setPaint(new GradientPaint(466, 0, new Color(71, 51, 32, 90), 489, 0,
                new Color(96, 70, 42, 0)));
        g.fillRect(466, 20, 25, 590);
        g.setColor(new Color(23, 36, 31));
        g.fillRect(444, 16, 20, 600);
        for (int y = 50; y < 600; y += 46) {
            g.setColor(STITCH);
            g.drawLine(449, y, 459, y + 7);
            g.drawLine(449, y + 7, 459, y + 14);
        }
        for (int x : new int[]{13, BOOK_WIDTH - 17}) {
            for (int y : new int[]{16, BOOK_HEIGHT - 21}) {
                g.setColor(BRASS);
                g.fillRect(x, y, 4, 4);
            }
        }
    }

    private static void drawWayfarer(Graphics2D g, GameSession session) {
        text(g, "YOUR INVENTORY", 48, 53, LABEL, FADED_INK);
        Font playerFont = fitFont(g, session.playerName(), TITLE, 364, 24);
        text(g, fit(g, session.playerName(), playerFont, 364), 48, 91, playerFont, INK);
        rule(g, 48, 106, 364, LINE);
        portrait(g, 49, 121);
        text(g, "YOUR CHARACTER", 175, 145, LABEL, MOSS);
        text(g, "Health", 175, 172, BODY, INK);
        for (int i = 0; i < (session.player().maxHealth() + 1) / 2; i++) {
            int hp = session.player().health() - i * 2;
            icon(g, hp >= 2 ? "heart_full" : hp == 1 ? "heart_half" : "heart_blank",
                    174 + i * 30, 184, 26);
        }
        text(g, session.player().health() + " / " + session.player().maxHealth(), 278, 204, LABEL, INK);
        text(g, "Your items stay with you.", 175, 229, SMALL, FADED_INK);
        text(g, "EQUIPMENT", 48, 265, LABEL, INK);
        text(g, "SPACE strike / TAB tool", 270, 265, SMALL.deriveFont(11f), FADED_INK);
        rule(g, 48, 275, 364, LINE);
        equipment(g, session, 48, 290, ItemType.SWORD, "sword_normal", "Woodland sword", "Strike facing enemies");
        equipment(g, session, 238, 290, ItemType.SHIELD, "shield_wood", "Wooden shield", "Hold SHIFT to guard");
        equipment(g, session, 48, 386, ItemType.BOOTS, "boots", "Traveller's boots", "Faster footsteps");
        equipment(g, session, 238, 386, ItemType.LANTERN, "lantern", "Sanctuary lantern", "Light in dark places");
        equipment(g, session, 48, 482, ItemType.AXE, "axe", "Woodcutter's axe", "Chop dry trees");
        equipment(g, session, 238, 482, ItemType.PICKAXE, "pickaxe", "Stone pickaxe", "Break cracked stone");
        text(g, "01", 48, 590, LABEL, FADED_INK);
        text(g, "READY TO EXPLORE", 181, 590, TINY, FADED_INK);
    }

    private static void portrait(Graphics2D g, int x, int y) {
        cutPanel(g, x, y, 108, 116, new Color(198, 188, 151), new Color(134, 124, 91));
        g.setColor(new Color(122, 136, 108, 80));
        g.setStroke(new BasicStroke(1));
        g.drawOval(x + 12, y + 15, 84, 84);
        g.drawOval(x + 21, y + 24, 66, 66);
        g.drawLine(x + 54, y + 8, x + 54, y + 105);
        g.drawLine(x + 8, y + 57, x + 100, y + 57);
        g.setColor(new Color(100, 111, 89, 90));
        g.drawLine(x + 20, y + 24, x + 87, y + 91);
        g.drawLine(x + 20, y + 91, x + 87, y + 24);
        text(g, "N", x + 50, y + 15, TINY, MOSS);
        image(g, "/Player/boy_down_1.png", x + 19, y + 25, 72, 72);
    }

    private static void equipment(Graphics2D g, GameSession session, int x, int y,
                                  ItemType item, String texture, String name, String effect) {
        boolean equipped = session.inventory().has(item);
        Color edge = equipped ? new Color(139, 122, 76) : new Color(153, 142, 109, 135);
        cutPanel(g, x, y, 174, 88,
                equipped ? new Color(205, 197, 160, 115) : new Color(192, 181, 145, 50), edge);
        // Corner marks make these look like small annotated specimens on a page.
        g.setColor(edge);
        g.drawLine(x + 8, y + 8, x + 17, y + 8);
        g.drawLine(x + 8, y + 8, x + 8, y + 17);
        g.drawLine(x + 157, y + 48, x + 166, y + 48);
        g.drawLine(x + 166, y + 39, x + 166, y + 48);
        Graphics2D sprite = (Graphics2D) g.create();
        if (!equipped) sprite.setComposite(AlphaComposite.SrcOver.derive(.27f));
        icon(sprite, texture, x + 68, y + 5, 36);
        sprite.dispose();
        if (equipped) {
            g.setColor(MOSS);
            g.fillRect(x + 147, y + 12, 14, 14);
            g.setColor(CREAM);
            g.setStroke(new BasicStroke(2));
            g.drawLine(x + 150, y + 18, x + 153, y + 21);
            g.drawLine(x + 153, y + 21, x + 158, y + 15);
            g.setStroke(new BasicStroke(1));
        }
        centered(g, name, x + 87, y + 58, BODY.deriveFont(Font.BOLD, 12), equipped ? INK : FADED_INK);
        centered(g, equipped ? effect : "Not found yet", x + 87, y + 77, SMALL.deriveFont(11f), FADED_INK);
    }

    private static void drawSatchel(Graphics2D g, GameSession session) {
        Inventory bag = session.inventory();
        text(g, "ITEMS & PROGRESS", 494, 53, LABEL, FADED_INK);
        text(g, "Your Bag", 494, 91, TITLE, INK);
        rule(g, 494, 106, 364, LINE);
        supply(g, 494, 123, "key", "KEYS", bag.keys());
        supply(g, 620, 123, "coin_bronze", "COINS", bag.coins());
        supply(g, 746, 123, "potion_red", "POTIONS", bag.potions());
        icon(g, "potion_red", 494, 250, 22);
        text(g, "Press Q while playing to heal two hearts.", 524, 266, SMALL, FADED_INK);
        Graphics2D spell = (Graphics2D) g.create();
        if (!session.spellKnown()) spell.setComposite(AlphaComposite.SrcOver.derive(.3f));
        image(spell, "/Projectile/fireball_right_1.png", 494, 277, 23, 23);
        spell.dispose();
        text(g, session.spellKnown() ? "Press R while playing to cast a fireball." : "Talk to the old man to learn fireball.",
                524, 294, SMALL, session.spellKnown() ? MOSS : FADED_INK);
        text(g, "SEALS FOUND", 494, 325, LABEL, INK);
        String count = session.relicCount() + " / " + session.relicGoal();
        g.setFont(LABEL);
        text(g, count, 858 - g.getFontMetrics().stringWidth(count), 325, LABEL, MOSS);
        rule(g, 494, 336, 364, LINE);
        if (session.relicGoal() == 4) {
            smallSeal(g, 535, 373, "I", "WHISPERWOOD", bag.flags().contains("forest-seal"));
            smallSeal(g, 629, 373, "II", "SUN TEMPLE", bag.flags().contains("sanctuary-seal"));
            smallSeal(g, 723, 373, "III", "LOST VAULT", bag.flags().contains("vault-seal"));
            smallSeal(g, 817, 373, "IV", "EMBER VILLAGE", bag.flags().contains("ember-seal"));
        } else if (session.relicGoal() == 2) {
            seal(g, 581, 364, "I", "WOODLAND", bag.flags().contains("forest-seal"));
            seal(g, 773, 364, "II", "SANCTUARY", bag.flags().contains("sanctuary-seal"));
        } else if (session.relicGoal() == 0) {
            centered(g, "An open road", 676, 371, HEADING, INK);
            centered(g, "No passage seals are needed on this journey.", 676, 402, SMALL, FADED_INK);
        } else {
            seal(g, 676, 364, Integer.toString(session.relicCount()),
                    "OF " + session.relicGoal() + " SEALS", session.relicCount() > 0);
        }
        // An area entry reads like a small note tucked into the journal.
        cutPanel(g, 494, 447, 364, 99, new Color(204, 194, 156, 120), new Color(133, 121, 85, 120));
        text(g, "CURRENT AREA", 509, 470, LABEL, FADED_INK);
        String location = session.level() == null ? "The beginning" : session.level().definition().name();
        Font locationFont = HEADING.deriveFont(23f);
        g.setFont(locationFont);
        while (g.getFontMetrics().stringWidth(location) > 330 && locationFont.getSize() > 16) {
            locationFont = locationFont.deriveFont(locationFont.getSize2D() - 1);
            g.setFont(locationFont);
        }
        while (g.getFontMetrics().stringWidth(location) > 330 && location.length() > 4)
            location = location.substring(0, location.length() - 4) + "...";
        text(g, location, 509, 499, locationFont, INK);
        text(g, "Time played", 509, 526, SMALL, FADED_INK);
        g.setFont(LABEL);
        String time = time(session.playTime());
        text(g, time, 842 - g.getFontMetrics().stringWidth(time), 526, LABEL, INK);
        text(g, "ITEMS ARE KEPT UNTIL YOU CLOSE THE GAME", 494, 590, TINY, FADED_INK);
        text(g, "02", 841, 590, LABEL, FADED_INK);
    }

    private static void supply(Graphics2D g, int x, int y, String texture, String name, int quantity) {
        g.setColor(LINE);
        g.drawLine(x + 4, y + 110, x + 108, y + 110);
        g.setColor(new Color(158, 142, 96, 90));
        g.drawOval(x + 28, y + 1, 58, 58);
        g.setColor(new Color(177, 163, 121, 55));
        g.fillOval(x + 29, y + 2, 56, 56);
        icon(g, texture, x + 38, y + 10, 40);
        String number = Integer.toString(quantity);
        Font numberFont = HEADING;
        g.setFont(numberFont);
        while (g.getFontMetrics().stringWidth(number) > 104 && numberFont.getSize() > 12) {
            numberFont = numberFont.deriveFont(numberFont.getSize2D() - 1);
            g.setFont(numberFont);
        }
        centered(g, number, x + 57, y + 86, numberFont, INK);
        centered(g, name, x + 57, y + 104, LABEL, FADED_INK);
    }

    private static void seal(Graphics2D g, int cx, int cy, String roman, String name, boolean earned) {
        g.setStroke(new BasicStroke(1));
        g.setColor(earned ? MOSS : new Color(152, 143, 110));
        g.drawOval(cx - 32, cy - 32, 64, 64);
        g.drawOval(cx - 27, cy - 27, 54, 54);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            int ax = cx + (int) (Math.cos(angle) * 30);
            int ay = cy + (int) (Math.sin(angle) * 30);
            int bx = cx + (int) (Math.cos(angle) * 34);
            int by = cy + (int) (Math.sin(angle) * 34);
            g.drawLine(ax, ay, bx, by);
        }
        if (earned) {
            g.setColor(MOSS);
            g.fillOval(cx - 23, cy - 23, 46, 46);
            centered(g, roman, cx, cy + 9, HEADING.deriveFont(26f), CREAM);
        } else {
            centered(g, roman, cx, cy + 9, HEADING.deriveFont(26f), new Color(155, 147, 114));
        }
        centered(g, name, cx, cy + 53, LABEL, earned ? INK : FADED_INK);
        centered(g, earned ? "RECOVERED" : "UNDISCOVERED", cx, cy + 70, TINY, earned ? MOSS : FADED_INK);
    }

    private static void smallSeal(Graphics2D g, int cx, int cy, String roman, String name, boolean earned) {
        g.setColor(earned ? MOSS : new Color(152, 143, 110));
        g.drawOval(cx - 24, cy - 24, 48, 48);
        g.drawOval(cx - 21, cy - 21, 42, 42);
        if (earned) g.fillOval(cx - 18, cy - 18, 36, 36);
        centered(g, roman, cx, cy + 7, HEADING.deriveFont(21f), earned ? CREAM : new Color(155, 147, 114));
        centered(g, name, cx, cy + 40, TINY.deriveFont(9f), earned ? INK : FADED_INK);
        centered(g, earned ? "FOUND" : "NOT FOUND", cx, cy + 55, TINY.deriveFont(9f), earned ? MOSS : FADED_INK);
    }
}
