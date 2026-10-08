package ru.servermine.cities.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityRoleTest {
    @Test
    void grantsAreSeparatedAndDelegationCannotCrossTheActorRank() {
        assertTrue(CityRole.DEPUTY.allows(CityPermission.INVITE_MEMBERS));
        assertTrue(CityRole.DEPUTY.allows(CityPermission.PROMOTE_STAGE));
        assertTrue(CityRole.DEPUTY.allows(CityPermission.MANAGE_DIPLOMACY));
        assertTrue(CityRole.RULER.allows(CityPermission.MANAGE_DIPLOMACY));
        assertFalse(CityRole.TREASURER.allows(CityPermission.MANAGE_DIPLOMACY));
        assertTrue(CityRole.TREASURER.allows(CityPermission.TRANSFER_TREASURY));
        assertTrue(CityRole.DEPUTY.allows(CityPermission.TRANSFER_TREASURY));
        assertTrue(CityRole.DEPUTY.allows(CityPermission.MANAGE_MARKET));
        assertTrue(CityRole.DEPUTY.allows(CityPermission.MANAGE_LAWS));
        assertFalse(CityRole.TREASURER.allows(CityPermission.MANAGE_LAWS));
        assertTrue(CityRole.DEPUTY.allows(CityPermission.MANAGE_TAXES));
        assertFalse(CityRole.TREASURER.allows(CityPermission.MANAGE_TAXES));
        assertTrue(CityRole.TREASURER.allows(CityPermission.MANAGE_MARKET));
        assertFalse(CityRole.BUILDER.allows(CityPermission.TRANSFER_TREASURY));
        assertFalse(CityRole.BUILDER.allows(CityPermission.MANAGE_MARKET));
        assertFalse(CityRole.TREASURER.allows(CityPermission.CLAIM_TERRITORY));
        assertTrue(CityRole.BUILDER.allows(CityPermission.CLAIM_TERRITORY));
        assertFalse(CityRole.MEMBER.allows(CityPermission.MANAGE_ROLES));
        assertTrue(CityRole.TREASURER.allows(CityPermission.VIEW_TREASURY));
        assertFalse(CityRole.BUILDER.allows(CityPermission.VIEW_TREASURY));
        assertFalse(CityRole.assignableBy(CityRole.DEPUTY).contains(CityRole.DEPUTY));
        assertTrue(CityRole.assignableBy(CityRole.DEPUTY).contains(CityRole.BUILDER));
        assertFalse(CityRole.assignableBy(CityRole.MEMBER).contains(CityRole.BUILDER));
    }
}
