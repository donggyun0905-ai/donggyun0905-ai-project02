/*
 * 스펙 아카이브 글쓰기 편집기 — 노션처럼 "쓰는 화면이 곧 올라갈 모습"인 블록 편집기
 *
 * 블록 종류: 글(textarea) · 사진(올린 파일) · 임베드(유튜브 영상 / 이미지 링크) · 코드
 *   - 사진 넣기: [사진 넣기] · Ctrl+V 붙여넣기 · 파일 끌어다 놓기 → 커서가 있던 자리(또는 놓은 자리)에 들어간다
 *   - 링크: 유튜브·이미지 링크를 한 줄에 붙여넣거나, 링크만 쓰고 Enter → 그 자리에서 영상·사진 블록이 된다
 *           (글 중간 링크는 글로 남고 올렸을 때 하이퍼링크가 된다)
 *   - 코드: [</> 코드] 또는 글 칸에서 ``` (``` 언어)를 치고 Enter → 코드 블록. 언어를 고르면 바로 색이 입혀진다(highlight.js)
 *   - 옮기기: 블록에 마우스를 올리면 왼쪽에 ⠿ 손잡이 — 끌어서 블록 사이나 글의 원하는 줄에 놓는다
 *   - 되돌리기: Ctrl+Z / 다시 하기: Ctrl+Y · Ctrl+Shift+Z (맥은 Cmd) — 글 입력과 블록 넣기·옮기기·빼기 모두
 *
 * 보낼 때: content = 블록을 순서대로 이은 글 (사진 [[upload:K]], 임베드는 링크 한 줄, 코드는 ```언어 … ```) / image_K = K번 사진
 *   서버(ArchiveContentCodec)가 같은 규칙으로 다시 나눠 저장하므로, 화면과 저장 결과가 같다.
 * 글자 수: 글만 센다 — 링크·사진·영상·코드는 세지 않는다 (서버 countedLength와 같은 규칙).
 * 개수 제한 없음, 용량만 — 글 하나에 사진을 모두 합쳐 10MB.
 *
 * 보안: 사용자 글은 textContent / value로만 다룬다. 코드 색 입히기만 highlight.js 결과를 쓰는데,
 *       highlight.js는 입력 글자를 모두 이스케이프한 뒤 <span class="hljs-…">만 붙여 돌려준다.
 */
(function () {
    'use strict';

    var form = document.getElementById('writeForm');
    if (!form) { return; }

    var editor = document.getElementById('editor');
    var contentField = document.getElementById('contentField');
    var fileFields = document.getElementById('fileFields');
    var notice = document.getElementById('editorNotice');
    var countEl = document.getElementById('contentCount');
    var imageStatus = document.getElementById('imageStatus');
    var picker = document.getElementById('imagePicker');
    var langTemplate = document.getElementById('codeLanguageOptions');

    var CONTENT_MAX = parseInt(form.getAttribute('data-content-max'), 10);
    var MAX_IMAGE = parseInt(form.getAttribute('data-max-image-mb'), 10) * 1024 * 1024;
    var MAX_TOTAL = parseInt(form.getAttribute('data-max-total-mb'), 10) * 1024 * 1024;
    var IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];
    var URL_PATTERN = /https?:\/\/[^\s<>"']+/g;
    var TRAILING = /[.,!?;:'")\]}>…]+$/;
    var FENCE_OPEN = /^(`{3,})([A-Za-z0-9+#.-]{0,20})[ \t]*$/;
    var INDENT = '    ';
    var LANG_ALIASES = { js: 'javascript', ts: 'typescript', py: 'python', 'c++': 'cpp', 'c#': 'csharp', cs: 'csharp',
        kt: 'kotlin', html: 'xml', htm: 'xml', sh: 'bash', shell: 'bash', zsh: 'bash', yml: 'yaml', md: 'markdown',
        text: 'plaintext', txt: 'plaintext', '': 'plaintext' };

    var images = {};     // key → { file, url } — 뺀 사진도 되돌리기를 위해 지우지 않고 남겨 둔다
    var nextKey = 0;
    var lastText = null; // 마지막으로 커서가 있던 글 칸
    var dragging = null; // 끌고 있는 블록
    var lastLanguage = 'java';

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text !== undefined) { node.textContent = text; }
        return node;
    }

    // ================================================================ 링크 · 언어 판별 (서버 규칙과 같다)

    function parseYoutube(link) {
        var u;
        try { u = new URL(link); } catch (e) { return null; }
        var host = u.hostname.toLowerCase();
        var ok = /^[A-Za-z0-9_-]{11}$/;
        if (host === 'youtu.be') {
            var id = u.pathname.slice(1).split('/')[0];
            return ok.test(id) ? id : null;
        }
        if (!(host === 'youtube.com' || /\.youtube\.com$/.test(host) || host === 'youtube-nocookie.com' || /\.youtube-nocookie\.com$/.test(host))) {
            return null;
        }
        if (u.pathname === '/watch') {
            var v = u.searchParams.get('v');
            return v && ok.test(v) ? v : null;
        }
        var m = u.pathname.match(/^\/(?:embed|shorts|live|v)\/([A-Za-z0-9_-]{11})(?:[/?#].*)?$/);
        return m ? m[1] : null;
    }

    function isImageLink(link) {
        try {
            var u = new URL(link);
            return u.protocol === 'https:' && !u.username && /\.(png|jpe?g|gif|webp)$/i.test(u.pathname);
        } catch (e) { return false; }
    }

    /** 한 줄이 임베드할 링크 하나뿐이면 그 링크, 아니면 null */
    function embedLinkOfLine(line) {
        var t = line.trim();
        if (!t || /\s/.test(t) || !/^https?:\/\//.test(t)) { return null; }
        var link = t.replace(TRAILING, '');
        return parseYoutube(link) || isImageLink(link) ? link : null;
    }

    function knownLanguages() {
        return Array.prototype.map.call(langTemplate.content.querySelectorAll('option'), function (o) { return o.value; });
    }

    function normalizeLanguage(lang) {
        var l = (lang || '').trim().toLowerCase();
        if (Object.prototype.hasOwnProperty.call(LANG_ALIASES, l)) { l = LANG_ALIASES[l]; }
        return knownLanguages().indexOf(l) >= 0 ? l : 'plaintext';
    }

    /** 글을 코드 블록(```언어 … ```)과 나머지로 나눈다 — 닫히지 않은 펜스는 글로 둔다 (서버 ArchiveContentCodec.chunks와 같은 규칙) */
    function splitFences(value) {
        var lines = value.split('\n');
        var out = [];
        var text = [];
        var i = 0;
        while (i < lines.length) {
            var m = lines[i].match(FENCE_OPEN);
            var close = -1;
            if (m) {
                for (var j = i + 1; j < lines.length; j++) {
                    if (lines[j].trim() === m[1]) { close = j; break; }
                }
            }
            if (close < 0) { text.push(lines[i]); i++; continue; }
            if (text.length) { out.push({ t: 'text', v: text.join('\n') }); text = []; }
            out.push({ t: 'code', lang: normalizeLanguage(m[2]), v: lines.slice(i + 1, close).join('\n') });
            i = close + 1;
        }
        if (text.length) { out.push({ t: 'text', v: text.join('\n') }); }
        return out;
    }

    /** 코드 블록 저장 형식 — 펜스는 코드 안 백틱 묶음보다 길게 (서버 ArchiveContentCodec.fence와 같다) */
    function fence(lang, code) {
        var longest = (code.match(/`+/g) || []).reduce(function (n, s) { return Math.max(n, s.length); }, 0);
        var f = new Array(Math.max(3, longest + 1) + 1).join('`');
        return f + lang + '\n' + code + '\n' + f;
    }

    // ================================================================ 블록

    function autosize(ta) {
        ta.style.height = 'auto';
        ta.style.height = ta.scrollHeight + 'px';
    }

    /** 모든 블록 공통 틀 — 왼쪽 ⠿ 손잡이를 잡았을 때만 끌 수 있다 (글을 고를 때 끌리지 않게) */
    function frame(kind, body) {
        var block = el('div', 'ed-block ed-' + kind);
        var handle = el('span', 'ed-handle', '⠿');
        handle.title = '끌어서 옮기기';
        handle.setAttribute('aria-hidden', 'true');
        handle.addEventListener('mousedown', function () { block.setAttribute('draggable', 'true'); });
        handle.addEventListener('mouseup', function () { block.removeAttribute('draggable'); });
        block.appendChild(handle);
        block.appendChild(body);
        block.addEventListener('dragstart', function (e) {
            if (block.getAttribute('draggable') !== 'true') { return; }
            checkpoint();
            dragging = block;
            block.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', ''); // Firefox는 데이터가 있어야 끌기가 시작된다
        });
        block.addEventListener('dragend', function () {
            block.classList.remove('dragging');
            block.removeAttribute('draggable');
            dragging = null;
            hideDropLine();
        });
        return block;
    }

    function removeButton(block) {
        var b = el('button', 'ed-remove', '✕');
        b.type = 'button';
        b.title = '빼기 (Ctrl+Z로 되돌릴 수 있어요)';
        b.setAttribute('aria-label', '빼기');
        b.addEventListener('click', function () {
            checkpoint();
            block.remove();
            normalize();
        });
        return b;
    }

    function textBlock(value) {
        var ta = el('textarea');
        ta.rows = 1;
        ta.value = value || '';
        ta.placeholder = '글을 쓰거나, 사진·유튜브 링크를 붙여넣거나, ```를 쳐서 코드를 넣어 보세요';
        var block = frame('text', ta);
        ta.addEventListener('input', function () { autosize(ta); refresh(); scheduleRecord(); });
        ta.addEventListener('focus', function () { lastText = ta; });
        // Enter → 링크만 있는 줄은 영상·사진으로, ``` 줄은 코드 블록으로
        ta.addEventListener('keyup', function (e) { if (e.key === 'Enter') { convertSpecial(ta, true, true); } });
        ta.addEventListener('paste', function () { setTimeout(function () { convertSpecial(ta, true, false); }, 0); });
        // 칸을 벗어날 때도 바꾸되, 다른 곳(제목·올리기 버튼)으로 간 포커스는 빼앗지 않는다
        ta.addEventListener('blur', function () { convertSpecial(ta, false, false); });
        return block;
    }

    function imageBlock(key) {
        var info = images[key];
        var body = el('div', 'ed-media');
        var img = el('img');
        img.src = info.url;
        img.alt = info.file.name || '붙여넣은 사진';
        img.draggable = false;
        body.appendChild(img);
        body.appendChild(el('span', 'ed-media-info', formatSize(info.file.size)));
        var block = frame('image', body);
        block.setAttribute('data-key', key);
        body.appendChild(removeButton(block));
        return block;
    }

    function embedBlock(link) {
        var body = el('div', 'ed-media');
        var id = parseYoutube(link);
        if (id) {
            var box = el('div', 'sa-video');
            var iframe = el('iframe');
            // 사이트 전체 Referrer-Policy(same-origin) 때문에 Referer가 빠지면 유튜브가 "동영상 플레이어 구성 오류(153)"를 낸다.
            // 이 iframe만 출처(도메인)까지 보내게 한다 — 페이지 경로·쿼리는 보내지 않는다. src보다 먼저 정해야 첫 요청에 적용된다.
            iframe.referrerPolicy = 'strict-origin-when-cross-origin';
            iframe.src = 'https://www.youtube-nocookie.com/embed/' + id;
            iframe.title = '유튜브 영상';
            iframe.setAttribute('allowfullscreen', '');
            iframe.setAttribute('allow', 'accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen');
            box.appendChild(iframe);
            body.appendChild(box);
        } else {
            var img = el('img');
            img.src = link;
            img.alt = '링크 이미지';
            img.referrerPolicy = 'no-referrer';
            img.draggable = false;
            body.appendChild(img);
        }
        var block = frame('embed', body);
        block.setAttribute('data-link', link);
        body.appendChild(removeButton(block));
        return block;
    }

    /** 코드 블록 — 투명한 입력칸 뒤에 같은 글자를 색 입혀 깔아, 쓰는 그대로 색이 보이게 한다 */
    function codeBlock(lang, code) {
        var box = el('div', 'ed-codebox');
        var head = el('div', 'ed-code-head');
        var select = el('select', 'ed-code-lang');
        select.setAttribute('aria-label', '코드 언어');
        select.appendChild(langTemplate.content.cloneNode(true));
        select.value = normalizeLanguage(lang);
        head.appendChild(select);
        head.appendChild(el('span', 'ed-code-hint', 'Tab 들여쓰기 · Shift+Tab 내어쓰기'));
        box.appendChild(head);

        var area = el('div', 'ed-code-area');
        var pre = el('pre', 'ed-code-hl');
        var codeEl = el('code', 'hljs');
        pre.appendChild(codeEl);
        var ta = el('textarea', 'ed-code-input');
        ta.value = code || '';
        ta.spellcheck = false;
        ta.setAttribute('autocapitalize', 'off');
        ta.setAttribute('autocomplete', 'off');
        ta.placeholder = '코드를 붙여넣거나 입력하세요';
        area.appendChild(pre);
        area.appendChild(ta);
        box.appendChild(area);

        var block = frame('code', box);
        block.setAttribute('data-lang', select.value);
        box.appendChild(removeButton(block));

        function paint() {
            var value = ta.value + '\n'; // 끝 줄바꿈도 한 줄을 차지하게
            if (window.hljs && select.value !== 'plaintext') {
                // highlight.js가 글자를 이스케이프하고 색 표시 span만 붙여 돌려준다
                codeEl.innerHTML = window.hljs.highlight(value, { language: select.value, ignoreIllegals: true }).value;
            } else {
                codeEl.textContent = value;
            }
            autosize(ta);
        }
        block.paint = paint;
        ta.addEventListener('input', function () { paint(); scheduleRecord(); });
        select.addEventListener('change', function () {
            checkpoint();
            block.setAttribute('data-lang', select.value);
            lastLanguage = select.value;
            paint();
            record();
        });
        ta.addEventListener('keydown', function (e) {
            if (e.key !== 'Tab') { return; }
            e.preventDefault();
            indent(ta, e.shiftKey);
            paint();
            scheduleRecord();
        });
        paint();
        return block;
    }

    /** Tab: 고른 줄들(또는 커서 자리)에 들여쓰기 / Shift+Tab: 줄 앞 공백 4칸까지 빼기 */
    function indent(ta, outdent) {
        var v = ta.value;
        var start = ta.selectionStart;
        var end = ta.selectionEnd;
        if (!outdent && start === end) {
            ta.setRangeText(INDENT, start, end, 'end');
            return;
        }
        var lineStart = v.lastIndexOf('\n', start - 1) + 1;
        var block = v.slice(lineStart, end);
        var lines = block.split('\n');
        var changed = lines.map(function (l) { return outdent ? l.replace(/^( {1,4}|\t)/, '') : INDENT + l; });
        var replaced = changed.join('\n');
        ta.setRangeText(replaced, lineStart, end, 'select');
        if (start === end) {
            var pos = Math.max(lineStart, start + (replaced.length - block.length));
            ta.setSelectionRange(pos, pos);
        }
    }

    function blocks() { return Array.prototype.slice.call(editor.querySelectorAll(':scope > .ed-block')); }
    function isText(b) { return b.classList.contains('ed-text'); }
    function isCode(b) { return b.classList.contains('ed-code'); }
    function textOf(b) { return b.querySelector('textarea'); }

    /** 붙어 있는 글 칸은 합치고, 맨 앞·맨 뒤·글이 아닌 블록끼리 사이에는 글 칸을 둬서 어디든 글을 쓸 수 있게 한다 */
    function fixStructure() {
        var list = blocks();
        for (var i = list.length - 1; i > 0; i--) {
            if (isText(list[i]) && isText(list[i - 1])) {
                var a = textOf(list[i - 1]);
                var b = textOf(list[i]);
                a.value = a.value && b.value ? a.value + '\n' + b.value : a.value + b.value;
                if (lastText === b) { lastText = a; }
                list[i].remove();
            }
        }
        list = blocks();
        if (list.length === 0 || !isText(list[0])) { editor.insertBefore(textBlock(''), list[0] || dropLine); }
        list = blocks();
        if (!isText(list[list.length - 1])) { editor.insertBefore(textBlock(''), dropLine); }
        list = blocks();
        for (var j = list.length - 1; j > 0; j--) {
            if (!isText(list[j]) && !isText(list[j - 1])) { editor.insertBefore(textBlock(''), list[j]); }
        }
    }

    function layout() {
        var list = blocks();
        list.forEach(function (blk) {
            if (isText(blk)) {
                var ta = textOf(blk);
                // 블록 사이의 빈 칸은 얇게, 글이 하나뿐일 때는 넉넉하게
                blk.classList.toggle('only', list.length === 1);
                blk.classList.toggle('gap', list.length > 1 && !ta.value);
                autosize(ta);
            } else if (isCode(blk) && blk.paint) {
                blk.paint();
            }
        });
        refresh();
    }

    /** 블록 구조를 바꾼 뒤 부른다 — 정리 · 모양 맞추기 · 되돌리기 기록 */
    function normalize() {
        fixStructure();
        layout();
        record();
    }

    /**
     * 글 칸의 특별한 줄을 블록으로 바꾼다.
     *   ```언어 … ``` 로 닫힌 묶음 → 코드 블록 / 링크만 있는 줄 → 영상·사진 블록
     *   fenceShortcut이면 닫히지 않은 ``` 한 줄도 빈 코드 블록으로 (Enter로 바로 코드 쓰기)
     * keepTyping이면 바꾼 뒤 이어서 쓸 곳에 커서를 둔다.
     */
    function convertSpecial(ta, keepTyping, fenceShortcut) {
        // 한 칸은 한 번만 바꾼다 — 바꾸면서 이 칸을 지울 때 Chrome이 blur를 보내고, 그 blur가 같은 변환을 또 불러
        // 영상·사진이 두 개 생기던 문제를 막는다
        if (!editor.contains(ta) || ta.converting) { return; }
        var parts = splitFences(ta.value);
        var pieces = [];
        parts.forEach(function (p) {
            if (p.t === 'code') { pieces.push(p); return; }
            var buffer = [];
            p.v.split('\n').forEach(function (line) {
                var link = embedLinkOfLine(line);
                var shortcut = fenceShortcut && line.match(FENCE_OPEN);
                if (link || shortcut) {
                    pieces.push({ t: 'text', v: buffer.join('\n') });
                    buffer = [];
                    pieces.push(link ? { t: 'embed', link: link } : { t: 'code', lang: normalizeLanguage(shortcut[2] || lastLanguage), v: '', fresh: true });
                } else {
                    buffer.push(line);
                }
            });
            pieces.push({ t: 'text', v: buffer.join('\n') });
        });
        if (!pieces.some(function (p) { return p.t !== 'text'; })) { return; }

        checkpointFrom(ta);
        ta.converting = true;
        var block = ta.parentNode;
        var anchor = block.nextSibling;
        var focusTarget = null;
        var lastSpecialIndex = -1;
        var nodes = pieces.map(function (p, i) {
            var n = buildBlock(p);
            if (p.t !== 'text') { lastSpecialIndex = i; }
            if (p.fresh) { focusTarget = n; }
            return n;
        });
        nodes.forEach(function (n) { editor.insertBefore(n, anchor); });
        block.remove();
        normalize();
        if (!keepTyping) { return; }
        // 새 코드 블록이면 그 안에, 아니면 마지막으로 바뀐 블록 바로 다음 글 칸 맨 앞에 커서
        if (!focusTarget) {
            var next = nodes[lastSpecialIndex] ? nodes[lastSpecialIndex].nextElementSibling : null;
            focusTarget = next && next.classList.contains('ed-block') ? next : null;
        }
        if (focusTarget && editor.contains(focusTarget)) {
            var t = textOf(focusTarget);
            t.focus();
            t.setSelectionRange(0, 0);
        }
    }

    function buildBlock(p) {
        if (p.t === 'text') { return textBlock(p.v); }
        if (p.t === 'code') { return codeBlock(p.lang, p.v); }
        if (p.t === 'embed') { return embedBlock(p.link); }
        return imageBlock(p.k);
    }

    // ================================================================ 되돌리기 · 다시 하기

    var history = [];
    var historyIndex = -1;
    var restoring = false;
    var typingTimer = null;
    var HISTORY_LIMIT = 300;

    function snapshot() {
        var list = blocks();
        var active = document.activeElement;
        var focus = null;
        list.forEach(function (b, i) {
            var ta = textOf(b);
            if (ta && ta === active) { focus = { index: i, start: ta.selectionStart, end: ta.selectionEnd }; }
        });
        return {
            blocks: list.map(function (b) {
                if (isText(b)) { return { t: 'text', v: textOf(b).value }; }
                if (isCode(b)) { return { t: 'code', lang: b.getAttribute('data-lang'), v: textOf(b).value }; }
                if (b.classList.contains('ed-image')) { return { t: 'image', k: b.getAttribute('data-key') }; }
                return { t: 'embed', link: b.getAttribute('data-link') };
            }),
            focus: focus
        };
    }

    function sameBlocks(a, b) { return JSON.stringify(a.blocks) === JSON.stringify(b.blocks); }

    /** 지금 상태를 기록한다 (같으면 커서 위치만 갱신). 다시 하기 목록은 버린다. */
    function record() {
        if (restoring) { return; }
        clearTimeout(typingTimer);
        typingTimer = null;
        var s = snapshot();
        if (historyIndex >= 0 && sameBlocks(history[historyIndex], s)) {
            history[historyIndex].focus = s.focus;
            return;
        }
        history = history.slice(0, historyIndex + 1);
        history.push(s);
        if (history.length > HISTORY_LIMIT) { history.shift(); }
        historyIndex = history.length - 1;
    }

    /** 글자 입력은 잠깐 멈췄을 때 한 번에 기록한다 (한 글자씩 되돌리지 않게) */
    function scheduleRecord() {
        clearTimeout(typingTimer);
        typingTimer = setTimeout(record, 500);
    }

    /** 블록을 바꾸기 직전 상태를 남긴다 — 입력 중이던 글도 함께 */
    function checkpoint() { record(); }
    function checkpointFrom(ta) {
        record();
        // 붙여넣기·Enter로 바뀌는 경우, 바뀌기 전(링크·``` 줄이 글로 있던) 상태가 기록되도록 커서도 그 칸에
        if (historyIndex >= 0 && !history[historyIndex].focus) {
            var i = blocks().indexOf(ta.parentNode);
            if (i >= 0) { history[historyIndex].focus = { index: i, start: ta.selectionStart, end: ta.selectionEnd }; }
        }
    }

    function restore(s) {
        restoring = true;
        blocks().forEach(function (b) { b.remove(); });
        s.blocks.forEach(function (p) {
            if (p.t === 'image' && !images[p.k]) { return; }
            editor.insertBefore(buildBlock(p), dropLine);
        });
        fixStructure();
        layout();
        restoring = false;
        if (s.focus) {
            var target = blocks()[s.focus.index];
            var ta = target ? textOf(target) : null;
            if (ta) {
                ta.focus();
                ta.setSelectionRange(Math.min(s.focus.start, ta.value.length), Math.min(s.focus.end, ta.value.length));
            }
        }
    }

    function undo() {
        record(); // 입력 중이던 글을 먼저 남겨야 그 직전으로 돌아간다
        if (historyIndex <= 0) { return; }
        historyIndex--;
        restore(history[historyIndex]);
    }

    function redo() {
        record();
        if (historyIndex >= history.length - 1) { return; }
        historyIndex++;
        restore(history[historyIndex]);
    }

    // 편집기 안(또는 편집기를 누른 뒤 아무 곳도 고르지 않은 상태)에서만 가로챈다 — 제목 칸은 브라우저 기본 되돌리기
    document.addEventListener('keydown', function (e) {
        if (!(e.ctrlKey || e.metaKey) || e.altKey) { return; }
        var t = e.target;
        var inEditor = editor.contains(t) || t === document.body;
        if (!inEditor) { return; }
        var k = e.key.toLowerCase();
        if (k === 'z' && !e.shiftKey) {
            e.preventDefault();
            undo();
        } else if (k === 'y' || (k === 'z' && e.shiftKey)) {
            e.preventDefault();
            redo();
        }
    });

    // ================================================================ 사진 넣기 · 코드 넣기

    function formatSize(bytes) {
        return bytes >= 1024 * 1024 ? (bytes / 1024 / 1024).toFixed(1) + 'MB' : Math.max(1, Math.round(bytes / 1024)) + 'KB';
    }

    /** 지금 편집기에 들어 있는 사진만 — 빼거나 되돌려서 사라진 사진은 세지 않는다 */
    function usedImageKeys() {
        return blocks().filter(function (b) { return b.classList.contains('ed-image'); })
            .map(function (b) { return b.getAttribute('data-key'); });
    }

    function totalBytes() {
        return usedImageKeys().reduce(function (sum, k) { return sum + images[k].file.size; }, 0);
    }

    function showNotice(message) {
        notice.textContent = message;
        notice.hidden = !message;
    }

    /** 받은 파일 중 쓸 수 있는 사진만 등록하고 key 목록을 돌려준다 */
    function register(files) {
        var keys = [];
        var problems = [];
        var total = totalBytes();
        Array.prototype.forEach.call(files, function (file) {
            if (IMAGE_TYPES.indexOf(file.type) < 0) {
                problems.push('"' + (file.name || '붙여넣은 파일') + '"은(는) 사진 파일이 아닙니다 (PNG·JPG·GIF·WebP).');
            } else if (file.size > MAX_IMAGE) {
                problems.push('"' + (file.name || '붙여넣은 사진') + '"이(가) ' + formatSize(MAX_IMAGE) + '를 넘습니다.');
            } else if (total + file.size > MAX_TOTAL) {
                problems.push('사진은 모두 합쳐 ' + formatSize(MAX_TOTAL) + '까지 넣을 수 있습니다.');
            } else {
                var key = String(nextKey++);
                images[key] = { file: file, url: URL.createObjectURL(file) };
                total += file.size;
                keys.push(key);
            }
        });
        showNotice(problems.join(' '));
        return keys;
    }

    /** 글 칸 ta의 offset 자리에서 글을 나누고 그 사이에 블록들을 넣는다 */
    function insertAtText(ta, offset, nodes) {
        if (nodes.length === 0) { return; }
        checkpoint();
        var block = ta.parentNode;
        var after = ta.value.slice(offset).replace(/^\n/, '');
        ta.value = ta.value.slice(0, offset).replace(/\n$/, '');
        var anchor = block.nextSibling;
        nodes.forEach(function (n) { editor.insertBefore(n, anchor); });
        editor.insertBefore(textBlock(after), anchor);
        normalize();
    }

    function insertAtIndex(index, nodes) {
        if (nodes.length === 0) { return; }
        checkpoint();
        var list = blocks();
        var anchor = list[index] || dropLine;
        nodes.forEach(function (n) { editor.insertBefore(n, anchor); });
        normalize();
    }

    function insertAtCaret(nodes) {
        var ta = lastText && editor.contains(lastText) ? lastText : null;
        if (ta) {
            insertAtText(ta, ta.selectionStart, nodes);
        } else {
            insertAtIndex(blocks().length, nodes);
        }
    }

    document.getElementById('pickImages').addEventListener('click', function () { picker.click(); });
    picker.addEventListener('change', function () {
        insertAtCaret(register(picker.files).map(imageBlock));
        picker.value = '';
    });

    document.getElementById('insertCode').addEventListener('click', function () {
        var block = codeBlock(lastLanguage, '');
        insertAtCaret([block]);
        textOf(block).focus();
    });

    // 붙여넣기 — 사진이 들어 있으면 사진 블록으로 (코드 칸 안이어도 사진은 코드 바깥에 넣는다)
    editor.addEventListener('paste', function (e) {
        var items = e.clipboardData ? e.clipboardData.items : [];
        var files = [];
        Array.prototype.forEach.call(items, function (item) {
            if (item.kind === 'file') {
                var f = item.getAsFile();
                if (f) { files.push(f); }
            }
        });
        if (files.length === 0) { return; }
        e.preventDefault();
        var target = e.target.closest ? e.target.closest('.ed-block') : null;
        if (target && isText(target)) {
            lastText = textOf(target);
            insertAtCaret(register(files).map(imageBlock));
        } else if (target) {
            insertAtIndex(blocks().indexOf(target) + 1, register(files).map(imageBlock));
        } else {
            insertAtCaret(register(files).map(imageBlock));
        }
    });

    // ================================================================ 끌어서 옮기기 · 끌어다 놓기

    var dropLine = el('div', 'ed-drop-line');
    dropLine.hidden = true;
    editor.appendChild(dropLine);

    function hideDropLine() {
        dropLine.hidden = true;
        editor.classList.remove('file-over');
    }

    // 글 칸 안의 각 줄 시작 위치와 화면상 높이 — 자동 줄바꿈까지 반영하려고 같은 모양의 숨은 div로 잰다
    var mirror = el('div', 'ed-mirror');
    document.body.appendChild(mirror);

    function lineTops(ta) {
        var style = window.getComputedStyle(ta);
        ['fontFamily', 'fontSize', 'fontWeight', 'lineHeight', 'letterSpacing', 'paddingTop', 'paddingRight',
            'paddingBottom', 'paddingLeft', 'borderTopWidth', 'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth',
            'boxSizing', 'wordBreak'].forEach(function (p) { mirror.style[p] = style[p]; });
        mirror.style.width = ta.clientWidth + 'px';
        mirror.textContent = '';
        var tops = [];
        var offset = 0;
        ta.value.split('\n').forEach(function (line, i, all) {
            var span = el('span', null, line + (i < all.length - 1 ? '\n' : ''));
            mirror.appendChild(span);
            tops.push({ offset: offset, top: span.offsetTop });
            offset += line.length + 1;
        });
        tops.push({ offset: ta.value.length, top: mirror.scrollHeight - parseFloat(style.paddingBottom) });
        return tops;
    }

    /** 마우스 위치로 놓을 자리 — 글 칸 위면 가장 가까운 줄 경계 { text, offset }, 아니면 블록 사이 { index } */
    function dropTarget(clientY) {
        var list = blocks();
        for (var i = 0; i < list.length; i++) {
            var rect = list[i].getBoundingClientRect();
            if (clientY < rect.top) { return { index: i, y: rect.top - 3 }; }
            if (clientY <= rect.bottom) {
                if (isText(list[i]) && textOf(list[i]).value && list[i] !== dragging) {
                    var ta = textOf(list[i]);
                    var taRect = ta.getBoundingClientRect();
                    var tops = lineTops(ta);
                    var y = clientY - taRect.top;
                    var best = tops[0];
                    tops.forEach(function (t) { if (Math.abs(t.top - y) < Math.abs(best.top - y)) { best = t; } });
                    if (best.offset === 0) { return { index: i, y: rect.top - 3 }; }
                    if (best.offset >= ta.value.length) { return { index: i + 1, y: rect.bottom + 1 }; }
                    return { text: ta, offset: best.offset, y: taRect.top + best.top - 2 };
                }
                var mid = rect.top + rect.height / 2;
                return clientY < mid ? { index: i, y: rect.top - 3 } : { index: i + 1, y: rect.bottom + 1 };
            }
        }
        var last = list[list.length - 1].getBoundingClientRect();
        return { index: list.length, y: last.bottom + 1 };
    }

    function showDropLine(target) {
        var box = editor.getBoundingClientRect();
        dropLine.style.top = (target.y - box.top) + 'px';
        dropLine.hidden = false;
    }

    function hasFiles(e) {
        return e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') >= 0;
    }

    editor.addEventListener('dragover', function (e) {
        if (!dragging && !hasFiles(e)) { return; }
        e.preventDefault();
        e.dataTransfer.dropEffect = dragging ? 'move' : 'copy';
        editor.classList.toggle('file-over', !dragging);
        showDropLine(dropTarget(e.clientY));
    });
    editor.addEventListener('dragleave', function (e) {
        if (!editor.contains(e.relatedTarget)) { hideDropLine(); }
    });
    editor.addEventListener('drop', function (e) {
        if (!dragging && !hasFiles(e)) { return; }
        e.preventDefault();
        var target = dropTarget(e.clientY);
        hideDropLine();
        if (dragging) {
            var moving = dragging;
            if (target.text) {
                insertAtText(target.text, target.offset, [moving]);
            } else {
                var at = blocks()[target.index] || dropLine;
                if (at !== moving) { editor.insertBefore(moving, at); }
                normalize();
            }
            return;
        }
        var nodes = register(e.dataTransfer.files).map(imageBlock);
        if (target.text) {
            insertAtText(target.text, target.offset, nodes);
        } else {
            insertAtIndex(target.index, nodes);
        }
    });

    // ================================================================ 글자 수 · 상태

    function serialize() {
        var parts = [];
        blocks().forEach(function (b) {
            if (isText(b)) {
                var v = textOf(b).value;
                if (v.trim()) { parts.push(v); }
            } else if (isCode(b)) {
                parts.push(fence(b.getAttribute('data-lang'), textOf(b).value));
            } else if (b.classList.contains('ed-image')) {
                parts.push('[[upload:' + b.getAttribute('data-key') + ']]');
            } else {
                parts.push(b.getAttribute('data-link'));
            }
        });
        return parts.join('\n');
    }

    function countedLength() {
        var text = blocks().filter(isText).map(function (b) { return textOf(b).value; })
            .filter(function (v) { return v.trim(); }).join('\n');
        return Array.from(text.replace(URL_PATTERN, '')).length;
    }

    function refresh() {
        var n = countedLength();
        countEl.textContent = n + ' / ' + CONTENT_MAX + '자 (링크·사진·코드 제외)';
        countEl.classList.toggle('over', n > CONTENT_MAX);
        var count = usedImageKeys().length;
        imageStatus.textContent = count === 0 ? '' : '사진 ' + count + '장 · ' + formatSize(totalBytes()) + ' / ' + formatSize(MAX_TOTAL);
    }

    // ================================================================ 보내기

    form.addEventListener('submit', function (e) {
        blocks().filter(isText).forEach(function (b) { convertSpecial(textOf(b), false, false); });
        var n = countedLength();
        if (n > CONTENT_MAX) {
            e.preventDefault();
            showNotice('본문은 ' + CONTENT_MAX + '자까지 쓸 수 있습니다 (링크·사진·코드 제외, 지금 ' + n + '자).');
            return;
        }
        var content = serialize();
        if (!content.trim()) {
            e.preventDefault();
            showNotice('본문을 입력해 주세요.');
            return;
        }
        contentField.value = content;
        fileFields.textContent = '';
        try {
            usedImageKeys().forEach(function (key) {
                var input = el('input');
                input.type = 'file';
                input.name = 'image_' + key;
                var dt = new DataTransfer();
                dt.items.add(images[key].file);
                input.files = dt.files;
                fileFields.appendChild(input);
            });
        } catch (err) {
            e.preventDefault();
            showNotice('이 브라우저에서는 사진을 함께 보낼 수 없습니다. 최신 Chrome·Edge·Firefox·Safari에서 시도해 주세요.');
            return;
        }
        var button = document.getElementById('submitButton');
        button.disabled = true;
        button.textContent = '올리는 중…';
    });

    // ================================================================ 시작 — 다시 쓰기 화면이면 이전 글의 코드·링크 줄도 블록으로

    // 뒤로 가기로 돌아와 브라우저가 예전 화면을 통째로 꺼내 온 경우(bfcache) — 예전 블록이 그대로 남아 있으므로 새로 연다
    window.addEventListener('pageshow', function (e) {
        if (e.persisted) { window.location.reload(); }
    });

    // 처음 내용은 서버가 준 값(입력 오류로 다시 그린 화면)에서만 읽는다.
    // 숨은 본문 칸(contentField)은 브라우저가 예전 값을 되살릴 수 있어 읽지 않고 비운다.
    var initialTemplate = document.getElementById('initialContent');
    var initialContent = initialTemplate ? initialTemplate.content.textContent : '';
    contentField.value = '';
    fileFields.textContent = '';

    splitFences(initialContent).forEach(function (p) { editor.insertBefore(buildBlock(p), dropLine); });
    fixStructure();
    blocks().filter(isText).forEach(function (b) { convertSpecial(textOf(b), false, false); });
    fixStructure();
    layout();
    history = [];
    historyIndex = -1;
    record(); // 시작 상태 — 여기보다 앞으로는 되돌리지 않는다
    window.addEventListener('resize', layout);
})();
