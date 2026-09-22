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

Database được tạo bằng Flyway khi ứng dụng khởi động.

Khi profile `demo` được bật, ứng dụng tạo các tài khoản còn thiếu mà không thay thế tài khoản đã tồn tại:

| Username | Vai trò |
|---|---|
| `requester_demo` | Người đề nghị |
| `request_approver_demo` | Người duyệt đề nghị |
| `inventory_staff_demo` | Nhân viên kho |
| `inventory_approver_demo` | Người duyệt kho |
| `warehouse_keeper_demo` | Thủ kho |

Tất cả dùng mật khẩu lấy từ `DEMO_USER_PASSWORD` trong `.env`; mật khẩu thật không được commit.

## Chạy bằng Maven Wrapper

Khởi động PostgreSQL và cung cấp các biến môi trường nếu khác giá trị local mặc định:

```text
DB_URL=jdbc:postgresql://localhost:5432/tttn_ql_nvl
DB_USERNAME=tttn
DB_PASSWORD=<mật-khẩu-local-của-bạn>
SPRING_PROFILES_ACTIVE=demo
DEMO_USER_PASSWORD=<mật-khẩu-demo-local-của-bạn>
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

Sau khi khởi động Docker Compose với profile `demo`, chạy smoke test chỉ đọc cho đăng nhập và phân quyền của năm vai trò:

```powershell
.\scripts\smoke-auth-workflow.ps1
```

Script lấy `DEMO_USER_PASSWORD` từ biến môi trường hoặc `.env`, kiểm tra dashboard, danh sách vật tư, quyền duyệt đề nghị, quyền báo cáo và quyền xem history. Có thể truyền `-BaseUrl` nếu app chạy ở địa chỉ khác. Docker build context chỉ bao gồm `pom.xml` và `src`; app container chạy bằng UID 10001.

Integration test dùng Testcontainers và PostgreSQL 16 tạm, chạy riêng khi Docker Desktop đang hoạt động. Test tạo dữ liệu trong database tạm, kiểm tra Request DRAFT/SUBMITTED cancellation, luồng nhập → xuất → điều chuyển, tồn vật lý và StatusHistory; không sửa database của Compose:

```powershell
.\mvnw.cmd "-Dtest=WarehouseRequestFlowIT" test
```

Ứng dụng dùng session authentication, CSRF và đúng năm role nghiệp vụ. Session hết hạn sau 10 phút không hoạt động.
