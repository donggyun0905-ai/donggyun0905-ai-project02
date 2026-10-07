/*
 * 내 프로필 > 데스크톱 캐릭터 (profile/_companion.jspf).
 * [캐릭터 켜기] → POST /companion/connect로 일회용 코드가 든 specodyssey:// 주소를 받아 연다 → 윈도우가 캐릭터 exe를 켠다.
 * 브라우저는 프로그램이 설치됐는지 알려 주지 않는다 — 누르고 잠깐 동안 화면이 프로그램 쪽으로 넘어가지 않으면(포커스 유지)
 * 설치가 안 된 것으로 보고 내려받기를 묻는다. 틀릴 수 있어서 "이미 설치했어요"로 다시 시도할 수 있게 둔다.
 */
(function () {
    'use strict';

    var card = document.getElementById('companionCard');
    if (!card) {
        return;
    }
    var base = card.dataset.base;
    var csrf = card.dataset.csrf;
    var launchBtn = document.getElementById('companionLaunch');
    var ask = document.getElementById('companionAsk');
    var download = document.getElementById('companionDownload');
    var retry = document.getElementById('companionRetry');
    var status = document.getElementById('companionStatus');
    var list = document.getElementById('companionDevices');
    var WAIT_MS = 2500;

    function post(path, params) {
        return fetch(base + path, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-Token': csrf, 'Accept': 'application/json' },
            body: new URLSearchParams(params || {})
        }).then(function (r) { return r.json().then(function (d) { return { ok: r.ok, data: d }; }); });
    }

    function launch() {
        launchBtn.disabled = true;
        status.textContent = '캐릭터를 켜는 중…';
        post('/companion/connect').then(function (res) {
            launchBtn.disabled = false;
            if (!res.ok) {
                status.textContent = (res.data && res.data.message) || '잠시 후 다시 눌러 주세요.';
                return;
            }
            download.href = res.data.downloadUrl;
            var left = false;
            function onLeave() { left = true; }
            window.addEventListener('blur', onLeave, { once: true });
            document.addEventListener('visibilitychange', onLeave, { once: true });
            location.href = res.data.launchUrl;
            setTimeout(function () {
                window.removeEventListener('blur', onLeave);
                if (left) {
                    ask.hidden = true;
                    status.textContent = '캐릭터를 켰어요. 바탕화면 오른쪽 아래를 확인해 보세요.';
                    setTimeout(loadDevices, 4000); // 연결이 끝나면 목록에 PC가 생긴다
                } else {
                    ask.hidden = false;
                    status.textContent = '';
                }
            }, WAIT_MS);
        }).catch(function () {
            launchBtn.disabled = false;
            status.textContent = '요청을 보내지 못했어요. 잠시 후 다시 눌러 주세요.';
        });
    }

    function loadDevices() {
        fetch(base + '/companion/devices', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (r) { return r.json(); })
            .then(function (d) {
                list.textContent = '';
                if (!d.devices || !d.devices.length) {
                    var empty = document.createElement('li');
                    empty.className = 'muted';
                    empty.textContent = '아직 연결된 PC가 없어요.';
                    list.appendChild(empty);
                    return;
                }
                d.devices.forEach(function (dev) {
                    var li = document.createElement('li');
                    li.className = 'spread';
                    var name = document.createElement('span');
                    name.textContent = dev.name + ' · 마지막 사용 ' + dev.lastUsed; // textContent — PC 이름은 사용자 입력
                    var btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'secondary';
                    btn.style.padding = '5px 12px';
                    btn.textContent = '연결 해제';
                    btn.addEventListener('click', function () {
                        btn.disabled = true;
                        post('/companion/revoke', { id: dev.id }).then(loadDevices);
                    });
                    li.appendChild(name);
                    li.appendChild(btn);
                    list.appendChild(li);
                });
            })
            .catch(function () {
                list.textContent = '';
                var li = document.createElement('li');
                li.className = 'muted';
                li.textContent = '목록을 불러오지 못했어요.';
                list.appendChild(li);
            });
    }

    launchBtn.addEventListener('click', launch);
    retry.addEventListener('click', function () {
        ask.hidden = true;
        launch();
    });
    download.addEventListener('click', function () {
        status.textContent = '내려받은 압축을 풀고 SpecOdysseyCompanion.exe를 실행한 뒤 "캐릭터 켜기"를 다시 눌러 주세요.';
    });
    loadDevices();
})();
