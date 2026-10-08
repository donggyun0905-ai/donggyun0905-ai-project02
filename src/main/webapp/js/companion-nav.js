/*
 * 메뉴 "성장 도구"의 데스크톱 캐릭터 항목 (common/companion-nav.jspf).
 *
 * 두 가지 모습이 있고 어느 쪽인지는 서버가 정한다(CompanionNavFilter → data-companion-nav).
 *   download  설치 파일 링크. JS가 없어도 받아진다. 받고 나면 이 자리에서 바로 [캐릭터 켜기]로 바뀐다.
 *   launch    프로필의 [캐릭터 켜기]와 같은 일 — 일회용 코드를 받아 specodyssey:// 로 캐릭터를 켠다.
 *
 * 켜기가 실패했는지(= 설치가 안 됐는지) 브라우저는 알려 주지 않는다. 그래서 프로필 쪽과 같은 방법을
 * 쓴다: 주소를 열고 잠깐 기다려 화면이 프로그램으로 넘어가지 않으면 설치가 안 된 것으로 보고
 * 프로필 "데스크톱 캐릭터" 칸으로 보낸다. 거기에 "이미 설치했어요 / 내려받기"가 있어 되돌릴 수 있다.
 *
 * 서버의 "받아갔다" 표시는 세션에 있어서 다시 로그인하면 지워진다. 같은 브라우저라면 또 받을 필요가
 * 없으니 localStorage로 한 번 더 기억해 둔다 — 읽기·쓰기 모두 실패할 수 있어 try로 감싼다
 * (시크릿 창, 사이트 데이터 차단).
 */
(function () {
    'use strict';

    var item = document.querySelector('a[data-companion-nav]');
    if (!item) {
        return;
    }
    var base = item.dataset.base;
    var csrf = item.dataset.csrf;
    var STORE_KEY = 'companionDownloaded';
    var WAIT_MS = 2500; // js/companion.js와 같은 값 — 프로그램이 뜨기까지 기다리는 시간

    function remember() {
        try {
            localStorage.setItem(STORE_KEY, '1');
        } catch (e) {
            /* 저장이 막혀 있어도 서버 세션 표시가 있으니 이번 방문은 정상 동작한다 */
        }
    }

    function remembered() {
        try {
            return localStorage.getItem(STORE_KEY) === '1';
        } catch (e) {
            return false;
        }
    }

    /** [캐릭터 내려받기]를 [캐릭터 켜기]로 바꾼다 — 새로고침을 기다리지 않게. */
    function becomeLaunch() {
        item.dataset.companionNav = 'launch';
        item.classList.remove('companion-nav-download');
        item.classList.add('companion-nav-launch');
        item.setAttribute('href', base + '/profile#companion');
        var badge = item.querySelector('.companion-nav-new');
        if (badge) {
            badge.remove();
        }
        var icon = item.querySelector('.ic');
        if (icon) {
            icon.className = 'ic ic-sparkles';
        }
        item.textContent = '';
        if (icon) {
            item.appendChild(icon);
            item.appendChild(document.createTextNode(' '));
        }
        var label = document.createElement('span');
        label.className = 'companion-nav-label';
        label.textContent = '캐릭터 켜기';
        item.appendChild(label);
    }

    function toProfile() {
        location.href = base + '/profile#companion';
    }

    function launch() {
        item.classList.add('busy');
        fetch(base + '/companion/connect', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-Token': csrf, 'Accept': 'application/json' },
            body: new URLSearchParams()
        }).then(function (r) {
            if (!r.ok) {
                throw new Error('HTTP ' + r.status);
            }
            return r.json();
        }).then(function (data) {
            var left = false;
            function onLeave() { left = true; }
            window.addEventListener('blur', onLeave, { once: true });
            document.addEventListener('visibilitychange', onLeave, { once: true });
            location.href = data.launchUrl;
            setTimeout(function () {
                window.removeEventListener('blur', onLeave);
                item.classList.remove('busy');
                if (!left) {
                    // 프로그램이 안 열렸다 — 설치가 안 됐을 가능성이 크다. 되돌릴 수 있는 화면으로 보낸다.
                    toProfile();
                }
            }, WAIT_MS);
        }).catch(function () {
            item.classList.remove('busy');
            toProfile(); // 코드를 못 받았으면 프로필에서 상태를 보고 다시 하게 한다
        });
    }

    // 세션은 지워졌지만 이 브라우저는 이미 받아간 적이 있다 → 바로 켜기로 보여 준다
    if (item.dataset.companionNav === 'download' && remembered()) {
        becomeLaunch();
    }

    item.addEventListener('click', function (event) {
        if (item.dataset.companionNav === 'download') {
            // 링크는 그대로 둔다(브라우저가 파일을 받는다). 받았다는 사실만 남기고 모습을 바꾼다.
            remember();
            setTimeout(becomeLaunch, 600); // 내려받기가 시작된 뒤에 바꿔야 눌린 느낌이 끊기지 않는다
            return;
        }
        event.preventDefault(); // href(프로필)는 JS가 안 될 때의 대비책이다
        launch();
    });
})();
