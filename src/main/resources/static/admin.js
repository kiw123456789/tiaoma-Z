/* ==========================================================
   admin.js : หน้าจัดการเนื้อหา (admin.html) — เฉพาะผู้ใช้ role ADMIN
   ต้องโหลด script.js ก่อนไฟล์นี้ (ใช้ api(), getSession(), escapeHTML() ร่วมกัน)
========================================================== */

let adminArticles = [];      // แคชบทความทั้งหมดไว้ในหน้านี้ (โหลดจาก /api/articles)
let adminUsers = [];         // แคชผู้ใช้ทั้งหมดไว้ในหน้านี้ (โหลดจาก /api/admin/users)
let editingPlaceId = null;   // null = กำลังเพิ่มใหม่, ไม่ null = กำลังแก้ไขสถานที่นี้
let editingArticleId = null; // null = กำลังเพิ่มใหม่, ไม่ null = กำลังแก้ไขบทความนี้

async function renderAdminPage() {
  const root = document.getElementById('adminRoot');
  if (!root) return;

  const session = getSession();
  if (!session) {
    root.innerHTML = `
      <div class="not-found">
        <h2>กรุณาเข้าสู่ระบบ</h2>
        <a href="login.html?redirect=admin.html"><button class="btn-primary">ไปหน้าเข้าสู่ระบบ</button></a>
      </div>`;
    return;
  }
  if (session.role !== 'ADMIN') {
    root.innerHTML = `
      <div class="not-found">
        <h2>ไม่มีสิทธิ์เข้าถึงหน้านี้</h2>
        <p>หน้านี้สำหรับผู้ดูแลระบบ (ADMIN) เท่านั้น</p>
        <a href="index.html"><button class="btn-primary">กลับหน้าแรก</button></a>
      </div>`;
    return;
  }

  try {
    adminArticles = await api('/articles');
  } catch (e) {
    adminArticles = [];
  }

  try {
    adminUsers = await api('/admin/users');
  } catch (e) {
    adminUsers = [];
  }

  root.innerHTML = `
    <div class="admin-tabs">
      <button class="admin-tab active" id="tabPlacesBtn" onclick="switchAdminTab('places')">📍 สถานที่ท่องเที่ยว (${PLACES.length})</button>
      <button class="admin-tab" id="tabArticlesBtn" onclick="switchAdminTab('articles')">📰 บทความ (${adminArticles.length})</button>
      <button class="admin-tab" id="tabUsersBtn" onclick="switchAdminTab('users')">👤 ผู้ใช้ทั้งหมด (${adminUsers.length})</button>
    </div>

    <div class="admin-panel active" id="panelPlaces"></div>
    <div class="admin-panel" id="panelArticles"></div>
    <div class="admin-panel" id="panelUsers"></div>
  `;

  renderPlacesPanel();
  renderArticlesPanel();
  renderUsersPanel();
}

function switchAdminTab(tab) {
  document.getElementById('tabPlacesBtn').classList.toggle('active', tab === 'places');
  document.getElementById('tabArticlesBtn').classList.toggle('active', tab === 'articles');
  document.getElementById('tabUsersBtn').classList.toggle('active', tab === 'users');
  document.getElementById('panelPlaces').classList.toggle('active', tab === 'places');
  document.getElementById('panelArticles').classList.toggle('active', tab === 'articles');
  document.getElementById('panelUsers').classList.toggle('active', tab === 'users');
}

/* ==================== สถานที่ท่องเที่ยว ==================== */

function renderPlacesPanel() {
  const panel = document.getElementById('panelPlaces');
  if (!panel) return;

  const rows = PLACES.map(p => `
    <tr>
      <td>${escapeHTML(p.id)}</td>
      <td>${escapeHTML(p.name)}</td>
      <td>${escapeHTML(p.province)}</td>
      <td>${escapeHTML(p.category)}</td>
      <td>
        <button class="admin-edit-btn" onclick="startEditPlace('${p.id}')">แก้ไข</button>
        <button class="admin-delete-btn" onclick="deletePlace('${p.id}')">ลบ</button>
      </td>
    </tr>
  `).join('');

  panel.innerHTML = `
    <table class="admin-table">
      <thead><tr><th>รหัส (id)</th><th>ชื่อ</th><th>จังหวัด</th><th>หมวดหมู่</th><th>จัดการ</th></tr></thead>
      <tbody>${rows || '<tr><td colspan="5">ยังไม่มีข้อมูล</td></tr>'}</tbody>
    </table>
    <div id="placeFormWrap"></div>
  `;

  renderPlaceForm();
}

function renderPlaceForm() {
  const wrap = document.getElementById('placeFormWrap');
  if (!wrap) return;

  const place = editingPlaceId ? getPlaceById(editingPlaceId) : null;
  const isEdit = !!place;

  wrap.innerHTML = `
    <div class="admin-form">
      <h4>${isEdit ? 'แก้ไขสถานที่: ' + escapeHTML(place.name) : 'เพิ่มสถานที่ใหม่'}</h4>

      <details class="admin-bulk-paste" style="margin-bottom:16px;border:1px dashed #bbb;border-radius:8px;padding:10px 14px;background:#fafafa;">
        <summary style="cursor:pointer;font-weight:600;color:#166534;">⚡ วางข้อมูลทั้งหมดทีเดียว แล้วให้ระบบแยกให้อัตโนมัติ (ไม่บังคับ ไม่ต้องพิมพ์ทีละช่อง)</summary>
        <p class="admin-hint" style="margin-top:8px;">พิมพ์หรือวางข้อความตามแบบด้านล่าง จะเรียงหัวข้อสลับกันหรือข้ามหัวข้อที่ไม่มีก็ได้ แล้วกด "แยกข้อมูลอัตโนมัติ" ระบบจะเติมทุกช่องด้านล่างให้เอง — ก่อนบันทึกลองตรวจทานอีกทีได้เสมอ</p>
        <textarea id="pBulkPaste" style="min-height:180px;font-family:ui-monospace,Consolas,monospace;font-size:13px;" placeholder="รหัส: doi-inthanon
ชื่อ: ดอยอินทนนท์
จังหวัด: เชียงใหม่
หมวดหมู่: ภูเขา/ธรรมชาติ
คำอธิบายสั้น: ยอดเขาที่สูงที่สุดในประเทศไทย อากาศเย็นตลอดปี
เนื้อหาเต็ม:
ย่อหน้าแรกของเนื้อหา
ย่อหน้าที่สอง
จุดเด่น:
ข้อแรก
ข้อสอง
เวลาเปิด-ปิด: 05:30-18:30
ค่าใช้จ่าย: รถยนต์ 50 บาท
ช่วงเวลาแนะนำ: พ.ย.-ก.พ.
คำค้นหาแผนที่: ดอยอินทนนท์ เชียงใหม่
สีการ์ด: 1"></textarea>
        <button type="button" class="btn-outline" style="margin-top:8px;" onclick="applyBulkPlaceParse()">⚡ แยกข้อมูลอัตโนมัติ</button>
      </details>

      <div class="form-error" id="placeFormError"></div>
      <form id="placeForm" onsubmit="return submitPlaceForm(event)">
        <div class="admin-form-row">
          <div>
            <label for="pId">รหัสสถานที่ (id) — ใช้ a-z 0-9 และ - เท่านั้น เช่น doi-inthanon</label>
            <input type="text" id="pId" value="${isEdit ? escapeHTML(place.id) : ''}" ${isEdit ? 'disabled' : ''} placeholder="doi-inthanon">
          </div>
          <div>
            <label for="pName">ชื่อสถานที่</label>
            <input type="text" id="pName" value="${isEdit ? escapeHTML(place.name) : ''}">
          </div>
        </div>
        <div class="admin-form-row">
          <div>
            <label for="pProvince">จังหวัด</label>
            <input type="text" id="pProvince" value="${isEdit ? escapeHTML(place.province) : ''}">
          </div>
          <div>
            <label for="pCategory">หมวดหมู่</label>
            <input type="text" id="pCategory" value="${isEdit ? escapeHTML(place.category) : ''}" placeholder="เช่น ภูเขา/ธรรมชาติ">
          </div>
        </div>
        <label for="pShort">คำอธิบายสั้น (แสดงในการ์ด)</label>
        <textarea id="pShort">${isEdit ? escapeHTML(place.short) : ''}</textarea>

        <label for="pDescription">เนื้อหาเต็ม (1 บรรทัด = 1 ย่อหน้า)</label>
        <textarea id="pDescription" style="min-height:100px;">${isEdit ? place.description.map(escapeHTML).join('\n') : ''}</textarea>

        <label for="pHighlights">จุดเด่น (1 บรรทัด = 1 ข้อ)</label>
        <textarea id="pHighlights">${isEdit ? place.highlights.map(escapeHTML).join('\n') : ''}</textarea>

        <div class="admin-form-row">
          <div>
            <label for="pHours">เวลาเปิด-ปิด</label>
            <input type="text" id="pHours" value="${isEdit ? escapeHTML(place.hours || '') : ''}">
          </div>
          <div>
            <label for="pFee">ค่าใช้จ่าย</label>
            <input type="text" id="pFee" value="${isEdit ? escapeHTML(place.fee || '') : ''}">
          </div>
        </div>
        <div class="admin-form-row">
          <div>
            <label for="pBestTime">ช่วงเวลาแนะนำ</label>
            <input type="text" id="pBestTime" value="${isEdit ? escapeHTML(place.bestTime || '') : ''}">
          </div>
          <div>
            <label for="pMapQuery">คำค้นหาแผนที่ (Google Maps)</label>
            <input type="text" id="pMapQuery" value="${isEdit ? escapeHTML(place.mapQuery || '') : ''}" placeholder="เช่น ดอยอินทนนท์ เชียงใหม่">
          </div>
        </div>
        <div class="admin-form-row">
          <div>
            <label for="pImageFile">รูปภาพ</label>
            <input type="file" id="pImageFile" accept="image/*" onchange="handleImageFileSelect('pImageFile', 'pImage', 'pImagePreview', 'pImageHint')">
            <input type="hidden" id="pImage" value="${isEdit ? escapeHTML(place.image || '') : ''}">
            <img id="pImagePreview" class="image-preview" src="${isEdit ? escapeHTML(place.image || '') : ''}" style="max-width:220px;max-height:160px;object-fit:cover;border-radius:6px;margin:8px 0;border:1px solid #ddd;${isEdit && place.image ? '' : 'display:none;'}">
            <p class="image-check-hint" id="pImageHint"></p>
          </div>
          <div>
            <label for="pAccent">สีการ์ด (1-6)</label>
            <input type="number" id="pAccent" min="1" max="6" value="${isEdit ? place.accent : 1}">
          </div>
        </div>
        <p class="admin-hint">เลือกไฟล์รูปจากเครื่องได้เลย ระบบจะอัปโหลดเก็บเป็นไฟล์บนเซิร์ฟเวอร์ให้อัตโนมัติ (สูงสุด 5 MB) — รูปควรกว้างอย่างน้อย 1200px ไม่งั้นจะเบลอตอนแสดงผลใหญ่ในหน้ารายละเอียด</p>
        <button type="submit" class="btn-primary">${isEdit ? 'บันทึกการแก้ไข' : 'เพิ่มสถานที่'}</button>
        ${isEdit ? '<button type="button" class="btn-outline" onclick="cancelEditPlace()" style="margin-left:10px;">ยกเลิก</button>' : ''}
      </form>
    </div>
  `;

  initImagePreview('pImage', 'pImagePreview', 'pImageHint');
}

/* หัวข้อที่รู้จักสำหรับกล่อง "วางข้อมูลทั้งหมดทีเดียว" — เพิ่ม/แก้ label ตรงนี้ได้ถ้าอยากรองรับคำอื่น
   labels: รายการคำที่ถือว่าเป็นหัวข้อเดียวกัน (ใส่ได้ทั้งแบบสั้นและแบบเต็มตามที่เห็นในฟอร์ม)
   multiline: true = ค่าที่ตามมาจะถูกแยกเป็นหลายรายการ (array) ทีละบรรทัด จนกว่าจะเจอหัวข้อถัดไป */
const PLACE_BULK_LABELS = [
  { key: 'id', labels: ['รหัสสถานที่', 'รหัส'], multiline: false },
  { key: 'name', labels: ['ชื่อสถานที่', 'ชื่อ'], multiline: false },
  { key: 'province', labels: ['จังหวัด'], multiline: false },
  { key: 'category', labels: ['หมวดหมู่'], multiline: false },
  { key: 'short', labels: ['คำอธิบายสั้น'], multiline: false },
  { key: 'description', labels: ['เนื้อหาเต็ม'], multiline: true },
  { key: 'highlights', labels: ['จุดเด่น'], multiline: true },
  { key: 'hours', labels: ['เวลาเปิด-ปิด'], multiline: false },
  { key: 'fee', labels: ['ค่าใช้จ่าย'], multiline: false },
  { key: 'bestTime', labels: ['ช่วงเวลาแนะนำ'], multiline: false },
  { key: 'mapQuery', labels: ['คำค้นหาแผนที่'], multiline: false },
  { key: 'accent', labels: ['สีการ์ด'], multiline: false }
];

/* แยกข้อความก้อนเดียวที่ผู้ใช้พิมพ์/วางมา ให้เป็น object ตามหัวข้อใน PLACE_BULK_LABELS
   รูปแบบที่รองรับ: "หัวข้อ: ค่า" — ไม่บังคับว่าแต่ละหัวข้อต้องขึ้นบรรทัดใหม่ (สแกนหาได้ทั้งก้อนข้อความ)
   หัวข้ออาจมีวงเล็บอธิบายเพิ่มคั่นก่อนเครื่องหมาย : ได้ เช่น "จุดเด่น (1 บรรทัด = 1 ข้อ):" ก็ถือเป็นหัวข้อ "จุดเด่น" */
function parseBulkPlaceText(text) {
  // เรียงหัวข้อจากยาวไปสั้น กันกรณีหัวข้อสั้นเป็นคำนำหน้าของหัวข้อยาว (เช่น "ชื่อ" อยู่ในคำว่า "ชื่อสถานที่")
  const allLabels = [];
  PLACE_BULK_LABELS.forEach(def => def.labels.forEach(l => allLabels.push(l)));
  allLabels.sort((a, b) => b.length - a.length);

  const globalRegex = new RegExp('(' + allLabels.join('|') + ')\\s*(?:\\([^)]*\\))?\\s*[:：]\\s*', 'g');

  const matches = [...text.matchAll(globalRegex)];
  const result = {};
  if (matches.length === 0) return result;

  matches.forEach((m, i) => {
    const found = PLACE_BULK_LABELS.find(def => def.labels.includes(m[1]));
    if (!found) return;
    const valueStart = m.index + m[0].length;
    const valueEnd = i + 1 < matches.length ? matches[i + 1].index : text.length;
    const rawValue = text.slice(valueStart, valueEnd).trim();

    if (found.multiline) {
      const items = rawValue.split('\n').map(s => s.trim()).filter(Boolean);
      if (items.length) result[found.key] = items;
    } else if (rawValue) {
      // ยุบช่องว่าง/ขึ้นบรรทัดใหม่ภายในค่าให้เหลือเว้นวรรคเดียว เผื่อค่านั้นถูกพิมพ์คร่อมหลายบรรทัด
      result[found.key] = rawValue.replace(/\s+/g, ' ').trim();
    }
  });
  return result;
}

/* อ่านค่าจากกล่อง pBulkPaste แล้วเติมลงทุกช่องในฟอร์มเพิ่ม/แก้ไขสถานที่ให้อัตโนมัติ */
function applyBulkPlaceParse() {
  const box = document.getElementById('pBulkPaste');
  const raw = box ? box.value : '';
  if (!raw || !raw.trim()) {
    toast('ยังไม่ได้พิมพ์หรือวางข้อมูลอะไรเลย', 'error');
    return;
  }

  const parsed = parseBulkPlaceText(raw);
  if (Object.keys(parsed).length === 0) {
    toast('ไม่พบหัวข้อที่ระบบรู้จักในข้อความที่วางเลย ลองเช็คว่าพิมพ์ชื่อหัวข้อตามด้วยเครื่องหมาย ":" ถูกต้องไหม (เช่น "จังหวัด: กาญจนบุรี")', 'error');
    return;
  }
  const idField = document.getElementById('pId');

  if (parsed.id && idField && !idField.disabled) {
    // ช่วยแปลงให้เป็นรูปแบบ id ที่ถูกต้อง (a-z 0-9 -) อัตโนมัติ เผื่อพิมพ์เป็นภาษาไทยหรือมีเว้นวรรค
    idField.value = parsed.id.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  }
  if (parsed.name) document.getElementById('pName').value = parsed.name;
  if (parsed.province) document.getElementById('pProvince').value = parsed.province;
  if (parsed.category) document.getElementById('pCategory').value = parsed.category;
  if (parsed.short) document.getElementById('pShort').value = parsed.short;
  if (parsed.description) document.getElementById('pDescription').value = parsed.description.join('\n');
  if (parsed.highlights) document.getElementById('pHighlights').value = parsed.highlights.join('\n');
  if (parsed.hours) document.getElementById('pHours').value = parsed.hours;
  if (parsed.fee) document.getElementById('pFee').value = parsed.fee;
  if (parsed.bestTime) document.getElementById('pBestTime').value = parsed.bestTime;
  if (parsed.mapQuery) document.getElementById('pMapQuery').value = parsed.mapQuery;
  if (parsed.accent && !isNaN(parseInt(parsed.accent, 10))) {
    document.getElementById('pAccent').value = Math.min(6, Math.max(1, parseInt(parsed.accent, 10)));
  }

  toast('แยกข้อมูลเรียบร้อย ✅ เลื่อนลงไปตรวจทานแต่ละช่องอีกทีก่อนกด "บันทึก" ได้เลย', 'success');
}

/* อัปโหลดไฟล์รูปที่ผู้ใช้เลือกไปเก็บเป็นไฟล์จริงบนเซิร์ฟเวอร์ (POST /api/admin/uploads)
   แล้วเก็บแค่ "พาธ" ลง hidden input

   เดิมฟังก์ชันนี้แปลงรูปเป็น base64 data URL แล้วยัดลงฐานข้อมูล ซึ่งทำให้
   - JSON ของ /api/places ใหญ่มาก (ส่งรูปทุกใบมาด้วยทุกครั้งที่เปิดหน้า)
   - เบราว์เซอร์ cache รูปไม่ได้เลย
   - ฐานข้อมูลบวมเร็วและไม่มีการจำกัดขนาดไฟล์ */
async function handleImageFileSelect(fileInputId, hiddenInputId, previewId, hintId, minWidth = 1200) {
  const fileInput = document.getElementById(fileInputId);
  const hidden = document.getElementById(hiddenInputId);
  const preview = document.getElementById(previewId);
  const hint = document.getElementById(hintId);
  const file = fileInput && fileInput.files && fileInput.files[0];
  if (!file) return;

  if (!file.type.startsWith('image/')) {
    if (hint) { hint.textContent = '⚠️ กรุณาเลือกไฟล์รูปภาพเท่านั้น'; hint.className = 'image-check-hint warning'; }
    fileInput.value = '';
    return;
  }

  const MAX_MB = 5;
  if (file.size > MAX_MB * 1024 * 1024) {
    if (hint) {
      hint.textContent = `⚠️ ไฟล์ใหญ่เกินไป (${(file.size / 1024 / 1024).toFixed(1)} MB) สูงสุด ${MAX_MB} MB กรุณาย่อรูปก่อน`;
      hint.className = 'image-check-hint warning';
    }
    fileInput.value = '';
    return;
  }

  if (hint) { hint.textContent = '⏳ กำลังอัปโหลด...'; hint.className = 'image-check-hint'; }

  try {
    const form = new FormData();
    form.append('file', file);
    // ไม่ตั้ง Content-Type เอง ให้เบราว์เซอร์ใส่ boundary ของ multipart ให้
    const res = await fetch('/api/admin/uploads', {
      method: 'POST',
      credentials: 'same-origin',
      body: form
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      throw new Error((data && data.message) || 'อัปโหลดไม่สำเร็จ');
    }

    if (hidden) hidden.value = data.url;
    if (preview) { preview.src = data.url; preview.style.display = ''; }
    if (hint) {
      if (data.warning) {
        hint.textContent = '⚠️ ' + data.warning;
        hint.className = 'image-check-hint warning';
        } else {
        hint.textContent = `✅ อัปโหลดแล้ว (กว้าง ${data.width}px, ${(data.sizeBytes / 1024).toFixed(0)} KB)`;
        hint.className = 'image-check-hint ok';
      }
      // ข้อมูลเสริมว่าบีบไปเท่าไร แสดงต่อท้าย (ไม่ว่า warning หรือไม่งั้นเงียบหาย)
      if (data.info) {
        hint.textContent += '\n' + data.info;
      }
    }
  } catch (e) {
    if (hint) { hint.textContent = '⚠️ ' + e.message; hint.className = 'image-check-hint warning'; }
    fileInput.value = '';
  }
}

/* ตอนเปิดฟอร์ม (โดยเฉพาะตอนแก้ไขของเดิม) แสดงรูปตัวอย่างจากค่าที่มีอยู่แล้ว และเช็คความละเอียดให้ทันที */
function initImagePreview(hiddenInputId, previewId, hintId, minWidth = 1200) {
  const hidden = document.getElementById(hiddenInputId);
  const preview = document.getElementById(previewId);
  const hint = document.getElementById(hintId);
  if (!hidden || !hint) return;

  const path = hidden.value.trim();
  hint.textContent = '';
  hint.className = 'image-check-hint';
  if (!path) { if (preview) preview.style.display = 'none'; return; }

  const img = new Image();
  img.onload = () => {
    if (preview) { preview.src = path; preview.style.display = ''; }
    if (img.naturalWidth < minWidth) {
      hint.textContent = `⚠️ รูปนี้กว้างแค่ ${img.naturalWidth}px แนะนำอย่างน้อย ${minWidth}px ไม่งั้นจะเบลอตอนแสดงผลใหญ่ในหน้ารายละเอียด`;
      hint.classList.add('warning');
    } else {
      hint.textContent = `✅ ความละเอียด ${img.naturalWidth}×${img.naturalHeight}px เพียงพอ ไม่น่าเบลอ`;
      hint.classList.add('ok');
    }
  };
  img.onerror = () => {
    hint.textContent = '⚠️ ยังไม่พบไฟล์รูปที่พาธนี้ — ลองอัปโหลดรูปใหม่อีกครั้ง';
    hint.classList.add('warning');
  };
  img.src = path;
}

function startEditPlace(id) {
  editingPlaceId = id;
  renderPlaceForm();
  document.getElementById('placeFormWrap').scrollIntoView({ behavior: 'smooth' });
}

function cancelEditPlace() {
  editingPlaceId = null;
  renderPlaceForm();
}

async function submitPlaceForm(event) {
  event.preventDefault();
  const errorBox = document.getElementById('placeFormError');
  hideBox(errorBox);

  const id = document.getElementById('pId').value.trim();
  const body = {
    id,
    name: document.getElementById('pName').value.trim(),
    province: document.getElementById('pProvince').value.trim(),
    category: document.getElementById('pCategory').value.trim(),
    short: document.getElementById('pShort').value.trim(),
    description: document.getElementById('pDescription').value.split('\n').map(s => s.trim()).filter(Boolean),
    highlights: document.getElementById('pHighlights').value.split('\n').map(s => s.trim()).filter(Boolean),
    hours: document.getElementById('pHours').value.trim(),
    fee: document.getElementById('pFee').value.trim(),
    bestTime: document.getElementById('pBestTime').value.trim(),
    mapQuery: document.getElementById('pMapQuery').value.trim(),
    image: document.getElementById('pImage').value.trim(),
    accent: parseInt(document.getElementById('pAccent').value, 10) || 1
  };

  if (!id || !body.name || !body.province || !body.category || !body.short) {
    return showBox(errorBox, 'กรุณากรอกข้อมูลที่จำเป็นให้ครบ (รหัส, ชื่อ, จังหวัด, หมวดหมู่, คำอธิบายสั้น)'), false;
  }

  try {
    if (editingPlaceId) {
      await api('/admin/places/' + encodeURIComponent(editingPlaceId), { method: 'PUT', body: JSON.stringify(body) });
    } else {
      await api('/admin/places', { method: 'POST', body: JSON.stringify(body) });
    }
    editingPlaceId = null;
    await loadPlaces();
    renderPlacesPanel();
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

async function deletePlace(id) {
  const ok = await confirmDialog({
    title: 'ลบสถานที่',
    message: 'ต้องการลบสถานที่นี้ใช่หรือไม่? การลบไม่สามารถย้อนกลับได้',
    confirmText: 'ลบสถานที่',
    cancelText: 'ยกเลิก',
    danger: true
  });
  if (!ok) return;
  try {
    await api('/admin/places/' + encodeURIComponent(id), { method: 'DELETE' });
    await loadPlaces();
    renderPlacesPanel();
  } catch (e) {
    toast(e.message, 'error');
  }
}

/* ==================== บทความ ==================== */

function renderArticlesPanel() {
  const panel = document.getElementById('panelArticles');
  if (!panel) return;

  const rows = adminArticles.map(a => `
    <tr>
      <td>${escapeHTML(a.title)}</td>
      <td>${escapeHTML(a.category)}</td>
      <td>${escapeHTML(a.publishedAt)}</td>
      <td>
        <button class="admin-edit-btn" onclick="startEditArticle(${a.id})">แก้ไข</button>
        <button class="admin-delete-btn" onclick="deleteArticle(${a.id})">ลบ</button>
      </td>
    </tr>
  `).join('');

  panel.innerHTML = `
    <table class="admin-table">
      <thead><tr><th>หัวข้อ</th><th>หมวดหมู่</th><th>วันที่เผยแพร่</th><th>จัดการ</th></tr></thead>
      <tbody>${rows || '<tr><td colspan="4">ยังไม่มีบทความ</td></tr>'}</tbody>
    </table>
    <div id="articleFormWrap"></div>
  `;

  renderArticleForm();
}

function renderArticleForm() {
  const wrap = document.getElementById('articleFormWrap');
  if (!wrap) return;

  const article = editingArticleId ? adminArticles.find(a => a.id === editingArticleId) : null;
  const isEdit = !!article;

  wrap.innerHTML = `
    <div class="admin-form">
      <h4>${isEdit ? 'แก้ไขบทความ: ' + escapeHTML(article.title) : 'เพิ่มบทความใหม่'}</h4>
      <div class="form-error" id="articleFormError"></div>
      <form id="articleForm" onsubmit="return submitArticleForm(event)">
        <label for="aTitle">หัวข้อบทความ</label>
        <input type="text" id="aTitle" value="${isEdit ? escapeHTML(article.title) : ''}">

        <div class="admin-form-row">
          <div>
            <label for="aCategory">หมวดหมู่</label>
            <input type="text" id="aCategory" value="${isEdit ? escapeHTML(article.category) : ''}" placeholder="เช่น ภาคเหนือ">
          </div>
          <div>
            <label for="aPublishedAt">วันที่เผยแพร่</label>
            <input type="date" id="aPublishedAt" value="${isEdit ? article.publishedAt : new Date().toISOString().slice(0, 10)}">
          </div>
        </div>

        <label for="aImageFile">รูปภาพ</label>
        <input type="file" id="aImageFile" accept="image/*" onchange="handleImageFileSelect('aImageFile', 'aImage', 'aImagePreview', 'aImageHint')">
        <input type="hidden" id="aImage" value="${isEdit ? escapeHTML(article.image || '') : ''}">
        <img id="aImagePreview" class="image-preview" src="${isEdit ? escapeHTML(article.image || '') : ''}" style="max-width:220px;max-height:160px;object-fit:cover;border-radius:6px;margin:8px 0;border:1px solid #ddd;${isEdit && article.image ? '' : 'display:none;'}">
        <p class="image-check-hint" id="aImageHint"></p>

        <label for="aSummary">สรุปย่อ (แสดงในหน้ารวมบทความ)</label>
        <textarea id="aSummary">${isEdit ? escapeHTML(article.summary) : ''}</textarea>

        <label for="aContent">เนื้อหาเต็ม (เว้นบรรทัดว่าง 1 บรรทัด = ขึ้นย่อหน้าใหม่)</label>
        <textarea id="aContent" style="min-height:160px;">${isEdit ? escapeHTML(article.content || '') : ''}</textarea>

        <button type="submit" class="btn-primary">${isEdit ? 'บันทึกการแก้ไข' : 'เพิ่มบทความ'}</button>
        ${isEdit ? '<button type="button" class="btn-outline" onclick="cancelEditArticle()" style="margin-left:10px;">ยกเลิก</button>' : ''}
      </form>
    </div>
  `;

  initImagePreview('aImage', 'aImagePreview', 'aImageHint');
}

function startEditArticle(id) {
  editingArticleId = id;
  renderArticleForm();
  document.getElementById('articleFormWrap').scrollIntoView({ behavior: 'smooth' });
}

function cancelEditArticle() {
  editingArticleId = null;
  renderArticleForm();
}

async function submitArticleForm(event) {
  event.preventDefault();
  const errorBox = document.getElementById('articleFormError');
  hideBox(errorBox);

  const body = {
    title: document.getElementById('aTitle').value.trim(),
    category: document.getElementById('aCategory').value.trim(),
    publishedAt: document.getElementById('aPublishedAt').value,
    image: document.getElementById('aImage').value.trim(),
    summary: document.getElementById('aSummary').value.trim(),
    content: document.getElementById('aContent').value.trim()
  };

  if (!body.title || !body.category || !body.summary) {
    return showBox(errorBox, 'กรุณากรอกหัวข้อ หมวดหมู่ และสรุปย่อให้ครบ'), false;
  }

  try {
    if (editingArticleId) {
      await api('/admin/articles/' + editingArticleId, {
        method: 'PUT',
        body: JSON.stringify(body)
      });
    } else {
      await api('/admin/articles', {
        method: 'POST',
        body: JSON.stringify(body)
      });
    }
    editingArticleId = null;
    adminArticles = await api('/articles');
    renderArticlesPanel();
  } catch (e) {
    showBox(errorBox, e.message);
  }
  return false;
}

async function deleteArticle(id) {
  const ok = await confirmDialog({
    title: 'ลบบทความ',
    message: 'ต้องการลบบทความนี้ใช่หรือไม่? การลบไม่สามารถย้อนกลับได้',
    confirmText: 'ลบบทความ',
    cancelText: 'ยกเลิก',
    danger: true
  });
  if (!ok) return;
  try {
    await api('/admin/articles/' + id, { method: 'DELETE' });
    adminArticles = await api('/articles');
    renderArticlesPanel();
  } catch (e) {
    toast(e.message, 'error');
  }
}

/* ==================== ผู้ใช้ทั้งหมด ==================== */

function renderUsersPanel() {
  const panel = document.getElementById('panelUsers');
  if (!panel) return;

  const myId = getSession().id;

  const rows = adminUsers.map(u => {
    const isAdmin = u.role === 'ADMIN';
    const isSelf = u.id === myId;
    const joined = new Date(u.createdAt).toLocaleDateString('th-TH', { day: 'numeric', month: 'short', year: 'numeric' });

    let actionCell;
    if (isSelf) {
      actionCell = `<span style="color:#888;">คุณเอง</span>`;
    } else if (isAdmin) {
      actionCell = `<button class="admin-delete-btn" onclick="changeUserRole(${u.id}, 'USER')">ถอดสิทธิ์แอดมิน</button>`;
    } else {
      actionCell = `<button class="admin-edit-btn" onclick="changeUserRole(${u.id}, 'ADMIN')">เลื่อนเป็นแอดมิน</button>`;
    }

    const badgeStyle = isAdmin
      ? 'background:#fde68a;color:#92400e;'
      : 'background:#e5e7eb;color:#374151;';

    return `
      <tr>
        <td>${escapeHTML(u.name)}</td>
        <td>${escapeHTML(u.email)}</td>
        <td><span style="padding:2px 10px;border-radius:10px;font-size:12px;font-weight:600;${badgeStyle}">${isAdmin ? 'ADMIN' : 'USER'}</span></td>
        <td>${joined}</td>
        <td>${actionCell}</td>
      </tr>`;
  }).join('');

  panel.innerHTML = `
    <div class="form-error" id="usersError"></div>
    <table class="admin-table">
      <thead>
        <tr><th>ชื่อ</th><th>อีเมล</th><th>สิทธิ์</th><th>สมัครเมื่อ</th><th>จัดการ</th></tr>
      </thead>
      <tbody>${rows || '<tr><td colspan="5">ยังไม่มีผู้ใช้สมัครสมาชิก</td></tr>'}</tbody>
    </table>
  `;
}

async function changeUserRole(id, role) {
  const label = role === 'ADMIN' ? 'เลื่อนขั้นผู้ใช้นี้เป็นแอดมิน' : 'ถอดสิทธิ์แอดมินของผู้ใช้นี้';
  const ok = await confirmDialog({
    title: role === 'ADMIN' ? 'เลื่อนขั้นผู้ใช้' : 'ถอดสิทธิ์แอดมิน',
    message: `ต้องการ${label}ใช่หรือไม่?`,
    confirmText: 'ยืนยัน',
    cancelText: 'ยกเลิก',
    danger: role !== 'ADMIN'
  });
  if (!ok) return;

  const errorBox = document.getElementById('usersError');
  hideBox(errorBox);
  try {
    await api('/admin/users/' + id + '/role', {
      method: 'PUT',
      body: JSON.stringify({ role })
    });
    adminUsers = await api('/admin/users');
    renderUsersPanel();
    // อัปเดตตัวเลขที่แท็บด้วย
    document.getElementById('tabUsersBtn').textContent = `👤 ผู้ใช้ทั้งหมด (${adminUsers.length})`;
  } catch (e) {
    showBox(errorBox, e.message);
  }
}

window.addEventListener('tiaoma:session-ready', renderAdminPage);