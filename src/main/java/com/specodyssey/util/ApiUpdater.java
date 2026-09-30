package com.specodyssey.util;

import com.specodyssey.service.work24.Work24CollectService;
import com.specodyssey.service.work24.Work24CollectService.Outcome;
import com.specodyssey.service.work24.Work24CollectService.Target;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 고용24 Open API 데이터를 수동으로 DB에 넣거나 갱신하는 실행기 (Work24Scheduler의 수동 버전).
 * 오늘 이미 받았어도 다시 받는다. DB 접속 정보와 API 키는 src/main/resources/.env에서 읽는다.
 *
 * 사용법:
 *   ApiUpdater                  세 API 모두 갱신
 *   ApiUpdater duty major       골라서 갱신 (duty = 직무정보, major = 학과정보, job = 직업정보)
 *   ApiUpdater --dry-run        호출·파싱만 하고 DB에는 쓰지 않는다 — 무엇이 들어갈지 미리 확인
 *
 * 실행:
 *   IntelliJ: 이 클래스의 main 옆 ▶ 버튼 (인자는 Run Configuration → Program arguments)
 *   Maven:    mvnw compile exec:java -Dexec.mainClass=com.specodyssey.util.ApiUpdater -Dexec.args="--dry-run"
 */
public final class ApiUpdater {

    private ApiUpdater() {
    }

    public static void main(String[] args) {
        boolean dryRun = false;
        List<Target> targets = new ArrayList<>();
        for (String arg : args) {
            switch (arg.toLowerCase()) {
                case "--dry-run" -> dryRun = true;
                case "all" -> targets.addAll(Arrays.asList(Target.values()));
                case "duty" -> targets.add(Target.DUTY);
                case "major" -> targets.add(Target.MAJOR);
                case "job" -> targets.add(Target.JOB);
                default -> {
                    System.err.println("알 수 없는 인자: " + arg + "  (사용: [duty|major|job|all ...] [--dry-run])");
                    System.exit(2);
                    return;
                }
            }
        }
        if (targets.isEmpty()) {
            targets.addAll(Arrays.asList(Target.values()));
        }
        List<Target> distinct = targets.stream().distinct().toList();

        System.out.println("고용24 갱신 시작: " + distinct + (dryRun ? " (dry-run — DB에 쓰지 않음)" : ""));
        List<Outcome> outcomes = new Work24CollectService().collect(distinct, dryRun);
        System.out.println();
        outcomes.forEach(System.out::println);

        boolean allOk = outcomes.stream().allMatch(Outcome::success);
        System.exit(allOk ? 0 : 1);
    }
}
