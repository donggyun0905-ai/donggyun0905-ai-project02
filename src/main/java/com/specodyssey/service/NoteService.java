package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.TransactionUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

/**
 * 로드맵 화면의 연습장(노트) — 자유롭게 쓰고 저장하면 서류 보관함(DOCUMENTS)에 텍스트 파일로 보관한다
 * (2026-10-01 사용자 요청). 새 테이블 없이 기존 DOCUMENTS를 재사용하고, 사용자당 노트 한 개만
 * 유지한다 — 저장할 때마다 새 파일로 교체하고 이전 행·파일은 지운다(보관함에 저장 횟수만큼 쌓이는 것 방지).
 */
public class NoteService {

    public static final String NOTE_FILE_NAME = "연습장 노트.txt";
    // 보관함이 노트 하나로 무한히 커지는 걸 막는 상한(글자 수).
    public static final int MAX_LENGTH = 20_000;

    private final DocumentDao documentDao = new DocumentDao();
    private final DocumentContentService documentContentService = new DocumentContentService();

    public String load(Long userId) throws SQLException {
        DocumentDto note = findNote(userId);
        if (note == null) {
            return "";
        }
        try {
            return documentContentService.readText(note);
        } catch (IOException e) {
            return ""; // 파일이 사라진 경우 — 빈 노트로 시작하고 다음 저장 때 다시 만들어진다.
        }
    }

    public void save(Long userId, String text) throws SQLException, IOException {
        String body = text == null ? "" : text;
        if (body.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("노트는 " + MAX_LENGTH + "자까지 저장할 수 있습니다.");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FileStorageUtil.SavedFile saved = FileStorageUtil.save(new ByteArrayInputStream(bytes), NOTE_FILE_NAME);

        DocumentDto document = new DocumentDto();
        document.setUserId(userId);
        document.setOriginalName(NOTE_FILE_NAME);
        document.setStoredName(saved.getStoredName());
        document.setFilePath(saved.getFilePath());
        document.setFileData(saved.getData()); // 파일 내용은 DB(DOCUMENTS.file_data)에
        document.setFileSize(saved.getFileSize());
        document.setMimeType("text/plain; charset=UTF-8");
        document.setChecksum(saved.getChecksum());

        DocumentDto previous = findNote(userId);
        try {
            TransactionUtil.runInTransaction(conn -> {
                documentDao.insert(conn, document);
                if (previous != null) {
                    documentDao.delete(conn, previous.getId(), userId);
                }
                return null;
            });
        } catch (SQLException e) {
            FileStorageUtil.deleteQuietly(saved.getFilePath());
            throw e;
        }
        if (previous != null) {
            FileStorageUtil.deleteQuietly(previous.getFilePath());
        }
    }

    // findByUserId가 id 내림차순이라 첫 번째가 가장 최근 노트다.
    private DocumentDto findNote(Long userId) throws SQLException {
        List<DocumentDto> documents = documentDao.findByUserId(userId);
        return documents.stream()
                .filter(d -> NOTE_FILE_NAME.equals(d.getOriginalName()))
                .findFirst().orElse(null);
    }
}
