package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;

public class RatelimitFuelStrategy extends FuelStrategy {
    private long last_check = System.currentTimeMillis();
    private final double fuel_per_sec;
    private final double max_fuel;

    public RatelimitFuelStrategy(double fuel_per_sec, double max_fuel) {
        this.fuel_per_sec = fuel_per_sec;
        this.max_fuel = max_fuel;
    }

    @Override
    public double generate(double fuel_cost) {
        long now = System.currentTimeMillis();
        double generated = fuel_per_sec * (now - last_check) / 1000.0;
        double space_left = (max_fuel - spare_fuel) + fuel_cost;
        last_check = now;
        return Math.min(space_left, generated);
    }

    @Override
    public String toString() {
        return String.format("RatelimitStrategy { %s fuel per second (max %s) }", fuel_per_sec, max_fuel);
    }
}
