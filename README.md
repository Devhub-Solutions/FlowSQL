# FlowSQL

[![CI](https://github.com/Devhub-Solutions/flowsql/actions/workflows/ci.yml/badge.svg)](https://github.com/Devhub-Solutions/flowsql/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/vn.io.devhub/flowsql.svg)](https://central.sonatype.com/artifact/vn.io.devhub/flowsql)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**FlowSQL** là thư viện JDBC gọn nhẹ của [Devhub Solutions](https://devhub.io.vn) — viết SQL thuần,
không ORM, không boilerplate:

- Connection pool **HikariCP** cấu hình sẵn cho MySQL, MariaDB, PostgreSQL, H2
- **Named parameter** `:name` — an toàn với `::cast` của PostgreSQL, chuỗi `'10:30'` và comment
- **Transaction theo thread** (`begin/commit/rollback` hoặc `inTransaction(...)`)
- **Nhiều database** cùng lúc qua `DataSetRegistry`
- **Spring Boot auto-config** (tùy chọn) — khai báo trong `application.yml` là dùng
- MySQL/MariaDB tự ép session time zone = UTC, tránh lệch giờ giữa `NOW()` và Java

Yêu cầu Java 17+.

## Cài đặt

```xml
<dependency>
    <groupId>vn.io.devhub</groupId>
    <artifactId>flowsql</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- và JDBC driver bạn dùng, ví dụ -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
</dependency>
```

Gradle: `implementation("vn.io.devhub:flowsql:1.0.0")`

## Dùng nhanh

```java
import static vn.io.devhub.flowsql.DataSet.params;

DataSet db = DataSet.mysql()
        .host("localhost").database("shop")
        .user("app").pass(System.getenv("DB_PASS"));

// SELECT -> List<Map<String,Object>> (key cột viết thường)
var users = db.query("SELECT id, name FROM users WHERE status = :st", params("st", "ACTIVE"));

// 1 dòng / 1 giá trị
var user  = db.queryOne("SELECT * FROM users WHERE id = :id", params("id", 42));
long total = ((Number) db.queryScalar("SELECT COUNT(*) FROM users", Map.of())).longValue();

// INSERT trả về khóa tự sinh
Object id = db.insert("INSERT INTO users(name) VALUES(:n)", params("n", "Thành"));

// Batch
db.batch("INSERT INTO tags(name) VALUES(:n)", List.of(params("n", "a"), params("n", "b")));

// Transaction: lỗi -> tự rollback
db.inTransaction(() -> {
    db.execute("UPDATE acc SET bal = bal - :v WHERE id = :id", params("v", 100, "id", 1));
    db.execute("UPDATE acc SET bal = bal + :v WHERE id = :id", params("v", 100, "id", 2));
});
```

Đọc giá trị không phân biệt hoa thường: `Rows.str(row, "NAME")`, `Rows.getLong(row, "id", 0)`,
`Rows.getBool(row, "active", false)`.

API "strict" (`query`, `execute`, `insert`, `batch`) ném `FlowSqlException` khi lỗi.

### API kiểu devhub (tương thích code cũ)

```java
db.setField("id", 42);
List<Map<String, Object>> rows = db.searchAndRetrieve("SELECT * FROM users WHERE id = :id");
if (rows == null) log.error(db.getLastError());

DbUtil.safeQuery(db, "SELECT * FROM users WHERE name = :n", "n", "Thành");
```

## Spring Boot

Chỉ cần thêm dependency, rồi khai báo:

```yaml
flowsql:
  datasources:
    main:
      type: mysql            # mysql | mariadb | postgresql | h2
      host: db.example.com
      database: app
      username: app
      password: ${DB_PASS}
      max-pool-size: 20
      slow-query-ms: 500     # log WARN câu lệnh chậm
    report:
      type: postgresql
      jdbc-url: jdbc:postgresql://report:5432/bi
      username: bi
      password: ${BI_PASS}
```

```java
@Service
class UserService {
    private final DataSet db;
    UserService(DataSetRegistry registry) { this.db = registry.get("main"); }
}
```

Đã có `DataSource` của Spring? Bọc lại: `new DataSet(dataSource)` (FlowSQL sẽ không đóng pool đó).

## Phát hành (maintainer)

Release chạy bằng GitHub Actions (`.github/workflows/release.yml`):

1. Cập nhật `<version>` trong `pom.xml`, commit.
2. `git tag v1.0.0 && git push origin v1.0.0`
3. Workflow build, ký GPG và upload lên Central Portal ở trạng thái *validated*;
   vào <https://central.sonatype.com/publishing/deployments> bấm **Publish**.

Secrets cần có: `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.

## License

[Apache License 2.0](LICENSE) © Devhub Solutions
