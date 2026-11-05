# Auto Rynek Skibidi Gabrys - 2.0 (projekt zrodlowy, NIEPRZETESTOWANY)

Fabric / Minecraft 1.21.4 / Java 21. To nie jest gotowy plik JAR.
Nie przeprowadzono kompilacji ani testu w Minecraft ani na anarchia.gg.
Srodowisko autora paczki nie ma Javy, Gradle ani sieci do pobrania zaleznosci.
Uzywaj tylko tam, gdzie regulamin pozwala na automatyzacje. Brak obchodzenia antycheata.

## Budowanie
Zainstaluj JDK 21 i Gradle 8.12, otworz katalog z build.gradle i wykonaj:

    gradle build

Wynik: build/libs/auto-rynek-skibidi-gabrys-2.0.jar
Wgraj go razem z Fabric API dla 1.21.4 do katalogu mods profilu Fabric 1.21.4.
Alternatywa: zaladuj zawartosc katalogu projektu do wlasnego repozytorium GitHub,
uruchom workflow Build Fabric 1.21.4 w Actions i pobierz artefakt po udanym buildzie.
Workflow dopiero ma sie wykonac; nie jest dowodem ze projekt sie kompiluje.
Paczka nie zawiera Gradle wrappera ani cudzych binarnych plikow.

## Sterowanie
- Prawy Shift: otwiera panel i ZATRZYMUJE automat.
- Z nad slotem w dowolnym oknie ekwipunku/rynku: dodaje wskazany przedmiot.
- Z poza oknem: dodaje przedmiot trzymany w glownej rece.
- C poza edytorem konfiguracji: start/stop. Start jest tez w zakladce Rynek.
- Wyglad: bialy/czarny, motyw jasny/ciemny, akcent (morski/fiolet/bursztyn),
  skala szerokosci panelu 70-120%, przelacznik stopki.
- Zakupy: lista najlepszych (najtanszych za sztuke) zakupow, licznik sprawdzonych
  ofert, licznik pominietych i suma wydana, przycisk czyszczenia listy oraz
  przelacznik trybu ceny: za sztuke albo za cala oferte.
- Rynek: lista z edytowalna cena, zapis i usuwanie, strony listy,
  pole na kod JSON CFG (Ctrl+V), Wczytaj CFG i Kopiuj do schowka.
- O modzie: lokalnie zapisywane pole licencji. Nie jest to system aktywacji.

Cena reguly to limit zalezny od trybu: w trybie "unit" (domyslnym) mod dzieli cene
oferty przez liczbe sztuk z ikony i porownuje z limitem ZA SZTUKE; w trybie "offer"
porownuje cene calej oferty. Serwer NIE podaje liczby sztuk w opisie, wiec tryb
"unit" opiera sie na liczbie wyswietlanej na ikonie w slocie - jesli ta liczba nie
jest liczba sztuk, wroc do trybu "offer" jednym przyciskiem w zakladce Zakupy. Dopasowanie obejmuje
identyfikator Minecraft i dokladna nazwe przedmiotu, ale nie wszystkie komponenty!
Przedmioty o tej samej nazwie i ID, lecz innych enchantach/lore NIE sa rozrozniane.
Nie uzywaj tego dopasowania do wartosciowych przedmiotow wymagajacych weryfikacji enchantow.
Nie ma wbudowanej czcionki z makiety ani dokladnego odwzorowania grafiki;
GUI uzywa fontu Minecrafta. Skala zmienia panel, nie globalna skale tekstu.

## Kalibracja serwera - czesciowo potwierdzona
Z prawdziwych tooltipow serwera: cena to linia "» Cena produktu: $1.65k ($1,650)",
a okno zakupu potwierdza sie LEWYM klikiem na minecraft:lime_dye z nazwa "Zakup".
Parser obsluguje zapis z "k", nawias z dokladna kwota oraz separatory przecinkiem
i spacja. Nie obsluguje przyrostka "m" ani "~". Brak dopasowania = nie kupuje.
Gdy nawias podaje inna kwote niz zapis z "k", mod NIE kupuje (dane sprzeczne).

1. Otworz /ah recznie. Najedz na oferte i nacisnij Z.
2. Uzyj Kopiuj opis do kalibracji. Porownaj rzeczywista linie ceny z priceRegex
   w pliku config/auto-rynek-skibidi-gabrys.json. Regex musi przechwytywac
   w grupie 1 cene calej oferty. Nigdy nie dopasowuj pierwszej dowolnej liczby!
3. POTWIERDZONE na serwerze: sortowanie to minecraft:repeater w slocie 52 i
   przelacza sie PRAWYM klikiem ("Kliknij PRAWYM, aby przelaczyc!"). Lista opcji:
   Brak motywu, Najwyzszej ceny, Najnizszej ceny, Najnowsze, Najstarsze - dlatego
   mod klika 3 razy prawym. Najnowsze rozpoznaje tez po napisie w lore (kazdy kolor).
   Ustawienia: sortItem, sortSlot, sortClicks w pliku config.
4. Zalozony uklad to skrzynia 6x9: oferty 0-44, sortowanie 52,
   poprzednia strona 48 (red_dye), nastepna 50 (lime_dye lub green_dye).
5. Okno zakupu tez nazywa sie "Rynek", dlatego rozrozniamy je po rozmiarze okna:
   6 rzedow = lista ofert, mniejsze = zakup. W oknie zakupu mod szuka przedmiotu
   minecraft:lime_dye z nazwa zawierajaca "zakup", sprawdza, czy widoczna cena jest
   identyczna z cena oferty, i klika LEWYM. Przy braku zielonego lub ceny, przy
   roznej cenie albo przy braku nazwy "zakup" automat sie zatrzymuje i nie kupuje.
   Pola confirmationTitle/confirmationSlot sa nieuzywane (zostawione dla starego CFG).
6. Pierwszy test: jeden tani przedmiot, maly maxSessionSpend, obserwacja reczna.

## Szybkosc i zabezpieczenia
Domyslnie odstep 150 ms, minimum 100 ms, najwyzej jedna akcja na tick klienta.
Nie wysyla kolejnego klikniecia do identycznej zawartosci: czeka na zmiane tytulu,
nazw, liczebnosci lub lore. Brak zmiany 3 sekundy = STOP. Serwer moze miec oferty
identyczne tekstowo, wtedy ten konserwatywny detektor tez zatrzyma automat.
Sortowanie moze kliknac do 5 razy, ale czeka na aktualizacje kazdego klikniecia.
Po sprawdzeniu ofert przewija naprzemiennie strony: 1-2-1-2 itd., nie caly rynek.
Po zakupie i zamknieciu okna wysyla /ah; nie spamuje komenda co tick.
Nie zamyka sam nieznanych okien i zatrzymuje sie po rozlaczeniu/innym serwerze.
Nie ma gwarancji zakupu jako pierwszy ani maksymalnej szybkosci na serwerze.

Budzet maxSessionSpend domyslnie 10000, rozliczany konserwatywnie przy WYSYLANIU
klikniecia zakupu, nawet jesli zakup sie nie uda. Licznik nie jest dowodem zakupu.
Nie zeruje sie od samego start/stop; zeruje go ponowne uruchomienie klienta.
Po awarii/rozlaczeniu automat nie wlacza sie sam.

## Braki / zakres
To eksperymentalny adapter oparty na screenach, a nie sprawdzony mod produkcyjny.
Potwierdzenie transakcji, formaty ceny i komunikaty serwera wymagaja testow.
Nie ma weryfikacji rzeczywistego salda, aktywacji licencji, wgrywania pliku
przeciagnieciem ani automatycznego rozpoznawania niestandardowych mechanik.
Import CFG dziala jako wklejenie JSON; zapis config jest lokalny.
Brak zewnetrznego logowania, telemetrii, ukrytych polaczen i serwera licencyjnego.
