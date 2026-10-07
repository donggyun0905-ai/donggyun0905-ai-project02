package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.util.FileStorageUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * 서류(DOCUMENTS) 파일 내용 읽기 — 내용은 DB(file_data)에 있다(2026-10-07).
 * DB로 옮기기 전에 올린 서류는 file_data가 비어 있고 업로드한 PC의 디스크에만 있으므로, 그때만 디스크에서 읽는다.
 * 소유자·공유 범위 확인은 호출부(서블릿·서비스)가 끝낸 뒤 부른다.
 */
public class DocumentContentService {

    private final DocumentDao documentDao = new DocumentDao();

    /** 이 서버에서 내보낼 수 있는지 — DB에 있거나, 예전 서류라면 이 PC 디스크에 있을 때 */
    public boolean exists(DocumentDto document) {
        return document != null && (document.isStoredInDb() || FileStorageUtil.existsOnDisk(document.getFilePath()));
    }

    public void writeTo(DocumentDto document, OutputStream out) throws IOException, SQLException {
        if (document.isStoredInDb() && documentDao.writeFileData(document.getId(), out)) {
            return;
        }
        FileStorageUtil.writeTo(document.getFilePath(), out);
    }

    /** 텍스트 서류(연습장 노트) — UTF-8 */
    public String readText(DocumentDto document) throws IOException, SQLException {
        if (document.isStoredInDb()) {
            byte[] data = documentDao.readFileData(document.getId());
            if (data != null) {
                return new String(data, StandardCharsets.UTF_8);
            }
        }
        return FileStorageUtil.readText(document.getFilePath());
    }
}
