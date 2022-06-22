package eelfloat.replcraft.net.handlers;

import eelfloat.replcraft.exceptions.ApiError;
import eelfloat.replcraft.net.RequestContext;

public interface ActionContinuation {
    /**
     * Runs the action continuation
     * @param ctx the request context
     * @return a new action continuation to run later, or null to finish the request
     * @throws ApiError reported to the client as a failed request
     */
    ActionContinuation execute(RequestContext ctx) throws ApiError;

    /**
     * Returns the delay before this ActionContinuation should be run
     * @return the delay, in ticks
     */
    default int getMinimumDelay() { return 1; }
}
