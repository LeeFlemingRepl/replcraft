package eelfloat.replcraft.listeners;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.StructureMaterial;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.net.ClientV2;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Stream;

public class ToolListeners implements Listener {
    private void trigger(ItemStack stack, int x, int y, int z, int range) {
        ItemMeta itemMeta = stack.getItemMeta();
        if (itemMeta == null) return;

        List<String> lore = itemMeta.getLore();
        if (lore == null) return;

        lore.stream()
            .filter(str -> str.startsWith("Replized: "))
            .flatMap(str -> {
                return ReplCraft.plugin.websocketServer.clients.values().stream()
                    .filter(client -> client instanceof ClientV2)
                    .flatMap(client -> ((ClientV2) client).items.stream())
                    .filter(item -> str.endsWith(item.getItemID()));
            })
            .forEach(itemCtx -> {
                Structure structure = new Structure(
                    StructureMaterial.META_MATERIAL,
                    null,
                    null,
                    itemCtx.getUsername(),
                    itemCtx.getUUID(),
                    null,
                    null,
                    null,
                    x - range,
                    y - range,
                    z - range,
                    x + range,
                    y + range,
                    z + range
                );
                itemCtx.client.createContext(structure, null, "itemUse");
            });
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent evt) {
        ItemStack stack = evt.getPlayer().getInventory().getItemInMainHand();
        Block block = evt.getBlock();
        trigger(stack, block.getX(), block.getY(), block.getZ(), 5);
    }
}
