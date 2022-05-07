package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.Client;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

public class EconomyFuelStrategy extends FuelStrategy {
    private final Client client;
    private final Economy economy;
    private final double fuel_price;

    public EconomyFuelStrategy(Client client, Economy economy, double fuel_price) {
        this.client = client;
        this.economy = economy;
        this.fuel_price = fuel_price;
    }

    @Override
    double generate(double fuel_amount) {
        OfflinePlayer player = client.getStructure().getPlayer();

        double price = fuel_price * fuel_amount;
        if (price > this.economy.getBalance(player)) return 0;

        EconomyResponse tx = this.economy.withdrawPlayer(player, client.getStructure().sign.getWorld().getName(), price);
        if (!tx.transactionSuccess()) return 0;

        return fuel_amount;
    }

    @Override
    public String toString() {
        return String.format("EconomyFuelStrategy { $%s per unit of fuel }", fuel_price);
    }
}
