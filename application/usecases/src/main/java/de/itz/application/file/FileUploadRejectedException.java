package de.itz.application.file;

public class FileUploadRejectedException extends RuntimeException {
    public FileUploadRejectedException() {
        super("The file was rejected by the virus scanner");
    }
}
