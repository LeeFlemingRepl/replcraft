package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.Client;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.json.JSONObject;

import java.util.Collection;

    
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
    public FuelCost cost() {
        return FuelCost.BlockChange;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public void execute(Client client, WsMessageContext ctx, JSONObject request, JSONObject response) throws ApiError {
        try {
            String blockDataString = request.getString("blockData");
            ApiUtil.validateBlockData(blockDataString);

            Inventory source = !request.isNull("source_x")
                    ? ApiUtil.getContainer(ApiUtil.getBlock(client, request, "source_x", "source_y", "source_z"), "source")
                    : null;
            Inventory destination = !request.isNull("target_x")
                    ? ApiUtil.getContainer(ApiUtil.getBlock(client, request, "target_x", "target_y", "target_z"), "destination")
                    : null;

            BlockData blockData = ReplCraft.plugin.getServer().createBlockData(blockDataString);
            Material material = ApiUtil.remapBlockMaterialToItemMaterial(blockData.getMaterial());

            if (material != Material.AIR && !ReplCraft.plugin.creative_mode) {
                ItemStack stack = null;
                if (source != null) {
                    int i = source.first(material);
                    if (i != -1) stack = source.getItem(i);
                } else {
                    stack = client.getStructure().findMaterial(material);
                }
                if (stack == null) {
                    String message = "No " + material + " available in any attached chests.";
                    throw new ApiError("invalid operation", message);
                }
                stack.setAmount(stack.getAmount() - 1);
            }

            Block target = ApiUtil.getBlock(client, request);
            ApiUtil.checkProtectionPlugins(client.getStructure().minecraft_uuid, target.getLocation());

            if (ReplCraft.plugin.block_protection) {
                // Simulate breaking the block to see if GriefPrevention et al. would deny it
                OfflinePlayer offlinePlayer = client.getStructure().getPlayer();
                if (!(offlinePlayer instanceof Player)) {
                    throw ApiError.OFFLINE;
                }
                BlockBreakEvent evt = new BlockBreakEvent(target, (Player) offlinePlayer);
                Bukkit.getPluginManager().callEvent(evt);
                if (evt.isCancelled()) {
                    throw new ApiError("bad request", "block break event was cancelled by another plugin");
                }
            }

            Location location = target.getLocation();
            Collection<ItemStack> drops = target.getDrops();
            BlockState state = target.getState();
            if (state instanceof Container) {
                for (ItemStack stack: ((Container) state).getInventory().getContents()) {
                    if (stack == null) continue;
                    drops.add(stack.clone());
                    stack.setAmount(0);
                }
            }

            target.setBlockData(blockData);
            if (ReplCraft.plugin.core_protect) {
                String player = client.getStructure().getPlayer().getName();
                ReplCraft.plugin.coreProtect.logPlacement(player + " [API]", target.getLocation(), material, blockData);
            }
            target.getState().update(false, true);

            for (ItemStack drop: drops) {
                ItemStack leftover = destination != null
                        ? destination.addItem(drop).values().stream().findFirst().orElse(null)
                        : client.getStructure().deposit(drop);
                if (leftover != null) target.getWorld().dropItemNaturally(location, leftover);
            }
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            throw new ApiError("bad request", ex.toString());
        }
    }
}
