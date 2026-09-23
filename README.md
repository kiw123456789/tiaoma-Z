# เที่ยวมะ (Tiaoma)

เว็บแนะนำที่เที่ยวในประเทศไทย — ค้นหาสถานที่ตามภาค/หมวดหมู่ ดูบนแผนที่รวม อ่านบทความท่องเที่ยว
บันทึกที่เที่ยวโปรด ให้คะแนนและเขียนรีวิว พร้อมหน้าจัดการเนื้อหาสำหรับผู้ดูแลระบบ
รองรับโหมดมืดและติดตั้งลงมือถือได้ (PWA)

สร้างด้วย **Spring Boot 3.3.4** (Java 17) + HTML/CSS/JavaScript ล้วน ไม่มี build step ฝั่งหน้าเว็บ

---

## เริ่มใช้งาน

### สิ่งที่ต้องมี
- JDK 17 ขึ้นไป
- Maven 3.8+ (หรือใช้ `./mvnw` ที่มากับโปรเจกต์)

### รัน

```bash
./mvnw spring-boot:run
```

เปิดเบราว์เซอร์ที่ <http://localhost:8080>

### build เป็นไฟล์เดียว

```bash
./mvnw clean package
java -jar target/tiaoma-backend-0.0.1-SNAPSHOT.jar
```

### รันเทสต์

```bash
./mvnw test
```

---

## บัญชีผู้ดูแลระบบครั้งแรก

ตอนเริ่มระบบครั้งแรก ถ้ายังไม่มีผู้ใช้สิทธิ์ `ADMIN` เลย ระบบจะสร้างให้อัตโนมัติ

- ถ้าตั้ง `APP_ADMIN_PASSWORD` ไว้ → ใช้รหัสนั้น
- ถ้า**ไม่ได้ตั้ง** → ระบบจะ **สุ่มรหัสผ่านแล้วพิมพ์ลง console ครั้งเดียว** ตอนเริ่มระบบ
  ให้คัดลอกเก็บไว้ทันที เพราะจะไม่แสดงอีก

```
====================================================
  สร้างบัญชีผู้ดูแลระบบเริ่มต้นแล้ว
  อีเมล    : admin@tiaoma.local
  รหัสผ่าน : xxxxxxxxxxxx
  *** เปลี่ยนรหัสผ่านทันทีหลังเข้าสู่ระบบครั้งแรก ***
====================================================
```

> เดิมโปรเจกต์นี้ hardcode รหัสผ่านแอดมินไว้ในซอร์สโค้ดและ commit ขึ้น git — ตอนนี้แก้แล้ว

---

## การตั้งค่า (environment variables)

ทุกค่ามี default ที่ใช้งานได้ทันทีตอนพัฒนา ปรับผ่าน env var ได้ทั้งหมด

| ตัวแปร | ค่าเริ่มต้น | คำอธิบาย |
|---|---|---|
| `PORT` | `8080` | พอร์ตที่เซิร์ฟเวอร์ฟัง |
| `APP_ADMIN_EMAIL` | `admin@tiaoma.local` | อีเมลแอดมินคนแรก |
| `APP_ADMIN_PASSWORD` | *(สุ่ม)* | รหัสผ่านแอดมินคนแรก |
| `COOKIE_SECURE` | `false` | ตั้งเป็น `true` เมื่อ deploy หลัง HTTPS |
| `H2_CONSOLE_ENABLED` | `true` | **ต้องปิดใน production** |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | URL จริงของเว็บ ใช้ในลิงก์รีเซ็ตรหัสผ่านและ sitemap |
| `UPLOAD_DIR` | `./uploads` | โฟลเดอร์เก็บรูปที่แอดมินอัปโหลด |
| `MAIL_FROM` | `เที่ยวมะ <no-reply@tiaoma.local>` | ชื่อผู้ส่งอีเมล |
| `RATELIMIT_LOGIN_MAX` | `8` | จำนวนครั้งล็อกอินต่อ IP ใน 15 นาที |
| `RATELIMIT_FORGOT_MAX` | `4` | จำนวนครั้งขอลิงก์รีเซ็ตต่อ IP ใน 60 นาที |

### อีเมล

ถ้ายังไม่ตั้ง `spring.mail.host` ระบบจะ **พิมพ์ลิงก์รีเซ็ตรหัสผ่านลง console** แทนการส่งอีเมลจริง
สะดวกตอนพัฒนา ส่วน production ให้ตั้งค่า SMTP ใน `application-prod.properties`
(ดูตัวอย่างที่ `src/main/resources/application-prod.properties.example`)

### รันแบบ production

```bash
cp src/main/resources/application-prod.properties.example src/main/resources/application-prod.properties
# แก้ค่าในไฟล์ให้เรียบร้อย (ไฟล์นี้ถูก .gitignore ไว้แล้ว)

export APP_ADMIN_PASSWORD='รหัสผ่านที่แข็งแรง'
export COOKIE_SECURE=true
export H2_CONSOLE_ENABLED=false
export PUBLIC_BASE_URL=https://tiaoma.example.com

java -jar target/tiaoma-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## โครงสร้างโปรเจกต์

```
src/main/java/com/tiaoma/
├── config/          SecurityConfig, WebConfig, DataSeeder, NotFoundConfig, FilterRegistrationConfig
├── controller/      REST API (auth, places, articles, reviews, likes, account, admin/*)
├── dto/             record สำหรับรับ-ส่งข้อมูล พร้อม bean validation
├── model/           JPA entity (User, Place, Article, Like, Review, PasswordResetToken)
├── repository/      Spring Data JPA
├── security/        RateLimitFilter, UserDetailsService
└── service/         AuthService, MailService, PlaceViewService

src/main/resources/
├── seed/            places.json (16 แห่ง), articles.json (6 บทความ) — ใส่ลง DB ครั้งแรก
└── static/          หน้าเว็บทั้งหมด (15 ไฟล์ HTML) + style.css + script.js + admin.js
                     + sw.js (service worker) + site.webmanifest + รูป
```

---

## ฐานข้อมูล

ใช้ **H2 แบบไฟล์** เก็บที่ `./data/tiaoma.mv.db` (อยู่ใน `.gitignore` แล้ว)
ข้อมูลไม่หายเมื่อปิดโปรแกรม แต่ **ไม่เหมาะกับ production ที่มีผู้ใช้จริง**

ตอนเริ่มระบบครั้งแรก `DataSeeder` จะอ่าน `seed/places.json` และ `seed/articles.json` ใส่ลงฐานข้อมูล
(ถ้าตารางว่างเท่านั้น — จะไม่เขียนทับข้อมูลที่แอดมินแก้ไปแล้ว)

เข้า H2 console ตอนพัฒนาได้ที่ <http://localhost:8080/h2-console>
(JDBC URL: `jdbc:h2:file:./data/tiaoma`, user `sa`, ไม่มีรหัสผ่าน)

**ก่อนขึ้น production ควรย้ายไป PostgreSQL + Flyway** และเลิกใช้ `ddl-auto=update`

---

## API

### สาธารณะ
| Method | Path | คำอธิบาย |
|---|---|---|
| GET | `/api/places` | สถานที่ทั้งหมด พร้อมคะแนนเฉลี่ยและจำนวนคนบันทึก |
| GET | `/api/places/{id}` | สถานที่รายตัว |
| GET | `/api/places/{id}/reviews` | รีวิวของสถานที่ + คะแนนเฉลี่ย |
| GET | `/api/articles` | รายการบทความ (ไม่ส่งเนื้อหาเต็ม) |
| GET | `/api/articles/{id}` | บทความพร้อมเนื้อหาเต็มและบทความที่เกี่ยวข้อง |
| GET | `/sitemap.xml` | sitemap สร้างอัตโนมัติจากฐานข้อมูล |
| GET | `/robots.txt` · `/sw.js` · `/site.webmanifest` | ไฟล์ SEO และ PWA |

### ต้องล็อกอิน
| Method | Path | คำอธิบาย |
|---|---|---|
| POST | `/api/auth/register` `/login` `/logout` | สมัคร / เข้าสู่ระบบ / ออกจากระบบ |
| GET | `/api/auth/me` | ดูว่าใครล็อกอินอยู่ |
| POST | `/api/auth/forgot-password` `/reset-password` | ลืมรหัสผ่าน / ตั้งรหัสใหม่ |
| GET PUT DELETE | `/api/likes` `/api/likes/{placeId}` | ที่เที่ยวโปรด |
| PUT DELETE | `/api/places/{id}/reviews` | เขียน/แก้ไข/ลบรีวิวของตัวเอง |
| GET PUT DELETE | `/api/account` `/api/account/profile` | ดู/แก้โปรไฟล์ / ลบบัญชี |
| POST | `/api/account/change-password` | เปลี่ยนรหัสผ่าน |

### เฉพาะ ADMIN
`/api/admin/places/**` · `/api/admin/articles/**` · `/api/admin/users/**` · `POST /api/admin/uploads`

ข้อผิดพลาดทุกแบบตอบเป็น JSON รูปแบบเดียวกัน: `{"message": "ข้อความภาษาไทย"}`

---

## ความปลอดภัย

- รหัสผ่านเก็บด้วย **BCrypt** ไม่เก็บรหัสจริง
- นโยบายรหัสผ่าน: 8–72 ตัวอักษร ต้องมีทั้งตัวอักษรและตัวเลข
- ล็อกอินแล้วเปลี่ยน session id (กัน session fixation)
- โทเคนรีเซ็ตรหัสผ่านเก็บเป็น **SHA-256 hash** อายุ 30 นาที ใช้ได้ครั้งเดียว
- ข้อความตอบกลับของ `/forgot-password` เหมือนกันเสมอ (กันการเดาว่าอีเมลไหนมีบัญชี)
- **จำกัดจำนวนครั้ง** ยิง login / forgot-password / register ต่อ IP
- session cookie: `HttpOnly` + `SameSite=Strict`
- ทุกข้อความจากผู้ใช้และแอดมินถูก escape ก่อนแสดงผล (กัน XSS)
- security headers: `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, frame options

### ข้อควรระวังที่ยังเหลือ
- **CSRF token ถูกปิดไว้** — พึ่ง `SameSite=Strict` แทน ถ้าจะเปิด API ให้โดเมนอื่นเรียก ต้องเปิด CSRF ก่อน
- rate limit เก็บใน memory ของเครื่องเดียว ถ้า scale หลายเครื่องต้องย้ายไป Redis
- ยังไม่มีการยืนยันอีเมลตอนสมัครสมาชิก

---

## เทสต์

```
src/test/java/com/tiaoma/
├── service/AuthServiceTest.java        เทสต์ระดับหน่วย: สมัคร/แฮชรหัสผ่าน/โทเคนรีเซ็ต
├── security/RateLimitFilterTest.java   เทสต์ตัวจำกัดจำนวนครั้ง
└── controller/ApiIntegrationTest.java  เทสต์ระดับ API: สิทธิ์เข้าถึง, รีวิว, จัดการบัญชี
```

เทสต์ใช้ H2 ในหน่วยความจำ (`application-test.properties`) ไม่แตะฐานข้อมูลจริงในโฟลเดอร์ `data/`

---

## โหมดมืด

ปุ่ม 🌙 บนแถบด้านบนของทุกหน้า สลับโหมดสว่าง/มืดได้ และจำค่าไว้ใน `localStorage`
ถ้าผู้ใช้ยังไม่เคยเลือกเอง จะใช้ค่าตามการตั้งค่าของเครื่อง (`prefers-color-scheme`) โดยอัตโนมัติ

สีทั้งหมดอยู่ในตัวแปร CSS ที่ `:root` และ `:root[data-theme="dark"]` (ดูหัวไฟล์ `style.css`)
ถ้าจะเพิ่ม component ใหม่ ให้ใช้ตัวแปรเหล่านี้แทนการใส่สีตายตัว ไม่งั้นโหมดมืดจะพัง:

| ตัวแปร | ใช้กับ |
|---|---|
| `--c-bg` | พื้นหลังหน้า |
| `--c-surface` / `--c-surface-2` | พื้นการ์ด / พื้นรอง |
| `--c-border` | เส้นขอบ |
| `--c-text` / `--c-muted` | ข้อความหลัก / ข้อความรอง |
| `--c-primary` / `--c-primary-soft` | สีแบรนด์ / พื้นเขียวจาง |

สคริปต์สั้นๆ ใน `<head>` ของทุกหน้าตั้งธีมก่อนเบราว์เซอร์วาดหน้า เพื่อไม่ให้เห็นจอขาวแวบหนึ่ง
ก่อนเปลี่ยนเป็นมืด — **อย่าย้ายสคริปต์นี้ไปไว้ท้ายหน้า**

---

## PWA (ติดตั้งลงมือถือ / ใช้งานออฟไลน์)

`sw.js` ทำให้เปิดหน้าที่เคยเข้าแล้วได้ตอนเน็ตหลุด และติดตั้งเว็บลงหน้าจอมือถือได้

- **หน้าเว็บ/CSS/JS** — network-first (เอาของใหม่ก่อน เน็ตล่มค่อยใช้ของเก่า)
- **รูปภาพ** — cache-first (รูปแทบไม่เปลี่ยนและกินเน็ตมากสุด)
- **`/api/*`, `/admin.html`, `/h2-console`** — ไม่แคชเลย ข้อมูลต้องสดและต้องเช็คสิทธิ์ทุกครั้ง

เวลาแก้ไฟล์หน้าเว็บ ต้องทำสองอย่างคู่กัน ไม่งั้นผู้ใช้เดิมจะยังเห็นของเก่า:
1. เพิ่มเลข `?v=` ที่ท้าย `style.css` / `script.js` ในไฟล์ HTML
2. เพิ่ม `VERSION` ใน `sw.js` (เพื่อล้างแคชชุดเก่าทิ้ง)

---

## สิ่งที่ยังทำได้อีก

- ย้ายไป PostgreSQL + Flyway migration
- render ฝั่งเซิร์ฟเวอร์ (Thymeleaf/SSR) — ตอนนี้ crawler เห็นหน้าว่างเพราะ render ด้วย JS ล้วน
- แบ่งหน้า (pagination) สำหรับ `/api/places` — ตอนนี้ส่งทั้งหมดแล้วกรองฝั่งเบราว์เซอร์
- ยืนยันอีเมลตอนสมัคร
- ค้นหาตามระยะทางจากตำแหน่งปัจจุบัน (หน้าแผนที่รวมทำแล้วที่ `map.html`
  แต่ยังใช้การค้นด้วยชื่อ เพราะข้อมูลยังไม่มีพิกัด lat/lng — ถ้าเพิ่มพิกัดจะทำหมุดจริงได้)
- วางแผนทริป / itinerary ต่อยอดจากรายการที่เที่ยวโปรด
- ภาษาอังกฤษสำหรับนักท่องเที่ยวต่างชาติ
- Dockerfile / docker-compose และ CI
