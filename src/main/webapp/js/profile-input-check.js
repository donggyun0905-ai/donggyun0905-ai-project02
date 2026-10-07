/*
 * 프로필 기술·스펙 이름 검사 (FR-23·25, 2026-10-06)
 * data-input-check="skill|spec" 칸에 입력하는 동안 서버(같은 폼의 action 주소 + ?check=)에 물어보고,
 * 엉터리 글자면 저장을 막고, 오타·줄임말이면 맞는 이름을 버튼으로 제안한다(누르면 칸의 값이 바뀐다).
 */
(function () {
    'use strict';

    var DELAY_MS = 450;

    document.querySelectorAll('input[data-input-check]').forEach(function (input) {
        var form = input.form;
        if (!form) { return; }
        var kind = input.getAttribute('data-input-check');
        var specType = form.querySelector('select[name="specType"]');
        var note = document.createElement('div');
        note.className = 'input-check';
        note.setAttribute('aria-live', 'polite');
        // 한 줄짜리 추가 폼은 폼 바로 아래, 수정 폼은 칸 바로 아래
        if (form.classList.contains('row')) {
            form.insertAdjacentElement('afterend', note);
        } else {
            input.insertAdjacentElement('afterend', note);
        }

        var timer = null;
        var seq = 0;
        var checkedKey = null;
        var lastResult = null;

        function key() {
            return input.value.trim() + '|' + (specType ? specType.value : '');
        }

        function check() {
            var value = input.value.trim();
            if (!value) { render(null); checkedKey = key(); return Promise.resolve(null); }
            var requestKey = key();
            var requestId = ++seq;
            var url = form.getAttribute('action') + '?check=' + encodeURIComponent(value)
                + (kind === 'spec' && specType ? '&specType=' + encodeURIComponent(specType.value) : '');
            return fetch(url, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
                .then(function (res) { return res.ok ? res.json() : null; })
                .then(function (data) {
                    // 늦게 온 응답이 최신 결과를 덮어쓰지 않게 한다
                    if (requestId !== seq) { return lastResult; }
                    checkedKey = requestKey;
                    render(data);
                    return data;
                })
                .catch(function () { render(null); return null; });
        }

        function render(data) {
            lastResult = data;
            note.textContent = '';
            note.className = 'input-check';
            input.classList.toggle('input-bad', !!data && data.status === 'GIBBERISH');
            if (!data || !data.message) { return; }
            note.classList.add('on', 'is-' + data.status.toLowerCase());
            var text = document.createElement('span');
            text.textContent = data.message;
            note.appendChild(text);
            (data.suggestions || []).forEach(function (name) {
                var pick = document.createElement('button');
                pick.type = 'button';
                pick.className = 'input-check-pick';
                pick.textContent = name;
                pick.addEventListener('click', function () {
                    input.value = name;
                    input.focus();
                    check();
                });
                note.appendChild(pick);
            });
        }

        input.addEventListener('input', function () {
            clearTimeout(timer);
            timer = setTimeout(check, DELAY_MS);
        });
        input.addEventListener('blur', function () {
            clearTimeout(timer);
            if (key() !== checkedKey) { check(); }
        });
        if (specType) {
            specType.addEventListener('change', function () { if (input.value.trim()) { check(); } });
        }

        // 저장 전에 최신 값으로 한 번 더 확인 — 엉터리 글자면 보내지 않는다(서버도 같은 기준으로 거절한다)
        form.addEventListener('submit', function (event) {
            if (key() === checkedKey) {
                if (lastResult && lastResult.status === 'GIBBERISH') {
                    event.preventDefault();
                    input.focus();
                }
                return;
            }
            event.preventDefault();
            clearTimeout(timer);
            check().then(function (data) {
                if (data && data.status === 'GIBBERISH') {
                    input.focus();
                    return;
                }
                form.submit();
            });
        });
    });
})();
