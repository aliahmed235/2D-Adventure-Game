package entity;

/** A tool selects a complete set of authored directional attack sprites. */
public enum Weapon {
    SWORD("attack", "sword_normal", .22), AXE("axe", "axe", .32), PICKAXE("pick", "pickaxe", .28);
    private final String spritePrefix;
    private final String texture;
    private final double duration;
    Weapon(String spritePrefix, String texture, double duration) {
        this.spritePrefix = spritePrefix;
        this.texture = texture;
        this.duration = duration;
    }
    public String spritePrefix() { return spritePrefix; }
    public String texture() { return texture; }
    public double duration() { return duration; }
}
