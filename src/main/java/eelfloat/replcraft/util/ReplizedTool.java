package eelfloat.replcraft.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ReplizedTool {
    public final ItemStack item;
    public final ItemMeta itemMeta;
    public final List<String> lore;
    public final String replizeID;
    public final UUID playerUUID;

    private ReplizedTool(ItemStack item, ItemMeta itemMeta, List<String> lore, String replizeID, UUID playerUUID) {
        this.item = item;
        this.itemMeta = itemMeta;
        this.lore = lore;
        this.replizeID = replizeID;
        this.playerUUID = playerUUID;
    }

    /**
     * Dereplizes the tool
     * @return if the tool was successfully dereplized
     */
    public boolean dereplize() {
        boolean dereplized = lore.removeIf(e -> e.startsWith("Replized:"));
        if (!dereplized) return false;
        itemMeta.setLore(lore);
        item.setItemMeta(itemMeta);
        return true;
    }

    /**
     * Attempts to parse a replized item
     * @param item the item to parse
     * @return a ReplizedTool, or null if parsing failed
     */
    public static ReplizedTool parse(ItemStack item) {
        if (item == null) return null;
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return null;
        List<String> lore = itemMeta.getLore();
        if (lore == null) return null;

        Optional<String> first = lore.stream().filter(e -> e.startsWith("Replized:")).findFirst();
        if (!first.isPresent()) return null;
        String[] split = first.get().split(" ");
        return new ReplizedTool(item, itemMeta, lore, split[1], UUID.fromString(split[2]));
    }
}
