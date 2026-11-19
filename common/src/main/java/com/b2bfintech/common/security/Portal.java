package com.b2bfintech.common.security;

/**
 * The three isolated workspaces of Paytrix. Every signed-in user belongs to exactly one portal.
 */
public enum Portal {
    PLATFORM,
    CORPORATE,
    VENDOR
}
