package eelfloat.replcraft;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

public class WorldGuardIntegration {
    public StateFlag replcraft_enabled;
    public StateFlag replcraft_infinite_fuel;

    public WorldGuardIntegration() {
        FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
        replcraft_enabled = new StateFlag("replcraft-enabled", true);
        replcraft_infinite_fuel = new StateFlag("replcraft-infinite-fuel", false);

        try {
            flagRegistry.register(replcraft_enabled);
            flagRegistry.register(replcraft_infinite_fuel);
        } catch(IllegalStateException ex) {
            ReplCraft.plugin.logger.warning("Failed to set up WorldGuard flags: " + ex);

            // Attempt to load any existing registered flags, since this typically happens when reloaded.
            Flag<?> replcraft_enabled = flagRegistry.get("replcraft-enabled");
            if (replcraft_enabled instanceof StateFlag)
                this.replcraft_enabled = (StateFlag) replcraft_enabled;

            Flag<?> replcraft_infinite_fuel = flagRegistry.get("replcraft-infinite-fuel");
            if (replcraft_infinite_fuel instanceof StateFlag)
                this.replcraft_infinite_fuel = (StateFlag) replcraft_infinite_fuel;
        }
    }
}
