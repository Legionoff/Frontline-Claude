package dev.frontline;

public final class CapPoint {
	public String name;
	public String dim;
	public int[] p1;
	public int[] p2;

	public CapPoint() {}

	public CapPoint(String name) {
		this.name = name;
	}

	public boolean complete() {
		return p1 != null && p2 != null && dim != null;
	}

	public double minX() { return Math.min(p1[0], p2[0]); }
	public double minY() { return Math.min(p1[1], p2[1]); }
	public double minZ() { return Math.min(p1[2], p2[2]); }
	public double maxX() { return Math.max(p1[0], p2[0]) + 1; }
	public double maxY() { return Math.max(p1[1], p2[1]) + 1; }
	public double maxZ() { return Math.max(p1[2], p2[2]) + 1; }

	public boolean contains(String dimension, double x, double y, double z) {
		if (!complete() || !dim.equals(dimension)) return false;
		return x >= minX() && x < maxX() && y >= minY() && y < maxY() && z >= minZ() && z < maxZ();
	}
}
