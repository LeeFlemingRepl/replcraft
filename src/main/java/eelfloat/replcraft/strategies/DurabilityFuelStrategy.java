package eelfloat.replcraft.strategies;

import eelfloat.replcraft.Structure;
import eelfloat.replcraft.ItemVirtualStructure;
import eelfloat.replcraft.net.StructureContext;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class DurabilityFuelStrategy extends FuelStrategy {
    private final double fuel_per_unit;

    public DurabilityFuelStrategy(String configName, double fuel_per_unit) {
        super(configName);
        this.fuel_per_unit = fuel_per_unit;
    }

    @Override
    double generate(double fuel_cost, StructureContext structureContext) {
        if (!(structureContext.getStructure() instanceof ItemVirtualStructure)) return 0;
        ItemVirtualStructure structure = (ItemVirtualStructure) structureContext.getStructure();

        ItemMeta meta = structure.item.getItemMeta();
        if (!(meta instanceof Damageable)) return 0;

        int maxDurability = structure.item.getType().getMaxDurability();
        int damage = ((Damageable) meta).getDamage();
        int used = (int) Math.ceil(Math.min(maxDurability - damage, fuel_cost / fuel_per_unit));
        ((Damageable) meta).setDamage(damage + used);
        structure.item.setItemMeta(meta);

        return used * fuel_per_unit * structure.getEnchantFuelGenerationMultiplier();
    }

    @Override
    public double getEstimatedFuelAvailable(StructureContext structureContext) {
        if (!(structureContext.getStructure() instanceof ItemVirtualStructure)) return 0;
        ItemVirtualStructure structure = (ItemVirtualStructure) structureContext.getStructure();

        ItemMeta meta = structure.item.getItemMeta();
        if (!(meta instanceof Damageable)) return 0;

        int maxDurability = structure.item.getType().getMaxDurability();
        int damage = ((Damageable) meta).getDamage();

        return (maxDurability - damage) * fuel_per_unit * structure.getEnchantFuelGenerationMultiplier();
    }

    @Override
    public String getType() {
        return "durability";
    }

    @Override
    public String toString() {
        return String.format("%s fuel per durability point", this.fuel_per_unit);
    }
}
