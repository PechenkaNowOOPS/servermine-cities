package ru.servermine.cities.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public view of a city-owned market listing. Item bytes are a Paper ItemStack serialization. */
public record CityMarketListing(UUID id, UUID cityId, String cityName, UUID sellerId,
                                String itemName, byte[] itemData, long price, Instant createdAt, CityMarketState state) {
    public CityMarketListing {
        Objects.requireNonNull(id); Objects.requireNonNull(cityId); Objects.requireNonNull(cityName);
        Objects.requireNonNull(sellerId); Objects.requireNonNull(itemName); Objects.requireNonNull(createdAt); Objects.requireNonNull(state);
        itemData = Objects.requireNonNull(itemData).clone();
        if (itemName.isBlank() || itemData.length == 0 || price <= 0) throw new IllegalArgumentException("Invalid market listing");
    }
    @Override public byte[] itemData() { return itemData.clone(); }
}
