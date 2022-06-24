package eelfloat.replcraft.net;

import eelfloat.replcraft.PhysicalStructure;
import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.handlers.WebsocketActionHandler;
import eelfloat.replcraft.strategies.*;
import eelfloat.replcraft.util.BoxedDoubleButActuallyUseful;
import eelfloat.replcraft.util.ExpirableCacheMap;
import eelfloat.replcraft.util.StructureUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.json.JSONObject;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class StructureContext {
    public final long id;
    private final Client client;

    /** The current structure this client is servicing requests for */
    private Structure structure;
    /** The client's authentication token */
    public String authentication;
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

    public List<FuelStrategy> strategies;
    /** A map of API routes to their associated ratetracker */
    public HashMap<String, RateTracker> rateTrackers = new HashMap<>();

    public StructureContext(long id, Client client, Structure structure, String authenticationToken) {
        this.id = id;
        this.client = client;
        this.authentication = authenticationToken;
        this.setStructure(structure);
        this.strategies = this.structure.material.strategies.stream()
            .map(strat -> strat.apply(this))
            .collect(Collectors.toList());
    }

    /**
     * Sets the context's structure, handling chunk loading and unloading as necessary.
     * @param structure the new structure to set, or null
     */
    public void setStructure(Structure structure) {
        if (this.structure != null)
            this.structure.unchunkLoad();
        this.structure = structure;
        if (this.structure != null)
            this.structure.chunkLoad();
    }

    public RateTracker tracker(WebsocketActionHandler handler) {
        if (!this.rateTrackers.containsKey(handler.route()))
            this.rateTrackers.put(handler.route(), new RateTracker());
        return this.rateTrackers.get(handler.route());
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
        if (strategies.stream().allMatch(FuelStrategy::isHidden))
            return true; // no enabled strategies

        final double tolerance = 0.01;
        if (this.structure.material.consumeFromAll) {
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

    public enum QueryStatus { Success, TimedOut }
    public ExpirableCacheMap<Long, BiFunction<QueryStatus, JSONObject, ApiError>> queryCallbacks = new ExpirableCacheMap<>(
    10 * 1000,
        (key, value) -> value.apply(QueryStatus.TimedOut, null)
    );
    private long nonceIncr;
    public void sendQuery(JSONObject json, BiFunction<QueryStatus, JSONObject, ApiError> callback) {
        long nonce = nonceIncr++;
        json.put("queryNonce", nonce);
        this.client.send(this, json);
        queryCallbacks.set(nonce, callback);
    }
    public void expireQueries() {
        queryCallbacks.expire();
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
            json.put("type", "block update");
            // Backwards compat note: "event: ..." is only used for block events
            // Use type for everything else, going forwards.
            json.put("event", "block update");
            json.put("cause", cause);
            json.put("x", location.getBlockX() - structure.inner_min_x());
            json.put("y", location.getBlockY() - structure.inner_min_y());
            json.put("z", location.getBlockZ() - structure.inner_min_z());
            json.put("block", newBlockData.getAsString());
            json.put("old_block", oldBlockData.getAsString());
            this.client.send(this, json);
        }
    }

    /** Forces the client to re-verify its structure if the given location is adjacent to any of its structural blocks */
    public void notifyChangeAndRevalidateStructureAt(Location location) {
        if (this.authentication == null) return;

        HashSet<Location> locations = this.structure == null
            ? this.invalidated_structure_locations
            : ((PhysicalStructure) this.structure).frameBlocks;

        for (BlockFace face: BlockFace.values()) {
            if (!face.isCartesian()) continue;
            if (locations.contains(location.getBlock().getRelative(face).getLocation())) {
                ReplCraft.plugin.logger.info(String.format(
                    "Revalidating structure %s due to block change at %s",
                    this.structure, location
                ));
                StructureUtil.verifyTokenAsync(
                    this.authentication,
                    structure -> {
                        setStructure(structure);
                        this.invalidated = false;
                    },
                    err -> {
                        ReplCraft.plugin.logger.info(String.format("Revalidation failed: %s", err));
                        if (this.client instanceof ClientV2) {
                            // v2 clients just kill the context, the user will have to make a new one
                            ((ClientV2) this.client).disposeContext(this.id);
                        } else {
                            // v1 clients can revalidate old contexts
                            if (this.structure instanceof PhysicalStructure) {
                                this.invalidated_structure_locations = ((PhysicalStructure) this.structure).frameBlocks;
                                this.setStructure(null);
                            }
                            this.invalidated = true;
                        }
                    }
                );
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

            this.setStructure(null);
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
