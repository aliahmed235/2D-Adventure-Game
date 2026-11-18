package tests;

import core.AudioManager;
import entity.Direction;
import game.GameSession;
import game.GameState;
import game.PlayerProfile;
import main.KeyHandler;
import world.WorldObject;
import java.awt.event.KeyEvent;

/** Name entry and menu behavior without opening or changing a native window. */
public final class ProfileMenuTests {
    private static final double TICK = 1.0 / 60;
    private ProfileMenuTests() { }
    public static void main(String[] args) { run(); System.out.println("Profile and menu tests passed."); }
    public static void run() {
        profiles();
        nameEntry();
        queuedStartAndHeldKeys();
        unicodeEditing();
        recovery();
        pauseAndHelp();
        menuRequests();
        fullscreenStates();
    }
    private static GameSession session() { return new GameSession(new AudioManager(false)); }

    private static void profiles() {
        check(new PlayerProfile("  Ali   Hassan  ").name().equals("Ali Hassan"), "Whitespace was not cleaned");
        check(new PlayerProfile("Jose\u0301").name().equals("Jos\u00e9"), "Unicode name was not normalized");
        check(new PlayerProfile("\u0639\u0644\u064a").name().equals("\u0639\u0644\u064a"), "Arabic letters were rejected");
        check(new PlayerProfile("A-1_O'Neil").name().equals("A-1_O'Neil"), "Allowed punctuation was rejected");
        check(new PlayerProfile("1234567890123456").name().length() == 16, "A 16-character name was rejected");
        invalid(() -> new PlayerProfile("   "));
        invalid(() -> new PlayerProfile("12345678901234567"));
        invalid(() -> new PlayerProfile("Ali!"));
        invalid(() -> new PlayerProfile("<script>"));
    }

    private static void nameEntry() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            check(s.state() == GameState.TITLE && !s.hasStarted(), "New session did not ask for a name");
            tap(s, input, KeyEvent.VK_ENTER);
            check(s.state() == GameState.TITLE && !s.nameError().isBlank(), "Blank name started a run");
            check(s.playTime() == 0, "Name entry advanced game time");
            // These letters are also gameplay shortcuts; they must remain text-only here.
            input.press(KeyEvent.VK_F); input.press(KeyEvent.VK_W); input.press(KeyEvent.VK_I);
            input.typeText("Fwi"); s.update(TICK, input);
            check(s.nameInput().equals("Fwi") && !s.muted(), "Name letters triggered a game action");
            check(s.state() == GameState.TITLE && s.nameError().isEmpty(), "Typing did not clear the error");
            tap(s, input, KeyEvent.VK_ENTER);
            check(s.state() == GameState.PLAYING && s.playerName().equals("Fwi") && s.hasStarted(), "Named run did not start");
            double x = s.player().x(), y = s.player().y();
            s.update(TICK, input);
            check(s.player().x() == x && s.player().y() == y && s.state() == GameState.PLAYING,
                    "Held name keys leaked into movement or inventory");
            check(!s.muted(), "Typing F muted the game after submission");
        }
    }

    private static void unicodeEditing() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            input.typeText("\u0639\u0644\u064a" + new String(Character.toChars(0x1D49C)));
            s.update(TICK, input);
            check(s.nameInput().codePointCount(0, s.nameInput().length()) == 4, "Name used UTF-16 length");
            tap(s, input, KeyEvent.VK_BACK_SPACE);
            check(s.nameInput().equals("\u0639\u0644\u064a"), "Backspace split a supplementary character");
            tap(s, input, KeyEvent.VK_ENTER);
            check(s.playerName().equals("\u0639\u0644\u064a"), "Unicode name did not reach the profile");
        }
        input.clear();
        try (GameSession s = session()) {
            input.typeText("12345678901234567"); s.update(TICK, input);
            check(s.nameInput().equals("1234567890123456") && !s.nameError().isEmpty(), "Length overflow was not handled");
            input.press(KeyEvent.VK_BACK_SPACE); input.press(KeyEvent.VK_BACK_SPACE);
            s.update(TICK, input); input.release(KeyEvent.VK_BACK_SPACE);
            check(s.nameInput().equals("12345678901234"), "Repeated Backspace did not edit");
            tap(s, input, KeyEvent.VK_ENTER);
            check(s.playerName().equals("12345678901234"), "Edited name was not submitted");
        }
    }

    private static void queuedStartAndHeldKeys() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            input.typeText("Al"); s.update(TICK, input);
            input.typeText("i");
            input.trigger(KeyHandler.Action.START);
            s.update(TICK, input);
            check(s.state() == GameState.PLAYING && s.playerName().equals("Ali"),
                    "Clicking Start lost a character typed in the same frame");
            input.press(KeyEvent.VK_H); s.update(TICK, input);
            check(s.state() == GameState.CONTROLS, "Held-help fixture did not open");
            s.update(TICK, input);
            input.press(KeyEvent.VK_H); s.update(TICK, input);
            check(s.state() == GameState.CONTROLS, "OS key repeat closed help while H was held");
            input.release(KeyEvent.VK_H);
            input.press(KeyEvent.VK_ESCAPE); s.update(TICK, input);
            check(s.state() == GameState.PLAYING, "Escape did not close help");
            input.press(KeyEvent.VK_ESCAPE); s.update(TICK, input);
            check(s.state() == GameState.PLAYING, "OS key repeat reopened pause after returning to play");
            input.release(KeyEvent.VK_ESCAPE);
            tap(s, input, KeyEvent.VK_ESCAPE);
            check(s.state() == GameState.PAUSED, "A fresh Escape press was incorrectly suppressed");
        }
    }

    private static void recovery() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            start(s, input, "Ali"); removeThreats(s);
            s.player().takeDamage(6, false); s.update(TICK, input);
            check(s.state() == GameState.GAME_OVER, "Recovery fixture did not die");
            tap(s, input, KeyEvent.VK_ENTER);
            check(s.state() == GameState.PLAYING && s.player().alive() && s.playerName().equals("Ali"), "Recovery lost the name");
            s.startNewRun();
            check(s.playerName().equals("Ali") && s.player().health() == s.player().maxHealth(), "New run replaced the profile");
        }
    }

    private static void pauseAndHelp() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            start(s, input, "Menu Tester"); removeThreats(s); s.update(.2, input);
            tap(s, input, KeyEvent.VK_ESCAPE); double time = s.playTime();
            check(s.state() == GameState.PAUSED && s.pauseOptions().size() == 6, "Pause menu missing");
            s.update(5, input); check(s.playTime() == time, "Pause advanced time");
            tap(s, input, KeyEvent.VK_UP); check(s.pauseSelection() == 5, "Up did not wrap");
            tap(s, input, KeyEvent.VK_DOWN); check(s.pauseSelection() == 0, "Down did not wrap");
            tap(s, input, KeyEvent.VK_S); check(s.pauseSelection() == 1, "S did not move down");
            tap(s, input, KeyEvent.VK_W); check(s.pauseSelection() == 0, "W did not move up");
            tap(s, input, KeyEvent.VK_ENTER); check(s.state() == GameState.PLAYING, "Resume failed");
            tap(s, input, KeyEvent.VK_H); check(s.state() == GameState.CONTROLS, "H did not open help");
            time = s.playTime(); s.update(5, input); check(s.playTime() == time, "Help advanced time");
            tap(s, input, KeyEvent.VK_ESCAPE); check(s.state() == GameState.PLAYING, "Help did not return to play");
            tap(s, input, KeyEvent.VK_ESCAPE); s.selectPauseOption(2); tap(s, input, KeyEvent.VK_ENTER);
            check(s.state() == GameState.CONTROLS, "How to play option failed");
            s.closeControls(); check(s.state() == GameState.PAUSED && s.pauseSelection() == 2, "Help lost pause context");
            s.selectPauseOption(1); s.activatePauseOption(); check(s.state() == GameState.INVENTORY, "Inventory option failed");
            tap(s, input, KeyEvent.VK_ESCAPE); check(s.state() == GameState.PLAYING, "Inventory did not close");
        }
    }

    private static void menuRequests() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            start(s, input, "Sam"); tap(s, input, KeyEvent.VK_ESCAPE);
            s.selectPauseOption(3); s.activatePauseOption();
            check(s.state() == GameState.PAUSED && s.consumeFullscreenRequest(), "Fullscreen option did not request a change");
            check(!s.consumeFullscreenRequest() && !s.fullscreen(), "Window request was not one-shot");
            s.setFullscreen(true); check(s.pauseOptions().get(3).equals("Windowed mode"), "Fullscreen label did not update");
            s.selectPauseOption(4); s.activatePauseOption();
            check(s.muted() && s.pauseOptions().get(4).contains("Off"), "Sound option did not mute");
            s.activatePauseOption(); check(!s.muted() && s.pauseOptions().get(4).contains("On"), "Sound option did not unmute");
            s.selectPauseOption(5); s.activatePauseOption();
            check(s.consumeQuitRequest() && !s.consumeQuitRequest(), "Quit request was not one-shot");
            check(s.state() == GameState.PAUSED, "Quit request changed window-independent state");
        }
    }

    private static void fullscreenStates() {
        KeyHandler input = new KeyHandler();
        try (GameSession s = session()) {
            fullscreen(s, input, GameState.TITLE);
            start(s, input, "Sky"); removeThreats(s); fullscreen(s, input, GameState.PLAYING);
            tap(s, input, KeyEvent.VK_ESCAPE); fullscreen(s, input, GameState.PAUSED);
            s.selectPauseOption(1); s.activatePauseOption(); fullscreen(s, input, GameState.INVENTORY);
            tap(s, input, KeyEvent.VK_H); fullscreen(s, input, GameState.CONTROLS);
            s.closeControls(); tap(s, input, KeyEvent.VK_ESCAPE);
            s.player().takeDamage(6, false); s.update(TICK, input); fullscreen(s, input, GameState.GAME_OVER);
        }
        input.clear();
        try (GameSession s = session()) {
            start(s, input, "Sky");
            for (String flag : new String[]{"forest-seal", "sanctuary-seal", "vault-seal", "ember-seal", "crown-defeated"}) s.inventory().grant(flag);
            use(s, input, "sanctuary-portal"); use(s, input, "vault-portal");
            use(s, input, "emberwood-portal"); use(s, input, "crypt-portal"); use(s, input, "final-treasure");
            fullscreen(s, input, GameState.VICTORY);
        }
    }

    private static void fullscreen(GameSession s, KeyHandler input, GameState expected) {
        check(s.state() == expected, "Wrong fullscreen fixture: " + s.state() + " expected " + expected);
        tap(s, input, KeyEvent.VK_F11);
        check(s.consumeFullscreenRequest() && !s.consumeFullscreenRequest(), "F11 did not make one request");
        check(s.state() == expected, "F11 changed game/menu state");
    }
    private static void start(GameSession s, KeyHandler input, String name) { s.setPlayerName(name); tap(s, input, KeyEvent.VK_ENTER); }
    private static void removeThreats(GameSession s) { s.enemies().forEach(enemy -> enemy.damage(100, Long.MAX_VALUE, Direction.UP)); }
    private static void use(GameSession s, KeyHandler input, String id) {
        removeThreats(s);
        WorldObject object = s.level().objects().stream().filter(value -> value.spawn().id().equals(id)).findFirst().orElseThrow();
        s.player().setPosition(object.col() * 64, object.row() * 64); s.update(.4, input); tap(s, input, KeyEvent.VK_E);
    }
    private static void tap(GameSession s, KeyHandler input, int key) { input.press(key); s.update(TICK, input); input.release(key); }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid profile was accepted");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
