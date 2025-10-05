package main;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Color;

public class GamePanel extends JPanel {

    // SCREEN SETTINGS
    final int OrginalTileSize = 16;
    final int scale = 4; // 64

    final int tileSize = OrginalTileSize * scale; // 48x48

    final int macScreenCol = 16;
    final int maxScreenRow = 12;
    final int screenWidth = tileSize * macScreenCol; // 1024 px
    final int screenHeight = tileSize * maxScreenRow; // 768 px


    public GamePanel() {

        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
    }
}
