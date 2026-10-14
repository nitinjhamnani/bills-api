package com.b2bfintech.organisation.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a candidate 6-character client code: 3 letters drawn at random from the letters that
 * appear in the client's name, followed by 3 random digits (e.g. "Acme Corp" -&gt; "ACM482"). Falls
 * back to a random A-Z letter for any position if the name has no letters at all. This only
 * produces a candidate - callers are responsible for retrying on a uniqueness collision (see
 * {@code ClientService.generateUniqueClientCode}).
 */
public final class ClientCodeGenerator {

    private static final String DIGITS = "0123456789";
    private static final int ALPHA_LENGTH = 3;
    private static final int DIGIT_LENGTH = 3;

    private ClientCodeGenerator() {
    }

    public static String generate(String clientName) {
        List<Character> letters = lettersOf(clientName);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        StringBuilder code = new StringBuilder(ALPHA_LENGTH + DIGIT_LENGTH);
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
