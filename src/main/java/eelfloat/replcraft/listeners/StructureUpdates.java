package eelfloat.replcraft.listeners;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.net.StructureContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import static eelfloat.replcraft.ReplCraft.plugin;

public class StructureUpdates implements Listener {
    /**
     * Notifies all clients that a block changed. (Events are filtered in Client#notifyBlockChange)
     * @param cause a name for the cause of the event
     * @param oldBlockData the block data before the change
     * @param location the location of the change
     * @param reverify whether the client should attempt structure re-verification. Avoid for high volume events.
     */
    private void notifyBlockChange(String cause, BlockData oldBlockData, Location location, boolean reverify) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Client client: plugin.websocketServer.clients.values()) {
                for (StructureContext ctx: client.getContexts()) {
                    ctx.notifyBlockChange(location, cause, oldBlockData, location.getBlock().getBlockData());
                    if (reverify) ctx.notifyChangeAndRevalidateStructureAt(location);
                }
            }
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBurnEvent(BlockBurnEvent event) {
        notifyBlockChange("burn", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakEvent(BlockBreakEvent event) {
        notifyBlockChange("break", event.getBlock().getBlockData(), event.getBlock().getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplodeEvent(BlockExplodeEvent event) {
        notifyBlockChange("explode", event.getBlock().getBlockData(), event.getBlock().getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFadeEvent(BlockFadeEvent event) {
        notifyBlockChange("fade", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockGrow(BlockGrowEvent event) {
        notifyBlockChange("grow", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStructureGrowEvent(StructureGrowEvent event) {
        for (BlockState block: event.getBlocks()) {
            notifyBlockChange("structure_grow", block.getBlockData(), block.getLocation(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent event) {
        notifyBlockChange("ignite", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block: event.getBlocks()) {
            notifyBlockChange("piston_extend", block.getBlockData(), event.getBlock().getLocation(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block: event.getBlocks()) {
            notifyBlockChange("piston_retract", block.getBlockData(), event.getBlock().getLocation(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        notifyBlockChange("place", event.getBlockReplacedState().getBlockData(), event.getBlock().getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFluidLevelChange(FluidLevelChangeEvent event) {
        notifyBlockChange("fluid", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event) {
        notifyBlockChange("decay", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }
    
    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        notifyBlockChange("redstone", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceSmeltEvent event) {
        notifyBlockChange("smelt", event.getBlock().getBlockData(), event.getBlock().getLocation(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(InventoryPickupItemEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockInventoryHolder) {
            Block block = ((BlockInventoryHolder) holder).getBlock();
            notifyBlockChange("[dev]inventory-pickup", block.getBlockData(), block.getLocation(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (event.isCancelled()) return;

//        plugin.logger.info("onMoveItem " + event.toString());
//        plugin.logger.info("src " + event.getSource());
//        plugin.logger.info("dest " + event.getDestination());

        Location source = event.getSource().getLocation();
        if (source != null && event.getSource().getHolder() instanceof BlockInventoryHolder) {
            Block block = ((BlockInventoryHolder) event.getSource().getHolder()).getBlock();
            notifyBlockChange("[dev]inventory-move", block.getBlockData(), block.getLocation(), false);
        }

        Location destination = event.getDestination().getLocation();
        if (destination != null && event.getDestination().getHolder() instanceof BlockInventoryHolder) {
            Block block = ((BlockInventoryHolder) event.getDestination().getHolder()).getBlock();
            notifyBlockChange("[dev]inventory-move", block.getBlockData(), block.getLocation(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.NOTHING) return;

//        ReplCraft.plugin.logger.info(String.format(
//            "click event\n\t%s\n\t%s\n\t%s\n\t%s",
//            event.getClickedInventory(),
//            event.getClickedInventory().getHolder(),
//            event.getInventory(),
//            event.getInventory().getHolder()
//        ));

        Inventory clickedInventory = event.getClickedInventory();
        InventoryHolder holder = clickedInventory.getHolder();
        if (holder instanceof BlockInventoryHolder) {
            Block block = ((BlockInventoryHolder) holder).getBlock();
            notifyBlockChange("[dev]inventory-move", block.getBlockData(), block.getLocation(), false);
        }

        Inventory otherInventory = event.getInventory();
        InventoryHolder otherHolder = otherInventory.getHolder();
        if (otherInventory != clickedInventory && otherHolder instanceof BlockInventoryHolder) {
            Block block = ((BlockInventoryHolder) otherHolder).getBlock();
            notifyBlockChange("[dev]inventory-move", block.getBlockData(), block.getLocation(), false);
        }
    }
}
