package object;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class OBJ_Key extends SuperObject {
    public int worldX;
    public int worldY;
    public OBJ_Key() {
        name = "Key";
        try {
            image = ImageIO.read(new File("res/objects/key.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}