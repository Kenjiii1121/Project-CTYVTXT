// Gửi email thông báo đơn mới qua Gmail
// Cần điền GMAIL_USER và GMAIL_APP_PASSWORD trong file .env (xem hướng dẫn trong .env)
const nodemailer = require('nodemailer');

const GMAIL_USER = process.env.GMAIL_USER;
const GMAIL_APP_PASSWORD = (process.env.GMAIL_APP_PASSWORD || '').replace(/\s/g, '');
const NOTIFY_TO = process.env.NOTIFY_TO || GMAIL_USER;

let transporter = null;
if (GMAIL_USER && GMAIL_APP_PASSWORD) {
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: GMAIL_USER, pass: GMAIL_APP_PASSWORD }
  });
  console.log('>> Gửi email: ĐÃ BẬT (từ ' + GMAIL_USER + ' đến ' + NOTIFY_TO + ')');
} else {
  console.log('>> Gửi email: chưa cấu hình — điền GMAIL_APP_PASSWORD trong backend/.env để bật');
}

async function sendBookingEmail(b) {
  if (!transporter) return false;
  const html = `
    <h2 style="color:#e85d04;">🚚 Đơn đặt xe mới #${b.id}</h2>
    <table cellpadding="8" style="border-collapse:collapse; font-family:Arial; font-size:14px;">
      <tr><td style="border:1px solid #ddd;"><b>Khách hàng</b></td><td style="border:1px solid #ddd;">${b.ho_ten}</td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Điện thoại</b></td><td style="border:1px solid #ddd;"><a href="tel:${b.dien_thoai}">${b.dien_thoai}</a></td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Email</b></td><td style="border:1px solid #ddd;">${b.email || '—'}</td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Điểm lấy hàng</b></td><td style="border:1px solid #ddd;">${b.diem_lay_hang}</td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Điểm giao hàng</b></td><td style="border:1px solid #ddd;">${b.diem_giao_hang}</td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Loại hàng</b></td><td style="border:1px solid #ddd;">${b.loai_hang || '—'}</td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Trọng lượng</b></td><td style="border:1px solid #ddd;">${b.trong_luong ? b.trong_luong + ' tấn' : '—'}</td></tr>
      <tr><td style="border:1px solid #ddd;"><b>Ghi chú</b></td><td style="border:1px solid #ddd;">${b.ghi_chu || '—'}</td></tr>
    </table>
    <p style="font-family:Arial; font-size:13px; color:#666;">Vào trang quản trị để xử lý đơn này.</p>`;

  await transporter.sendMail({
    from: `"Website Vận tải Xuân Trường" <${GMAIL_USER}>`,
    to: NOTIFY_TO,
    subject: `🚚 Đơn đặt xe mới #${b.id} — ${b.ho_ten} (${b.diem_lay_hang} → ${b.diem_giao_hang})`,
    html
  });
  return true;
}

module.exports = { sendBookingEmail };
