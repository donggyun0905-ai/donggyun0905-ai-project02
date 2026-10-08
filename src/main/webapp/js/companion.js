/*
 * 오셍이들 화면 (/bot, bot.jsp) — 캐릭터 연결과 연결된 PC 목록.
 * [캐릭터 켜기] → 이 PC가 이미 연결돼 있으면 specodyssey://open 으로 설치된 캐릭터를 켜기만 한다(새 연결 없음).
 *               연결이 없으면 [캐릭터 연결]과 같다.
 * [캐릭터 연결] → POST /companion/connect로 일회용 코드가 든 specodyssey:// 주소를 받아 연다 → 윈도우가 캐릭터 exe를 켠다.
 * 브라우저는 프로그램이 설치됐는지 알려 주지 않는다 — 누르고 잠깐 동안 화면이 프로그램 쪽으로 넘어가지 않으면(포커스 유지)
 * 설치가 안 된 것으로 보고 내려받기를 묻는다. 틀릴 수 있어서 "이미 설치했어요"로 다시 시도할 수 있게 둔다.
 * 연결이 끝날 때까지 PC 목록을 몇 번 다시 불러온다 — 그 요청에서 서버가 이 브라우저에 "이 PC의 캐릭터" 쿠키를 남겨,
 * 이후 이 브라우저에서 로그인·로그아웃하면 캐릭터가 따라간다 (CompanionServlet, CompanionLinkFilter).
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
    var startBtn = document.getElementById('companionStart');
    var linkedHere = false; // 이 브라우저(PC)의 캐릭터가 연결돼 있나 — PC 목록의 thisPc
    var ask = document.getElementById('companionAsk');
    var download = document.getElementById('companionDownload');
    var retry = document.getElementById('companionRetry');
    var status = document.getElementById('companionStatus');
    var list = document.getElementById('companionDevices');
    var WAIT_MS = 2500;
    var POLL_MS = 3000;     // 연결을 기다리며 PC 목록을 다시 부르는 간격
    var POLL_TIMES = 20;    // 처음 설치하면 캐릭터가 뜨기까지 오래 걸릴 수 있어 1분 동안

    function post(path, params) {
        return fetch(base + path, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-CSRF-Token': csrf, 'Accept': 'application/json' },
            body: new URLSearchParams(params || {})
        }).then(function (r) { return r.json().then(function (d) { return { ok: r.ok, data: d }; }); });
    }

    // 주소를 열고, 잠깐 안에 화면이 프로그램 쪽으로 넘어가면 켜진 것 — 아니면 설치가 안 된 것으로 본다
    function openApp(url, onOpened, onMissing) {
        var left = false;
        function onLeave() { left = true; }
        window.addEventListener('blur', onLeave, { once: true });
        document.addEventListener('visibilitychange', onLeave, { once: true });
        location.href = url;
        setTimeout(function () {
            window.removeEventListener('blur', onLeave);
            if (left) {
                onOpened();
            } else {
                onMissing();
            }
        }, WAIT_MS);
    }

    function start() {
        if (!linkedHere) {
            launch(); // 아직 이 PC와 연결이 없다 — 연결까지 한다
            return;
        }
        startBtn.disabled = true;
        status.textContent = '캐릭터를 켜는 중…';
        openApp('specodyssey://open', function () {
            startBtn.disabled = false;
            ask.hidden = true;
            status.textContent = '캐릭터를 켰어요. 바탕화면 오른쪽 아래를 확인해 보세요.';
        }, function () {
            startBtn.disabled = false;
            prepareDownload();
            ask.hidden = false;
            status.textContent = '';
        });
    }

    function prepareDownload() {
        var top = document.getElementById('companionDownloadTop');
        if (top && top.getAttribute('href')) {
            download.href = top.getAttribute('href');
            download.textContent = '내려받기';
        }
    }

    function launch() {
        launchBtn.disabled = true;
        status.textContent = '캐릭터를 연결하는 중…';
        post('/companion/connect').then(function (res) {
            launchBtn.disabled = false;
            if (!res.ok) {
                status.textContent = (res.data && res.data.message) || '잠시 후 다시 눌러 주세요.';
                return;
            }
            if (res.data.downloadUrl) {
                download.href = res.data.downloadUrl;
                download.textContent = '내려받기' + (res.data.version ? ' (' + res.data.version + ' · ' + res.data.sizeText + ')' : '');
                download.removeAttribute('aria-disabled');
            } else {
                download.removeAttribute('href');
                download.setAttribute('aria-disabled', 'true');
                download.textContent = '설치 파일 준비 중';
            }
            openApp(res.data.launchUrl, function () {
                ask.hidden = true;
                status.textContent = '캐릭터를 불렀어요. 바탕화면 오른쪽 아래를 확인해 보세요.';
                waitForConnection(POLL_TIMES);
            }, function () {
                ask.hidden = false;
                status.textContent = '';
            });
        }).catch(function () {
            launchBtn.disabled = false;
            status.textContent = '요청을 보내지 못했어요. 잠시 후 다시 눌러 주세요.';
        });
    }

    // 연결이 끝나면 목록에 "이 PC"가 생긴다 — 생길 때까지 몇 번 다시 부른다
    function waitForConnection(left) {
        setTimeout(function () {
            loadDevices().then(function (d) {
                if (linkedHere) {
                    status.textContent = '연결됐어요! 이제 이 브라우저에서 로그인한 계정을 오셍이가 따라가요.';
                } else if (left > 1) {
                    waitForConnection(left - 1);
                }
            });
        }, POLL_MS);
    }

    function loadDevices() {
        return fetch(base + '/companion/devices', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function (r) { return r.json(); })
            .then(function (d) {
                linkedHere = !!(d && d.devices && d.devices.some(function (dev) { return dev.thisPc; }));
                list.textContent = '';
                if (!d.devices || !d.devices.length) {
                    var empty = document.createElement('li');
                    empty.className = 'muted';
                    empty.textContent = '아직 연결된 PC가 없어요.';
                    list.appendChild(empty);
                    return d;
                }
                d.devices.forEach(function (dev) {
                    var li = document.createElement('li');
                    li.className = 'spread';
                    var name = document.createElement('span');
                    name.textContent = dev.name + ' · 마지막 사용 ' + dev.lastUsed; // textContent — PC 이름은 사용자 입력
                    if (dev.thisPc) {
                        var chip = document.createElement('span');
                        chip.className = 'companion-this-pc';
                        chip.textContent = '이 PC';
                        name.appendChild(chip);
                    }
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
                return d;
            })
            .catch(function () {
                list.textContent = '';
                var li = document.createElement('li');
                li.className = 'muted';
                li.textContent = '목록을 불러오지 못했어요.';
                list.appendChild(li);
                return null;
            });
    }

    launchBtn.addEventListener('click', launch);
    if (startBtn) {
        startBtn.addEventListener('click', start);
    }
    retry.addEventListener('click', function () {
        ask.hidden = true;
        launch();
    });
    download.addEventListener('click', function () {
        status.textContent = download.hasAttribute('href')
            ? '내려받은 설치 파일을 실행해 주세요. 설치가 끝나면 "캐릭터 연결"을 다시 누르면 연결돼요.'
            : '아직 올라간 설치 파일이 없어요. 관리자에게 알려 주세요.';
    });
    loadDevices();
})();
