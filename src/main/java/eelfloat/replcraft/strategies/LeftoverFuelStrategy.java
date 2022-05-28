package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.util.BoxedDoubleButActuallyUseful;

/**
 * A strategy to carry over unused fuel when a client disconnects
 */
public class LeftoverFuelStrategy extends FuelStrategy {
    @Override
    double generate(double fuel_cost, Client client) {
        BoxedDoubleButActuallyUseful tracker = ReplCraft.plugin.leftOverFuel.get(
            client.getStructure(),
            () -> new BoxedDoubleButActuallyUseful(0.0)
        );
        double amount = Math.min(tracker.value, fuel_cost);
        tracker.value = Math.max(tracker.value - amount, 0);
        return amount;
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public String name() {
        return "leftover";
    }
}
