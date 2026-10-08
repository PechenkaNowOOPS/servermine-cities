package ru.servermine.cities.core;

import ru.servermine.cities.api.CityView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Minimal storage projection used by the periodic tax worker. */
public record CityTaxDue(UUID cityId, String cityName, long dailyAmount, Instant cycle,
                         List<CityView.ResidentView> residents) {
    public CityTaxDue {
        residents = List.copyOf(residents);
    }
}
