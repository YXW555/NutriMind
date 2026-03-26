package com.yxw.food.config;

import com.yxw.food.entity.FoodBasic;
import com.yxw.food.service.FoodBasicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "app.bootstrap.sample-foods-enabled", havingValue = "true", matchIfMissing = true)
public class FoodCatalogBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FoodCatalogBootstrap.class);
    private static final String DEFAULT_UNIT = "100克";

    private final FoodBasicService foodBasicService;

    public FoodCatalogBootstrap(FoodBasicService foodBasicService) {
        this.foodBasicService = foodBasicService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<FoodBasic> samples = buildSamples();
        Set<String> sampleNames = samples.stream()
                .map(FoodBasic::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingNames = foodBasicService.lambdaQuery()
                .in(FoodBasic::getName, sampleNames)
                .list()
                .stream()
                .map(FoodBasic::getName)
                .collect(Collectors.toSet());

        List<FoodBasic> missingFoods = samples.stream()
                .filter(food -> !existingNames.contains(food.getName()))
                .toList();

        if (missingFoods.isEmpty()) {
            log.info("Food catalog bootstrap skipped because all core foods already exist.");
            return;
        }

        foodBasicService.saveBatch(missingFoods);
        log.info("Inserted {} core foods for recognition and manual logging.", missingFoods.size());
    }

    private List<FoodBasic> buildSamples() {
        return List.of(
                sample("鸡胸肉", "蛋白质", "690100000001", 165, 31.0, 3.6, 0.0),
                sample("燕麦片", "主食", "690100000002", 389, 16.9, 6.9, 66.3),
                sample("米饭", "主食", "690100000003", 116, 2.6, 0.3, 25.9),
                sample("面条", "主食", "690100000004", 138, 4.5, 0.7, 27.0),
                sample("西兰花", "蔬菜", "690100000005", 34, 2.8, 0.4, 6.6),
                sample("鸡蛋", "蛋白质", "690100000006", 144, 13.3, 8.8, 1.3),
                sample("纯牛奶", "饮品", "690100000007", 62, 3.2, 3.5, 4.8),
                sample("牛奶", "饮品", "690100000008", 62, 3.2, 3.5, 4.8),
                sample("香蕉", "水果", "690100000009", 93, 1.4, 0.2, 22.8),
                sample("希腊酸奶", "饮品", "690100000010", 98, 9.0, 4.0, 5.0),
                sample("酸奶", "饮品", "690100000011", 72, 2.5, 2.7, 9.3),
                sample("全麦面包", "主食", "690100000012", 246, 12.0, 3.5, 41.0),
                sample("面包", "主食", "690100000013", 265, 8.9, 3.2, 49.0),
                sample("牛油果", "水果", "690100000014", 171, 2.0, 15.3, 8.5),
                sample("三文鱼", "蛋白质", "690100000015", 206, 22.0, 12.0, 0.0),
                sample("北豆腐", "蛋白质", "690100000016", 81, 8.1, 4.0, 2.0),
                sample("豆腐", "蛋白质", "690100000017", 76, 8.0, 4.8, 1.9),
                sample("沙拉", "轻食", "690100000018", 120, 4.5, 6.0, 12.0),
                sample("鸡肉沙拉", "轻食", "690100000019", 146, 11.5, 6.5, 10.8),
                sample("三明治", "轻食", "690100000020", 256, 12.0, 8.0, 30.0),
                sample("全麦三明治", "轻食", "690100000021", 239, 13.0, 7.5, 28.0),
                sample("炒饭", "复合餐", "690100000022", 188, 4.7, 6.1, 28.9),
                sample("蛋炒饭", "复合餐", "690100000023", 194, 6.3, 6.7, 27.0),
                sample("饺子", "主食", "690100000024", 231, 8.1, 7.1, 33.0),
                sample("水饺", "主食", "690100000025", 191, 7.6, 4.8, 29.4),
                sample("馒头", "主食", "690100000026", 223, 7.0, 1.1, 46.9),
                sample("包子", "主食", "690100000027", 230, 8.0, 6.5, 34.0),
                sample("苹果", "水果", "690100000028", 53, 0.3, 0.2, 13.5),
                sample("橙子", "水果", "690100000029", 48, 0.8, 0.2, 11.1),
                sample("黄瓜", "蔬菜", "690100000030", 16, 0.8, 0.2, 2.9),
                sample("番茄", "蔬菜", "690100000031", 20, 0.9, 0.2, 4.0),
                sample("土豆", "蔬菜", "690100000032", 81, 2.0, 0.2, 17.8),
                sample("玉米", "主食", "690100000033", 106, 4.0, 1.2, 22.8),
                sample("牛肉", "蛋白质", "690100000034", 125, 20.3, 4.2, 0.0),
                sample("鱼", "蛋白质", "690100000035", 123, 18.5, 5.0, 0.0),
                sample("虾", "蛋白质", "690100000036", 87, 18.6, 0.8, 0.2),
                sample("炒面", "复合餐", "690100000037", 175, 5.8, 7.2, 22.0),
                sample("粥", "主食", "690100000038", 46, 1.0, 0.3, 9.8),
                sample("酸辣土豆丝", "家常菜", "690100000039", 92, 1.7, 3.5, 13.9),
                sample("西红柿炒蛋", "家常菜", "690100000040", 86, 4.2, 5.8, 4.8),
                sample("宫保鸡丁", "家常菜", "690100000041", 179, 15.6, 10.5, 7.0),
                sample("清炒时蔬", "家常菜", "690100000042", 68, 2.1, 3.8, 6.3)
        );
    }

    private FoodBasic sample(String name,
                             String category,
                             String barcode,
                             double calories,
                             double protein,
                             double fat,
                             double carbohydrate) {
        FoodBasic food = new FoodBasic();
        food.setName(name);
        food.setCategory(category);
        food.setUnit(DEFAULT_UNIT);
        food.setCalories(BigDecimal.valueOf(calories));
        food.setProtein(BigDecimal.valueOf(protein));
        food.setFat(BigDecimal.valueOf(fat));
        food.setCarbohydrate(BigDecimal.valueOf(carbohydrate));
        food.setFiber(BigDecimal.ZERO);
        food.setStatus(1);
        return food;
    }
}
