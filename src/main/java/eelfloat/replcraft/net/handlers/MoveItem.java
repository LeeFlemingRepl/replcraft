package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.ApiUtil;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.util.VirtualInventory;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;


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
    public double cost(RequestContext ctx) {
        return FuelCost.Expensive.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        VirtualInventory source = ApiUtil.getInventory(ctx, s -> "source_" + s, false);
        VirtualInventory target = ApiUtil.getInventory(ctx, s -> "target_" + s, false);
        int index = ctx.request.getInt("index");
        int amount = ctx.request.isNull("amount") ? 0 : ctx.request.getInt("amount");
        int targetIndex = ctx.request.isNull("target_index") ? -1 : ctx.request.getInt("target_index");
        source.checkProtectionPlugins(ctx);
        target.checkProtectionPlugins(ctx);

        VirtualInventory.Slot sourceItemSlot = source.getSlot(index);
        ItemStack item = sourceItemSlot.get();

        Optional<VirtualInventory.Slot> targetItemSlot = targetIndex != -1
                ? Optional.ofNullable(source.getSlot(targetIndex))
                : Optional.empty();

        if (amount == 0) {
            amount = item.getAmount();
        }
        if (amount > item.getAmount()) {
            throw new ApiError(ApiError.INVALID_OPERATION, "tried to move more items than there are");
        }

        ItemStack moved = item.clone();
        moved.setAmount(amount);
        if (ReplCraft.plugin.core_protect) {
            String name = ctx.structureContext.getStructure().getPlayer().getName() + " [API]";
            sourceItemSlot.getInventory().container.ifPresent(container -> {
                ReplCraft.plugin.coreProtect.logContainerTransaction(name, container.getLocation());
            });
            targetItemSlot.flatMap(slot -> slot.getInventory().container).ifPresent(container -> {
                ReplCraft.plugin.coreProtect.logContainerTransaction(name, container.getLocation());
            });
        }

        ArrayList<ItemStack> leftover = new ArrayList<>();
        int finalAmount = amount;

        if (!targetItemSlot.isPresent()) {
            leftover.addAll(Arrays.asList(target.deposit(moved)));
            item.setAmount(item.getAmount() - amount);
        } else {
            VirtualInventory.Slot slot = targetItemSlot.get();
            ItemStack existingItem = slot.get();
            if (existingItem == null) {
                slot.set(moved);
                item.setAmount(item.getAmount() - finalAmount);
                ReplCraft.plugin.logger.info("no existing item");
            } else {
                if (!existingItem.isSimilar(moved)) {
                    throw new ApiError(
                        ApiError.INVALID_OPERATION,
                        "failed to move item: item exists in target slot and is a different type"
                    );
                }
                int mergedAmount = existingItem.getAmount() + moved.getAmount();
                int mergedAmountCapped = Math.min(mergedAmount, existingItem.getMaxStackSize());
                int unmergableAmount = Math.max(mergedAmount - mergedAmountCapped, 0);
                existingItem.setAmount(Math.min(mergedAmount, existingItem.getMaxStackSize()));
                moved.setAmount(unmergableAmount);
                item.setAmount(item.getAmount() - finalAmount);
                if (unmergableAmount > 0) leftover.add(moved);
            }
        }

        if (!leftover.isEmpty()) {
            for (ItemStack value: leftover) {
                ItemStack[] failed = source.deposit(value);
                Location location = sourceItemSlot.getInventory().getLocation();
                for (ItemStack item1 : failed)
                    location.getWorld().dropItemNaturally(location, item1);
            }
            throw new ApiError(ApiError.INVALID_OPERATION, "failed to move all items");
        }
        return null;
    }
}
