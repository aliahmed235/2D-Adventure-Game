package main;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;

/** Borderless fullscreen uses the current monitor without changing its display mode. */
public final class WindowMode {
    private final JFrame window;
    private final GamePanel panel;
    private Rectangle windowedBounds;
    private boolean windowedUndecorated;
    private boolean windowedResizable;
    private int windowedState;
    private boolean fullscreen;
    private boolean transitioning;

    public WindowMode(JFrame window, GamePanel panel) {
        this.window = java.util.Objects.requireNonNull(window);
        this.panel = java.util.Objects.requireNonNull(panel);
    }

    public boolean fullscreen() { return fullscreen; }
    public boolean transitioning() { return transitioning; }

    public void toggle() { setFullscreen(!fullscreen); }

    public void setFullscreen(boolean value) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Window mode changes must run on the Swing event thread");
        }
        if (transitioning || value == fullscreen) return;
        transitioning = true;
        panel.setWindowTransition(true);
        if (value) {
            windowedBounds = new Rectangle(window.getBounds());
            windowedUndecorated = window.isUndecorated();
            windowedResizable = window.isResizable();
            windowedState = window.getExtendedState();
        }
        Rectangle bounds = value ? new Rectangle(window.getGraphicsConfiguration().getBounds())
                : new Rectangle(windowedBounds);
        try {
            window.dispose();
            window.setExtendedState(JFrame.NORMAL);
            window.setUndecorated(value || windowedUndecorated);
            window.setResizable(!value && windowedResizable);
            window.setBounds(bounds);
            if (!value) window.setExtendedState(windowedState);
            window.setVisible(true);
            fullscreen = value;
            panel.setFullscreenMode(fullscreen);
        } finally {
            // Native redecoration emits focus events; only a later real focus loss pauses play.
            SwingUtilities.invokeLater(() -> {
                panel.setWindowTransition(false);
                transitioning = false;
                panel.requestFocusInWindow();
                panel.repaint();
            });
        }
    }
}
