/*
 * 테스트 계정 시뮬레이션 패널 (common/header.jsp 오른쪽 위).
 * 상태는 GET /simulation(JSON), 버튼은 POST /simulation action=start|pause|reset|follow — CSRF는 X-CSRF-Token 헤더.
 * 목표 점수를 넣고(또는 등급으로 채우고) 시작하면 총점이 그 점수에 닿을 때까지 하루씩 돈다. 끝나면 며칠 걸렸는지 보여준다.
 * 성향은 새 판을 시작할 때 고른다(비우면 무작위). 결과는 판마다 무작위로 달라진다.
 * 끝난 뒤 더 높은 점수를 넣고 시작하면 이어서 간다.
 *
 * 실시간 화면 갱신: 하루가 끝날 때마다 지금 보고 있는 페이지를 뒤에서 다시 받아, 바뀐 글자·숫자·속성만 그 자리에서 바꿔 끼운다
 *   (새로고침 없음, 스크롤 그대로, 바뀐 곳은 잠깐 금색으로 비침). 페이지 스크립트가 붙인 이벤트는 같은 요소를 그대로 쓰므로 유지된다.
 * 화면 따라가기: 켜면 방금 바뀐 화면(로드맵 단계를 끝낸 날 → 로드맵, 그 밖 → 대시보드)으로 이동한다.
 *   이동은 @view-transition(simulation.css)으로 깜빡임 없이 넘어가고, 한 화면에 최소 몇 초는 머문다. 끄면 직접 메뉴로 다니면 된다.
 * 초기화는 한 번 더 눌러야 실행된다(브라우저 확인 창은 쓰지 않음).
 */
(function () {
    'use strict';

    var panel = document.getElementById('simPanel');
    if (!panel) {
        return;
    }
    var endpoint = panel.dataset.endpoint;
    var ctx = endpoint.replace(/\/simulation$/, '');
    var csrf = panel.dataset.csrf;
    var fill = document.getElementById('simFill');
    var text = document.getElementById('simText');
    var dayLine = document.getElementById('simDay');
    var msg = document.getElementById('simMsg');
    var targetInput = document.getElementById('simTarget');
    var tierPick = document.getElementById('simTierPick');
    var personaPick = document.getElementById('simPersona');
    var buttons = {};
    panel.querySelectorAll('.sim-btn').forEach(function (b) { buttons[b.dataset.action] = b; });

    var POLL_MS = 1000;
    var MIN_STAY_MS = 6000;          // 화면 따라가기: 한 화면에 최소 이만큼 머문다
    var FOLLOW_KEY = 'simFollow';     // 켜 둔 상태는 이 브라우저에만 기억 (localStorage)
    var NAV_AT_KEY = 'simNavAt';      // 마지막으로 따라간 시각 (sessionStorage — 페이지를 넘어가도 유지)
    var FOCUS_KEY = 'simFocusStep';   // 따라간 뒤 보여줄 로드맵 단계

    var pollTimer = null;
    var msgTimer = null;
    var resetArmTimer = null;
    var lastStatus = null;
    var lastDays = null;
    var refreshing = false;
    var refreshAgain = false;
    var busy = false;
    var following = readFollow();

    // ---------------------------------------------------------------- 저장소 (없거나 막혀 있어도 동작)

    function readFollow() {
        try { return localStorage.getItem(FOLLOW_KEY) === '1'; } catch (e) { return false; }
    }
    function writeFollow(on) {
        try { localStorage.setItem(FOLLOW_KEY, on ? '1' : '0'); } catch (e) { /* 기억만 못 할 뿐 */ }
    }
    function session(key, value) {
        try {
            if (value === undefined) { return sessionStorage.getItem(key); }
            if (value === null) { sessionStorage.removeItem(key); } else { sessionStorage.setItem(key, value); }
        } catch (e) { return null; }
        return null;
    }

    // ---------------------------------------------------------------- 안내

    function showMessage(message, keep) {
        if (!message) {
            return;
        }
        msg.textContent = message;
        msg.hidden = false;
        clearTimeout(msgTimer);
        if (!keep) {
            msgTimer = setTimeout(function () { msg.hidden = true; }, 4000);
        }
    }

    // ---------------------------------------------------------------- 패널 그리기

    function num(n) {
        return Number(n || 0).toLocaleString('ko-KR');
    }

    function render(s) {
        var done = s.daysDone || 0;
        var target = s.targetScore;
        var score = s.score || 0;
        fill.style.width = target ? Math.min(100, Math.round(score * 100 / target)) + '%' : '0%';

        var label;
        if (s.status === 'IDLE' || !target) {
            label = '지금 ' + num(score) + '점';
        } else if (s.status === 'DONE') {
            label = s.reached ? '달성! ' + done + '일 걸림' : done + '일 동안 미달성';
        } else {
            label = num(score) + ' / ' + num(target) + '점 · ' + done + '일째' + (s.status === 'PAUSED' ? ' 멈춤' : '');
        }
        text.textContent = label;
        // 목표 칸 — 비어 있으면 지금 목표로 채워 둔다 (입력 중이면 건드리지 않음)
        if (target && !targetInput.value && document.activeElement !== targetInput) {
            targetInput.value = target;
        }
        targetInput.disabled = busy || s.status === 'RUNNING';
        tierPick.disabled = busy || s.status === 'RUNNING';
        // 성향은 새 판을 시작할 때만 고른다 — 판이 있으면 그 판의 성향을 보여 준다
        personaPick.disabled = busy || s.status !== 'IDLE';
        if (s.personaCode) {
            personaPick.value = s.personaCode;
        }
        panel.title = (s.persona ? '성향: ' + s.persona : '성향을 고르지 않으면 무작위') + (s.error ? '\n' + s.error : '');

        if (s.report && s.report.text && s.status !== 'IDLE') {
            dayLine.textContent = s.report.text;
            dayLine.hidden = false;
        } else {
            dayLine.hidden = true;
        }

        buttons.start.disabled = busy || s.status === 'RUNNING';
        buttons.start.textContent = s.status === 'PAUSED' ? '이어 하기' : s.status === 'DONE' ? '더 하기' : '시작';
        buttons.pause.disabled = busy || s.status !== 'RUNNING';
        buttons.reset.disabled = busy;
        buttons.follow.disabled = busy;
        buttons.follow.setAttribute('aria-pressed', following ? 'true' : 'false');

        // 하루가 지났으면 화면에 반영 — 따라가기면 그 화면으로, 아니면 지금 화면을 실시간 갱신
        if (lastDays !== null && done !== lastDays) {
            onNewDay(s);
        }
        if (lastStatus === 'RUNNING' && s.status === 'DONE') {
            showMessage(s.reached
                ? '목표 ' + num(target) + '점 달성! ' + done + '일 걸렸어요. 더 높은 점수를 넣고 "더 하기"를 누르면 이어서 갑니다.'
                : '최대 ' + done + '일까지 돌았는데 목표에 못 닿았어요 (' + num(score) + '점).', true);
        } else if (lastStatus === 'RUNNING' && s.status === 'PAUSED' && s.error) {
            showMessage(s.error, true);
        }
        // 서버를 다시 켜서 서버가 따라가기 상태를 잊었으면 다시 알려 준다
        if (s.status === 'RUNNING' && following && s.follow === false) {
            post('follow', { on: 'true' }, true);
        }
        lastStatus = s.status;
        lastDays = done;

        clearTimeout(pollTimer);
        if (s.status === 'RUNNING') {
            pollTimer = setTimeout(load, POLL_MS);
        }
    }

    function onNewDay(s) {
        var report = s.report;
        if (following && report && report.page) {
            var here = location.pathname.replace(ctx, '') || '/';
            var navAt = Number(session(NAV_AT_KEY) || 0);
            if (here !== report.page && Date.now() - navAt >= MIN_STAY_MS) {
                session(NAV_AT_KEY, String(Date.now()));
                session(FOCUS_KEY, report.stepId && report.page === '/roadmap' ? String(report.stepId) : null);
                location.href = ctx + report.page; // 깜빡임 없이 넘어간다 (@view-transition)
                return;
            }
            liveRefresh(report.page === '/roadmap' && report.stepId ? report.stepId : null);
            return;
        }
        liveRefresh(null);
    }

    // ---------------------------------------------------------------- 실시간 갱신 (바뀐 곳만)

    var REGIONS = ['main', 'aside.side-left', 'aside.side-right', '.nav-drawer'];

    function liveRefresh(focusStepId) {
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        fetch(location.pathname + location.search, { credentials: 'same-origin', headers: { 'Accept': 'text/html' } })
            .then(function (r) {
                // 로그인이 풀려 다른 화면으로 넘어갔으면 건드리지 않는다
                if (!r.ok || (r.redirected && new URL(r.url).pathname !== location.pathname)) {
                    return null;
                }
                return r.text();
            })
            .then(function (html) {
                if (!html) {
                    return;
                }
                var next = new DOMParser().parseFromString(html, 'text/html');
                REGIONS.forEach(function (sel) {
                    var cur = document.querySelector(sel);
                    var neu = next.querySelector(sel);
                    if (cur && neu) {
                        morph(cur, neu);
                    }
                });
                syncTierBadge(next);
                // 로드맵 길 그림은 창 크기가 바뀔 때 다시 그리게 되어 있다 — 완료된 단계까지 길을 다시 잇는다
                if (document.querySelector('.journey-track')) {
                    window.dispatchEvent(new Event('resize'));
                }
                if (focusStepId) {
                    focusStep(focusStepId);
                }
            })
            .catch(function () { /* 다음 날 다시 시도 */ })
            .then(function () {
                refreshing = false;
                if (refreshAgain) {
                    refreshAgain = false;
                    liveRefresh(null);
                }
            });
    }

    // 헤더의 등급·점수 배지 (패널은 건드리지 않는다)
    function syncTierBadge(next) {
        var cur = document.querySelector('header .tier-badge');
        var neu = next.querySelector('header .tier-badge');
        if (cur && neu) {
            morph(cur, neu);
        } else if (!cur && neu) {
            panel.parentNode.insertBefore(document.importNode(neu, true), panel);
        } else if (cur && !neu) {
            cur.remove();
        }
    }

    var SCRIPT_DRAWN = 'svg.journey-path';
    var flashed = [];

    function flash(el) {
        if (!el || el.nodeType !== 1 || flashed.length > 40 || el.closest('#simPanel')) {
            return;
        }
        flashed.push(el);
        el.classList.remove('sim-flash');
        void el.offsetWidth; // 애니메이션을 처음부터 다시
        el.classList.add('sim-flash');
        setTimeout(function () { el.classList.remove('sim-flash'); }, 1500);
    }

    /**
     * from(지금 화면)을 to(새로 받은 화면)와 같게 맞춘다. 같은 자리의 같은 태그는 요소를 그대로 두고 속성·글자만 바꾼다
     * (이벤트·열린 대화상자·입력 중인 칸 유지). 다른 태그면 그 요소만 바꿔 끼운다. 스크립트는 다시 실행하지 않는다.
     */
    function morph(from, to) {
        flashed = [];
        morphNode(from, to);
    }

    function morphNode(from, to) {
        if (from.nodeType !== to.nodeType || from.nodeName !== to.nodeName) {
            var copy = document.importNode(to, true);
            from.parentNode.replaceChild(copy, from);
            flash(copy.nodeType === 1 ? copy : copy.parentNode);
            return;
        }
        if (from.nodeType === 3 || from.nodeType === 8) {
            if (from.nodeValue !== to.nodeValue) {
                from.nodeValue = to.nodeValue;
                if (from.nodeType === 3 && to.nodeValue.trim()) {
                    flash(from.parentNode);
                }
            }
            return;
        }
        if (from.nodeType !== 1) {
            return;
        }
        var tag = from.nodeName;
        if (tag === 'SCRIPT' || tag === 'TEXTAREA' || from.id === 'simPanel') {
            return; // 스크립트·쓰던 글·패널 자신은 그대로
        }
        var typing = from === document.activeElement;
        syncAttributes(from, to, typing);
        if (tag === 'INPUT' || tag === 'SELECT' || typing) {
            return;
        }
        // 페이지 스크립트가 그려 넣은 요소(로드맵 길 그림 등)는 서버 HTML에 없으니 짝 맞추기에서 빼고 그대로 둔다
        var oldKids = Array.prototype.filter.call(from.childNodes, function (n) {
            return !(n.nodeType === 1 && n.matches(SCRIPT_DRAWN));
        });
        var newKids = to.childNodes;
        for (var i = 0; i < newKids.length; i++) {
            if (i < oldKids.length) {
                morphNode(oldKids[i], newKids[i]);
            } else {
                var added = document.importNode(newKids[i], true);
                from.appendChild(added);
                flash(added.nodeType === 1 ? added : from);
            }
        }
        for (var j = newKids.length; j < oldKids.length; j++) {
            if (oldKids[j].parentNode === from) {
                from.removeChild(oldKids[j]);
            }
        }
    }

    function syncAttributes(from, to, typing) {
        var changed = false;
        Array.prototype.forEach.call(to.attributes, function (a) {
            if (typing && a.name === 'value') {
                return;
            }
            if (from.getAttribute(a.name) !== a.value) {
                from.setAttribute(a.name, a.value);
                changed = a.name === 'class' || a.name === 'style' || changed;
            }
        });
        Array.prototype.slice.call(from.attributes).forEach(function (a) {
            // 사용자가 연 대화상자(open)·실시간 표시용 클래스는 남긴다
            if (!to.hasAttribute(a.name) && a.name !== 'open' && !(a.name === 'class' && /sim-(flash|focus)/.test(a.value))) {
                from.removeAttribute(a.name);
            }
        });
        if (changed && (from.className || '').indexOf('journey') >= 0) {
            flash(from); // 로드맵 카드가 완료로 바뀐 것 등
        }
    }

    function focusStep(stepId) {
        var row = document.querySelector('.journey-row[data-step-id="' + stepId + '"]');
        if (!row) {
            return;
        }
        row.scrollIntoView({ behavior: 'smooth', block: 'center' });
        row.classList.add('sim-focus');
        setTimeout(function () { row.classList.remove('sim-focus'); }, 2500);
    }

    // ---------------------------------------------------------------- 서버와 주고받기

    function load() {
        fetch(endpoint, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (r) { return r.json(); })
            .then(function (s) {
                if (s && s.status) {
                    render(s);
                }
            })
            .catch(function () {
                text.textContent = '상태 확인 실패';
                clearTimeout(pollTimer);
                pollTimer = setTimeout(load, POLL_MS * 2);
            });
    }

    function post(action, extra, quiet) {
        var body = new URLSearchParams();
        body.set('action', action);
        Object.keys(extra || {}).forEach(function (k) { body.set(k, extra[k]); });
        return fetch(endpoint, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-Token': csrf, 'Accept': 'application/json' },
            body: body
        }).then(function (r) {
            return r.json().then(function (data) { return { ok: r.ok, data: data, quiet: quiet }; });
        });
    }

    function send(action, extra) {
        busy = true;
        Object.keys(buttons).forEach(function (k) { buttons[k].disabled = true; });
        return post(action, extra)
            .then(function (res) {
                busy = false;
                showMessage(res.data && res.data.message, !res.ok);
                if (res.data && res.data.status) {
                    render(res.data);
                } else {
                    load();
                }
                return res;
            })
            .catch(function () {
                busy = false;
                showMessage('요청을 보내지 못했어요. 잠시 후 다시 눌러 주세요.', true);
                load();
            });
    }

    function disarmReset() {
        clearTimeout(resetArmTimer);
        buttons.reset.classList.remove('sim-confirm');
        buttons.reset.textContent = '초기화';
    }

    buttons.start.addEventListener('click', function () {
        var target = targetInput.value.trim();
        if (!target && lastStatus !== 'PAUSED') {
            showMessage('목표 점수를 넣거나 등급을 골라 주세요.');
            targetInput.focus();
            return;
        }
        if (following) {
            post('follow', { on: 'true' }, true); // 시작 전에 속도부터 맞춘다
        }
        var params = target ? { target: target } : {};
        if (lastStatus === 'IDLE') {
            params.persona = personaPick.value; // 비어 있으면 무작위
        }
        send('start', params);
    });
    tierPick.addEventListener('change', function () {
        if (tierPick.value) {
            targetInput.value = tierPick.value;
            tierPick.value = '';
        }
    });
    targetInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !buttons.start.disabled) {
            buttons.start.click();
        }
    });
    buttons.pause.addEventListener('click', function () { send('pause'); });
    buttons.follow.addEventListener('click', function () {
        following = !following;
        writeFollow(following);
        session(NAV_AT_KEY, null);
        buttons.follow.setAttribute('aria-pressed', following ? 'true' : 'false');
        send('follow', { on: following ? 'true' : 'false' });
        if (!following) {
            showMessage('화면 따라가기를 껐어요. 원하는 메뉴로 직접 들어가면 그 화면이 실시간으로 바뀝니다.');
        }
    });
    buttons.reset.addEventListener('click', function () {
        if (!buttons.reset.classList.contains('sim-confirm')) {
            buttons.reset.classList.add('sim-confirm');
            buttons.reset.textContent = '정말 지울까요?';
            showMessage('이 테스트 계정의 미션·점수·로드맵·그래프 기록을 지워요. 한 번 더 누르면 초기화합니다.');
            resetArmTimer = setTimeout(disarmReset, 4000);
            return;
        }
        disarmReset();
        send('reset').then(function (res) {
            if (res && res.ok) {
                lastDays = null;
                targetInput.value = '';
                personaPick.value = '';
                liveRefresh(null); // 지운 결과(점수·등급·로드맵)도 새로고침 없이
            }
        });
    });

    // 화면 따라가기로 넘어온 직후 — 방금 끝낸 로드맵 단계를 보여준다
    var focusId = session(FOCUS_KEY);
    if (focusId) {
        session(FOCUS_KEY, null);
        setTimeout(function () { focusStep(focusId); }, 300);
    }

    load();
})();
