package entity;

import main.GamePanel;
import main.KeyHandler;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Player extends Entity {

    private final GamePanel gp;
    private final KeyHandler keyH;

    public Player(GamePanel gp , KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        setDefaultValues();
        getPlayerImage();
    }

    public void setDefaultValues() {
        x = 100;
        y = 100;
        speed = 4;
        direction = "down";
        spriteCounter = 0;
        spriteNum = 1;
    }

    public void getPlayerImage() {
        try {
            up1    = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_up_1.png"));
            up2    = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_up_2.png"));
            down1  = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_down_1.png"));
            down2  = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_down_2.png"));
            right1 = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_right_1.png"));
            right2 = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_right_2.png"));
            left1  = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_left_1.png"));
            left2  = ImageIO.read(new File("C:/Users/aliah/OneDrive/Desktop/2D_GAME/res/Player/boy_left_2.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update() {
        boolean moving = false;

        if (keyH.upPressed) {
            direction = "up";
            y -= speed;
            moving = true;
        } else if (keyH.downPressed) {
            direction = "down";
            y += speed;
            moving = true;
        } else if (keyH.leftPressed) {
            direction = "left";
            x -= speed;
            moving = true;
        } else if (keyH.rightPressed) {
            direction = "right";
            x += speed;
            moving = true;
        }

        if (moving) {
            spriteCounter++;
            if (spriteCounter > 12) {
                spriteNum = (spriteNum == 1) ? 2 : 1;
                spriteCounter = 0;
            }
        } else {
            spriteNum = 1;
            spriteCounter = 0;
        }
    }

    public void draw(Graphics2D g2) {
        BufferedImage image = null;

        switch (direction) {
            case "up":
                image = (spriteNum == 1) ? up1 : up2;
                break;
            case "down":
                image = (spriteNum == 1) ? down1 : down2;
                break;
            case "left":
                image = (spriteNum == 1) ? left1 : left2;
                break;
            case "right":
                image = (spriteNum == 1) ? right1 : right2;
                break;
            default:
                image = down1;
        }

        if (image != null) {
            g2.drawImage(image, x, y, gp.tileSize, gp.tileSize, null);
        }
    }
}
