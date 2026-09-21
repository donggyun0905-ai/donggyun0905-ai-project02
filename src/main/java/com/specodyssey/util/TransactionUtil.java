package com.specodyssey.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 여러 테이블을 한 트랜잭션으로 묶어 실행하는 공용 헬퍼.
 * 관련 규칙: claude.md "여러 테이블을 함께 변경하면 트랜잭션으로 묶는다"
 * 커넥션 발급·커밋·롤백·autoCommit 복구를 여기서 책임지고, 실제 SQL은 호출부의 람다가 담당한다.
 */
public final class TransactionUtil {

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection conn) throws SQLException;
    }

    private TransactionUtil() {
    }

    public static <T> T runInTransaction(SqlFunction<T> action) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                // RuntimeException(NPE 등)도 반드시 롤백해야 한다 — 여기서 SQLException만 잡으면
                // finally의 setAutoCommit(true)가 그 시점까지의 작업을 그대로 커밋해버린다.
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
