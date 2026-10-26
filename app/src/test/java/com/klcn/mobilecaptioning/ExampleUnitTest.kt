package com.klcn.mobilecaptioning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPermissionTest {
    @Test
    fun guestCanOnlyCreateCaptionsAndReadModelInformation() {
        val guest = AuthSession(account = null)

        assertTrue(guest.can(AppResource.CAPTION, PermissionAction.READ))
        assertTrue(guest.can(AppResource.CAPTION, PermissionAction.CREATE))
        assertTrue(guest.can(AppResource.MODEL_INSIGHTS, PermissionAction.READ))
        assertFalse(guest.can(AppResource.HISTORY, PermissionAction.READ))
        assertFalse(guest.can(AppResource.FAVORITES, PermissionAction.READ))
        assertFalse(guest.can(AppResource.USERS, PermissionAction.READ))
        assertFalse(guest.can(AppResource.ROLES, PermissionAction.READ))
    }

    @Test
    fun ordinaryUserDoesNotReceiveUserOrRoleAdministrationPermissions() {
        val permissions = defaultUserPermissions()

        assertFalse(permissions.any { it.startsWith("${AppResource.USERS}:") })
        assertFalse(permissions.any { it.startsWith("${AppResource.ROLES}:") })
        assertTrue(permissionKey(AppResource.HISTORY, PermissionAction.READ) in permissions)
        assertTrue(permissionKey(AppResource.FAVORITES, PermissionAction.UPDATE) in permissions)
    }

    @Test
    fun superAdminAlwaysPassesPermissionChecks() {
        val account = UserAccount(
            id = "admin",
            displayName = "Admin",
            username = "admin",
            email = "admin@example.com",
            passwordHash = null,
            passwordSalt = null,
            provider = AuthProvider.EMAIL,
            role = UserRole.SUPER_ADMIN,
            permissions = emptySet(),
        )
        val session = AuthSession(account)

        AppResource.entries.forEach { resource ->
            PermissionAction.entries.forEach { action ->
                assertTrue(session.can(resource, action))
            }
        }
    }
}
