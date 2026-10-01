package com.specodyssey.util;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 문장을 768차원 의미 벡터로 바꾸는 로컬 임베딩 (ko-sroberta-multitask, DJL + ONNX Runtime).
 * 관련 요구사항: TD-1 임베딩 시맨틱 매칭
 *
 * 모델 폴더(EMBEDDING_MODEL_DIR)에 model.onnx와 tokenizer.json이 있어야 한다. 모델 파일은 커밋하지 않는다.
 * 문장 벡터는 토큰 벡터들의 평균(mean pooling)이다 — 모델 배포본의 1_Pooling/config.json 설정과 같다.
 * ONNX Runtime 엔진은 NDArray 연산을 지원하지 않아서 평균은 Java 배열로 직접 계산한다.
 *
 * 모델 로딩이 느리므로(수 초) 한 번 만들어 재사용하고, 다 쓰면 close()한다.
 * Predictor가 스레드 안전하지 않아 embed()는 synchronized다.
 */
public class LocalEmbedder implements AutoCloseable {

    /** SKILL.embedding_model에 저장할 모델 식별자 */
    public static final String MODEL_NAME = "ko-sroberta-multitask";
    public static final int DIMENSION = 768;
    public static final String MODEL_DIR_KEY = "EMBEDDING_MODEL_DIR";

    // 스킬명·짧은 문장 용도라 128토큰이면 충분하다 (모델 최대는 512)
    private static final int MAX_TOKENS = 128;

    private final HuggingFaceTokenizer tokenizer;
    private final ZooModel<NDList, NDList> model;
    private final Predictor<NDList, NDList> predictor;
    private final boolean needsTokenTypeIds;

    public LocalEmbedder(Path modelDir) throws IOException, ModelNotFoundException, MalformedModelException {
        Path modelFile = modelDir.resolve("model.onnx");
        Path tokenizerFile = modelDir.resolve("tokenizer.json");
        if (!Files.isRegularFile(modelFile) || !Files.isRegularFile(tokenizerFile)) {
            throw new IOException("임베딩 모델 폴더에 model.onnx와 tokenizer.json이 필요합니다: " + modelDir);
        }
        tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerFile)
                .optMaxLength(MAX_TOKENS)
                .optTruncation(true)
                .optPadding(false)
                .build();
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(modelFile)
                .optEngine("OnnxRuntime")
                .optTranslator(new NoopTranslator())
                .build();
        try {
            model = criteria.loadModel();
        } catch (IOException | ModelNotFoundException | MalformedModelException | RuntimeException e) {
            tokenizer.close();
            throw e;
        }
        predictor = model.newPredictor();
        // ONNX 내보내기 방식에 따라 token_type_ids 입력이 있을 수도 없을 수도 있다
        needsTokenTypeIds = model.describeInput().keys().contains("token_type_ids");
    }

    /** .env(또는 환경변수)의 EMBEDDING_MODEL_DIR로 만든다. 값이 없으면 IOException. */
    public static LocalEmbedder fromConfig() throws IOException, ModelNotFoundException, MalformedModelException {
        String dir = AppConfig.get(MODEL_DIR_KEY);
        if (dir == null) {
            throw new IOException(MODEL_DIR_KEY + "가 설정되지 않았습니다 (.env 또는 환경변수)");
        }
        return new LocalEmbedder(Path.of(dir));
    }

    /** 문장 하나를 길이 768 벡터로 바꾼다. 빈 입력은 IllegalArgumentException. */
    public synchronized float[] embed(String text) throws TranslateException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩할 문장이 비어 있습니다");
        }
        Encoding encoding = tokenizer.encode(text.trim());
        long[] ids = encoding.getIds();
        long[] mask = encoding.getAttentionMask();
        Shape shape = new Shape(1, ids.length);

        try (NDManager manager = model.getNDManager().newSubManager()) {
            NDList inputs = new NDList(named(manager.create(ids, shape), "input_ids"),
                    named(manager.create(mask, shape), "attention_mask"));
            if (needsTokenTypeIds) {
                inputs.add(named(manager.create(encoding.getTypeIds(), shape), "token_type_ids"));
            }
            NDList outputs = predictor.predict(inputs);
            // 첫 번째 출력 = last_hidden_state [1, 토큰 수, 768]
            float[] tokenVectors = outputs.get(0).toFloatArray();
            if (tokenVectors.length != ids.length * DIMENSION) {
                throw new TranslateException("예상과 다른 모델 출력 크기: " + outputs.get(0).getShape());
            }
            return meanPool(tokenVectors, mask);
        }
    }

    /** 코사인 유사도 (-1~1). 길이가 다르면 IllegalArgumentException. */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("벡터 길이가 다릅니다: " + a.length + " vs " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public void close() {
        predictor.close();
        model.close();
        tokenizer.close();
    }

    // attention_mask가 1인 토큰만 평균낸다 (padding을 끄므로 지금은 전부 1이지만 계약대로 따른다)
    private static float[] meanPool(float[] tokenVectors, long[] mask) {
        int tokenCount = tokenVectors.length / DIMENSION;
        float[] sum = new float[DIMENSION];
        int counted = 0;
        for (int t = 0; t < tokenCount; t++) {
            if (mask[t] == 0) {
                continue;
            }
            for (int d = 0; d < DIMENSION; d++) {
                sum[d] += tokenVectors[t * DIMENSION + d];
            }
            counted++;
        }
        for (int d = 0; d < DIMENSION; d++) {
            sum[d] /= Math.max(counted, 1);
        }
        return sum;
    }

    private static NDArray named(NDArray array, String name) {
        array.setName(name);
        return array;
    }
}
