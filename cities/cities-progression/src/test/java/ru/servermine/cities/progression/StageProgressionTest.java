package ru.servermine.cities.progression;
import org.junit.jupiter.api.Test;
import ru.servermine.cities.api.CityStage;
import ru.servermine.cities.core.CityRepository;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class StageProgressionTest {
    @Test void progressionIsOrderedAndKingdomIsTerminal() {
        var stages = CityStage.values();
        for (int i=0; i<stages.length-1; i++) assertEquals(stages[i+1], StageProgression.next(stages[i]).orElseThrow());
        assertTrue(StageProgression.next(CityStage.KINGDOM).isEmpty());
    }

    @Test void configuredPoliciesExposeEveryEnabledTransitionInOrder() {
        Map<CityStage, StagePromotionPolicy> policies = new EnumMap<>(CityStage.class);
        for (CityStage stage : CityStage.values()) {
            if (stage != CityStage.KINGDOM) policies.put(stage, new StagePromotionPolicy(true, 100, 2, 4));
        }
        CityProgressionServiceImpl service = new CityProgressionServiceImpl(unusedRepository(), policies);
        for (int i = 0; i < CityStage.values().length - 1; i++) {
            CityStage stage = CityStage.values()[i];
            assertEquals(CityStage.values()[i + 1], service.nextStage(stage).orElseThrow());
            assertEquals(100, service.promotionPrice(stage));
            assertEquals(2, service.minimumResidents(stage));
            assertEquals(4, service.minimumChunks(stage));
        }
        assertTrue(service.nextStage(CityStage.KINGDOM).isEmpty());
    }

    @Test void disabledTransitionCannotBeUsed() {
        Map<CityStage, StagePromotionPolicy> policies = new EnumMap<>(CityStage.class);
        policies.put(CityStage.SETTLEMENT, new StagePromotionPolicy(false, 0, 1, 4));
        CityProgressionServiceImpl service = new CityProgressionServiceImpl(unusedRepository(), policies);
        assertTrue(service.nextStage(CityStage.SETTLEMENT).isEmpty());
    }

    private CityRepository unusedRepository() {
        return (CityRepository) Proxy.newProxyInstance(CityRepository.class.getClassLoader(),
                new Class<?>[]{CityRepository.class}, (proxy, method, args) -> {
                    throw new AssertionError("Repository should not be used for policy queries: " + method.getName());
                });
    }
}
