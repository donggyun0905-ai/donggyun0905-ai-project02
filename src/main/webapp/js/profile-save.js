/*
 * 내 프로필 — 저장하고 나면 보던 자리로 돌아오게 한다 (2026-10-12).
 *
 * 프로필의 폼들은 각자 다른 서블릿으로 POST하고, 끝나면 /profile 로 리다이렉트한다
 * (POST-Redirect-GET). 새 요청이라 브라우저가 화면을 맨 위로 올려 버려서, 아래쪽
 * 프로젝트나 기술을 고치고 나면 한참 다시 스크롤해 내려와야 했다.
 *
 * 서버를 고치려면 폼마다 흩어진 리다이렉트를 전부 손봐야 한다. 그래서 화면 쪽에서
 * 한 번에 해결한다 — 보내기 직전에 스크롤 위치를 적어 두고, 돌아왔을 때 그 자리로
 * 되돌린 뒤 "저장되었습니다"를 잠깐 띄운다.
 *
 * sessionStorage 는 막혀 있을 수 있다(시크릿 창, 사이트 데이터 차단). 읽기·쓰기를
 * 모두 try 로 감쌌고, 실패하면 예전처럼 맨 위로 가기만 한다 — 저장 자체는 영향이 없다.
 */
(function () {
    'use strict';

    var KEY = 'profileSaveMark';
    var MAX_AGE_MS = 15000;   // 저장하고 돌아오는 데 이보다 오래 걸렸다면 다른 이동으로 본다
    var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function write(v) { try { sessionStorage.setItem(KEY, JSON.stringify(v)); } catch (e) { /* 막혀 있어도 저장은 된다 */ } }
    function read() { try { return JSON.parse(sessionStorage.getItem(KEY)); } catch (e) { return null; } }
    function clear() { try { sessionStorage.removeItem(KEY); } catch (e) { /* 무시 */ } }

    /* 누른 버튼의 글자로 무슨 일을 했는지 가린다 — 지웠는데 "저장되었습니다"가 뜨면 이상하다 */
    function resultText(label) {
        if (!label) { return '저장되었습니다'; }
        if (label.indexOf('삭제') >= 0 || label.indexOf('지우') >= 0) { return '삭제되었습니다'; }
        if (label.indexOf('해제') >= 0) { return '해제되었습니다'; }
        if (label.indexOf('올리') >= 0 || label.indexOf('업로드') >= 0) { return '올렸습니다'; }
        return '저장되었습니다';
    }

    /* ---------- 보내기 직전: 지금 위치를 적어 둔다 ---------- */
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form || form.tagName !== 'FORM') { return; }
        if ((form.getAttribute('method') || 'get').toLowerCase() !== 'post') { return; }

        var btn = event.submitter || form.querySelector('button[type="submit"], button:not([type])');
        write({
            y: window.scrollY || document.documentElement.scrollTop || 0,
            at: Date.now(),
            label: btn ? (btn.textContent || '').trim() : ''
        });
    }, true);

    /* ---------- 돌아왔을 때: 그 자리로 되돌리고 결과를 알려 준다 ---------- */
    var mark = read();
    clear();   // 한 번만 쓴다. 남겨 두면 다음에 그냥 들어와도 또 뜬다.
    if (!mark || typeof mark.y !== 'number' || Date.now() - mark.at > MAX_AGE_MS) { return; }

    /* 사용자가 직접 스크롤하면 그 뒤로는 우리가 건드리지 않는다.
       scroll 이벤트는 우리가 옮겨도 발생하므로, 사람이 한 행동만 본다. */
    var userMoved = false;
    ['wheel', 'touchmove', 'keydown'].forEach(function (type) {
        window.addEventListener(type, function () { userMoved = true; }, { passive: true, once: true });
    });

    function restore() {
        if (userMoved) { return; }
        window.scrollTo(0, mark.y);
    }

    function toast(text) {
        var el = document.createElement('div');
        el.setAttribute('role', 'status');
        el.textContent = text;
        el.style.cssText = [
            'position:fixed', 'left:50%', 'bottom:28px', 'transform:translateX(-50%)',
            'z-index:9999', 'padding:11px 20px', 'border-radius:999px',
            'background:var(--teal,#1f7a6c)', 'color:#fff',
            'font-size:0.92rem', 'font-weight:600',
            'box-shadow:0 6px 20px rgba(58,47,36,0.28)',
            'pointer-events:none'
        ].join(';');
        if (!reduced) {
            el.style.opacity = '0';
            el.style.transition = 'opacity .18s ease, transform .18s ease';
            el.style.transform = 'translateX(-50%) translateY(6px)';
        }
        document.body.appendChild(el);
        if (!reduced) {
            requestAnimationFrame(function () {
                el.style.opacity = '1';
                el.style.transform = 'translateX(-50%) translateY(0)';
            });
        }
        setTimeout(function () {
            if (reduced) { el.remove(); return; }
            el.style.opacity = '0';
            setTimeout(function () { el.remove(); }, 220);
        }, 2200);
    }

    function run() {
        restore();
        /* 글꼴·이미지가 늦게 올라오면 높이가 바뀐다 — 한 번 더 맞춘다 */
        requestAnimationFrame(restore);
        window.addEventListener('load', restore, { once: true });

        /* 오류 문구가 떠 있으면 저장이 안 된 것이다. 성공했다고 말하면 안 된다. */
        if (document.querySelector('.error-message')) { return; }
        toast(resultText(mark.label));
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', run, { once: true });
    } else {
        run();
    }
})();
