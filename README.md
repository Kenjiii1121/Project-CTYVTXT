# Project CTYVTXT

Hệ thống web quản lý vận tải gồm website tiếp nhận yêu cầu đặt xe và trang quản trị nội bộ.

## Chức năng

- Website giới thiệu dịch vụ, đội xe và form yêu cầu báo giá.
- Quản lý đơn đặt xe, khách hàng, phương tiện và chuyến xe.
- Dashboard thống kê đơn hàng, phương tiện và doanh thu sáu tháng.
- Đăng nhập bằng session, phân quyền `owner` và `staff`.
- Email thông báo đơn mới khi cấu hình Gmail.

## Kiến trúc

```text
Public website / Admin dashboard
              |
          REST API
              |
Spring Boot 4 + JDBC + Spring Session
              |
       PostgreSQL / Supabase
```

Backend production là `backend-v2` dùng Java 21. `backend` là Node.js V1 được giữ lại để đối chiếu và hỗ trợ migration.

## Chạy Java V2 ở local

Yêu cầu Java 21 và Maven 3.9+.

```powershell
cd backend-v2
$env:APP_FORCE_H2='true'
$env:ADMIN_INITIAL_PASSWORD='replace-with-at-least-12-characters'
mvn spring-boot:run
```

- Website: `http://localhost:3001`
- Admin: `http://localhost:3001/admin`
- Health check: `http://localhost:3001/healthz`

H2 chỉ dùng cho demo local và bị xóa khi dừng ứng dụng.

## Kiểm tra

```powershell
cd backend-v2
mvn test
mvn package
```

Integration test kiểm tra health check, authentication, booking validation, session và chống gán trùng xe, kể cả hai request đồng thời.

## Production

`render.yaml` triển khai Java V2 bằng Docker. Các secret bắt buộc phải được nhập trong Render Dashboard:

- `DATABASE_URL`
- `ADMIN_INITIAL_PASSWORD` nếu database chưa có admin

Các biến email là tùy chọn. Không commit `.env`, database credential hay password vào repository.

Xem thêm tại [`backend-v2/README.md`](backend-v2/README.md).
## Deployment

### V1 — Production / Interview Demo
- Backend: Node.js, Express
- Database: PostgreSQL (Supabase)
- Hosting: Render
- Live Demo: https://project-ctyvtxt.onrender.com

### V2 — Development / Extended Version
- Backend: Java, Spring Boot
- Status: Separate development version

The production deployment currently uses V1.
