# TTTN QL NVL

Ứng dụng Spring Boot + Thymeleaf quản lý vật tư và kho cho đồ án tốt nghiệp.

## Yêu cầu

- Java 21; và
- Docker Desktop, hoặc PostgreSQL 16 cùng Maven 3.6.3+.

## Chạy nhanh bằng Docker

Tạo file cấu hình local từ mẫu và thay giá trị `DB_PASSWORD` trước khi chạy. File `.env` không được Git theo dõi.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux/macOS:

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Mở `http://localhost:8080/login`.

Database được tạo bằng Flyway khi ứng dụng khởi động. Baseline chưa tự tạo demo user; tài khoản demo sẽ được bổ sung bằng cơ chế provisioning riêng trước giai đoạn demo.

## Chạy bằng Maven Wrapper

Khởi động PostgreSQL và cung cấp các biến môi trường nếu khác giá trị local mặc định:

```text
DB_URL=jdbc:postgresql://localhost:5432/tttn_ql_nvl
DB_USERNAME=tttn
DB_PASSWORD=<mật-khẩu-local-của-bạn>
SESSION_COOKIE_SECURE=false
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

## Kiểm thử

```powershell
.\mvnw.cmd test
```

Ứng dụng dùng session authentication, CSRF và đúng năm role nghiệp vụ. Session hết hạn sau 10 phút không hoạt động.
