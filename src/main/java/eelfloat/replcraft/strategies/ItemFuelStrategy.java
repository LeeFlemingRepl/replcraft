package eelfloat.replcraft.strategies;

import eelfloat.replcraft.net.StructureContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemFuelStrategy extends FuelStrategy {
    private final StructureContext structureContext;
    private final Material material;
    private final double fuel_per_item;

    public ItemFuelStrategy(String configName, StructureContext structureContext, Material material, double fuel_per_item) {
        super(configName);
        this.structureContext = structureContext;
        this.material = material;
        this.fuel_per_item = fuel_per_item;
    }

    @Override
    public double generate(double fuel, StructureContext structureContext) {
        double generated = 0;
        while (generated < fuel) {
            ItemStack stack = this.structureContext.getStructure().findMaterial(this.material);
            if (stack == null) break;
            stack.setAmount(stack.getAmount() - 1);
            generated += fuel_per_item;
        }
        return generated;
    }

    @Override
    public double getEstimatedFuelAvailable(StructureContext structureContext) {
        long count = this.structureContext.getStructure()
            .getStructureInventory()
            .stream()
            .filter(el -> el != null && el.getType() == this.material)
            .count();
        return count * fuel_per_item;
    }

    @Override
    public String getType() {
        return "item";
    }

    @Override
    public String toString() {
        return String.format("%s fuel per %s", fuel_per_item, material);
    }
}