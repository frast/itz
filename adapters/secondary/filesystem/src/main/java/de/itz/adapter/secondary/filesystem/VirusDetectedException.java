package de.itz.adapter.secondary.filesystem;

public class VirusDetectedException extends Exception {
    public VirusDetectedException() {
        super("The uploaded file was detected as malicious");
    }
}
