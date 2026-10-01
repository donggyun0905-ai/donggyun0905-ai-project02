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
 * 내 프로필의 이력서·자소서 파일 — 올리기·바꾸기·삭제·조회. 둘 다 선택 항목이다.
 * 이력서와 자소서는 사용자당 각각 하나다. 파일 정보는 DOCUMENTS에 한 행으로 저장하고(project_id 없음),
 * USERS.resume_document_id / cover_letter_document_id가 그 행을 가리킨다.
 * 두 종류의 규칙이 완전히 같아서 같은 코드를 쓰고, 어느 컬럼을 건드리는지만 Kind로 가른다.
 */
public class ResumeService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "hwp", "hwpx");

    private final UserDao userDao = new UserDao();
    private final DocumentDao documentDao = new DocumentDao();

    private enum Kind { RESUME, COVER_LETTER }

    /** 이력서·자소서로 올릴 수 있는 파일 형식인지 — 디스크에 쓰기 전에 확인한다. */
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
        return find(userId, Kind.RESUME);
    }

    /** 지금 지정된 자소서. 없거나 그 서류가 지워졌으면 null. */
    public DocumentDto findCoverLetter(Long userId) throws SQLException {
        return find(userId, Kind.COVER_LETTER);
    }

    /**
     * 이미 디스크에 저장된 파일을 이력서로 등록한다. 이전 이력서가 있으면 지운다.
     * @param document 호출부가 FileStorageUtil로 저장한 뒤 채워 넘긴 파일 정보 (userId는 여기서 채운다)
     */
    public void replaceResume(Long userId, DocumentDto document) throws SQLException {
        replace(userId, document, Kind.RESUME);
    }

    /** replaceResume과 같다 — 자소서용. */
    public void replaceCoverLetter(Long userId, DocumentDto document) throws SQLException {
        replace(userId, document, Kind.COVER_LETTER);
    }

    public void removeResume(Long userId) throws SQLException {
        remove(userId, Kind.RESUME);
    }

    public void removeCoverLetter(Long userId) throws SQLException {
        remove(userId, Kind.COVER_LETTER);
    }

    private DocumentDto find(Long userId, Kind kind) throws SQLException {
        UserDto user = userDao.findById(userId);
        Long documentId = user == null ? null : documentIdOf(user, kind);
        if (documentId == null) {
            return null;
        }
        DocumentDto document = documentDao.findById(documentId);
        return (document == null || !document.getUserId().equals(userId)) ? null : document;
    }

    private void replace(Long userId, DocumentDto document, Kind kind) throws SQLException {
        DocumentDto previous = find(userId, kind);
        document.setUserId(userId);
        document.setProjectId(null);

        TransactionUtil.runInTransaction(conn -> {
            Long newId = documentDao.insert(conn, document);
            point(conn, userId, kind, newId);
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

    private void remove(Long userId, Kind kind) throws SQLException {
        DocumentDto previous = find(userId, kind);
        TransactionUtil.runInTransaction(conn -> {
            point(conn, userId, kind, null);
            if (previous != null) {
                documentDao.delete(conn, previous.getId(), userId);
            }
            return null;
        });
        if (previous != null) {
            FileStorageUtil.deleteQuietly(previous.getFilePath());
        }
    }

    private static Long documentIdOf(UserDto user, Kind kind) {
        return kind == Kind.RESUME ? user.getResumeDocumentId() : user.getCoverLetterDocumentId();
    }

    private void point(java.sql.Connection conn, Long userId, Kind kind, Long documentId) throws SQLException {
        if (kind == Kind.RESUME) {
            userDao.updateResumeDocument(conn, userId, documentId);
        } else {
            userDao.updateCoverLetterDocument(conn, userId, documentId);
        }
    }
}
