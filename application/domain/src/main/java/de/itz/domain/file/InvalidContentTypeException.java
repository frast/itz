package de.itz.domain.file;

public class InvalidContentTypeException extends IllegalArgumentException {
    public InvalidContentTypeException() {
        super("Content type must be a valid concrete media type with optional parameters and at most 512 characters");
    }
}
