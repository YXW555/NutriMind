package com.yxw.ai.engine;

import org.springframework.web.multipart.MultipartFile;

public interface FoodRecognitionEngine {

    RecognitionEngineResult recognize(MultipartFile file, int topK);
}
