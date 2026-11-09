package com.b2bfintech.organisation.util;

import java.util.concurrent.ThreadLocalRandom;

/** A candidate 6-character user code drawn from A-Z and 0-9. Callers retry when it is already taken. */
public final class UserCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    public static final int LENGTH = 6;

    private UserCodeGenerator() {
    }

    public static String generate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] code = new char[LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
