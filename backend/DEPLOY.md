# Hướng dẫn đưa website lên thực tế (production)

Backend Node/Express phục vụ **cả API lẫn trang web tĩnh** trong cùng một service.

> ⚠️ **Quan trọng:** server lấy trang tĩnh (`index.html`, `css/`, `js/`, `images/`) từ thư mục **cha** của `backend/`. Vì vậy phải đưa **toàn bộ repo `website-xuan-truong2/`** lên host, không chỉ riêng thư mục `backend/`. Khi cấu hình host, đặt *Root Directory* = `backend`.

---

## 1. Chuẩn bị database Supabase (đã có sẵn)

DB đang dùng Supabase. Nếu tạo mới:
1. Tạo project Supabase → SQL Editor → chạy file `supabase-schema.sql` (hoặc cứ để server tự tạo bảng khi khởi động).
2. Project Settings → Database → copy **Connection string** dạng *pooler* (host `aws-...pooler.supabase.com`, port `6543`).

## 2. Rotate (đổi mới) các secret — BẮT BUỘC

Các secret cũ đã từng để lộ trong file `.env`, nên trước khi go-live phải đổi mới hết:
- **Mật khẩu Supabase:** Supabase → Database → Reset database password → cập nhật lại `DATABASE_URL`.
- **Gmail App Password:** https://myaccount.google.com/apppasswords → xoá cái cũ, tạo cái mới.
- **SESSION_SECRET:** tạo chuỗi ngẫu nhiên mới: `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`

> Lưu ý: nếu mật khẩu DB có ký tự đặc biệt như `@`, phải mã hoá thành `%40` trong `DATABASE_URL`.

## 3. Đưa code lên GitHub

```bash
cd website-xuan-truong2
git init
git add .
git commit -m "Deploy ready"
git branch -M main
git remote add origin https://github.com/<tài-khoản>/<repo>.git
git push -u origin main
```

`.gitignore` đã chặn `.env`, `node_modules/`, `*.db`, `*.log` — an toàn để push (secret KHÔNG bị đẩy lên).

## 4. Deploy trên Render (gợi ý, miễn phí + tự cấp HTTPS)

1. https://render.com → New → **Blueprint** → chọn repo vừa push (Render đọc `render.yaml`).
   - Hoặc New → **Web Service** và nhập tay: Root Directory `backend`, Build `npm install`, Start `npm start`.
2. Vào tab **Environment** nhập các biến (KHÔNG commit `.env`):

```
NODE_ENV=production
DATABASE_URL=postgresql://...        (pooler URI Supabase)
SESSION_SECRET=...                   (chuỗi ngẫu nhiên mới ở bước 2)
GMAIL_USER=...
GMAIL_APP_PASSWORD=...
NOTIFY_TO=...
# ALLOWED_ORIGINS=...   (chỉ cần nếu tách frontend sang domain khác)
```

3. Deploy. Render tự cấp HTTPS + domain `*.onrender.com`. Có thể gắn domain riêng sau.

## 5. Sau khi deploy

- Mở `https://<app>.onrender.com` (website) và `/admin` (quản trị).
- Đăng nhập, **đổi mật khẩu admin ngay** nếu còn là mặc định (`admin` / `xuantruong123`).
- Vào trang admin cập nhật **biển số xe thật** (dữ liệu seed đang là `CHUA-CAP-NHAT-xx`).
- Kiểm tra `/healthz` trả `{"ok":true}`.

## Chạy thử ở máy local

```bash
cd backend
cp .env.example .env   # rồi điền giá trị thật
npm install
npm start
```
- Website: http://localhost:3000 · Admin: http://localhost:3000/admin

## Đã tích hợp sẵn cho production

- Security headers (helmet), cookie phiên `secure` khi `NODE_ENV=production`.
- Rate-limit: đăng nhập (10 lần/15 phút/IP), đặt xe (8 đơn/giờ/IP).
- Session lưu trong Postgres (không mất khi restart).
- CORS khoá theo whitelist `ALLOWED_ORIGINS` (mặc định chỉ cùng domain).
- Không phơi bày thư mục `backend/` qua web.
