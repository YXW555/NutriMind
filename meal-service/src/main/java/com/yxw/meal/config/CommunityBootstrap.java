package com.yxw.meal.config;

import com.yxw.meal.entity.CommunityPost;
import com.yxw.meal.service.CommunityService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.bootstrap.sample-community-enabled", havingValue = "true", matchIfMissing = true)
public class CommunityBootstrap implements ApplicationRunner {

    private final CommunityService communityService;

    public CommunityBootstrap(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (communityService.countPosts() > 0) {
            return;
        }
        communityService.saveAll(List.of(
                sample("健康达人_1", "减脂", "今天试了牛油果鸡胸肉沙拉，口感很清爽，饱腹感比想象中更强，适合午餐。"),
                sample("健身上班族", "增肌", "训练日我会把米饭放在训练前后吃，鸡胸肉和牛奶固定安排，力量状态明显更稳。"),
                sample("素食研究员", "素食", "北豆腐加西兰花和藜麦，做成一锅端真的很省事，素食也能把蛋白质吃够。"),
                sample("厨房十分钟", "快手菜", "晚上加班回家就做番茄鸡蛋燕麦粥，十分钟能出锅，热量也不会太夸张。")
        ));
    }

    private CommunityPost sample(String authorName, String tag, String content) {
        CommunityPost post = new CommunityPost();
        post.setAuthorName(authorName);
        post.setTag(tag);
        post.setTitle(tag + "日常分享");
        post.setContent(content);
        post.setLikeCount(0);
        return post;
    }
}
