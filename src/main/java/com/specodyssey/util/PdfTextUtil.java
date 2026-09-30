package com.specodyssey.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

/**
 * 로드맵 SKILL 단계 학습 검증(공부노트·기술 설명 글) — 업로드된 PDF에서 규칙 판정에 쓸 텍스트를
 * 꺼낸다. 암호화됐거나 손상된 PDF는 판정 자체가 불가능하므로 IOException으로 통일해 던진다.
 */
public final class PdfTextUtil {

    private PdfTextUtil() {
    }

    public static String extractText(InputStream in) throws IOException {
        try (PDDocument document = PDDocument.load(in)) {
            if (document.isEncrypted()) {
                throw new IOException("암호가 걸린 PDF는 내용을 읽을 수 없습니다.");
            }
            return new PDFTextStripper().getText(document);
        }
    }
}
