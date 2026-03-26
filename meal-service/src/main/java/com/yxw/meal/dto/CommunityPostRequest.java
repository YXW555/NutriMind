package com.yxw.meal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CommunityPostRequest {

    private String authorName;

    private String title;

    @NotBlank(message = "分享内容不能为空")
    private String content;

    private String tag;

    @Size(max = 3, message = "最多上传 3 张图片")
    private List<String> imageUrls;
}
