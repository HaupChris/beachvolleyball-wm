# Zahnputz-Tracker 🦷

Android-App (Kotlin, Jetpack Compose) zum Erinnern und Abhaken von Zähneputzen und Zahnseide – komplett offline, ohne Server.

## Funktionen
- **Heute**: Putz-Einheiten und Zahnseide abhaken, Serie 🔥, Level/XP, Wochen-Challenge, nächste Ziele
- **Kalender**: Monatsansicht (perfekt / teilweise / verpasst), Tage nachtragen/korrigieren, Monatsbilanz
- **Statistik**: Wochen (8) und Monate (6) als Balkendiagramm, Quoten, Gesamtwerte
- **Ziele**: Level, XP-Regeln, Erfolge (Serien, Anzahl, perfekte Wochen/Monate) mit Fortschritt
- **Optionen**: Putzen pro Tag (1–4) mit Erinnerungszeiten, Zahnseide-Wochentage + Uhrzeit, Erinnerungen an/aus
- Benachrichtigungen mit „✓ Geputzt“-Button; bereits erledigte Einheiten werden nicht mehr erinnert

## Aufbau
- `core/` – reine Kotlin-Domänenlogik (Streaks, Statistik, XP, Erfolge) mit Unit-Tests, ohne Android-Abhängigkeit
- `app/` – Android-UI (Compose), SQLite-Speicher, Erinnerungen via `AlarmManager`

Die Tagesziele werden pro Tag mitgespeichert – Planänderungen gelten ab heute und verändern die Vergangenheit nicht.
Daten liegen nur lokal; Androids Auto-Backup (Google-Konto) sichert sie mit, sofern aktiviert.

## APK bauen & installieren
- **CI**: Jeder Push baut über GitHub Actions ein APK → Actions → Lauf → Artifact `zahnputz-apk` herunterladen, entpacken, auf dem Handy installieren („Unbekannte Apps installieren“ erlauben).
- **Lokal**: Android Studio öffnen oder `./gradlew :app:assembleRelease` (benötigt Android SDK).

### Stabile Signatur (empfohlen)
Ohne eigenen Schlüssel wird mit einem wechselnden Debug-Key signiert – ein neues APK lässt sich dann nicht als Update installieren (Deinstallation = Datenverlust). Einmalig einen Schlüssel erzeugen und als Repository-Secrets hinterlegen:

```bash
keytool -genkeypair -keystore zahnputz.keystore -alias zahnputz -keyalg RSA -keysize 2048 -validity 20000
base64 -w0 zahnputz.keystore   # → Secret SIGNING_KEYSTORE_BASE64
```
Weitere Secrets: `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`zahnputz`), `SIGNING_KEY_PASSWORD`. Den Keystore nicht committen.

---

# Liegestütz-Tracker 💪 (`pushups/`)

Zweite, unabhängige App nach demselben Prinzip: tägliches Ziel (Standard 12 Liegestütze), Serie, Level/XP, Erfolge, Kalender, Statistik, Erinnerungen.

- **Serie** nach „Never miss twice“: ein einzelner verpasster Tag wird verziehen, zwei in Folge beenden die Serie; Warnhinweis nach einem Fehltag
- **Heute**: Wiederholungen per +1/+5/+10 oder „✓ Rest gemacht“ eintragen; mehr als das Ziel zählt (XP bis zum doppelten Ziel)
- **Erinnerungen**: 1–3 Uhrzeiten, feuern nur, solange das Tagesziel offen ist; Benachrichtigung mit „✓ N gemacht“-Button
- Eigene `applicationId` (`de.liegestuetz.app`) und eigene Datenbank (`liegestuetz.db`) – läuft parallel zur Zahnputz-App
- Module: `:pushups-core` (Logik + Tests), `:pushups-app` (Android); CI-Artifact `liegestuetz-apk`
- Signatur: nutzt dieselben `SIGNING_*`-Secrets wie die Zahnputz-App (siehe oben)
