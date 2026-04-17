package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.FosRoles;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.Date;
import java.util.UUID;

/**
 * Abstract Factory for test actors.
 * The JWT is HMAC-signed with a fixed test secret.
 */
public final class MockActorFactory {

    private static final String TEST_SECRET = "fos-test-secret-key-32-bytes-min!!";

    private MockActorFactory() {}

    public static TestActor player() {
        return build(FosRoles.PLAYER, CanonicalType.PLAYER);
    }

    public static TestActor headCoach() {
        return build(FosRoles.HEAD_COACH, CanonicalType.CLUB);
    }

    public static TestActor clubAdmin() {
        return build(FosRoles.CLUB_ADMIN, CanonicalType.CLUB);
    }

    private static TestActor build(String role, CanonicalType refType) {
        UUID actorId = UUID.randomUUID();
        String jwt = signJwt(actorId, role);
        CanonicalRef ref = CanonicalRef.of(refType, actorId);
        return new TestActor(actorId, jwt, role, ref);
    }

    private static String signJwt(UUID actorId, String role) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(actorId.toString())
                    .issuer("http://localhost/realms/fos")
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .claim("role", role)
                    .claim("preferred_username", role.toLowerCase() + "-test")
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims);
            jwt.sign(new MACSigner(TEST_SECRET));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }

    public static String testJwtSecret() {
        return TEST_SECRET;
    }
}
