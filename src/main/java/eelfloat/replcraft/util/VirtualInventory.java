package eelfloat.replcraft.util;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class VirtualInventory {
    public InventoryReference[] inventories;

    public VirtualInventory(InventoryReference... ref) {
        this.inventories = ref;
    }

    public VirtualInventory(Stream<InventoryReference> refs) {
        inventories = refs.toArray(InventoryReference[]::new);
    }

    public void checkProtectionPlugins(RequestContext ctx) throws ApiError {
        UUID uuid = ctx.structureContext.getStructure().minecraft_uuid;
        for (InventoryReference ref: this.inventories) {
            if (ref.container.isPresent()) {
                ApiUtil.checkProtectionPlugins(uuid, ref.container.get().getLocation());
            }
        }
    }

    public ItemStack get(int i) throws ApiError {
        return this.getSlot(i).get();
    }

    public static class Slot {
        private final VirtualInventory virtualInventory;
        private final int inventory;
        private final int slot;

        public Slot(VirtualInventory virtualInventory, int inventory, int slot) {
            this.virtualInventory = virtualInventory;
            this.inventory = inventory;
            this.slot = slot;
        }

        public void checkProtectionPlugins(RequestContext ctx) throws ApiError {
            UUID uuid = ctx.structureContext.getStructure().minecraft_uuid;
            Optional<Container> container = this.getInventory().container;
            if (container.isPresent()) ApiUtil.checkProtectionPlugins(uuid, container.get().getLocation());
        }

        public InventoryReference getInventory() {
            return this.virtualInventory.inventories[inventory];
        }

        public ItemStack get() {
            return this.getInventory().inventory.getItem(slot);
        }

        public void set(ItemStack stack) {
            this.getInventory().inventory.setItem(slot, stack);
        }

        public int getIndex() {
            return slot;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || this.getClass() != other.getClass()) return false;
            Slot otherSlot = (Slot) other;
            return (
                this.slot == otherSlot.slot &&
                this.inventory == otherSlot.inventory &&
                this.getInventory() == otherSlot.getInventory()
            );
        }
    }

    public Slot getSlot(int slot) throws ApiError {
        if (slot < 0) {
            throw new ApiError(ApiError.INVALID_OPERATION, "inventory index out of bounds");
        }

        int inventory = 0;
        while (slot > inventories[inventory].inventory.getSize()) {
            inventory++;
            slot -= inventories[inventory].inventory.getSize();
            if (inventory >= inventories.length) {
                throw new ApiError(ApiError.INVALID_OPERATION, "inventory index out of bounds");
            }
        }

        return new Slot(this, inventory, slot);
    }

    public ItemStack[] deposit(ItemStack... stack) {
        for (InventoryReference inventory: this.inventories) {
            stack = inventory.inventory.addItem(stack).values().toArray(new ItemStack[0]);
            if (stack.length == 0) break;
        }
        return stack;
    }

    public Stream<ItemStack> stream() {
        return Arrays.stream(this.inventories).flatMap(inventory -> Arrays.stream(inventory.inventory.getContents()));
    }
}
