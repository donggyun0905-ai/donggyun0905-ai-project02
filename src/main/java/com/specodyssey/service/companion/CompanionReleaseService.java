package com.specodyssey.service.companion;

import com.specodyssey.dao.CompanionReleaseDao;
import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.util.TransactionUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;

/**
 * 데스크톱 캐릭터 설치 파일(Setup.exe) — 관리자가 올리면 DB에 8MB씩 나눠 두고, 사이트 [내려받기]와 캐릭터 [업데이트]가 내려받는다.
 * 팀원마다 자기 PC에서 서버를 켜도 같은 DB라 같은 파일이 나온다. 최근 2개 버전만 내용을 남긴다.
 */
public class CompanionReleaseService {

    static final int CHUNK_BYTES = 8 * 1024 * 1024;
    static final int KEEP_VERSIONS = 2;
    public static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    private static final int NOTES_MAX = 500;

    private final CompanionReleaseDao dao = new CompanionReleaseDao();

    /** 가장 새 버전 (없으면 null) */
    public CompanionReleaseDto latest() throws SQLException {
        List<CompanionReleaseDto> list = dao.findActive();
        return list.isEmpty() ? null : list.get(0);
    }

    public List<CompanionReleaseDto> active() throws SQLException {
        return dao.findActive();
    }

    public void writeTo(CompanionReleaseDto release, OutputStream out) throws SQLException, IOException {
        dao.writeTo(release.getId(), out);
    }

    /**
     * 새 버전을 올린다 — 내용을 나눠 저장하면서 크기·SHA-256을 센다. 지금 최신보다 높은 버전만 받는다.
     * @throws IllegalArgumentException 버전 형식이 틀렸거나, 이미 있거나, 최신보다 낮음 (화면에 보여준다)
     */
    public CompanionReleaseDto upload(Long adminId, String version, String notes, String fileName, InputStream in)
            throws SQLException, IOException {
        String v = version == null ? "" : version.trim().replaceFirst("^[vV]", "");
        if (!v.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            throw new IllegalArgumentException("버전은 0.2.0처럼 숫자.숫자.숫자로 적어 주세요.");
        }
        if (dao.existsVersion(v)) {
            throw new IllegalArgumentException(v + " 버전은 이미 올라가 있어요. 버전 번호를 올려 주세요.");
        }
        CompanionReleaseDto current = latest();
        if (current != null && compare(v, current.getVersion()) <= 0) {
            throw new IllegalArgumentException("지금 최신이 " + current.getVersion() + "이에요. 그보다 높은 버전으로 올려 주세요.");
        }
        String n = notes == null ? null : notes.strip();
        if (n != null && n.length() > NOTES_MAX) {
            n = n.substring(0, NOTES_MAX);
        }
        CompanionReleaseDto release = new CompanionReleaseDto();
        release.setVersion(v);
        release.setNotes(n == null || n.isEmpty() ? null : n);
        release.setFileName(fileName == null || fileName.isBlank() ? "SpecOdysseyCompanion-Setup.exe" : fileName);
        release.setSha256("0".repeat(64));

        MessageDigest md = sha256();
        Long[] idHolder = new Long[1];
        TransactionUtil.runInTransaction(conn -> {
            Long id = dao.insertRelease(conn, release, adminId);
            idHolder[0] = id;
            byte[] buf = new byte[CHUNK_BYTES];
            long total = 0;
            int seq = 0;
            try {
                int filled;
                while ((filled = fill(in, buf)) > 0) {
                    md.update(buf, 0, filled);
                    dao.insertChunk(conn, id, seq++, buf, filled);
                    total += filled;
                    if (total > MAX_FILE_BYTES) {
                        throw new IllegalArgumentException("파일이 너무 커요 (200MB 이하).");
                    }
                }
            } catch (IOException e) {
                throw new SQLException("설치 파일을 읽지 못했어요", e);
            }
            if (total == 0) {
                throw new IllegalArgumentException("빈 파일이에요.");
            }
            String hash = HexFormat.of().formatHex(md.digest());
            dao.updateSizeAndHash(conn, id, total, hash);
            release.setFileSize(total);
            release.setSha256(hash);
            return null;
        });
        release.setId(idHolder[0]);
        // 최근 KEEP_VERSIONS개만 남기고 정리
        List<CompanionReleaseDto> all = dao.findActive();
        for (int i = KEEP_VERSIONS; i < all.size(); i++) {
            Long oldId = all.get(i).getId();
            TransactionUtil.runInTransaction(conn -> {
                dao.retire(conn, oldId);
                return null;
            });
        }
        return release;
    }

    /** buf를 최대한 채워 읽는다 (마지막 조각만 덜 찬다) */
    static int fill(InputStream in, byte[] buf) throws IOException {
        int filled = 0;
        int n;
        while (filled < buf.length && (n = in.read(buf, filled, buf.length - filled)) > 0) {
            filled += n;
        }
        return filled;
    }

    /** a가 b보다 새 버전이면 양수 */
    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int xi = i < x.length ? Integer.parseInt(x[i].replaceAll("\\D", "0")) : 0;
            int yi = i < y.length ? Integer.parseInt(y[i].replaceAll("\\D", "0")) : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
