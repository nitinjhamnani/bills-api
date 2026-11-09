package com.b2bfintech.organisation.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a candidate 8-character client code: a 2-letter prefix ({@code CO} for a corporate,
 * {@code VD} for a vendor), then 3 letters drawn at random from the letters in the client's name,
 * then 3 random digits (e.g. "Acme Corp" -&gt; "COACM482"). Falls back to a random A-Z letter for
 * any position if the name has no letters at all. This only produces a candidate - callers are
 * responsible for retrying on a uniqueness collision (see {@code ClientService.generateUniqueClientCode}).
 */
public final class ClientCodeGenerator {

    public static final String CORPORATE_PREFIX = "CO";
    public static final String VENDOR_PREFIX = "VD";
    private static final String DIGITS = "0123456789";
    private static final int ALPHA_LENGTH = 3;
    private static final int DIGIT_LENGTH = 3;

    private ClientCodeGenerator() {
    }

    public static String generate(String clientName, String prefix) {
        List<Character> letters = lettersOf(clientName);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        StringBuilder code = new StringBuilder(prefix.length() + ALPHA_LENGTH + DIGIT_LENGTH);
        code.append(prefix);
        for (int i = 0; i < ALPHA_LENGTH; i++) {
            code.append(letters.isEmpty()
                    ? (char) ('A' + random.nextInt(26))
                    : letters.get(random.nextInt(letters.size())));
        }
        for (int i = 0; i < DIGIT_LENGTH; i++) {
            code.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return code.toString();
    }

    private static List<Character> lettersOf(String clientName) {
        List<Character> letters = new ArrayList<>();
        if (clientName == null) return letters;
        for (char c : clientName.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetter(c)) letters.add(c);
        }
        return letters;
    }
}
