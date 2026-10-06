// 헤더 알림 드롭다운(<details class="noti-bell">)
// - 바깥을 누르거나 Esc를 누르면 닫는다. 열고 닫기 자체는 <details>가 JS 없이 처리한다.
// - "모두 읽음"은 화면을 옮기지 않고 그 자리에서 읽음 처리만 한다(fetch → 204). 실패하면 폼을 그대로 보내
//   서버가 보던 화면으로 되돌려 준다(NotificationServlet).
document.addEventListener('click', function (event) {
    document.querySelectorAll('details.noti-bell[open]').forEach(function (bell) {
        if (!bell.contains(event.target)) {
            bell.removeAttribute('open');
        }
    });
});
document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
        document.querySelectorAll('details.noti-bell[open]').forEach(function (bell) {
            bell.removeAttribute('open');
        });
    }
});

document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form.matches('details.noti-bell form.noti-readall') || form.dataset.fallback) {
        return;
    }
    event.preventDefault();
    fetch(form.action, {
        method: 'POST',
        headers: {'X-Requested-With': 'fetch'},
        body: new URLSearchParams(new FormData(form)),
        credentials: 'same-origin'
    }).then(function (response) {
        if (!response.ok) {
            throw new Error('HTTP ' + response.status);
        }
        markAllReadInPlace(form.closest('details.noti-bell'));
    }).catch(function () {
        form.dataset.fallback = '1';
        form.submit();
    });
});

// 빨간 숫자를 지우고, 드롭다운 목록을 "새 알림이 없습니다"로 바꾼다 (드롭다운에는 안 읽은 알림만 나오므로)
function markAllReadInPlace(bell) {
    var count = bell.querySelector('.noti-count');
    if (count) {
        count.remove();
    }
    bell.querySelector('summary').setAttribute('aria-label', '알림 0개');
    var list = bell.querySelector('.noti-list');
    if (list) {
        var empty = document.createElement('p');
        empty.className = 'noti-empty';
        empty.textContent = '새 알림이 없습니다.';
        list.replaceWith(empty);
    }
    var readAll = bell.querySelector('form.noti-readall');
    if (readAll) {
        readAll.remove();
    }
}
