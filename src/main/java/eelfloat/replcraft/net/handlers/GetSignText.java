package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import static eelfloat.replcraft.util.ApiUtil.getBlock;

    
public class GetSignText implements WebsocketActionHandler {
    @Override
    public String route() {
        return "get_sign_text";
    }

    @Override
    public String permission() {
        return "replcraft.api.get_sign_text";
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
        BlockState state = getBlock(ctx.structureContext, ctx.request).getState();
        if (!(state instanceof Sign)) {
            throw new ApiError(ApiError.INVALID_OPERATION, "block is not a sign");
        }
        ctx.response.put("lines", ((Sign) state).getLines());
        return null;
    }
}
