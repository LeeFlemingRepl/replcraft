package eelfloat.replcraft;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;

public class ItemVirtualStructure extends Structure {
    public final Player player;
    public final ItemStack item;

    public ItemVirtualStructure(
        Player player,
        ItemStack item,
        StructureMaterial material,
        String replit_username, String replit_repl_id, String minecraft_username, UUID minecraft_uuid,
        World world, int center_x, int center_y, int center_z
    ) {
        super(
            material,
            replit_username, replit_repl_id, minecraft_username, minecraft_uuid,
            world,
            center_x - getEnchantStructureSize(item),
            center_y - getEnchantStructureSize(item),
            center_z - getEnchantStructureSize(item),
            center_x + getEnchantStructureSize(item),
            center_y + getEnchantStructureSize(item),
            center_z + getEnchantStructureSize(item)
        );
        this.player = player;
        this.item = item;
    }

    public double getEnchantFuelGenerationMultiplier() {
        ItemMeta itemMeta = this.item.getItemMeta();
        if (itemMeta == null) return 1;
        return 1 + itemMeta.getEnchantLevel(Enchantment.DURABILITY);
    }

    public double getEnchantFuelPerTickMultiplier() {
        ItemMeta itemMeta = this.item.getItemMeta();
        if (itemMeta == null) return 1;
        return 1.0 - (itemMeta.getEnchantLevel(Enchantment.DIG_SPEED)/5.0 * 0.8); // 16% per level
    }

    /**
     * @param item the item with enchants to calculate from
     * @return the side half-length of the structure
     */
    private static int getEnchantStructureSize(ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return 2;
        return 2 + Math.max(
            itemMeta.getEnchantLevel(Enchantment.LOOT_BONUS_BLOCKS),
            itemMeta.getEnchantLevel(Enchantment.LOOT_BONUS_MOBS)
        );
    }

    @Override
    public Iterator<Inventory> getStructureInventory() {
        return Collections.singleton((Inventory) player.getInventory()).iterator();
    }

    @Override
    public Location getPrimaryLocation() {
        return this.player.getLocation();
    }
}
