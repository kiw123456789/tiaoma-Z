/* ==========================================================
   Service Worker ของ "เที่ยวมะ"
   ทำให้เว็บติดตั้งลงมือถือได้ และเปิดดูหน้าที่เคยเข้าแล้วได้ตอนเน็ตหลุด

   กลยุทธ์แคช:
   - ไฟล์หน้าเว็บ/CSS/JS  -> network-first (เอาของใหม่ก่อนเสมอ ถ้าเน็ตล่มค่อยใช้ของเก่า)
     เพราะถ้าใช้ cache-first ผู้ใช้จะติดเวอร์ชันเก่าจนกว่าจะล้างแคชเอง
   - รูปภาพ              -> cache-first (รูปแทบไม่เปลี่ยน และเป็นตัวกินเน็ตหลัก)
   - เรียก /api/*         -> ไม่แคช (ข้อมูลต้องสดเสมอ) แต่ตอนออฟไลน์ตอบ JSON บอกสาเหตุ
========================================================== */

const VERSION = 'v2';
const SHELL_CACHE = `tiaoma-shell-${VERSION}`;
const IMAGE_CACHE = `tiaoma-image-${VERSION}`;

// ไฟล์ที่โหลดไว้ล่วงหน้าตอนติดตั้ง เพื่อให้เปิดออฟไลน์ได้ตั้งแต่ครั้งแรก
const PRECACHE = [
  '/',
  '/index.html',
  '/places.html',
  '/map.html',
  '/articles.html',
  '/about.html',
  '/404.html',
  '/style.css',
  '/script.js',
  '/places-data.js',
  '/site.webmanifest',
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(SHELL_CACHE)
      // ไม่ใช้ addAll เพราะถ้าไฟล์เดียวพลาด จะล้มทั้งชุด
      .then(cache => Promise.allSettled(PRECACHE.map(url => cache.add(url))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== SHELL_CACHE && k !== IMAGE_CACHE)
            .map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const { request } = event;

  // แคชเฉพาะ GET และเฉพาะโดเมนตัวเอง
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // ไม่แตะ API, หน้าแอดมิน, h2-console — ต้องสดและต้องเช็คสิทธิ์เสมอ
  if (url.pathname.startsWith('/api/')
      || url.pathname.startsWith('/h2-console')
      || url.pathname === '/admin.html'
      || url.pathname === '/sitemap.xml') {
    return;
  }

  // รูปภาพ: ใช้ของในแคชก่อน
  if (url.pathname.startsWith('/image/') || url.pathname.startsWith('/uploads/')) {
    event.respondWith(cacheFirst(request, IMAGE_CACHE));
    return;
  }

  // ที่เหลือ (หน้า HTML, CSS, JS): เอาของใหม่ก่อน
  event.respondWith(networkFirst(request, SHELL_CACHE));
});

async function cacheFirst(request, cacheName) {
  const cached = await caches.match(request);
  if (cached) return cached;
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      cache.put(request, response.clone());
    }
    return response;
  } catch (e) {
    return new Response('', { status: 504, statusText: 'ออฟไลน์' });
  }
}

async function networkFirst(request, cacheName) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(cacheName);
      cache.put(request, response.clone());
    }
    return response;
  } catch (e) {
    const cached = await caches.match(request);
    if (cached) return cached;

    // ถ้าเป็นการเปิดหน้าเว็บ ให้แสดงหน้าออฟไลน์แทนหน้าขาวของเบราว์เซอร์
    if (request.mode === 'navigate') {
      const fallback = await caches.match('/index.html');
      if (fallback) return fallback;
    }
    return new Response('ออฟไลน์อยู่ กรุณาเชื่อมต่ออินเทอร์เน็ตแล้วลองใหม่', {
      status: 503,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    });
  }
}
