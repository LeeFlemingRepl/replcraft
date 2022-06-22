package eelfloat.replcraft.net;

import io.javalin.websocket.WsContext;
import org.json.JSONObject;

import java.util.Collection;

public abstract class Client {
    protected final WsContext ctx;
    protected final WebsocketServer websocketServer;

    public Client(WsContext ctx, WebsocketServer websocketServer) {
        this.ctx = ctx;
        this.websocketServer = websocketServer;
    }

    public abstract StructureContext getDefaultContext();

    public abstract Collection<StructureContext> getContexts();

    /**
     * Sends a message over the client's websocket connection
     */
    public void send(StructureContext context, JSONObject json) {
        json.put("context", context.id);
        ctx.send(json.toString());
    }

    public abstract void dispose();
}
