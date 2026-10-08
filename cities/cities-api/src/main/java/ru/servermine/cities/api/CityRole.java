package ru.servermine.cities.api;

import java.util.EnumSet;
import java.util.Set;

/** Built-in, persisted city positions. RULER is assigned only by founding or succession. */
public enum CityRole {
    RULER("Правитель", 100, EnumSet.allOf(CityPermission.class)),
    DEPUTY("Заместитель", 80, EnumSet.of(CityPermission.BUILD, CityPermission.INVITE_MEMBERS,
            CityPermission.KICK_MEMBERS, CityPermission.MANAGE_ROLES, CityPermission.CLAIM_TERRITORY,
            CityPermission.PROMOTE_STAGE, CityPermission.DEPOSIT_TREASURY, CityPermission.VIEW_TREASURY,
            CityPermission.MANAGE_DIPLOMACY, CityPermission.TRANSFER_TREASURY, CityPermission.MANAGE_MARKET,
            CityPermission.MANAGE_LAWS, CityPermission.MANAGE_TAXES)),
    TREASURER("Казначей", 40, EnumSet.of(CityPermission.BUILD, CityPermission.DEPOSIT_TREASURY,
            CityPermission.VIEW_TREASURY, CityPermission.TRANSFER_TREASURY, CityPermission.MANAGE_MARKET)),
    BUILDER("Строитель", 30, EnumSet.of(CityPermission.BUILD, CityPermission.CLAIM_TERRITORY,
            CityPermission.DEPOSIT_TREASURY)),
    MEMBER("Житель", 10, EnumSet.of(CityPermission.BUILD, CityPermission.DEPOSIT_TREASURY));

    private final String displayName;
    private final int rank;
    private final Set<CityPermission> permissions;

    CityRole(String displayName, int rank, Set<CityPermission> permissions) {
        this.displayName = displayName;
        this.rank = rank;
        this.permissions = Set.copyOf(permissions);
    }

    public String displayName() { return displayName; }
    public int rank() { return rank; }
    public Set<CityPermission> permissions() { return permissions; }
    public boolean allows(CityPermission permission) { return permissions.contains(permission); }

    public static CityRole fromStorage(String stored) {
        if (stored == null || stored.equalsIgnoreCase("RESIDENT")) return MEMBER;
        try { return valueOf(stored.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException unknown) { return MEMBER; }
    }

    public static Set<CityRole> assignableBy(CityRole actor) {
        if (!actor.allows(CityPermission.MANAGE_ROLES)) return Set.of();
        return EnumSet.allOf(CityRole.class).stream()
                .filter(target -> target != RULER && target.rank() < actor.rank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
