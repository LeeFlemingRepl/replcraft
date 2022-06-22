package eelfloat.replcraft.strategies;

import eelfloat.replcraft.net.StructureContext;

public class RatelimitFuelStrategy extends FuelStrategy {
    private long last_check = System.currentTimeMillis();
    private final double fuel_per_sec;
    private final double max_fuel;

    public RatelimitFuelStrategy(double fuel_per_sec, double max_fuel) {
        this.fuel_per_sec = fuel_per_sec;
        this.max_fuel = max_fuel;
        this.spareFuel = max_fuel;
    }

    /** Updates the spare fuel count immediately */
    public void updateSpareFuelNow() {
        long now = System.currentTimeMillis();
        double generated = fuel_per_sec * (now - last_check) / 1000.0;
        double space_left = max_fuel - spareFuel;
        this.spareFuel += Math.min(space_left, generated);
        last_check = now;
    }

    @Override
    public double generate(double fuel_cost, StructureContext structureContext) {
        long now = System.currentTimeMillis();
        double generated = fuel_per_sec * (now - last_check) / 1000.0;
        double space_left = (max_fuel - spareFuel) + fuel_cost;
        last_check = now;
        return Math.min(space_left, generated);
    }

    @Override
    public String name() {
        return "ratelimit";
    }

    @Override
    public String toString() {
        return String.format("RatelimitStrategy { %s fuel per second (max %s) }", fuel_per_sec, max_fuel);
    }
}
