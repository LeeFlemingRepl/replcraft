package eelfloat.replcraft.net;

import java.util.*;

public class RateTracker {
    /** Human readable name to container */
    public HashMap<String, RateTrackTimeContainer> containers = new HashMap<>();

    public RateTracker() {
        containers.put("second", new RateTrackTimeContainer(1000));
        containers.put("minute", new RateTrackTimeContainer(60*1000));
    }

    public void queue(double fuelCost) {
        RateTrackTimeContainer.RateTrack track = new RateTrackTimeContainer.RateTrack(fuelCost);
        for (RateTrackTimeContainer value: containers.values()) {
            value.queue(track);
            value.cleanup();
        }
    }

    public static class RateTrackTimeContainer {
        public ArrayDeque<RateTrack> calls = new ArrayDeque<>();
        private final int expirationMs;

        private static class RateTrack {
            private final long time;
            private final double fuelCost;
            private RateTrack(double fuelCost) {
                this.time = System.currentTimeMillis();
                this.fuelCost = fuelCost;
            }
        }

        private RateTrackTimeContainer(int expirationMs) {
            this.expirationMs = expirationMs;
        }

        public double fuelUsed() {
            this.cleanup();

            double total = 0;
            for (RateTrack call : this.calls)
                total += call.fuelCost;

            return total;
        }

        private void queue(RateTrack track) {
            this.calls.add(track);
        }

        private void cleanup() {
            while (!calls.isEmpty() && System.currentTimeMillis() > calls.element().time + expirationMs)
                calls.remove();
        }
    }
}
