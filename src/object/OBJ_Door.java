package object;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class OBJ_Door extends SuperObject{
    public int worldX;
    public int worldY;
    public OBJ_Door() {
        name = "Door";
        try {
            image = ImageIO.read(new File("res/objects/door.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        collision = true;
    }
}
