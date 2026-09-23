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

/* ป้องกัน HTML injection เวลาแทรกชื่อผู้ใช้ลงใน innerHTML */
function escapeHTML(text) {
  return String(text).replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
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
   เรียกตอนโหลดหน้า และเรียกซ้ำได้จากหน้าจัดการเนื้อหา (admin.html) หลังเพิ่ม/แก้ไข/ลบสถานที่ */
async function loadPlaces() {
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
  if (password.length < 6) return showBox(errorBox, 'รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร'), false;
  if (password !== confirm) return showBox(errorBox, 'รหัสผ่านทั้งสองช่องไม่ตรงกัน'), false;

  try {
    await api('/auth/register', { method: 'POST', body: JSON.stringify({ name, email, password }) });
  } catch (e) {
    showBox(errorBox, e.message);
    return false;
  }

  showBox(successBox, `สมัครสมาชิกสำเร็จ! ยินดีต้อนรับคุณ ${name} — กำลังพาไปหน้าเข้าสู่ระบบ...`);
  setTimeout(() => { window.location.href = 'login.html'; }, 1500);
  return false;
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

  let user;
  try {
    user = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
  } catch (e) {
    showBox(errorBox, e.message);
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
  if (password.length < 6) return showBox(errorBox, 'รหัสผ่านต้องมีอย่างน้อย 6 ตัวอักษร'), false;
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
  try { await api('/auth/logout', { method: 'POST' }); } catch (e) { /* ออกจากหน้าอยู่ดี */ }
  window.location.href = 'index.html';
}

/* ----- บังคับให้เข้าสู่ระบบก่อนบันทึกที่เที่ยวโปรด ----- */
function requireLogin(nextUrl) {
  const goTo = nextUrl || (window.location.pathname.split('/').pop() + window.location.search);
  const ok = confirm('ต้องเข้าสู่ระบบก่อนจึงจะบันทึกที่เที่ยวโปรดได้ ต้องการไปหน้าเข้าสู่ระบบตอนนี้เลยไหม?');
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
    alert(e.message);
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
    if (btn) btn.textContent = liked ? '❤️' : '🤍';
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
      <span class="user-chip">สวัสดี, ${escapeHTML(session.name.split(' ')[0])}</span>
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

function placeCardHTML(place) {
  const imageInner = place.image
    ? `<img src="${place.image}" alt="${place.name}" loading="lazy">`
    : `<div class="placeholder-label">รูป: ${place.name}</div>`;
  return `
    <div class="card" data-name="${place.name}" data-id="${place.id}">
      <div class="thumb">${imageInner}</div>
      <div class="card-body">
        <button class="like-btn" onclick="toggleLike(this)">🤍</button>
        <span class="badge-category">${place.category}</span>
        <h3>${place.name}</h3>
        <p>${place.short}</p>
        <a class="read-more" href="place-detail.html?id=${place.id}">อ่านเพิ่มเติม ›</a>
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
    <div class="region-section" data-region="${region}">
      <div class="region-header">
        <div class="region-title-group">
          <span class="region-icon">${REGION_ICONS[region] || '📍'}</span>
          <h3 class="region-title">${REGION_LABELS[region] || region}</h3>
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

  const grid = document.getElementById('places');
  if (!grid) return;
  const cards = grid.querySelectorAll('.card');
  let found = 0;

  cards.forEach(card => {
    const name = (card.dataset.name || '').toLowerCase();
    const place = typeof PLACES !== 'undefined' ? getPlaceById(card.dataset.id) : null;
    const cardCategory = place ? place.category : '';
    const cardShort = place ? place.short.toLowerCase() : '';
    const cardProvince = place ? place.province.toLowerCase() : '';
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
    noResult.textContent = `ไม่พบสถานที่ที่ตรงกับ "${keyword}"`;
  } else if (noResult) {
    noResult.remove();
  }
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

function renderPlaceDetail() {
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

  const session = getSession();
  const isLiked = !!session && likedIds.has(place.id);
  const heroInner = place.image
    ? `<img src="${place.image}" alt="${place.name}">`
    : `<div class="placeholder-label detail-placeholder">รูป: ${place.name}</div>`;

  const mapSrc = `https://www.google.com/maps?q=${encodeURIComponent(place.mapQuery)}&output=embed`;

  const related = PLACES.filter(p => p.id !== place.id && p.category === place.category).slice(0, 3);
  const relatedFallback = PLACES.filter(p => p.id !== place.id).slice(0, 3);
  const relatedList = related.length ? related : relatedFallback;

  container.innerHTML = `
    <div class="detail-breadcrumb">
      <a href="index.html">หน้าแรก</a> ›
      <a href="places.html">แนะนำที่เที่ยว</a> ›
      <span>${place.name}</span>
    </div>

    <div class="detail-hero">${heroInner}</div>

    <div class="detail-header">
      <div>
        <span class="badge-category">${place.category}</span>
        <span class="badge-province">📍 ${place.province}</span>
        <h1>${place.name}</h1>
        <p class="detail-short">${place.short}</p>
      </div>
      <button class="like-btn-lg ${isLiked ? 'liked' : ''}" id="detailLikeBtn" onclick="toggleDetailLike('${place.id}')">
        ${isLiked ? '❤️ บันทึกแล้ว' : '🤍 บันทึกเป็นที่เที่ยวโปรด'}
      </button>
    </div>

    <div class="detail-layout">
      <div class="detail-main">
        ${place.description.map(p => `<p>${p}</p>`).join('')}

        <h3 class="detail-subtitle">จุดเด่นที่ไม่ควรพลาด</h3>
        <ul class="highlight-list">
          ${place.highlights.map(h => `<li>✅ ${h}</li>`).join('')}
        </ul>

        <h3 class="detail-subtitle">แผนที่</h3>
        <div class="map-embed">
          <iframe src="${mapSrc}" width="100%" height="320" style="border:0;" loading="lazy"></iframe>
        </div>
      </div>

      <aside class="detail-sidebar">
        <div class="info-box">
          <h4>ข้อมูลสำหรับวางแผนทริป</h4>
          <div class="info-row"><span>🕒 เวลาเปิด-ปิด</span><p>${place.hours}</p></div>
          <div class="info-row"><span>💵 ค่าใช้จ่าย</span><p>${place.fee}</p></div>
          <div class="info-row"><span>📅 ช่วงเวลาแนะนำ</span><p>${place.bestTime}</p></div>
          <div class="info-row"><span>📍 จังหวัด</span><p>${place.province}</p></div>
        </div>
      </aside>
    </div>

    <h3 class="section-title" style="margin-top:40px;">สถานที่ใกล้เคียงที่น่าสนใจ</h3>
    <section class="places-grid" id="relatedPlaces"></section>
  `;

  renderPlacesGrid('relatedPlaces', relatedList);
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
      btn.innerHTML = nowLiked ? '❤️ บันทึกแล้ว' : '🤍 บันทึกเป็นที่เที่ยวโปรด';
    }
  } catch (e) {
    handleLikeError(e, 'place-detail.html?id=' + placeId);
  } finally {
    if (btn) btn.disabled = false;
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

  root.innerHTML = `<section class="places-grid" id="likedGrid"></section>`;
  renderPlacesGrid('likedGrid', likedPlaces);
}

/* ==========================================================
   การเริ่มทำงานเมื่อโหลดหน้าเว็บ (ทุกหน้า)
========================================================== */

document.addEventListener('DOMContentLoaded', async () => {
  // รู้ก่อนว่าใครล็อกอินอยู่ (และมีที่เที่ยวโปรดอะไรบ้าง) กับโหลดรายการสถานที่ แล้วค่อยวาดหน้า
  await Promise.all([initSession(), loadPlaces()]);
  renderAuthHeader();

  // แจ้งหน้าที่ต้องรอ session/PLACES พร้อมก่อนวาดผล (เช่น admin.html) ว่าโหลดเสร็จแล้ว
  window.dispatchEvent(new CustomEvent('tiaoma:session-ready'));

  // หน้าแรก: แสดงสถานที่แนะนำ 6 อันดับแรก
  const homeGrid = document.getElementById('places');
  if (homeGrid && homeGrid.dataset.mode === 'home' && typeof PLACES !== 'undefined') {
    renderPlacesGrid('places', PLACES.slice(0, 6));
  }
  // หน้าแนะนำที่เที่ยวทั้งหมด
  if (homeGrid && homeGrid.dataset.mode === 'all' && typeof PLACES !== 'undefined') {
    renderPlacesGroupedByRegion('places', PLACES);
  }

  syncLikedIcons();

  // หน้ารายละเอียดสถานที่
  renderPlaceDetail();

  // หน้าที่เที่ยวโปรด
  renderLikedPlaces();

  // ช่องค้นหาในหน้าแนะนำที่เที่ยว
  const searchInput = document.getElementById('searchInput');
  const params = new URLSearchParams(window.location.search);
  const q = params.get('q');
  if (searchInput && q) {
    searchInput.value = q;
    searchPlaces(q);
  }
  if (searchInput) {
    searchInput.addEventListener('input', () => searchPlaces());
    searchInput.addEventListener('keyup', (e) => {
      if (e.key === 'Enter') searchPlaces();
    });
  }
  const categoryFilter = document.getElementById('categoryFilter');
  if (categoryFilter) {
    categoryFilter.addEventListener('change', () => searchPlaces());
  }
  const regionFilter = document.getElementById('regionFilter');
  if (regionFilter) {
    regionFilter.addEventListener('change', () => searchPlaces());
  }
});

/* ==========================================================
   หน้าบทความ (articles.html) — โหลดจาก API ถ้าโหลดไม่ได้จะใช้รายการที่เขียนไว้ใน HTML แทน
========================================================== */

async function renderArticles() {
  const list = document.getElementById('articleList');
  if (!list) return;
  try {
    const articles = await api('/articles');
    if (!Array.isArray(articles) || articles.length === 0) return;
    list.innerHTML = articles.map(a => {
      const date = new Date(a.publishedAt + 'T00:00:00')
        .toLocaleDateString('th-TH', { day: 'numeric', month: 'short', year: 'numeric' });
      const img = a.image
        ? `<img src="${escapeHTML(a.image)}" alt="${escapeHTML(a.title)}" loading="lazy">`
        : '';
      return `
        <div class="article-item">
          <div class="thumb-sm">${img}</div>
          <div class="content">
            <h3>${escapeHTML(a.title)}</h3>
            <p>${escapeHTML(a.summary)}</p>
            <div class="meta">เผยแพร่ ${date} · หมวดหมู่: ${escapeHTML(a.category)}</div>
          </div>
        </div>`;
    }).join('');
  } catch (e) {
    console.warn('โหลดบทความจาก API ไม่สำเร็จ ใช้รายการใน HTML แทน:', e.message);
  }
}

document.addEventListener('DOMContentLoaded', renderArticles);