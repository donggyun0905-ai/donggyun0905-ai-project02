package com.specodyssey.util;

public interface LlmClient {
    /** 프롬프트를 보내고 응답 JSON을 type으로 파싱해 돌려준다 */
    <T> T completeJson(String prompt, Class<T> type) throws ExternalApiClient.ExternalApiException;
}
