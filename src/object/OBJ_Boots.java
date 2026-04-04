package object;

import javax.imageio.ImageIO;
import java.io.File;

public class OBJ_Boots extends  SuperObject {
    public OBJ_Boots() {
        name = "Boots";
        try {
            image = ImageIO.read(new File("res/objects/boots.png"));
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}