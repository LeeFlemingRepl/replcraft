package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;


public class MoveItem implements WebsocketActionHandler {
    @Override
    public String route() {
        return "move_item";
    }

    @Override
    public String permission() {
        return "replcraft.api.move_item";
    }

    @Override
    public FuelCost cost() {
        return FuelCost.Expensive;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        Block source = ApiUtil.getBlock(ctx.client, ctx.request, "source_x", "source_y", "source_z");
        Block target = ApiUtil.getBlock(ctx.client, ctx.request, "target_x", "target_y", "target_z");
        ApiUtil.checkProtectionPlugins(ctx.client.getStructure().minecraft_uuid, source.getLocation());
        ApiUtil.checkProtectionPlugins(ctx.client.getStructure().minecraft_uuid, target.getLocation());
        int index = ctx.request.getInt("index");
        int amount = ctx.request.isNull("amount") ? 0 : ctx.request.getInt("amount");
        int targetIndex = ctx.request.isNull("target_index") ? -1 : ctx.request.getInt("target_index");

        Inventory source_inventory = ApiUtil.getContainer(source, "source block");
        Inventory target_inventory = ApiUtil.getContainer(target, "target block");
        ItemStack item = ApiUtil.getItem(source, "source block", index);

        if (amount == 0) amount = item.getAmount();
        if (amount > item.getAmount()) {
            throw new ApiError("invalid operation", "tried to move more items than there are");
        }
        ItemStack moved = item.clone();
        moved.setAmount(amount);
        if (ReplCraft.plugin.core_protect) {
            String player = ctx.client.getStructure().getPlayer().getName();
            ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", source.getLocation());
            ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", target.getLocation());
        }

        ArrayList<ItemStack> leftover = new ArrayList<>();
        ReplCraft.plugin.logger.info(String.format("index %s targetIndex %s amount %s", index, targetIndex, amount));
        if (targetIndex == -1) {
            leftover.addAll(target_inventory.addItem(moved).values());
            ReplCraft.plugin.logger.info("moving all");
            item.setAmount(item.getAmount() - amount);
        } else {
            ItemStack existingItem = target_inventory.getItem(targetIndex);
            if (existingItem == null) {
                target_inventory.setItem(targetIndex, moved);
                item.setAmount(item.getAmount() - amount);
                ReplCraft.plugin.logger.info("no existing item");
            } else {
                if (!existingItem.isSimilar(moved)) {
                    throw new ApiError(
                        "invalid operation",
                        "failed to move item: item exists in target slot and is a different type"
                    );
                }
                int mergedAmount = existingItem.getAmount() + moved.getAmount();
                int mergedAmountCapped = Math.min(mergedAmount, existingItem.getMaxStackSize());
                int unmergableAmount = Math.max(mergedAmount - mergedAmountCapped, 0);
                existingItem.setAmount(Math.min(mergedAmount, existingItem.getMaxStackSize()));
                moved.setAmount(unmergableAmount);
                item.setAmount(item.getAmount() - amount);
                ReplCraft.plugin.logger.info(String.format("ma %s mac %s uma %s", mergedAmount, mergedAmountCapped, unmergableAmount));
                if (unmergableAmount > 0) leftover.add(moved);
            }
        }
        if (!leftover.isEmpty()) {
            for (ItemStack value: leftover) {
                source_inventory.addItem(value);
            }
            throw new ApiError("invalid operation", "failed to move all items");
        }
        return null;
    }
}
