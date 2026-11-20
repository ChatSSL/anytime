# docs

- [erd.md](erd.md) : ERD (GitHub에서 그림으로 보임)와 설계 메모
- [schema.sql](schema.sql) : 19개 테이블 DDL (MySQL 8.4)
- `api.md` : API 명세 (11/21까지 확정)

schema.sql을 직접 넣어 보려면 MySQL 8.4 Command Line Client에서:

```sql
USE anytime;
SOURCE <저장소 경로>/docs/schema.sql;   -- 예: D:/dev/anytime/docs/schema.sql (역슬래시 대신 /)
```

3단계에서 JPA 엔티티가 생기면 `ddl-auto: update`가 테이블을 만들어 주므로, 이 파일은 설계 기준과 검토용이다.
