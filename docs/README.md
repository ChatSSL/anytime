# docs

- [erd.md](erd.md) : ERD (GitHub에서 그림으로 보임)와 설계 메모
- `api.md` : API 명세

## DB 스키마와 시드 데이터

3단계부터 DDL은 백엔드 안으로 옮겼다. 앱이 이 파일로 테이블을 만든다.

- [backend/src/main/resources/db/schema.sql](../backend/src/main/resources/db/schema.sql) : 19개 테이블 DDL (MySQL 8.4)
- [backend/src/main/resources/db/data.sql](../backend/src/main/resources/db/data.sql) : 시연용 시드 (프론트 data.ts를 옮긴 것)

로컬에서 `bootRun`하면 매번 테이블을 지우고 다시 만든 뒤 시드를 넣는다. 데이터를 남기고 싶으면 `SQL_INIT_MODE=never`로 실행한다.

```powershell
$env:SQL_INIT_MODE="never"; .\gradlew.bat bootRun
```

테이블을 바꿀 때는 schema.sql을 고치고, 같은 PR에서 엔티티(`domain/`)와 이 폴더의 erd.md도 같이 고친다. JPA는 테이블을 만들지 않는다(`ddl-auto: none`).

## 시드 데이터 id

| DB id | 사용자 | 비고 |
| --- | --- | --- |
| 1 | 데모유저 | 로그인 붙기 전까지 '현재 사용자'로 쓴다 |
| 2~9 | 민트초코, 라떼한잔, 코딩하는곰, 새벽러닝, 영화광하루, 카탄마스터, 어학당, 연필한자루 | 프론트 u1~u8 |
| 10 | 운영팀 | 프론트 staff |

모임은 프론트 r1, r2, r3, r4, r5, r6, r9, r11, r12, r7 순서로 1~10이다. 데모유저가 들어가 있는 모임(월요일 카페공부)은 10번이다. 모임 날짜(`meet_date`)는 시드를 넣는 날 기준으로 가장 가까운 그 요일이다.
