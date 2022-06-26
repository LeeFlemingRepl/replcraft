package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.StructureContext;
import eelfloat.replcraft.util.BoxedDoubleButActuallyUseful;

/**
 * A strategy to carry over unused fuel when a client disconnects
 */
public class LeftoverFuelStrategy extends FuelStrategy {
    public LeftoverFuelStrategy(String configName) {
        super(configName);
    }

    @Override
    double generate(double fuel_cost, StructureContext structureContext) {
        BoxedDoubleButActuallyUseful tracker = ReplCraft.plugin.leftOverFuel.get(
            structureContext.getStructure(),
            () -> new BoxedDoubleButActuallyUseful(0.0)
        );
        double amount = Math.min(tracker.value, fuel_cost);
        tracker.value = Math.max(tracker.value - amount, 0);
        return amount;
    }

    @Override
    public double getEstimatedFuelAvailable(StructureContext structureContext) {
        BoxedDoubleButActuallyUseful tracker = ReplCraft.plugin.leftOverFuel.get(
            structureContext.getStructure(),
            () -> new BoxedDoubleButActuallyUseful(0.0)
        );
        return tracker.value;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public String getType() {
        return "leftover";
    }
}
