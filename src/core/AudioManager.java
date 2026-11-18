package core;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.Map;

/** Unavailable sound hardware must never prevent playing. */
public final class AudioManager implements AutoCloseable {
    private final Map<String, Clip> effects = new HashMap<>();
    private boolean available;
    private boolean muted;
    private Clip music;
    private String track = "";
    public AudioManager() { this(!GraphicsEnvironment.isHeadless()); }
    public AudioManager(boolean enabled) { available = enabled; }
    public boolean muted() { return muted; }
    public void toggleMute() {
        muted = !muted;
        if (music != null) {
            if (muted) music.stop();
            else music.loop(Clip.LOOP_CONTINUOUSLY);
        }
        if (muted) effects.values().forEach(Clip::stop);
    }
    public void music(String name) {
        if (name.equals(track)) return;
        stopMusic();
        track = name;
        if (!available || name.isBlank()) return;
        music = open(name);
        if (music != null && !muted) music.loop(Clip.LOOP_CONTINUOUSLY);
    }
    public void effect(String name) {
        if (!available || muted) return;
        Clip clip = effects.computeIfAbsent(name, this::open);
        if (clip != null) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }
    private Clip open(String name) {
        String path = name.startsWith("/") ? name
                : "/sounds/" + name + (name.endsWith(".wav") ? "" : ".wav");
        try {
            var resource = AudioManager.class.getResource(path);
            if (resource == null) throw new IllegalArgumentException("Missing sound: " + path);
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(resource)) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                return clip;
            }
        } catch (Exception exception) {
            System.err.println("Audio disabled: " + exception.getMessage());
            available = false;
            return null;
        }
    }
    public void stopMusic() {
        if (music != null) { music.stop(); music.close(); music = null; }
        track = "";
    }
    @Override public void close() {
        stopMusic();
        effects.values().forEach(clip -> { clip.stop(); clip.close(); });
        effects.clear();
    }
}
