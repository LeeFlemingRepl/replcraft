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
        int requestedMoveAmount = ctx.request.isNull("amount") ? 0 : ctx.request.getInt("amount");
        int targetIndex = ctx.request.isNull("target_index") ? -1 : ctx.request.getInt("target_index");
        source.checkProtectionPlugins(ctx);
        target.checkProtectionPlugins(ctx);

        VirtualInventory.Slot sourceItemSlot = source.getSlot(index);
        ItemStack sourceItem = sourceItemSlot.get();

        Optional<VirtualInventory.Slot> targetItemSlotOpt = targetIndex != -1
                ? Optional.ofNullable(target.getSlot(targetIndex))
                : Optional.empty();

        if (sourceItem == null) {
            throw new ApiError(ApiError.INVALID_OPERATION, "no item in slot");
        }
        final int moveAmount = requestedMoveAmount > 0 ? requestedMoveAmount : sourceItem.getAmount();
        if (moveAmount > sourceItem.getAmount()) {
            throw new ApiError(ApiError.INVALID_OPERATION, "tried to move more items than are present");
        }

        if (ReplCraft.plugin.core_protect) {
            String name = ctx.structureContext.getStructure().getPlayer().getName() + " [API]";
            sourceItemSlot.getInventory().container.ifPresent(container -> {
                ReplCraft.plugin.coreProtect.logContainerTransaction(name, container.getLocation());
            });
            targetItemSlotOpt.flatMap(slot -> slot.getInventory().container).ifPresent(container -> {
                ReplCraft.plugin.coreProtect.logContainerTransaction(name, container.getLocation());
            });
        }

        if (targetItemSlotOpt.isEmpty()) {
            ItemStack newStack = sourceItem.clone();
            newStack.setAmount(moveAmount);
            ItemStack[] leftover = target.deposit(newStack);

            int unmoved = 0;
            // this branch should always be entered since we're working with single items,
            // but might as well add the guards just in case.
            if (leftover.length == 1 && sourceItem.isSimilar(leftover[0])) {
                unmoved += leftover[0].getAmount();
                leftover[0].setAmount(0);
            }
            sourceItem.setAmount(sourceItem.getAmount() - moveAmount + unmoved);

            for (ItemStack value: leftover) {
                ItemStack[] failed = source.deposit(value);
                Location location = sourceItemSlot.getInventory().getLocation();
                for (ItemStack item1 : failed)
                    location.getWorld().dropItemNaturally(location, item1);
            }

            if (leftover.length > 0)
                throw new ApiError(ApiError.INVALID_OPERATION, "failed to move all items");
        } else {
            VirtualInventory.Slot targetItemSlot = targetItemSlotOpt.get();
            ItemStack existingTargetItem = targetItemSlot.get();

            if (existingTargetItem == null) {
                ItemStack newStack = sourceItem.clone();
                newStack.setAmount(moveAmount);
                targetItemSlot.set(newStack);
                sourceItem.setAmount(sourceItem.getAmount() - moveAmount);
            } else {
                if (!existingTargetItem.isSimilar(sourceItem)) {
                    throw new ApiError(
                        ApiError.INVALID_OPERATION,
                        "failed to move item: item exists in target slot and is a different type"
                    );
                }

                int capacityLeft = existingTargetItem.getMaxStackSize() - existingTargetItem.getAmount();
                if (moveAmount > capacityLeft) {
                    throw new ApiError(
                        ApiError.INVALID_OPERATION,
                        "failed to move item: target slot can't hold the requested amount"
                    );
                }

                existingTargetItem.setAmount(existingTargetItem.getAmount() + moveAmount);
                sourceItem.setAmount(sourceItem.getAmount() - moveAmount);
            }
        }

        return null;
    }
}
