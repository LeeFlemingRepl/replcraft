package eelfloat.replcraft.net;

import io.javalin.websocket.WsMessageContext;
import org.json.JSONObject;

/**
 * A context for a client request, scoped to run only on the main thread.
 */
public class RequestContext {
    public final Client client;
    public final StructureContext structureContext;
    public final WsMessageContext wsContext;
    public final JSONObject request;
    public final JSONObject response;
    public final String nonce;

    public RequestContext(
            Client client,
            StructureContext structureContext,
            WsMessageContext wsContext,
            JSONObject request,
            JSONObject response,
            String nonce
    ) {
        this.client = client;
        this.structureContext = structureContext;
        this.wsContext = wsContext;
        this.request = request;
        this.response = response;
        this.nonce = nonce;
    }
}
