package world;

/** Texture paths refer to resources on the Java classpath. */
public record TileDefinition(int id, String texture, boolean solid) { }
