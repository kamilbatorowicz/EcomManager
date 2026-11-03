# EcomManager

System webowy do automatyzacji sprzedaży wielokanałowej (Omnichannel), dynamicznego ustalania cen, tworzenia zestawów i prognozowania zapasów. Projekt realizowany w ramach pracy inżynierskiej.

## Cel projektu

Aplikacja ma monitorować oferty dostawców na podstawie adresów URL, przeliczać ceny zgodnie z regułami biznesowymi i aktualizować oferty we własnych kanałach sprzedaży. Celem jest ograniczenie ręcznych aktualizacji oraz ryzyka sprzedaży towaru, którego nie można już dostarczyć.

Docelowy przepływ:

1. Użytkownik wkleja link do produktu dostawcy.
2. System rozpoznaje platformę i pobiera dane oferty.
3. Użytkownik przypisuje produkt do oferty sprzedażowej i ustala regułę ceny.
4. Aplikacja wylicza cenę i aktualizuje ofertę w kanale docelowym.
5. Zadania cykliczne odświeżają dane, zapisując historię wyników i błędów.

## Aktualny stan i MVP

Repozytorium zawiera konfigurację lokalnego środowiska i dokumentację. Backend, frontend, integracje i algorytmy biznesowe nie zostały jeszcze zaimplementowane. Opis funkcjonalności poniżej określa planowany zakres systemu.

Pierwsza wersja obejmie jedno źródło danych, kalkulator ceny i aktualizację produktu w WooCommerce na zewnętrznym hostingu. Kolejne integracje, zestawy i prognozy zostaną dodane po uruchomieniu pełnego przepływu dla jednego produktu.

## Stack technologiczny

| Obszar | Technologia | Zastosowanie |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3 | REST API i logika biznesowa |
| Dane | Spring Data JPA, PostgreSQL 16 | Produkty, reguły i historia |
| Zadania w tle | Spring `@Scheduled` | Cykliczna synchronizacja |
| Klient HTTP | Spring RestClient | Integracje z usługami zewnętrznymi |
| Frontend | React, Tailwind CSS | Responsywny panel zarządzania |
| Środowisko | Docker Compose | Lokalna baza PostgreSQL |
| Sklep zewnętrzny | WordPress, WooCommerce | Osobna strona na hostingu, komunikacja przez REST API |

Frontend będzie korzystać ze wspólnych stylów przechowywanych poza kodem HTML, z zachowaniem spójności i responsywności.

## Planowane funkcjonalności

### Ekstrakcja danych z URL

- Rozpoznawanie obsługiwanej platformy i identyfikatora oferty.
- Pobieranie nazwy, ceny, waluty i dostępności przez API lub JSON-LD, zależnie od źródła.
- Zapisywanie czasu odczytu i historii zmian.
- Obsługa błędnych linków, nieobsługiwanych platform i niedostępnych ofert.
- Walidacja domen oraz przekierowań i blokowanie zapytań do adresów lokalnych i prywatnych.

Liczba sztuk będzie zapisywana tylko wtedy, gdy źródło ją udostępnia. Status „dostępny” nie jest liczbowym stanem magazynowym.

### Dynamiczne ustalanie cen

- Koszt zakupu, koszty dodatkowe, marża i prowizja platformy.
- Przewalutowanie według kursów NBP i zapis kursu użytego do wyliczenia.
- Cena minimalna (Floor Price) i zaokrąglenia psychologiczne, np. `,99 PLN`.
- Podgląd składników wyliczenia i historia aktualizacji.
- Wstrzymywanie aktualizacji przy brakujących lub nieaktualnych danych.

W uproszczonym modelu `K` oznacza całkowity koszt, `p` prowizję proporcjonalną do ceny sprzedaży, a `m` marżę od ceny sprzedaży:

```text
cena = K / (1 - p - m)
```

Wymagane są `p + m < 1` i spójna podstawa netto lub brutto. Marża nie jest narzutem. Po zaokrągleniu należy ponownie sprawdzić minimalną cenę i opłacalność. Do obliczeń pieniężnych backend będzie używać `BigDecimal`.

### Dynamiczne zestawy (Bundles)

- Definiowanie składników i liczby sztuk w zestawie.
- Wyliczanie rabatu z kontrolą minimalnej opłacalności.
- Wyznaczanie dostępności na podstawie ograniczającego składnika.

```text
stan_zestawu = min(floor(stan_składnika / liczba_sztuk_w_zestawie))
```

Dla jednej sztuki każdego z dwóch produktów wzór upraszcza się do `min(stan_A, stan_B)`. Produkty sprzedawane jednocześnie osobno i w zestawach wymagają kontroli wspólnego zapasu i rezerwacji.

### Prognozowanie zapasów

- Ważona średnia krocząca (WMA) dziennej sprzedaży, z większą wagą nowszych dni.
- Szacowanie czasu wyczerpania własnego zapasu i sygnalizowanie ryzyka braków.
- Obsługa niewystarczającej historii oraz zerowej sprzedaży.

```text
WMA = suma(waga_dnia * sprzedaż_dnia) / suma(wag)
dni_do_wyczerpania = dostępny_zapas / WMA
```

Prognoza dla dostawcy wymaga historii liczbowych stanów i będzie oznaczana jako szacunkowa. Spadek obserwowanego stanu nie musi oznaczać sprzedaży, a sam status dostępności nie wystarcza do prognozowania liczby dni.

### Synchronizacja w tle

- Cykliczne odświeżanie ofert i limitowanie zapytań osobno dla integracji.
- Obsługa `429`, limitów czasu oraz ponowień z rosnącym opóźnieniem.
- Ochrona przed równoczesną synchronizacją tej samej oferty.
- Historia uruchomień i błędów oraz pomijanie zapisów bez zmiany wartości.

Sam `@Scheduled` nie zapewnia limitowania zapytań ani blokady pomiędzy wieloma instancjami. Te mechanizmy wymagają osobnej implementacji.

## Planowane integracje

| Rola | Platformy | Metoda integracji |
| --- | --- | --- |
| Odczyt | Allegro, eBay, Amazon, AliExpress | API, eBay Browse API, usługi pośredniczące, np. RapidAPI, lub JSON-LD, zależnie od źródła |
| Zapis i testy | Allegro Sandbox, eBay Sandbox | API platform i wymagana autoryzacja |
| Zapis i testy | Shopify Dev | Admin API GraphQL |
| Zapis i testy | WooCommerce na zewnętrznym hostingu | WooCommerce REST API przez HTTPS |
| Kursy walut | NBP | API kursów walut |

To plan rozwoju, a nie lista działających integracji. Dla każdej platformy trzeba potwierdzić dostęp do danych, uprawnienia, limity, koszty i zasady korzystania. Środowisko testowe nie gwarantuje bezpłatnego dostępu do wszystkich danych i usług pośredniczących.

## Architektura i dane

Planowana architektura to modularny monolit Spring Boot z osobnym frontendem React. Adaptery platform będą oddzielone od logiki biznesowej, aby algorytmy można było testować bez zewnętrznych API.

| Moduł | Odpowiedzialność |
| --- | --- |
| `catalog` | Produkty i powiązania ofert |
| `integrations` | Adaptery platform oraz kursy NBP |
| `pricing` | Reguły i obliczenia cen |
| `inventory` | Stany, historia i prognozy |
| `bundles` | Skład, ceny i dostępność zestawów |
| `jobs` | Harmonogramy, limity, ponowienia i historia |

Podstawowe planowane encje to `Product`, `SupplierOffer`, `SalesOffer`, `PriceRule`, `OfferObservation` i `SyncExecution`. W kolejnych etapach dojdą encje zestawów oraz historii sprzedaży i zapasów. Skalowanie wymaga dodatkowej koordynacji zadań pomiędzy instancjami. Obecny Compose służy do lokalnego developmentu.

## Uruchomienie lokalne

### Wymagania

- Docker i Docker Compose v2, np. Docker Desktop na Windows.
- Internet przy pierwszym pobraniu obrazu PostgreSQL oraz do komunikacji ze sklepem.
- Wolny port `5432` lub inny ustawiony w `.env`.

Java 21 oraz Node.js będą potrzebne po dodaniu aplikacji. Compose uruchamia obecnie wyłącznie infrastrukturę.

### 1. Ustawienia środowiska

W katalogu projektu skopiuj plik ustawień i zmień przykładowe hasła:

```powershell
Copy-Item .env.example .env
```

Linux/macOS:

```sh
cp .env.example .env
```

Plik `.env` jest pomijany przez Git. Zmiana hasła w `.env` po inicjalizacji bazy nie zmienia hasła w istniejącej bazie.

### 2. Uruchomienie usług

```sh
docker compose up -d
docker compose ps
```

| Usługa | Dostęp z komputera | Przeznaczenie |
| --- | --- | --- |
| `postgres` | `localhost:5432` | Baza EcomManager |

PostgreSQL jest dostępny tylko na interfejsie lokalnym, a jego dane są zachowywane w nazwanym wolumenie. Compose nie uruchamia WordPressa, WooCommerce ani bazy sklepu.

### 3. Sklep na zewnętrznym hostingu

WordPress z WooCommerce będzie osobną stroną na zewnętrznym hostingu. EcomManager będzie komunikować się z nią przez REST API; baza sklepu jest utrzymywana niezależnie przez hosting.

1. Zainstaluj WordPress i WooCommerce na hostingu oraz skonfiguruj domenę z HTTPS.
2. W ustawieniach WordPress włącz bezpośrednie odnośniki inne niż proste.
3. Dodaj produkt testowy z ceną i włączonym zarządzaniem stanem magazynowym.
4. W ustawieniach WooCommerce, w sekcji zaawansowanej REST API, wygeneruj klucz odczytu i zapisu dla użytkownika uprawnionego do zarządzania produktami.
5. Zachowaj adres sklepu, Consumer Key i Consumer Secret do przyszłej konfiguracji backendu. Klucze przechowuj wyłącznie po stronie backendu, poza repozytorium.

Przykładowy adres API: `https://twoj-sklep.pl/wp-json/wc/v3`. Backend może działać lokalnie i łączyć się ze sklepem przez internet. Frontend React komunikuje się z backendem Spring Boot, który korzysta z PostgreSQL i API sklepu.
### Połączenie przyszłego backendu

Spring Boot uruchomiony na komputerze będzie korzystać z następujących ustawień:

| Zmienna | Wartość |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ecom_manager` |
| `SPRING_DATASOURCE_USERNAME` | `POSTGRES_USER` z `.env` |
| `SPRING_DATASOURCE_PASSWORD` | `POSTGRES_PASSWORD` z `.env` |
| Adres WooCommerce | Adres HTTPS sklepu na zewnętrznym hostingu |

Dostosuj porty i nazwę bazy do `.env`. Compose odczytuje ten plik automatycznie; backend na komputerze wymaga osobnego ustawienia zmiennych środowiskowych. Backend dodany w przyszłości do tej samej sieci Compose użyje hosta `postgres:5432` dla bazy. Adres zewnętrznego sklepu pozostanie taki sam. Konfiguracja integracji WooCommerce zostanie dodana wraz z implementacją backendu.

### Obsługa środowiska

```sh
docker compose logs -f
docker compose stop
docker compose start
docker compose down
```

`docker compose down` usuwa kontenery i sieć, zachowując dane. Poniższe polecenie usuwa również wolumen z danymi lokalnej bazy PostgreSQL. Nie wpływa na sklep na zewnętrznym hostingu:

```sh
docker compose down -v
```

## Plan realizacji

1. Konfiguracja lokalnej bazy i sklepu testowego na zewnętrznym hostingu.
2. Backend z migracjami bazy i katalogiem produktów.
3. Ręczny odczyt jednego źródła i historia obserwacji.
4. Kalkulator ceny i testy reguł biznesowych.
5. Aktualizacja produktu WooCommerce i panel React.
6. Harmonogramy, limity, ponowienia i kontrola aktualności danych.
7. Zestawy i prognozowanie zapasów.
8. Kolejne integracje, pomiary i dokumentacja pracy.

## Plan weryfikacji

- Testy cen, marż, zaokrągleń, zestawów i WMA, również dla danych granicznych.
- Testy integracyjne bazy oraz aktualizacji oferty WooCommerce.
- Scenariusze niedostępnego źródła, braku kursu, odpowiedzi `429` i przerwanego połączenia.
- Kontrola skutków ponawiania operacji i braku aktualizacji przy nieaktualnych danych.
- Porównanie błędu prognozy WMA ze zwykłą średnią na tych samych danych historycznych, bez używania przyszłych danych do prognozy.

## Struktura repozytorium

```text
EcomManager/
├── .env.example
├── .gitignore
├── compose.yaml
└── README.md
```

Katalogi backendu i frontendu zostaną dodane wraz z ich implementacją.
