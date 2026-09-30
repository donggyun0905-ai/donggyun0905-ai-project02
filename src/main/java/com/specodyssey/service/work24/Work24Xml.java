package com.specodyssey.service.work24;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고용24 XML 응답 파싱 헬퍼 — JDK 내장 DOM만 쓴다 (의존성 추가 없음).
 * 외부에서 받은 XML이라 DTD·외부 엔티티를 막아 XXE를 차단한다.
 */
final class Work24Xml {

    private Work24Xml() {
    }

    /** 반복 요소(예: majorList)마다 자식 태그명 → 텍스트(trim) 맵으로 돌려준다. */
    static List<Map<String, String>> rows(String xml, String rowTag) throws ExternalApiException {
        Document doc = parse(xml);
        NodeList nodes = doc.getElementsByTagName(rowTag);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Map<String, String> row = new LinkedHashMap<>();
            NodeList children = nodes.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child instanceof Element e) {
                    row.put(e.getTagName(), e.getTextContent().trim());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private static Document parse(String xml) throws ExternalApiException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null); // 기본 핸들러는 System.err에 "[Fatal Error]"를 찍는다 — 오류는 아래 예외로만 알린다
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new ExternalApiException("고용24 XML 파싱 실패", e);
        }
    }
}
