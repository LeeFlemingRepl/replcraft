package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;

public abstract class FuelStrategy {
    /** The amount of fuel consumed during the last consume operation */
    private double last_consumption = 0;
    /** The amount of spare fuel generated or returned to the strategy */
    protected double spare_fuel = 0;

    /**
     * Attempts to consume fuel from the strategy.
     * @param fuel_cost the amount of fuel to consume
     * @return how much fuel was consumed
     */
    public final double consume(double fuel_cost) {
        this.last_consumption = 0;

        while (true) {
            double spare_fuel_used = Math.min(spare_fuel, fuel_cost);
            fuel_cost -= spare_fuel_used;
            spare_fuel -= spare_fuel_used;
            last_consumption += spare_fuel_used;
            if (fuel_cost == 0) break;

            double generated = this.generate(fuel_cost);
            if (generated == 0) break;
            this.spare_fuel += generated;
        }


        return this.last_consumption;
    }

    /**
     * Attempts to consume fuel from the strategy by generating it immediately
     * @param fuel_cost how much fuel needs to be generated
     * @return how much fuel was generated
     */
    abstract double generate(double fuel_cost);

    /**
     * Returns the last consumption's consumed fuel.
     */
    public final void cancel_and_restore() {
        spare_fuel += last_consumption;
        last_consumption = 0;
    }

    /**
     * Commits the last consumption's consumed fuel, clearing the restorable amount.
     */
    public final void commit() {
        last_consumption = 0;
    }
}
