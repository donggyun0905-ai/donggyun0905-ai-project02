package com.specodyssey.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.specodyssey.util.ExternalApiClient;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 트렌드 후보 글 수집 소스 모음. 전부 공식 공개 API/RSS라 인증이 필요 없고 크롤링이 아니다.
 * 관련 요구사항: FR-54 트렌드 기술 노출
 * 소스 하나가 실패해도 나머지로 진행할 수 있도록, 각 fetch는 예외를 던지고 호출부(TrendCollectService)가 소스 단위로 처리한다.
 */
public class TrendSourceClient {

    private static final Logger LOG = Logger.getLogger(TrendSourceClient.class.getName());
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_TEXT_LENGTH = 200;

    private static final String HN_TOP_STORIES = "https://hacker-news.firebaseio.com/v0/topstories.json";
    private static final String HN_ITEM = "https://hacker-news.firebaseio.com/v0/item/%d.json";
    private static final String GITHUB_SEARCH =
            "https://api.github.com/search/repositories?q=created:%%3E%s&sort=stars&order=desc&per_page=%d";
    private static final String DEVTO_TOP = "https://dev.to/api/articles?top=1&per_page=%d";
    private static final String DEVTO_TAG_TOP = "https://dev.to/api/articles?tag=%s&top=%d&per_page=%d";
    private static final String LOBSTERS_HOTTEST = "https://lobste.rs/hottest.json";
    private static final String GEEKNEWS_FEED = "https://news.hada.io/rss/news";

    private static final int HN_LIMIT = 100;
    private static final int GITHUB_LIMIT = 100;
    private static final int GITHUB_LOOKBACK_DAYS = 7;
    private static final int DEVTO_LIMIT = 60;
    // 직군 태그 글은 하루치로는 적어서 최근 일주일 인기글을 본다
    private static final int DEVTO_TAG_LOOKBACK_DAYS = 7;
    private static final int DEVTO_TAG_LIMIT = 30;
    private static final int HN_PARALLELISM = 8;
    private static final Map<String, String> UA = Map.of("User-Agent", "spec-odyssey");

    // FR-55 직군별 보충 수집용 dev.to 태그 (JOB.job_category → 태그). 일반 소스가 개발 글 위주라 기획 등 다른 직군 글이 모자랄 때 쓴다.
    private static final Map<String, List<String>> CATEGORY_DEVTO_TAGS = Map.of(
            "BACKEND", List.of("backend", "api", "database"),
            "FRONTEND", List.of("frontend", "webdev", "css"),
            "DATA", List.of("datascience", "dataengineering", "machinelearning"),
            "DEVOPS", List.of("devops", "cloud", "kubernetes"),
            "SECURITY", List.of("security", "cybersecurity"),
            "PM", List.of("productmanagement", "product", "agile", "ux"));

    /** 후보 1건. text는 LLM에 보내는 요약 텍스트, url·publishedAt은 저장할 출처 정보. */
    public record Candidate(String text, String url, LocalDateTime publishedAt) {
    }

    /** 소스 이름 → 수집 작업. 호출부가 소스별로 실행·실패 처리를 한다. */
    public interface Source {
        String name();

        List<Candidate> fetch() throws Exception;
    }

    public List<Source> sources() {
        return List.of(
                source("Hacker News", this::fetchHackerNews),
                source("GitHub", this::fetchGithub),
                source("dev.to", this::fetchDevTo),
                source("Lobsters", this::fetchLobsters),
                source("GeekNews", this::fetchGeekNews));
    }

    /** 직군 하나의 보충 수집 소스. 태그가 정해지지 않은 직군이면 빈 목록. */
    public List<Source> categorySources(String jobCategory) {
        List<Source> result = new ArrayList<>();
        for (String tag : CATEGORY_DEVTO_TAGS.getOrDefault(jobCategory, List.of())) {
            result.add(source("dev.to #" + tag, () -> fetchDevToArticles(
                    String.format(DEVTO_TAG_TOP, tag, DEVTO_TAG_LOOKBACK_DAYS, DEVTO_TAG_LIMIT))));
        }
        return result;
    }

    private interface Fetcher {
        List<Candidate> fetch() throws Exception;
    }

    private static Source source(String name, Fetcher fetcher) {
        return new Source() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<Candidate> fetch() throws Exception {
                return fetcher.fetch();
            }
        };
    }

    // Hacker News 인기글 — 항목이 1건씩 조회라 병렬로 가져온다
    private List<Candidate> fetchHackerNews() throws Exception {
        JsonArray ids = JsonParser.parseString(ExternalApiClient.get(HN_TOP_STORIES)).getAsJsonArray();
        int limit = Math.min(HN_LIMIT, ids.size());
        ExecutorService pool = Executors.newFixedThreadPool(HN_PARALLELISM);
        try {
            List<Future<Candidate>> futures = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                long id = ids.get(i).getAsLong();
                futures.add(pool.submit(() -> fetchHnItem(id)));
            }
            List<Candidate> result = new ArrayList<>();
            for (Future<Candidate> f : futures) {
                Candidate c = f.get();
                if (c != null) {
                    result.add(c);
                }
            }
            return result;
        } finally {
            pool.shutdownNow();
        }
    }

    private Candidate fetchHnItem(long id) {
        try {
            JsonObject item = JsonParser.parseString(
                    ExternalApiClient.get(String.format(HN_ITEM, id))).getAsJsonObject();
            String title = string(item, "title");
            if (title == null) {
                return null;
            }
            // 외부 링크가 없는 글(Ask HN 등)은 HN 토론 페이지를 출처로 쓴다
            String url = string(item, "url");
            if (url == null) {
                url = "https://news.ycombinator.com/item?id=" + id;
            }
            return new Candidate(cut(title), url,
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(item.get("time").getAsLong()), ZONE));
        } catch (ExternalApiException | RuntimeException e) {
            // 글 1건 실패는 건너뛴다 — 목록 전체를 버리지 않는다
            LOG.log(Level.WARNING, "HN 항목 수집 실패: " + id, e);
            return null;
        }
    }

    // GitHub 최근 일주일 신규 저장소 중 별이 많은 순 — Trending 페이지는 공식 API가 없어 검색 API로 대체한다
    private List<Candidate> fetchGithub() throws Exception {
        String since = LocalDate.now(ZONE).minusDays(GITHUB_LOOKBACK_DAYS).toString();
        String body = ExternalApiClient.get(String.format(GITHUB_SEARCH, since, GITHUB_LIMIT),
                Map.of("Accept", "application/vnd.github+json", "User-Agent", "spec-odyssey"));
        JsonArray items = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("items");
        List<Candidate> result = new ArrayList<>();
        for (JsonElement e : items) {
            JsonObject repo = e.getAsJsonObject();
            String fullName = string(repo, "full_name");
            String url = string(repo, "html_url");
            if (fullName == null || url == null) {
                continue;
            }
            String description = string(repo, "description");
            String created = string(repo, "created_at");
            result.add(new Candidate(cut(description == null ? fullName : fullName + " — " + description), url,
                    created == null ? LocalDateTime.now(ZONE) : LocalDateTime.ofInstant(Instant.parse(created), ZONE)));
        }
        return result;
    }

    // dev.to 최근 하루 인기 글 — 개발 기술 글 비중이 높다
    private List<Candidate> fetchDevTo() throws Exception {
        return fetchDevToArticles(String.format(DEVTO_TOP, DEVTO_LIMIT));
    }

    private List<Candidate> fetchDevToArticles(String apiUrl) throws Exception {
        JsonArray items = JsonParser.parseString(ExternalApiClient.get(apiUrl, UA)).getAsJsonArray();
        List<Candidate> result = new ArrayList<>();
        for (JsonElement e : items) {
            JsonObject article = e.getAsJsonObject();
            String title = string(article, "title");
            String url = string(article, "url");
            if (title == null || url == null) {
                continue;
            }
            String description = string(article, "description");
            String published = string(article, "published_timestamp");
            result.add(new Candidate(cut(description == null || description.isBlank() ? title : title + " — " + description),
                    url, published == null ? LocalDateTime.now(ZONE) : LocalDateTime.ofInstant(Instant.parse(published), ZONE)));
        }
        return result;
    }

    // Lobsters 인기글 — 태그로 기술 분야가 붙어 있다
    private List<Candidate> fetchLobsters() throws Exception {
        JsonArray items = JsonParser.parseString(ExternalApiClient.get(LOBSTERS_HOTTEST, UA)).getAsJsonArray();
        List<Candidate> result = new ArrayList<>();
        for (JsonElement e : items) {
            JsonObject story = e.getAsJsonObject();
            String title = string(story, "title");
            String url = string(story, "url");
            if (url == null || url.isBlank()) {
                url = string(story, "comments_url");
            }
            String created = string(story, "created_at");
            if (title == null || url == null || created == null) {
                continue;
            }
            StringBuilder tags = new StringBuilder();
            JsonArray tagArray = story.getAsJsonArray("tags");
            if (tagArray != null) {
                for (JsonElement t : tagArray) {
                    tags.append(tags.length() == 0 ? " [" : ", ").append(t.getAsString());
                }
                if (tags.length() > 0) {
                    tags.append(']');
                }
            }
            result.add(new Candidate(cut(title + tags), url,
                    OffsetDateTime.parse(created).atZoneSameInstant(ZONE).toLocalDateTime()));
        }
        return result;
    }

    // GeekNews(국내 개발·기술 뉴스) Atom 피드 — 한국어 제목
    private List<Candidate> fetchGeekNews() throws Exception {
        String xml = ExternalApiClient.get(GEEKNEWS_FEED, UA);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE 방지
        NodeList entries = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getElementsByTagName("entry");
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String title = text(entry, "title");
            String published = text(entry, "published");
            NodeList links = entry.getElementsByTagName("link");
            String url = links.getLength() == 0 ? null : ((Element) links.item(0)).getAttribute("href");
            if (title == null || url == null || url.isBlank() || published == null) {
                continue;
            }
            result.add(new Candidate(cut(title), url,
                    OffsetDateTime.parse(published).atZoneSameInstant(ZONE).toLocalDateTime()));
        }
        return result;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String cut(String text) {
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    private static String string(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
