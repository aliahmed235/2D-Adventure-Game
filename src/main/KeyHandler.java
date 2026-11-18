package main;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Set;

/** Held input is distinct from one-time actions; key repeat cannot reopen menus. */
public final class KeyHandler implements KeyListener {
    public enum Action { UP, DOWN, LEFT, RIGHT, START, INTERACT, ATTACK, POTION,
        PAUSE, INVENTORY, MAP, MUTE, CYCLE_TOOL, GUARD, CAST, RESET_ROCKS, FULLSCREEN, HELP }
    private final Set<Integer> held = new HashSet<>();
    private final Set<Integer> suppressed = new HashSet<>();
    private final EnumSet<Action> pressed = EnumSet.noneOf(Action.class);
    private final StringBuilder typed = new StringBuilder();

    private Action action(int code) {
        return switch (code) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> Action.UP;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> Action.DOWN;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> Action.LEFT;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> Action.RIGHT;
            case KeyEvent.VK_ENTER -> Action.START;
            case KeyEvent.VK_E -> Action.INTERACT;
            case KeyEvent.VK_SPACE -> Action.ATTACK;
            case KeyEvent.VK_Q -> Action.POTION;
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_P -> Action.PAUSE;
            case KeyEvent.VK_I -> Action.INVENTORY;
            case KeyEvent.VK_M -> Action.MAP;
            case KeyEvent.VK_F -> Action.MUTE;
            case KeyEvent.VK_TAB -> Action.CYCLE_TOOL;
            case KeyEvent.VK_SHIFT -> Action.GUARD;
            case KeyEvent.VK_R -> Action.CAST;
            case KeyEvent.VK_BACK_SPACE -> Action.RESET_ROCKS;
            case KeyEvent.VK_F11 -> Action.FULLSCREEN;
            case KeyEvent.VK_H -> Action.HELP;
            default -> null;
        };
    }
    public boolean isDown(Action wanted) {
        return held.stream().anyMatch(code -> !suppressed.contains(code) && action(code) == wanted);
    }
    public boolean consume(Action wanted) { return pressed.remove(wanted); }
    public void trigger(Action wanted) { pressed.add(wanted); }
    public void clear() { held.clear(); suppressed.clear(); pressed.clear(); typed.setLength(0); }
    /** Menu transitions must wait for key-up, rather than interpreting OS repeat as a new press. */
    public void suppressHeldInput() { suppressed.addAll(held); pressed.clear(); typed.setLength(0); }
    public void endFrame() { pressed.clear(); typed.setLength(0); }
    public String consumeText() { String value = typed.toString(); typed.setLength(0); return value; }
    public void typeText(String value) { typed.append(value); }
    public void press(int code) {
        if (suppressed.contains(code)) return;
        if (code == KeyEvent.VK_BACK_SPACE) typed.append('\b');
        if (held.add(code)) {
            Action wanted = action(code);
            if (wanted != null) pressed.add(wanted);
        }
    }
    public void release(int code) { held.remove(code); suppressed.remove(code); }
    @Override public void keyPressed(KeyEvent event) { press(event.getKeyCode()); }
    @Override public void keyReleased(KeyEvent event) { release(event.getKeyCode()); }
    @Override public void keyTyped(KeyEvent event) {
        char value = event.getKeyChar();
        if (value != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(value)) typed.append(value);
    }
}
