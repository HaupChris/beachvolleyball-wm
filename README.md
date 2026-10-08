# Habits 🌱

Android-App (Kotlin, Jetpack Compose) zum Aufbauen und Abgewöhnen von Gewohnheiten – komplett offline, ohne Server.
Ersetzt die früheren Einzel-Apps (Zahnputz-, Liegestütz-Tracker); beides lässt sich als Habit (Vorlagen) anlegen.

## Konzept
- **Habit** = Ziel + Rhythmus + Laufzeit + Erinnerungen
  - **Angewöhnen** (abhaken oder Menge, z. B. 20 Wdh.) oder **Abgewöhnen** (Ausrutscher eintragen, oder „höchstens n pro Tag“)
  - **Rhythmus**: feste Wochentage (jeder Tag zählt einzeln) oder **x-mal pro Woche** frei verteilt (die Woche zählt)
  - **Laufzeit**: unbegrenzt oder **Challenge** mit Enddatum und eigener Erfolgsschwelle (z. B. 80 %)
  - Pausieren (Urlaub/Krankheit) – pausierte Tage zählen weder als verpasst noch für die Serie
  - Zieländerungen gelten ab heute; die Vergangenheit wird nach den damaligen Zielen bewertet
- **Serien** nach „Never miss twice“: eine verpasste Periode wird verziehen, zwei in Folge beenden die Serie
- **Gefestigt**: ≥ 66 Tage alt und ≥ 80 % erfüllt in den letzten 66 Tagen (Orientierung an Lally et al., 2009)
- **Gamification**: globales Level/XP über alle Habits, Erfolge (Serien, perfekte Tage, Challenges, gefestigte Habits)

## Oberfläche
- **Heute**: nur die heute fälligen Habits, Antippen = abhaken / Menge eintragen
- **Habits**: laufend, pausiert, abgeschlossen, Archiv; Detailseite mit Kalender (nachtragen), Wochenquoten, Challenge-Stand
- **Fortschritt**: Level, Ø-Quote (Woche/Monat, filterbar nach laufend/alle), Tabelle pro Habit (schwächste zuerst), Aktivitäts-Heatmap, Erfolge

## Aufbau
- `core/` – reine Kotlin-Domänenlogik (Perioden, Serien, Quoten, Challenges, XP, Erfolge) mit Unit-Tests
- `app/` – Compose-UI, SQLite (Habits als JSON, Einträge pro Tag), Erinnerungen via `AlarmManager`

## APK bauen & installieren
- **CI**: Jeder Push baut über GitHub Actions ein APK → Actions → Lauf → Artifact `habits-apk`.
- **Lokal**: `./gradlew :app:assembleRelease` (benötigt Android SDK) oder Android Studio.

### Stabile Signatur (empfohlen)
Ohne eigenen Schlüssel wird mit einem wechselnden Debug-Key signiert – ein neues APK lässt sich dann nicht als Update installieren (Deinstallation = Datenverlust). Einmalig einen Schlüssel erzeugen und als Repository-Secrets hinterlegen:

```bash
keytool -genkeypair -keystore habits.keystore -alias habits -keyalg RSA -keysize 2048 -validity 20000
base64 -w0 habits.keystore   # → Secret SIGNING_KEYSTORE_BASE64
```
Weitere Secrets: `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`habits`), `SIGNING_KEY_PASSWORD`. Den Keystore nicht committen.

