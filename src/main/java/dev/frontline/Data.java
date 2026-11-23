package dev.frontline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Data {
	public int captureSeconds = 60;
	public int holdSeconds = 60;
	/** true: red starts at point #1 and pushes toward the last point, blue the opposite way. */
	public boolean redPushesUp = true;
	/** rollback speed of an abandoned capture / hold bar, relative to the fill speed */
	public double captureDecay = 0.5;
	public double holdDecay = 0.5;
	/** extra multiplier for the rollback while the enemy has the numerical superiority */
	public double rollbackAccel = 2.0;
	public String center;
	public List<CapPoint> points = new ArrayList<>();
	/** player UUID -> team name */
	public Map<String, String> teams = new HashMap<>();

	public CapPoint find(String name) {
		for (CapPoint p : points) if (p.name.equalsIgnoreCase(name)) return p;
		return null;
	}

	public int centerIndex() {
		if (center != null) {
			for (int i = 0; i < points.size(); i++) if (points.get(i).name.equalsIgnoreCase(center)) return i;
		}
		return points.isEmpty() ? 0 : points.size() / 2;
	}
}
