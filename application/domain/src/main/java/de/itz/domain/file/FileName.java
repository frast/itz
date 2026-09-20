package de.itz.domain.file;

public record FileName(String value) {
    public static final int MAX_LENGTH = 255;

    public FileName {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > MAX_LENGTH
                || value.equals(".") || value.equals("..")
                || value.codePoints().anyMatch(FileName::isForbiddenCharacter)) {
            throw new InvalidFileNameException();
        }
    }

    private static boolean isForbiddenCharacter(int point) {
        return Character.isISOControl(point)
                || (point >= 0xD800 && point <= 0xDFFF)
                || "<>:\"/\\|?*".indexOf(point) >= 0
                || point == 0x2028 || point == 0x2029
                // Bidi controls can disguise extensions; ordinary RTL letters and emoji joiners remain valid.
                || point == 0x061C || point == 0x200E || point == 0x200F
                || (point >= 0x202A && point <= 0x202E)
                || (point >= 0x2066 && point <= 0x2069);
    }
}
