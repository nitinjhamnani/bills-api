package com.b2bfintech.common.document;

import com.b2bfintech.common.security.AuthenticatedUser;

/** Supplies the client code used as the folder name for an upload. */
public interface ClientCodeResolver {

    String resolve(AuthenticatedUser principal);
}
