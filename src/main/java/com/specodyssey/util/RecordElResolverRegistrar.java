package com.specodyssey.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.jsp.JspFactory;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 앱 시작 때 RecordElResolver를 JSP EL에 등록한다 — 첫 요청 전에 해야 해서 리스너에서 한다.
 * 여기서 더한 해석기는 톰캣 기본 해석기(Map·List·Record·Bean 등)보다 먼저 물어본다(JSP 명세의 순서).
 */
@WebListener
public class RecordElResolverRegistrar implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(RecordElResolverRegistrar.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            JspFactory factory = JspFactory.getDefaultFactory();
            if (factory == null) {
                LOG.warning("JspFactory가 아직 없어 record EL 해석기를 등록하지 못했습니다 — Tomcat 10·11 차이가 그대로 남습니다");
                return;
            }
            factory.getJspApplicationContext(event.getServletContext()).addELResolver(new RecordElResolver());
        } catch (RuntimeException e) {
            // 등록에 실패해도 앱은 뜬다 — 그 경우 record는 각 버전 기본 규칙대로 읽힌다
            LOG.log(Level.WARNING, "record EL 해석기 등록 실패", e);
        }
    }
}
