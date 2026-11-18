package world;

/** Immutable placement data; mutable state lives in WorldObject. */
public record ObjectSpawn(String id, ObjectType type, int col, int row,
                          String argument, String requires) {
    public ObjectSpawn {
        if (id == null || id.isBlank() || type == null) {
            throw new IllegalArgumentException("An object needs an ID and a type");
        }
        argument = argument == null ? "" : argument;
        requires = requires == null ? "" : requires;
    }
}
