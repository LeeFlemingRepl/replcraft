package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.RequestContext;
import eelfloat.replcraft.util.StructureUtil;
import eelfloat.replcraft.exceptions.ApiError;

public class Authenticate implements WebsocketActionHandler {
    @Override
    public String route() {
        return "authenticate";
    }

    @Override
    public String permission() {
        return null;
    }

    @Override
    public double cost(RequestContext ctx) {
        return FuelCost.None.toDouble();
    }

    @Override
    public boolean authenticated() {
        return false;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        ctx.client.setStructure(
            StructureUtil.verifyToken(ctx.request.getString("token")),
                ctx.request.getString("token")
        );
        ReplCraft.plugin.logger.info("Client " + ctx.ctx.session.getRemoteAddress() + " authenticated: " + ctx.client.getStructure());
        return null;
    }
}
