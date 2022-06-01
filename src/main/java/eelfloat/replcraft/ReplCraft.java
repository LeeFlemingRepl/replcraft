package eelfloat.replcraft;

import eelfloat.replcraft.command.RecipeExecutor;
import eelfloat.replcraft.command.TransactExecutor;
import eelfloat.replcraft.listeners.StructureInteractions;
import eelfloat.replcraft.listeners.StructureUpdates;
import eelfloat.replcraft.net.Client;
import eelfloat.replcraft.net.WebsocketServer;
import eelfloat.replcraft.permissions.DefaultPermissionProvider;
import eelfloat.replcraft.permissions.PermissionProvider;
import eelfloat.replcraft.permissions.VaultPermissionProvider;
import eelfloat.replcraft.strategies.*;
import eelfloat.replcraft.util.BoxedDoubleButActuallyUseful;
import eelfloat.replcraft.util.ExpirableCacheMap;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.Key;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class ReplCraft extends JavaPlugin {
    public static ReplCraft plugin;
    public Logger logger = null;

    public List<StructureMaterial> frame_materials = new ArrayList<>();

    public WebsocketServer websocketServer = null;
    public String public_address = null;
    public String listen_address = null;
    public int listen_port = 28080;

    public boolean block_protection;
    public boolean sign_protection;
    public boolean grief_prevention;
    public boolean world_guard;
    public boolean core_protect;

    public boolean creative_mode;

    public boolean consume_from_all;
    public double cost_per_api_call;
    public double cost_per_expensive_api_call;
    public double cost_per_block_change_api_call;

    /** The number of chests that a structure can have as its inventory */
    public int structureInventoryLimit = 4;

    /** The fuel strategies, which provide fuel consumption. */
    public List<Function<Client, FuelStrategy>> strategies = new ArrayList<>();
    /** The permission provider, which provides bukkit permissions for `eelfloat.replcraft.net.Client`s */
    public PermissionProvider permissionProvider;
    /** The cryptographic key used to sign auth tokens */
    public Key key = null;
    public CoreProtectAPI coreProtect;
    public WorldGuardIntegration worldGuard;

    public ExpirableCacheMap<Structure, BoxedDoubleButActuallyUseful> leftOverFuel = new ExpirableCacheMap<>(60*60*1000);
    public ExpirableCacheMap<UUID, RatelimitFuelStrategy> ratelimiters = new ExpirableCacheMap<>(60*1000);
    public Economy economy;

    @Override
    public void onLoad() {
        plugin = this;
        logger = Bukkit.getLogger();
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuard = new WorldGuardIntegration();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        public_address = config.getString("public_address");
        listen_address = config.getString("listen_address");
        listen_port = config.getInt("listen_port");

        creative_mode = config.getBoolean("creative_mode");

        // https://gist.github.com/RezzedUp/d7957af10bfbfc6837ae1a4b55975f40
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ReplCraft.class.getClassLoader());
        websocketServer = new WebsocketServer();
        Thread.currentThread().setContextClassLoader(classLoader);

        for (Map<?, ?> structureType: config.getMapList("materials")) {
            String name = (String) structureType.get("name");
            int maxSize = ((Number) structureType.get("max_size")).intValue();
            double fuelMultiplier = ((Number) structureType.get("fuel_multiplier")).doubleValue();
            //noinspection unchecked
            Set<Material> valid = ((Collection<String>) structureType.get("valid")).stream().map(matName -> {
                Material mat = Material.matchMaterial(matName);
                if (mat == null) throw new RuntimeException("Failed to parse material " + matName);
                return mat;
            }).collect(Collectors.toSet());
            Set<String> apis = new HashSet<>();
            if (Objects.equals(structureType.get("apis"), "all")) {
                apis.addAll(websocketServer.apis.keySet());
            } else {
                //noinspection unchecked
                apis.addAll((Collection<String>) structureType.get("apis"));
            }
            frame_materials.add(new StructureMaterial(name, maxSize, fuelMultiplier, valid, apis));
        }


        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        RegisteredServiceProvider<Economy> rspe = getServer().getServicesManager().getRegistration(Economy.class);
        if (vault != null && rspe != null)
            economy = rspe.getProvider();

        block_protection = config.getBoolean("protection.default_block");
        sign_protection = config.getBoolean("protection.default_sign");
        grief_prevention = config.getBoolean("protection.grief_prevention");
        if (grief_prevention && getServer().getPluginManager().getPlugin("GriefPrevention") == null) {
            throw new RuntimeException("GriefPrevention not found, install or disable protection.grief_prevention.");
        }
        world_guard = config.getBoolean("protection.world_guard");
        if (world_guard && getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            throw new RuntimeException("WorldGuard not found, install or disable protection.world_guard.");
        }
        core_protect = config.getBoolean("protection.core_protect");
        if (core_protect) {
            Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
            if (plugin == null) throw new RuntimeException("CoreProtect not found, install or disable protection.core_protect.");
            this.coreProtect = ((CoreProtect) plugin).getAPI();
            if (this.coreProtect.APIVersion() < 9) {
                logger.warning("CoreProtect API too old, expected v9 got v" + this.coreProtect.APIVersion());
            }
        }

        consume_from_all = config.getBoolean("fuel.consume_from_all");
        cost_per_api_call = config.getDouble("fuel.cost_per_api_call");
        cost_per_expensive_api_call = config.getDouble("fuel.cost_per_expensive_api_call");
        cost_per_block_change_api_call = config.getDouble("fuel.cost_per_block_change_api_call");

        if (config.getBoolean("fuel.ratelimit_strategy.enabled")) {
            double fuel_per_sec = config.getDouble("fuel.ratelimit_strategy.fuel_per_sec");
            double max_fuel = config.getDouble("fuel.ratelimit_strategy.max_fuel");
            boolean shared = config.getBoolean("fuel.ratelimit_strategy.enabled");
            logger.info("Creating new " + (shared ? "shared" : "independent") + " ratelimit strategies");
            strategies.add(client -> {
                if (shared) {
                    return ratelimiters.get(
                        client.getStructure().minecraft_uuid,
                        () -> {
                            logger.info("Created new global ratelimit strategy for " + client.getStructure().minecraft_username);
                            return new RatelimitFuelStrategy(fuel_per_sec, max_fuel);
                        }
                    );
                } else {
                    logger.info("Created new per-connection ratelimit strategy");
                    return new RatelimitFuelStrategy(fuel_per_sec, max_fuel);
                }
            });
        }

        strategies.add(client -> new LeftoverFuelStrategy());

        if (config.getBoolean("fuel.item_strategy.enabled")) {
            String mat_name = config.getString("fuel.item_strategy.item", "");
            Material material = Material.getMaterial(mat_name);
            if (material == null) throw new RuntimeException("fuel.item_strategy.item: invalid material");
            double fuel = config.getDouble("fuel.item_strategy.fuel_provided");
            strategies.add(client -> new ItemFuelStrategy(client, material, fuel));
        }

        if (config.getBoolean("fuel.economy_strategy.enabled")) {
            double fuel_price = config.getDouble("fuel.economy_strategy.fuel_price");
            if (vault == null) throw new RuntimeException("Vault API not found, install or disable economy_strategy.");
            if (rspe == null) throw new RuntimeException("Economy API provider not found, install one or disable economy_strategy.");
            strategies.add(client -> new EconomyFuelStrategy(client, fuel_price));
        }

        permissionProvider = new DefaultPermissionProvider();
        if (config.getBoolean("protection.permissions_vault")) {
            if (vault == null) throw new RuntimeException("Vault API not found, install or disable permissions_vault.");

            RegisteredServiceProvider<Permission> rspp = getServer().getServicesManager().getRegistration(Permission.class);
            if (rspp == null) throw new RuntimeException("Vault Permission API provider not found, install one or disable permissions_vault.");

            permissionProvider = new VaultPermissionProvider(rspp.getProvider());
        }

        String secret_key = config.getString("secret_key");
        if (secret_key != null) {
            key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret_key));
        } else {
            key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            config.set("secret_key", Encoders.BASE64.encode(key.getEncoded()));
            saveConfig();
        }

        logger.info("Hello, world!");
        getServer().getPluginManager().registerEvents(new StructureInteractions(), this);
        getServer().getPluginManager().registerEvents(new StructureUpdates(), this);
        this.getCommand("transact").setExecutor(new TransactExecutor());
        this.getCommand("recipe").setExecutor(new RecipeExecutor());
        //noinspection CodeBlock2Expr
        getServer().getScheduler().runTaskTimer(plugin, () -> {
            websocketServer.clients.values().forEach(Client::pollOne);
        }, 0, 1);
        getServer().getScheduler().runTaskTimer(plugin, () -> {
            websocketServer.clients.values().forEach(client -> {
                if (client.getStructure() == null) return;
                leftOverFuel.resetExpiration(client.getStructure());
                ratelimiters.resetExpiration(client.getStructure().minecraft_uuid);
                client.expireQueries();
            });
            leftOverFuel.expire();
            ratelimiters.expire();
        }, 0, 100);
    }

    @Override
    public void onDisable() {
        websocketServer.shutdown();
    }
}
