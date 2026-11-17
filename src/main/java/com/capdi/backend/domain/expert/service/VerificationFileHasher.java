package com.capdi.backend.domain.expert.service;

import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class VerificationFileHasher {
    public String hash(String path) {
        try (var input = Files.newInputStream(Path.of(path))) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 10 * 1024 * 1024) return null;
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            // A missing or unreadable file must never be represented as a matching snapshot.
            return null;
        }
    }
}
