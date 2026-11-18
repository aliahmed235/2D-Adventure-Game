package render;

import core.Assets;
import core.Camera;
import entity.Enemy;
import entity.Projectile;
import game.GameSession;
import game.ItemType;
import world.Level;
import world.ObjectSpawn;
import world.ObjectType;
import world.WorldObject;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The scene is a read-only view of the session, with culling and depth sorting. */
public final class WorldRenderer {
    private static final int TILE = GameSession.TILE_SIZE;
    private final Camera camera = new Camera();
    private record Layer(double depth, Runnable draw) { }

    public void draw(Graphics2D g, GameSession session, int width, int height) {
        Level level = session.level();
        camera.follow(session.player().x(), session.player().y(), level.width() * TILE,
                level.height() * TILE, width, height, TILE);
        g.setColor(new Color(16, 24, 28));
        g.fillRect(0, 0, width, height);
        int left = Math.max(0, (int) camera.x() / TILE);
        int top = Math.max(0, (int) camera.y() / TILE);
        int right = Math.min(level.width() - 1, (int) (camera.x() + width) / TILE);
        int bottom = Math.min(level.height() - 1, (int) (camera.y() + height) / TILE);
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                var tile = session.tiles().definition(level.tileAt(col, row));
                g.drawImage(Assets.image(tile.texture()), camera.screenX(col * TILE),
                        camera.screenY(row * TILE), TILE, TILE, null);
            }
        }
        List<Layer> layers = new ArrayList<>();
        for (WorldObject object : level.objects()) {
            if (object.removed()) continue;
            int x = camera.screenX(object.col() * TILE);
            int y = camera.screenY(object.row() * TILE);
            if (x < -TILE || y < -TILE || x > width + TILE || y > height + TILE) continue;
            layers.add(new Layer(object.row() * TILE + (object.spawn().type() == ObjectType.PLATE ? 2 : 52),
                    () -> drawObject(g, object, x, y, session.playTime(), session)));
        }
        for (Enemy enemy : session.enemies()) {
            if (!enemy.alive()) continue;
            layers.add(new Layer(enemy.y() + 56,
                    () -> enemy.draw(g, camera.screenX(enemy.x()), camera.screenY(enemy.y()), TILE)));
        }
        for (Projectile projectile : session.projectiles()) {
            if (!projectile.alive()) continue;
            layers.add(new Layer(projectile.y() + 32,
                    () -> projectile.draw(g, camera.screenX(projectile.x()), camera.screenY(projectile.y()), TILE)));
        }
        layers.add(new Layer(session.player().y() + 58,
                () -> session.player().draw(g, camera.screenX(session.player().x()),
                        camera.screenY(session.player().y()), TILE,
                        session.inventory().has(ItemType.SWORD) || session.inventory().has(ItemType.AXE)
                                || session.inventory().has(ItemType.PICKAXE), session.inventory().has(ItemType.SHIELD))));
        layers.sort(Comparator.comparingDouble(Layer::depth));
        layers.forEach(layer -> layer.draw().run());
        drawAtmosphere(g, session, width, height);
    }

    private void drawObject(Graphics2D original, WorldObject object, int x, int y,
                            double time, GameSession session) {
        Graphics2D g = (Graphics2D) original.create();
        ObjectSpawn spawn = object.spawn();
        boolean pickup = switch (spawn.type()) {
            case KEY, BOOTS, COIN, POTION, SWORD, SHIELD, LANTERN, HEART, AXE, PICKAXE -> true;
            default -> false;
        };
        int bob = pickup ? (int) (Math.sin(time * 3 + spawn.col()) * 3) : 0;
        g.setColor(new Color(0, 0, 0, 65));
        g.fillOval(x + 12, y + 47, 40, 11);
        if (spawn.type() == ObjectType.PORTAL) {
            boolean unlocked = spawn.requires().isBlank()
                    || java.util.Arrays.stream(spawn.requires().split("\\+"))
                    .allMatch(session.inventory().flags()::contains);
            Color light = unlocked ? new Color(87, 223, 244) : new Color(123, 138, 162);
            g.setColor(new Color(light.getRed(), light.getGreen(), light.getBlue(), 38));
            g.fillOval(x - 9, y - 10, 82, 82);
            g.setStroke(new BasicStroke(4));
            g.setColor(light);
            g.drawOval(x + 5, y + 2, 54, 57);
            g.setStroke(new BasicStroke(2));
            g.drawArc(x + 13, y + 10, 38, 41, (int) (time * 70) % 360, 245);
            g.drawImage(Assets.image("/objects/blueheart.png"), x + 19, y + 19, 26, 26, null);
        } else if (spawn.type() == ObjectType.NPC || spawn.type() == ObjectType.MERCHANT) {
            drawNpc(g, object, x, y, time, session);
        } else if (spawn.type() == ObjectType.PLATE) {
            g.drawImage(Assets.image("/Tiles_Interactive/metalplate.png"), x, y, TILE, TILE, null);
            if (object.active()) {
                g.setColor(new Color(231, 199, 115, 80));
                g.fillRect(x + 9, y + 9, TILE - 18, TILE - 18);
                g.setColor(new Color(242, 222, 156));
                g.drawRect(x + 8, y + 8, TILE - 17, TILE - 17);
            }
        } else if (spawn.type() == ObjectType.SWITCH) {
            g.setColor(new Color(32, 43, 56));
            g.fillOval(x + 5, y + 40, 54, 18);
            if (object.active()) {
                g.setColor(new Color(255, 210, 105, 65));
                g.fillOval(x - 2, y - 5, 68, 68);
            }
            g.drawImage(Assets.image(object.active() ? "/objects/manacrystal_full.png"
                    : "/objects/manacrystal_blank.png"), x + 10, y + 4, 44, 44, null);
            String label = spawn.id().replace("beacon-", "").toUpperCase(java.util.Locale.ROOT);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(object.active() ? new Color(255, 227, 154) : Color.WHITE);
            int textWidth = g.getFontMetrics().stringWidth(label);
            g.drawString(label, x + 32 - textWidth / 2, y - 4);
        } else if (spawn.type() == ObjectType.SIGN) {
            g.drawImage(Assets.image("/tiles/table01.png"), x + 8, y + 10, 48, 48, null);
            g.setColor(new Color(255, 227, 162));
            g.setFont(new Font("Serif", Font.BOLD, 25));
            g.drawString("i", x + 28, y + 30);
        } else {
            String texture = texture(spawn.type(), object.active());
            int inset = pickup ? 10 : 0;
            if (spawn.type() == ObjectType.CHEST && !object.active()) {
                g.setColor(new Color(255, 207, 87, 34));
                g.fillOval(x - 3, y - 3, 70, 70);
            }
            g.drawImage(Assets.image(texture), x + inset, y + inset + bob,
                    TILE - inset * 2, TILE - inset * 2, null);
        }
        g.dispose();
    }

    private void drawNpc(Graphics2D g, WorldObject object, int x, int y, double time, GameSession session) {
        boolean merchant = object.spawn().type() == ObjectType.MERCHANT;
        String direction = "down";
        if (!merchant) {
            double dx = session.player().x() - object.col() * TILE;
            double dy = session.player().y() - object.row() * TILE;
            direction = Math.abs(dx) > Math.abs(dy) ? dx < 0 ? "left" : "right" : dy < 0 ? "up" : "down";
        }
        int frame = ((int) (time * 2) & 1) + 1;
        String character = merchant ? "merchant" : "oldman";
        g.drawImage(Assets.image("/NPC/" + character + "_" + direction + "_" + frame + ".png"), x, y, TILE, TILE, null);
        String label = merchant ? "MERCHANT" : "OLD MAN";
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        int width = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(19, 28, 23, 205));
        g.fillRect(x + 32 - width / 2 - 4, y - 17, width + 8, 15);
        g.setColor(new Color(245, 224, 174));
        g.drawString(label, x + 32 - width / 2, y - 6);
    }

    public static String texture(ObjectType type, boolean active) {
        return switch (type) {
            case KEY -> "/objects/key.png";
            case DOOR -> "/objects/door.png";
            case GATE -> "/objects/door_iron.png";
            case BOOTS -> "/objects/boots.png";
            case CHEST -> active ? "/objects/chest_opened.png" : "/objects/chest.png";
            case COIN -> "/objects/coin_bronze.png";
            case POTION -> "/objects/potion_red.png";
            case SWORD -> "/objects/sword_normal.png";
            case AXE -> "/objects/axe.png";
            case PICKAXE -> "/objects/pickaxe.png";
            case SHIELD -> "/objects/shield_wood.png";
            case LANTERN -> "/objects/lantern.png";
            case HEART -> "/objects/heart_full.png";
            case PORTAL -> "/objects/blueheart.png";
            case SWITCH -> "/objects/manacrystal_full.png";
            case SIGN -> "/tiles/table01.png";
            case DRY_TREE -> active ? "/Tiles_Interactive/trunk.png" : "/Tiles_Interactive/drytree.png";
            case CRACKED_WALL -> "/Tiles_Interactive/destructiblewall.png";
            case PLATE -> "/Tiles_Interactive/metalplate.png";
            case ROCK -> "/NPC/bigrock.png";
            case NPC -> "/NPC/oldman_down_1.png";
            case MERCHANT -> "/NPC/merchant_down_1.png";
        };
    }

    private void drawAtmosphere(Graphics2D g, GameSession session, int width, int height) {
        if (!session.level().definition().dark()) return;
        float radius = session.inventory().has(ItemType.LANTERN) ? 410 : 180;
        Point2D center = new Point2D.Float(camera.screenX(session.player().x()) + TILE / 2f,
                camera.screenY(session.player().y()) + TILE / 2f);
        Paint saved = g.getPaint();
        g.setPaint(new RadialGradientPaint(center, radius, new float[]{0, .55f, 1},
                new Color[]{new Color(8, 15, 30, 0), new Color(8, 15, 30, 35), new Color(8, 15, 30, 205)}));
        g.fillRect(0, 0, width, height);
        g.setPaint(saved);
    }
}
