/*
 * 자소서·이력서 첨삭 비교 화면 (FR-91)
 *
 * 서버가 준 데이터(#feedback-data):
 *   { text: 첨삭받은 원문, items: [{ start, end, original, suggestion, reason }] }
 *   start·end는 text 안에서 original이 차지하는 위치 [start, end) — 서로 겹치지 않고 앞에서부터 정렬돼 있다.
 *
 * 제안 하나(hunk)를 단어 단위로 비교해 "같은 부분"과 "바뀐 묶음(change)"으로 나눈다.
 *   − 줄: 원문 (바뀌는 단어 빨강)   + 줄: 제안 (넣는 단어 초록)
 *   바뀐 묶음을 누르면 그 묶음만 적용/취소된다. 적용한 결과로 왼쪽 "내 글"이 다시 만들어지고,
 *   적용한 부분은 내 글에 초록 형광펜으로 표시된다.
 *
 * 보안: 원문·제안은 사용자 입력과 LLM 출력이라 절대 innerHTML로 넣지 않는다 — 전부 textContent / createTextNode.
 */
(function () {
    'use strict';

    var textarea = document.getElementById('content');
    var backdrop = document.getElementById('backdrop');
    var charCount = document.getElementById('charCount');
    var form = document.getElementById('feedbackForm');
    if (!textarea) {
        return;
    }

    // ---- 내 글: 형광펜 · 글자 수 ---------------------------------------------------------------
    // 내 글 칸은 화면에 고정되고(sticky) 글이 길면 칸 안에서 스크롤된다. 형광펜 층도 같은 만큼 스크롤시켜 글자와 맞춘다.
    var marks = []; // 형광펜 구간 { start, end, part } — 내 글(textarea.value) 기준. part = 그 구간을 만든 바뀐 묶음

    function syncScroll() {
        if (backdrop) {
            backdrop.scrollTop = textarea.scrollTop;
        }
    }

    function paintBackdrop() {
        if (!backdrop) {
            return;
        }
        var value = textarea.value;
        backdrop.textContent = '';
        var pos = 0;
        marks.forEach(function (m) {
            backdrop.appendChild(document.createTextNode(value.slice(pos, m.start)));
            var mark = document.createElement('mark');
            mark.textContent = value.slice(m.start, m.end);
            backdrop.appendChild(mark);
            m.element = mark;
            pos = m.end;
        });
        // 끝 줄바꿈은 textarea처럼 한 줄을 차지하게 공백 하나를 덧붙인다
        backdrop.appendChild(document.createTextNode(value.slice(pos) + ' '));
        syncScroll();
    }

    /** 방금 적용한 부분이 내 글 칸 안에 보이도록 칸을 스크롤한다 */
    function revealPart(part) {
        var target = null;
        marks.forEach(function (m) {
            if (m.part === part) { target = m; }
        });
        if (!target || !target.element) {
            return;
        }
        var top = target.element.offsetTop;
        var visibleTop = textarea.scrollTop;
        var visibleBottom = visibleTop + textarea.clientHeight;
        if (top < visibleTop + 20 || top > visibleBottom - 40) {
            textarea.scrollTop = Math.max(0, top - textarea.clientHeight / 3);
            syncScroll();
        }
    }

    function refreshEditor() {
        if (charCount) {
            charCount.textContent = textarea.value.length;
        }
        paintBackdrop();
    }
    textarea.addEventListener('scroll', syncScroll);
    window.addEventListener('resize', syncScroll);
    refreshEditor();

    if (form) {
        form.addEventListener('submit', function () {
            var button = document.getElementById('submitButton');
            button.disabled = true;
            button.textContent = '첨삭 중… (최대 1분)';
        });
    }

    var copyButton = document.getElementById('copyButton');
    var copyNotice = document.getElementById('copyNotice');
    if (copyButton) {
        copyButton.addEventListener('click', function () {
            var fallback = function () {
                textarea.focus();
                textarea.select();
                copyNotice.textContent = '글을 선택해 두었습니다. Ctrl+C로 복사하세요.';
            };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(textarea.value).then(function () {
                    copyNotice.textContent = '글을 복사했습니다. 원하는 곳에 붙여넣으세요.';
                }, fallback);
            } else {
                fallback();
            }
        });
    }

    // ---- 첨삭 비교 -----------------------------------------------------------------------
    var dataEl = document.getElementById('feedback-data');
    var doc = document.getElementById('diffDoc');
    if (!dataEl || !doc) {
        textarea.addEventListener('input', refreshEditor);
        return;
    }
    var data;
    try {
        data = JSON.parse(dataEl.textContent);
    } catch (e) {
        doc.textContent = '첨삭 결과를 표시하지 못했습니다. 다시 첨삭을 받아 주세요.';
        textarea.addEventListener('input', refreshEditor);
        return;
    }

    var source = data.text || '';
    var progress = document.getElementById('progress');
    var syncNotice = document.getElementById('syncNotice');

    // 지원자가 채울 자리표시 — 예: [어떤 문제를 겪었는지], [사용한 기술], [수치]
    var SLOT = /\[([^\[\]\n]{1,40})\]/g;

    /** 단어(공백 포함) 단위로 나눈다. 자리표시 [ ... ]는 안에 공백이 있어도 한 단어로 다룬다. */
    function tokenize(s) {
        return s.split(/(\[[^\[\]\n]{1,40}\]|\s+)/).filter(function (t) { return t.length > 0; });
    }

    /**
     * 원문 a → 제안 b를 LCS로 비교해 parts를 만든다.
     *   { type: 'same', text } | { type: 'change', del, add, applied }
     * 연속된 삭제·추가 단어는 하나의 change로 묶는다 (누를 때 이 단위로 적용된다).
     */
    function diffParts(a, b) {
        var x = tokenize(a), y = tokenize(b);
        var n = x.length, m = y.length;
        // 공백끼리는 짝을 짓지 않는다 — 공백을 맞추느라 "Docker로" 같은 실제 공통 단어를 놓치지 않게
        var same = function (p, q) { return p === q && !/^\s+$/.test(p); };
        var dp = [];
        for (var i = 0; i <= n; i++) {
            dp.push(new Array(m + 1).fill(0));
        }
        for (i = n - 1; i >= 0; i--) {
            for (var j = m - 1; j >= 0; j--) {
                dp[i][j] = same(x[i], y[j]) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        var parts = [];
        var pending = null;
        function flush() {
            if (pending) { parts.push(pending); pending = null; }
        }
        function change() {
            if (!pending) { pending = { type: 'change', del: '', add: '', applied: false }; }
            return pending;
        }
        i = 0; j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && same(x[i], y[j])) {
                flush();
                var last = parts[parts.length - 1];
                if (last && last.type === 'same') { last.text += x[i]; } else { parts.push({ type: 'same', text: x[i] }); }
                i++; j++;
            } else if (j >= m || (i < n && dp[i + 1][j] >= dp[i][j + 1])) {
                change().del += x[i]; i++;
            } else {
                change().add += y[j]; j++;
            }
        }
        flush();
        return mergeAcrossSpaces(parts);
    }

    /**
     * 바뀐 묶음 사이에 공백만 끼어 있으면 하나로 합친다.
     * 공백까지 "같은 부분"으로 잡으면 문장을 통째로 다시 쓴 제안이 "단절과→중", "협업은→[겪은"처럼
     * 단어끼리 엇갈려 잘게 쪼개져서, 하나씩 눌러 적용하는 의미가 없어진다.
     */
    function mergeAcrossSpaces(parts) {
        // 공백을 짝짓지 않아서 생긴 "공백 → 같은 공백" 묶음은 바뀐 게 아니므로 같은 부분으로 되돌린다
        var normalized = [];
        parts.forEach(function (p) {
            var q = p.type === 'change' && p.del === p.add ? { type: 'same', text: p.del } : p;
            var last = normalized[normalized.length - 1];
            if (q.type === 'same' && last && last.type === 'same') {
                last.text += q.text;
            } else {
                normalized.push(q);
            }
        });
        var merged = [];
        normalized.forEach(function (p) {
            var prev = merged[merged.length - 1];
            var beforePrev = merged[merged.length - 2];
            if (p.type === 'change' && prev && prev.type === 'same' && /^\s+$/.test(prev.text)
                    && beforePrev && beforePrev.type === 'change') {
                merged.pop();
                beforePrev.del += prev.text + p.del;
                beforePrev.add += prev.text + p.add;
            } else if (p.type === 'change' && prev && prev.type === 'change') {
                prev.del += p.del;
                prev.add += p.add;
            } else {
                merged.push(p);
            }
        });
        return merged;
    }

    var hunks = (data.items || []).map(function (it) {
        var h = { start: it.start, end: it.end, reason: it.reason || '', parts: diffParts(it.original, it.suggestion),
                  slots: [], fills: [] };
        // 넣는 부분 안의 자리표시마다 칸 번호를 매긴다 (문장 안에서 0, 1, 2 …)
        h.parts.forEach(function (p) {
            if (p.type !== 'change') { return; }
            p.slots = [];
            var match;
            SLOT.lastIndex = 0;
            while ((match = SLOT.exec(p.add)) !== null) {
                var slot = { index: h.slots.length, label: match[1].trim(), part: p };
                p.slots.push(slot);
                h.slots.push(slot);
            }
        });
        return h;
    });

    function changesOf(h) {
        return h.parts.filter(function (p) { return p.type === 'change'; });
    }

    // 자리표시 바로 뒤에 붙은 조사 — 채운 값의 받침에 맞춰 바꾼다 ("상황를" → "상황을")
    // 조사는 뒤에 공백·문장부호·끝이 올 때만 조사로 본다 ("[결과]이를"의 "이"는 조사가 아니다)
    var SLOT_WITH_PARTICLE = /\[([^\[\]\n]{1,40})\]((?:으로|을|를|이|가|은|는|과|와|로)(?=[\s.,!?·)]|$))?/g;
    var PARTICLES = { '을': ['을', '를'], '를': ['을', '를'], '이': ['이', '가'], '가': ['이', '가'],
                      '은': ['은', '는'], '는': ['은', '는'], '과': ['과', '와'], '와': ['과', '와'],
                      '으로': ['으로', '로'], '로': ['으로', '로'] };

    /** 조사를 value의 마지막 글자에 맞춘다. 마지막이 한글이 아니면(예: "Docker") 원래 조사를 둔다. */
    function fitParticle(value, particle) {
        if (!particle) {
            return '';
        }
        var code = value.charCodeAt(value.length - 1) - 0xAC00;
        if (code < 0 || code > 11171) {
            return particle;
        }
        var batchim = code % 28;
        var pair = PARTICLES[particle];
        if (particle === '으로' || particle === '로') {
            return batchim !== 0 && batchim !== 8 ? pair[0] : pair[1]; // ㄹ 받침(8)은 "로"
        }
        return batchim !== 0 ? pair[0] : pair[1];
    }

    /** 넣는 부분에서 자리표시를 사용자가 채운 값으로 바꾼 글. 비어 있는 칸은 [ … ] 그대로 둔다. */
    function filledAdd(h, p) {
        var k = 0;
        return p.add.replace(SLOT_WITH_PARTICLE, function (whole, label, particle) {
            var slot = p.slots[k++];
            var value = slot ? (h.fills[slot.index] || '').trim() : '';
            return value ? value + fitParticle(value, particle) : whole;
        });
    }

    /**
     * 원문 + 적용한 묶음으로 내 글을 다시 만든다. 적용한 "넣는 부분"의 위치도 함께 돌려준다 (형광펜용).
     */
    function compose() {
        var out = '';
        var applied = [];
        var pos = 0;
        hunks.forEach(function (h) {
            out += source.slice(pos, h.start);
            h.parts.forEach(function (p) {
                if (p.type === 'same') {
                    out += p.text;
                } else if (p.applied) {
                    var add = filledAdd(h, p);
                    if (add.trim()) {
                        // 앞뒤 공백은 형광펜에서 빼서 단어만 칠한다
                        var lead = add.length - add.replace(/^\s+/, '').length;
                        var trail = add.length - add.replace(/\s+$/, '').length;
                        applied.push({ start: out.length + lead, end: out.length + add.length - trail, part: p });
                    }
                    out += add;
                } else {
                    out += p.del;
                }
            });
            pos = h.end;
        });
        return { text: out + source.slice(pos), marks: applied };
    }

    // 스크립트가 마지막으로 내 글에 써 넣은 값. 사용자가 그 뒤 직접 고쳤는지 알아보는 데 쓴다.
    var lastWritten = textarea.value;

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text !== undefined) { node.textContent = text; }
        return node;
    }

    function setChunkState(node, part) {
        node.classList.toggle('applied', part.applied);
        node.setAttribute('aria-pressed', part.applied ? 'true' : 'false');
        node.title = part.applied ? '눌러서 적용 취소' : '눌러서 이 부분만 적용';
    }

    function chunk(tag, part) {
        var node = el(tag, 'rf-chunk');
        node.setAttribute('role', 'button');
        node.setAttribute('tabindex', '0');
        setChunkState(node, part);
        var toggle = function () { update(function () { part.applied = !part.applied; }, part); };
        node.addEventListener('click', toggle);
        node.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
        });
        return node;
    }

    /** 자리표시 칸 하나의 표시 글자 — 채웠으면 그 값, 아니면 [라벨] */
    function slotText(h, slot) {
        var value = (h.fills[slot.index] || '').trim();
        return value || '[' + slot.label + ']';
    }

    /** 넣는 부분을 그린다. 자리표시는 따로 감싸서 채운 값이 바로 보이게 한다. */
    function fillAddContent(node, h, p) {
        var k = 0;
        var pos = 0;
        var match;
        SLOT.lastIndex = 0;
        while ((match = SLOT.exec(p.add)) !== null) {
            node.appendChild(document.createTextNode(p.add.slice(pos, match.index)));
            var slot = p.slots[k++];
            var span = el('span', 'rf-slot', slot ? slotText(h, slot) : match[0]);
            if (slot) {
                span.classList.toggle('filled', !!(h.fills[slot.index] || '').trim());
                slot.span = span;
            }
            node.appendChild(span);
            pos = match.index + match[0].length;
        }
        node.appendChild(document.createTextNode(p.add.slice(pos)));
    }

    function line(kind, h) {
        var row = el('div', 'rf-line ' + kind);
        row.appendChild(el('span', 'sign', kind === 'del' ? '−' : '+'));
        var body = el('span');
        h.parts.forEach(function (p) {
            if (p.type === 'same') {
                body.appendChild(document.createTextNode(p.text));
                return;
            }
            var text = kind === 'del' ? p.del : p.add;
            if (!text) {
                return;
            }
            // 공백만 바뀐 묶음은 누를 거리가 없으니 글자 그대로 둔다
            if (!text.trim()) {
                body.appendChild(document.createTextNode(text));
                return;
            }
            var node = chunk(kind === 'del' ? 'del' : 'ins', p);
            if (kind === 'del') {
                node.textContent = text;
                p.delEl = node;
            } else {
                fillAddContent(node, h, p);
                p.addEl = node;
            }
            body.appendChild(node);
        });
        row.appendChild(body);
        return row;
    }

    /**
     * 자리표시 채우기 칸 — "어떤 문제를 겪었는지: [입력칸]".
     * 입력할 때마다 화면 전체를 다시 그리면 한글 조합 중인 입력칸이 사라져 글자가 깨지므로,
     * 입력 중에는 바뀐 곳만 그 자리에서 고치고(fillChanged) 칸을 벗어날 때 한 번 다시 그린다.
     */
    function fillForm(h, hunkIndex) {
        var box = el('div', 'rf-fill');
        box.appendChild(el('div', 'rf-fill-title', '직접 채워 넣기'));
        h.slots.forEach(function (slot) {
            var id = 'rf-fill-' + hunkIndex + '-' + slot.index;
            var label = el('label', null, slot.label + ':');
            label.setAttribute('for', id);
            var input = el('input');
            input.type = 'text';
            input.id = id;
            input.maxLength = 200;
            input.autocomplete = 'off';
            input.placeholder = '여기에 입력';
            input.value = h.fills[slot.index] || '';
            input.addEventListener('input', function () { fillChanged(h, slot, input.value); });
            box.appendChild(label);
            box.appendChild(input);
        });
        return box;
    }

    function fillChanged(h, slot, value) {
        if (textarea.value !== lastWritten &&
                !window.confirm('왼쪽 글을 첨삭 뒤에 직접 고쳤습니다. 제안을 반영하면 직접 고친 내용이 사라집니다. 계속할까요?')) {
            return;
        }
        h.fills[slot.index] = value;
        var part = slot.part;
        var newlyApplied = false;
        // 채우기 시작하면 그 묶음을 쓰겠다는 뜻이니 자동으로 적용한다
        if (value.trim() && !part.applied) {
            part.applied = true;
            newlyApplied = true;
            if (part.addEl) { setChunkState(part.addEl, part); }
            if (part.delEl) { setChunkState(part.delEl, part); }
        }
        if (slot.span) {
            slot.span.textContent = slotText(h, slot);
            slot.span.classList.toggle('filled', !!value.trim());
        }
        writeText(newlyApplied ? part : null);
        refreshCounts();
    }

    /** 문장 테두리 색·"N / M 적용"·전체 진행 표시만 다시 계산한다 (입력칸은 건드리지 않음) */
    function refreshCounts() {
        var total = 0;
        var appliedTotal = 0;
        hunks.forEach(function (h) {
            var changes = changesOf(h);
            var applied = changes.filter(function (p) { return p.applied; }).length;
            total += changes.length;
            appliedTotal += applied;
            if (h.box) {
                h.box.className = 'rf-hunk ' + (applied === 0 ? 'none' : applied === changes.length ? 'all' : 'some');
            }
            if (h.countEl) {
                h.countEl.textContent = applied + ' / ' + changes.length + ' 적용';
            }
            if (h.applyBtn) {
                h.applyBtn.hidden = applied === changes.length;
                h.revertBtn.hidden = applied === 0;
            }
        });
        if (progress) {
            progress.textContent = total === 0 ? '' : '바꾼 부분 ' + appliedTotal + ' / ' + total;
        }
    }

    function button(label, className, onClick) {
        var b = el('button', className, label);
        b.type = 'button';
        b.addEventListener('click', onClick);
        return b;
    }

    function hunkView(h, index) {
        var changes = changesOf(h);
        var applied = changes.filter(function (p) { return p.applied; }).length;
        var box = el('div', 'rf-hunk ' + (applied === 0 ? 'none' : applied === changes.length ? 'all' : 'some'));
        box.setAttribute('aria-label', '제안 ' + (index + 1));
        box.appendChild(line('del', h));
        box.appendChild(line('add', h));
        if (h.slots.length > 0) {
            box.appendChild(fillForm(h, index));
        }
        h.box = box;

        var foot = el('div', 'rf-hunk-foot');
        foot.appendChild(el('span', 'reason', h.reason));
        var actions = el('span', 'actions');
        h.countEl = el('span', 'count');
        actions.appendChild(h.countEl);
        // 보임/숨김은 refreshCounts가 정한다 — 입력 중에도 화면을 다시 그리지 않고 갱신할 수 있게
        h.applyBtn = button('이 문장 전부 바꾸기', null, function () {
            update(function () { changes.forEach(function (p) { p.applied = true; }); }, changes[0]);
        });
        h.revertBtn = button('원문으로', 'secondary', function () {
            update(function () { changes.forEach(function (p) { p.applied = false; }); });
        });
        actions.appendChild(h.applyBtn);
        actions.appendChild(h.revertBtn);
        foot.appendChild(actions);
        box.appendChild(foot);
        return box;
    }

    function render() {
        doc.textContent = '';
        var pos = 0;
        hunks.forEach(function (h, index) {
            doc.appendChild(document.createTextNode(source.slice(pos, h.start)));
            doc.appendChild(hunkView(h, index));
            pos = h.end;
        });
        doc.appendChild(document.createTextNode(source.slice(pos)));
        refreshCounts();
    }

    /** 지금 선택·채운 값으로 내 글을 다시 만들어 넣는다. focusPart가 있으면 그 부분이 보이게 스크롤한다. */
    function writeText(focusPart) {
        var keepScroll = textarea.scrollTop;
        var result = compose();
        textarea.value = result.text;
        textarea.scrollTop = keepScroll; // value를 바꾸면 브라우저가 스크롤을 맨 위로 돌리는 경우가 있다
        lastWritten = result.text;
        marks = result.marks;
        syncNotice.hidden = true;
        refreshEditor();
        if (focusPart && focusPart.applied) {
            revealPart(focusPart);
        }
    }

    /**
     * 선택을 바꾸고 내 글을 다시 만든다.
     * 첨삭 뒤에 사용자가 내 글을 직접 고쳤다면, 덮어쓰기 전에 한 번 묻는다.
     */
    function update(mutate, focusPart) {
        if (textarea.value !== lastWritten &&
                !window.confirm('왼쪽 글을 첨삭 뒤에 직접 고쳤습니다. 제안을 반영하면 직접 고친 내용이 사라집니다. 계속할까요?')) {
            return;
        }
        mutate();
        writeText(focusPart);
        render();
    }

    textarea.addEventListener('input', function () {
        var changed = textarea.value !== lastWritten;
        // 직접 고치면 위치가 어긋나므로 형광펜을 지운다
        if (changed) {
            marks = [];
        }
        syncNotice.hidden = !changed;
        syncNotice.textContent = changed
            ? '왼쪽 글을 직접 고쳐서 형광펜 표시를 지웠습니다. 오른쪽 비교는 첨삭받을 때의 글 기준입니다. 고친 글로 다시 보려면 "AI 첨삭 받기"를 누르세요.'
            : '';
        refreshEditor();
    });

    var applyAll = document.getElementById('applyAll');
    var resetAll = document.getElementById('resetAll');
    function setAll(value) {
        update(function () { hunks.forEach(function (h) { changesOf(h).forEach(function (p) { p.applied = value; }); }); });
    }
    if (hunks.length === 0) {
        if (applyAll) { applyAll.hidden = true; }
        if (resetAll) { resetAll.hidden = true; }
    } else {
        applyAll.addEventListener('click', function () { setAll(true); });
        resetAll.addEventListener('click', function () { setAll(false); });
    }
    render();
})();
