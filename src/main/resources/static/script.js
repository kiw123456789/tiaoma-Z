/* ==========================================================
   script.js : ไฟล์ JavaScript หลักของเว็บไซต์ "เที่ยวมะ"
   ใช้ร่วมกันทุกหน้า
   ต้องโหลด places-data.js ก่อนไฟล์นี้ในหน้าที่ใช้ข้อมูลสถานที่
========================================================== */

/* ----- ปุ่มเลื่อนกลับขึ้นบนสุด (ทุกหน้า) ----- */
window.addEventListener('scroll', () => {
  const btn = document.getElementById('back-to-top');
  if (btn) btn.style.display = window.scrollY > 300 ? 'block' : 'none';
});

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ----- เลื่อนไปยังส่วนสถานที่ท่องเที่ยว (หน้าแรก) ----- */
function scrollToPlaces() {
  const el = document.getElementById('places');
  if (el) el.scrollIntoView({ behavior: 'smooth' });
}

/* ==========================================================
   ระบบสมัครสมาชิก / เข้าสู่ระบบ / บันทึกที่เที่ยวโปรด
   คุยกับ backend (Spring Boot) ผ่าน REST API — ล็อกอินด้วย session cookie
   ต้องเปิดหน้าเว็บผ่านเซิร์ฟเวอร์เดียวกัน เช่น http://localhost:8080
========================================================== */

let currentUser = null;        // ผู้ใช้ที่ล็อกอินอยู่ (null = ยังไม่ล็อกอิน)
let likedIds = new Set();      // id สถานที่ที่ผู้ใช้ปัจจุบันกดหัวใจไว้

/* ----- เรียก API แล้วคืนค่า JSON (ถ้าผิดพลาดจะ throw Error ที่มี message ภาษาไทยจากเซิร์ฟเวอร์) ----- */
async function api(path, options = {}) {
  let res;
  try {
    res = await fetch('/api' + path, {
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      ...options
    });
  } catch (e) {
    const err = new Error('เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ กรุณาตรวจสอบว่า backend เปิดอยู่');
    err.status = 0;
    throw err;
  }
  let data = null;
  try { data = await res.json(); } catch (e) { /* 204 ไม่มี body */ }
  if (!res.ok) {
    const err = new Error((data && data.message) || 'เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง');
    err.status = res.status;
    throw err;
  }
  return data;
}

/* ป้องกัน HTML injection เวลาแทรกข้อความลงใน innerHTML
   *** ต้องใช้กับ "ทุกค่า" ที่มาจากฐานข้อมูลหรือผู้ใช้เสมอ *** */
function escapeHTML(text) {
  if (text === null || text === undefined) return '';
  return String(text).replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}

/* escape สำหรับค่าที่จะไปอยู่ใน attribute ของ HTML (เช่น src, alt, data-*) */
function escapeAttr(text) {
  return escapeHTML(text);
}

/* อนุญาตเฉพาะ URL รูปที่ปลอดภัย (path ในเว็บนี้ หรือ data:image) กัน javascript: injection */
function safeImageSrc(src) {
  const value = String(src || '').trim();
  if (!value) return '';
  if (/^(https?:)?\/\//i.test(value)) return value;          // รูปจากเว็บนอก
  if (/^data:image\//i.test(value)) return value;            // รูป base64 (ของเดิมที่ยังอยู่ใน DB)
  if (/^[a-zA-Z0-9._\/-]+$/.test(value)) return value;       // path ปกติ เช่น image/xxx.jpg
  return '';
}

/* รูปไหนมีไฟล์ .webp คู่กันบ้าง (สร้างไว้ตอนบีบอัดรูป) — เบราว์เซอร์สมัยใหม่จะโหลด webp ที่เล็กกว่ามาก */
const WEBP_AVAILABLE = new Set([
  'ayutthaya',
  'chiangmai-old-city',
  'doi-inthanon',
  'erawan-national-park',
  'hero-bg',
  'jomtien-beach',
  'khao-laem-ya',
  'khao-yai',
  'koh-kood',
  'koh-samui',
  'mon-bridge',
  'pai-canyon',
  'phi-phi',
  'phuket-beach',
  'river-kwai-bridge',
  'wat-phra-kaew'
]);

/* สร้าง <picture> ที่เสิร์ฟ .webp ให้เบราว์เซอร์ที่รองรับ และถอยไปใช้ .jpg ให้ตัวที่ไม่รองรับ
   ถ้ารูปนั้นไม่มีคู่ .webp ก็คืน <img> ธรรมดา */
function pictureHTML(src, alt, { width, height, lazy = true, className = '' } = {}) {
  const safe = safeImageSrc(src);
  if (!safe) return '';
  const attrs = [
    `alt="${escapeAttr(alt || '')}"`,
    lazy ? 'loading="lazy" decoding="async"' : '',
    width ? `width="${width}"` : '',
    height ? `height="${height}"` : '',
    className ? `class="${escapeAttr(className)}"` : '',
  ].filter(Boolean).join(' ');

  const m = safe.match(/^(?:\/)?image\/([\w-]+)\.(?:jpg|jpeg|png)$/i);
  if (m && WEBP_AVAILABLE.has(m[1])) {
    return `<picture>` +
      `<source srcset="image/${m[1]}.webp" type="image/webp">` +
      `<img src="${escapeAttr(safe)}" ${attrs}>` +
      `</picture>`;
  }
  return `<img src="${escapeAttr(safe)}" ${attrs}>`;
}

/* อนุญาตให้ redirect เฉพาะหน้าในเว็บนี้ (กันลิงก์หลอกไปเว็บอื่น) */
function safeRedirect(target) {
  return /^[A-Za-z0-9_-]+\.html(\?[^\s]*)?$/.test(target || '') ? target : 'index.html';
}

function showBox(box, message) {
  if (!box) return;
  box.textContent = message;
  box.style.display = 'block';
}
function hideBox(box) {
  if (box) box.style.display = 'none';
}

function getSession() { return currentUser; }

/* ==========================================================
   Toast และกล่องยืนยัน — แทน alert() / confirm() ของเบราว์เซอร์
========================================================== */

function toast(message, type = 'info', duration = 3500) {
  let stack = document.querySelector('.toast-stack');
  if (!stack) {
    stack = document.createElement('div');
    stack.className = 'toast-stack';
    stack.setAttribute('role', 'status');
    stack.setAttribute('aria-live', 'polite');
    document.body.appendChild(stack);
  }
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  const icon = type === 'success' ? '✅' : type === 'error' ? '⚠️' : 'ℹ️';
  el.innerHTML = `<span>${icon}</span><span>${escapeHTML(message)}</span>`;
  stack.appendChild(el);
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transition = 'opacity .25s ease';
    setTimeout(() => el.remove(), 260);
  }, duration);
}

/**
 * กล่องยืนยันในธีมของเว็บ คืนค่า Promise<boolean>
 * ใช้แทน confirm() ที่หน้าตาไม่เข้ากับเว็บและบล็อกการทำงานของเบราว์เซอร์
 */
function confirmDialog({ title, message, confirmText = 'ตกลง', cancelText = 'ยกเลิก', danger = false }) {
  return new Promise(resolve => {
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.innerHTML = `
      <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="modalTitle">
        <h3 id="modalTitle">${escapeHTML(title)}</h3>
        <p>${escapeHTML(message)}</p>
        <div class="modal-actions">
          <button type="button" class="btn-outline" data-act="cancel">${escapeHTML(cancelText)}</button>
          <button type="button" class="${danger ? 'btn-danger' : 'btn-primary'}" data-act="ok">${escapeHTML(confirmText)}</button>
        </div>
      </div>`;

    const close = (result) => {
      document.removeEventListener('keydown', onKey);
      backdrop.remove();
      resolve(result);
    };
    const onKey = (e) => {
      if (e.key === 'Escape') close(false);
    };

    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) close(false);
      const act = e.target.dataset ? e.target.dataset.act : null;
      if (act === 'ok') close(true);
      if (act === 'cancel') close(false);
    });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(backdrop);
    const okBtn = backdrop.querySelector('[data-act="ok"]');
    if (okBtn) okBtn.focus();
  });
}

/* ==========================================================
   สถานะโหลด (skeleton) กันหน้าโล่งระหว่างรอ API
========================================================== */

function skeletonCards(count = 6) {
  return `<div class="skeleton-grid">${Array.from({ length: count }).map(() => `
    <div class="skeleton-card">
      <div class="sk-thumb"></div>
      <div class="sk-body">
        <div class="sk-line"></div>
        <div class="sk-line"></div>
        <div class="sk-line short"></div>
      </div>
    </div>`).join('')}</div>`;
}

function showSkeleton(containerId, count = 6) {
  const el = document.getElementById(containerId);
  if (el) el.innerHTML = skeletonCards(count);
}

/* โหลดสถานะล็อกอิน + รายการที่เที่ยวโปรดจาก backend (เรียกครั้งเดียวตอนเปิดหน้า) */
async function initSession() {
  currentUser = null;
  likedIds = new Set();
  try {
    const me = await api('/auth/me');
    if (me && me.authenticated) {
      currentUser = me;
      likedIds = new Set(await api('/likes'));
    }
  } catch (e) {
    console.warn('โหลดสถานะล็อกอินไม่สำเร็จ:', e.message);
  }
}

/* ----- โหลดรายการสถานที่ท่องเที่ยวทั้งหมดจาก backend เข้าตัวแปร PLACES (places-data.js) -----
   เรียกเฉพาะหน้าที่ใช้ข้อมูลสถานที่จริงๆ (ดู pageNeedsPlaces()) */
async function loadPlaces() {
  if (typeof PLACES === 'undefined') return [];
  try {
    PLACES = await api('/places');
  } catch (e) {
    console.warn('โหลดรายการสถานที่ไม่สำเร็จ:', e.message);
    PLACES = [];
  }
  return PLACES;
}

/* ----- สมัครสมาชิก (register.html) ----- */
async function handleRegister(event) {
  event.preventDefault();

  const name = document.getElementById('regName').value.trim();
  const email = document.getElementById('regEmail').value.trim().toLowerCase();
  const password = document.getElementById('regPassword').value;
  const confirm = document.getElementById('regConfirm').value;

  const errorBox = document.getElementById('regError');
  const successBox = document.getElementById('regSuccess');
  hideBox(errorBox);

  if (!name || !email || !password || !confirm) return showBox(errorBox, 'กรุณากรอกข้อมูลให้ครบทุกช่อง'), false;
  if (!email.includes('@')) return showBox(errorBox, 'กรุณากรอกอีเมลให้ถูกต้อง'), false;

  const strength = checkPasswordStrength(password);
  if (!strength.ok) return showBox(errorBox, strength.message), false;
  if (password !== confirm) return showBox(errorBox, 'รหัสผ่านทั้งสองช่องไม่ตรงกัน'), false;

  const submitBtn = event.target.querySelector('button[type="submit"]');
  if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'กำลังสมัคร...'; }

  try {
    await api('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) });
  } catch (e) {
    showBox(errorBox, e.message);
    if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = 'สมัครสมาชิก'; }
    return false;
  }

  showBox(successBox, `สมัครสมาชิกสำเร็จ! ยินดีต้อนรับคุณ ${name} — กำลังพาไปหน้าเข้าสู่ระบบ...`);
  setTimeout(() => { window.location.href = 'login.html'; }, 1500);
  return false;
}

/**
 * ตรวจความแข็งแรงของรหัสผ่าน
 * เกณฑ์: อย่างน้อย 8 ตัวอักษร และต้องมีทั้งตัวอักษรกับตัวเลข
 * (เดิมกำหนดแค่ 6 ตัวอักษรและไม่ตรวจอะไรเลย)
 */
function checkPasswordStrength(password) {
  if (!password || password.length < 8) {
    return { ok: false, score: 0, message: 'รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร' };
  }
  if (password.length > 72) {
    return { ok: false, score: 0, message: 'รหัสผ่านยาวเกินไป (สูงสุด 72 ตัวอักษร)' };
  }
  const hasLetter = /[A-Za-z\u0E00-\u0E7F]/.test(password);
  const hasDigit = /\d/.test(password);
  if (!hasLetter || !hasDigit) {
    return { ok: false, score: 1, message: 'รหัสผ่านควรมีทั้งตัวอักษรและตัวเลขผสมกัน' };
  }

  const common = ['password', '12345678', '11111111', 'qwertyui', 'abc12345', '123456789'];
  if (common.includes(password.toLowerCase())) {
    return { ok: false, score: 1, message: 'รหัสผ่านนี้ถูกใช้บ่อยเกินไปและเดาง่าย กรุณาตั้งรหัสอื่น' };
  }

  let score = 2;
  if (password.length >= 12) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  return { ok: true, score, message: '' };
}

/* แสดงแถบความแข็งแรงของรหัสผ่านใต้ช่องกรอก (ถ้าหน้านั้นมี element รองรับ) */
function renderPasswordStrength(inputId, meterId) {
  const input = document.getElementById(inputId);
  const meter = document.getElementById(meterId);
  if (!input || !meter) return;
  input.addEventListener('input', () => {
    const value = input.value;
    if (!value) { meter.textContent = ''; meter.className = 'password-meter'; return; }
    const r = checkPasswordStrength(value);
    const labels = ['อ่อนมาก', 'อ่อน', 'พอใช้', 'ดี', 'แข็งแรงมาก'];
    meter.textContent = r.ok
      ? `ความแข็งแรง: ${labels[Math.min(r.score, 4)]}`
      : r.message;
    meter.className = 'password-meter ' + (r.ok ? 'ok' : 'warn');
  });
}

/* ----- เข้าสู่ระบบ (login.html) ----- */
async function handleLogin(event) {
  event.preventDefault();

  const email = document.getElementById('loginEmail').value.trim().toLowerCase();
  const password = document.getElementById('loginPassword').value;

  const errorBox = document.getElementById('loginError');
  const successBox = document.getElementById('loginSuccess');
  hideBox(errorBox);

  if (!email || !password) return showBox(errorBox, 'กรุณากรอกอีเมลและรหัสผ่าน'), false;

  const submitBtn = event.target.querySelector('button[type="submit"]');
  if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'กำลังเข้าสู่ระบบ...'; }

  let user;
  try {
    user = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
  } catch (e) {
    showBox(errorBox, e.message);
    if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = 'เข้าสู่ระบบ'; }
    return false;
  }

  showBox(successBox, `เข้าสู่ระบบสำเร็จ! ยินดีต้อนรับคุณ ${user.name} — กำลังพาไปหน้าที่เหมาะสม...`);

  const redirect = safeRedirect(new URLSearchParams(window.location.search).get('redirect'));
  setTimeout(() => { window.location.href = redirect; }, 1200);
  return false;
}

/* ----- ลืมรหัสผ่าน (forgot-password.html) ----- */
async function handleForgotPassword(event) {
  event.preventDefault();

  const email = document.getElementById('forgotEmail').value.trim().toLowerCase();
  const errorBox = document.getElementById('forgotError');
  const successBox = document.getElementById('forgotSuccess');
  hideBox(errorBox);
  hideBox(successBox);

  if (!email || !email.includes('@')) return showBox(errorBox, 'กรุณากรอกอีเมลให้ถูกต้อง'), false;

  try {
    const data = await api('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
    showBox(successBox, data.message);
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

/* ----- ตั้งรหัสผ่านใหม่จากลิงก์ในอีเมล (reset-password.html?token=...) ----- */
async function handleResetPassword(event) {
  event.preventDefault();

  const token = new URLSearchParams(window.location.search).get('token');
  const password = document.getElementById('resetPassword').value;
  const confirm = document.getElementById('resetConfirm').value;
  const errorBox = document.getElementById('resetError');
  const successBox = document.getElementById('resetSuccess');
  hideBox(errorBox);

  if (!token) return showBox(errorBox, 'ลิงก์ไม่ถูกต้อง กรุณาขอลิงก์ตั้งรหัสผ่านใหม่อีกครั้ง'), false;

  const strength = checkPasswordStrength(password);
  if (!strength.ok) return showBox(errorBox, strength.message), false;
  if (password !== confirm) return showBox(errorBox, 'รหัสผ่านทั้งสองช่องไม่ตรงกัน'), false;

  try {
    const data = await api('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, password }) });
    showBox(successBox, data.message + ' — กำลังพาไปหน้าเข้าสู่ระบบ...');
    setTimeout(() => { window.location.href = 'login.html'; }, 1800);
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

async function logoutUser() {
  const ok = await confirmDialog({
    title: 'ออกจากระบบ',
    message: 'ต้องการออกจากระบบตอนนี้ใช่ไหม?',
    confirmText: 'ออกจากระบบ',
    cancelText: 'อยู่ต่อ'
  });
  if (!ok) return;
  try { await api('/auth/logout', { method: 'POST' }); } catch (e) { /* ออกจากหน้าอยู่ดี */ }
  window.location.href = 'index.html';
}

/* ----- บังคับให้เข้าสู่ระบบก่อนบันทึกที่เที่ยวโปรด ----- */
async function requireLogin(nextUrl) {
  const goTo = nextUrl || (window.location.pathname.split('/').pop() + window.location.search);
  const ok = await confirmDialog({
    title: 'ต้องเข้าสู่ระบบก่อน',
    message: 'บันทึกที่เที่ยวโปรดได้เมื่อเข้าสู่ระบบแล้ว ต้องการไปหน้าเข้าสู่ระบบตอนนี้ไหม?',
    confirmText: 'ไปหน้าเข้าสู่ระบบ',
    cancelText: 'ไว้ก่อน'
  });
  if (ok) {
    window.location.href = 'login.html?redirect=' + encodeURIComponent(goTo);
  }
  return false;
}

/* บันทึก/ยกเลิกที่เที่ยวโปรดที่ backend แล้วอัปเดตสถานะในหน้า คืนค่า true ถ้าตอนนี้ถูกบันทึกอยู่ */
async function setLike(placeId, shouldLike) {
  await api('/likes/' + encodeURIComponent(placeId), { method: shouldLike ? 'PUT' : 'DELETE' });
  if (shouldLike) likedIds.add(placeId); else likedIds.delete(placeId);
  return shouldLike;
}

/* session หมดอายุ → กลับเป็นผู้เยี่ยมชม แล้วชวนล็อกอินใหม่ / ข้อผิดพลาดอื่น → แจ้งข้อความ */
function handleLikeError(e, nextUrl) {
  if (e.status === 401) {
    currentUser = null;
    likedIds = new Set();
    renderAuthHeader();
    syncLikedIcons();
    requireLogin(nextUrl);
  } else {
    toast(e.message, 'error');
  }
}

/* ----- กดปุ่มหัวใจ บันทึก/ยกเลิกสถานที่โปรด (ผูกกับผู้ใช้ที่ล็อกอินอยู่) ----- */
async function toggleLike(button) {
  const card = button.closest('.card');
  const placeId = card ? card.dataset.id : null;

  if (!currentUser) {
    requireLogin();
    return;
  }
  if (!placeId) return;

  button.disabled = true;
  try {
    const nowLiked = await setLike(placeId, !likedIds.has(placeId));
    card.classList.toggle('liked', nowLiked);
    button.textContent = nowLiked ? '❤️' : '🤍';
    button.setAttribute('aria-pressed', nowLiked ? 'true' : 'false');
    button.setAttribute('aria-label', nowLiked ? 'ยกเลิกที่เที่ยวโปรด' : 'บันทึกเป็นที่เที่ยวโปรด');
    toast(nowLiked ? 'บันทึกเป็นที่เที่ยวโปรดแล้ว' : 'นำออกจากที่เที่ยวโปรดแล้ว', 'success', 2000);
  } catch (e) {
    handleLikeError(e);
  } finally {
    button.disabled = false;
  }
}

/* ----- ทำให้หัวใจในการ์ดตรงกับสถานะที่บันทึกไว้จริงของผู้ใช้ปัจจุบัน ----- */
function syncLikedIcons() {
  document.querySelectorAll('.card[data-id]').forEach(card => {
    const liked = likedIds.has(card.dataset.id);
    card.classList.toggle('liked', liked);
    const btn = card.querySelector('.like-btn');
    if (btn) {
      btn.textContent = liked ? '❤️' : '🤍';
      btn.setAttribute('aria-pressed', liked ? 'true' : 'false');
      btn.setAttribute('aria-label', liked ? 'ยกเลิกที่เที่ยวโปรด' : 'บันทึกเป็นที่เที่ยวโปรด');
    }
  });
}

/* ----- ปรับส่วนหัวเว็บไซต์ตามสถานะการล็อกอิน (ทุกหน้า) -----
   หมายเหตุ: บนมือถือ ลิงก์/ปุ่มพวกนี้จะถูกซ่อนไว้ในเมนู ☰ (ดู .auth-menu-panel ใน style.css)
   เพื่อให้แถบบนสุดเหลือแค่ช่องค้นหา ไม่รกจอ — เดสก์ท็อปยังแสดงผลแบบเดิมทุกอย่าง */
function renderAuthHeader() {
  const area = document.getElementById('authArea');
  if (!area) return;
  const session = getSession();

  const menuLinks = session
    ? `
      ${session.role === 'ADMIN' ? `<a href="admin.html" class="btn-outline" onclick="closeMobileMenu()">⚙️ จัดการเนื้อหา</a>` : ''}
      <a href="liked.html" class="btn-outline" onclick="closeMobileMenu()">💚 ที่เที่ยวโปรดของฉัน</a>
      <a href="profile.html" class="btn-outline" onclick="closeMobileMenu()">👤 บัญชีของฉัน</a>
      <span class="user-chip">สวัสดี, ${escapeHTML(String(session.name).split(' ')[0])}</span>
      <button class="btn-primary" onclick="logoutUser()">ออกจากระบบ</button>
    `
    : `<a href="login.html" onclick="closeMobileMenu()"><button class="btn-primary">สมัครสมาชิก/เข้าสู่ระบบ</button></a>`;

  area.innerHTML = `
    <button type="button" class="mobile-menu-toggle" aria-label="เมนู" aria-expanded="false" onclick="toggleMobileMenu(event)">☰</button>
    <div class="auth-menu-panel" id="authMenuPanel">${menuLinks}</div>
  `;
}

/* ----- เปิด/ปิดเมนู ☰ บนมือถือ (ไม่มีผลตอนจอกว้าง เพราะ .auth-menu-panel แสดงผลแบบ inline อยู่แล้ว) ----- */
function toggleMobileMenu(event) {
  if (event) event.stopPropagation();
  const panel = document.getElementById('authMenuPanel');
  const toggleBtn = document.querySelector('.mobile-menu-toggle');
  if (!panel) return;
  const nowOpen = panel.classList.toggle('open');
  if (toggleBtn) toggleBtn.setAttribute('aria-expanded', nowOpen ? 'true' : 'false');
}

function closeMobileMenu() {
  const panel = document.getElementById('authMenuPanel');
  const toggleBtn = document.querySelector('.mobile-menu-toggle');
  if (panel) panel.classList.remove('open');
  if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
}

// ปิดเมนูเวลาแตะ/คลิกที่อื่นนอกเมนู
document.addEventListener('click', (e) => {
  const panel = document.getElementById('authMenuPanel');
  if (!panel || !panel.classList.contains('open')) return;
  if (!panel.contains(e.target) && !e.target.closest('.mobile-menu-toggle')) closeMobileMenu();
});

/* ==========================================================
   การแสดงผลสถานที่ท่องเที่ยว (หน้า index.html / places.html)
========================================================== */

/* แปลงคะแนนเป็นดาว เช่น 4.3 -> ★★★★☆ */
function starsHTML(rating) {
  const full = Math.round(Number(rating) || 0);
  return '★'.repeat(full) + '☆'.repeat(Math.max(0, 5 - full));
}

function ratingBadgeHTML(place) {
  const count = Number(place.ratingCount) || 0;
  if (!count) {
    return `<span class="rating-inline"><span class="stars">☆☆☆☆☆</span> ยังไม่มีรีวิว</span>`;
  }
  const avg = Number(place.ratingAverage) || 0;
  return `<span class="rating-inline">
      <span class="stars" aria-hidden="true">${starsHTML(avg)}</span>
      <span>${avg.toFixed(1)} (${count} รีวิว)</span>
    </span>`;
}

/**
 * การ์ดสถานที่ — ทุกค่าที่มาจากฐานข้อมูลถูก escape ก่อนเสมอ
 * (เดิมยัด place.name / place.short ลง innerHTML ตรงๆ ซึ่งเป็นช่องโหว่ stored XSS)
 */
function placeCardHTML(place) {
  const imageInner = pictureHTML(place.image, place.name, { width: 400, height: 140 })
    || `<div class="placeholder-label">รูป: ${escapeHTML(place.name)}</div>`;

  return `
    <div class="card" data-name="${escapeAttr(place.name)}" data-id="${escapeAttr(place.id)}">
      <button class="like-btn" onclick="toggleLike(this)" aria-label="บันทึกเป็นที่เที่ยวโปรด" aria-pressed="false">🤍</button>
      <div class="thumb">${imageInner}</div>
      <div class="card-body">
        <span class="badge-category">${escapeHTML(place.category)}</span>
        <h3>${escapeHTML(place.name)}</h3>
        <p>${escapeHTML(place.short)}</p>
        ${ratingBadgeHTML(place)}
        <a class="read-more" href="place-detail.html?id=${encodeURIComponent(place.id)}">อ่านเพิ่มเติม ›</a>
      </div>
    </div>
  `;
}

function renderPlacesGrid(containerId, placeList) {
  const grid = document.getElementById(containerId);
  if (!grid || typeof PLACES === 'undefined') return;
  grid.innerHTML = placeList.map(placeCardHTML).join('');
  syncLikedIcons();
}

/* ----- แสดงสถานที่แบบแยกเป็นส่วนตามภาค (ใช้ในหน้า places.html โหมด "all") -----
   จัดกลุ่มด้วย getRegionByProvince() จาก places-data.js แล้ววาดเป็นแถบต่อภาค เลื่อนแนวนอนได้ทีละภาค */
function renderPlacesGroupedByRegion(containerId, placeList) {
  const container = document.getElementById(containerId);
  if (!container || typeof PLACES === 'undefined') return;

  const groups = {};
  placeList.forEach(place => {
    const region = getRegionByProvince(place.province) || 'อื่นๆ';
    if (!groups[region]) groups[region] = [];
    groups[region].push(place);
  });

  const orderedRegions = [...REGION_ORDER, 'อื่นๆ'].filter(r => groups[r] && groups[r].length);

  container.innerHTML = orderedRegions.map(region => `
    <div class="region-section" data-region="${escapeAttr(region)}">
      <div class="region-header">
        <div class="region-title-group">
          <span class="region-icon" aria-hidden="true">${REGION_ICONS[region] || '📍'}</span>
          <h3 class="region-title">${escapeHTML(REGION_LABELS[region] || region)}</h3>
          <span class="region-count">${groups[region].length} ที่</span>
        </div>
        <div class="region-nav">
          <button type="button" aria-label="เลื่อนดูสถานที่ก่อนหน้า" onclick="scrollRegion(this, -1)">‹</button>
          <button type="button" aria-label="เลื่อนดูสถานที่ถัดไป" onclick="scrollRegion(this, 1)">›</button>
        </div>
      </div>
      <div class="region-grid">
        ${groups[region].map(placeCardHTML).join('')}
      </div>
    </div>
  `).join('');

  syncLikedIcons();

  const sections = container.querySelectorAll('.region-section');
  sections.forEach(updateRegionNavState);
  // เผื่อหน้าจอถูกปรับขนาด (เช่น หมุนมือถือ) ให้เช็คใหม่ว่ายังต้องมีปุ่มเลื่อนไหม
  window.addEventListener('resize', () => sections.forEach(updateRegionNavState));
}

/* เลื่อนแถวสถานที่ในภาคที่กดปุ่ม ‹ › ไปทีละเกือบ 1 หน้าจอ */
function scrollRegion(buttonEl, direction) {
  const section = buttonEl.closest('.region-section');
  const grid = section ? section.querySelector('.region-grid') : null;
  if (!grid) return;
  grid.scrollBy({ left: direction * grid.clientWidth * 0.9, behavior: 'smooth' });
  setTimeout(() => updateRegionNavState(section), 350);
}

/* ซ่อนปุ่มเลื่อนถ้าการ์ดในภาคนั้นพอดีจออยู่แล้ว (ไม่ต้องเลื่อน) และปิดปุ่มฝั่งที่เลื่อนสุดทางแล้ว */
function updateRegionNavState(section) {
  const grid = section.querySelector('.region-grid');
  const nav = section.querySelector('.region-nav');
  if (!grid || !nav) return;
  const [prevBtn, nextBtn] = nav.querySelectorAll('button');
  const maxScroll = grid.scrollWidth - grid.clientWidth;

  if (maxScroll <= 4) {
    nav.style.display = 'none';
    return;
  }
  nav.style.display = '';
  prevBtn.disabled = grid.scrollLeft <= 4;
  nextBtn.disabled = grid.scrollLeft >= maxScroll - 4;
  grid.onscroll = () => {
    prevBtn.disabled = grid.scrollLeft <= 4;
    nextBtn.disabled = grid.scrollLeft >= maxScroll - 4;
  };
}

/* ----- ค้นหาสถานที่ท่องเที่ยวจากคำค้นหา (ใช้ได้ทั้งช่องค้นหาบน header และในหน้า) ----- */
function searchPlaces(keywordFromHero) {
  const input = document.getElementById('searchInput');
  const keyword = (keywordFromHero !== undefined ? keywordFromHero : (input ? input.value : ''))
    .trim().toLowerCase();
  const categorySelect = document.getElementById('categoryFilter');
  const category = categorySelect ? categorySelect.value : '';
  const regionSelect = document.getElementById('regionFilter');
  const region = regionSelect ? regionSelect.value : '';
  const sortSelect = document.getElementById('sortFilter');
  const sort = sortSelect ? sortSelect.value : '';

  const grid = document.getElementById('places');
  if (!grid) return;
  const cards = grid.querySelectorAll('.card');
  let found = 0;

  cards.forEach(card => {
    const name = (card.dataset.name || '').toLowerCase();
    const place = typeof PLACES !== 'undefined' ? getPlaceById(card.dataset.id) : null;
    const cardCategory = place ? place.category : '';
    const cardShort = place ? String(place.short || '').toLowerCase() : '';
    const cardProvince = place ? String(place.province || '').toLowerCase() : '';
    const cardRegion = place ? (getRegionByProvince(place.province) || 'อื่นๆ') : '';
    const keywordMatch = keyword === ''
      || name.includes(keyword)
      || cardCategory.toLowerCase().includes(keyword)
      || cardShort.includes(keyword)
      || cardProvince.includes(keyword);
    const categoryMatch = category === '' || cardCategory.includes(category);
    const regionMatch = region === '' || cardRegion === region;
    const match = keywordMatch && categoryMatch && regionMatch;
    card.style.display = match ? '' : 'none';
    if (match) found++;
  });

  // เรียงลำดับการ์ดที่ยังแสดงอยู่ (ถ้าหน้านั้นมีตัวเลือกการเรียง)
  if (sort) applySort(grid, sort);

  // ซ่อนหัวข้อภาคที่ไม่เหลือการ์ดที่ตรงเงื่อนไขเลยสักใบ (เผื่อหน้านี้แสดงแบบแยกภาค)
  grid.querySelectorAll('.region-section').forEach(section => {
    const hasVisibleCard = Array.from(section.querySelectorAll('.card'))
      .some(card => card.style.display !== 'none');
    section.style.display = hasVisibleCard ? '' : 'none';
    if (hasVisibleCard) updateRegionNavState(section);
  });

  let noResult = grid.querySelector('.no-result');
  if (found === 0) {
    if (!noResult) {
      noResult = document.createElement('p');
      noResult.className = 'no-result';
      grid.appendChild(noResult);
    }
    noResult.textContent = keyword
      ? `ไม่พบสถานที่ที่ตรงกับ "${keyword}" ลองเปลี่ยนคำค้นหาหรือล้างตัวกรองดูนะ`
      : 'ไม่พบสถานที่ที่ตรงกับตัวกรองที่เลือก';
  } else if (noResult) {
    noResult.remove();
  }
}

/* เรียงการ์ดตามตัวเลือก: ยอดนิยม / คะแนนรีวิว / ชื่อ */
function applySort(grid, sort) {
  const containers = grid.querySelectorAll('.region-grid').length
    ? grid.querySelectorAll('.region-grid')
    : [grid];

  containers.forEach(container => {
    const cards = Array.from(container.querySelectorAll('.card'));
    cards.sort((a, b) => {
      const pa = getPlaceById(a.dataset.id) || {};
      const pb = getPlaceById(b.dataset.id) || {};
      if (sort === 'popular') return (pb.likeCount || 0) - (pa.likeCount || 0);
      if (sort === 'rating') return (pb.ratingAverage || 0) - (pa.ratingAverage || 0);
      if (sort === 'name') return String(pa.name || '').localeCompare(String(pb.name || ''), 'th');
      return 0;
    });
    cards.forEach(c => container.appendChild(c));
  });
}

/* ----- ค้นหาจากช่องค้นหาใน Hero แล้วพาไปหน้ารายการสถานที่ ----- */
function heroSearchAndGo() {
  const heroInput = document.getElementById('heroSearchInput');
  const keyword = heroInput ? heroInput.value.trim() : '';
  window.location.href = 'places.html' + (keyword ? ('?q=' + encodeURIComponent(keyword)) : '');
}

/* ----- ช่องค้นหาบน header: กด Enter แล้วพาไปหน้ารายการสถานที่ ----- */
function headerSearchAndGo() {
  const input = document.getElementById('headerSearchInput');
  const keyword = input ? input.value.trim() : '';
  window.location.href = 'places.html' + (keyword ? ('?q=' + encodeURIComponent(keyword)) : '');
}

/* ==========================================================
   หน้ารายละเอียดสถานที่ (place-detail.html)
========================================================== */

async function renderPlaceDetail() {
  const container = document.getElementById('placeDetailRoot');
  if (!container || typeof PLACES === 'undefined') return;

  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  const place = getPlaceById(id);

  if (!place) {
    container.innerHTML = `
      <div class="not-found">
        <h2>ไม่พบสถานที่ที่คุณกำลังค้นหา</h2>
        <p>สถานที่นี้อาจถูกลบไปแล้ว หรือลิงก์ไม่ถูกต้อง</p>
        <a href="places.html"><button class="btn-primary">กลับไปหน้าแนะนำที่เที่ยว</button></a>
      </div>
    `;
    document.title = 'ไม่พบสถานที่ | เที่ยวมะ';
    return;
  }

  document.title = place.name + ' | เที่ยวมะ';
  // อัปเดต meta description / og ให้ตรงกับสถานที่นี้ (ช่วยตอนแชร์ลิงก์)
  setMeta('description', place.short);
  setMeta('og:title', place.name + ' | เที่ยวมะ', 'property');
  setMeta('og:description', place.short, 'property');

  const session = getSession();
  const isLiked = !!session && likedIds.has(place.id);
  const img = safeImageSrc(place.image);
  const heroInner = img
    ? pictureHTML(place.image, place.name, { width: 1200, height: 380, lazy: false })
    : `<div class="placeholder-label detail-placeholder">รูป: ${escapeHTML(place.name)}</div>`;

  const mapSrc = `https://www.google.com/maps?q=${encodeURIComponent(place.mapQuery || place.name)}&output=embed`;

  const related = PLACES.filter(p => p.id !== place.id && p.category === place.category).slice(0, 3);
  const relatedFallback = PLACES.filter(p => p.id !== place.id).slice(0, 3);
  const relatedList = related.length ? related : relatedFallback;

  const description = Array.isArray(place.description) ? place.description : [];
  const highlights = Array.isArray(place.highlights) ? place.highlights : [];

  container.innerHTML = `
    <nav class="detail-breadcrumb" aria-label="เส้นทางนำทาง">
      <a href="index.html">หน้าแรก</a> ›
      <a href="places.html">แนะนำที่เที่ยว</a> ›
      <span>${escapeHTML(place.name)}</span>
    </nav>

    <div class="detail-hero">${heroInner}</div>

    <div class="detail-header">
      <div>
        <span class="badge-category">${escapeHTML(place.category)}</span>
        <span class="badge-province">📍 ${escapeHTML(place.province)}</span>
        <h1>${escapeHTML(place.name)}</h1>
        <p class="detail-short">${escapeHTML(place.short)}</p>
        <div style="margin-top:8px;">${ratingBadgeHTML(place)}</div>
      </div>
      <div class="detail-actions">
        <button class="like-btn-lg ${isLiked ? 'liked' : ''}" id="detailLikeBtn"
                aria-pressed="${isLiked ? 'true' : 'false'}"
                onclick="toggleDetailLike('${escapeAttr(place.id)}')">
          ${isLiked ? '❤️ บันทึกแล้ว' : '🤍 บันทึกเป็นที่เที่ยวโปรด'}
        </button>
        <button class="btn-outline" onclick="sharePlace()">🔗 แชร์</button>
      </div>
    </div>

    <div class="detail-layout">
      <div class="detail-main">
        ${description.map(p => `<p>${escapeHTML(p)}</p>`).join('')}

        <h3 class="detail-subtitle">จุดเด่นที่ไม่ควรพลาด</h3>
        <ul class="highlight-list">
          ${highlights.map(h => `<li>✅ ${escapeHTML(h)}</li>`).join('')}
        </ul>

        <h3 class="detail-subtitle">แผนที่</h3>
        <div class="map-embed">
          <iframe src="${escapeAttr(mapSrc)}" width="100%" height="320" style="border:0;"
                  loading="lazy" title="แผนที่ ${escapeAttr(place.name)}"
                  referrerpolicy="no-referrer-when-downgrade"></iframe>
        </div>
      </div>

      <aside class="detail-sidebar">
        <div class="info-box">
          <h4>ข้อมูลสำหรับวางแผนทริป</h4>
          <div class="info-row"><span>🕒 เวลาเปิด-ปิด</span><p>${escapeHTML(place.hours || 'ไม่ระบุ')}</p></div>
          <div class="info-row"><span>💵 ค่าใช้จ่าย</span><p>${escapeHTML(place.fee || 'ไม่ระบุ')}</p></div>
          <div class="info-row"><span>📅 ช่วงเวลาแนะนำ</span><p>${escapeHTML(place.bestTime || 'ไม่ระบุ')}</p></div>
          <div class="info-row"><span>📍 จังหวัด</span><p>${escapeHTML(place.province)}</p></div>
          <div class="info-row"><span>💚 คนบันทึกไว้</span><p>${Number(place.likeCount) || 0} คน</p></div>
        </div>
      </aside>
    </div>

    <section class="review-section" id="reviewSection" aria-label="รีวิวจากผู้เดินทาง">
      <h3 class="section-title" style="text-align:left;">รีวิวจากผู้เดินทาง</h3>
      <div id="reviewRoot">กำลังโหลดรีวิว...</div>
    </section>

    <h3 class="section-title" style="margin-top:40px;">สถานที่ใกล้เคียงที่น่าสนใจ</h3>
    <section class="places-grid" id="relatedPlaces"></section>
  `;

  renderPlacesGrid('relatedPlaces', relatedList);
  renderReviews(place.id);
}

/* อัปเดต meta tag แบบไดนามิก (ใช้ในหน้ารายละเอียด) */
function setMeta(name, content, attr = 'name') {
  let el = document.querySelector(`meta[${attr}="${name}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, name);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content || '');
}

/* แชร์หน้านี้ — ใช้ Web Share API บนมือถือ ถ้าไม่รองรับก็คัดลอกลิงก์ */
async function sharePlace() {
  const url = window.location.href;
  const title = document.title;
  if (navigator.share) {
    try {
      await navigator.share({ title, url });
      return;
    } catch (e) { /* ผู้ใช้กดยกเลิก */ }
  }
  try {
    await navigator.clipboard.writeText(url);
    toast('คัดลอกลิงก์แล้ว นำไปแชร์ได้เลย', 'success');
  } catch (e) {
    toast('คัดลอกลิงก์ไม่สำเร็จ กรุณาคัดลอกจากแถบที่อยู่ของเบราว์เซอร์', 'error');
  }
}

async function toggleDetailLike(placeId) {
  if (!currentUser) {
    requireLogin('place-detail.html?id=' + placeId);
    return;
  }
  const btn = document.getElementById('detailLikeBtn');
  if (btn) btn.disabled = true;
  try {
    const nowLiked = await setLike(placeId, !likedIds.has(placeId));
    if (btn) {
      btn.classList.toggle('liked', nowLiked);
      btn.setAttribute('aria-pressed', nowLiked ? 'true' : 'false');
      btn.innerHTML = nowLiked ? '❤️ บันทึกแล้ว' : '🤍 บันทึกเป็นที่เที่ยวโปรด';
    }
    toast(nowLiked ? 'บันทึกเป็นที่เที่ยวโปรดแล้ว' : 'นำออกจากที่เที่ยวโปรดแล้ว', 'success', 2000);
  } catch (e) {
    handleLikeError(e, 'place-detail.html?id=' + placeId);
  } finally {
    if (btn) btn.disabled = false;
  }
}

/* ==========================================================
   รีวิวและให้ดาว (แสดงในหน้ารายละเอียดสถานที่)
========================================================== */

let reviewDraftRating = 0;

async function renderReviews(placeId) {
  const root = document.getElementById('reviewRoot');
  if (!root) return;

  let data;
  try {
    data = await api(`/places/${encodeURIComponent(placeId)}/reviews`);
  } catch (e) {
    root.innerHTML = `<p class="no-result">โหลดรีวิวไม่สำเร็จ: ${escapeHTML(e.message)}</p>`;
    return;
  }

  const mine = (data.items || []).find(r => r.mine);
  reviewDraftRating = mine ? mine.rating : 0;

  const summary = data.count
    ? `<div class="review-summary">
         <div class="big-score">${Number(data.average).toFixed(1)}</div>
         <div>
           <div class="stars" style="color:#f2a900;font-size:1.1rem;">${starsHTML(data.average)}</div>
           <div style="font-size:.85rem;color:var(--c-muted);">จาก ${data.count} รีวิว</div>
         </div>
       </div>`
    : `<p class="no-result" style="padding:14px 0;">ยังไม่มีรีวิวสำหรับที่นี่ — มาเป็นคนแรกกันเถอะ</p>`;

  const form = currentUser
    ? `<form class="review-form" onsubmit="return submitReview(event, '${escapeAttr(placeId)}')">
         <label style="font-weight:600;font-size:.92rem;display:block;margin-bottom:8px;">
           ${mine ? 'แก้ไขรีวิวของคุณ' : 'ให้คะแนนและเขียนรีวิว'}
         </label>
         <div class="star-picker" id="starPicker" role="radiogroup" aria-label="ให้คะแนน 1 ถึง 5 ดาว">
           ${[1, 2, 3, 4, 5].map(n => `
             <button type="button" role="radio" aria-checked="${reviewDraftRating === n}"
                     aria-label="${n} ดาว" data-star="${n}"
                     class="${n <= reviewDraftRating ? 'on' : ''}"
                     onclick="pickStar(${n})">⭐</button>`).join('')}
         </div>
         <textarea id="reviewComment" maxlength="1000"
                   placeholder="เล่าประสบการณ์ของคุณให้คนอื่นฟังหน่อย (ไม่บังคับ)">${mine ? escapeHTML(mine.comment || '') : ''}</textarea>
         <div style="display:flex;gap:10px;flex-wrap:wrap;">
           <button type="submit" class="btn-primary">${mine ? 'บันทึกการแก้ไข' : 'ส่งรีวิว'}</button>
           ${mine ? `<button type="button" class="review-delete" onclick="deleteReview('${escapeAttr(placeId)}', ${mine.id})">ลบรีวิวของฉัน</button>` : ''}
         </div>
       </form>`
    : `<div class="review-form" style="text-align:center;">
         <p style="color:var(--c-muted);margin-bottom:12px;">เข้าสู่ระบบเพื่อให้คะแนนและเขียนรีวิวสถานที่นี้</p>
         <a href="login.html?redirect=${encodeURIComponent('place-detail.html?id=' + placeId)}">
           <button type="button" class="btn-primary">เข้าสู่ระบบ</button>
         </a>
       </div>`;

  const list = (data.items || []).length
    ? `<div class="review-list">${data.items.map(reviewItemHTML.bind(null, placeId)).join('')}</div>`
    : '';

  root.innerHTML = summary + form + list;
}

function reviewItemHTML(placeId, r) {
  const when = r.createdAt
    ? new Date(r.createdAt).toLocaleDateString('th-TH', { day: 'numeric', month: 'short', year: 'numeric' })
    : '';
  const edited = r.updatedAt ? ' (แก้ไขแล้ว)' : '';
  const canDelete = r.mine || (currentUser && currentUser.role === 'ADMIN');
  return `
    <article class="review-item">
      <div class="review-head">
        <span class="who">${escapeHTML(r.authorName)}${r.mine ? ' (คุณ)' : ''}</span>
        <span class="stars" aria-label="${r.rating} ดาว">${starsHTML(r.rating)}</span>
      </div>
      <div class="when">${escapeHTML(when)}${edited}</div>
      ${r.comment ? `<p>${escapeHTML(r.comment)}</p>` : ''}
      ${canDelete ? `<button type="button" class="review-delete"
                        onclick="deleteReview('${escapeAttr(placeId)}', ${r.id})">ลบรีวิวนี้</button>` : ''}
    </article>`;
}

function pickStar(n) {
  reviewDraftRating = n;
  document.querySelectorAll('#starPicker button').forEach(btn => {
    const v = Number(btn.dataset.star);
    btn.classList.toggle('on', v <= n);
    btn.setAttribute('aria-checked', v === n ? 'true' : 'false');
  });
}

async function submitReview(event, placeId) {
  event.preventDefault();
  if (!reviewDraftRating) {
    toast('กรุณาเลือกคะแนนดาวก่อนส่งรีวิว', 'error');
    return false;
  }
  const comment = document.getElementById('reviewComment').value;
  const btn = event.target.querySelector('button[type="submit"]');
  if (btn) btn.disabled = true;
  try {
    await api(`/places/${encodeURIComponent(placeId)}/reviews`, {
      method: 'PUT',
      body: JSON.stringify({ rating: reviewDraftRating, comment })
    });
    toast('บันทึกรีวิวเรียบร้อยแล้ว ขอบคุณที่แบ่งปัน', 'success');
    await loadPlaces();               // อัปเดตคะแนนเฉลี่ยในหน้า
    await renderReviews(placeId);
  } catch (e) {
    if (e.status === 401) {
      requireLogin('place-detail.html?id=' + placeId);
    } else {
      toast(e.message, 'error');
    }
  } finally {
    if (btn) btn.disabled = false;
  }
  return false;
}

async function deleteReview(placeId, reviewId) {
  const ok = await confirmDialog({
    title: 'ลบรีวิว',
    message: 'ต้องการลบรีวิวนี้ใช่ไหม? การลบไม่สามารถย้อนกลับได้',
    confirmText: 'ลบรีวิว',
    danger: true
  });
  if (!ok) return;
  try {
    await api(`/places/${encodeURIComponent(placeId)}/reviews/${reviewId}`, { method: 'DELETE' });
    toast('ลบรีวิวแล้ว', 'success');
    await loadPlaces();
    await renderReviews(placeId);
  } catch (e) {
    toast(e.message, 'error');
  }
}

/* ==========================================================
   หน้าที่เที่ยวโปรดของฉัน (liked.html)
========================================================== */

function renderLikedPlaces() {
  const root = document.getElementById('likedRoot');
  if (!root || typeof PLACES === 'undefined') return;

  const session = getSession();

  if (!session) {
    root.innerHTML = `
      <div class="not-found">
        <h2>กรุณาเข้าสู่ระบบ</h2>
        <p>เข้าสู่ระบบเพื่อดูรายการที่เที่ยวโปรดที่คุณบันทึกไว้</p>
        <a href="login.html?redirect=liked.html"><button class="btn-primary">ไปหน้าเข้าสู่ระบบ</button></a>
      </div>
    `;
    return;
  }

  const likedPlaces = PLACES.filter(p => likedIds.has(p.id));

  if (likedPlaces.length === 0) {
    root.innerHTML = `
      <div class="not-found">
        <h2>ยังไม่มีที่เที่ยวโปรด</h2>
        <p>กดปุ่มหัวใจ 🤍 ที่การ์ดสถานที่ท่องเที่ยวเพื่อบันทึกไว้ดูภายหลัง</p>
        <a href="places.html"><button class="btn-primary">ไปเลือกที่เที่ยว</button></a>
      </div>
    `;
    return;
  }

  root.innerHTML = `
    <p style="text-align:center;color:var(--c-muted);margin-bottom:18px;">
      บันทึกไว้ทั้งหมด ${likedPlaces.length} แห่ง
    </p>
    <section class="places-grid" id="likedGrid"></section>`;
  renderPlacesGrid('likedGrid', likedPlaces);
}

/* ==========================================================
   หน้าบทความ (articles.html) — โหลดจาก API
========================================================== */

let ARTICLES_CACHE = [];

async function renderArticles() {
  const list = document.getElementById('articleList');
  if (!list) return;

  list.innerHTML = skeletonCards(3);

  try {
    ARTICLES_CACHE = await api('/articles');
  } catch (e) {
    list.innerHTML = `<p class="no-result">โหลดบทความไม่สำเร็จ: ${escapeHTML(e.message)}</p>`;
    return;
  }

  renderArticleFilters();
  paintArticleList(ARTICLES_CACHE);
}

function renderArticleFilters() {
  const box = document.getElementById('articleFilters');
  if (!box) return;
  const categories = [...new Set(ARTICLES_CACHE.map(a => a.category))].sort();
  box.innerHTML = `
    <button type="button" class="chip active" onclick="filterArticles('', this)">ทั้งหมด</button>
    ${categories.map(c => `
      <button type="button" class="chip" onclick="filterArticles('${escapeAttr(c)}', this)">${escapeHTML(c)}</button>
    `).join('')}`;
}

function filterArticles(category, btn) {
  document.querySelectorAll('#articleFilters .chip').forEach(c => c.classList.remove('active'));
  if (btn) btn.classList.add('active');
  paintArticleList(category ? ARTICLES_CACHE.filter(a => a.category === category) : ARTICLES_CACHE);
}

function paintArticleList(articles) {
  const list = document.getElementById('articleList');
  if (!list) return;

  if (!articles.length) {
    list.innerHTML = `<p class="no-result">ยังไม่มีบทความในหมวดนี้</p>`;
    return;
  }

  list.innerHTML = articles.map(a => {
    const date = formatThaiDate(a.publishedAt);
    const img = safeImageSrc(a.image);
    const thumb = img
      ? pictureHTML(a.image, a.title, { width: 220, height: 150 })
      : '';
    const href = `article-detail.html?id=${encodeURIComponent(a.id)}`;
    return `
      <article class="article-item" onclick="window.location.href='${href}'">
        <div class="thumb-sm">${thumb}</div>
        <div class="content">
          <h3><a href="${href}">${escapeHTML(a.title)}</a></h3>
          <p>${escapeHTML(a.summary)}</p>
          <div class="meta">เผยแพร่ ${escapeHTML(date)} · หมวดหมู่: ${escapeHTML(a.category)} · อ่าน ${a.readMinutes || 1} นาที</div>
          <a class="read-more" href="${href}">อ่านบทความ ›</a>
        </div>
      </article>`;
  }).join('');
}

function formatThaiDate(value) {
  if (!value) return '';
  const d = new Date(String(value).length <= 10 ? value + 'T00:00:00' : value);
  if (isNaN(d.getTime())) return String(value);
  return d.toLocaleDateString('th-TH', { day: 'numeric', month: 'short', year: 'numeric' });
}

/* ==========================================================
   หน้ารายละเอียดบทความ (article-detail.html)
========================================================== */

async function renderArticleDetail() {
  const root = document.getElementById('articleDetailRoot');
  if (!root) return;

  const id = new URLSearchParams(window.location.search).get('id');
  if (!id) {
    root.innerHTML = notFoundHTML('ไม่พบบทความ', 'ลิงก์ไม่ถูกต้อง', 'articles.html', 'กลับไปหน้าบทความ');
    return;
  }

  root.innerHTML = `<div class="skeleton-card"><div class="sk-thumb" style="height:300px;"></div>
    <div class="sk-body"><div class="sk-line"></div><div class="sk-line"></div><div class="sk-line short"></div></div></div>`;

  let a;
  try {
    a = await api('/articles/' + encodeURIComponent(id));
  } catch (e) {
    root.innerHTML = notFoundHTML('ไม่พบบทความนี้', e.message, 'articles.html', 'กลับไปหน้าบทความ');
    document.title = 'ไม่พบบทความ | เที่ยวมะ';
    return;
  }

  document.title = a.title + ' | เที่ยวมะ';
  setMeta('description', a.summary);
  setMeta('og:title', a.title + ' | เที่ยวมะ', 'property');
  setMeta('og:description', a.summary, 'property');

  const img = safeImageSrc(a.image);
  const hero = img
    ? `<div class="article-hero">${pictureHTML(a.image, a.title, { width: 800, height: 340, lazy: false })}</div>`
    : '';

  // เนื้อหาเก็บเป็นข้อความธรรมดา ตัดเป็นย่อหน้าตามบรรทัดว่าง แล้ว escape ทุกย่อหน้า
  const paragraphs = String(a.content || '')
    .split(/\n\s*\n/)
    .map(p => p.trim())
    .filter(Boolean);

  const body = paragraphs.length
    ? paragraphs.map(p => `<p>${escapeHTML(p).replace(/\n/g, '<br>')}</p>`).join('')
    : `<p>${escapeHTML(a.summary)}</p>`;

  const related = (a.related || []).length
    ? `<h3 class="section-title" style="margin-top:44px;">บทความที่เกี่ยวข้อง</h3>
       <div class="article-list">
         ${a.related.map(r => {
           const rimg = safeImageSrc(r.image);
           const rhref = `article-detail.html?id=${encodeURIComponent(r.id)}`;
           return `
             <article class="article-item" onclick="window.location.href='${rhref}'">
               <div class="thumb-sm">${pictureHTML(r.image, r.title)}</div>
               <div class="content">
                 <h3><a href="${rhref}">${escapeHTML(r.title)}</a></h3>
                 <p>${escapeHTML(r.summary)}</p>
                 <div class="meta">เผยแพร่ ${escapeHTML(formatThaiDate(r.publishedAt))}</div>
               </div>
             </article>`;
         }).join('')}
       </div>`
    : '';

  root.innerHTML = `
    <article class="article-detail">
      <nav class="detail-breadcrumb" aria-label="เส้นทางนำทาง">
        <a href="index.html">หน้าแรก</a> ›
        <a href="articles.html">บทความ</a> ›
        <span>${escapeHTML(a.title)}</span>
      </nav>
      ${hero}
      <h1>${escapeHTML(a.title)}</h1>
      <div class="article-meta">
        เผยแพร่ ${escapeHTML(formatThaiDate(a.publishedAt))} ·
        หมวดหมู่: ${escapeHTML(a.category)} ·
        อ่านประมาณ ${a.readMinutes || 1} นาที
        <button class="btn-outline" style="float:right;padding:5px 14px;font-size:.8rem;"
                onclick="sharePlace()">🔗 แชร์</button>
      </div>
      <div class="article-body">${body}</div>
      ${related}
    </article>`;
}

function notFoundHTML(title, message, href, label) {
  return `
    <div class="not-found">
      <h2>${escapeHTML(title)}</h2>
      <p>${escapeHTML(message)}</p>
      <a href="${href}"><button class="btn-primary">${escapeHTML(label)}</button></a>
    </div>`;
}

/* ==========================================================
   หน้าโปรไฟล์ (profile.html)
========================================================== */

async function renderProfile() {
  const root = document.getElementById('profileRoot');
  if (!root) return;

  if (!currentUser) {
    root.innerHTML = notFoundHTML('กรุณาเข้าสู่ระบบ',
      'เข้าสู่ระบบเพื่อจัดการบัญชีของคุณ', 'login.html?redirect=profile.html', 'ไปหน้าเข้าสู่ระบบ');
    return;
  }

  let profile;
  try {
    profile = await api('/account');
  } catch (e) {
    root.innerHTML = notFoundHTML('โหลดข้อมูลไม่สำเร็จ', e.message, 'index.html', 'กลับหน้าแรก');
    return;
  }

  const initial = escapeHTML(String(profile.name || '?').trim().charAt(0).toUpperCase());
  const joined = profile.createdAt ? formatThaiDate(profile.createdAt) : '-';

  root.innerHTML = `
    <div class="profile-wrap">
      <div class="profile-card">
        <div class="profile-head">
          <div class="profile-avatar" aria-hidden="true">${initial}</div>
          <div>
            <h3 style="margin-bottom:4px;">${escapeHTML(profile.name)}</h3>
            <div style="font-size:.88rem;color:var(--c-muted);">${escapeHTML(profile.email)}</div>
            <div style="margin-top:6px;">
              <span class="profile-role-badge ${profile.role === 'ADMIN' ? 'admin' : ''}">
                ${profile.role === 'ADMIN' ? 'ผู้ดูแลระบบ' : 'สมาชิก'}
              </span>
            </div>
          </div>
        </div>
        <p class="card-hint" style="margin-top:16px;">
          เป็นสมาชิกตั้งแต่ ${escapeHTML(joined)} · บันทึกที่เที่ยวโปรดไว้ ${Number(profile.likeCount) || 0} แห่ง
        </p>
      </div>

      <div class="profile-card">
        <h3>แก้ไขชื่อที่แสดง</h3>
        <p class="card-hint">ชื่อนี้จะแสดงในรีวิวที่คุณเขียนและบนแถบเมนู</p>
        <div class="form-success" id="nameSuccess"></div>
        <div class="form-error" id="nameError"></div>
        <form onsubmit="return saveProfileName(event)">
          <div class="form-group">
            <label for="profileName">ชื่อ-นามสกุล</label>
            <input type="text" id="profileName" maxlength="100" value="${escapeAttr(profile.name)}">
          </div>
          <button type="submit" class="btn-primary">บันทึกชื่อ</button>
        </form>
      </div>

      <div class="profile-card">
        <h3>เปลี่ยนรหัสผ่าน</h3>
        <p class="card-hint">หลังเปลี่ยนรหัสผ่าน ระบบจะให้เข้าสู่ระบบใหม่เพื่อความปลอดภัย</p>
        <div class="form-success" id="pwSuccess"></div>
        <div class="form-error" id="pwError"></div>
        <form onsubmit="return changePassword(event)">
          <div class="form-group">
            <label for="curPassword">รหัสผ่านปัจจุบัน</label>
            <input type="password" id="curPassword" autocomplete="current-password">
          </div>
          <div class="form-group">
            <label for="newPassword">รหัสผ่านใหม่</label>
            <input type="password" id="newPassword" autocomplete="new-password" placeholder="อย่างน้อย 8 ตัวอักษร มีตัวเลขผสม">
            <div class="password-meter" id="pwMeter"></div>
          </div>
          <div class="form-group">
            <label for="newPassword2">ยืนยันรหัสผ่านใหม่</label>
            <input type="password" id="newPassword2" autocomplete="new-password">
          </div>
          <button type="submit" class="btn-primary">เปลี่ยนรหัสผ่าน</button>
        </form>
      </div>

      <div class="profile-card danger">
        <h3>ลบบัญชีถาวร</h3>
        <p class="card-hint">
          การลบบัญชีจะลบข้อมูลที่เที่ยวโปรดและรีวิวทั้งหมดของคุณอย่างถาวร และไม่สามารถกู้คืนได้
        </p>
        <div class="form-error" id="delError"></div>
        <form onsubmit="return deleteAccount(event)">
          <div class="form-group">
            <label for="delPassword">กรอกรหัสผ่านเพื่อยืนยัน</label>
            <input type="password" id="delPassword" autocomplete="current-password">
          </div>
          <button type="submit" class="btn-danger">ลบบัญชีของฉัน</button>
        </form>
      </div>
    </div>`;

  renderPasswordStrength('newPassword', 'pwMeter');
}

async function saveProfileName(event) {
  event.preventDefault();
  const name = document.getElementById('profileName').value.trim();
  const errorBox = document.getElementById('nameError');
  const successBox = document.getElementById('nameSuccess');
  hideBox(errorBox); hideBox(successBox);

  if (!name) return showBox(errorBox, 'กรุณากรอกชื่อ'), false;

  try {
    const res = await api('/account/profile', { method: 'PUT', body: JSON.stringify({ name }) });
    showBox(successBox, res.message);
    if (currentUser) currentUser.name = res.name;
    renderAuthHeader();
    toast('บันทึกชื่อใหม่แล้ว', 'success');
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

async function changePassword(event) {
  event.preventDefault();
  const currentPassword = document.getElementById('curPassword').value;
  const newPassword = document.getElementById('newPassword').value;
  const confirm2 = document.getElementById('newPassword2').value;
  const errorBox = document.getElementById('pwError');
  const successBox = document.getElementById('pwSuccess');
  hideBox(errorBox); hideBox(successBox);

  if (!currentPassword) return showBox(errorBox, 'กรุณากรอกรหัสผ่านปัจจุบัน'), false;
  const strength = checkPasswordStrength(newPassword);
  if (!strength.ok) return showBox(errorBox, strength.message), false;
  if (newPassword !== confirm2) return showBox(errorBox, 'รหัสผ่านใหม่ทั้งสองช่องไม่ตรงกัน'), false;

  try {
    const res = await api('/account/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword })
    });
    showBox(successBox, res.message);
    setTimeout(() => { window.location.href = 'login.html'; }, 2000);
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

async function deleteAccount(event) {
  event.preventDefault();
  const password = document.getElementById('delPassword').value;
  const errorBox = document.getElementById('delError');
  hideBox(errorBox);

  if (!password) return showBox(errorBox, 'กรุณากรอกรหัสผ่านเพื่อยืนยัน'), false;

  const ok = await confirmDialog({
    title: 'ยืนยันการลบบัญชี',
    message: 'ข้อมูลทั้งหมดของคุณจะถูกลบถาวรและกู้คืนไม่ได้ ต้องการดำเนินการต่อใช่ไหม?',
    confirmText: 'ลบบัญชีถาวร',
    cancelText: 'ยกเลิก',
    danger: true
  });
  if (!ok) return false;

  try {
    await api('/account', { method: 'DELETE', body: JSON.stringify({ password }) });
    window.location.href = 'index.html';
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

/* ==========================================================
   การเริ่มทำงานเมื่อโหลดหน้าเว็บ (ทุกหน้า)
========================================================== */

/* หน้าไหนต้องใช้ข้อมูลสถานที่บ้าง — หน้าที่ไม่ต้องใช้จะได้ไม่ยิง API เปล่าๆ */
function pageNeedsPlaces() {
  return !!(document.getElementById('places')
    || document.getElementById('placeDetailRoot')
    || document.getElementById('likedRoot')
    || document.getElementById('adminRoot'));
}

document.addEventListener('DOMContentLoaded', async () => {
  // แสดง skeleton ทันทีก่อนข้อมูลมา (กันหน้าโล่ง)
  const grid = document.getElementById('places');
  if (grid) grid.innerHTML = skeletonCards(6);

  // รู้ก่อนว่าใครล็อกอินอยู่ (และมีที่เที่ยวโปรดอะไรบ้าง) กับโหลดรายการสถานที่ แล้วค่อยวาดหน้า
  const tasks = [initSession()];
  if (pageNeedsPlaces()) tasks.push(loadPlaces());
  await Promise.all(tasks);

  renderAuthHeader();

  // แจ้งหน้าที่ต้องรอ session/PLACES พร้อมก่อนวาดผล (เช่น admin.html) ว่าโหลดเสร็จแล้ว
  window.dispatchEvent(new CustomEvent('tiaoma:session-ready'));

  // หน้าแรก: แสดงสถานที่แนะนำ 6 อันดับแรก
  if (grid && grid.dataset.mode === 'home' && typeof PLACES !== 'undefined') {
    renderPlacesGrid('places', PLACES.slice(0, 6));
  }
  // หน้าแนะนำที่เที่ยวทั้งหมด
  if (grid && grid.dataset.mode === 'all' && typeof PLACES !== 'undefined') {
    renderPlacesGroupedByRegion('places', PLACES);
  }

  syncLikedIcons();

  renderPlaceDetail();      // หน้ารายละเอียดสถานที่
  renderLikedPlaces();      // หน้าที่เที่ยวโปรด
  renderArticles();         // หน้ารายการบทความ
  renderArticleDetail();    // หน้ารายละเอียดบทความ
  renderProfile();          // หน้าโปรไฟล์

  // แถบความแข็งแรงรหัสผ่านในหน้าสมัคร/ตั้งรหัสใหม่
  renderPasswordStrength('regPassword', 'regPwMeter');
  renderPasswordStrength('resetPassword', 'resetPwMeter');

  bindSearchInputs();
});

/* ผูกเหตุการณ์ของช่องค้นหาทุกช่อง — รวมถึงการกด Enter ที่เดิมใช้ไม่ได้ */
function bindSearchInputs() {
  // ช่องค้นหาบน header (มีทุกหน้า) — เดิมกด Enter แล้วไม่เกิดอะไรขึ้น ต้องคลิกปุ่ม 🔍 เท่านั้น
  const headerInput = document.getElementById('headerSearchInput');
  if (headerInput) {
    headerInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); headerSearchAndGo(); }
    });
  }

  // ช่องค้นหาใน hero ของหน้าแรก — เดิมกด Enter ก็ไม่ทำงานเช่นกัน
  const heroInput = document.getElementById('heroSearchInput');
  if (heroInput) {
    heroInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); heroSearchAndGo(); }
    });
  }

  // ช่องค้นหาในหน้ารายการสถานที่
  const searchInput = document.getElementById('searchInput');
  const params = new URLSearchParams(window.location.search);
  const q = params.get('q');
  if (searchInput && q) {
    searchInput.value = q;
    searchPlaces(q);
  }
  if (searchInput) {
    searchInput.addEventListener('input', () => searchPlaces());
    searchInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); searchPlaces(); }
    });
  }

  ['categoryFilter', 'regionFilter', 'sortFilter'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('change', () => searchPlaces());
  });

  const clearBtn = document.getElementById('clearFilters');
  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      ['searchInput', 'categoryFilter', 'regionFilter', 'sortFilter'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
      });
      searchPlaces();
    });
  }
}
