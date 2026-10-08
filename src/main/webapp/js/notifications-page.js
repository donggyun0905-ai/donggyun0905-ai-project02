// 알림 목록 화면(/notifications)의 "모두 읽음" — 화면 전체를 다시 불러오지 않고 목록 부분만 고친다.
// 서버는 fetch 요청이면 204만 돌려준다(NotificationServlet). 실패하면 폼을 그대로 보내 예전처럼 새로고침한다.
document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form.matches('form.noti-page-readall') || form.dataset.fallback) {
        return;
    }
    event.preventDefault();
    var button = form.querySelector('button');
    if (button) {
        button.disabled = true;
    }
    // form.action은 안의 <input name="action">에 가려져 입력칸이 나온다 — 속성값으로 읽는다
    fetch(form.getAttribute('action'), {
        method: 'POST',
        headers: {'X-Requested-With': 'fetch'},
        body: new URLSearchParams(new FormData(form)),
        credentials: 'same-origin'
    }).then(function (response) {
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        // 안 읽은 알림을 회색(읽음)으로, 버튼은 더 누를 게 없으니 없앤다
        document.querySelectorAll('.noti-page-list .noti-item.unread').forEach(function (item) {
            item.classList.remove('unread');
            item.classList.add('read');
        });
        form.remove();
        // 헤더 종의 빨간 숫자와 드롭다운도 같이 비운다 (notification-bell.js)
        var bell = document.querySelector('details.noti-bell');
        if (bell && typeof markAllReadInPlace === 'function') {
            markAllReadInPlace(bell);
        }
    }).catch(function () {
        form.dataset.fallback = '1';
        form.submit();
    });
});
