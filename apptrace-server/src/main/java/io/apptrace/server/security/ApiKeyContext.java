package io.apptrace.server.security;

import io.apptrace.server.domain.model.ApiKeyEntity;

import java.util.UUID;

/**
 * Thread-local holder for the authenticated API key.
 *
 * Set by ApiKeyAuthFilter after successful authentication.
 * Read by controllers to get the current tenant ID.
 * Cleared by ApiKeyAuthFilter in the finally block after every request.
 */
public final class ApiKeyContext {

    private static final ThreadLocal<ApiKeyEntity> HOLDER = new ThreadLocal<>();

    private ApiKeyContext() {}

    public static void set(ApiKeyEntity apiKey) {
        HOLDER.set(apiKey);
    }

    public static ApiKeyEntity get() {
        ApiKeyEntity apiKey = HOLDER.get();
        if (apiKey == null) {
            throw new IllegalStateException(
                    "No authenticated API key in context — is the auth filter registered?");
        }
        return apiKey;
    }

    public static UUID getTenantId() {
        return get().getTenant().getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}