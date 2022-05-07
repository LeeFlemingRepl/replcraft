package eelfloat.replcraft.listeners;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.Client;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;

public class StructureUpdates implements Listener {
    /**
     * Notifies all clients that a block changed. (Events are filtered in Client#notifyBlockChange)
     * @param cause a name for the cause of the event
     * @param oldBlockData the block data before the change
     * @param location the location of the change
     * @param reverify whether the client should check if reverification is necessary. Avoid for high volume events.
     */
    private void notifyBlockChange(String cause, BlockData oldBlockData, Location location, boolean reverify) {
        ReplCraft.plugin.getServer().getScheduler().runTask(ReplCraft.plugin, () -> {
            for (Client client: ReplCraft.plugin.websocketServer.clients.values()) {
                client.notifyBlockChange(location, cause, oldBlockData, location.getBlock().getBlockData());
                if (reverify) client.notifyChangeAndRevalidateStructureAt(location);
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
}
