package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.block.Block;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetBlock implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_block";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_block";
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.Regular.toDouble();
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        Block block = getBlock(ctx.structureContext, ctx.request);
        switch (block.getType()) {
            case COAL_ORE: case COPPER_ORE: case IRON_ORE: case GOLD_ORE:
            case DIAMOND_ORE: case LAPIS_ORE: case REDSTONE_ORE: case STONE:
                ctx.response.put("block", "replcraft:obfuscated_stone");
                break;

            case DEEPSLATE_COAL_ORE: case DEEPSLATE_COPPER_ORE: case DEEPSLATE_IRON_ORE: case DEEPSLATE_GOLD_ORE:
            case DEEPSLATE_DIAMOND_ORE: case DEEPSLATE_LAPIS_ORE: case DEEPSLATE_REDSTONE_ORE: case DEEPSLATE:
                ctx.response.put("block", "replcraft:obfuscated_deepslate");
                break;

            case ANCIENT_DEBRIS: case NETHER_QUARTZ_ORE: case NETHER_GOLD_ORE: case NETHERRACK:
                ctx.response.put("block", "replcraft:obfuscated_netherrack");
                break;

            default:
                ctx.response.put("block", block.getBlockData().getAsString());
        }
        return null;
    }
}
