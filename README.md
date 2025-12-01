# Orlog – Java Swing megvalósítás

Ez a projekt a **Norse Orlog** jellegű dobókocka-játék egyszerű, Java 21 alapú, Swing felülettel ellátott megvalósítása.

## Projekt felépítése

- `src/main/java/hu/bme/orlog/`
  - `App.java` – belépési pont, elindítja a grafikus felületet.
  - `model/` – játékszabályok és modell:
    - `GameState`, `Player`, `DiceSet`, `Die`, `Face`
    - `GodFavor`, `GodFavorCatalog`, `OrlogEngine`
  - `ui/` – Swing felület:
    - `OrlogFrame` – fő ablak, menük, gombok, napló
    - `BoardPanel` – játéktábla rajzolása, animációk
    - `LogTableModel` – napló táblázat modellje
  - `io/` – mentés/betöltés:
    - `SaveLoadService` – `GameState` mentése és visszatöltése
- `src/test/java/hu/bme/orlog/model/`
  - Egységtesztek a modellhez és a motorhoz.
- `docs/`
  - `felhasznaloi_kezikonyv.md` – felhasználói dokumentáció
  - `osztalydiagram.md` – osztálydiagram mermaid-ben

## Követelmények

- **Java 21** (vagy kompatibilis JDK)
- **Maven** (legalább 3.x)

## Fordítás és futtatás

A projekt Maven segítségével fordítható és futtatható.

```bash
cd "d:/Work/Egyetem/3_felev/prog 3/prog3_orlog_NAGYHF_BME"
mvn clean package

# Futtatás (Mavenből)
mvn exec:java -Dexec.mainClass="hu.bme.orlog.App"

# Vagy közvetlenül a target/classes-ből (ha a CLASSPATH be van állítva)
# java -cp target/classes hu.bme.orlog.App
```

## Játékmenet röviden

- A játékos és az AI 6–6 kockával játszik, 3 dobás/kör.
- Kattintással lehet a kockákat LOCK/UNLOCK állapotba tenni.
- A `God Favor…` gombbal lehet istenerőket választani és aktiválni.
- A `Roll / Next` gomb dobást, majd a 3. dobás után körlezárást indít.
- A jobb oldali napló a dobásokat, sebzéseket, favor-hatásokat és összegzéseket mutatja.

A részletes játékszabályok és a God Favor-ok magyarázata a `docs/felhasznaloi_kezikonyv.md` fájlban található.

## Dokumentáció

- **Felhasználói kézikönyv:**
  - `docs/felhasznaloi_kezikonyv.md` (szükség esetén PDF-be exportálható)
- **Osztálydiagram:**
  - `docs/osztalydiagram.md` – mermaid kóddal, amely könnyen PNG/PDF-re exportálható

## Licenc

A projekt kizárólag oktatási/gyakorlati céllal készült egyetemi beadandóhoz.