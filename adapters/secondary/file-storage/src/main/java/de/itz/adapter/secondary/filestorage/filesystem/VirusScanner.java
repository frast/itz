package de.itz.adapter.secondary.filestorage.filesystem;

import java.io.IOException;
import java.nio.file.Path;

public interface VirusScanner {
    VirusScanResult scan(Path quarantinedFile) throws IOException;
}
