package eelfloat.replcraft.net;

import eelfloat.replcraft.ReplCraft;
import eelfloat.replcraft.net.handlers.ActionContinuation;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.Bukkit;
import org.json.JSONObject;

/**
 * A context for a client request, scoped to run only on the main thread.
 */
public class RequestContext {
    public final Client client;
    public final WsMessageContext ctx;
    public final JSONObject request;
    public final JSONObject response;
    public final String nonce;
    public final boolean freeFuel;

    public RequestContext(
        Client client, WsMessageContext ctx, JSONObject request, JSONObject response, String nonce, boolean freeFuel
    ) {
        this.client = client;
        this.ctx = ctx;
        this.request = request;
        this.response = response;
        this.nonce = nonce;
        this.freeFuel = freeFuel;
    }
}
