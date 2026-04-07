package com.yxw.ai.client;

import com.yxw.ai.config.VisionProperties;
import com.yxw.ai.dto.PythonInferenceResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class PythonInferenceClient {

    private final VisionProperties visionProperties;
    private final RestClient restClient;

    public PythonInferenceClient(VisionProperties visionProperties) {
        this.visionProperties = visionProperties;
        this.restClient = RestClient.builder()
                .baseUrl(visionProperties.getPython().getBaseUrl())
                .build();
    }

    public PythonInferenceResponse predict(MultipartFile file, int topK) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            byte[] content = file.getBytes();
            String fileName = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename()
                    : "uploaded-image.jpg";
            MediaType mediaType = StringUtils.hasText(file.getContentType())
                    ? MediaType.parseMediaType(file.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;

            builder.part("file", new ByteArrayResource(content) {
                        @Override
                        public String getFilename() {
                            return fileName;
                        }
                    })
                    .filename(fileName)
                    .contentType(mediaType);
            builder.part("top_k", topK);

            return restClient.post()
                    .uri(visionProperties.getPython().getPredictPath())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(PythonInferenceResponse.class);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read uploaded image", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("python inference service unavailable", ex);
        }
    }
}
