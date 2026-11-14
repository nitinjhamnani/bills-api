package com.b2bfintech.organisation.entity;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.organisation.entity.AppUser.UserType;

/** Maps the stored user type to the role names the current screens already understand. */
public final class UserTypes {

    private UserTypes() {
    }

    public static UserType fromLegacyRole(String role, Client.ClientType clientType) {
        boolean vendor = clientType == Client.ClientType.VENDOR;
        return switch (role) {
            case "ORGANISATION_ADMIN" -> UserType.ORG_ADMIN;
            case "MAKER" -> UserType.ORG_MAKER;
            case "CHECKER" -> UserType.ORG_CHECKER;
            case "VIEWER" -> UserType.ORG_USER;
            case "SALES" -> UserType.ORG_SALES;
            case "CLIENT_ADMIN" -> vendor ? UserType.VENDOR_ADMIN : UserType.CORP_ADMIN;
            case "CLIENT_OPERATOR", "BRANCH_USER" -> vendor ? UserType.VENDOR_USER : UserType.CORP_USER;
            default -> throw new BadRequestException("Unknown role " + role);
        };
    }

    public static String legacyRole(UserType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ORG_SUPERADMIN, ORG_ADMIN -> "ORGANISATION_ADMIN";
            case ORG_USER -> "VIEWER";
            case ORG_MAKER -> "MAKER";
            case ORG_CHECKER -> "CHECKER";
            case ORG_SALES -> "SALES";
            case CORP_ADMIN, VENDOR_ADMIN -> "CLIENT_ADMIN";
            case CORP_USER, VENDOR_USER -> "CLIENT_OPERATOR";
        };
    }

    public static String organisationId(Client client) {
        return client == null ? AppUser.PAYTRIX : client.getId().toString();
    }
}
