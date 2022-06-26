package eelfloat.replcraft.strategies;

import eelfloat.replcraft.net.StructureContext;

public class RatelimitFuelStrategy extends FuelStrategy {
    private long last_check = System.currentTimeMillis();
    private final double fuel_per_sec;
    private final double max_fuel;

    public RatelimitFuelStrategy(String configName, double fuel_per_sec, double max_fuel) {
        super(configName);
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
    public String getType() {
        return "ratelimit";
    }

    @Override
    public double getEstimatedFuelAvailable(StructureContext structureContext) {
        this.updateSpareFuelNow();
        return 0;
    }

    @Override
    public String toString() {
        return String.format("%s fuel per second (max %s)", fuel_per_sec, max_fuel);
    }
}
