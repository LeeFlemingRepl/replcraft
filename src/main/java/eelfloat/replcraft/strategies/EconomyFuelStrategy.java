package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.StructureContext;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

public class EconomyFuelStrategy extends FuelStrategy {
    private final StructureContext structureContext;
    private final double fuel_price;

    public EconomyFuelStrategy(StructureContext structureContext, double fuel_price) {
        this.structureContext = structureContext;
        this.fuel_price = fuel_price;
    }

    @Override
    double generate(double fuel_amount, StructureContext structureContext) {
        OfflinePlayer player = this.structureContext.getStructure().getPlayer();

        double price = fuel_price * fuel_amount;
        if (price > ReplCraft.plugin.economy.getBalance(player)) return 0;

        EconomyResponse tx = ReplCraft.plugin.economy.withdrawPlayer(player, this.structureContext.getStructure().sign.getWorld().getName(), price);
        if (!tx.transactionSuccess()) return 0;

        return fuel_amount;
    }

    @Override
    public String name() {
        return "economy";
    }

    @Override
    public String toString() {
        return String.format("EconomyFuelStrategy { $%s per unit of fuel }", fuel_price);
    }
}
