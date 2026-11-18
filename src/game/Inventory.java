package game;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Run-wide equipment and flags survive movement between levels. */
public final class Inventory {
    private int keys;
    private int coins;
    private int potions;
    private final EnumSet<ItemType> equipment = EnumSet.noneOf(ItemType.class);
    private final Set<String> flags = new LinkedHashSet<>();
    public int keys() { return keys; }
    public int coins() { return coins; }
    public int potions() { return potions; }
    public boolean has(ItemType item) { return equipment.contains(item); }
    public void addKey() { keys++; }
    public boolean useKey() { if (keys == 0) return false; keys--; return true; }
    public void addCoins(int amount) { coins += Math.max(0, amount); }
    public boolean spendCoins(int amount) {
        if (amount < 0 || coins < amount) return false;
        coins -= amount;
        return true;
    }
    public void addPotion() { potions++; }
    public boolean usePotion() { if (potions == 0) return false; potions--; return true; }
    public void equip(ItemType item) { equipment.add(item); }
    public boolean grant(String flag) { return !flag.isBlank() && flags.add(flag); }
    public Set<String> flags() { return Collections.unmodifiableSet(flags); }
}
