# รีวิวโปรเจกต์ "เที่ยวมะ" (tiaoma-Z)

ตรวจเมื่อ 23 ก.ย. 2026 — ครอบคลุม backend (Spring Boot 3.3.4 / Java 17 / JPA / Security / H2)
และ frontend (HTML/CSS/JS ล้วน เสิร์ฟจาก `src/main/resources/static`)

---

## สรุปภาพรวม

| ด้าน | คะแนน | ความเห็นสั้นๆ |
|---|---|---|
| โครงสร้างโค้ด | 8/10 | แบ่ง layer ชัด (controller/service/repository/dto/model) คอมเมนต์ไทยดีมาก อ่านง่าย |
| ระบบสมาชิก/ความปลอดภัยพื้นฐาน | 7/10 | BCrypt, session fixation, reset token แบบ hash + หมดอายุ + ใช้ครั้งเดียว ทำถูกหลักจริง |
| ฟีเจอร์ | 6/10 | ครบพอเป็นเว็บแนะนำที่เที่ยว แต่มีของค้างครึ่งทาง (บทความกดไม่ได้) และขาดโปรไฟล์ผู้ใช้ |
| ประสิทธิภาพ | 3/10 | **จุดอ่อนที่สุด** — รูปรวม 33 MB, ไฟล์เดียว 14 MB, รูปแอดมินเก็บเป็น base64 ใน DB |
| SEO / มาตรฐานเว็บ | 2/10 | ไม่มี meta description, og:, favicon, robots.txt, sitemap เลยสักหน้า |
| Accessibility / UX รายละเอียด | 5/10 | responsive ใช้ได้ แต่ Enter ในช่องค้นหาไม่ทำงาน, ใช้ `alert()/confirm()`, ไม่มี focus style |
| คุณภาพงานวิศวกรรม | 3/10 | ไม่มี `.gitignore`, `target/` ถูก commit, ไม่มีเทสต์แม้แต่ไฟล์เดียว, ไม่มี README |

**ความเห็นรวม:** เป็นโปรเจกต์ที่ "ทำจริงจัง" ไม่ใช่งานลวก โดยเฉพาะฝั่ง auth กับหน้า admin
ที่คิดละเอียดเกินคาด (กันถอดสิทธิ์ตัวเอง, บังคับให้เหลือแอดมินอย่างน้อย 1 คน, ตอบข้อความเดียวกัน
เสมอตอนลืมรหัสผ่านเพื่อไม่ให้เดาว่าอีเมลมีในระบบ) แต่ยังติด "ด่านขึ้นระบบจริง" อยู่หลายจุด
โดยเฉพาะเรื่องรูปภาพและ config ที่ยัง dev-mode อยู่

---

## สิ่งที่ทำได้ดี (ควรเก็บไว้)

1. **Flow ลืมรหัสผ่านทำถูกตำรา** — เก็บเฉพาะ SHA-256 ของ token, TTL 30 นาที, ลบ token เก่าทั้งหมด
   ก่อนออกใบใหม่, ใช้ได้ครั้งเดียว, ตอบ message เดียวกันเสมอไม่ว่าอีเมลจะมีจริงหรือไม่
   และถ้ายังไม่ตั้ง SMTP ก็ log ลิงก์ลง console ให้เทสต์ได้ — ดีไซน์นี้ดีกว่าโปรเจกต์นักศึกษาทั่วไปมาก
2. **`GlobalExceptionHandler`** รวม error ทุกแบบเป็น `{"message": "..."}` ภาษาไทย ทำให้ฝั่งหน้าเว็บ
   แสดงข้อความได้ตรงๆ ไม่ต้องเดา
3. **`AdminUserController`** มี business rule ที่คนมักลืม: ห้ามถอดสิทธิ์ตัวเอง และต้องเหลือแอดมิน ≥ 1
4. **การจัดกลุ่มที่เที่ยวตามภาค** ใน `places.html` พร้อมปุ่มเลื่อน ‹ › ที่ซ่อนเองเมื่อไม่จำเป็น
   (`updateRegionNavState`) — ใส่ใจรายละเอียด
5. `escapeHTML()` + `safeRedirect()` แสดงว่าคิดเรื่อง XSS / open redirect มาแล้ว (แค่ใช้ยังไม่ครบ ดูข้อ B1)

---

## สิ่งที่ต้องแก้ / ต้องเพิ่ม (เรียงตามความสำคัญ)

### 🔴 P0 — ควรแก้ก่อนส่ง/ก่อน deploy

**A. ประสิทธิภาพรูปภาพ (เร่งด่วนที่สุด)**
- `image/ayutthaya.jpg` = **14.5 MB**, `river-kwai-bridge.jpg` = **11 MB**, ทั้งโฟลเดอร์ **33 MB**
  → เปิดหน้าแรกบนมือถือ 4G รอเป็นนาที และ repo บวม (`.git` 33 MB ตามไปด้วย)
  **แก้:** ย่อให้กว้างสุด ~1600px แปลงเป็น WebP/JPEG คุณภาพ 80 → เหลือไฟล์ละ 150–400 KB
- **มี 8 ไฟล์ที่ไม่มีโค้ดไหนอ้างถึงเลย** (~14 MB สูญเปล่า):
  `river-kwai-bridge.jpg`, `koh-kood.jpg`, `erawan-national-park.jpg`, `jomtien-beach.jpg`,
  `khao-laem-ya.jpg`, `mon-bridge.jpg`, `pai-canyon.jpg`, และ **`logo.png`**
  (น่าสนใจ: มีโลโก้แต่ไม่ได้ใช้ — header ใช้ตัวอักษร และเว็บไม่มี favicon)
- `<img>` ไม่มี `width`/`height` → เกิด layout shift ตอนรูปโหลด

**B. ช่องโหว่ / ของที่ยัง dev-mode**
| # | เรื่อง | รายละเอียด |
|---|---|---|
| B1 | **XSS ที่ยังเหลือ** | `placeCardHTML()` และ `renderPlaceDetail()` ยัด `place.name`, `short`, `description`, `highlights`, `image` ลง `innerHTML` **โดยไม่ผ่าน `escapeHTML`** (ฝั่งบทความ escape แล้ว) ตอนนี้ปลอดภัยเพราะมีแต่แอดมินกรอก แต่วันที่มีแอดมินหลายคน หรือเปิดให้ผู้ใช้รีวิว = stored XSS ทันที |
| B2 | **รหัสผ่านแอดมินอยู่ในโค้ด** | `app.admin.password=123456789+a` commit อยู่ใน `application.properties` → ย้ายไป environment variable (`APP_ADMIN_PASSWORD`) |
| B3 | **H2 console เปิดอยู่** | `spring.h2.console.enabled=true` + `frameOptions(sameOrigin)` → ถ้า deploy ตามนี้คือเปิดหน้าจัดการฐานข้อมูลให้คนนอก แยกเป็น `application-prod.properties` ที่ปิดทิ้ง |
| B4 | **ไม่มี rate limit เลย** | `/api/auth/login` ยิงเดารหัสผ่านได้ไม่จำกัด และ `/api/auth/forgot-password` ใช้ยิงอีเมลถล่มคนอื่นได้ (email bomb) → ใส่ Bucket4j หรือ filter นับ IP ง่ายๆ ก็ยังดี |
| B5 | CSRF ปิดหมด + `SameSite=Lax` | คอมเมนต์อธิบายไว้แล้วว่าตั้งใจ แต่ Lax ยังยอมให้ POST แบบ top-level navigation บางกรณี → ถ้าไม่เปิด CSRF token ก็ควรใช้ `SameSite=Strict` |
| B6 | เปลี่ยน role แล้ว session เดิมไม่เปลี่ยน | โค้ดคอมเมนต์ยอมรับไว้แล้ว — แก้ได้ด้วย `SessionRegistry` แล้ว expire session ของ user นั้น |

**C. งานพื้นฐานของ repo**
- **ไม่มี `.gitignore` เลย** และ **`target/` (71 ไฟล์ `.class`) ถูก commit เข้า git**
  → เพิ่ม `.gitignore` (`target/`, `data/`, `*.class`, `.idea/`, `.DS_Store`) แล้ว `git rm -r --cached target`
  (สำคัญ: โฟลเดอร์ `data/` ของ H2 จะโดน commit ตามไปด้วยตอนรัน = ข้อมูลผู้ใช้จริงหลุดขึ้น GitHub)
- **ไม่มี `README.md`** — ไม่มีที่บอกวิธีรัน (`mvn spring-boot:run`), บัญชีแอดมินเริ่มต้น, การตั้ง SMTP
- **ไม่มีเทสต์เลยแม้แต่ไฟล์เดียว** ทั้งที่ `pom.xml` ใส่ `spring-boot-starter-test` ไว้แล้ว
  อย่างน้อยควรมีเทสต์ของ `AuthService` (สมัครซ้ำ, token หมดอายุ, token ใช้ซ้ำ)

**D. บั๊ก UX ที่เจอจริง**
- **กด Enter ในช่องค้นหาบน header และใน hero ไม่ทำงาน** — ผูกไว้แค่ `onclick` ที่ปุ่ม 🔍
  (มีเฉพาะ `searchInput` ในหน้า places ที่ผูก Enter ไว้) ต้องคลิกปุ่มอย่างเดียว ซึ่งขัดสัญชาตญาณมาก
- `.like-btn { top: -125px }` — ตำแหน่งปุ่มหัวใจ hardcode ผูกกับความสูง thumb 140px
  เปลี่ยนความสูงรูปเมื่อไหร่ปุ่มหลุดทันที ควรย้ายไปวางใน `.thumb` ที่ `position:relative`
- `requireLogin()` ใช้ `confirm()` ของเบราว์เซอร์ใน flow สำคัญ → ควรเป็น modal/toast ในธีมเว็บ
- ไม่มี loading state ระหว่างรอ `/api/places` → หน้าโล่งแวบหนึ่งทุกครั้ง

### 🟠 P1 — ทำแล้วเว็บ "ครบ" ขึ้นชัดเจน

1. **หน้ารายละเอียดบทความ (ของค้างครึ่งทาง)** — `Article` มีฟิลด์ `content` (20,000 ตัวอักษร)
   และแอดมินกรอกได้ แต่**ไม่มี `article-detail.html` และการ์ดบทความคลิกไม่ได้เลย**
   คนอ่านเห็นได้แค่คำโปรย → ต้องทำหน้านี้ + `GET /api/articles/{id}`
2. **หน้าโปรไฟล์ผู้ใช้** — ตอนนี้ล็อกอินแล้วแก้อะไรไม่ได้เลย ควรมี: แก้ชื่อ,
   **เปลี่ยนรหัสผ่านทั้งที่ยังล็อกอินอยู่** (`POST /api/auth/change-password` — ตอนนี้ต้องไปกดลืมรหัสผ่านอย่างเดียว), ลบบัญชี
3. **SEO / metadata** — ทุกหน้าขาด `meta description`, `og:title/og:image` (แชร์ LINE/FB แล้วไม่ขึ้นรูป),
   `canonical`, favicon, `robots.txt`, `sitemap.xml`
   หมายเหตุ: หน้า `place-detail` ตั้ง `<title>` ด้วย JS เท่านั้น → bot ที่ไม่รัน JS ไม่เห็นเนื้อหาอะไรเลย
   ถ้าอยากติดอันดับ Google จริงควร server-render หน้านี้ด้วย Thymeleaf
4. **ฟอนต์ไทย** — `font-family: "Segoe UI","Tahoma"` เป็นฟอนต์ฝั่ง Windows
   บน macOS/Android/Linux จะ fallback ไปฟอนต์ที่ภาษาไทยดูแย่ → ใส่ `"Noto Sans Thai"` / `"IBM Plex Sans Thai"`
5. **รูปที่แอดมินอัปโหลด = base64 data URL เก็บลง CLOB ใน DB** (`handleImageFileSelect`)
   → `GET /api/places` ต้องส่งรูปทั้งหมดเป็น base64 ทุกครั้งที่เปิดหน้า, เบราว์เซอร์ cache ไม่ได้,
   DB บวมเร็วมาก และไม่มีการจำกัดขนาดไฟล์เลย (อัป 20 MB ก็เข้า)
   → ควรทำ `POST /api/admin/uploads` เก็บเป็นไฟล์แล้วเก็บแค่ path + จำกัดขนาด/ชนิดไฟล์
6. **Accessibility** — ไม่มี `:focus-visible` style เลย (คนใช้คีย์บอร์ดหลงทาง),
   ปุ่มหัวใจไม่มี `aria-label`/`aria-pressed`, contrast ข้อความสีเทา `#888` บนขาวต่ำกว่าเกณฑ์ WCAG AA
7. **หน้า 404 ของตัวเอง** + empty state ที่สวยกว่าข้อความเปล่าๆ

### 🟡 P2 — ฟีเจอร์ที่จะทำให้เว็บน่าใช้ขึ้นจริง

- **รีวิว + ให้ดาว** สถานที่ (ตอนนี้มีแค่หัวใจ) และเรียง "ยอดนิยม" จากจำนวนคนกดใจ
- **แผนที่รวมทุกที่เที่ยว** หน้าเดียว (ตอนนี้แผนที่มีเฉพาะในหน้ารายละเอียด)
- **วางแผนทริป / itinerary** ต่อยอดจากรายการที่เที่ยวโปรด — เป็นจุดขายที่ต่างจากเว็บอื่น
- **กรองเพิ่ม**: ตามงบประมาณ, ช่วงฤดูที่เหมาะ, ระยะทางจากตำแหน่งปัจจุบัน
- **แชร์ไปโซเชียล** + ปุ่มคัดลอกลิงก์
- pagination / infinite scroll (ตอนนี้ค้นหาและกรองทำฝั่ง client ทั้งหมดด้วยการ `display:none` — มี 50 ที่ก็เริ่มอืด)
- dark mode, PWA (ติดตั้งลงมือถือ + ดูออฟไลน์), ภาษาอังกฤษสำหรับนักท่องเที่ยวต่างชาติ

### 🔵 P3 — ระยะยาว

- ย้าย H2 → PostgreSQL + Flyway migration (ตอนนี้ `ddl-auto=update` ใช้จริงระยะยาวอันตราย)
- Dockerfile + GitHub Actions (build + test ทุก push)
- Structured logging + health check (`spring-boot-starter-actuator`)
- Google Analytics / Plausible ดูว่าคนเข้าหน้าไหน

---

## เรื่องเล็กๆ ที่เจอระหว่างอ่านโค้ด

- `about.html`, `articles.html`, `register.html`, `reset-password.html` ไม่ได้โหลด `places-data.js`
  แต่ `script.js` เรียก `loadPlaces()` ทุกหน้า → บรรทัด `PLACES = await api('/places')`
  สร้างตัวแปร global โดยบังเอิญ (implicit global) ทำงานได้แต่เปราะ และเป็นการยิง API เปล่าๆ ทุกหน้า
  → ควรเรียก `loadPlaces()` เฉพาะหน้าที่ใช้
- `articles.html` มีบทความ hardcode ไว้ 4 ชิ้นเป็น fallback — ถ้าแอดมินลบบทความจนหมด
  หน้าเว็บจะกลับไปโชว์ 4 ชิ้นเดิมที่ลบไปแล้ว
- ไฟล์ static อ้าง `places-data.js?v=2` บางหน้า ไม่มี `?v=` บางหน้า → cache ไม่ตรงกัน ควรทำ cache-busting ให้สม่ำเสมอ
- `login.html` โหลด `places-data.js` ทั้งที่ไม่ได้ใช้
- รหัสผ่านขั้นต่ำแค่ 6 ตัวอักษร และไม่เช็คความแข็งแรงเลย

---

## ถ้าให้เลือกทำแค่ 5 อย่างในสุดสัปดาห์นี้

1. บีบ/ลบรูป 33 MB → เหลือ ~3 MB (ได้ผลกับผู้ใช้ทันทีที่สุด)
2. เพิ่ม `.gitignore` + เอา `target/` ออกจาก git + เขียน `README.md`
3. แก้ Enter ในช่องค้นหา + escape HTML ให้ครบใน `placeCardHTML`/`renderPlaceDetail`
4. ทำหน้ารายละเอียดบทความให้จบ (ของมีอยู่แล้ว แค่ยังไม่มีทางเข้า)
5. ย้ายรหัสแอดมินไป env + ปิด H2 console ใน profile prod + ใส่ favicon/meta/og:image
