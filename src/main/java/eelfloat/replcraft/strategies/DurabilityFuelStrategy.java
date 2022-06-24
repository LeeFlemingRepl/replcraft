package eelfloat.replcraft.strategies;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.ItemVirtualStructure;
import eelfloat.replcraft.net.StructureContext;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class DurabilityFuelStrategy extends FuelStrategy {
    private final double fuel_per_unit;

    public DurabilityFuelStrategy(double fuel_per_unit) {
        this.fuel_per_unit = fuel_per_unit;
    }

    @Override
    double generate(double fuel_cost, StructureContext structureContext) {
        Structure structure = structureContext.getStructure();
        if (!(structure instanceof ItemVirtualStructure)) return 0;

        ItemStack item = ((ItemVirtualStructure) structure).item;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) return 0;

        int maxDurability = item.getType().getMaxDurability();
        int damage = ((Damageable) meta).getDamage();
        int used = (int) Math.ceil(Math.min(maxDurability - damage, fuel_cost / fuel_per_unit));
        ((Damageable) meta).setDamage(damage + used);
        item.setItemMeta(meta);

        return used * fuel_per_unit;
    }

    @Override
    public String name() {
        return "durability";
    }

    @Override
    public String toString() {
        return String.format("DurabilityFuelStrategy { %s fuel per durability point }", this.fuel_per_unit);
    }
}
