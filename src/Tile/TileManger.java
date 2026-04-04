package Tile;

import main.GamePanel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.util.Objects;

public class TileManger {
    GamePanel gp;
    public Tile[] tile;
    public int mapTileNum[][];

    public TileManger(GamePanel gp) {
        this.gp = gp;
        tile = new Tile[10];
        mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];
        GetTileImage();
        loadMap("/maps/world01.txt");
    }

    public void GetTileImage()
    {
        try{

            tile[0] = new Tile();
            tile[0].image = ImageIO.read(new File("res/tiles/grass01.png"));


            tile[1] = new Tile();
            tile[1].image = ImageIO.read(new File("res/tiles/wall.png"));
            tile[1].collision = true;

            tile[2] = new Tile();
            tile[2].image = ImageIO.read(new File("res/tiles/water00.png"));
            tile[2].collision = true;

            tile[3] = new Tile();
            tile[3].image = ImageIO.read(new File("res/tiles/earth.png"));

            tile[4] = new Tile();
            tile[4].image = ImageIO.read(new File("res/tiles/tree.png"));
            tile[4].collision = true;

            tile[5] = new Tile();
            tile[5].image = ImageIO.read(new File("res/tiles/road00.png"));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadMap(String filepath)
    {
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File("res" + filepath)));

            int col =0;
            int row=0;

            while (col < gp.maxWorldCol && row < gp.maxWorldRow) {
                String line = br.readLine();
                while (col < gp.maxWorldCol && row < gp.maxWorldRow) {
                    String[] numbers = line.split(" ");
                    int num = Integer.parseInt(numbers[col]);

                    mapTileNum[col][row] = num;
                    col++;
                }
                if (col == gp.maxWorldCol) {
                    col = 0;
                    row++;
                }
            }
            br.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void draw(Graphics g2) {

        int worldCol =0;
        int worldRow = 0;


        while(worldCol < gp.maxWorldCol && worldRow < gp.maxWorldRow)
        {
            int tileNum = mapTileNum[worldCol][worldRow];

            int worldX = worldCol * gp.tileSize;
            int worldY = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;

            g2.drawImage(tile[tileNum].image,screenX,screenY,gp.tileSize,gp.tileSize,null);
            worldCol++;

            if (worldCol == gp.maxWorldCol)
            {
                worldCol = 0;
                worldRow++;
            }
        }
    };
}
