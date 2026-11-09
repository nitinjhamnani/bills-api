package com.b2bfintech.common.security;

import java.util.Optional;

/** Resolves the stable user code for a profile id. Used when a token was issued before the code existed. */
public interface UserCodeLookup {

    Optional<String> findByUserId(String userId);
}
