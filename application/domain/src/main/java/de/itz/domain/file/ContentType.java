package de.itz.domain.file;

import java.util.regex.Pattern;

/** Declared content type, including parameters; not a verified content classification. */
public record ContentType(String value) {
    public static final int MAX_LENGTH = 512;
    private static final String TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    private static final String QUOTED_STRING = "\"(?:[\\t !#-\\[\\]-~\\x80-\\xFF]|\\\\[\\t !-~\\x80-\\xFF])*\"";
    // RFC 9110 sections 5.6.6 and 8.3.1; wildcard ranges belong to Accept, not Content-Type.
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "(?!\\*/|[^/]+/\\*(?:[ \\t;]|$))" + TOKEN + "/" + TOKEN
                    + "(?:[ \\t]*;[ \\t]*(?:" + TOKEN + "=(?:" + TOKEN + "|" + QUOTED_STRING + "))?)*");

    public ContentType {
        if (value == null || value.length() > MAX_LENGTH || !MEDIA_TYPE.matcher(value).matches()) {
            throw new InvalidContentTypeException();
        }
    }
}
