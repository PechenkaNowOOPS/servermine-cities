package ru.servermine.cities.api;

/** City-wide rules. Defaults preserve the server's existing protection behavior. */
public record CityLaws(boolean pvpAllowed, boolean outsiderInteractionsAllowed,
                       boolean fireAllowed, boolean explosionsAllowed) {
    public static final CityLaws DEFAULT = new CityLaws(true, false, false, false);
}
