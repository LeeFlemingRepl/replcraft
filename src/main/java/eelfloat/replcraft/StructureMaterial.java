package eelfloat.replcraft;

import eelfloat.replcraft.net.StructureContext;
import eelfloat.replcraft.strategies.FuelStrategy;
import org.bukkit.Material;

import java.util.*;
import java.util.function.Function;

public class StructureMaterial {
    public final String name;
    public final StructureType type;
    public final int max_size;
    public final double fuelMultiplier;
    public final double fuelPerTick;
    public final Set<Material> validMaterials;
    public final Set<String> apis;
    public final Set<Function<StructureContext, FuelStrategy>> strategies;
    public final boolean consumeFromAll;

    public StructureMaterial(
        String name,
        StructureType type,
        int max_size,
        double fuelMultiplier,
        double fuelPerTick,
        Set<Material> validMaterials,
        Set<String> apis,
        Set<Function<StructureContext, FuelStrategy>> strategies,
        boolean consumeFromAll
    ) {
        this.name = name;
        this.type = type;
        this.max_size = max_size;
        this.fuelMultiplier = fuelMultiplier;
        this.fuelPerTick = fuelPerTick;
        this.validMaterials = validMaterials;
        this.apis = apis;
        this.strategies = strategies;
        this.consumeFromAll = consumeFromAll;
    }
}