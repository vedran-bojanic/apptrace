package io.apptrace.server.service;

import io.apptrace.server.domain.enums.ApiKeyScope;
import io.apptrace.server.domain.model.ApiKeyEntity;
import io.apptrace.server.domain.model.TenantEntity;
import io.apptrace.server.exception.InvalidApiKeyException;
import io.apptrace.server.exception.ResourceNotFoundException;
import io.apptrace.server.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX_LITERAL = "at_";
    private static final int RAW_KEY_BYTES = 32; // 256 bits of randomness

    private final ApiKeyRepository apiKeyRepository;
    private final TenantService tenantService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates a new API key for a tenant.
     *
     * Returns a CreatedApiKey record containing BOTH the hashed entity (saved to DB)
     * AND the raw plaintext key. The plaintext is returned exactly once here and
     * never stored anywhere — the caller must give it to the tenant immediately.
     */
    public CreatedApiKey create(
            UUID tenantId,
            String description,
            Set<ApiKeyScope> scopes,
            OffsetDateTime expiresAt
    ) {
        TenantEntity tenant = tenantService.getById(tenantId);

        // Generate a cryptographically random key: "at_" + base64url(32 random bytes)
        byte[] randomBytes = new byte[RAW_KEY_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawKey = KEY_PREFIX_LITERAL
                + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String keyHash   = sha256hex(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(8, rawKey.length()));

        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .tenant(tenant)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .description(description)
                .scopes(scopes)
                .expiresAt(expiresAt)
                .build();

        ApiKeyEntity saved = apiKeyRepository.save(apiKey);

        // rawKey is returned once and never stored — caller must handle it immediately
        return new CreatedApiKey(saved, rawKey);
    }

    /**
     * Authenticates an incoming bearer token.
     * Called on every request by the security filter.
     *
     * @param rawBearerToken the raw "at_..." token from the Authorization header
     * @return the authenticated ApiKey (with tenant loaded)
     * @throws InvalidApiKeyException if the key is not found, revoked, or expired
     */
    @Transactional(readOnly = true)
    public ApiKeyEntity authenticate(String rawBearerToken) {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            throw new InvalidApiKeyException("Missing API key");
        }
        String hash = sha256hex(rawBearerToken);
        ApiKeyEntity apiKey = apiKeyRepository.findActiveByKeyHash(hash)
                .orElseThrow(() -> new InvalidApiKeyException("Invalid or revoked API key"));

        if (!apiKey.isActive()) {
            throw new InvalidApiKeyException("API key is expired");
        }
        return apiKey;
    }

    public void revoke(UUID apiKeyId) {
        ApiKeyEntity apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + apiKeyId));
        apiKey.revoke();
        apiKeyRepository.save(apiKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyEntity> listForTenant(UUID tenantId) {
        return apiKeyRepository.findAllByTenantId(tenantId);
    }

    // -------------------------------------------------------------------------

    private static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    md.digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Returned from create() — holds both the saved entity and the one-time plaintext key.
     *
     * @param apiKey  the persisted entity (no plaintext key inside)
     * @param rawKey  the plaintext bearer token — show to the user ONCE, then discard
     */
    public record CreatedApiKey(ApiKeyEntity apiKey, String rawKey) {}
}

