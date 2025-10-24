package Tile;

import main.GamePanel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Objects;
import java.io.InputStream;

public class TileManger {
    GamePanel gp;
    Tile[] tile;
    int mapTileNum[][];

    public TileManger(GamePanel gp) {
        this.gp = gp;
        tile = new Tile[10];
        mapTileNum = new int[gp.maxScreenCol][gp.maxScreenRow];
        GetTileImage();
        loadMap("/maps/map001.txt");
    }

    public void GetTileImage()
    {
        try{

            tile[0] = new Tile();
            tile[0].image = ImageIO.read(getClass().getResourceAsStream("/tiles/grass00.png"));


            tile[1] = new Tile();
            tile[1].image =ImageIO.read(getClass().getResourceAsStream("/tiles/wall.png"));

            tile[2] = new Tile();
            tile[2].image = ImageIO.read(getClass().getResourceAsStream("/tiles/water00.png"));
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadMap(String filepath)
    {
        try {
            InputStream is = getClass().getResourceAsStream(filepath);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            int col =0;
            int row=0;

            while (col < gp.maxScreenCol && row < gp.maxScreenRow) {
                String line = br.readLine();
                while (col < gp.maxScreenCol && row < gp.maxScreenRow) {
                    String[] numbers = line.split(" ");
                    int num = Integer.parseInt(numbers[col]);

                    mapTileNum[col][row] = num;
                    col++;
                }
                if (col == gp.maxScreenCol) {
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

        int col =0;
        int row = 0;
        int x =0;
        int y =0;

        while(col < gp.maxScreenCol && row < gp.maxScreenRow)
        {
            int tileNum = mapTileNum[col][row];
            g2.drawImage(tile[tileNum].image,x,y,gp.tileSize,gp.tileSize,null);
            col++;
            x +=gp.tileSize;

            if (col == gp.maxScreenCol)
            {
                col = 0;
                x =0;
                row++;
                y +=gp.tileSize;
            }
        }
    };
}
