package com.ella.backend.services.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Feature.Type;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GoogleVisionOcrService {

    @Value("${google.cloud.vision.enabled:false}")
    private boolean enabled;

    @Value("${google.cloud.project-id:}")
    private String projectId;

    @Value("${google.cloud.vision.credentials-path:}")
    private String credentialsPath;

    public boolean isEnabled() {
        return enabled;
    }

    public String extractTextFromImage(byte[] imageBytes) {
        if (!enabled) {
            throw new IllegalStateException("Google Cloud Vision OCR está desabilitado (google.cloud.vision.enabled=false)");
        }
        if (imageBytes == null || imageBytes.length == 0) return "";

        long startMs = System.currentTimeMillis();
        log.info("[GoogleVision]: Processando imagem bytes={} projectId='{}'", imageBytes.length, safe(projectId));

        try (ImageAnnotatorClient client = createClient()) {
            Image image = Image.newBuilder().setContent(ByteString.copyFrom(imageBytes)).build();
            Feature feature = Feature.newBuilder().setType(Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            BatchAnnotateImagesResponse response = client.batchAnnotateImages(List.of(request));
            if (response == null || response.getResponsesCount() == 0) {
                log.info("[GoogleVision]: Resposta vazia (0 responses)");
                return "";
            }

            AnnotateImageResponse r = response.getResponses(0);
            if (r.hasError()) {
                String message = r.getError().getMessage();
                throw new OcrException("Google Vision retornou erro: " + message, null);
            }

            String text = "";
            if (r.hasFullTextAnnotation()) {
                text = r.getFullTextAnnotation().getText();
            } else if (r.getTextAnnotationsCount() > 0) {
                text = r.getTextAnnotations(0).getDescription();
            }

            text = text == null ? "" : text;
            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[GoogleVision]: ✅ Texto extraído: {} caracteres em {}ms", text.length(), elapsedMs);
            return text;
        } catch (OcrException e) {
            // já está normalizado
            log.warn("[GoogleVision]: Falha ao extrair texto (OcrException): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[GoogleVision]: Falha ao extrair texto: {}", e.toString());
            throw new OcrException("Falha ao executar OCR (Google Vision)", e);
        }
    }

    public String extractTextFromFile(String filePath) {
        if (!enabled) {
            throw new IllegalStateException("Google Cloud Vision OCR está desabilitado (google.cloud.vision.enabled=false)");
        }
        String p = filePath == null ? "" : filePath.trim();
        if (p.isEmpty()) return "";

        try {
            byte[] bytes = Files.readAllBytes(Path.of(p));
            return extractTextFromImage(bytes);
        } catch (IOException e) {
            log.warn("[GoogleVision]: Erro ao ler arquivo '{}': {}", p, e.toString());
            throw new OcrException("Falha ao ler arquivo para OCR (Google Vision)", e);
        }
    }

    private ImageAnnotatorClient createClient() throws IOException {
        // Prefer ADC (GOOGLE_APPLICATION_CREDENTIALS). Se credentials-path estiver setado e existir, usa explicitamente.
        String path = credentialsPath == null ? "" : credentialsPath.trim();
        if (!path.isEmpty()) {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(Files.newInputStream(p))
                        .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
                ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();
                log.info("[GoogleVision]: Usando credentials-path='{}'", path);
                return ImageAnnotatorClient.create(settings);
            }
            log.warn("[GoogleVision]: credentials-path não encontrado: '{}' (usando ADC via env)", path);
        }

        return ImageAnnotatorClient.create();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
