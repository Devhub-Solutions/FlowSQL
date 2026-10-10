# Hướng dẫn phát hành FlowSQL lên Maven Central

Tài liệu cho maintainer. Toàn bộ việc build, ký và upload chạy trên GitHub Actions
(`.github/workflows/release.yml`); máy cá nhân chỉ cần tạo khóa và điền secrets **một lần**.

```
GitHub Actions ──build + test──► ký GPG (.asc) ──upload──► central.sonatype.com ──bấm Publish──► Maven Central
```

## 0. Cần chuẩn bị (một lần)

| Secret trên GitHub | Lấy ở đâu |
|---|---|
| `CENTRAL_USERNAME` | User token của Central Portal (bước 1) |
| `CENTRAL_PASSWORD` | User token của Central Portal (bước 1) |
| `GPG_PRIVATE_KEY` | Khóa ký tạo ở bước 2 |
| `GPG_PASSPHRASE` | Mật khẩu của khóa ký ở bước 2 |

Điều kiện tiên quyết: namespace **`vn.io.devhub`** phải ở trạng thái **Verified** trong cùng tài khoản
Central Portal sinh ra token (xem *central.sonatype.com → avatar → View Namespaces*).
Nếu chưa: *Add Namespace* `vn.io.devhub` → thêm bản ghi DNS **TXT** cho `devhub.io.vn` với mã verification Sonatype đưa → bấm *Verify*.

---

## 1. Tạo token Central Portal

1. Đăng nhập <https://central.sonatype.com> bằng **tài khoản sở hữu namespace `vn.io.devhub`**.
2. Avatar (góc phải) → **View Account** → **Generate User Token** → *Ok*.
3. Màn hình hiện **Username** và **Password** của token (chỉ hiện **một lần**):
   - Username → secret `CENTRAL_USERNAME`
   - Password → secret `CENTRAL_PASSWORD`

> Đây **không** phải username/mật khẩu đăng nhập. Tạo token mới sẽ làm token cũ hết hiệu lực.
> Đừng dán token vào chat, issue hay commit — chỉ dán thẳng vào GitHub Secrets.

## 2. Tạo khóa ký GPG

Maven Central bắt buộc mọi file (`.jar`, `.pom`, sources, javadoc) có chữ ký GPG.
Nên dùng một khóa **riêng cho việc release**, không dùng khóa cá nhân.

### Cài GnuPG

- **Windows:** cài [Gpg4win](https://www.gpg4win.org/) rồi mở *PowerShell* (hoặc dùng *Git Bash*, đã có sẵn `gpg`).
- **macOS:** `brew install gnupg`
- **Ubuntu/Debian:** `sudo apt install gnupg`

### Tạo khóa

```bash
gpg --quick-gen-key "Devhub Solutions Release (FlowSQL signing) <release@devhub.io.vn>" ed25519 sign 3y
```

GnuPG sẽ hỏi **passphrase** → đặt một chuỗi dài, ngẫu nhiên (vd. tạo bằng `openssl rand -hex 24`).
Passphrase này chính là secret `GPG_PASSPHRASE`.

Lấy fingerprint (chuỗi 40 ký tự hex):

```bash
gpg --list-secret-keys --keyid-format long
#  sec   ed25519/13F69D79A4692C3C 2026-10-10 [SC] [expires: 2029-10-09]
#        58290E368F11B995E487810C13F69D79A4692C3C   <- fingerprint
```

### Xuất private key cho GitHub

```bash
gpg --armor --export-secret-keys <FINGERPRINT> > GPG_PRIVATE_KEY.txt
```

Mở file, copy **toàn bộ** (kể cả dòng `-----BEGIN PGP PRIVATE KEY BLOCK-----` và `-----END ...-----`)
→ secret `GPG_PRIVATE_KEY`. Xong thì **xóa file** này khỏi máy.

### Công khai public key

Central dùng public key để kiểm tra chữ ký. Workflow release đã tự đẩy key lên keyserver mỗi lần chạy,
nhưng có thể làm tay cho chắc:

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <FINGERPRINT>
gpg --keyserver hkps://keys.openpgp.org     --send-keys <FINGERPRINT>
```

### Cất chứng chỉ thu hồi

GnuPG tự tạo file thu hồi ở `~/.gnupg/openpgp-revocs.d/<FINGERPRINT>.rev`
(Windows: `%APPDATA%\gnupg\openpgp-revocs.d\`). Cất vào nơi an toàn (password manager).
Nếu khóa bị lộ: `gpg --import <file>.rev` rồi `--send-keys` lại để thu hồi, sau đó tạo khóa mới.

## 3. Điền secrets vào GitHub

Repo **FlowSQL → Settings → Secrets and variables → Actions → New repository secret**, tạo 4 secret:

| Name | Value |
|---|---|
| `CENTRAL_USERNAME` | Username của token (bước 1) |
| `CENTRAL_PASSWORD` | Password của token (bước 1) |
| `GPG_PRIVATE_KEY` | Nội dung `GPG_PRIVATE_KEY.txt` (bước 2) |
| `GPG_PASSPHRASE` | Passphrase của khóa (bước 2) |

Muốn đổi giá trị: bấm vào secret → **Update secret**. GitHub không cho xem lại giá trị cũ.

## 4. Phát hành một phiên bản

1. Sửa `<version>` trong `pom.xml` (vd. `1.0.1`) và các chỗ ghi version trong `README.md`,
   `docs/index.html`, `skills/flowsql/SKILL.md`; commit lên `main`, chờ CI xanh.
2. **Chạy thử** (không phát hành): *Actions → Release to Maven Central → Run workflow*, branch `main`,
   **không** tick `auto_publish`. Workflow build, ký, upload; deployment dừng ở trạng thái **VALIDATED**.
3. Vào <https://central.sonatype.com/publishing/deployments>: kiểm tra file → **Publish**
   (hoặc **Drop** nếu chỉ thử).
4. **Phát hành chính thức kèm GitHub Release:** *Releases → Draft a new release* → tag `v1.0.1`
   (*Create new tag on publish*) → **Publish release**. Workflow kiểm tra tag khớp `pom.xml`, upload,
   tạo GitHub Release; sau đó bấm **Publish** trên Central như bước 3.

Sau khi Publish, artifact xuất hiện trên Central sau ~10–30 phút; search.maven.org/badge có thể chậm vài giờ.

> Phiên bản đã publish **không thể xóa hay ghi đè**. Lỗi thì tăng version và phát hành lại.

## 5. Xử lý lỗi thường gặp

Khi deploy fail, bước *Report deploy errors* in lý do từ Central ra phần **Annotations** của run.

| Thông báo | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Namespace 'vn.io.devhub' is not allowed` | Token thuộc tài khoản không sở hữu namespace, hoặc namespace chưa Verified | Kiểm tra *View Namespaces*; tạo token trong đúng tài khoản; cập nhật `CENTRAL_*` |
| `401` / `Unauthorized` | Token sai/hết hiệu lực | Tạo token mới, cập nhật `CENTRAL_*` |
| `Invalid signature` / `Could not find a public key` | Public key chưa lên keyserver | Chạy `--send-keys` (bước 2), đợi 5–10 phút, chạy lại |
| `gpg: signing failed: Bad passphrase` | `GPG_PASSPHRASE` không khớp khóa | Cập nhật đúng passphrase |
| `Bundle has content that does NOT have a .pom file` | Maven quá mới trên runner đưa file nội bộ vào bundle | Workflow đã ghim Maven 3.9.9 — giữ nguyên bước *Use Maven 3.9.9* |
| `Component with version ... already exists` | Version này đã publish | Tăng `<version>` |
| Failed ở *Check tag matches pom version* | Tag `vX.Y.Z` khác `<version>` | Sửa tag hoặc pom cho khớp |

## 6. Chạy release từ máy (tùy chọn)

Thêm vào `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.CENTRAL_USERNAME}</username>
      <password>${env.CENTRAL_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

```bash
export CENTRAL_USERNAME=... CENTRAL_PASSWORD=... MAVEN_GPG_PASSPHRASE=...
mvn -P release deploy                       # upload, dừng ở VALIDATED
mvn -P release deploy -Dcentral.autoPublish=true   # upload và publish luôn
```

Dùng Maven 3.9.x (không dùng 3.10+) để tránh lỗi bundle ở bảng trên.
