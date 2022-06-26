package eelfloat.replcraft.net;

import eelfloat.replcraft.PhysicalStructure;
import eelfloat.replcraft.Structure;
import eelfloat.replcraft.net.handlers.WebsocketActionHandler;
import io.javalin.websocket.WsContext;
import org.json.JSONObject;

import java.util.*;

/**
 * V2 clients can connect to multiple structures at a time by specifying the context along with requests.
 * Unlike V1 clients, they no longer have an implicit default context.
 */
public class ClientV2 extends Client {
    private final HashMap<Long, StructureContext> contexts = new HashMap<>();
    public final ArrayList<ItemContext> items = new ArrayList<>();
    private long idcounter = 0;

    public static final String CONTEXT_CAUSE_LOGIN = "login";

    public ClientV2(WsContext ctx, WebsocketServer websocketServer) {
        super(ctx, websocketServer);
    }

    public StructureContext createContext(Structure structure, String authenticationToken, String cause) {
        long id = this.idcounter++;

        StructureContext ctx = new StructureContext(id, this, structure, authenticationToken);
        for (WebsocketActionHandler tracker: websocketServer.apis.values())
            ctx.tracker(tracker);

        this.contexts.put(id, ctx);
        JSONObject json = new JSONObject();
        json.put("type", "contextOpened");
        json.put("cause", cause);
        json.put("id", id);
        this.send(ctx, json);
        return ctx;
    }

    @Override
    public StructureContext getDefaultContext() {
        return this.contexts.get(0L);
    }

    public StructureContext getContext(long id) {
        return this.contexts.get(id);
    }

    public Collection<StructureContext> getContexts() {
        return this.contexts.values();
    }

    public void disposeContext(long id, String cause) {
        StructureContext removed = this.contexts.remove(id);
        if (removed != null) {
            JSONObject json = new JSONObject();
            json.put("type", "contextClosed");
            json.put("cause", cause);
            json.put("id", id);
            this.send(removed, json);
        }
    }

    @Override
    public void dispose() {
        for (StructureContext ctx: this.getContexts())
            this.disposeContext(ctx.id, "connection closed");
    }
}
