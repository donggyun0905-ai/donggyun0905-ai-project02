/*
 * 스펙 아카이브 화면 스크립트 (목록 · 글쓰기 · 상세 공용)
 *   - 글자 수 표시 ([data-count-for])
 *   - 본문·댓글 속 링크를 눌러지는 링크로 ([data-linkify])
 *   - 인스타그램식 답글 ([data-reply])
 *   - 지우기 확인 ([data-confirm])
 *
 * 보안: 사용자 글은 서버가 c:out으로 이스케이프해 둔 글자(textNode)만 다룬다. innerHTML은 쓰지 않는다.
 * 링크는 http(s)만 <a>로 바꾸고, 새 창 + noopener·noreferrer로 연다.
 */
(function () {
    'use strict';

    // ---- 글자 수 ---------------------------------------------------------------------------
    // 서버는 유니코드 글자(코드포인트) 수로 센다 — 이모지 하나를 2로 세지 않게 맞춘다
    function length(text) {
        return Array.from(text.replace(/\r\n/g, '\n')).length;
    }
    document.querySelectorAll('[data-count-for]').forEach(function (counter) {
        var field = document.getElementById(counter.getAttribute('data-count-for'));
        var max = parseInt(counter.getAttribute('data-max'), 10);
        if (!field) { return; }
        var update = function () {
            var n = length(field.value);
            counter.textContent = n + ' / ' + max + '자';
            counter.classList.toggle('over', n > max);
        };
        field.addEventListener('input', update);
        update();
    });

    // ---- 지우기 확인 -------------------------------------------------------------------------
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            if (!window.confirm(form.getAttribute('data-confirm'))) { e.preventDefault(); }
        });
    });

    // ---- 링크 자동 연결 ------------------------------------------------------------------------
    var URL_PATTERN = /https?:\/\/[^\s<>"']+/g;
    // 문장 끝 마침표·괄호는 링크에서 뺀다 ("…참고하세요 https://a.com)." → https://a.com)
    var TRAILING = /[.,!?;:'")\]}>…]+$/;

    function linkify(root) {
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        var nodes = [];
        while (walker.nextNode()) { nodes.push(walker.currentNode); }
        nodes.forEach(function (node) {
            var text = node.nodeValue;
            URL_PATTERN.lastIndex = 0;
            if (!URL_PATTERN.test(text)) { return; }
            URL_PATTERN.lastIndex = 0;
            var frag = document.createDocumentFragment();
            var pos = 0;
            var m;
            while ((m = URL_PATTERN.exec(text)) !== null) {
                var url = m[0];
                var trail = (url.match(TRAILING) || [''])[0];
                url = url.slice(0, url.length - trail.length);
                if (!url) { continue; }
                frag.appendChild(document.createTextNode(text.slice(pos, m.index)));
                var a = document.createElement('a');
                a.href = url;
                a.textContent = url;
                a.target = '_blank';
                a.rel = 'noopener noreferrer nofollow ugc';
                frag.appendChild(a);
                pos = m.index + url.length;
            }
            frag.appendChild(document.createTextNode(text.slice(pos)));
            node.parentNode.replaceChild(frag, node);
        });
    }
    document.querySelectorAll('[data-linkify]').forEach(linkify);

    // ---- 답글 (인스타그램식) ---------------------------------------------------------------------
    var commentForm = document.getElementById('commentForm');
    if (commentForm) {
        var replyTo = document.getElementById('replyTo');
        var replying = document.getElementById('replying');
        var replyingText = document.getElementById('replyingText');
        var commentContent = document.getElementById('commentContent');
        document.querySelectorAll('[data-reply]').forEach(function (button) {
            button.addEventListener('click', function () {
                replyTo.value = button.getAttribute('data-reply');
                replyingText.textContent = '@' + button.getAttribute('data-reply-name') + ' 님에게 답글';
                replying.hidden = false;
                commentForm.scrollIntoView({ behavior: 'smooth', block: 'center' });
                commentContent.focus();
            });
        });
        document.getElementById('cancelReply').addEventListener('click', function () {
            replyTo.value = '';
            replying.hidden = true;
        });
    }

    // ---- 코드 블록 (상세 화면) — 색 입히기 · 복사 -----------------------------------------------------
    // 코드는 서버가 c:out으로 이스케이프한 글자로 들어 있고, highlight.js는 그 글자(textContent)로만 색을 입힌다
    if (window.hljs) {
        document.querySelectorAll('.sa-code pre code').forEach(function (code) {
            if (!code.classList.contains('language-plaintext')) { window.hljs.highlightElement(code); }
        });
    }
    document.querySelectorAll('[data-copy-code]').forEach(function (button) {
        button.addEventListener('click', function () {
            var code = button.closest('.sa-code').querySelector('pre code').textContent;
            var done = function () {
                button.textContent = '복사됨 ✓';
                setTimeout(function () { button.textContent = '복사'; }, 1500);
            };
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(code).then(done, function () { selectCode(button); });
            } else {
                selectCode(button);
            }
        });
    });
    // 자동 복사가 막힌 환경(http 접속 등)에서는 코드를 선택해 두고 Ctrl+C를 안내한다
    function selectCode(button) {
        var range = document.createRange();
        range.selectNodeContents(button.closest('.sa-code').querySelector('pre code'));
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        button.textContent = 'Ctrl+C로 복사하세요';
    }
})();
