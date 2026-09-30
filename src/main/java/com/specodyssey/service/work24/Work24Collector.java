package com.specodyssey.service.work24;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;

import java.sql.SQLException;

/**
 * 고용24 API 하나의 수집 단위.
 * prepare()는 호출·파싱만 하고 DB에 쓰지 않는다 → ApiUpdater --dry-run에서 무엇이 들어갈지 미리 볼 수 있다.
 */
public interface Work24Collector {

    /** 로그용 이름 (예: "학과정보") */
    String name();

    /** 이 API의 캐시 request_key 접두어 (예: "213L01:") — "오늘 이미 받았는지" 판단에 쓴다 */
    String keyPrefix();

    Prepared prepare() throws ExternalApiException, SQLException;

    interface Prepared {
        /** 저장될 내용 요약 (dry-run 출력·로그용) */
        String summary();

        /** DB에 반영한다. 반영한 결과 한 줄 요약을 돌려준다. */
        String save() throws SQLException;
    }
}
