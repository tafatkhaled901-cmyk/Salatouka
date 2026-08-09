# صلاتك × Météo — Dashboard islamique

Application web progressive combinant horaires de prière et météo, inspirée de l'écran de verrouillage Salatuk / Météo.

## 🌟 Fonctionnalités

| Module | Détail |
|--------|--------|
| 🕐 Horloge temps réel | Mise à jour chaque seconde |
| 🌙 5 prières du jour | Fajr · Dhuhr · Asr · Maghrib · Isha |
| ⏳ Compte à rebours | Temps avant la prochaine prière |
| ⛅ Météo locale | Icône + température + humidité + vent |
| 📅 Date hégirien | Calendrier islamique affiché |

---

## 🚀 Déploiement sur GitHub Pages (étape par étape)

### 1. Créer le dépôt

```bash
# Sur GitHub.com → New repository
# Nom : salatuk-meteo   (ou ce que tu veux)
# Visibilité : Public ✅
# Cocher : Add a README file ✅
```

### 2. Cloner et pousser les fichiers

```bash
git clone https://github.com/TON_USERNAME/salatuk-meteo.git
cd salatuk-meteo

# Copier index.html, prayer_times.py, README.md ici
git add .
git commit -m "feat: initial Salatuk × Météo dashboard"
git push origin main
```

### 3. Activer GitHub Pages

```
GitHub → ton dépôt → Settings → Pages
Source : Deploy from a branch
Branch : main   /root   → Save
```

✅ Ton app sera en ligne sur :
**`https://TON_USERNAME.github.io/salatuk-meteo/`**

---

## 🐍 Script Python — Calcul des prières

Le fichier `prayer_times.py` calcule les horaires selon la méthode **Muslim World League (MWL)**.

### Installation

```bash
# Aucune dépendance externe requise — Python 3.8+ suffit
python --version
```

### Utilisation

```bash
# Paris, aujourd'hui
python prayer_times.py

# Ville personnalisée
python prayer_times.py --lat 36.7370 --lon 3.0869 --tz 1   # Alger
python prayer_times.py --lat 33.5731 --lon -7.5898 --tz 1  # Casablanca
python prayer_times.py --lat 48.8566 --lon 2.3522  --tz 2  # Paris (CEST)
python prayer_times.py --lat 51.5072 --lon -0.1276 --tz 1  # Londres (BST)

# Sortie JSON (pour intégration API)
python prayer_times.py --lat 48.85 --lon 2.35 --json
```

### Exemple de sortie

```
📍  Lat 48.8566  Lon 2.3522  —  Lundi 03 août 2026
🕌  Méthode : MWL  |  Asr : Standard  |  UTC+2

  🌙  Fajr       04:18
  🌄  Sunrise    06:21
  ☀️  Dhuhr      13:39
  🌤  Asr        17:20
  🌅  Maghrib    21:24
  🌌  Isha       23:10
```

### Méthodes disponibles

| Code | Organisation |
|------|-------------|
| `MWL` | Muslim World League *(défaut)* |
| `ISNA` | Islamic Society of North America |
| `Egypt` | Egyptian General Authority |
| `Makkah` | Umm al-Qura (Arabie Saoudite) |
| `Karachi` | University of Islamic Sciences |
| `Tehran` | Institut géophysique de Téhéran |

---

## 🔧 Intégration API météo (optionnel)

Pour avoir la météo réelle, crée un compte gratuit sur [OpenWeatherMap](https://openweathermap.org/api) et remplace dans `index.html` :

```javascript
// Ajouter avant la balise </script>
const API_KEY = 'TA_CLE_OWM';

async function fetchWeather(lat, lon) {
  const r = await fetch(
    `https://api.openweathermap.org/data/2.5/weather?lat=${lat}&lon=${lon}&appid=${API_KEY}&units=metric&lang=fr`
  );
  const d = await r.json();
  document.getElementById('weatherTemp').textContent = Math.round(d.main.temp) + '°';
  document.getElementById('weatherCondition').textContent = d.weather[0].description;
  document.getElementById('humidity').textContent = d.main.humidity + '%';
  document.getElementById('wind').textContent = Math.round(d.wind.speed * 3.6) + ' km/h';
  document.getElementById('feelsLike').textContent = Math.round(d.main.feels_like) + '°';
}

navigator.geolocation.getCurrentPosition(p =>
  fetchWeather(p.coords.latitude, p.coords.longitude)
);
```

---

## 📁 Structure du projet

```
salatuk-meteo/
├── index.html         ← App web principale (ouvrir directement dans le navigateur)
├── prayer_times.py    ← Calculateur Python des horaires de prière
└── README.md          ← Ce fichier
```

---

*بسم الله الرحمن الرحيم*  
Projet open source · MIT License
