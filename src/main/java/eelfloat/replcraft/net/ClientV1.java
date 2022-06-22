package eelfloat.replcraft.net;

import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.handlers.WebsocketActionHandler;
import io.javalin.websocket.WsContext;

import java.util.Collection;
import java.util.Collections;

/**
 * V1 clients can only connect to a single structure at a time and don't support temporary contexts.
 */
public class ClientV1 extends Client {
    StructureContext context = null;

    public ClientV1(WsContext ctx, WebsocketServer websocketServer) {
        super(ctx, websocketServer);
    }

    @Override
    public StructureContext getDefaultContext() {
        return context;
    }

    public StructureContext setContext(Structure structure, String authenticationToken) {
        this.context = new StructureContext(0, this, structure, authenticationToken);
        for (WebsocketActionHandler tracker: websocketServer.apis.values())
            this.context.tracker(tracker);
        return this.context;
    }

    @Override
    public Collection<StructureContext> getContexts() {
        return context == null
            ? Collections.EMPTY_LIST
            : Collections.singleton(context);
    }

    @Override
    public void dispose() {
        if (this.context != null)
            this.context.dispose();
    }
}
