package me.lovelace.lovecontracts.npc.model;

import java.util.Map;

/** Optional destination/working area, shared by simple orders and story stages. */
public record QuestLocation(String world, double x, double y, double z, double radius) {
    public QuestLocation {
        if (world == null || world.isBlank() || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Double.isFinite(radius) || radius < 1 || radius > 256)
            throw new IllegalArgumentException("Invalid quest location");
    }
    public boolean contains(String world, double x, double y, double z) {
        return this.world.equals(world) && distance(x, y, z) <= radius;
    }
    public double distance(double x, double y, double z) {
        return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.y - y, 2) + Math.pow(this.z - z, 2));
    }
    public String address() { return world + " " + (int)Math.floor(x) + ", " + (int)Math.floor(y) + ", " + (int)Math.floor(z); }
    public static QuestLocation parse(Map<?, ?> map) {
        if (map.get("world") == null) throw new IllegalArgumentException("Missing world");
        return new QuestLocation(map.get("world").toString(), number(map, "x", null), number(map, "y", null),
                number(map, "z", null), number(map, "radius", 4));
    }
    private static double number(Map<?, ?> map, String key, Number fallback) {
        Object value = map.containsKey(key) ? map.get(key) : fallback;
        if (value == null) throw new IllegalArgumentException("Missing coordinate " + key);
        return Double.parseDouble(value.toString());
    }
}
