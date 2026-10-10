---
name: flowsql
description: Write Java database code with FlowSQL (vn.io.devhub:flowsql) - plain SQL with :named params over HikariCP, thread-bound transactions, multi-datasource registry, Spring Boot auto-config. Use whenever code imports vn.io.devhub.flowsql, uses DataSet/DataSetRegistry/DbUtil/Rows, or the user asks for JDBC/SQL access in a Devhub Solutions Java project.
---

# FlowSQL — coding guide for agents

FlowSQL is a small JDBC toolkit from Devhub Solutions. You write **plain SQL**; there is no ORM,
no entity mapping, no query builder. Rows come back as `List<Map<String, Object>>`.

- Maven: `vn.io.devhub:flowsql:1.0.0` (Java 17+). Package: `vn.io.devhub.flowsql`.
- Docs: https://devhub-solutions.github.io/FlowSQL/ — Javadoc: https://devhub-solutions.github.io/FlowSQL/apidocs/
- Source: https://github.com/Devhub-Solutions/FlowSQL

## 1. Dependency

FlowSQL does NOT bundle a JDBC driver. Always add the driver for the target database.

```xml
<dependency>
  <groupId>vn.io.devhub</groupId>
  <artifactId>flowsql</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency> <!-- pick one: mysql-connector-j / mariadb-java-client / postgresql / h2 -->
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>8.4.0</version>
</dependency>
```

## 2. Creating a DataSet (one DataSet = one connection pool)

```java
import vn.io.devhub.flowsql.*;
import static vn.io.devhub.flowsql.DataSet.params;

DataSet db = DataSet.mysql()            // also: mariadb(), postgres(), h2("name")
        .host("db.local").port(3306)    // port optional: default per DB type
        .database("shop")
        .user("app").pass(System.getenv("DB_PASS"))
        .maxPoolSize(20).minIdle(5)     // optional
        .slowQueryMs(500)               // optional: WARN log for slow statements
        .leakDetectionMs(0);            // optional: 0 = off
// or: new DataSet(DBType.POSTGRESQL).jdbcUrl("jdbc:postgresql://...")
// or wrap an existing pool: new DataSet(springDataSource)  (FlowSQL will not close it)
```

Rules:
- Create ONE DataSet per database and reuse it (it is thread-safe). Never create one per request.
- The pool is created lazily on first query. Call `db.close()` on shutdown (or let Spring do it).
- MySQL/MariaDB sessions are forced to UTC (`SET time_zone = '+00:00'`). Store/compare times in UTC.
- H2: `DataSet.h2("test")` = in-memory, `DataSet.h2("file:./data/app")` = file, MySQL compatibility mode.

## 3. Queries — prefer the strict API

Strict methods throw `FlowSqlException` (unchecked) on any error. Use them in new code.

| Method | Returns | Use for |
|---|---|---|
| `query(sql)` / `query(sql, params)` | `List<Map<String,Object>>` (never null) | SELECT many rows |
| `queryOne(sql, params)` | `Optional<Map<String,Object>>` | SELECT one row |
| `queryScalar(sql, params)` | `Object` (first column of first row, or null) | COUNT, MAX, EXISTS |
| `execute(sql)` / `execute(sql, params)` | `int` affected rows | UPDATE, DELETE, DDL, INSERT without key |
| `insert(sql, params)` | `Object` generated key (or null) | INSERT with auto-increment id |
| `batch(sql, List<Map>)` | `int[]` | same statement, many param sets |

```java
List<Map<String, Object>> rows = db.query(
    "SELECT id, name, created_at FROM users WHERE status = :st AND age >= :age",
    params("st", "ACTIVE", "age", 18));

Optional<Map<String, Object>> u = db.queryOne("SELECT * FROM users WHERE id = :id", params("id", id));
long n = ((Number) db.queryScalar("SELECT COUNT(*) FROM users", Map.of())).longValue();
Object newId = db.insert("INSERT INTO users(name, email) VALUES(:name, :email)",
                         params("name", "Thanh", "email", "t@x.vn"));
int changed = db.execute("UPDATE users SET status = :st WHERE id = :id", params("st", "LOCKED", "id", 5));
db.batch("INSERT INTO tags(name) VALUES(:n)", List.of(params("n", "a"), params("n", "b")));
```

`params(k1, v1, k2, v2, ...)` builds a mutable `HashMap` and allows null values. `Map.of(...)`
works too but rejects nulls — use `params` when a value may be null.

## 4. Named parameters

- Syntax: `:name` (letters, digits, `_`; must start with a letter or `_`). Same name may repeat; it is bound each time.
- Every `:name` in the SQL MUST have a key in the map (value may be null) or `FlowSqlException` is thrown.
- Not treated as parameters: PostgreSQL casts `col::text`, text inside `'...'`, `"..."`, `` `...` ``, `-- comments`, `/* comments */`.
- Values are bound with `PreparedStatement.setObject` — `java.time.LocalDate/LocalDateTime/Instant`, `BigDecimal`, `UUID` (Postgres), `Boolean`, `byte[]` all work.
- Parameters are VALUES only. Never put table/column names or `ORDER BY` direction in a parameter; whitelist them in Java and concatenate.
- **Never concatenate user input into SQL.** Always use `:name`.
- `IN (...)` lists: there is no list expansion. Generate `:p0, :p1, ...` yourself:

```java
List<Long> ids = List.of(3L, 7L, 9L);
Map<String, Object> p = new HashMap<>();
StringJoiner in = new StringJoiner(", ");
for (int i = 0; i < ids.size(); i++) { in.add(":id" + i); p.put("id" + i, ids.get(i)); }
List<Map<String, Object>> rows = db.query("SELECT * FROM users WHERE id IN (" + in + ")", p);
```

## 5. Reading rows

Map keys are the column **labels in lowercase** (`SELECT user_name AS userName` → key `username`).
Use `Rows` helpers — they are case-insensitive and null-safe:

```java
Map<String, Object> r = rows.get(0);
String name   = Rows.str(r, "name");               // null if missing
String nick   = Rows.str(r, "nick", "-");          // default
long id       = Rows.getLong(r, "ID", 0L);
int age       = Rows.getInt(r, "age", 0);
boolean on    = Rows.getBool(r, "active", false);  // true/1/Y
BigDecimal amt = Rows.getDecimal(r, "amount", BigDecimal.ZERO);
Object raw    = Rows.get(r, "created_at");         // driver type, e.g. LocalDateTime / Timestamp
```

Map results to records/DTOs in your own code:

```java
record User(long id, String name) {}
List<User> users = db.query("SELECT id, name FROM users").stream()
        .map(r -> new User(Rows.getLong(r, "id", 0), Rows.str(r, "name")))
        .toList();
```

## 6. Transactions

Transactions are bound to the **current thread**. Every FlowSQL call on that thread (same DataSet)
joins the open transaction automatically.

```java
db.inTransaction(() -> {                       // commit on success, rollback on any RuntimeException/Error
    db.execute("UPDATE acc SET bal = bal - :v WHERE id = :a", params("v", 100, "a", 1));
    db.execute("UPDATE acc SET bal = bal + :v WHERE id = :b", params("v", 100, "b", 2));
});
Long orderId = db.inTransaction(() -> ((Number) db.insert("INSERT INTO orders(total) VALUES(:t)", params("t", 99))).longValue());
```

- Nested `inTransaction` joins the outer one (no savepoints).
- Manual form: `db.begin(); try { ...; db.commit(); } catch (RuntimeException e) { db.rollback(); throw e; }`. Always pair begin with commit/rollback in `finally` logic, or the connection leaks.
- Calling `begin()` twice on the same thread throws `IllegalStateException`.
- Do not start threads/async tasks inside a transaction and expect them to share it.
- A transaction on one DataSet does not cover another DataSet (no distributed tx).

## 7. Multiple databases — DataSetRegistry

```java
DataSetRegistry reg = new DataSetRegistry();
reg.register("main", DataSet.mysql().host("a").database("app").user("u").pass("p"));
reg.register("report", DataSet.postgres().host("b").database("bi").user("u").pass("p"));
DataSet main = reg.get("main");     // throws IllegalStateException if not registered
reg.closeAll();                     // or try-with-resources: DataSetRegistry is AutoCloseable
```

## 8. Spring Boot

Adding the dependency enables `FlowSqlAutoConfiguration` (Spring Boot 3). It creates one
`DataSetRegistry` bean from `flowsql.datasources.*`:

```yaml
flowsql:
  datasources:
    main:
      type: mysql          # mysql | mariadb | postgresql (or pg/postgres) | h2
      host: db.local
      port: 3306           # 0/omitted = default for type
      database: app
      username: app
      password: ${DB_PASS}
      ssl: false
      max-pool-size: 20
      min-idle: 5
      slow-query-ms: 500
      leak-detection-ms: 0
    report:
      type: postgresql
      jdbc-url: jdbc:postgresql://bi:5432/bi   # overrides host/port/database
      username: bi
      password: ${BI_PASS}
```

```java
@Repository
class UserRepo {
    private final DataSet db;
    UserRepo(DataSetRegistry registry) { this.db = registry.get("main"); }

    Optional<Map<String, Object>> findById(long id) {
        return db.queryOne("SELECT * FROM users WHERE id = :id", DataSet.params("id", id));
    }
}
```

Define your own `DataSetRegistry` bean to replace the auto-config. Spring's `@Transactional`
does NOT apply to FlowSQL calls (FlowSQL takes connections straight from its own pool) — use
`db.inTransaction(...)` for FlowSQL writes.

## 9. Legacy devhub API (only when editing existing code that already uses it)

```java
db.setField("id", 5);                                   // per-thread params, cleared after each call
List<Map<String, Object>> rows = db.searchAndRetrieve("SELECT * FROM t WHERE id = :id"); // null on error
int n = db.update("UPDATE t SET x = 1 WHERE id = :id");  // 0 on error
if (rows == null) log.error(db.getLastError());
DbUtil.safeQuery(db, sql, "k1", v1, "k2", v2);           // clear + set + searchAndRetrieve
DbUtil.getStr(row, "NAME");                              // same as Rows.str
```

These swallow exceptions; you MUST check `null` / `getLastError()`. Do not use them in new code.

## 10. Checklist before you finish

- [ ] JDBC driver dependency present for the database type.
- [ ] One shared DataSet per DB (field/bean), not created per call.
- [ ] All user input goes through `:params`; identifiers are whitelisted.
- [ ] Multi-statement writes wrapped in `inTransaction`.
- [ ] Row access via `Rows.*` with lowercase-insensitive keys; no `row.get("NAME")` on raw map.
- [ ] `FlowSqlException` handled or propagated at the boundary (controller/advice), not swallowed.
- [ ] Times stored/compared in UTC on MySQL/MariaDB.
- [ ] Tests can use `DataSet.h2("test_x")` (in-memory, MySQL mode) with the `com.h2database:h2` test dependency.
