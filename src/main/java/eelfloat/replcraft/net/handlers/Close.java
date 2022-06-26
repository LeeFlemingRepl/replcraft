package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.ClientV2;
import eelfloat.replcraft.net.RequestContext;

public class Close implements WebsocketActionHandler {
    @Override
    public String route() {
        return "close";
    }

    @Override
    public String permission() {
        return "replcraft.api.close";
    }

    @Override
    public double cost(RequestContext ctx) {
        return 0;
    }

    @Override
    public boolean authenticated() {
        return true;
    }

    @Override
    public ActionContinuation execute(RequestContext ctx) throws ApiError {
        if (!(ctx.client instanceof ClientV2))
            throw new ApiError(ApiError.BAD_REQUEST, "This API is only available for protocol V2");
        ((ClientV2) ctx.client).disposeContext(ctx.structureContext.id, "closed by client");
        return null;
    }
}
