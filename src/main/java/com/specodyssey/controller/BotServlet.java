package com.specodyssey.controller;

import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ScoreService;
import com.specodyssey.service.companion.CompanionMessageService;
import com.specodyssey.service.companion.CompanionReleaseService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "오셍이들" — 데스크톱 캐릭터 소개 · 내려받기 · 연결 · 연결된 PC 관리 화면 (2026-10-08).
 * 예전에는 메뉴의 [캐릭터 켜기]와 내 프로필 "데스크톱 캐릭터" 칸에 나뉘어 있던 것을 여기로 모았다.
 * 연결 버튼·PC 목록은 js/companion.js가 /companion/* 를 불러 처리한다. 여기서는 화면에 보일 값만 담는다.
 */
@WebServlet("/bot")
public class BotServlet extends HttpServlet {

    /** 등급별 캐릭터 그림 (webapp/image/bot/tier1~5.png — 캐릭터 프로그램과 같은 그림을 웹 크기로 줄인 것) */
    static final int TIER_IMAGES = 5;

    private final ScoreService scoreService = new ScoreService();
    private final CompanionMessageService messageService = new CompanionMessageService();
    private final CompanionReleaseService releaseService = new CompanionReleaseService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        UserDto user = session == null ? null : (UserDto) session.getAttribute("loginUser");
        try {
            int myIndex = messageService.currentTier(user.getId()).index();
            List<LevelTierDto> tiers = new ArrayList<>(scoreService.getAllTiers());
            tiers.sort(Comparator.comparing(LevelTierDto::getMinScore));
            List<Map<String, Object>> cards = new ArrayList<>();
            for (int i = 0; i < Math.min(TIER_IMAGES, tiers.size()); i++) {
                LevelTierDto t = tiers.get(i);
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("index", i + 1);
                card.put("tierName", t.getTierName());
                card.put("titleName", t.getTitleName());
                card.put("minScore", t.getMinScore());
                card.put("mine", i + 1 == myIndex);
                cards.add(card);
            }
            req.setAttribute("tierCards", cards);
            req.setAttribute("myTierIndex", myIndex);

            CompanionReleaseDto latest = releaseService.latest();
            if (latest != null) {
                req.setAttribute("releaseVersion", latest.getVersion());
                req.setAttribute("releaseSize", latest.getSizeText());
            }
        } catch (SQLException e) {
            throw new ServletException("오셍이들 화면을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/bot.jsp").forward(req, resp);
    }
}
