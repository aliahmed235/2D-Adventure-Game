package entity;

/** Enemy identity belongs in level content, rather than in object IDs or filenames. */
public enum EnemyKind {
    GREEN_SLIME("greenslime", "Green slime"), RED_SLIME("redslime", "Red slime"),
    BAT("bat", "Cave bat"), ORC("orc", "Orc raider"), SKELETON_LORD("skeletonlord", "Skeleton King");
    public final String sprite;
    public final String displayName;
    EnemyKind(String sprite, String displayName) { this.sprite = sprite; this.displayName = displayName; }
    public boolean melee() { return this == ORC || this == SKELETON_LORD; }
}
