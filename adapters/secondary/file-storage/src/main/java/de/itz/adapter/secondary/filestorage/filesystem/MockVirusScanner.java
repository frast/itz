package de.itz.adapter.secondary.filestorage.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MockVirusScanner implements VirusScanner {
    private static final String EICAR_MARKER = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Override
    public VirusScanResult scan(Path quarantinedFile) throws IOException {
        String content = Files.readString(quarantinedFile, StandardCharsets.ISO_8859_1);
        return content.contains(EICAR_MARKER) ? VirusScanResult.INFECTED : VirusScanResult.CLEAN;
    }
}
