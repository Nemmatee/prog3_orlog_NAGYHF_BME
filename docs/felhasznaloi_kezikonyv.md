# Felhasználói kézikönyv – Orlog játék (Swing)

## Indítás

- A program Java 21 alatt fut.
- Futtatás Mavenből:
  - `mvn clean package` után a `hu.bme.orlog.App` main futtatásával indul az alkalmazás.
- Indításkor megjelenik az Orlog játéktábla: bal oldalt a tálak és kockák, jobb oldalt a napló és az irányítógombok.

## Főképernyő felépítése

### Bal oldal – Játéktábla
- Felső tál: AI kockái.
- Alsó tál: játékos (You) kockái.
- A tálakon kívül, sorban elhelyezve látszanak a **lezárt kockák**.
- A két játékos életerejét (HP) és favor tokenjeit vizuális jelölések mutatják.

### Jobb oldal – Napló és gombok
- Felső rész: táblázat, amely a játék eseményeit listázza (dobások, favor választás, sebzés összegzés, stb.).
- Alsó rész: két gomb:
  - `God Favor…`
  - `Roll / Next` (kör elején dobás, a 3. után körzárás).

## Új játék indítása

- A program indulásakor automatikusan új játék kezdődik: a játékos (You) és az AI 6–6 kockával indul, teljes életerővel.
- Bármikor indíthatsz új játékot a menüből:
  - `File` → `New`
- Ha egy játékos HP-ja 0-ra csökken, a program jelzi a győztest, és rákérdez, hogy szeretnél-e új játékot indítani.

## God Favors (istenek ereje) kezelése

- A játék elején mindkét játékosnak **3 God Favor-t** kell választania:
  - Az első dobás előtt a program kéri, hogy válassz 3 favor-t a listából.
  - Az AI automatikusan agresszívebb (sebzés-orientált) favor készletet választ.
- A választott 3 favor később minden körben elérhető, ha van elég favor tokened.
- A jobb oldali naplóban rögtön a játék elején megjelenik:
  - `Loadout (You): [...]`
  - `Loadout (AI): [...]`

## Kockadobás és zárolás menete

- Egy körben **legfeljebb 3 dobás** van.
- A kör elején nyomd meg a `Roll / Next` gombot:
  - Az összes **nem zárolt** kocka dobódik.
  - Az AI is dob, majd a saját egyszerű stratégiája alapján pár kockát lezár.
- Egy kocka lezárása/kioldása:
  - A tálban lévő kockára kattintva **LOCK/UNLOCK** között tudsz váltani.
  - A lezárt kockák a tálon kívül, sorban jelennek meg.
- A 3. dobás után a gomb felirata `End round`-ra vált: ekkor a következő megnyomás a kör lezárását indítja.

## God Favor választás körönként

- A `God Favor…` gombbal kör közben bármikor választhatsz favor-t a már előre kiválasztott 3-as készletből:
  - A program először azt ellenőrzi, hogy rendelkezel-e **legalább egy megfizethető favorral**.
  - Ha nincs elég favor token bármelyikhez, tájékoztató üzenetet kapsz, és ebben a körben nem használsz favor-t.
  - Ha van elérhető favor:
    1. Megjelenik egy lista a 3 favor-oddal, innen választasz egyet.
    2. Ezután egy újabb ablakban kiválasztod a **tier**-t (1–3), ami a hatást és az árát határozza meg.
- Az AI is választhat favor-t:
  - Egyszerű heurisztika alapján dönt: a nagy sebzésű, gyógyító vagy token-nyerő favorokat részesíti előnyben, ha tudja fizetni az árát.
- A naplóban rögzítésre kerül:
  - Játékos: `You selected favor: ...`
  - AI: `AI favor: ...`

## Kör lezárása és harc feloldása

- Ha a `Roll / Next` gomb `End round` állapotban van, a megnyomása:
  1. Összegyűjti a két játékos aktuális kocka-eredményeit.
  2. Az `OrlogEngine` kiszámolja:
     - közelharci (melee) sebzést,
     - távolsági (ranged) sebzést,
     - pajzsokat, sisakokat,
     - arany (favor token) és lopás értékeket.
  3. Először bizonyos God Favors **a sebzés kiszámítása előtt** hatnak (pl. sisak eltávolítás).
  4. Ezután lezajlik az alap harc (melee/ranged vs shields/helmets).
  5. Végül az **utóhatású God Favors** érvényesülnek (pl. extra sebzés, gyógyítás, token módosítás).
- A vizuális feloldást a `BoardPanel` animációval jeleníti meg:
  - a HP villan, majd csökken az elszenvedett sebzésnek megfelelően.

### Mi üt mit? – Sebzés és védekezés

- **Közelharci (melee) sebzés vs. pajzs (shield):**
  - A melee ikonok közelharci sebzést jelentenek.
  - A shield ikonok ez ellen védenek.
  - Hatás: a tényleges közelharci sebzés = `melee - shields`, de minimum 0.
- **Távolsági (ranged) sebzés vs. sisak (helmet):**
  - A ranged ikonok távolsági sebzést jelentenek.
  - A helmet ikonok ez ellen védenek.
  - Hatás: a tényleges távolsági sebzés = `ranged - helmets`, de minimum 0.
- **Lopás (steal) vs. favor tokenek:**
  - A steal ikonok favor tokeneket lopnak az ellenféltől.
  - A lopás mértéke a steal ikonok számától függ, de legfeljebb annyit visz el, amennyi tokenje ténylegesen van az ellenfélnek.
- **Arany (gold) ikonok:**
  - Minden gold ikon plusz favor tokent ad a kör végén.

### God Favor hatások részletesen

Minden God Favorból három tier (1–3) érhető el: minél magasabb a tier,
annál erősebb a hatás és annál magasabb a favor költség.

Az alábbi felsorolás az összes elérhető God Favor-t és azok hatását foglalja össze.

- **Thor's Strike** – utóhatású, extra sebző favor
  - **Típus:** Tiszta sebzés a harc után.
  - **Hatás:** Közvetlen extra sebzést okoz az ellenfélnek a már kiszámított melee/ranged sebzés után.
- **Idun's Rejuvenation** – utóhatású, gyógyító favor
  - **Típus:** Gyógyítás.
  - **Hatás:** Visszatölt bizonyos mennyiségű HP-t a játékoson a kör végén.
- **Vidar's Might** – kör eleji, sisakokat eltávolító favor
  - **Típus:** Védelem eltávolítása.
  - **Hatás:** Eltávolítja az ellenfél sisakjainak (helmet) egy részét a sebzés kiszámítása előtt, így a ranged támadások jobban sebeznek.
- **Ullr's Aim** – kör eleji, sisak-védelmet figyelmen kívül hagyó favor
  - **Típus:** Védelem figyelmen kívül hagyása.
  - **Hatás:** A ranged sebzés egy részére/egészére úgy számít, mintha az ellenfél sisakjai nem védenének.
- **Heimdall's Watch** – utóhatású, blokkolt támadások után gyógyító favor
  - **Típus:** Gyógyítás blokkolt találatok után.
  - **Hatás:** Minden sikeresen blokkolt támadás után gyógyít valamennyit.
- **Baldr's Invulnerability** – kör eleji, pajzsokat és sisakokat erősítő favor
  - **Típus:** Erősített védelem.
  - **Hatás:** Megduplázza a pajzsok (shield) és sisakok (helmet) hatását egy körre.
- **Brunhild's Fury** – kör eleji, közelharci sebzést sokszorozó favor
  - **Típus:** Melee erősítés.
  - **Hatás:** A melee sebzést sokszorozza (a tier-től függő szorzóval), ha van elég közelharci ikonod – nagyon erős burst sebzésre.
- **Freyr's Gift** – kör eleji, többségi szimbólumért bónuszt adó favor
  - **Típus:** Bónusz többségért.
  - **Hatás:** Ha valamelyik szimbólumból (pl. melee, ranged, shield, stb.) nálad van többség, extra sebzést vagy token bónuszt ad.
- **Hel's Grip** – utóhatású, bejövő közelharci sebzés alapján gyógyító favor
  - **Típus:** Gyógyítás bejövő melee sebzés alapján.
  - **Hatás:** Minél több közelharci sebzést kapnál, annál többet gyógyulsz a kör végén.
- **Skadi's Hunt** – kör eleji, ranged ikonok után bónuszt adó favor
  - **Típus:** Ranged bónusz.
  - **Hatás:** Extra jutalmat (sebzést vagy tokeneket) kapsz minden ranged ikonod után.
- **Skuld's Claim** – kör eleji, az ellenfél favor tokenjeit égető favor
  - **Típus:** Ellenfél tokenjeinek rombolása.
  - **Hatás:** A ranged ikonjaid alapján favor tokeneket éget el az ellenfélnél.
- **Frigg's Sight** – kör eleji, ellenfél kockáit ideiglenesen tiltó favor
  - **Típus:** Ellenfél kockáinak tiltása.
  - **Hatás:** Egyes ellenfél kockákat "kiiktat" erre a körre, így azok nem vesznek részt a dobásban/harcban.
- **Loki's Trick** – kör eleji, zavaró, kocka-tiltó favor
  - **Típus:** Zavarás, kocka-tiltás.
  - **Hatás:** Hasonlóan Frigg favorjához, ideiglenesen letilt bizonyos ellenfél kockákat, de eltérő költség/mérték mellett.
- **Freyja's Plenty** – kör eleji, favor tokent adó favor
  - **Típus:** Token-szerzés.
  - **Hatás:** Közvetlenül favor tokeneket ad a játékosnak a kör során.
- **Mimir's Wisdom** – utóhatású, elszenvedett sebzés után tokeneket adó favor
  - **Típus:** Token jutalom sebzésért cserébe.
  - **Hatás:** Minden elszenvedett sebzés után extra favor tokeneket kapsz.
- **Bragi's Verve** – utóhatású, sikeres lopások után tokeneket adó favor
  - **Típus:** Token jutalom lopás után.
  - **Hatás:** Minden sikeres steal ikon után plusz favor tokent ad.
- **Odin's Sacrifice** – utóhatású, erős gyógyító favor
  - **Típus:** Erős gyógyítás.
  - **Hatás:** Jelentősebb mennyiségű HP-t tölt vissza a kör végén, magasabb tier-en különösen erős.
- **Var's Bond** – utóhatású, ellenfél token-költése után gyógyító favor
  - **Típus:** Gyógyítás az ellenfél token-költése alapján.
  - **Hatás:** Ha az ellenfél sok favor tokent költött ebben a körben, te ennek arányában gyógyulsz.
- **Thrymr's Theft** – kör eleji, ellenfél favorját/hatásszintjét gyengítő favor
  - **Típus:** Ellenfél favor-jának gyengítése.
  - **Hatás:** Csökkenti az ellenfél választott favor tier-jét vagy elérhető tokenjeit, így az ellenfél gyengébb hatást tud csak használni.

## Naplózás és statisztikák értelmezése

- Minden kör végén több részletes log-sor jelenik meg a jobb oldali táblázatban, például:
  - Favor összefoglaló:
    - `Favor summary (You/AI): +gold Y/A, steal S1/S2, net: ΔYou/ΔAI`
  - Sebzés összefoglaló:
    - `Summary: You dealt X base damage (melee: M, ranged: R) + favor effects`
    - `Summary: AI dealt Y base damage (melee: M2, ranged: R2) + favor effects`
  - Részletek:
    - `Details: You melee ... vs AI shield ... | You ranged ... vs AI helmet ...`
    - `Details: AI melee ... vs Your shield ... | AI ranged ... vs Your helmet ...`
- A napló maximálisan 300 sort tart meg; a legrégebbi bejegyzések automatikusan törlődnek.

## Mentés és betöltés

- **Mentés:**
  - Menü: `File` → `Save...`
  - Fájl kiválasztása után a jelenlegi játékállás (GameState) kerül mentésre.
- **Betöltés:**
  - Menü: `File` → `Load...`
  - A kiválasztott fájlból visszatöltődik a korábbi játékállás:
    - A tábla, a HP, favor tokenek, kockák és napló is frissül.

## Játék vége és újrakezdés

- Amikor bármelyik játékos HP-ja eléri a 0-t:
  - A program jelzi a győztest egy felugró ablakban.
  - Felajánlja, hogy azonnal indítasz-e **új játékot**:
    - Igen: mindkét játékos újraindul teljes HP-val, friss kockákkal és üres naplóval.
    - Nem: az utolsó állapot látható marad, de további lépések már nem hajthatók végre, amíg új játékot nem indítasz (`File > New`).
