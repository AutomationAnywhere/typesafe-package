package com.automationanywhere.botcommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.automationanywhere.core.security.SecureString;

/** Loads the TypeSafe API key from the project's .env file for integration tests. */
final class TestSupport {

    private TestSupport() {
    }

    static SecureString apiKey() {
        Path envFile = Paths.get(System.getProperty("user.dir"), ".env");
        try {
            for (String line : Files.readAllLines(envFile)) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equals("API_KEY")) {
                    return new SecureString(line.substring(colon + 1).trim().toCharArray());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read .env at " + envFile, e);
        }
        throw new IllegalStateException("API_KEY not found in " + envFile);
    }
}
