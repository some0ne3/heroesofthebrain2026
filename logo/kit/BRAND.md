# Freehand — identyfikacja wizualna

> Wersja 1.0 · 2026-11-14 · Heroes of the Brain 2026
> Źródło prawdy dla logo, kolorów, typografii, kształtów i ruchu. UI/UX aplikacji ma się na tym wzorować.
> Podgląd całości: `brand-sheet.png` (motyw ciemny i jasny).

---

## 1. Marka w jednym zdaniu

**Freehand steruje muzyką, wideo i podcastami gestami twarzy odczytywanymi z opaski EEG — ręce zostają wolne.**

Gesty: pojedyncze / podwójne / długie mrugnięcie, zaciśnięcie szczęki, ruch głowy (kiwnięcie, potrząśnięcie).
Kiedy: gotowanie, trening, jazda, brudne lub zajęte ręce, osoby z ograniczoną sprawnością rąk.

### Charakter

| Jesteśmy | Nie jesteśmy |
|---|---|
| precyzyjni, nowocześni, wiarygodni (prawdziwy BCI) | zabawką ani gadżetem |
| ciepli, pewni siebie, przyjaźni (jak dobry sprzęt audio) | sprzętem medycznym ani laboratoryjnym |
| dostępni z założenia (accessibility to historia marki) | „aplikacją dla chorych” — dostępność bez szpitalnej estetyki |

Słowa-klucze do decyzji projektowych: **ciepło · sygnał · spokój · pewność · dużo powietrza**.

---

## 2. Logo — koncepcja „Wave Eye”

**Oko narysowane jako fala sygnału. Źrenica to sygnał.**

- Pięć elementów: dwa krótkie słupki, dwa wysokie słupki i okrągła źrenica w środku.
- Obrys całości (krótki–wysoki–koło–wysoki–krótki) tworzy migdał oka.
- Słupki czyta się jednocześnie jako sygnał EEG z opaski i jako falę dźwięku, czyli media.
- Ciepła gradacja kolorów od krawędzi do środka (Ember → Flame → Pupil) prowadzi wzrok do źrenicy, czyli do miejsca, w którym rodzi się gest.

### Geometria (siatka 256 × 256, oś pozioma y = 128)

| Element | ID w SVG | Kształt | Wymiary |
|---|---|---|---|
| słupek zewn. lewy | `#bar1` | kapsuła | 26 × 36, x = 14 |
| słupek wewn. lewy | `#bar2` | kapsuła | 26 × 104, x = 54 |
| źrenica | `#pupil` | koło | Ø 68, środek (128, 128) |
| słupek wewn. prawy | `#bar4` | kapsuła | 26 × 104, x = 176 |
| słupek zewn. prawy | `#bar5` | kapsuła | 26 × 36, x = 216 |

- Odstęp między elementami: **14**. Wszystkie końce w pełni zaokrąglone (promień = ½ szerokości).
- Obrys znaku: 228 × 104 (proporcja ≈ 2.2 : 1).
- **Wersja mała (favicon, < 24 px):** tylko `bar2 + pupil + bar4`. Zewnętrzne słupki znikają, bo przy 16 px zlewają się z tłem.

### Wordmark „freehand”

- Własny krój, monoline, rysowany jedną kreską z okrągłymi końcówkami, tak jak słupki znaku. Nie jest to font, więc nie ma problemu z licencją.
- Zawsze **małymi literami**: `freehand`. Tylko w zwykłym tekście piszemy nazwę wielką literą: „Freehand”.
- Parametry: grubość kreski = 20 % wysokości x, brzuszki to idealne okręgi, wydłużenia górne = 1.5 × wysokość x.
- W SVG każda litera to osobna ścieżka: `#f #r #e1 #e2 #h #a #n #d` w grupie `#wordmark`.
- Kreski są obrysem (`stroke`), więc w Remotion można je „rysować” animacją `stroke-dashoffset`, co nawiązuje do nazwy *freehand*.

### Wersje i lockupy

| Plik | Kiedy |
|---|---|
| `freehand-horizontal-*` | domyślny: nagłówki, splash, prezentacje, wideo |
| `freehand-stacked-*` | formaty kwadratowe i pionowe, ekran powitalny, social |
| `freehand-symbol-*` | ikona, awatar, loader, watermark |
| `freehand-wordmark-*` | gdy znak jest już obok (np. ikona aplikacji + nazwa) |

Wariant kolorystyczny = sufiks pliku:

- `-dark`: kolor, na **ciemne** tło;
- `-light`: kolor, na **jasne** tło;
- `-white`: jeden kolor, na zdjęcia i kolorowe tła;
- `-black`: jeden kolor, do druku i grawerów.

### Pole ochronne i minimalne rozmiary

- **Pole ochronne:** wokół logo zostaw wolne miejsce równe **średnicy źrenicy**. W lockupie poziomym to ok. 30 % wysokości znaku.
- **Minimalne rozmiary:**

  | Wersja | Ekran | Druk |
  |---|---|---|
  | lockup poziomy | 96 px szer. | 25 mm |
  | znak (pełna wersja) | 24 px szer. | — |
  | znak, wersja mała | 16 px | — |

### Czego nie robić

- Nie zmieniaj kolejności kolorów: ciemniejszy kolor zawsze na zewnątrz, jaśniejszy w środku.
- Nie zamieniaj źrenicy na inny kształt (play, serce, kropkę z obwódką).
- Nie dodawaj obrysów, cieni, poświaty ani gradientów wewnątrz elementów.
- Nie rozciągaj, nie obracaj i nie zmieniaj odstępów między słupkami.
- Nie stawiaj wersji `-dark` na jasnym tle ani `-light` na ciemnym, bo kontrast spada.
- Nie składaj wordmarka innym fontem. Nie pisz go wielką literą („Freehand” tylko w tekście).
- Nie zestawiaj z ikonami słuchawek ani mózgu.

---

## 3. Kolor

### 3.1 Kolory marki (gradacja „Warm ramp”)

| Token | Na ciemne tło | Na jasne tło | Rola |
|---|---|---|---|
| `ember` | `#E5483A` | `#C93328` | zewnętrzne słupki; akcent mocny, rzadki |
| `flame` | `#FF7A45` | `#E35A26` | wewnętrzne słupki; **kolor główny UI (primary)** |
| `pupil` | `#FFC247` | `#EA9A0E` | źrenica; **stan aktywny, wykryty gest, „live”** |

Proporcje w interfejsie: **neutralne 80 % · flame 12 % · pupil 5 % · ember 3 %**.
Pupil to kolor „sygnału”: używaj go oszczędnie, tylko gdy coś się właśnie dzieje (gest wykryty, odtwarzanie trwa, opaska połączona).

### 3.2 Neutralne (ciepła szarość, nigdy chłodna)

| Token | Dark | Light | Użycie |
|---|---|---|---|
| `background` | `#16120F` | `#FAF6F1` | tło ekranu |
| `surface` | `#201A16` | `#FFFFFF` | karty, bottom sheet |
| `surface-2` | `#2A231E` | `#F1EAE2` | pola, chipy, wyróżnione karty |
| `outline` | `#3D342D` | `#DDD2C6` | linie podziału (dekoracyjne) |
| `text-primary` | `#F7EFE6` | `#1E1814` | tekst główny |
| `text-secondary` | `#B8ACA0` | `#6B5F55` | podpisy, metadane |
| `text-tertiary` | `#7D7166` | `#968A80` | tylko podpowiedzi i placeholdery |

**Motyw ciemny jest domyślny**, bo tak działają aplikacja i wideo promocyjne. Jasny jest pełnoprawny, ale drugi.

### 3.3 Semantyczne

| Token | Dark | Light | Uwaga |
|---|---|---|---|
| `success` | `#5CC98A` | `#1E7F4F` | opaska połączona, kalibracja OK |
| `warning` | = `pupil` | `#B8701A` | słaby sygnał, mała bateria |
| `error` | `#FF6B6B` | `#C62828` | brak sygnału, rozłączono |

Czerwień błędu jest blisko `ember`. Dlatego błąd **zawsze** ma ikonę i tekst, nigdy sam kolor. `ember` nie oznacza błędu.

### 3.4 Kontrast (WCAG 2.x, zmierzone)

| Para | Ratio | Ocena |
|---|---|---|
| text-primary / background (dark · light) | 16.4 · 16.3 | AAA |
| text-secondary / background (dark · light) | 8.4 · 5.8 | AA |
| text-tertiary / background (dark · light) | 3.9 · 3.1 | tylko duży tekst i hinty |
| `#16120F` na `flame` (przycisk dark) | 7.2 | AAA |
| `#FFFFFF` na `#B8461F` (przycisk light) | 5.3 | AA |
| pupil `#FFC247` / surface dark | 10.7 | ok |
| pupil light `#EA9A0E` / background light | 2.1 | **tylko grafika/logo, nigdy tekst** |

### 3.5 Kolory gestów (chipy, overlay potwierdzenia, ustawienia mapowania)

| Gest | Kolor | Ikona (rysowana kreską 2 dp) |
|---|---|---|
| mrugnięcia (single/double/long) | `pupil` | oko: migdał + kropka |
| zaciśnięcie szczęki | `flame` | dwa łuki zbliżające się do siebie |
| ruch głowy (nod/shake) | `ember` | łuk ze strzałką |

Kolor gestu zawsze idzie w parze z ikoną i nazwą, bo część użytkowników nie rozróżnia kolorów.

---

## 4. Typografia

Wordmark jest rysowany ręcznie i **nie jest fontem UI**. Do interfejsu używamy dwóch krojów na licencji OFL (Google Fonts):

| Rola | Krój | Dlaczego |
|---|---|---|
| Display / nagłówki / liczby | **Outfit** (SemiBold 600, Medium 500) | geometryczny, okrągłe „e/a/o”, rodzina z wordmarkiem |
| Tekst / UI | **Inter** (Regular 400, Medium 500, SemiBold 600) | czytelność w małych rozmiarach, cyfry tabelaryczne |

Zapas, jeśli nie da się dociągnąć fontów: systemowy `sans-serif` (Roboto) dla obu.

### Skala (sp; interfejs czytany z odległości ramienia, więc **większa niż standard M3**)

| Styl | Krój | Rozmiar / interlinia | Użycie |
|---|---|---|---|
| `display` | Outfit 600 | 44 / 48 | ekran „Teraz gra”: stan, wielki zegar |
| `headline` | Outfit 600 | 30 / 36 | tytuł utworu |
| `title` | Outfit 500 | 22 / 28 | nagłówki sekcji, wykonawca |
| `body-lg` | Inter 400 | 18 / 26 | treść, opisy gestów |
| `body` | Inter 400 | 16 / 24 | domyślny tekst |
| `label` | Inter 600 | 15 / 20, +0.2 tracking | przyciski, chipy |
| `caption` | Inter 500 | 13 / 18 | metadane; **minimum w aplikacji** |

Zasady:

- Dla tekstu na zdjęciu okładki zapewnij półprzezroczystą warstwę `background` z kryciem 60 %.
- Liczby i czasy (np. 2:14) zawsze z `fontFeatureSettings = "tnum"`.
- Ten sam rozmiar nigdy w dwóch różnych grubościach na jednym ekranie.

---

## 5. Kształt, siatka, ikonografia

Motyw przewodni: **kapsuła i koło**, czyli elementy znaku.

| Element | Reguła |
|---|---|
| Przyciski główne | kapsuła (pełne zaokrąglenie), wys. **56 dp** |
| Duży przycisk akcji (play/pause) | koło Ø **88 dp** (echo źrenicy) |
| Karty | promień **24 dp** |
| Bottom sheet | promień **28 dp** (tylko górne rogi) |
| Chipy, tagi, przełączniki | kapsuła |
| Pola tekstowe | promień 16 dp |
| Siatka odstępów | **8 dp** (pół-krok 4 dp); marginesy ekranu 20 dp |
| Cele dotykowe | **min. 56 dp** (więcej niż 48 dp z M3, z myślą o osobach z ograniczoną precyzją ruchów) |

**Ikony:** siatka 24 dp, kreska 2 dp, okrągłe końce i łączenia, tak jak wordmark. Najlepiej pasuje Material Symbols *Rounded*, weight 400 (zestawy *Sharp* i *Outlined* odpadają). Wypełnienie ikony tylko dla stanu aktywnego.

**Cień i głębia:** zamiast cieni rozróżniamy warstwy tonem: `surface` → `surface-2`. Jedyna dozwolona poświata to łagodna poświata wokół elementu w kolorze `pupil` przy wykryciu gestu (opisana w §6).

---

## 6. Motyw graficzny „sygnał” i ruch

Słupki i źrenica z logo to **żywy element interfejsu**, nie tylko dekoracja.

| Komponent | Co pokazuje | Zachowanie |
|---|---|---|
| **Signal meter** (5 elementów jak w logo) | jakość sygnału z opaski | wysokości słupków = poziom sygnału; szare (`outline`) = brak kontaktu elektrod |
| **Pupil dot** | stan systemu | `pupil` pulsuje = nasłuch; pełne = gest rozpoznany; `text-tertiary` = pauza |
| **Gesture toast** | potwierdzenie gestu | kapsuła u dołu ekranu: ikona gestu + nazwa akcji („Podwójne mrugnięcie · Pauza”), 1.2 s |
| **Now playing** | odtwarzanie | słupki delikatnie oddychają w rytm (amplituda ≤ 15 %) |

### Ruch

| Animacja | Przebieg | Czas i easing |
|---|---|---|
| **Blink** (sygnatura marki) | słupki `scaleY` → 0.15 i z powrotem, źrenica `scaleY` → 0.1 | zamknięcie 90 ms `ease-in`, przytrzymanie 40 ms, otwarcie 160 ms `ease-out` |
| **Gesture detected** | źrenica `scale` 1 → 1.15 → 1 plus poświata `pupil` | 280 ms, ease-out-back |
| **Wejście ekranu** | fade + przesunięcie 12 dp w górę | 220 ms `FastOutSlowIn` |
| **Rysowanie wordmarka** (intro, promo) | `stroke-dashoffset` litera po literze, opóźnienie 60 ms | 900 ms łącznie |

Zasady ruchu:

- Ruch zawsze **potwierdza akcję** i nigdy nie dekoruje bez powodu.
- Szanuj systemowe „usuń animacje” (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`): wtedy tylko zmiana koloru i stanu, bez skalowania.
- Nic nie miga szybciej niż 3 razy na sekundę.

---

## 7. Dostępność (część marki, nie dodatek)

- Każdy gest ma też **dotykowy odpowiednik** w UI. Aplikacja nie może wymagać EEG do podstawowej obsługi.
- Potwierdzenie gestu przekazujemy na kilka sposobów: wizualnie (toast), dźwiękiem (krótki „tick”, wyłączalny) i opcjonalnie wibracją.
- Wszystkie interaktywne elementy mają `contentDescription`. Signal meter ma opis słowny, np. „Sygnał: dobry”.
- Obsługa skalowania tekstu do 200 %: układy elastyczne, bez ucinania tytułów (zamiast tego marquee lub dwie linie).
- Kontrast tekstu ≥ 4.5:1 (patrz §3.4). Stan nigdy nie jest komunikowany samym kolorem.

---

## 8. Głos i ton

- Krótko, ciepło, rzeczowo. Mówimy na „ty”.
- Opisujemy, co się stało, a nie jak działa EEG: „Mrugnij dwa razy, żeby zatrzymać”, a nie „Wykryto artefakt EOG”.
- Bez medycznego słownictwa („pacjent”, „terapia”, „niepełnosprawność” w UI). Dostępność pokazujemy czynem, nie etykietą.
- Błędy mówią, co zrobić: „Słaby kontakt opaski — popraw ją na czole”.

---

## 9. Tokeny dla kodu (Jetpack Compose, Material 3)

```kotlin
// ui/theme/Color.kt
import androidx.compose.ui.graphics.Color

object FreehandColors {
    // brand — dark-mode values
    val Ember = Color(0xFFE5483A)
    val Flame = Color(0xFFFF7A45)
    val Pupil = Color(0xFFFFC247)
    // brand — light-mode values
    val EmberLight = Color(0xFFC93328)
    val FlameLight = Color(0xFFE35A26)
    val PupilLight = Color(0xFFEA9A0E)
    val Rust = Color(0xFFB8461F)          // primary on light (white text 5.3:1)

    // neutrals dark
    val Ink900 = Color(0xFF16120F)        // background
    val Ink800 = Color(0xFF201A16)        // surface
    val Ink700 = Color(0xFF2A231E)        // surface-2
    val Ink600 = Color(0xFF3D342D)        // outline
    val Cream = Color(0xFFF7EFE6)         // text-primary
    val Sand = Color(0xFFB8ACA0)          // text-secondary
    // neutrals light
    val Paper = Color(0xFFFAF6F1)
    val PaperDim = Color(0xFFF1EAE2)
    val Linen = Color(0xFFDDD2C6)
    val InkText = Color(0xFF1E1814)
    val Taupe = Color(0xFF6B5F55)

    val SuccessDark = Color(0xFF5CC98A); val SuccessLight = Color(0xFF1E7F4F)
    val ErrorDark = Color(0xFFFF6B6B);   val ErrorLight = Color(0xFFC62828)
}
```

```kotlin
// ui/theme/Theme.kt
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.heroesofthebrain.halo.ui.theme.FreehandColors as C

val FreehandDark = darkColorScheme(
    primary = C.Flame, onPrimary = C.Ink900,
    primaryContainer = Color(0xFF4A2414), onPrimaryContainer = Color(0xFFFFD9C9),
    secondary = C.Pupil, onSecondary = C.Ink900,
    tertiary = C.Ember, onTertiary = C.Cream,
    background = C.Ink900, onBackground = C.Cream,
    surface = C.Ink800, onSurface = C.Cream,
    surfaceVariant = C.Ink700, onSurfaceVariant = C.Sand,
    outline = C.Ink600, outlineVariant = C.Ink700,
    error = C.ErrorDark, onError = C.Ink900,
)

val FreehandLight = lightColorScheme(
    primary = C.Rust, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCB), onPrimaryContainer = Color(0xFF3A1406),
    secondary = C.PupilLight, onSecondary = C.InkText,
    tertiary = C.EmberLight, onTertiary = Color.White,
    background = C.Paper, onBackground = C.InkText,
    surface = Color.White, onSurface = C.InkText,
    surfaceVariant = C.PaperDim, onSurfaceVariant = C.Taupe,
    outline = C.Linen, outlineVariant = C.PaperDim,
    error = C.ErrorLight, onError = Color.White,
)
```

Kształty M3: `small = RoundedCornerShape(16.dp)`, `medium = RoundedCornerShape(24.dp)`, `large = RoundedCornerShape(28.dp)`, przyciski `CircleShape` lub `RoundedCornerShape(50)`.

`primaryContainer` (`#4A2414` / `#FFDCCB`) to kolory dobrane, ale nie wyliczone z palety. Sprawdź je na ekranie.

---

## 10. Pliki w kicie

```
kit/
├─ BRAND.md                ← ten dokument
├─ brand-sheet.png/.html   ← podgląd całej identyfikacji (dark + light)
├─ svg/                    ← mastery wektorowe (viewBox bez width/height, przezroczyste)
│   freehand-{symbol|wordmark|horizontal|stacked}-{dark|light|black|white}.svg
├─ png/                    ← te same, 2048 px szerokości, przezroczyste
├─ icons/
│   ├─ android/res/        ← gotowe do skopiowania do app/src/main/res/
│   │    mipmap-anydpi-v26/ic_launcher(.xml|_round.xml)  (adaptive: background + foreground + monochrome)
│   │    drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml  (VectorDrawable, znak w strefie 66 dp)
│   │    values/ic_launcher_background.xml  (#16120F)
│   │    mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher(_round).png  (Android < 8)
│   ├─ play-store-512.png  ← ikona do Google Play (pełne tło, sklep sam przycina)
│   ├─ app-icon-{dark|light|round}.svg + -1024.png
│   ├─ favicon.svg         ← wersja mała (3 elementy) na kafelku
│   └─ web/                ← favicon.ico (16/32/48), favicon-*.png, apple-touch-icon, icon-192/512, maskable-512
├─ build_kit.py            ← generator wszystkiego (py -3 build_kit.py --png)
└─ brand_sheet.py          ← generator arkusza
```

### Podpięcie ikony w aplikacji

1. Skopiuj `icons/android/res/*` do `app/src/main/res/`.
2. W `AndroidManifest.xml` w `<application>` dodaj:
   `android:icon="@mipmap/ic_launcher"` i `android:roundIcon="@mipmap/ic_launcher_round"`.
3. Opcjonalnie zmień `android:label` na `Freehand`.

### Remotion

Użyj `svg/freehand-horizontal-dark.svg` lub `svg/freehand-symbol-dark.svg` jako inline JSX. Elementy do animacji:

- `#symbol` → `#bar1 #bar2 #pupil #bar4 #bar5`: przy skalowaniu ustaw `transform-box: fill-box; transform-origin: center`;
- `#wordmark` → `#f #r #e1 #e2 #h #a #n #d`: kreski, więc działa `pathLength` + `stroke-dashoffset`.

Scenariusz intro:

1. Pusty ekran.
2. Źrenica pojawia się ze skalowaniem.
3. Słupki wyrastają od środka na zewnątrz.
4. Mrugnięcie.
5. Wordmark rysuje się literami.

---

## 11. Otwarte kwestie

- **Wordmark jako kreska:** wordmark jest zbudowany z kresek (`stroke`), co ułatwia animację. Do druku lub cięcia (np. folia) zrób kopię z kreskami zamienionymi na obrysy (Inkscape: *Ścieżka → Obrys na ścieżkę*).
- **Znak towarowy:** nie przeprowadzono badania znaków towarowych ani wyszukiwania obrazem. Przed publicznym startem sprawdź EUIPO/UPRP.
- **Bliskość Deezera:** gradacja kolorów na słupkach jest stylistycznie blisko equalizera Deezera. Odróżniają nas symetria, sylwetka oka i źrenica; nie zmieniaj tych proporcji.
- **Pupil na jasnym tle:** ma kontrast 2.1:1. W logo wystarcza, ale tego koloru nie używaj do tekstu ani cienkich ikon na jasnym tle.
