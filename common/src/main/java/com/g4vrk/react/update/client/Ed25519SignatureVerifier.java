package com.g4vrk.react.update.client;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies detached Ed25519 release signatures against the pinned React release key.
 */
final class Ed25519SignatureVerifier {

    private static final String PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAjBPvgQr/pgYBmpHeXr0t+G402p1vHKGx3MbANe+FntI=";
    private static final PublicKey RELEASE_PUBLIC_KEY = decodePublicKey();

    private Ed25519SignatureVerifier() {

        throw new UnsupportedOperationException("Utility class");

    }

    static void verify(
            final byte @NotNull [] data,
            final byte @NotNull [] signatureText,
            final @NotNull String artifactName
    ) throws ReleaseClient.VerificationException {

        final boolean valid;
        try {
            final Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(RELEASE_PUBLIC_KEY);
            verifier.update(data);
            final byte[] rawSignature = Base64.getDecoder().decode(
                    new String(signatureText, StandardCharsets.US_ASCII).strip()
            );
            valid = verifier.verify(rawSignature);
        } catch (final GeneralSecurityException | IllegalArgumentException ex) {
            throw new ReleaseClient.VerificationException("unreadable ed25519 signature on " + artifactName);
        }
        if (!valid) {
            throw new ReleaseClient.VerificationException("bad ed25519 signature on " + artifactName);
        }

    }

    private static @NotNull PublicKey decodePublicKey() {

        try {
            final byte[] encoded = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("Ed25519 release key could not be decoded", ex);
        }

    }

}
