package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;

public enum FuelCost {
    None,
    Regular,
    Expensive,
    BlockChange;

    public double toDouble() {
        switch (this) {
            case None: return 0;
            case Regular: return ReplCraft.plugin.cost_per_api_call;
            case Expensive: return ReplCraft.plugin.cost_per_expensive_api_call;
            case BlockChange: return ReplCraft.plugin.cost_per_block_change_api_call;
        }
        throw new RuntimeException("unreachable!");
    }
}
