package eelfloat.replcraft.net;

import eelfloat.replcraft.ReplCraft;
import io.javalin.websocket.WsContext;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
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
        try {
            ctx.send(json.toString());
        } catch(Exception ex) {
            // "condition is always false" - LIES.
            // It's even a compilation error if I put `IOException` in the catch block
            // But this block is _definitely_ entered.
            if (ex instanceof IOException && ex.getCause() instanceof java.nio.channels.ClosedChannelException) {
                ReplCraft.plugin.logger.fine("Failed to send message to client, channel was closed.");
            } else {
                throw ex;
            }
        }
    }

    public abstract void dispose();
}
