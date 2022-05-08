package eelfloat.replcraft.net;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.Util;
import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.exceptions.InvalidStructure;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.BoundingBox;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebsocketServer {
    public final Map<WsContext, Client> clients = new ConcurrentHashMap<>();
    private final Javalin app;

    public WebsocketServer() {
        app = Javalin.create();
        app.get("/", ctx -> ctx.result("Hello World"));
        app.ws("/gateway", ws -> {
            ws.onConnect(ctx -> {
                ReplCraft.plugin.logger.info("Client " + ctx.session.getRemoteAddress() + " connected.");
                clients.put(ctx, new Client(ctx));
            });
            ws.onClose(ctx -> {
                ReplCraft.plugin.logger.info("Client " + ctx.session.getRemoteAddress() + " disconnected.");
                Bukkit.getScheduler().runTask(ReplCraft.plugin, clients.remove(ctx)::dispose);
            });
            ws.onMessage(this::onMessage);
        });
        app.start(ReplCraft.plugin.listen_address, ReplCraft.plugin.listen_port);
    }

    public void onMessage(WsMessageContext ctx) {
        Bukkit.getScheduler().runTask(ReplCraft.plugin, () -> {
            String nonce = null;
            try {
                JSONObject request = new JSONObject(ctx.message());
                nonce = request.getString("nonce");

                JSONObject response = new JSONObject();
                response.put("ok", true);
                response.put("nonce", nonce);
                handle(ctx, request, response);
                ctx.send(response.toString());
            } catch (JSONException ex) {
                JSONObject json = new JSONObject();
                json.put("ok", false);
                json.put("nonce", nonce);
                json.put("error", "bad request");
                json.put("message", ex.getMessage());
                ctx.send(json.toString());
            } catch (InvalidStructure ex) {
                JSONObject json = new JSONObject();
                json.put("ok", false);
                json.put("nonce", nonce);
                json.put("error", "invalid structure");
                json.put("message", ex.getMessage());
                ctx.send(json.toString());
            } catch (ApiError ex) {
                JSONObject json = new JSONObject();
                json.put("ok", false);
                json.put("nonce", nonce);
                json.put("error", ex.type);
                json.put("message", ex.message);
                ctx.send(json.toString());
            } catch (Exception ex) {
                JSONObject json = new JSONObject();
                json.put("nonce", nonce);
                json.put("ok", false);
                json.put("error", "internal error");
                json.put("message", "an internal server error occurred (this is a bug)");
                ex.printStackTrace();
                ctx.send(json.toString());
            }
        });
    }

    public void handle(WsMessageContext ctx, JSONObject request, JSONObject response) throws InvalidStructure, ApiError {
        Client client = clients.get(ctx);
        String action = request.getString("action");

        if (action.equals("authenticate")) {
            client.setStructure(
                Util.verifyToken(request.getString("token")),
                request.getString("token")
            );
            ReplCraft.plugin.logger.info("Client " + ctx.session.getRemoteAddress() + " authenticated: " + client.getStructure());
        } else if (client.getStructure() == null) {
            String error = client.isInvalidated()
                ? "Structure was invalidated"
                : "Connection isn't authenticated yet";
            throw new ApiError("unauthenticated", error);
        } else switch (action) {
            case "watch":
                checkPermission(client, "replcraft.api.watch");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.watch(getBlock(client, request)); break;
            case "unwatch":
                checkPermission(client, "replcraft.api.unwatch");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.unwatch(getBlock(client, request)); break;
            case "poll":
                checkPermission(client, "replcraft.api.poll");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.poll(getBlock(client, request)); break;
            case "unpoll":
                checkPermission(client, "replcraft.api.unpoll");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.unpoll(getBlock(client, request)); break;
            case "watch_all":
                checkPermission(client, "replcraft.api.watch_all");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.setWatchAll(true); break;
            case "unwatch_all":
                checkPermission(client, "replcraft.api.unwatch_all");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.setWatchAll(false); break;
            case "poll_all":
                checkPermission(client, "replcraft.api.poll_all");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.setPollAll(true); break;
            case "unpoll_all":
                checkPermission(client, "replcraft.api.unpoll_all");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                client.setPollAll(false); break;

            case "get_entities": {
                checkPermission(client, "replcraft.api.get_entities");
                chargeFuel(client, ReplCraft.plugin.cost_per_expensive_api_call);
                Block zero = client.getStructure().getBlock(0, 0, 0);
                Block max = client.getStructure().getBlock(
                    client.getStructure().inner_size_x()-1,
                    client.getStructure().inner_size_y()-1,
                    client.getStructure().inner_size_z()-1
                );

                JSONArray entities = new JSONArray();
                for (Entity entity: zero.getWorld().getNearbyEntities(BoundingBox.of(zero, max))) {
                    JSONObject entity_json = new JSONObject();
                    entity_json.put("type", entity.getType());
                    entity_json.put("name", entity.getName());
                    if (entity instanceof LivingEntity) {
                        LivingEntity live = (LivingEntity) entity;
                        entity_json.put("health", live.getHealth());
                        entity_json.put("max_health", live.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                    }
                    entity_json.put("x", entity.getLocation().getX() - client.getStructure().inner_min_x());
                    entity_json.put("y", entity.getLocation().getY() - client.getStructure().inner_min_y());
                    entity_json.put("z", entity.getLocation().getZ() - client.getStructure().inner_min_z());
                    entities.put(entity_json);
                }
                response.put("entities", entities);
                break;
            }

            case "get_inventory": {
                checkPermission(client, "replcraft.api.get_inventory");
                chargeFuel(client, ReplCraft.plugin.cost_per_expensive_api_call);
                JSONArray items = new JSONArray();
                BlockState state = getBlock(client, request).getState();
                if (!(state instanceof Container)) {
                    throw new ApiError("invalid operation", "block isn't a container");
                }
                ItemStack[] contents = ((Container) state).getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item == null) continue;
                    JSONObject jsonitem = new JSONObject();
                    jsonitem.put("index", i);
                    jsonitem.put("type", item.getType().getKey());
                    jsonitem.put("amount", item.getAmount());
                    items.put(jsonitem);
                }
                response.put("items", items);
                break;
            }

            case "move_item": {
                checkPermission(client, "replcraft.api.move_item");
                chargeFuel(client, ReplCraft.plugin.cost_per_expensive_api_call);
                Block source = getBlock(client, request, "source_x", "source_y", "source_z");
                Block target = getBlock(client, request, "target_x", "target_y", "target_z");
                checkProtectionPlugins(client.getStructure().minecraft_uuid, source.getLocation());
                checkProtectionPlugins(client.getStructure().minecraft_uuid, target.getLocation());
                int index = request.getInt("index");
                int amount = request.isNull("amount") ? 0 : request.getInt("amount");

                Inventory source_inventory = getContainer(source, "source block");
                Inventory target_inventory = getContainer(target, "target block");
                ItemStack item = getItem(source, "source block", index);

                if (amount == 0) amount = item.getAmount();
                if (amount > item.getAmount()) {
                    throw new ApiError("invalid operation", "tried to move more items than there are");
                }
                ItemStack moved = item.clone();
                item.setAmount(item.getAmount() - amount);
                moved.setAmount(amount);
                if (ReplCraft.plugin.core_protect) {
                    String player = client.getStructure().getPlayer().getName();
                    ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", source.getLocation());
                    ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", target.getLocation());
                }

                HashMap<Integer, ItemStack> leftover = target_inventory.addItem(moved);
                if (!leftover.values().isEmpty()) {
                    for (ItemStack value: leftover.values()) {
                        source_inventory.addItem(value);
                    }
                    throw new ApiError("invalid operation", "failed to move all items");
                }
                break;
            }

            case "get_power_level": {
                checkPermission(client, "replcraft.api.get_power_level");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                response.put("power", getBlock(client, request).getBlockPower());
                break;
            }

            case "get_size": {
                checkPermission(client, "replcraft.api.get_size");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                response.put("x", client.getStructure().inner_size_x());
                response.put("y", client.getStructure().inner_size_y());
                response.put("z", client.getStructure().inner_size_z());
                break;
            }

            case "get_location": {
                checkPermission(client, "replcraft.api.get_location");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                response.put("x", client.getStructure().inner_min_x());
                response.put("y", client.getStructure().inner_min_y());
                response.put("z", client.getStructure().inner_min_z());
                break;
            }

            case "get_block": {
                checkPermission(client, "replcraft.api.get_block");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                response.put("block", getBlock(client, request).getBlockData().getAsString());
                break;
            }

            case "get_sign_text": {
                checkPermission(client, "replcraft.api.get_sign_text");
                chargeFuel(client, ReplCraft.plugin.cost_per_api_call);
                BlockState state = getBlock(client, request).getState();
                if (!(state instanceof Sign)) {
                    throw new ApiError("invalid operation", "block is not a sign");
                }
                response.put("lines", ((Sign) state).getLines());
                break;
            }

            case "set_sign_text": {
                checkPermission(client, "replcraft.api.set_sign_text");
                chargeFuel(client, ReplCraft.plugin.cost_per_block_change_api_call);
                Block block = getBlock(client, request);
                BlockState state = block.getState();
                if (!(state instanceof Sign)) {
                    throw new ApiError("invalid operation", "block is not a sign");
                }
                JSONArray lines = request.getJSONArray("lines");
                if (lines.length() != 4) {
                    throw new ApiError("bad request", "expected exactly 4 lines of text");
                }

                // Simulate sign change event to make chestshop and similar verify the request
                String[] line_array = new String[4];
                for (int i = 0; i < 4; i++) {
                    line_array[i] = lines.getString(i);
                }

                checkProtectionPlugins(client.getStructure().minecraft_uuid, block.getLocation());
                if (ReplCraft.plugin.sign_protection) {
                    OfflinePlayer offlinePlayer = client.getStructure().getPlayer();
                    if (!(offlinePlayer instanceof Player)) {
                        throw new ApiError("offline", "this API call requires you to be online");
                    }
                    SignChangeEvent event = new SignChangeEvent(block, (Player) offlinePlayer, line_array);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        throw new ApiError("bad request", "sign change event was cancelled by another plugin");
                    }
                    // Use lines from fired event, since chestshop will rewrite them to be valid
                    line_array = event.getLines();
                }

                for (int i = 0; i < 4; i++) {
                    ((Sign) state).setLine(i, line_array[i]);
                }
                if (ReplCraft.plugin.core_protect) {
                    String player = client.getStructure().getPlayer().getName();
                    ReplCraft.plugin.coreProtect.logPlacement(player + " [API]", block.getLocation(), block.getBlockData().getMaterial(), block.getBlockData());
                }
                state.update();
                break;
            }

            case "craft": {
                checkPermission(client, "replcraft.api.craft");
                chargeFuel(client, ReplCraft.plugin.cost_per_expensive_api_call);
                JSONArray ingredients = request.getJSONArray("ingredients");
                Inventory output = getContainer(getBlock(client, request), "output container");
                checkProtectionPlugins(client.getStructure().minecraft_uuid, output.getLocation());

                class CraftingHelper {
                    final ItemStack item;
                    final Location location;
                    final int index;
                    /** The number of items that have been consumed from this helper */
                    int timesUsed = 0;
                    /** The number of times this helper was included in the client's provided recipe */
                    int timesReferenced = 0;
                    CraftingHelper(ItemStack item, Location location, int index) {
                        this.item = item;
                        this.location = location;
                        this.index = index;
                    }
                }

                // A list of crafting helpers. Crafting helpers pointing at the same location are duplicated
                // in the list. Some slots are null to account for spaces.
                ArrayList<CraftingHelper> items = new ArrayList<>();
                for (int i = 0; i < ingredients.length(); i++) {
                    if (ingredients.isNull(i)) {
                        items.add(null);
                        continue;
                    }

                    JSONObject reference = ingredients.getJSONObject(i);
                    Block block = getBlock(client, reference);
                    int index = reference.getInt("index");
                    ItemStack item = getItem(block, String.format("ingredient %d block", i), index);
                    Location location = block.getLocation();
                    checkProtectionPlugins(client.getStructure().minecraft_uuid, location);
                    CraftingHelper new_or_existing = items.stream()
                        .filter(helper -> helper != null && helper.location.equals(location) && helper.index == index)
                        .findFirst()
                        .orElseGet(() -> new CraftingHelper(item, location, index));
                    new_or_existing.timesReferenced += 1;
                    items.add(new_or_existing);
                }

                boolean crafted = false;
                Iterator<Recipe> iter = Bukkit.recipeIterator();
                recipes: while (!crafted && iter.hasNext()) {
                    Recipe next = iter.next();
                    if (next instanceof ShapedRecipe) {
                        Map<Character, ItemStack> ingredientMap = ((ShapedRecipe) next).getIngredientMap();
                        String[] rows = ((ShapedRecipe) next).getShape();
                        for (int row = 0; row < rows.length; row++) {
                            for (int col = 0; col < rows[row].length(); col++) {
                                ItemStack stack = ingredientMap.get(rows[row].charAt(col));

                                int i = row * rows.length + col;
                                if (i >= items.size()) continue recipes;
                                CraftingHelper ingredient = items.get(i);

                                if (stack == null && ingredient != null) {
                                    // Item provided but no item required here
                                    // Reset used items and try next recipe
                                    for (CraftingHelper item : items) if (item != null) item.timesUsed = 0;
                                    continue recipes;
                                }
                                if (stack == null) {
                                    // No item to check here and no item provided, move on to next slot
                                    continue;
                                }
                                if (ingredient == null || stack.getType() != ingredient.item.getType()) {
                                    // Incorrect item provided for slot
                                    // Reset used items and try next recipe
                                    for (CraftingHelper item : items) if (item != null) item.timesUsed = 0;
                                    continue recipes;
                                }

                                ingredient.timesUsed += 1;
                            }
                        }
                    } else if (next instanceof ShapelessRecipe) {
                        List<ItemStack> recipe_ingredients = ((ShapelessRecipe) next).getIngredientList();
                        for (ItemStack required_item: recipe_ingredients) {
                            boolean matched = items.stream().anyMatch(helper -> {
                                if (helper == null) return false;
                                if (helper.item.getType() != required_item.getType()) return false;
                                if (helper.timesUsed >= helper.item.getAmount()) return false;
                                helper.timesUsed++;
                                return true;
                            });
                            if (!matched) {
                                for (CraftingHelper item: items) if (item != null) item.timesUsed = 0;
                                continue recipes;
                            }
                        }
                        crafted = true;
                    } else {
                        continue recipes;
                    }

                    // Ensure all items have actually been used as many times as they claim
                    if (!items.stream().allMatch(helper -> helper == null || helper.timesUsed == helper.timesReferenced)) {
                        for (CraftingHelper item: items) if (item != null) item.timesUsed = 0;
                        continue recipes;
                    }

                    // Ensure no item underflowed
                    for (CraftingHelper ingredient: items) {
                        if (ingredient == null) continue;
                        if (ingredient.timesUsed > ingredient.item.getAmount()) {
                            throw new ApiError("invalid operation", String.format(
                                "attempted to use more %s than available",
                                ingredient.item.getType()
                            ));
                        }
                    }

                    // Ensure there's somewhere to put the resulting item
                    if (!output.addItem(next.getResult()).isEmpty()) {
                        throw new ApiError("invalid operation", "no space to store result");
                    }
                    if (ReplCraft.plugin.core_protect) {
                        String player = client.getStructure().getPlayer().getName();
                        ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", output.getLocation());
                    }

                    for (CraftingHelper ingredient: items) {
                        if (ingredient == null) continue;
                        ingredient.item.setAmount(ingredient.item.getAmount() - ingredient.timesUsed);
                        if (ReplCraft.plugin.core_protect) {
                            String player = client.getStructure().getPlayer().getName();
                            ReplCraft.plugin.coreProtect.logContainerTransaction(player + " [API]", ingredient.location);
                        }
                    }
                    crafted = true;
                }

                if (!crafted) throw new ApiError("invalid operation", "no matching recipe");
                break;
            }

            case "set_block": {
                checkPermission(client, "replcraft.api.set_block");
                chargeFuel(client, ReplCraft.plugin.cost_per_block_change_api_call);
                try {
                    String blockDataString = request.getString("blockData");
                    validateBlockData(blockDataString);

                    Inventory source = !request.isNull("source_x")
                        ? getContainer(getBlock(client, request, "source_x", "source_y", "source_z"), "source")
                        : null;
                    Inventory destination = !request.isNull("target_x")
                        ? getContainer(getBlock(client, request, "target_x", "target_y", "target_z"), "destination")
                        : null;

                    BlockData blockData = ReplCraft.plugin.getServer().createBlockData(blockDataString);
                    Material material = remapBlockMaterialToItemMaterial(blockData.getMaterial());
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

                    Block target = getBlock(client, request);
                    checkProtectionPlugins(client.getStructure().minecraft_uuid, target.getLocation());

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
                break;
            }

            default: throw new ApiError("bad request", "Unknown action");
        }
    }

    /**
     * Validates and returns a container inventory
     * @param container the block that should be a container
     * @param term a human-readable name for the block that's used for the error message
     * @return the block's inventory
     * @throws ApiError if the block isn't a container
     */
    private static Inventory getContainer(Block container, String term) throws ApiError {
        BlockState sourceState = container.getState();
        if (!(sourceState instanceof Container)) {
            throw new ApiError("invalid operation", term + " isn't a container");
        }
        return ((Container) sourceState).getInventory();
    }

    /**
     * Validates and returns a non-null itemstack in the given container at the given index
     * @param container the block that should be a container
     * @param term a human-readable name for the block that's used for the error message
     * @param index the index of the itemstack
     * @return the item
     * @throws ApiError if the block isn't a container or doesn't have an item at the given index
     */
    private static ItemStack getItem(Block container, String term, int index) throws ApiError {
        ItemStack item = getContainer(container, term).getItem(index);
        if (item == null) throw new ApiError("invalid operation", "no item at specified index");
        return item;
    }

    private static final RegionQuery worldguardQuery = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();

    /**
     * Checks if the player with the given UUID is allowed to build in the claim at the given location
     * @param player the uuid of the player who owns this region
     * @param location the location of the block being modified
     * @throws ApiError if any protection plugin denied the block modification
     */
    private static void checkProtectionPlugins(UUID player, Location location) throws ApiError {
        if (ReplCraft.plugin.grief_prevention) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claim != null && !claim.hasExplicitPermission(player, ClaimPermission.Build))
                throw new ApiError("invalid operation", "This block is protected by GriefPrevention.");
        }
        if (ReplCraft.plugin.world_guard) {
            ApplicableRegionSet set = worldguardQuery.getApplicableRegions(BukkitAdapter.adapt(location));
            if (set.queryState(null, ReplCraft.plugin.worldGuard.flag) == StateFlag.State.DENY) {
                throw new ApiError("invalid operation", "This block is protected by WorldGuard.");
            }
        }
    }

    private static final Pattern blockStates = Pattern.compile("\\[(\\w+)=(\\w+)]");
    /**
     * Validates the BlockStates in a block string
     * @param block a block string
     * @throws ApiError if any disallowed block states are present
     */
    private static void validateBlockData(String block) throws ApiError {
        Matcher matcher = blockStates.matcher(block);
        while (matcher.find()) {
            String name = matcher.group(1);
            switch (name) {
                case "facing":
                case "rotation":
                case "axis":
                case "attachment":
                case "face":
                case "hinge": // doors
                case "hanging": // lanterns
                case "shape": // rails, stairs
                case "mode": // comparators
                case "delay": // repeater
                case "type": // slabs (todo: potential duplication?)
                case "open": // trapdoors
                // case "east": // maybe, for fences, walls and redstone
                // case "north":
                // case "south":
                // case "west":
                // case "up":
                // case "down":
                // case "half": // maybe, for doors, beds, stairs, tall grass, etc.
                // case "color": // maybe
                    continue;

                default:
                    throw new ApiError("bad request", String.format("Disallowed block state tag \"%s\"", name));
            }
        }
    }

    /**
     * Remaps a material based on what item is necessary to place it (e.g. wheat -> seeds)
     * @param material the block material
     * @return the item material
     */
    private static Material remapBlockMaterialToItemMaterial(Material material) {
        // todo: I'm sure there's more
        // Beds and doors may also be problematic
        switch (material) {
            case WHEAT:        return Material.WHEAT_SEEDS;
            case PUMPKIN_STEM: return Material.PUMPKIN_SEEDS;
            case MELON_STEM:   return Material.MELON_SEEDS;
            case BEETROOTS:    return Material.BEETROOT_SEEDS;
            case COCOA:        return Material.COCOA_BEANS;
            default: return material;
        }
    }

    private static void checkPermission(Client client, String permission) throws ApiError {
        OfflinePlayer player = client.getStructure().getPlayer();
        World world = client.getStructure().getWorld();
        if (!ReplCraft.plugin.permissionProvider.hasPermission(player, world, permission))
            throw new ApiError("bad request", "You lack the permission to make this API call.");
    }

    private static void chargeFuel(Client client, double amount) throws ApiError {
        if (!client.useFuel(amount)) {
            String message = String.format(
                "out of fuel (cost: %s). available strategies: provide %s of %s.",
                amount, ReplCraft.plugin.consume_from_all ? "ALL" : "ANY", client.getFuelSources()
            );
            throw new ApiError("out of fuel", message);
        }
    }

    private static Block getBlock(Client client, JSONObject request) throws ApiError {
        return getBlock(client, request, "x", "y", "z");
    }

    private static Block getBlock(Client client, JSONObject request, String label_x, String label_y, String label_z) throws ApiError {
        int x = request.getInt(label_x);
        int y = request.getInt(label_y);
        int z = request.getInt(label_z);
        Block block = client.getStructure().getBlock(x, y, z);
        if (block == null) throw new ApiError("bad request", "block out of bounds");
        return block;
    }

    public void shutdown() {
        this.app.stop();
    }
}
