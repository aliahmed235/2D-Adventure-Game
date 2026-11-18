package tests;

import core.AudioManager;
import game.GameSession;
import game.GameState;
import main.GamePanel;
import main.WindowMode;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Rectangle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Optional headful check. It briefly enters fullscreen, then restores and closes the window. */
public final class NativeWindowTest {
    private NativeWindowTest() { }

    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<JFrame> frameRef = new AtomicReference<>();
        AtomicReference<GamePanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("The Sunken Seal | Fullscreen check");
            GameSession session = new GameSession(new AudioManager(false));
            GamePanel panel = new GamePanel(session);
            frameRef.set(frame);
            panelRef.set(panel);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setResizable(false);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            Rectangle original = frame.getBounds();
            frame.setVisible(true);
            panel.start();
            WindowMode mode = new WindowMode(frame, panel);
            Runnable close = () -> { panel.close(); frame.dispose(); done.countDown(); };
            after(150, () -> {
                session.startNewRun();
                mode.toggle();
                after(250, () -> {
                    check(mode.fullscreen() && frame.isUndecorated() && session.fullscreen(),
                            "Fullscreen did not update the window and session together");
                    check(frame.getBounds().equals(frame.getGraphicsConfiguration().getBounds()),
                            "Fullscreen must fill the selected monitor");
                    check(session.state() == GameState.PLAYING, "Fullscreen incorrectly paused the game");
                    mode.toggle();
                    after(250, () -> {
                        check(!mode.fullscreen() && !frame.isUndecorated() && !session.fullscreen(),
                                "Window decorations or fullscreen state were not restored");
                        check(frame.getBounds().equals(original), "Windowed bounds were not restored");
                        check(session.state() == GameState.PLAYING, "Restoring the window incorrectly paused the game");
                        double before = session.playTime();
                        after(180, () -> {
                            check(session.playTime() > before, "Window recreation stopped the game timer");
                            close.run();
                        }, failure, close);
                    }, failure, close);
                }, failure, close);
            }, failure, close);
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            SwingUtilities.invokeAndWait(() -> {
                if (panelRef.get() != null) panelRef.get().close();
                if (frameRef.get() != null) frameRef.get().dispose();
            });
            throw new AssertionError("Fullscreen check did not finish");
        }
        if (failure.get() != null) throw new AssertionError("Native fullscreen check failed", failure.get());
        System.out.println("Native fullscreen entry, restore, focus, timer, and shutdown passed.");
    }

    private static void after(int delay, Runnable action, AtomicReference<Throwable> failure, Runnable close) {
        Timer timer = new Timer(delay, event -> {
            try { action.run(); }
            catch (Throwable exception) { failure.set(exception); close.run(); }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
