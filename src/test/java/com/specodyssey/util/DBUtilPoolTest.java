package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 커넥션 풀: 반납한 연결을 재사용하되 트랜잭션·autoCommit 상태가 다음 사용자에게 새지 않는다. */
class DBUtilPoolTest {

    @Test
    void 반납한_연결은_닫히지_않고_재사용되며_열린_트랜잭션은_롤백된다() throws Exception {
        long id1;
        try (Connection c = DBUtil.getConnection()) {
            id1 = connectionId(c);
            c.setAutoCommit(false); // 커밋도 롤백도 안 하고 반납
        }
        try (Connection c = DBUtil.getConnection()) {
            assertEquals(id1, connectionId(c), "같은 물리 연결을 다시 받는다");
            assertTrue(c.getAutoCommit(), "autoCommit이 복구돼 있어야 한다");
        }
    }

    @Test
    void 반납한_뒤_다시_쓰거나_두_번_닫아도_안전하다() throws Exception {
        Connection c = DBUtil.getConnection();
        c.close();
        assertTrue(c.isClosed());
        c.close(); // 두 번 닫아도 풀이 꼬이지 않는다
        assertThrows(SQLException.class, c::createStatement);

        try (Connection a = DBUtil.getConnection(); Connection b = DBUtil.getConnection()) {
            assertFalse(connectionId(a) == connectionId(b), "동시에 빌린 두 연결은 서로 달라야 한다");
        }
    }

    private static long connectionId(Connection c) throws SQLException {
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT CONNECTION_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
