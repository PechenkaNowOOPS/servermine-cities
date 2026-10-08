package ru.servermine.cities.core;

import ru.servermine.cities.api.CityView;
import ru.servermine.cities.api.CityFoundationDraft;
import ru.servermine.cities.api.CityFoundationResult;
import ru.servermine.cities.api.CityPromotionResult;
import ru.servermine.cities.api.CityClaimResult;
import ru.servermine.cities.api.CityLeaveResult;
import ru.servermine.cities.api.CityMembershipResult;
import ru.servermine.cities.api.CityTreasuryEntry;
import ru.servermine.cities.api.CityTreasuryResult;
import ru.servermine.cities.api.CityRole;
import ru.servermine.cities.api.CityRoleResult;
import ru.servermine.cities.api.CityDiplomacyRequest;
import ru.servermine.cities.api.CityDiplomacyResult;
import ru.servermine.cities.api.CityTreatyView;
import ru.servermine.cities.api.CityDiplomacyEvent;
import ru.servermine.cities.api.CityTreasuryTransferRequest;
import ru.servermine.cities.api.CityTreasuryTransferResult;
import ru.servermine.cities.api.CityMarketCreateRequest;
import ru.servermine.cities.api.CityMarketListing;
import ru.servermine.cities.api.CityMarketPurchaseRequest;
import ru.servermine.cities.api.CityMarketResult;
import ru.servermine.cities.api.CityMarketDelivery;
import ru.servermine.cities.api.CityLawsRequest;
import ru.servermine.cities.api.CityLawsResult;
import ru.servermine.cities.api.CityTaxRequest;
import ru.servermine.cities.api.CityTaxResult;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Cities-owned persistence port. Implementations must never expose connections or mutable entities. */
public interface CityRepository {
    CompletionStage<Optional<CityView>> find(UUID cityId);

    CompletionStage<Optional<CityView>> findForPlayer(UUID playerId);

    /** OP-only test helpers; implementations must record these changes in the administrative audit. */
    CompletionStage<Optional<CityView>> adminTestGrantTreasury(UUID operationId, UUID actorId,
                                                               UUID targetPlayerId, long amount);

    CompletionStage<Optional<CityView>> adminTestSetStage(UUID operationId, UUID actorId,
                                                          UUID targetPlayerId, ru.servermine.cities.api.CityStage stage);

    CompletionStage<List<CityView>> findAllCities();

    CompletionStage<List<CityTreatyView>> diplomacyTreaties(UUID cityId);

    CompletionStage<List<CityDiplomacyEvent>> diplomacyHistory(UUID cityId, int limit);

    CompletionStage<CityDiplomacyResult> executeDiplomacy(CityDiplomacyRequest request);

    CompletionStage<CityTreasuryTransferResult> transferTreasury(CityTreasuryTransferRequest request, long maximumAmount);

    CompletionStage<List<CityMarketListing>> marketListings(UUID viewerId);
    CompletionStage<List<CityMarketListing>> myMarketListings(UUID playerId);
    CompletionStage<List<CityMarketListing>> pendingMarketListings(UUID sellerId);
    CompletionStage<CityMarketResult> createMarketListing(CityMarketCreateRequest request, long maximumPrice);
    CompletionStage<Boolean> markMarketListingRemoving(UUID listingId, UUID sellerId);
    CompletionStage<Boolean> activateMarketListing(UUID listingId, UUID sellerId);
    CompletionStage<Boolean> abortMarketListing(UUID listingId, UUID sellerId);
    CompletionStage<CityMarketResult> purchaseMarketListing(CityMarketPurchaseRequest request, int feeBasisPoints);
    CompletionStage<CityMarketResult> cancelMarketListing(UUID listingId, UUID actorId);
    CompletionStage<List<CityMarketDelivery>> marketDeliveries(UUID playerId);
    CompletionStage<Boolean> acknowledgeMarketDelivery(UUID deliveryId, UUID playerId);

    CompletionStage<CityLawsResult> updateCityLaws(CityLawsRequest request);
    CompletionStage<CityTaxResult> updateCityTaxPolicy(CityTaxRequest request);
    CompletionStage<Boolean> completeCityTaxCycle(UUID cityId, java.time.Instant expectedCollection,
                                                   java.time.Instant nextCollection);

    CompletionStage<List<CityTaxDue>> dueCityTaxes(java.time.Instant now);

    CompletionStage<CityLeaveResult> leaveCity(UUID playerId);

    CompletionStage<CityMembershipResult> inviteToCity(UUID inviterId, UUID inviteeId, String inviteeName);

    CompletionStage<CityMembershipResult> acceptCityInvite(UUID playerId, String playerName);

    CompletionStage<CityMembershipResult> kickCityMember(UUID actorId, UUID targetId, long expectedRevision);

    CompletionStage<CityRoleResult> assignMemberRole(UUID actorId, UUID targetId, CityRole role, long expectedRevision);

    CompletionStage<CityTreasuryResult> prepareTreasuryDeposit(CityTreasuryDraft draft);

    CompletionStage<Boolean> reserveTreasuryDeposit(UUID operationId);

    CompletionStage<CityTreasuryResult> applyTreasuryDeposit(CityTreasuryDraft draft);

    CompletionStage<Void> markTreasuryCommitPending(UUID operationId);

    CompletionStage<CityTreasuryResult> completeTreasuryDeposit(UUID operationId);

    CompletionStage<Void> failTreasuryDeposit(UUID operationId, boolean releasePending);

    CompletionStage<Void> markTreasuryDepositReleased(UUID operationId);

    CompletionStage<List<PendingTreasuryDeposit>> pendingTreasuryDeposits();

    CompletionStage<Void> recordTreasuryRecoveryAudit(UUID operationId, UUID actorId, String outcome);

    CompletionStage<List<CityTreasuryEntry>> treasuryHistory(UUID cityId, int limit);

    CompletionStage<Optional<CityFoundationResult>> findFoundationReplay(CityFoundationDraft draft);

    CompletionStage<CityFoundationResult> found(CityFoundationDraft draft);

    CompletionStage<CityPromotionResult> preparePromotion(CityPromotionDraft draft);

    CompletionStage<Boolean> reservePromotion(UUID operationId);

    CompletionStage<CityPromotionResult> applyPromotion(CityPromotionDraft draft);

    CompletionStage<Void> markPromotionCommitPending(UUID operationId);

    CompletionStage<CityPromotionResult> completePromotion(UUID operationId);

    CompletionStage<ru.servermine.cities.api.CityUpgradeResult> purchaseUpgrade(CityUpgradeDraft draft);

    CompletionStage<Void> failPromotion(UUID operationId, ru.servermine.cities.api.CityPromotionCode code,
                                        boolean releasePending);

    CompletionStage<Void> markPromotionReleased(UUID operationId);

    CompletionStage<CityClaimResult> prepareClaim(CityClaimDraft draft);

    CompletionStage<Boolean> reserveClaim(UUID operationId);

    CompletionStage<CityClaimResult> applyClaim(CityClaimDraft draft);

    CompletionStage<Void> markClaimCommitPending(UUID operationId);

    CompletionStage<CityClaimResult> completeClaim(UUID operationId);

    CompletionStage<Void> failClaim(UUID operationId, ru.servermine.cities.api.CityClaimCode code);

    CompletionStage<Void> markClaimReleasePending(UUID operationId, ru.servermine.cities.api.CityClaimCode code);

    CompletionStage<Void> markClaimReleased(UUID operationId);

    CompletionStage<Set<ru.servermine.cities.api.ChunkPosition>> claimedChunks(Set<ru.servermine.cities.api.ChunkPosition> candidates);
}
