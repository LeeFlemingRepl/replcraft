package eelfloat.replcraft.net;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.strategies.*;
import eelfloat.replcraft.util.BoxedDoubleButActuallyUseful;
import eelfloat.replcraft.util.StructureUtil;
import eelfloat.replcraft.exceptions.InvalidStructure;
import io.javalin.websocket.WsContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class Client {
    private final WsContext ctx;

    /** The client's authentication token */
    public String authentication = null;
    /** The current structure this client is servicing requests for */
    private Structure structure = null;
    /** If this client has been previously valid but was then invalidated */
    private boolean invalidated = false;
    /** The locations from the last valid structure */
    private HashSet<Location> invalidated_structure_locations = new HashSet<>();

    /** A set of blocks the client is watching for spigot event based changes */
    private final HashSet<Location> watchedBlocks = new HashSet<>();
    /** The last known state of each polled block */
    private final HashMap<Location, BlockData> polledBlockStates = new HashMap<>();
    /** A set of blocks the client is watching for poll based event changes */
    private final List<Location> polledBlocks = new ArrayList<>();
    /** The current index in polledBlocks */
    private int pollingPosition = 0;

    private final List<FuelStrategy> strategies = new ArrayList<>();

    public Client(WsContext ctx) {
        this.ctx = ctx;
    }

    /** @return a human readable list of fuel sources */
    public List<String> getFuelSources() {
        return this.strategies.stream()
            .filter(strat -> !strat.isHidden())
            .map(Object::toString)
            .collect(Collectors.toList());
    }

    /**
     * Consumes a given amount of fuel from the available strategies.
     * @param amount the amount of fuel to consume
     * @return if the fuel consumption was successful
     */
    public boolean useFuel(double amount) {
        if (strategies.isEmpty()) return true; // no enabled strategies

        final double tolerance = 0.01;
        if (ReplCraft.plugin.consume_from_all) {
            double finalAmount = amount;
            if (!strategies.stream().allMatch(strat -> Math.abs(strat.consume(finalAmount, this) - finalAmount) < tolerance)) {
                strategies.forEach(FuelStrategy::cancel_and_restore);
                return false;
            }
        } else {
            for (FuelStrategy strategy: strategies) {
                amount -= strategy.consume(amount, this);
            }
            if (amount > tolerance) {
                strategies.forEach(FuelStrategy::cancel_and_restore);
                return false;
            }
        }
        strategies.forEach(FuelStrategy::commit);
        return true;
    }

    /** Sends a message over the client's websocket connection */
    public void send(JSONObject json) {
        ctx.send(json.toString());
    }

    /** Polls a single block, notifying the client if it changed. */
    public void pollOne() {
        if (this.structure == null) return;
        if (polledBlocks.isEmpty()) return;
        pollingPosition = (pollingPosition + 1) % polledBlocks.size();

        Location location = polledBlocks.get(pollingPosition);
        BlockData newBlockData = location.getBlock().getBlockData();
        if (!newBlockData.equals(polledBlockStates.get(location))) {
            BlockData oldBlockData = polledBlockStates.put(location, newBlockData);
            notifyBlockChange(location, "poll", oldBlockData, newBlockData);
        }
    }

    /** Notifies a client of a block change if it's watching for it */
    public void notifyBlockChange(Location location, String cause, BlockData oldBlockData, BlockData newBlockData) {
        if (this.structure == null) return;

        if (watchedBlocks.contains(location) || cause.equals("poll")) {
            JSONObject json = new JSONObject();
            json.put("event", "block update");
            json.put("cause", cause);
            json.put("x", location.getBlockX() - structure.inner_min_x());
            json.put("y", location.getBlockY() - structure.inner_min_y());
            json.put("z", location.getBlockZ() - structure.inner_min_z());
            json.put("block", newBlockData.getAsString());
            json.put("old_block", oldBlockData.getAsString());
            this.send(json);
        }
    }

    /** Forces the client to re-verify its structure if the given location is adjacent to any of its structural blocks */
    public void notifyChangeAndRevalidateStructureAt(Location location) {
        HashSet<Location> locations = this.structure == null
            ? this.invalidated_structure_locations
            : this.structure.frameBlocks;

        for (BlockFace face: BlockFace.values()) {
            if (!face.isCartesian()) continue;
            if (locations.contains(location.getBlock().getRelative(face).getLocation())) {
                try {
                    ReplCraft.plugin.logger.info(String.format(
                        "Revalidating structure %s due to block change at %s",
                        this.structure, location
                    ));
                    this.structure = StructureUtil.verifyToken(this.authentication);
                    this.invalidated = false;
                    return;
                } catch (InvalidStructure e) {
                    ReplCraft.plugin.logger.info(String.format("Revalidation failed: %s", e));
                    if (this.structure != null) {
                        this.invalidated_structure_locations = this.structure.frameBlocks;
                        this.structure = null;
                    }
                    this.invalidated = true;
                }
            }
        }
    }

    /**
     * Adds a block to the watch list
     * Watched blocks notify the client immediately, but aren't always reliable.
     */
    public void watch(Block block) {
        watchedBlocks.add(block.getLocation());
    }

    /** Removes a block from the watch list */
    public void unwatch(Block block) {
        watchedBlocks.remove(block.getLocation());
    }

    /**
     * Adds a block to the poll list
     * Polled blocks are reliable, but have a limited poll rate of one block per tick.
     * Polling more blocks reduces the update rate per block.
     */
    public void poll(Block block) {
        polledBlockStates.put(block.getLocation(), block.getBlockData());
        polledBlocks.add(block.getLocation());
        notifyBlockChange(block.getLocation(), "poll", block.getBlockData(), block.getBlockData());
    }

    /** Removes a block from the poll list */
    public void unpoll(Block block) {
        polledBlockStates.remove(block.getLocation());
        polledBlocks.remove(block.getLocation());
    }

    /** Sets the client's structure and re-authentication string */
    public void setStructure(Structure structure, String authentication) {
        if (this.structure != null) this.structure.unchunkLoad();
        this.authentication = authentication;
        this.structure = structure;
        this.strategies.addAll(
            ReplCraft.plugin.strategies.stream()
                .map(strat -> strat.apply(this))
                .collect(Collectors.toList())
        );
        this.structure.chunkLoad();
    }

    /** Disposes of the client */
    public void dispose() {
        if (this.structure != null) {
            for (FuelStrategy strategy: this.strategies) {
                if (strategy instanceof ItemFuelStrategy || strategy instanceof EconomyFuelStrategy) {
                    BoxedDoubleButActuallyUseful leftover = ReplCraft.plugin.leftOverFuel.get(
                        this.structure,
                        () -> new BoxedDoubleButActuallyUseful(0.0)
                    );
                    leftover.value += strategy.getSpareFuel();
                }
            }

            this.structure.unchunkLoad();
            this.structure = null;
        }
    }

    public Structure getStructure() {
        return structure;
    }

    /** Marks or unmarks all blocks in the client's structure for watching */
    public void setWatchAll(boolean watchAll) {
        this.watchedBlocks.clear();
        if (watchAll) {
            for (int x = 0; x < structure.inner_size_x(); x++) {
                for (int y = 0; y < structure.inner_size_y(); y++) {
                    for (int z = 0; z < structure.inner_size_z(); z++) {
                        this.watch(structure.getBlock(x, y, z));
                    }
                }
            }
        }
    }

    /** Marks or unmarks all blocks in the client's structure for polling */
    public void setPollAll(boolean pollAll) {
        polledBlockStates.clear();
        polledBlocks.clear();
        if (pollAll) {
            for (int x = 0; x < structure.inner_size_x(); x++) {
                for (int y = 0; y < structure.inner_size_y(); y++) {
                    for (int z = 0; z < structure.inner_size_z(); z++) {
                        this.poll(structure.getBlock(x, y, z));
                    }
                }
            }
        }
    }

    /** If this client was invalidated due to a block change in the world */
    public boolean isInvalidated() {
        return invalidated;
    }
}
