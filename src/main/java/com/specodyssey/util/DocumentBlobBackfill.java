package com.specodyssey.util;

import com.specodyssey.dao.DocumentDao;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 서버를 켤 때 한 번 — DB로 옮기기 전에 올린 서류(DOCUMENTS.file_data가 빈 행) 중 이 PC 디스크에 파일이 있는 것을
 * DB로 옮긴다(2026-10-07, sql/32). 업로드 폴더가 PC마다 따로라, 팀원이 각자 한 번씩 서버를 켜면 각자 올렸던 서류가 채워진다.
 * 디스크 파일은 지우지 않는다(되돌릴 수 있게). 이미 다른 PC가 채운 행은 건너뛴다.
 */
@WebListener
public class DocumentBlobBackfill implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(DocumentBlobBackfill.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        Thread thread = new Thread(DocumentBlobBackfill::runSafely, "document-blob-backfill");
        thread.setDaemon(true);
        thread.start();
    }

    // 실패해도 서버 기동을 막지 않는다 — 옮기지 못한 서류는 지금처럼 디스크에서 읽힌다
    static void runSafely() {
        try {
            int moved = run(new DocumentDao());
            if (moved > 0) {
                LOG.info(() -> "디스크에 있던 서류 " + moved + "개를 DB로 옮겼습니다");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "디스크 서류를 DB로 옮기던 중 실패 — 다음 기동 때 다시 시도", e);
        }
    }

    static int run(DocumentDao documentDao) throws Exception {
        int moved = 0;
        for (Map.Entry<Long, String> row : documentDao.findDiskOnlyPaths().entrySet()) {
            if (!FileStorageUtil.existsOnDisk(row.getValue())) {
                continue; // 다른 PC에서 올린 서류 — 그 PC가 켜질 때 옮겨진다
            }
            try {
                if (documentDao.fillFileData(row.getKey(), FileStorageUtil.readBytes(row.getValue()))) {
                    moved++;
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "서류 " + row.getKey() + "를 DB로 옮기지 못함 — 건너뜀", e);
            }
        }
        return moved;
    }
}
