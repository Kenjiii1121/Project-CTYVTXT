const path = require('path');
const os = require('os');

try { process.loadEnvFile(path.join(__dirname, '.env')); } catch { /* .env is optional on cloud */ }

const express = require('express');
const session = require('express-session');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const pgSession = require('connect-pg-simple')(session);
const crypto = require('crypto');
const { pool, get, query, run, withTransaction, initDb, hashPassword, verifyPassword } = require('./db');
const { sendBookingEmail } = require('./mailer');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

app.set('trust proxy', 1);

function getLanUrls(port) {
  return Object.values(os.networkInterfaces())
    .flat()
    .filter((net) => net && net.family === 'IPv4' && !net.internal)
    .map((net) => `http://${net.address}:${port}`);
}

function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

// Security headers. Tat CSP vi cac trang dung inline script/handler + CDN font-awesome
// + iframe Google Maps; bat CSP se vo giao dien. Cac header con lai (X-Frame-Options,
// nosniff, HSTS...) van duoc bat.
app.use(helmet({ contentSecurityPolicy: false }));

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

if (process.env.NODE_ENV === 'production' && !process.env.SESSION_SECRET) {
  throw new Error('Thieu SESSION_SECRET trong moi truong production');
}

app.use(session({
  store: new pgSession({ pool, tableName: 'session', createTableIfMissing: false }),
  secret: process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex'),
  resave: false,
  saveUninitialized: false,
  cookie: {
    httpOnly: true,
    maxAge: 8 * 60 * 60 * 1000,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax'
  }
}));

// CORS: chi cho phep cac domain khai bao trong ALLOWED_ORIGINS (cach nhau dau phay).
// Mac dinh (khong khai bao) => khong gui header CORS => chi chap nhan request cung
// domain. Khong bao gio phan chieu mot Origin la khi kem Allow-Credentials.
const allowedOrigins = (process.env.ALLOWED_ORIGINS || '')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

app.use((req, res, next) => {
  const origin = req.headers.origin;
  if (origin && allowedOrigins.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  }
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

// Chong do mat khau: toi da 10 lan dang nhap sai / 15 phut / IP.
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  skipSuccessfulRequests: true,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Thu qua nhieu lan. Vui long doi 15 phut roi thu lai.' }
});

// Chong spam form dat xe: toi da 8 don / gio / IP.
const bookingLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 8,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Ban da gui qua nhieu yeu cau. Vui long thu lai sau hoac goi hotline.' }
});

app.get('/healthz', (req, res) => {
  res.json({ ok: true, uptime: process.uptime() });
});

// Chan truy cap web vao thu muc backend (source code, log, data.db cu...)
// Chi /admin (mount rieng ben duoi) va /api moi duoc phep cham vao backend.
app.use((req, res, next) => {
  if (req.path === '/backend' || req.path.startsWith('/backend/')) {
    return res.status(404).json({ error: 'Not found' });
  }
  next();
});

app.use(express.static(path.join(__dirname, '..'), { dotfiles: 'deny' }));
app.use('/admin', express.static(path.join(__dirname, 'admin')));

function requireAuth(req, res, next) {
  if (req.session && req.session.adminId) return next();
  res.status(401).json({ error: 'Chua dang nhap' });
}

const requireOwner = asyncHandler(async (req, res, next) => {
  if (!req.session || !req.session.adminId) return res.status(401).json({ error: 'Chua dang nhap' });
  const admin = await get('SELECT role FROM admins WHERE id = $1', [req.session.adminId]);
  if (!admin || admin.role !== 'owner') return res.status(403).json({ error: 'Chi chu tai khoan moi co quyen nay' });
  next();
});

app.post('/api/login', loginLimiter, asyncHandler(async (req, res) => {
  const { username, password } = req.body || {};
  const admin = await get('SELECT * FROM admins WHERE username = $1', [username || '']);
  if (!admin || !verifyPassword(password || '', admin.salt, admin.password_hash)) {
    return res.status(401).json({ error: 'Sai ten dang nhap hoac mat khau' });
  }
  req.session.adminId = admin.id;
  res.json({ ok: true });
}));

app.post('/api/logout', (req, res) => {
  req.session.destroy(() => res.json({ ok: true }));
});

app.get('/api/me', asyncHandler(async (req, res) => {
  if (!req.session || !req.session.adminId) return res.json({ loggedIn: false });
  const admin = await get('SELECT username, ho_ten, role FROM admins WHERE id = $1', [req.session.adminId]);
  res.json({ loggedIn: true, ...admin });
}));

app.post('/api/change-password', requireAuth, asyncHandler(async (req, res) => {
  const { oldPassword, newPassword } = req.body || {};
  if (!newPassword || newPassword.length < 8) {
    return res.status(400).json({ error: 'Mat khau moi phai tu 8 ky tu' });
  }
  const admin = await get('SELECT * FROM admins WHERE id = $1', [req.session.adminId]);
  if (!verifyPassword(oldPassword || '', admin.salt, admin.password_hash)) {
    return res.status(400).json({ error: 'Mat khau cu khong dung' });
  }
  const salt = crypto.randomBytes(16).toString('hex');
  await run('UPDATE admins SET password_hash = $1, salt = $2 WHERE id = $3', [
    hashPassword(newPassword, salt),
    salt,
    admin.id
  ]);
  res.json({ ok: true });
}));

app.post('/api/bookings', bookingLimiter, asyncHandler(async (req, res) => {
  const b = req.body || {};
  if (!b.ho_ten || !b.dien_thoai || !b.diem_lay_hang || !b.diem_giao_hang) {
    return res.status(400).json({ error: 'Thieu thong tin bat buoc' });
  }

  const id = await withTransaction(async (client) => {
    let customer = (await client.query('SELECT * FROM customers WHERE dien_thoai = $1', [b.dien_thoai])).rows[0];
    if (!customer) {
      const insertedCustomer = await client.query(
        'INSERT INTO customers (ten, dien_thoai, email) VALUES ($1, $2, $3) RETURNING id',
        [b.ho_ten, b.dien_thoai, b.email || null]
      );
      customer = insertedCustomer.rows[0];
    }

    const insertedBooking = await client.query(`
      INSERT INTO bookings (customer_id, ho_ten, dien_thoai, email, diem_lay_hang, diem_giao_hang, loai_hang, trong_luong, ghi_chu)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
      RETURNING id
    `, [
      customer.id,
      b.ho_ten,
      b.dien_thoai,
      b.email || null,
      b.diem_lay_hang,
      b.diem_giao_hang,
      b.loai_hang || null,
      b.trong_luong || null,
      b.ghi_chu || null
    ]);
    return Number(insertedBooking.rows[0].id);
  });

  sendBookingEmail({ id, ...b }).catch((err) => console.error('Loi gui email:', err.message));
  res.json({ ok: true, id });
}));

app.get('/api/bookings', requireAuth, asyncHandler(async (req, res) => {
  res.json(await query('SELECT * FROM bookings ORDER BY id DESC'));
}));

app.put('/api/bookings/:id', requireAuth, asyncHandler(async (req, res) => {
  const { trang_thai, gia_tri } = req.body || {};
  if (trang_thai !== undefined) {
    const allowed = ['moi', 'da_bao_gia', 'dang_chay', 'hoan_thanh', 'huy'];
    if (!allowed.includes(trang_thai)) return res.status(400).json({ error: 'Trang thai khong hop le' });
    await run('UPDATE bookings SET trang_thai = $1 WHERE id = $2', [trang_thai, req.params.id]);
  }
  if (gia_tri !== undefined) {
    await run('UPDATE bookings SET gia_tri = $1 WHERE id = $2', [
      gia_tri === null || gia_tri === '' ? null : Number(gia_tri),
      req.params.id
    ]);
  }
  res.json({ ok: true });
}));

app.get('/api/customers', requireAuth, asyncHandler(async (req, res) => {
  res.json(await query(`
    SELECT c.*, COUNT(b.id)::int AS so_don,
           MAX(b.created_at) AS don_gan_nhat
    FROM customers c
    LEFT JOIN bookings b ON b.customer_id = c.id
    GROUP BY c.id
    ORDER BY don_gan_nhat DESC NULLS LAST
  `));
}));

app.get('/api/customers/:id/bookings', requireAuth, asyncHandler(async (req, res) => {
  res.json(await query('SELECT * FROM bookings WHERE customer_id = $1 ORDER BY id DESC', [req.params.id]));
}));

app.put('/api/customers/:id', requireAuth, asyncHandler(async (req, res) => {
  const { ten, email, ghi_chu } = req.body || {};
  await run(
    'UPDATE customers SET ten = COALESCE($1, ten), email = COALESCE($2, email), ghi_chu = COALESCE($3, ghi_chu) WHERE id = $4',
    [ten || null, email || null, ghi_chu || null, req.params.id]
  );
  res.json({ ok: true });
}));

app.get('/api/vehicles', requireAuth, asyncHandler(async (req, res) => {
  res.json(await query('SELECT * FROM vehicles ORDER BY id'));
}));

app.post('/api/vehicles', requireAuth, asyncHandler(async (req, res) => {
  const { ten, bien_so, loai, tai_trong } = req.body || {};
  if (!ten || !bien_so) return res.status(400).json({ error: 'Can ten xe va bien so' });
  try {
    const inserted = await get(
      'INSERT INTO vehicles (ten, bien_so, loai, tai_trong) VALUES ($1, $2, $3, $4) RETURNING id',
      [ten, bien_so, loai || null, tai_trong || null]
    );
    res.json({ ok: true, id: Number(inserted.id) });
  } catch (error) {
    if (error.code === '23505') return res.status(400).json({ error: 'Bien so da ton tai' });
    throw error;
  }
}));

app.put('/api/vehicles/:id', requireAuth, asyncHandler(async (req, res) => {
  const { ten, bien_so, loai, tai_trong, trang_thai } = req.body || {};
  await run(`
    UPDATE vehicles SET
      ten = COALESCE($1, ten),
      bien_so = COALESCE($2, bien_so),
      loai = COALESCE($3, loai),
      tai_trong = COALESCE($4, tai_trong),
      trang_thai = COALESCE($5, trang_thai)
    WHERE id = $6
  `, [ten || null, bien_so || null, loai || null, tai_trong || null, trang_thai || null, req.params.id]);
  res.json({ ok: true });
}));

app.delete('/api/vehicles/:id', requireAuth, asyncHandler(async (req, res) => {
  await run('DELETE FROM vehicles WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
}));

app.get('/api/trips', requireAuth, asyncHandler(async (req, res) => {
  res.json(await query(`
    SELECT t.*, v.ten AS ten_xe, v.bien_so,
           b.ho_ten AS khach, b.diem_lay_hang, b.diem_giao_hang
    FROM trips t
    LEFT JOIN vehicles v ON v.id = t.vehicle_id
    LEFT JOIN bookings b ON b.id = t.booking_id
    ORDER BY t.id DESC
  `));
}));

app.post('/api/trips', requireAuth, asyncHandler(async (req, res) => {
  const { booking_id, vehicle_id, tai_xe, ngay_khoi_hanh, ghi_chu } = req.body || {};
  if (!vehicle_id) return res.status(400).json({ error: 'Can chon xe' });

  const id = await withTransaction(async (client) => {
    const inserted = await client.query(
      'INSERT INTO trips (booking_id, vehicle_id, tai_xe, ngay_khoi_hanh, ghi_chu) VALUES ($1, $2, $3, $4, $5) RETURNING id',
      [booking_id || null, vehicle_id, tai_xe || null, ngay_khoi_hanh || null, ghi_chu || null]
    );
    await client.query("UPDATE vehicles SET trang_thai = 'dang_chay' WHERE id = $1", [vehicle_id]);
    if (booking_id) await client.query("UPDATE bookings SET trang_thai = 'dang_chay' WHERE id = $1", [booking_id]);
    return Number(inserted.rows[0].id);
  });

  res.json({ ok: true, id });
}));

app.put('/api/trips/:id', requireAuth, asyncHandler(async (req, res) => {
  const { trang_thai } = req.body || {};
  const allowed = ['cho_chay', 'dang_chay', 'hoan_thanh', 'huy'];
  if (!allowed.includes(trang_thai)) return res.status(400).json({ error: 'Trang thai khong hop le' });

  const trip = await get('SELECT * FROM trips WHERE id = $1', [req.params.id]);
  if (!trip) return res.status(404).json({ error: 'Khong tim thay chuyen' });

  await withTransaction(async (client) => {
    await client.query('UPDATE trips SET trang_thai = $1 WHERE id = $2', [trang_thai, req.params.id]);
    if (trang_thai === 'hoan_thanh' || trang_thai === 'huy') {
      await client.query("UPDATE vehicles SET trang_thai = 'san_sang' WHERE id = $1", [trip.vehicle_id]);
      if (trip.booking_id && trang_thai === 'hoan_thanh') {
        await client.query("UPDATE bookings SET trang_thai = 'hoan_thanh' WHERE id = $1", [trip.booking_id]);
      }
    }
  });
  res.json({ ok: true });
}));

app.get('/api/stats', requireAuth, asyncHandler(async (req, res) => {
  const [donMoi, donDangChay, xeSanSang, xeDangChay, tongKhach, donThangNay, doanhThuThangNay] = await Promise.all([
    get("SELECT COUNT(*)::int AS c FROM bookings WHERE trang_thai = 'moi'"),
    get("SELECT COUNT(*)::int AS c FROM bookings WHERE trang_thai = 'dang_chay'"),
    get("SELECT COUNT(*)::int AS c FROM vehicles WHERE trang_thai = 'san_sang'"),
    get("SELECT COUNT(*)::int AS c FROM vehicles WHERE trang_thai = 'dang_chay'"),
    get('SELECT COUNT(*)::int AS c FROM customers'),
    get("SELECT COUNT(*)::int AS c FROM bookings WHERE created_at >= date_trunc('month', now())"),
    get("SELECT COALESCE(SUM(gia_tri), 0)::float AS s FROM bookings WHERE trang_thai != 'huy' AND created_at >= date_trunc('month', now())")
  ]);

  res.json({
    don_moi: donMoi.c,
    don_dang_chay: donDangChay.c,
    xe_san_sang: xeSanSang.c,
    xe_dang_chay: xeDangChay.c,
    tong_khach: tongKhach.c,
    don_thang_nay: donThangNay.c,
    doanh_thu_thang_nay: doanhThuThangNay.s
  });
}));

app.get('/api/stats/revenue', requireAuth, asyncHandler(async (req, res) => {
  res.json(await query(`
    SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS thang,
           COUNT(*)::int AS so_don,
           COALESCE(SUM(CASE WHEN trang_thai != 'huy' THEN gia_tri ELSE 0 END), 0)::float AS doanh_thu
    FROM bookings
    WHERE created_at >= date_trunc('month', now()) - interval '5 months'
    GROUP BY date_trunc('month', created_at)
    ORDER BY date_trunc('month', created_at)
  `));
}));

app.get('/api/admins', requireOwner, asyncHandler(async (req, res) => {
  res.json(await query('SELECT id, username, ho_ten, role, created_at FROM admins ORDER BY id'));
}));

app.post('/api/admins', requireOwner, asyncHandler(async (req, res) => {
  const { username, password, ho_ten, role } = req.body || {};
  if (!username || !password || password.length < 8) {
    return res.status(400).json({ error: 'Can ten dang nhap va mat khau tu 8 ky tu' });
  }
  const vaiTro = role === 'owner' ? 'owner' : 'staff';
  try {
    const salt = crypto.randomBytes(16).toString('hex');
    const inserted = await get(
      'INSERT INTO admins (username, password_hash, salt, ho_ten, role) VALUES ($1, $2, $3, $4, $5) RETURNING id',
      [username, hashPassword(password, salt), salt, ho_ten || username, vaiTro]
    );
    res.json({ ok: true, id: Number(inserted.id) });
  } catch (error) {
    if (error.code === '23505') return res.status(400).json({ error: 'Ten dang nhap da ton tai' });
    throw error;
  }
}));

app.delete('/api/admins/:id', requireOwner, asyncHandler(async (req, res) => {
  const id = Number(req.params.id);
  if (id === Number(req.session.adminId)) return res.status(400).json({ error: 'Khong the xoa chinh minh' });
  const remainingOwners = await get("SELECT COUNT(*)::int AS c FROM admins WHERE role = 'owner' AND id != $1", [id]);
  const target = await get('SELECT role FROM admins WHERE id = $1', [id]);
  if (target && target.role === 'owner' && remainingOwners.c === 0) {
    return res.status(400).json({ error: 'Phai con it nhat 1 chu tai khoan' });
  }
  await run('DELETE FROM admins WHERE id = $1', [id]);
  res.json({ ok: true });
}));

app.use((error, req, res, next) => {
  console.error(error);
  res.status(500).json({ error: 'Loi server' });
});

initDb()
  .then(() => {
    app.listen(PORT, HOST, () => {
      console.log(`>> Backend Van tai Xuan Truong chay tai http://localhost:${PORT}`);
      console.log(`>> Trang admin: http://localhost:${PORT}/admin`);
      getLanUrls(PORT).forEach((url) => console.log(`>> LAN: ${url}`));
    });
  })
  .catch((error) => {
    console.error('Khong khoi dong duoc database:', error);
    process.exit(1);
  });
