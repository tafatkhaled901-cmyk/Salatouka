"""
prayer_times.py — Calcul des horaires de prière islamique
Méthode : Muslim World League (MWL)
Utilisation : python prayer_times.py --lat 48.85 --lon 2.35 --date 2026-08-03
"""

import math
import json
import argparse
from datetime import date, datetime, timedelta


# ── Constantes ───────────────────────────────────────────────────────────────
METHODS = {
    "MWL":      {"fajr": 18.0, "isha": 17.0},   # Muslim World League
    "ISNA":     {"fajr": 15.0, "isha": 15.0},   # Islamic Society of North America
    "Egypt":    {"fajr": 19.5, "isha": 17.5},   # Egyptian General Authority
    "Makkah":   {"fajr": 18.5, "isha": 90},     # Umm al-Qura (Isha = 90 min après Maghrib)
    "Karachi":  {"fajr": 18.0, "isha": 18.0},   # University of Islamic Sciences
    "Tehran":   {"fajr": 17.7, "isha": 14.0},
    "Jafari":   {"fajr": 16.0, "isha": 14.0},
}

ASR_METHODS = {"Standard": 1, "Hanafi": 2}  # shadow ratio


# ── Fonctions astronomiques ──────────────────────────────────────────────────
def deg_to_rad(d): return d * math.pi / 180
def rad_to_deg(r): return r * 180 / math.pi
def sin(d):  return math.sin(deg_to_rad(d))
def cos(d):  return math.cos(deg_to_rad(d))
def tan(d):  return math.tan(deg_to_rad(d))
def asin(x): return rad_to_deg(math.asin(x))
def acos(x): return rad_to_deg(math.acos(x))
def atan2(y, x): return rad_to_deg(math.atan2(y, x))

def fix_angle(a):
    """Ramène un angle dans [0, 360["""
    return a - 360 * math.floor(a / 360)

def fix_hour(h):
    """Ramène une heure dans [0, 24["""
    return h - 24 * math.floor(h / 24)


def julian_day(y, m, d):
    """Numéro du jour julien."""
    if m <= 2:
        y -= 1; m += 12
    A = math.floor(y / 100)
    B = 2 - A + math.floor(A / 4)
    return math.floor(365.25 * (y + 4716)) + math.floor(30.6001 * (m + 1)) + d + B - 1524.5


def sun_position(jd):
    """Déclinaison solaire et équation du temps pour un JD donné."""
    D  = jd - 2451545.0               # jours depuis J2000.0
    g  = fix_angle(357.529 + 0.98560028 * D)   # anomalie moyenne
    q  = fix_angle(280.459 + 0.98564736 * D)   # longitude moyenne
    L  = fix_angle(q + 1.915 * sin(g) + 0.020 * sin(2 * g))  # ecliptique
    e  = 23.439 - 0.00000036 * D      # obliquité
    RA = atan2(cos(e) * sin(L), cos(L)) / 15  # ascension droite (heures)
    D_sun = asin(sin(e) * sin(L))     # déclinaison
    EqT = q / 15 - fix_hour(RA)      # équation du temps (heures)
    return D_sun, EqT


def compute_prayer_times(
    lat: float, lon: float,
    target_date: date,
    method: str = "MWL",
    asr_method: str = "Standard",
    timezone: float = 2.0,    # UTC+2 pour Paris en été
) -> dict:
    """
    Calcule les 5 prières + Sunrise pour la latitude/longitude données.

    Returns:
        dict avec clés 'Fajr','Sunrise','Dhuhr','Asr','Maghrib','Isha'
        Valeurs : chaînes "HH:MM"
    """
    params  = METHODS.get(method, METHODS["MWL"])
    asr_rat = ASR_METHODS.get(asr_method, 1)

    jd = julian_day(target_date.year, target_date.month, target_date.day)
    decl, eqt = sun_position(jd)

    # ── Midi solaire (transit) ──
    noon = 12 - lon / 15 - eqt + timezone  # heures UTC+tz

    def hour_angle(angle):
        """Heure solaire correspondant à l'angle de hauteur donné."""
        num = -sin(angle) - sin(lat) * sin(decl)
        den = cos(lat) * cos(decl)
        if abs(num / den) > 1:
            return float('nan')   # soleil jamais à cette hauteur
        return acos(num / den) / 15

    def asr_angle():
        """Angle pour l'Asr selon le ratio d'ombre."""
        x = asr_rat + tan(abs(lat - decl))
        return rad_to_deg(math.atan(1 / x))

    def fmt(h):
        """Convertit des heures décimales en 'HH:MM'."""
        if math.isnan(h): return "—"
        h = fix_hour(h)
        hh = int(h)
        mm = int(round((h - hh) * 60))
        if mm == 60: hh += 1; mm = 0
        return f"{hh:02d}:{mm:02d}"

    fajr_ha    = hour_angle(params["fajr"])
    sunrise_ha = hour_angle(0.833)
    asr_ha     = hour_angle(asr_angle())
    sunset_ha  = hour_angle(0.833)

    isha_is_min = isinstance(params["isha"], int)  # Makkah = minutes fixes
    if isha_is_min:
        isha_ha = params["isha"] / 60
    else:
        isha_ha = hour_angle(params["isha"])

    times = {
        "Fajr":    noon - fajr_ha,
        "Sunrise": noon - sunrise_ha,
        "Dhuhr":   noon,
        "Asr":     noon + asr_ha,
        "Maghrib": noon + sunset_ha,
        "Isha":    noon + sunset_ha + isha_ha if isha_is_min else noon + isha_ha,
    }

    return {k: fmt(v) for k, v in times.items()}


# ── CLI ───────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="Calcul des horaires de prière islamique")
    parser.add_argument("--lat",    type=float, default=48.8566, help="Latitude  (défaut: Paris)")
    parser.add_argument("--lon",    type=float, default=2.3522,  help="Longitude (défaut: Paris)")
    parser.add_argument("--date",   default=str(date.today()),   help="Date YYYY-MM-DD")
    parser.add_argument("--method", default="MWL", choices=METHODS.keys())
    parser.add_argument("--asr",    default="Standard", choices=ASR_METHODS.keys())
    parser.add_argument("--tz",     type=float, default=2.0,    help="UTC offset (ex: 2 pour CEST)")
    parser.add_argument("--json",   action="store_true",         help="Sortie JSON")
    args = parser.parse_args()

    d = date.fromisoformat(args.date)
    times = compute_prayer_times(args.lat, args.lon, d, args.method, args.asr, args.tz)

    if args.json:
        print(json.dumps(times, ensure_ascii=False, indent=2))
    else:
        print(f"\n📍  Lat {args.lat:.4f}  Lon {args.lon:.4f}  —  {d.strftime('%A %d %B %Y')}")
        print(f"🕌  Méthode : {args.method}  |  Asr : {args.asr}  |  UTC+{args.tz}\n")
        for name, t in times.items():
            icon = {"Fajr":"🌙","Sunrise":"🌄","Dhuhr":"☀️","Asr":"🌤","Maghrib":"🌅","Isha":"🌌"}.get(name,"⏰")
            print(f"  {icon}  {name:<10} {t}")
        print()


if __name__ == "__main__":
    main()
