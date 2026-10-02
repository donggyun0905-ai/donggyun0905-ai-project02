package com.specodyssey.service.archive;

import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dao.TechArticleAttachmentDao;
import com.specodyssey.dao.TechArticleCommentDao;
import com.specodyssey.dao.TechArticleDao;
import com.specodyssey.dao.TechArticleDao.CountColumn;
import com.specodyssey.dao.TechArticleReactionDao;
import com.specodyssey.dao.TechArticleReactionDao.Kind;
import com.specodyssey.dao.UserScoreSummaryDao;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.TechArticleAttachmentDto;
import com.specodyssey.dto.TechArticleCommentDto;
import com.specodyssey.dto.TechArticleDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.util.TransactionUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스펙 아카이브 — 상위 티어 사용자의 팁 게시판. TECH_ARTICLE을 source_type = ARCHIVE_TIP으로 쓴다.
 *
 * 권한: 글쓰기는 상위 2개 티어(현재 개척자·오디세이아)만, 읽기·댓글·하트·북마크는 로그인한 누구나.
 *       티어 이름이 바뀌어도 맞게 동작하도록 이름이 아니라 LEVEL_TIER의 min_score 순서로 "위에서 2개"를 고른다.
 * 집계(하트·댓글·북마크·조회 수)는 행이 바뀌는 같은 트랜잭션에서 함께 고친다 (docs/db-design.md).
 */
public class SpecArchiveService {

    public static final int PAGE_SIZE = 10;
    private static final int WRITER_TIER_COUNT = 2;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final TechArticleDao articleDao = new TechArticleDao();
    private final TechArticleCommentDao commentDao = new TechArticleCommentDao();
    private final TechArticleAttachmentDao attachmentDao = new TechArticleAttachmentDao();
    private final TechArticleReactionDao reactionDao = new TechArticleReactionDao();
    private final UserScoreSummaryDao summaryDao = new UserScoreSummaryDao();
    private final LevelTierDao tierDao = new LevelTierDao();

    // ---------------------------------------------------------------- 화면용 값 (JSP EL은 getter로 읽는다)

    public record ListPage(List<TechArticleDto> posts, int page, int totalPages, int totalCount) {
        public List<TechArticleDto> getPosts() { return posts; }
        public int getPage() { return page; }
        public int getTotalPages() { return totalPages; }
        public int getTotalCount() { return totalCount; }
        public boolean isHasPrev() { return page > 1; }
        public boolean isHasNext() { return page < totalPages; }
    }

    /** 최상위 댓글 하나와 그 밑에 모인 답글들 (인스타그램식 — 깊이는 1단계) */
    public record CommentThread(TechArticleCommentDto comment, List<TechArticleCommentDto> replies) {
        public TechArticleCommentDto getComment() { return comment; }
        public List<TechArticleCommentDto> getReplies() { return replies; }
    }

    /** segments = 본문과 이미지·영상을 글에 놓인 순서대로 나눈 화면 조각 (ArchiveContentCodec.decode) */
    public record PostDetail(TechArticleDto post, List<ArchiveContentCodec.Segment> segments, List<CommentThread> threads,
                             boolean liked, boolean bookmarked, boolean mine) {
        public TechArticleDto getPost() { return post; }
        public List<ArchiveContentCodec.Segment> getSegments() { return segments; }
        public List<CommentThread> getThreads() { return threads; }
        public boolean isLiked() { return liked; }
        public boolean isBookmarked() { return bookmarked; }
        public boolean isMine() { return mine; }
    }

    /** 편집기에서 올린 사진 — 서블릿이 이미 디스크에 저장한 뒤, 본문의 [[upload:K]] 번호(K)와 함께 넘긴다 */
    public record UploadedImage(String originalName, String storedName, String filePath, long fileSize, String mimeType) {
    }

    // ---------------------------------------------------------------- 권한

    /**
     * 글쓰기 가능한 티어인지 — 총점으로 티어를 정한다 (헤더 배지와 같은 ScoreService.getTierForScore 방식).
     * USER_SCORE_SUMMARY.current_tier_id는 티어 표를 다시 넣거나 점수를 직접 고치면 비거나 어긋날 수 있어서 믿지 않는다.
     */
    public boolean canWrite(Long userId) throws SQLException {
        UserScoreSummaryDto summary = summaryDao.findByUserId(userId);
        int score = summary == null || summary.getTotalScore() == null ? 0 : summary.getTotalScore();
        List<LevelTierDto> tiers = tierDao.findAll(); // min_score 오름차순
        LevelTierDto tier = tierForScore(tiers, score);
        return tier != null && topTiers(tiers).stream().anyMatch(t -> t.getId().equals(tier.getId()));
    }

    /** 점수가 들어가는 티어 (ScoreService.getTierForScore와 같은 규칙). 없으면 null. */
    static LevelTierDto tierForScore(List<LevelTierDto> tiers, int score) {
        for (LevelTierDto t : tiers) {
            if (score >= t.getMinScore() && (t.getMaxScore() == null || score <= t.getMaxScore())) {
                return t;
            }
        }
        return null;
    }

    /** 글쓰기 가능한 티어 칭호 (낮은 것부터) — 안내 문구용. 예: [개척자, 오디세이아] */
    public List<String> writerTierTitles() throws SQLException {
        return writerTiers().stream().map(LevelTierDto::getTitleName).toList();
    }

    private List<LevelTierDto> writerTiers() throws SQLException {
        return topTiers(tierDao.findAll());
    }

    /** min_score 오름차순 티어 목록에서 위의 2개 */
    static List<LevelTierDto> topTiers(List<LevelTierDto> tiers) {
        return tiers.subList(Math.max(0, tiers.size() - WRITER_TIER_COUNT), tiers.size());
    }

    // ---------------------------------------------------------------- 목록·상세

    private static final int PREVIEW_CHARS = 90;

    public ListPage list(TechArticleDao.Sort sort, int page) throws SQLException {
        int total = articleDao.countArchive();
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(page, 1), totalPages);
        return new ListPage(withPreview(articleDao.findArchivePage(sort, (p - 1) * PAGE_SIZE, PAGE_SIZE)), p, totalPages, total);
    }

    public ListPage listBookmarks(Long userId, int page) throws SQLException {
        int total = articleDao.countBookmarkedArchive(userId);
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(page, 1), totalPages);
        return new ListPage(withPreview(articleDao.findBookmarkedArchivePage(userId, (p - 1) * PAGE_SIZE, PAGE_SIZE)),
                p, totalPages, total);
    }

    private static List<TechArticleDto> withPreview(List<TechArticleDto> posts) {
        posts.forEach(post -> post.setPreviewText(ArchiveContentCodec.plainPreview(post.getContent(), PREVIEW_CHARS)));
        return posts;
    }

    /**
     * 글 상세. 없거나 지워졌으면 null.
     * 본인이 아닌 사람이 오늘 처음 열었으면 조회수를 1 올린다 (사용자 1명 × 글 1개 × 하루 1회).
     */
    public PostDetail detail(Long articleId, Long viewerId) throws SQLException {
        TechArticleDto post = articleDao.findArchiveById(articleId);
        if (post == null) {
            return null;
        }
        boolean mine = post.getUserId().equals(viewerId);
        if (!mine) {
            boolean counted = TransactionUtil.runInTransaction(conn -> {
                boolean first = reactionDao.recordView(conn, articleId, viewerId, LocalDate.now(ZONE));
                if (first) {
                    articleDao.adjustCount(conn, articleId, CountColumn.VIEW, 1);
                }
                return first;
            });
            if (counted) {
                post.setViewCount(post.getViewCount() + 1);
            }
        }
        List<ArchiveContentCodec.Segment> segments =
                ArchiveContentCodec.decode(post.getContent(), attachmentDao.findByArticleId(articleId));
        return new PostDetail(post, segments, threads(commentDao.findByArticleId(articleId)),
                reactionDao.isActive(Kind.LIKE, articleId, viewerId),
                reactionDao.isActive(Kind.BOOKMARK, articleId, viewerId), mine);
    }

    /**
     * 댓글을 줄기로 묶는다. 지운 답글은 빼고, 지운 최상위 댓글은 살아 있는 답글이 있을 때만 "삭제된 댓글"로 남긴다.
     */
    static List<CommentThread> threads(List<TechArticleCommentDto> all) {
        Map<Long, List<TechArticleCommentDto>> repliesByParent = new LinkedHashMap<>();
        List<TechArticleCommentDto> tops = new ArrayList<>();
        for (TechArticleCommentDto c : all) {
            if (c.getParentCommentId() == null) {
                tops.add(c);
            } else if (!c.isDeleted()) {
                repliesByParent.computeIfAbsent(c.getParentCommentId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<CommentThread> threads = new ArrayList<>();
        for (TechArticleCommentDto top : tops) {
            List<TechArticleCommentDto> replies = repliesByParent.getOrDefault(top.getId(), List.of());
            if (top.isDeleted() && replies.isEmpty()) {
                continue;
            }
            threads.add(new CommentThread(top, replies));
        }
        return threads;
    }

    // ---------------------------------------------------------------- 쓰기

    /**
     * 새 글. 본문 속 [[upload:K]]를 올린 사진으로, 유튜브·이미지 링크를 첨부로 바꿔(ArchiveContentCodec)
     * 글과 첨부를 한 트랜잭션으로 넣는다. 첨부 개수 제한은 없다(용량은 서블릿이 본다). 새 글 id를 돌려준다.
     * 본문에서 가리키지 않은 업로드 사진은 저장하지 않는다 — 호출부가 그 파일을 지운다(unusedUploadKeys).
     * @throws IllegalArgumentException 입력이 규칙에 맞지 않음 (메시지를 화면에 보여준다)
     * @throws SecurityException        글쓰기 권한이 없는 티어
     */
    public Long create(Long userId, String title, String rawContent, Map<Integer, UploadedImage> uploads) throws SQLException {
        if (!canWrite(userId)) {
            throw new SecurityException("글쓰기는 " + String.join("·", writerTierTitles()) + " 티어부터 할 수 있습니다.");
        }
        String t = SpecArchiveRules.checkTitle(title);
        ArchiveContentCodec.Encoded encoded = ArchiveContentCodec.encode(rawContent, uploads.keySet());
        SpecArchiveRules.checkContent(encoded);

        return TransactionUtil.runInTransaction(conn -> {
            TechArticleDto article = new TechArticleDto();
            article.setUserId(userId);
            article.setSourceType(TechArticleDao.SOURCE_ARCHIVE_TIP);
            article.setTitle(t);
            article.setContent(encoded.content());
            article.setStatus(TechArticleDao.STATUS_PUBLISHED); // 자동 게시 + 사후 관리 (신고 → HIDDEN)
            article.setPublishedAt(LocalDateTime.now(ZONE));
            Long articleId = articleDao.insert(conn, article);

            int order = 0; // 본문의 [[att:N]] 번호와 같다
            for (ArchiveContentCodec.Item item : encoded.items()) {
                TechArticleAttachmentDto row = new TechArticleAttachmentDto();
                row.setArticleId(articleId);
                row.setSortOrder(order++);
                // Java 17 기준이라 switch 패턴 대신 instanceof로 나눈다
                if (item instanceof ArchiveContentCodec.Upload upload) {
                    UploadedImage img = uploads.get(upload.key());
                    row.setAttachmentType(TechArticleAttachmentDto.IMAGE_UPLOAD);
                    row.setOriginalName(img.originalName());
                    row.setStoredName(img.storedName());
                    row.setFilePath(img.filePath());
                    row.setFileSize(img.fileSize());
                    row.setMimeType(img.mimeType());
                } else if (item instanceof ArchiveContentCodec.ImageLink link) {
                    row.setAttachmentType(TechArticleAttachmentDto.IMAGE_URL);
                    row.setUrl(link.url());
                } else if (item instanceof ArchiveContentCodec.Youtube video) {
                    row.setAttachmentType(TechArticleAttachmentDto.YOUTUBE);
                    row.setUrl(video.url());
                    row.setEmbedKey(video.videoId());
                }
                attachmentDao.insert(conn, row);
            }
            return articleId;
        });
    }

    /** 올라왔지만 본문에서 가리키지 않아 저장되지 않는 업로드 번호 — 서블릿이 그 파일을 지우는 데 쓴다 */
    public static java.util.Set<Integer> unusedUploadKeys(String rawContent, java.util.Set<Integer> uploadKeys) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (ArchiveContentCodec.Item item : ArchiveContentCodec.encode(rawContent, uploadKeys).items()) {
            if (item instanceof ArchiveContentCodec.Upload upload) {
                used.add(upload.key());
            }
        }
        java.util.Set<Integer> unused = new java.util.HashSet<>(uploadKeys);
        unused.removeAll(used);
        return unused;
    }

    /** 작성자 본인 글 삭제. 지웠으면 true. */
    public boolean deletePost(Long userId, Long articleId) throws SQLException {
        return TransactionUtil.runInTransaction(conn -> articleDao.softDeleteByOwner(conn, articleId, userId));
    }

    /** 하트 켜기/끄기 — 바뀐 뒤 상태를 돌려준다. 글이 없으면 IllegalArgumentException. */
    public boolean toggleLike(Long userId, Long articleId) throws SQLException {
        return toggle(Kind.LIKE, CountColumn.LIKE, userId, articleId);
    }

    public boolean toggleBookmark(Long userId, Long articleId) throws SQLException {
        return toggle(Kind.BOOKMARK, CountColumn.BOOKMARK, userId, articleId);
    }

    private boolean toggle(Kind kind, CountColumn column, Long userId, Long articleId) throws SQLException {
        requireVisible(articleId);
        return TransactionUtil.runInTransaction(conn -> {
            boolean on = reactionDao.toggle(conn, kind, articleId, userId);
            articleDao.adjustCount(conn, articleId, column, on ? 1 : -1);
            return on;
        });
    }

    /**
     * 댓글·답글 달기 (인스타그램식).
     * replyToCommentId가 없으면 최상위 댓글. 있으면 그 댓글이 속한 줄기(최상위 댓글) 밑에 달고, 그 댓글 작성자를 @대상으로 남긴다.
     */
    public void addComment(Long userId, Long articleId, String content, Long replyToCommentId) throws SQLException {
        requireVisible(articleId);
        String text = SpecArchiveRules.checkComment(content);

        TechArticleCommentDto comment = new TechArticleCommentDto();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setContent(text);
        if (replyToCommentId != null) {
            TechArticleCommentDto target = commentDao.findActiveById(replyToCommentId);
            if (target == null || !target.getArticleId().equals(articleId)) {
                throw new IllegalArgumentException("답글을 달 댓글을 찾을 수 없습니다.");
            }
            comment.setParentCommentId(target.getParentCommentId() != null ? target.getParentCommentId() : target.getId());
            comment.setReplyToUserId(target.getUserId());
        }
        TransactionUtil.runInTransaction(conn -> {
            commentDao.insert(conn, comment);
            articleDao.adjustCount(conn, articleId, CountColumn.COMMENT, 1);
            return null;
        });
    }

    /** 작성자 본인 댓글 삭제. 지웠으면 true. */
    public boolean deleteComment(Long userId, Long commentId) throws SQLException {
        TechArticleCommentDto comment = commentDao.findActiveById(commentId);
        if (comment == null || !comment.getUserId().equals(userId)) {
            return false;
        }
        return TransactionUtil.runInTransaction(conn -> {
            boolean removed = commentDao.softDeleteByOwner(conn, commentId, userId);
            if (removed) {
                articleDao.adjustCount(conn, comment.getArticleId(), CountColumn.COMMENT, -1);
            }
            return removed;
        });
    }

    private void requireVisible(Long articleId) throws SQLException {
        if (articleDao.findArchiveById(articleId) == null) {
            throw new IllegalArgumentException("글을 찾을 수 없습니다. 지워졌을 수 있습니다.");
        }
    }
}
