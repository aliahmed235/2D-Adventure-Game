package object;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class OBJ_Chest extends SuperObject {
    public int worldX;
    public int worldY;

    public OBJ_Chest() {
        name = "Chest";
        try {
            image = ImageIO.read(new File("res/objects/chest.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
