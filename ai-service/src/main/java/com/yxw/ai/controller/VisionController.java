package com.yxw.ai.controller;

import com.yxw.ai.dto.FoodRecognitionResponse;
import com.yxw.ai.service.VisionRecognitionService;
import com.yxw.common.core.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/vision")
public class VisionController {

    private final VisionRecognitionService visionRecognitionService;

    public VisionController(VisionRecognitionService visionRecognitionService) {
        this.visionRecognitionService = visionRecognitionService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FoodRecognitionResponse> recognize(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(defaultValue = "3") int topK) {
        return ApiResponse.success("recognition success", visionRecognitionService.recognize(file, topK));
    }
}
