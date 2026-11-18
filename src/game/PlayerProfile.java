package game;

import java.text.Normalizer;

/** A display name is separate from an actor's health, position, and new-run state. */
public record PlayerProfile(String name) {
    public static final int MAX_NAME_LENGTH = 16;

    public PlayerProfile {
        name = clean(name);
        if (name.isEmpty()) throw new IllegalArgumentException("Enter your name to start.");
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH)
            throw new IllegalArgumentException("Use up to 16 characters.");
        if (!name.codePoints().allMatch(PlayerProfile::allowed))
            throw new IllegalArgumentException("Use letters, numbers, spaces, - ' or _.");
    }

    public static String clean(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
    }

    public static boolean allowed(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || codePoint == ' '
                || codePoint == '-' || codePoint == '\'' || codePoint == '_';
    }
}
