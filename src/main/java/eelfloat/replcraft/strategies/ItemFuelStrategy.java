package eelfloat.replcraft.strategies;

import eelfloat.replcraft.net.Client;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemFuelStrategy extends FuelStrategy {
    private final Client client;
    private final Material material;
    private final double fuel_per_item;

    public ItemFuelStrategy(Client client, Material material, double fuel_per_item) {
        this.client = client;
        this.material = material;
        this.fuel_per_item = fuel_per_item;
    }

    @Override
    public double generate(double fuel, Client client) {
        double generated = 0;
        while (generated < fuel) {
            ItemStack stack = this.client.getStructure().findMaterial(this.material);
            if (stack == null) break;
            stack.setAmount(stack.getAmount() - 1);
            generated += fuel_per_item;
        }
        return generated;
    }

    @Override
    public String name() {
        return "item";
    }

    @Override
    public String toString() {
        return String.format("ItemFuelStrategy { %s fuel per %s }", fuel_per_item, material);
    }
}