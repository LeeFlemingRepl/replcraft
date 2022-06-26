package eelfloat.replcraft;

import eelfloat.replcraft.command.*;
import eelfloat.replcraft.listeners.StructureInteractions;
import eelfloat.replcraft.listeners.StructureUpdates;
import eelfloat.replcraft.listeners.ToolListeners;
import eelfloat.replcraft.net.StructureContext;
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
import org.bukkit.configuration.ConfigurationSection;
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
    public boolean anti_xray;
    public double replizePrice;

    public double cost_per_api_call;
    public double cost_per_expensive_api_call;
    public double cost_per_block_change_api_call;

    /** The number of chests that a structure can have as its inventory */
    public int structure_inventory_limit = 20;
    /** The extra fuel cost per chest for API calls that interact with structure inventory */
    public double fuel_cost_per_structure_inventory = 0.25;
    /** The minimum number of chests before fuel_cost_per_structure_inventory takes effect */
    public int fuel_cost_per_structure_inventory_start = 4;
    /** The fuel strategies, which provide fuel consumption. Map from name to factory. */
    public HashMap<String, Function<StructureContext, FuelStrategy>> strategies = new HashMap<>();
    /** The permission provider, which provides bukkit permissions for Clients */
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
        anti_xray = config.getBoolean("anti_xray");

        // https://gist.github.com/RezzedUp/d7957af10bfbfc6837ae1a4b55975f40
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ReplCraft.class.getClassLoader());
        websocketServer = new WebsocketServer();
        Thread.currentThread().setContextClassLoader(classLoader);

        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        RegisteredServiceProvider<Economy> rspe = getServer().getServicesManager().getRegistration(Economy.class);
        if (vault != null && rspe != null)
            economy = rspe.getProvider();
        logger.info(String.format("Vault %s rspe %s economy %s", vault, rspe, economy));

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

        cost_per_api_call = config.getDouble("fuel.cost_per_api_call");
        cost_per_expensive_api_call = config.getDouble("fuel.cost_per_expensive_api_call");
        cost_per_block_change_api_call = config.getDouble("fuel.cost_per_block_change_api_call");

        strategies.put("leftover", ctx -> new LeftoverFuelStrategy("leftover"));
        ConfigurationSection strats = config.getConfigurationSection("fuel.strategies");
        for (String key: strats.getKeys(false)) {
            ConfigurationSection strat = strats.getConfigurationSection(key);
            String type = strat.getString("type", "");
            Function<StructureContext, FuelStrategy> stratProvider = null;
            switch (type) {
                case "ratelimit":
                    double fuel_per_sec = strat.getDouble("fuel_per_sec");
                    double max_fuel = strat.getDouble("max_fuel");
                    boolean shared = strat.getBoolean("shared");
                    logger.info("Creating new " + (shared ? "shared" : "independent") + " ratelimit strategies");
                    RatelimitFuelStrategy sharedStrat = new RatelimitFuelStrategy(key, fuel_per_sec, max_fuel);
                    stratProvider = !shared
                        ? ctx -> new RatelimitFuelStrategy(key, fuel_per_sec, max_fuel)
                        : ctx -> sharedStrat;
                    break;

                case "item":
                    String mat_name = strat.getString("item", "");
                    Material material = Material.getMaterial(mat_name);
                    if (material == null) {
                        throw new RuntimeException(String.format("fuel.strategies.%s.item: invalid material", key));
                    }
                    double fuel = strat.getDouble("fuel.item_strategy.fuel_provided");
                    stratProvider = ctx -> new ItemFuelStrategy(key, ctx, material, fuel);
                    break;

                case "economy":
                    double fuel_price = strat.getDouble("fuel_price");
                    if (vault == null) throw new RuntimeException("Vault API not found, install or disable economy_strategy.");
                    if (rspe == null) throw new RuntimeException("Economy API provider not found, install one or disable economy_strategy.");
                    stratProvider = client -> new EconomyFuelStrategy(key, client, fuel_price);
                    break;

                case "durability":
                    double fuel_per_unit = strat.getDouble("fuel_per_unit");
                    stratProvider = client -> new DurabilityFuelStrategy(key, fuel_per_unit);
                    break;

                default: throw new RuntimeException(String.format("fuel.strategies.%s.type: unknown type", key));
            }
            strategies.put(key, stratProvider);
        }
        ReplCraft.plugin.logger.info("Loaded strategies: " + strategies.keySet().toString());

        for (Map<?, ?> structureType: config.getMapList("materials")) {
            String name = (String) structureType.get("name");

            String typeName = (String) structureType.get("type");
            typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1).toLowerCase();
            StructureType type = StructureType.valueOf(typeName);

            int maxSize = type == StructureType.Structure ? ((Number) structureType.get("max_size")).intValue() : 0;
            double fuelMultiplier = ((Number) structureType.get("fuel_multiplier")).doubleValue();
            double fuelPerTick = ((Number) structureType.get("fuel_per_tick")).doubleValue();
            boolean consumeFromAll = (Boolean) structureType.get("consume_from_all_strategies");

            //noinspection unchecked
            Set<Material> valid = ((Collection<String>) structureType.get("valid"))
                .stream()
                .map(matName -> {
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) throw new RuntimeException("Failed to parse material " + matName);
                    return mat;
                })
                .collect(Collectors.toSet());

            Set<String> apis = new HashSet<>();
            if (Objects.equals(structureType.get("apis"), "all")) {
                apis.addAll(websocketServer.apis.keySet());
            } else {
                //noinspection unchecked
                apis.addAll((Collection<String>) structureType.get("apis"));
            }

            //noinspection unchecked
            Set<Function<StructureContext, FuelStrategy>> applicableStrategies =
                ((Collection<String>) structureType.get("strategies"))
                .stream()
                .map(strategyName -> {
                    if (!strategies.containsKey(strategyName)) {
                        throw new RuntimeException(String.format(
                            "No such strategy %s on structure type %s",
                            strategyName, name
                        ));
                    }
                    return strategies.get(strategyName);
                })
                .collect(Collectors.toSet());

            frame_materials.add(new StructureMaterial(
                name, type, maxSize, fuelMultiplier, fuelPerTick, valid, apis, applicableStrategies, consumeFromAll
            ));
        }

        replizePrice = config.getDouble("replize_price");
        if (replizePrice > 0 && (vault == null || rspe == null)) {
            throw new RuntimeException("No Vault or compatible economy plugin installed, install or set replize_price to 0");
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
        getServer().getPluginManager().registerEvents(new ToolListeners(), this);
        this.getCommand("transact").setExecutor(new TransactExecutor());
        this.getCommand("recipe").setExecutor(new RecipeExecutor());
        this.getCommand("token").setExecutor(new CreateTokenExecutor());
        this.getCommand("replize").setExecutor(new ReplizeToolExecutor());
        this.getCommand("dereplize").setExecutor(new DereplizeToolExecutor());
        //noinspection CodeBlock2Expr
        getServer().getScheduler().runTaskTimer(plugin, () -> {
            websocketServer.clients.values().forEach(wss -> {
                wss.getContexts().forEach(StructureContext::tick);
            });
        }, 0, 1);
        getServer().getScheduler().runTaskTimer(plugin, () -> {
            websocketServer.clients.values().forEach(client -> {
                client.getContexts().forEach(context -> {
                    if (context.getStructure() == null) return;
                    leftOverFuel.resetExpiration(context.getStructure());
                    ratelimiters.resetExpiration(context.getStructure().minecraft_uuid);
                    context.expireQueries();
                });
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
