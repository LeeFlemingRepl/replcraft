package eelfloat.replcraft.strategies;

import eelfloat.replcraft.net.Client;

public abstract class FuelStrategy {
    /** The amount of fuel consumed during the last consume operation */
    private double lastConsumption = 0;
    /** The amount of spare fuel generated or returned to the strategy */
    protected double spareFuel = 0;

    public double getSpareFuel() {
        return spareFuel;
    }

    /**
     * Attempts to consume fuel from the strategy.
     *
     * @param fuel_cost the amount of fuel to consume
     * @param client
     * @return how much fuel was consumed
     */
    public final double consume(double fuel_cost, Client client) {
        this.lastConsumption = 0;

        while (true) {
            double spare_fuel_used = Math.min(spareFuel, fuel_cost);
            fuel_cost -= spare_fuel_used;
            spareFuel -= spare_fuel_used;
            lastConsumption += spare_fuel_used;
            if (fuel_cost == 0) break;

            double generated = this.generate(fuel_cost, client);
            if (generated == 0) break;
            this.spareFuel += generated;
        }


        return this.lastConsumption;
    }

    /**
     * Attempts to consume fuel from the strategy by generating it immediately
     *
     * @param fuel_cost how much fuel needs to be generated
     * @param client
     * @return how much fuel was generated
     */
    abstract double generate(double fuel_cost, Client client);

    /**
     * Determines if a fuel strategy is excluded from client errors
     * @return if the fuel strategy is hidden
     */
    public boolean isHidden() {
        return false;
    }

    /**
     * Returns the last consumption's consumed fuel.
     */
    public final void cancel_and_restore() {
        spareFuel += lastConsumption;
        lastConsumption = 0;
    }

    /**
     * Commits the last consumption's consumed fuel, clearing the restorable amount.
     */
    public final void commit() {
        lastConsumption = 0;
    }
}
