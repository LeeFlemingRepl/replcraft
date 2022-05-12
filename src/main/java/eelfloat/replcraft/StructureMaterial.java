package eelfloat.replcraft;

import org.bukkit.Material;

import java.util.Set;

public class StructureMaterial {
    public final String name;
    public final int max_size;
    public final double fuelMultiplier;
    public final Set<Material> validMaterials;
    public final Set<String> apis;

    public StructureMaterial(String name, int max_size, double fuelMultiplier, Set<Material> validMaterials, Set<String> apis) {
        this.name = name;
        this.max_size = max_size;
        this.fuelMultiplier = fuelMultiplier;
        this.validMaterials = validMaterials;
        this.apis = apis;
    }
}