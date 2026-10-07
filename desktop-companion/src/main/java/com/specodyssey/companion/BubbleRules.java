package com.specodyssey.companion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 언제 무슨 말을 할지 — 화면과 떨어진 순수 계산이라 시각(now)을 넘겨 받아 테스트한다.
 *
 * 규칙 (docs/desktop-companion-plan.md 4절)
 *  - 한 번에 하나만 말한다. 말풍선은 사용자가 상호작용할 때까지 떠 있다.
 *  - 상호작용 = ✕ / 말풍선을 눌러 화면 열기 / 말한 일을 실제로 함(서버 목록에서 그 key가 사라짐) → 말풍선을 끈다.
 *  - 꺼진 시점부터 interval(기본 30분)이 지나야 다음 말을 한다. 한 번 한 말(key)은 다시 하지 않는다.
 *  - 조용히 시간에는 먼저 말하지 않는다. 캐릭터를 직접 누르면(지금 할 일) 규칙과 상관없이 바로 말한다.
 *  - 인사·등급 상승 칭찬처럼 urgent인 것은 대기 시간과 상관없이 바로 (조용히 시간 제외).
 */
public class BubbleRules {

    private final Set<String> spoken = new HashSet<>();
    private Message current;
    private long lastClosedAt;
    private long intervalMillis;

    public BubbleRules(long intervalMillis, Set<String> alreadySpoken) {
        this.intervalMillis = intervalMillis;
        if (alreadySpoken != null) {
            spoken.addAll(alreadySpoken);
        }
    }

    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    public Message current() {
        return current;
    }

    public Set<String> spokenKeys() {
        return Set.copyOf(spoken);
    }

    /**
     * 지금 새로 띄울 말 — 없으면 null.
     * @param available 지금 할 수 있는 말 (우선순위 순)
     * @param urgent    대기 시간을 건너뛸 말 (인사·칭찬), 없으면 빈 목록
     */
    public Message next(List<Message> available, List<Message> urgent, long now, long quietUntil) {
        if (current != null || now < quietUntil) {
            return null;
        }
        for (Message m : urgent) {
            if (!spoken.contains(m.key())) {
                return m;
            }
        }
        if (lastClosedAt > 0 && now - lastClosedAt < intervalMillis) {
            return null;
        }
        for (Message m : available) {
            if (!spoken.contains(m.key())) {
                return m;
            }
        }
        return null;
    }

    /** 지금 떠 있는 서버 말이 목록에서 사라졌나 — 사용자가 그 일을 했다는 뜻이라 말풍선을 끈다 */
    public boolean shouldAutoClose(List<Message> available) {
        if (current == null || current.local()) {
            return false;
        }
        for (Message m : available) {
            if (m.key().equals(current.key())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 떠 있는 말의 숫자만 바뀐 새 말 (예: mission-left:날짜:3 → :2) — 있으면 말풍선을 닫지 않고 내용만 바꾼다.
     * key에 ':'가 두 개 이상이면 마지막 ':' 앞까지를 같은 이야기로 본다.
     */
    public Message successor(List<Message> available) {
        if (current == null || current.local()) {
            return null;
        }
        String family = family(current.key());
        if (family == null) {
            return null;
        }
        for (Message m : available) {
            if (family.equals(family(m.key())) && !m.key().equals(current.key())) {
                return m;
            }
        }
        return null;
    }

    static String family(String key) {
        int last = key.lastIndexOf(':');
        return last > 0 && key.indexOf(':') < last ? key.substring(0, last) : null;
    }

    /** 대기 중인 다른 말 개수 — 말풍선 구석 "+N" */
    public int waitingCount(List<Message> available) {
        int n = 0;
        for (Message m : available) {
            if (!spoken.contains(m.key()) && (current == null || !m.key().equals(current.key()))) {
                n++;
            }
        }
        return n;
    }

    public void shown(Message m) {
        current = m;
        spoken.add(m.key());
    }

    public void closed(long now) {
        current = null;
        lastClosedAt = now;
    }

    /**
     * 한 번 한 말 목록을 지금 있는 말로 줄인다 — 사라졌다가 같은 상황이 다시 오면 다시 말할 수 있게,
     * 그리고 저장 파일이 끝없이 커지지 않게.
     */
    public void forgetGone(List<Message> available, List<Message> keepAlso) {
        Set<String> alive = new HashSet<>();
        for (Message m : available) {
            alive.add(m.key());
        }
        for (Message m : keepAlso) {
            alive.add(m.key());
        }
        if (current != null) {
            alive.add(current.key());
        }
        spoken.retainAll(alive);
    }

    /** 캐릭터를 직접 눌렀을 때 — 지금 가장 급한 것 (이미 한 말이어도) */
    public static Message mostUrgent(List<Message> available) {
        return available.isEmpty() ? null : available.get(0);
    }

    static List<Message> merge(List<Message> server, List<Message> local) {
        List<Message> all = new ArrayList<>(server);
        all.addAll(local);
        return all;
    }
}
