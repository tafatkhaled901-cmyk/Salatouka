// Service Worker — Salatuk × Météo v4
const CACHE_NAME = 'salatuk-v5';
const OFFLINE_URL = './index.html';

const ASSETS = [
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './icon-96.png',
  './favicon.ico',
  './adhan-makkah.mp3',
  './adhan-madinah.mp3',
  './adhan-egypt.mp3',
  './adhan-fajr.mp3',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (!url.origin.includes(self.location.origin)) {
    e.respondWith(fetch(e.request).catch(() => new Response('')));
    return;
  }
  e.respondWith(
    fetch(e.request)
      .then(r => {
        const clone = r.clone();
        caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
        return r;
      })
      .catch(() => caches.match(e.request).then(r => r || caches.match(OFFLINE_URL)))
  );
});

// ── Notification de prière envoyée par la page ──
self.addEventListener('message', event => {
  const d = event.data || {};
  if (d.type === 'PRAYER_NOTIFICATION') {
    self.registration.showNotification(d.title, d.options || {});
  }
});

// ── Clic sur la notification : ouvrir / focaliser l'app ──
self.addEventListener('notificationclick', event => {
  const action = event.action;
  event.notification.close();
  if (action === 'stop') return;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
      for (const client of list) {
        if ('focus' in client) return client.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow('./');
    })
  );
});

// ── Push (si un serveur en envoie un jour) ──
self.addEventListener('push', event => {
  let d = {};
  try { d = event.data ? event.data.json() : {}; } catch (e) {}
  event.waitUntil(
    self.registration.showNotification(d.title || '🕌 وقت الصلاة', {
      body: d.body || 'حان وقت الصلاة',
      icon: './icon-192.png',
      badge: './icon-96.png',
      image: './icon-512.png',
      tag: 'salatuk-prayer',
      requireInteraction: true,
      vibrate: [800, 300, 800, 300, 800],
    })
  );
});
