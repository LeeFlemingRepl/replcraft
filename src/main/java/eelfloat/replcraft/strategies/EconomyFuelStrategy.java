package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.StructureContext;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

public class EconomyFuelStrategy extends FuelStrategy {
    private final StructureContext structureContext;
    private final double fuel_price;

    public EconomyFuelStrategy(String configName, StructureContext structureContext, double fuel_price) {
        super(configName);
        this.structureContext = structureContext;
        this.fuel_price = fuel_price;
    }

    @Override
    double generate(double fuel_amount, StructureContext structureContext) {
        OfflinePlayer player = this.structureContext.getStructure().getPlayer();

        double price = fuel_price * fuel_amount;
        if (price > ReplCraft.plugin.economy.getBalance(player)) return 0;

        String worldName = this.structureContext.getStructure().getWorld().getName();
        EconomyResponse tx = ReplCraft.plugin.economy.withdrawPlayer(player, worldName, price);
        if (!tx.transactionSuccess()) return 0;

        return fuel_amount;
    }

    @Override
    public double getEstimatedFuelAvailable(StructureContext structureContext) {
        OfflinePlayer player = this.structureContext.getStructure().getPlayer();
        return ReplCraft.plugin.economy.getBalance(player) / fuel_price;
    }

    @Override
    public String getType() {
        return "economy";
    }

    @Override
    public String toString() {
        return String.format("$%s per unit of fuel", fuel_price);
    }
}
