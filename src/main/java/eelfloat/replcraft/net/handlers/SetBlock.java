package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.PhysicalStructure;
import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.util.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;


public class SetBlock implements WebsocketActionHandler {
    @Override
    public String route() {
        return "set_block";
    }

    @Override
    public String permission() {
        return "replcraft.api.set_block";
    }

    @Override
    public double cost(RequestContext ctx) {
        double base = FuelCost.BlockChange.toDouble();
        Structure structure = ctx.structureContext.getStructure();
        int chests = structure instanceof PhysicalStructure
            ? ((PhysicalStructure) structure).chests.size()
            : 1;
        int minChests = ReplCraft.plugin.fuel_cost_per_structure_inventory_start;
        double perChest = ReplCraft.plugin.fuel_cost_per_structure_inventory;
        return base + Math.max(chests - minChests, 0) * perChest;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        try {
            String blockDataString = ctx.request.getString("blockData");
            ApiUtil.validateBlockData(blockDataString);

            VirtualInventory source = ApiUtil.getInventory(ctx, s -> "source_" + s, true);
            VirtualInventory target = ApiUtil.getInventory(ctx, s -> "target_" + s, true);

            BlockData blockData = ReplCraft.plugin.getServer().createBlockData(blockDataString);
            Material material = ApiUtil.remapBlockMaterialToItemMaterial(blockData.getMaterial());

            if (material != Material.AIR && !ReplCraft.plugin.creative_mode) {
                Optional<ItemStack> stack = source.stream()
                    .filter(item -> item.getType() == material && item.getAmount() >= 1)
                    .findFirst();
                if (!stack.isPresent()) {
                    String message = "No " + material + " available in any attached chests.";
                    throw new ApiError(ApiError.INVALID_OPERATION, message);
                }
                stack.get().setAmount(stack.get().getAmount() - 1);
            }

            Block targetBlock = ApiUtil.getBlock(ctx.structureContext, ctx.request);
            ApiUtil.checkProtectionPlugins(ctx.structureContext.getStructure().minecraft_uuid, targetBlock.getLocation());

            if (ReplCraft.plugin.block_protection) {
                // Simulate breaking the block to see if GriefPrevention et al. would deny it
                OfflinePlayer offlinePlayer = ctx.structureContext.getStructure().getPlayer();
                if (!(offlinePlayer instanceof Player)) {
                    throw ApiError.OFFLINE;
                }
                BlockBreakEvent evt = new BlockBreakEvent(targetBlock, (Player) offlinePlayer);
                Bukkit.getPluginManager().callEvent(evt);
                if (evt.isCancelled()) {
                    throw new ApiError(ApiError.BAD_REQUEST, "block break event was cancelled by another plugin");
                }
            }

            if (targetBlock.getType().getHardness() == Material.BEDROCK.getHardness()) {
                throw new ApiError(ApiError.INVALID_OPERATION, "cannot break unbreakable block");
            }

            Location location = targetBlock.getLocation();
            Collection<ItemStack> drops = targetBlock.getDrops();
            BlockState state = targetBlock.getState();
            if (state instanceof Container) {
                for (ItemStack stack: ((Container) state).getInventory().getContents()) {
                    if (stack == null) continue;
                    drops.add(stack.clone());
                    stack.setAmount(0);
                }
            }

            state.setBlockData(blockData);
            state.update(true, true);

            // Force physics updates around this block, to prevent floating-air farms
            for (BlockFace face: BlockFace.values()) {
                BlockState nState = targetBlock.getRelative(face).getState();
                if (nState.getType() == Material.AIR) {
                    nState.setType(Material.COBBLESTONE);
                    nState.update(true, true);
                    nState.setType(Material.AIR);
                    nState.update(true, true);
                }
            }

            if (ReplCraft.plugin.core_protect) {
                String player = ctx.structureContext.getStructure().getPlayer().getName();
                ReplCraft.plugin.coreProtect.logPlacement(player + " [API]", targetBlock.getLocation(), material, blockData);
            }

            ItemStack[] leftovers = target.deposit(drops.toArray(new ItemStack[0]));
            for (ItemStack leftover: leftovers)
                targetBlock.getWorld().dropItemNaturally(location, leftover);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            throw new ApiError(ApiError.BAD_REQUEST, ex.toString());
        }
        return null;
    }
}
