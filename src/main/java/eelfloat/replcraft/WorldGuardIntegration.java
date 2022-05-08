package eelfloat.replcraft;

import com.sk89q.intake.CommandException;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;

public class WorldGuardIntegration {
    public StateFlag flag;

    public WorldGuardIntegration() {
        flag = new StateFlag("replcraft", false);
        try {
            WorldGuard.getInstance().getFlagRegistry().register(flag);
        } catch(IllegalStateException ex) {
            ReplCraft.plugin.logger.warning("Failed to set up WorldGuard flags: " + ex);
            // Attempt to load any existing registered flag, since this typically happens when reloaded.
            Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get("replcraft");
            if (flag instanceof StateFlag)
                this.flag = (StateFlag) flag;
        }
    }
}
