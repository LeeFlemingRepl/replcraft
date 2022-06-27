package eelfloat.replcraft.util;

import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.Optional;

public class InventoryReference {
    public final Inventory inventory;
    public final Optional<Container> container;
    public final Optional<Player> player;

    public InventoryReference(Player player) {
        this.inventory = player.getInventory();
        this.container = Optional.empty();
        this.player = Optional.of(player);
    }

    public InventoryReference(Container container) {
        this.inventory = container.getInventory();
        this.container = Optional.of(container);
        this.player = Optional.empty();
    }

    public Location getLocation() {
        if (this.container.isPresent()) {
            return this.container.get().getLocation();
        } else if (this.player.isPresent()) {
            return this.player.get().getLocation();
        } else {
            throw new RuntimeException("Unreachable");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        InventoryReference otherRef = (InventoryReference) other;
        if (this.player.isPresent() && otherRef.player.isPresent()) {
            return this.player.get().getUniqueId().equals(otherRef.player.get().getUniqueId());
        }
        if (this.container.isPresent() && otherRef.container.isPresent()) {
            return this.getLocation().equals(((InventoryReference) other).getLocation());
        }
        return false;
    }
}