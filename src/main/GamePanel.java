package main;

import game.GameSession;
import render.WorldRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/** Swing owns both updates and painting on the EDT: no shared-thread races. */
public final class GamePanel extends JPanel implements AutoCloseable {
    public static final int WIDTH = 1024;
    public static final int HEIGHT = 768;
    private final GameSession session;
    private final KeyHandler input = new KeyHandler();
    private final WorldRenderer renderer = new WorldRenderer();
    private final UI ui;
    private final Timer timer;
    private long lastTick;
    private boolean windowTransition;
    private boolean closed;
    private Runnable fullscreenAction = () -> { };
    private Runnable quitAction = () -> { };

    public GamePanel() {
        this(new GameSession());
    }

    public GamePanel(GameSession session) {
        this.session = java.util.Objects.requireNonNull(session);
        ui = new UI(session);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false); // Tab cycles tools inside the game.
        addKeyListener(input);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) {
                if (windowTransition) input.suppressHeldInput();
                else { input.clear(); session.pauseForFocusLoss(); }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                requestFocusInWindow();
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                Point point = logicalPoint(event);
                if (point == null) return;
                switch (session.state()) {
                    case TITLE -> {
                        if (MenuLayout.startButton(WIDTH, HEIGHT).contains(point)) {
                            // The next update commits any final typed character before starting.
                            input.trigger(KeyHandler.Action.START);
                        }
                    }
                    case PAUSED -> {
                        int option = MenuLayout.pauseOptionAt(point.x, point.y, WIDTH, HEIGHT);
                        if (option >= 0) {
                            input.suppressHeldInput();
                            session.selectPauseOption(option);
                            session.activatePauseOption();
                        }
                    }
                    case CONTROLS -> {
                        if (MenuLayout.controlsBackButton(WIDTH, HEIGHT).contains(point)) {
                            input.suppressHeldInput();
                            session.closeControls();
                        }
                    }
                    default -> { }
                }
                repaint();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                Point point = logicalPoint(event);
                boolean actionable = false;
                if (point != null) {
                    switch (session.state()) {
                        case TITLE -> actionable = MenuLayout.startButton(WIDTH, HEIGHT).contains(point);
                        case CONTROLS -> actionable = MenuLayout.controlsBackButton(WIDTH, HEIGHT).contains(point);
                        case PAUSED -> {
                            int option = MenuLayout.pauseOptionAt(point.x, point.y, WIDTH, HEIGHT);
                            actionable = option >= 0;
                            if (actionable) { session.selectPauseOption(option); repaint(); }
                        }
                        default -> { }
                    }
                }
                setCursor(Cursor.getPredefinedCursor(actionable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        timer = new Timer(1000 / 60, event -> tick());
        timer.setCoalesce(true);
    }

    public void start() {
        lastTick = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    public void setWindowActions(Runnable fullscreenAction, Runnable quitAction) {
        this.fullscreenAction = java.util.Objects.requireNonNull(fullscreenAction);
        this.quitAction = java.util.Objects.requireNonNull(quitAction);
    }

    public GameSession session() { return session; }

    public void setFullscreenMode(boolean value) { session.setFullscreen(value); }

    public void setWindowTransition(boolean value) {
        windowTransition = value;
        input.suppressHeldInput();
        lastTick = System.nanoTime();
    }

    private Point logicalPoint(MouseEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return null;
        return Viewport.fit(getWidth(), getHeight(), WIDTH, HEIGHT).toLogical(event.getX(), event.getY());
    }

    private void tick() {
        long now = System.nanoTime();
        // Ignore long OS stalls rather than moving an actor through an entire room.
        double dt = Math.min(.05, (now - lastTick) / 1_000_000_000.0);
        lastTick = now;
        session.update(dt, input);
        if (session.consumeFullscreenRequest()) fullscreenAction.run();
        if (session.consumeQuitRequest()) quitAction.run();
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        if (getWidth() <= 0 || getHeight() <= 0) { g.dispose(); return; }
        Viewport.fit(getWidth(), getHeight(), WIDTH, HEIGHT).apply(g);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        renderer.draw(g, session, WIDTH, HEIGHT);
        ui.draw(g, WIDTH, HEIGHT);
        g.dispose();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        timer.stop();
        input.clear();
        session.close();
    }
}
