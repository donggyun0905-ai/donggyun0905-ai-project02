package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HikariCP 커넥션 풀: 연결을 재사용하되 트랜잭션·autoCommit 상태가 다음 사용자에게 새지 않는다. */
class DBUtilPoolTest {

    @Test
    void 반복해서_빌려도_물리_연결은_풀_크기를_넘지_않는다() throws Exception {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            try (Connection c = DBUtil.getConnection()) {
                ids.add(connectionId(c));
            }
        }
        assertTrue(ids.size() <= 10, "40번 빌렸는데 물리 연결이 " + ids.size() + "개 — 재사용되지 않는다");
    }

    @Test
    void 커밋하지_않고_반납한_트랜잭션은_롤백되고_autoCommit이_복구된다() throws Exception {
        String marker = "pooltest_" + System.nanoTime();
        try (Connection c = DBUtil.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO SKILL (skill_name, category) VALUES ('" + marker + "', 'TEST')");
            }
            // 커밋도 롤백도 안 하고 반납
        }
        for (int i = 0; i < 15; i++) { // 어떤 연결을 받아도 깨끗해야 한다
            try (Connection c = DBUtil.getConnection(); Statement st = c.createStatement()) {
                assertTrue(c.getAutoCommit(), "autoCommit이 복구돼 있어야 한다");
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM SKILL WHERE skill_name = '" + marker + "'")) {
                    rs.next();
                    assertEquals(0, rs.getInt(1), "반납한 미커밋 변경이 보이면 안 된다");
                }
            }
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

    @Test
    void 여러_스레드가_동시에_빌려도_서로_다른_연결을_받고_모두_반납된다() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch allHolding = new CountDownLatch(threads);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try (Connection c = DBUtil.getConnection()) {
                    allHolding.countDown();
                    allHolding.await(); // 전부 동시에 쥐고 있는 상태를 만든다
                    return connectionId(c);
                }
            }));
        }
        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        pool.shutdown();
        assertEquals(threads, ids.size(), "동시에 쥔 연결은 서로 달라야 한다");
        try (Connection c = DBUtil.getConnection()) { // 반납됐으니 다시 빌릴 수 있다
            assertTrue(c.isValid(2));
        }
    }

    private static long connectionId(Connection c) throws SQLException {
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT CONNECTION_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
