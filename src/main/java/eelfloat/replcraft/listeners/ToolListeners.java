package eelfloat.replcraft.listeners;

import eelfloat.replcraft.*;
import eelfloat.replcraft.net.ClientV2;
import eelfloat.replcraft.net.StructureContext;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.util.ReplizedTool;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ToolListeners implements Listener {
    private int trigger(Player player, ReplizedTool tool, int x, int y, int z, String reason) {
        AtomicInteger triggers = new AtomicInteger();
        tool.lore.stream()
            .filter(str -> str.startsWith("Replized: "))
            .flatMap(str -> {
                return ReplCraft.plugin.websocketServer.clients.values().stream()
                    .filter(client -> client instanceof ClientV2)
                    .flatMap(client -> ((ClientV2) client).items.stream())
                    .filter(item -> {
                        ReplCraft.plugin.logger.info(String.format("Checking %s against %s", str, item.getItemID()));
                        return str.contains(item.getItemID());
                    });
            })
            .forEach(itemCtx -> {
                Optional<StructureMaterial> material = ReplCraft.plugin.frame_materials.stream()
                    .filter(mats -> (
                        mats.validMaterials.stream().anyMatch(mat -> tool.item.getType() == mat) &&
                        mats.type == StructureType.Item
                    ))
                    .findFirst();
                if (!material.isPresent()) return;
                Structure structure = new ItemVirtualStructure(
                    player,
                    tool.item,
                    material.get(),
                    null,
                    null,
                    itemCtx.getUsername(),
                    itemCtx.getUUID(),
                    player.getWorld(),
                    x,
                    y,
                    z
                );
                ReplCraft.plugin.logger.info(String.format(
                    "Creating tool context %s %s %s %s %s %s %s %s",
                    player.getName(), tool.item, x, y, z, 5, reason, tool.lore
                ));
                StructureContext context = itemCtx.client.createContext(structure, null, reason);
                Bukkit.getScheduler().runTaskLater(ReplCraft.plugin, () -> {
                    itemCtx.client.disposeContext(context.id, "hit hard timeout threshold");
                }, 10 * 60 * 20);
                triggers.getAndIncrement();
            });
        return triggers.get();
    }

    @EventHandler
    public void onPlayerHoldReplizedItem(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        ReplizedTool tool = ReplizedTool.parse(item);
        if (tool != null && !tool.playerUUID.equals(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(String.format(
                "%sWarning: This is a replized tool owned by %s%s%s%s%s. It can interact with your " +
                "surroundings and inventory if used. Only use the tool if you trust its author. This " +
                "includes right-clicking blocks and in the air.",
                ChatColor.GOLD,
                ChatColor.RED,
                ChatColor.BOLD,
                Bukkit.getServer().getOfflinePlayer(tool.playerUUID).getName(),
                ChatColor.RESET,
                ChatColor.GOLD
            ));
        }
    }

    @EventHandler
    public void onItemMendEvent(PlayerItemMendEvent evt) {
        if (ReplizedTool.parse(evt.getItem()) == null || ReplCraft.plugin.replizePrice == 0) return;
        String world = evt.getExperienceOrb().getWorld().getName();
        double price = evt.getRepairAmount() * ReplCraft.plugin.replizePrice;
        EconomyResponse tx = ReplCraft.plugin.economy.withdrawPlayer(evt.getPlayer(), world, price);
        if (tx.transactionSuccess()) {
            evt.getPlayer().sendMessage(String.format(
                "%sYou have been charged %s$%s%s for replized tool mending",
                ChatColor.DARK_GREEN,
                ChatColor.GOLD,
                price,
                ChatColor.DARK_GREEN
            ));
        } else {
            evt.getPlayer().sendMessage(String.format(
                "%sInsufficient funds to pay for replized tool mending",
                ChatColor.RED
            ));
            evt.setCancelled(true);
        }
    }

    @EventHandler
    public void onAnvilEvent(PrepareAnvilEvent evt) {
        AnvilInventory inventory = evt.getInventory();
        if (ReplizedTool.parse(inventory.getItem(0)) == null)
            return;
        for (HumanEntity viewer: evt.getViewers())
            viewer.sendMessage("Replized tools cannot be modified normally in an anvil. `/dereplize` them first.");
        inventory.setMaximumRepairCost(0);
    }

    @EventHandler
    public void onPlayerDamageEntity(EntityDamageByEntityEvent evt) {
        if (!(evt.getDamager() instanceof Player)) return;
        Player player = (Player) evt.getDamager();
        ItemStack stack = player.getInventory().getItemInMainHand();
        Location location = evt.getEntity().getLocation();
        ReplizedTool tool = ReplizedTool.parse(stack);
        if (tool == null) return;
        trigger(player, tool, location.getBlockX(), location.getBlockY(), location.getBlockZ(), "itemAttack");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent evt) {
        Player player = evt.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        Block block = evt.getBlock();
        ReplizedTool tool = ReplizedTool.parse(stack);
        if (tool == null) return;
        int triggers = trigger(player, tool, block.getX(), block.getY(), block.getZ(), "itemBreakBlock");
        if (triggers > 0) evt.setCancelled(true);
    }


    HashMap<UUID, Long> lastTrigger = new HashMap<>();
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent evt) {
        Player player = evt.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        ReplizedTool tool = ReplizedTool.parse(stack);
        if (tool == null) return;
        evt.setCancelled(true);

        long now = System.currentTimeMillis();
        long last = lastTrigger.computeIfAbsent(player.getUniqueId(), key -> 0L);
        if (now < last + 200) return;
        lastTrigger.put(player.getUniqueId(), now);

        if (evt.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = evt.getClickedBlock();
            int triggers = trigger(player, tool, block.getX(), block.getY(), block.getZ(), "itemInteractBlock");
            if (triggers > 0) evt.setCancelled(true);
        }
        if (evt.getAction() == Action.RIGHT_CLICK_AIR) {
            Location location = player.getLocation();
            int triggers = trigger(player, tool, location.getBlockX(), location.getBlockY(), location.getBlockZ(), "itemInteractAir");
            if (triggers > 0) evt.setCancelled(true);
        }
    }
}
