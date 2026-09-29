# Docker로 MySQL 띄우기 (다른 컴퓨터에서 이어서 작업하기)

## 왜

지금까지 각자 로컬에 MySQL을 직접 설치하고 `sql/01~04` 스크립트를 순서대로 적용해왔다
(2026-09-22 개발일지에 이 과정에서 겪은 트러블슈팅 기록이 있다). 이 과정을 Docker 컨테이너
하나로 대신하면, 다른 컴퓨터(집 PC 등)에서도 `docker compose up -d` 한 줄로 완전히 같은
스키마·시드 데이터를 가진 DB를 바로 받을 수 있다.

## 사용법

```bash
# 1) 저장소 클론(또는 pull) 후 프로젝트 루트에서
docker compose up -d

# 2) 잘 떴는지 확인 (healthy가 될 때까지 몇 초 걸림)
docker compose ps

# 3) 끝났으면 (컨테이너만 멈춤, 데이터는 유지)
docker compose down

# 데이터까지 완전히 초기화하고 싶을 때만 (sql/01~04가 처음부터 다시 실행됨)
docker compose down -v
```

`.env`(또는 `.env.example`을 복사한 값)는 그대로 쓰면 된다 — 컨테이너가 호스트의 3306 포트를
그대로 쓰기 때문에 `DB_URL`을 바꿀 필요가 없다.

```
DB_URL=jdbc:mysql://localhost:3306/spec_odyssey_test?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USER=root
DB_PASSWORD=1234
```

## 주의 — 포트 3306 충돌

이 컴퓨터(또는 집 PC)에 이미 로컬 MySQL이 설치되어 그대로 실행 중이면, 같은 3306 포트를
컨테이너와 동시에 쓸 수 없어 `docker compose up`이 실패한다. 둘 중 하나로 해결한다.

- 로컬 MySQL 서비스를 멈추고 컨테이너만 쓴다 (권장 — 이후로는 DB를 컨테이너로 통일).
- 아니면 `docker-compose.yml`의 `ports`를 `"3307:3306"` 같은 다른 호스트 포트로 바꾸고,
  `.env`의 `DB_URL`도 `localhost:3307`로 맞춰 바꾼다.

## 지금까지 쌓인 실제 작업 데이터(데모 계정 등)를 그대로 가져가고 싶다면

이 compose 파일은 `sql/01~04`(스키마 + 기본 시드)만 자동 적용한다 — 지금 로컬 DB에 있는
`demo_roadmap` 등 테스트하면서 쌓인 실제 값(점수·완료 상태·업로드 파일 기록 등)까지 그대로
옮기고 싶다면 별도로 덤프를 떠서 가져가야 한다.

### DB 스냅샷 내보내기 (이 컴퓨터에서)

```bash
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe" -u root -p1234 \
  --default-character-set=utf8mb4 --routines --triggers --single-transaction \
  spec_odyssey_test > db-backup/spec_odyssey_test_YYYYMMDD.sql
```

`db-backup/`는 `.gitignore`에 걸려 있어 git으로는 안 옮겨진다 — 이 파일 하나만 USB나
클라우드 드라이브(구글 드라이브 등)로 직접 옮겨야 한다. 다른 팀원의 테스트 데이터나 개인
계정 정보가 섞여 있을 수 있으니 팀 저장소나 공개된 곳에는 올리지 않는다.

### 집 PC에서 가져오기

```bash
# 1) docker compose up -d로 컨테이너를 띄운 다음(빈 상태로 초기화됨)
# 2) 옮겨온 덤프 파일을 그 위에 덮어씌운다
docker exec -i spec-odyssey-mysql mysql -uroot -p1234 spec_odyssey_test < spec_odyssey_test_YYYYMMDD.sql
```

이러면 지금 이 컴퓨터에서 보던 상태(등급·점수·완료한 로드맵 단계 등) 그대로 집 PC에서
이어서 확인할 수 있다.

### IntelliJ 쪽은 별도로

`.idea/`는 `.gitignore`에 있어 git으로 안 따라간다 — 집 PC에서는 Maven 프로젝트 재import,
Tomcat 실행 설정(Run 구성)을 처음 한 번 다시 잡아야 한다(2026-09-22 개발일지에 이 과정
기록 있음). DB 연결은 위 docker-compose로 대체되니 그 부분은 다시 겪지 않아도 된다.
