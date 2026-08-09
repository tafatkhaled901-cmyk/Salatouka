// Service Worker — Salatuk × Météo v3
// PWABuilder compatible

const CACHE_NAME = 'salatuk-v3';
const OFFLINE_URL = './index.html';

const ASSETS = [
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './favicon.ico',
  './favicon-180.png',
  './og-image.png',
];

// ── Install ──────────────────────────────────────
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      return cache.addAll(ASSETS);
    }).then(() => self.skipWaiting())
  );
});

// ── Activate ─────────────────────────────────────
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      )
    ).then(() => self.clients.claim())
  );
});

// ── Fetch — Network first, cache fallback ────────
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;

  // APIs externes → network only
  const url = new URL(event.request.url);
  const isExternal = !url.origin.includes('github.io');
  if (isExternal) {
    event.respondWith(fetch(event.request).catch(() => new Response('')));
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then(response => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        return response;
      })
      .catch(() => caches.match(event.request).then(r => r || caches.match(OFFLINE_URL)))
  );
});

// ── Push Notifications (pour les prières) ────────
self.addEventListener('push', event => {
  const data = event.data ? event.data.json() : {};
  event.waitUntil(
    self.registration.showNotification(data.title || 'وقت الصلاة', {
      body: data.body || 'حان وقت الصلاة',
      icon: './icon-192.png',
      badge: './favicon-32.png',
      tag: 'prayer-time',
      renotify: true,
      vibrate: [500, 200, 500, 200, 500],
      data: { url: './' }
    })
  );
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(clients.openWindow('./'));
});
