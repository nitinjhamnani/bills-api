package com.b2bfintech.app.security;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.common.security.Portal;
import com.b2bfintech.organisation.entity.AppUser.UserType;
import com.b2bfintech.organisation.entity.Client.ClientType;
import com.b2bfintech.organisation.entity.UserTypes;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTypesAndScopeGuardTest {

    @Test
    void mapsEveryUserTypeToDistinctPortalAndRole() {
        assertEquals(Portal.PLATFORM, UserTypes.portal(UserType.SUPER_ADMIN));
        assertEquals("SUPER_ADMIN", UserTypes.tokenRole(UserType.SUPER_ADMIN, false));

        assertEquals(Portal.PLATFORM, UserTypes.portal(UserType.ORG_ADMIN));
        assertEquals("ORGANISATION_ADMIN", UserTypes.tokenRole(UserType.ORG_ADMIN, false));

        assertEquals(Portal.CORPORATE, UserTypes.portal(UserType.CORP_ADMIN));
        assertEquals("CORP_ADMIN", UserTypes.tokenRole(UserType.CORP_ADMIN, false));

        assertEquals(Portal.CORPORATE, UserTypes.portal(UserType.CORP_USER));
        assertEquals("CORP_OPERATOR", UserTypes.tokenRole(UserType.CORP_USER, false));
        assertEquals("BRANCH_USER", UserTypes.tokenRole(UserType.CORP_USER, true));

        assertEquals(Portal.VENDOR, UserTypes.portal(UserType.VENDOR_ADMIN));
        assertEquals("VENDOR_ADMIN", UserTypes.tokenRole(UserType.VENDOR_ADMIN, false));

        assertEquals(Portal.VENDOR, UserTypes.portal(UserType.VENDOR_USER));
        assertEquals("VENDOR_OPERATOR", UserTypes.tokenRole(UserType.VENDOR_USER, false));
    }

    @Test
    void rejectsLegacyAndCrossPortalRoleAssignments() {
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("CLIENT_ADMIN", ClientType.CORPORATE));
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("CLIENT_ADMIN", ClientType.VENDOR));
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("VENDOR_ADMIN", ClientType.CORPORATE));
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("CORP_ADMIN", ClientType.VENDOR));
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("BRANCH_USER", ClientType.VENDOR));
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("ORGANISATION_ADMIN", ClientType.CORPORATE));
        assertThrows(BadRequestException.class, () -> UserTypes.fromRole("CORP_ADMIN", null));
    }

    @Test
    void enforcesStrictTenantAndPortalIsolationInClientScopeGuard() {
        AuthenticatedUser corp1Admin = new AuthenticatedUser(
                "US0001", "9000000001", null, "CORP_ADMIN", "Corp 1",
                "CORP0001", "US0001", null, UUID.randomUUID(), Portal.CORPORATE, "CORP_ADMIN");
        AuthenticatedUser vendor1Admin = new AuthenticatedUser(
                "US0002", "9000000002", null, "VENDOR_ADMIN", "Vendor 1",
                "VEND0001", "US0002", null, UUID.randomUUID(), Portal.VENDOR, "VENDOR_ADMIN");
        AuthenticatedUser platformAdmin = new AuthenticatedUser(
                "US0003", "8123941123", null, "SUPER_ADMIN", "Admin",
                null, "US0003", null, UUID.randomUUID(), Portal.PLATFORM, "SUPER_ADMIN");

        assertDoesNotThrow(() -> ClientScopeGuard.assertCorporateAccess(corp1Admin, "CORP0001"));
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertCorporateAccess(corp1Admin, "CORP0002"));
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertVendorAccess(corp1Admin, "CORP0001"));

        assertDoesNotThrow(() -> ClientScopeGuard.assertVendorAccess(vendor1Admin, "VEND0001"));
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertVendorAccess(vendor1Admin, "VEND0002"));
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertCorporateAccess(vendor1Admin, "VEND0001"));
        assertThrows(AccessDeniedException.class, () -> ClientScopeGuard.assertCorporateAccess(vendor1Admin, "CORP0001"));

        assertDoesNotThrow(() -> ClientScopeGuard.assertCorporateAccess(platformAdmin, "CORP0001"));
        assertDoesNotThrow(() -> ClientScopeGuard.assertVendorAccess(platformAdmin, "VEND0001"));
    }
}
