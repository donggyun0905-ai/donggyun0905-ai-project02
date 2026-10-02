/*
 * 메인 소개(랜딩) 화면의 움직임 — landing.jsp에서만 불러온다.
 * 화면 연출만 담당하고 서버 호출은 하지 않는다 (AI·외부 API 호출은 서블릿에서만).
 */
(function () {
    'use strict';

    var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var root = document.documentElement;
    root.classList.add('lp-js');

    function $(sel, ctx) { return (ctx || document).querySelector(sel); }
    function $$(sel, ctx) { return Array.prototype.slice.call((ctx || document).querySelectorAll(sel)); }

    // ------------------------------------------------------------ 숫자 카운트업
    function countUp(el) {
        var to = Number(el.dataset.to);
        if (reduced) { el.textContent = to; return; }
        var start = null, dur = 1400;
        function tick(now) {
            if (start === null) start = now;
            var t = Math.min((now - start) / dur, 1);
            var eased = 1 - Math.pow(1 - t, 3);
            el.textContent = Math.round(to * eased);
            if (t < 1) requestAnimationFrame(tick);
        }
        requestAnimationFrame(tick);
    }

    // ------------------------------------------------------------ 스크롤 등장
    var revealTargets = $$('.reveal');
    var counters = $$('.lp-stats .lp-count');
    if ('IntersectionObserver' in window) {
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
                if (!e.isIntersecting) return;
                e.target.classList.add('is-in');
                $$('.lp-count', e.target).forEach(countUp);
                io.unobserve(e.target);
            });
        }, { threshold: 0.18 });
        revealTargets.forEach(function (el) { io.observe(el); });
    } else {
        revealTargets.forEach(function (el) { el.classList.add('is-in'); });
        counters.forEach(countUp);
    }

    // 히어로 목업은 첫 화면에 있으니 로드 직후에 막대와 숫자를 채운다
    window.addEventListener('load', function () {
        setTimeout(function () {
            document.body.classList.add('is-loaded');
            $$('.lp-mock .lp-count').forEach(countUp);
        }, 900);
    });

    // ------------------------------------------------------------ 상단 네비 + 진행 바 + 여정 선
    var nav = $('#lpNav');
    var progress = $('#lpProgress');
    var journey = $('#lpJourney');
    var journeyFill = $('#lpJourneyFill');
    var journeyLine = journeyFill.parentNode;
    var steps = $$('.lp-step');
    var navLinks = $$('.lp-links a');
    var sections = navLinks.map(function (a) { return $(a.getAttribute('href')); });
    var ticking = false;

    function onScroll() {
        ticking = false;
        var y = window.scrollY;
        var max = root.scrollHeight - window.innerHeight;
        nav.classList.toggle('is-scrolled', y > 30);
        progress.style.transform = 'scaleX(' + (max > 0 ? y / max : 0) + ')';

        // 여정 선: 화면 가운데 높이가 선의 어디쯤인지를 비율로 채운다
        var rect = journeyLine.getBoundingClientRect();
        var ratio = (window.innerHeight * 0.6 - rect.top) / rect.height;
        ratio = Math.max(0, Math.min(1, ratio));
        journeyFill.style.height = (ratio * 100) + '%';
        var reachedY = rect.top + rect.height * ratio;
        steps.forEach(function (s) {
            s.classList.toggle('is-reached', s.getBoundingClientRect().top + 28 <= reachedY);
        });

        // 지금 보고 있는 섹션의 메뉴에 밑줄
        var current = -1;
        sections.forEach(function (sec, i) {
            if (sec && sec.getBoundingClientRect().top <= window.innerHeight * 0.4) current = i;
        });
        navLinks.forEach(function (a, i) { a.classList.toggle('is-current', i === current); });
    }
    // 선은 1번 원의 중심에서 시작해 마지막 원의 중심에서 끝난다 (5번 뒤로 꼬리가 남지 않게).
    // 마지막 카드의 높이가 글 길이에 따라 달라져서 CSS 고정값 대신 여기서 잰다.
    function layoutJourney() {
        var last = steps[steps.length - 1];
        var half = $('.lp-step-no', last).offsetHeight / 2;
        journeyLine.style.top = (steps[0].offsetTop + half) + 'px';
        journeyLine.style.bottom = (journey.offsetHeight - last.offsetTop - half) + 'px';
        onScroll();
    }
    window.addEventListener('scroll', function () {
        if (!ticking) { ticking = true; requestAnimationFrame(onScroll); }
    }, { passive: true });
    window.addEventListener('resize', layoutJourney);
    window.addEventListener('load', layoutJourney);
    layoutJourney();

    // ------------------------------------------------------------ 히어로 타이핑
    var typeEl = $('#lpType');
    if (typeEl && !reduced) {
        var words = typeEl.dataset.words.split(',');
        var wi = 0, ci = words[0].length, deleting = true;
        var type = function () {
            var word = words[wi];
            ci += deleting ? -1 : 1;
            typeEl.textContent = word.slice(0, ci);
            var delay = deleting ? 55 : 110;
            if (!deleting && ci === word.length) { deleting = true; delay = 1900; }
            else if (deleting && ci === 0) { deleting = false; wi = (wi + 1) % words.length; delay = 320; }
            setTimeout(type, delay);
        };
        // 첫 단어는 이미 화면에 찍혀 있으니 잠시 보여준 뒤 지우기부터 시작한다
        setTimeout(type, 2200);
    }

    // ------------------------------------------------------------ 목업 기울이기 (마우스)
    var tilt = $('#lpTilt');
    if (tilt && !reduced && window.matchMedia('(hover: hover)').matches) {
        var mock = $('.lp-mock', tilt);
        tilt.addEventListener('mousemove', function (e) {
            var r = tilt.getBoundingClientRect();
            var px = (e.clientX - r.left) / r.width - 0.5;
            var py = (e.clientY - r.top) / r.height - 0.5;
            mock.style.setProperty('--ry', (px * 7) + 'deg');
            mock.style.setProperty('--rx', (4 - py * 6) + 'deg');
        });
        tilt.addEventListener('mouseleave', function () {
            mock.style.removeProperty('--ry');
            mock.style.removeProperty('--rx');
        });
    }

    // ------------------------------------------------------------ 기능 탭 자동 전환
    var TAB_MS = 6000; // landing.css의 lp-fill 애니메이션 길이와 같아야 한다
    var tabsRoot = $('#lpTabs');
    if (tabsRoot) {
        var tabs = $$('.lp-tab', tabsRoot);
        var panels = $$('.lp-panel', tabsRoot);
        var active = 0, timer = null;

        function show(i) {
            active = i;
            tabs.forEach(function (t, k) {
                t.classList.remove('is-active');
                t.setAttribute('aria-selected', k === i ? 'true' : 'false');
            });
            panels.forEach(function (p, k) { p.classList.toggle('is-active', k === i); });
            void tabs[i].offsetWidth; // 리플로우를 강제해 진행 바 애니메이션을 처음부터 다시 돌린다
            tabs[i].classList.add('is-active');
        }
        function schedule() {
            clearTimeout(timer);
            if (reduced) return;
            timer = setTimeout(function () { show((active + 1) % tabs.length); schedule(); }, TAB_MS);
        }
        tabs.forEach(function (t, i) {
            t.addEventListener('click', function () { show(i); schedule(); });
        });
        // 읽는 동안에는 넘어가지 않게 멈추고, 마우스가 나가면 처음부터 다시 센다.
        // 터치 기기는 mouseleave가 오지 않아 멈춘 채로 남으므로 마우스가 있는 환경에서만 건다.
        if (window.matchMedia('(hover: hover)').matches) {
            tabsRoot.addEventListener('mouseenter', function () {
                tabsRoot.classList.add('is-paused');
                clearTimeout(timer);
            });
            tabsRoot.addEventListener('mouseleave', function () {
                tabsRoot.classList.remove('is-paused');
                show(active);
                schedule();
            });
        }
        schedule();
    }

    // ------------------------------------------------------------ 예시 화면 내려 보기
    // 예시 화면의 내용이 창보다 길면 넘치는 만큼을 --pan에 넣는다 (landing.css의 lp-pan이 그만큼 움직인다).
    function measureScreens() {
        $$('.lp-screen-view').forEach(function (view) {
            var head = $('.lp-m-head', view), body = $('.lp-m-body', view);
            var over = head.offsetHeight + body.offsetHeight - view.clientHeight;
            body.style.setProperty('--pan', (over > 0 ? -over : 0) + 'px');
        });
    }
    window.addEventListener('load', measureScreens);
    window.addEventListener('resize', measureScreens);
    measureScreens();

    // ------------------------------------------------------------ 공유 링크 토큰 연출
    // 화면 연출용 가짜 문자열이다 — 실제 토큰은 서버에서 SecureRandom으로 만든다.
    var tokenEl = $('#lpToken');
    if (tokenEl && !reduced) {
        var chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
        setInterval(function () {
            var s = '';
            for (var i = 0; i < 12; i++) s += chars.charAt(Math.floor(Math.random() * chars.length));
            tokenEl.textContent = s;
        }, 2200);
    }

    // ------------------------------------------------------------ 히어로 별자리 캔버스
    var canvas = $('#lpStars');
    if (canvas && canvas.getContext && !reduced) {
        var ctx = canvas.getContext('2d');
        var stars = [], w = 0, h = 0, visible = true;
        var LINK = 120;

        function resize() {
            var dpr = Math.min(window.devicePixelRatio || 1, 2);
            w = canvas.clientWidth; h = canvas.clientHeight;
            canvas.width = w * dpr; canvas.height = h * dpr;
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            var count = Math.min(90, Math.floor(w * h / 14000));
            stars = [];
            for (var i = 0; i < count; i++) {
                stars.push({
                    x: Math.random() * w, y: Math.random() * h,
                    vx: (Math.random() - 0.5) * 0.25, vy: (Math.random() - 0.5) * 0.25,
                    r: Math.random() * 1.4 + 0.5
                });
            }
        }
        function draw() {
            if (visible) {
                ctx.clearRect(0, 0, w, h);
                for (var i = 0; i < stars.length; i++) {
                    var a = stars[i];
                    a.x += a.vx; a.y += a.vy;
                    if (a.x < 0 || a.x > w) a.vx *= -1;
                    if (a.y < 0 || a.y > h) a.vy *= -1;
                    ctx.fillStyle = 'rgba(231, 200, 119, 0.8)';
                    ctx.beginPath(); ctx.arc(a.x, a.y, a.r, 0, Math.PI * 2); ctx.fill();
                    for (var j = i + 1; j < stars.length; j++) {
                        var b = stars[j];
                        var dx = a.x - b.x, dy = a.y - b.y;
                        var d = Math.sqrt(dx * dx + dy * dy);
                        if (d < LINK) {
                            ctx.strokeStyle = 'rgba(201, 162, 75, ' + (0.22 * (1 - d / LINK)) + ')';
                            ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
                        }
                    }
                }
            }
            requestAnimationFrame(draw);
        }
        // 히어로가 화면 밖이면 그리지 않는다
        if ('IntersectionObserver' in window) {
            new IntersectionObserver(function (entries) {
                visible = entries[0].isIntersecting;
            }).observe(canvas);
        }
        window.addEventListener('resize', resize);
        resize();
        draw();
    }
})();
