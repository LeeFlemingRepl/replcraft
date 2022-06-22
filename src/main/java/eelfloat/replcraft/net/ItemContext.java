package eelfloat.replcraft.net;

import io.jsonwebtoken.Claims;

import java.util.UUID;

public class ItemContext {
    public final ClientV2 client;
    public final Claims token;

    public ItemContext(ClientV2 client, Claims token) {
        this.client = client;
        this.token = token;
    }

    public UUID getUUID() {
        return UUID.fromString(token.get("uuid", String.class));
    }

    public String getUsername() {
        return token.get("username", String.class);
    }

    public String getItemID() {
        return token.get("item", String.class);
    }
}
