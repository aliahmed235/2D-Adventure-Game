package tests;

import main.GamePanel;
import javax.swing.*;

/** Optional headful smoke check: creates and closes a real off-screen Swing window. */
public final class NativeSmokeTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("True Adventure startup check");
            GamePanel panel = new GamePanel();
            frame.add(panel);
            frame.pack();
            frame.setLocation(-10000, -10000);
            frame.setVisible(true);
            panel.start();
            Timer stop = new Timer(1200, event -> {
                panel.close();
                frame.dispose();
                System.out.println("Native Swing startup and shutdown passed.");
            });
            stop.setRepeats(false);
            stop.start();
        });
    }
}
