package core;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Classpath assets work identically from the IDE and the packaged JAR. */
public final class Assets {
    private static final Map<String, BufferedImage> IMAGES = new HashMap<>();
    private Assets() { }
    public static BufferedImage image(String path) {
        return IMAGES.computeIfAbsent(path, Assets::readImage);
    }
    private static BufferedImage readImage(String path) {
        try (InputStream stream = Assets.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing image: " + path);
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IllegalArgumentException("Unsupported image: " + path);
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read image: " + path, exception);
        }
    }
}
