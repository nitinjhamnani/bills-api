package com.b2bfintech.organisation.entity;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.Portal;
import com.b2bfintech.organisation.entity.AppUser.UserType;

import java.util.Set;

/**
 * Maps stored {@link UserType} values to distinct API/JWT roles and portals across
 * Platform, Corporate, and Vendor workspaces.
 */
public final class UserTypes {

    public static final Set<String> PLATFORM_ASSIGNABLE_ROLES =
            Set.of("ORGANISATION_ADMIN", "MAKER", "CHECKER", "VIEWER", "SALES");
    public static final Set<String> CORPORATE_ASSIGNABLE_ROLES =
            Set.of("CORP_ADMIN", "CORP_OPERATOR", "BRANCH_USER");
    public static final Set<String> VENDOR_ASSIGNABLE_ROLES =
            Set.of("VENDOR_ADMIN", "VENDOR_OPERATOR");

    private UserTypes() {
    }

    public static UserType fromRole(String role, Client.ClientType clientType) {
        if (role == null || role.isBlank()) {
            throw new BadRequestException("role is required");
        }
        if (clientType == null) {
            return switch (role) {
                case "ORGANISATION_ADMIN" -> UserType.ORG_ADMIN;
                case "MAKER" -> UserType.ORG_MAKER;
                case "CHECKER" -> UserType.ORG_CHECKER;
                case "VIEWER" -> UserType.ORG_USER;
                case "SALES" -> UserType.ORG_SALES;
                default -> throw new BadRequestException("role must be one of " + PLATFORM_ASSIGNABLE_ROLES);
            };
        }
        if (clientType == Client.ClientType.CORPORATE) {
            return switch (role) {
                case "CORP_ADMIN" -> UserType.CORP_ADMIN;
                case "CORP_OPERATOR", "BRANCH_USER" -> UserType.CORP_USER;
                default -> throw new BadRequestException("Corporate role must be one of " + CORPORATE_ASSIGNABLE_ROLES);
            };
        }
        return switch (role) {
            case "VENDOR_ADMIN" -> UserType.VENDOR_ADMIN;
            case "VENDOR_OPERATOR" -> UserType.VENDOR_USER;
            default -> throw new BadRequestException("Vendor role must be one of " + VENDOR_ASSIGNABLE_ROLES);
        };
    }

    public static Portal portal(UserType type) {
        if (type == null) {
            throw new IllegalArgumentException("UserType is required");
        }
        return switch (type) {
            case SUPER_ADMIN, ORG_SUPERADMIN, ORG_ADMIN, ORG_USER, ORG_MAKER, ORG_CHECKER, ORG_SALES -> Portal.PLATFORM;
            case CORP_ADMIN, CORP_USER -> Portal.CORPORATE;
            case VENDOR_ADMIN, VENDOR_USER -> Portal.VENDOR;
        };
    }

    public static String tokenRole(UserType type, boolean hasBranch) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SUPER_ADMIN, ORG_SUPERADMIN -> "SUPER_ADMIN";
            case ORG_ADMIN -> "ORGANISATION_ADMIN";
            case ORG_USER -> "VIEWER";
            case ORG_MAKER -> "MAKER";
            case ORG_CHECKER -> "CHECKER";
            case ORG_SALES -> "SALES";
            case CORP_ADMIN -> "CORP_ADMIN";
            case CORP_USER -> hasBranch ? "BRANCH_USER" : "CORP_OPERATOR";
            case VENDOR_ADMIN -> "VENDOR_ADMIN";
            case VENDOR_USER -> "VENDOR_OPERATOR";
        };
    }

    public static String defaultRole(UserType type) {
        return tokenRole(type, false);
    }

    public static String organisationId(Client client) {
        return client == null ? AppUser.PAYTRIX : client.getId().toString();
    }
}
