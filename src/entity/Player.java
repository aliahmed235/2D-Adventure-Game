package entity;

import main.GamePanel;
import main.KeyHandler;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Player extends Entity {

    private final GamePanel gp;
    private final KeyHandler keyH;

    public final int screenX;
    public final int screenY;

    public Player(GamePanel gp , KeyHandler keyH) {
        this.gp = gp;
        this.keyH = keyH;

        screenX = gp.screenWidth/2 - (gp.tileSize/2);
        screenY = gp.screenHeight/2 - (gp.tileSize/2);

        solidArea = new Rectangle();
        solidArea.x = 8;
        solidArea.y = 16;
        solidArea.width = 32;
        solidArea.height = 32;

        setDefaultValues();
        getPlayerImage();
    }

    public void setDefaultValues() {
        worldX = gp.tileSize*23;
        worldY=  gp.tileSize*21;
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
            moving = true;
        } else if (keyH.downPressed) {
            direction = "down";
            moving = true;
        } else if (keyH.leftPressed) {
            direction = "left";
            moving = true;
        } else if (keyH.rightPressed) {
            direction = "right";
            moving = true;
        }

        //Check Tile Collision
        collisionOn = false;
        gp.cChecker.checkTile(this);

        //if a key is pressed AND collision is false, THEN move
        if (moving == true && collisionOn == false) // <--- THIS IS THE FIX
        {
            switch (direction) {
                case "up": worldY -= speed;break;
                case "down": worldY += speed;break;
                case "left": worldX -= speed;break;
                case "right": worldX += speed;break;
            }
        }

        // Animation code (this part was already correct)
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
            g2.drawImage(image, screenX, screenY, gp.tileSize, gp.tileSize, null);
        }
    }
}
