package com.yxw.meal;

import com.yxw.meal.service.NutritionKnowledgeBaseService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionKnowledgeBaseServiceTests {

    @Test
    void searchReturnsRelevantNutritionReferences() {
        NutritionKnowledgeBaseService service = new NutritionKnowledgeBaseService();
        service.loadKnowledgeBase();

        List<NutritionKnowledgeBaseService.KnowledgeHit> hits = service.search("减脂期间蛋白质和热量应该怎么安排", 3);

        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(hit ->
                hit.title().contains("减脂")
                        || hit.title().contains("蛋白质")
                        || hit.section().contains("减脂")
                        || hit.section().contains("蛋白质")));
    }
}
