package eelfloat.replcraft.strategies;

import eelfloat.replcraft.net.StructureContext;

import java.util.ArrayList;

public abstract class FuelStrategy {
    /** The amount of fuel consumed during the last consume operation */
    private double lastConsumption = 0;
    /** The amount of spare fuel generated or returned to the strategy */
    protected double spareFuel = 0;
    /** The name of the strategy as named in the config */
    public final String configName;

    protected FuelStrategy(String configName) {
        this.configName = configName;
    }

    public double getSpareFuel() {
        return spareFuel;
    }

    /**
     * Attempts to consume fuel from the strategy.
     *
     * @param fuel_cost the amount of fuel to consume
     * @param structureContext
     * @return how much fuel was consumed
     */
    public final double consume(double fuel_cost, StructureContext structureContext) {
        this.lastConsumption = 0;

        while (true) {
            double spare_fuel_used = Math.min(spareFuel, fuel_cost);
            fuel_cost -= spare_fuel_used;
            spareFuel -= spare_fuel_used;
            lastConsumption += spare_fuel_used;
            if (fuel_cost == 0) break;

            double generated = this.generate(fuel_cost, structureContext);
            if (generated == 0) break;
            this.spareFuel += generated;
        }


        return this.lastConsumption;
    }

    /**
     * Attempts to consume fuel from the strategy by generating it immediately
     *
     * @param fuel_cost how much fuel needs to be generated
     * @param structureContext
     * @return how much fuel was generated
     */
    abstract double generate(double fuel_cost, StructureContext structureContext);

    /**
     * Determines if a fuel strategy is excluded from client error. Hidden strategies also don't prevent fuel from being
     * free, so if the only available strategies are hidden, fuel will be skipped entirely.
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

    /**
     * @return the unique, human-readable name of the fuel strategy
     */
    public abstract String getType();

    /**
     * Estimates the amount of fuel available for generation
     * @return fuel estimate
     */
    public abstract double getEstimatedFuelAvailable(StructureContext structureContext);

    /**
     * Does string formatting stuff to produce a user-friendly view of the strategy suitable for slapping into an error.
     * @param limit the user-set fuel limit
     * @param used how much fuel the strategy has provided so far
     * @return the strategy formatted as a string
     */
    public String asStringWithData(double limit, double used) {
        ArrayList<String> list = new ArrayList<>();
        list.add(this.toString());
        if (limit != Double.POSITIVE_INFINITY) {
            list.add(String.format("used %s of user limit %s", used, limit));
        } else {
            list.add(String.format("used %s", used));
        }

        return String.format(
            "%s { %s }",
            this.getClass().getSimpleName(),
            String.join(", ", list)
        );
    }
}
