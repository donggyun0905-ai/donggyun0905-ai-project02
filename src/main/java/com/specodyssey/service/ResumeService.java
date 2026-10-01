package com.specodyssey.service;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.FileStorageUtil;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

/**
 * 내 프로필의 이력서 파일 — 올리기·바꾸기·삭제·조회.
 * 이력서는 사용자당 하나다. 파일 정보는 DOCUMENTS에 한 행으로 저장하고(project_id 없음),
 * USERS.resume_document_id가 그 행을 가리킨다.
 */
public class ResumeService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "hwp", "hwpx");

    private final UserDao userDao = new UserDao();
    private final DocumentDao documentDao = new DocumentDao();

    /** 이력서로 올릴 수 있는 파일 형식인지 — 디스크에 쓰기 전에 확인한다. */
    public static boolean isAllowedFile(String originalFilename) {
        if (originalFilename == null) {
            return false;
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0
                && ALLOWED_EXTENSIONS.contains(originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 지금 지정된 이력서. 없거나 그 서류가 지워졌으면 null. */
    public DocumentDto findResume(Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        if (user == null || user.getResumeDocumentId() == null) {
            return null;
        }
        DocumentDto document = documentDao.findById(user.getResumeDocumentId());
        return (document == null || !document.getUserId().equals(userId)) ? null : document;
    }

    /**
     * 이미 디스크에 저장된 파일을 이력서로 등록한다. 이전 이력서가 있으면 지운다.
     * @param document 호출부가 FileStorageUtil로 저장한 뒤 채워 넘긴 파일 정보 (userId는 여기서 채운다)
     */
    public void replaceResume(Long userId, DocumentDto document) throws SQLException {
        DocumentDto previous = findResume(userId);
        document.setUserId(userId);
        document.setProjectId(null);

        TransactionUtil.runInTransaction(conn -> {
            Long newId = documentDao.insert(conn, document);
            userDao.updateResumeDocument(conn, userId, newId);
            if (previous != null) {
                documentDao.delete(conn, previous.getId(), userId);
            }
            return null;
        });
        // DB가 확정된 뒤에 이전 파일을 디스크에서 지운다 — 먼저 지우면 롤백됐을 때 파일만 사라진다
        if (previous != null) {
            FileStorageUtil.deleteQuietly(previous.getFilePath());
        }
    }

    public void removeResume(Long userId) throws SQLException {
        DocumentDto previous = findResume(userId);
        TransactionUtil.runInTransaction(conn -> {
            userDao.updateResumeDocument(conn, userId, null);
            if (previous != null) {
                documentDao.delete(conn, previous.getId(), userId);
            }
            return null;
        });
        if (previous != null) {
            FileStorageUtil.deleteQuietly(previous.getFilePath());
        }
    }
}
