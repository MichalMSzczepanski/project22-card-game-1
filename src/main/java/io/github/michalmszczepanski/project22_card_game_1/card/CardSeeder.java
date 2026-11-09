package io.github.michalmszczepanski.project22_card_game_1.card;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds a starter set of the free "Generic" pack. The full freemium pack targets
 * 300 cards; this is a representative subset to get the MVP running end to end.
 */
@Component
public class CardSeeder implements ApplicationRunner {

    private static final List<String> QUESTION_CARDS = List.of(
            "Najgorszy prezent, jaki można dostać na urodziny: ___",
            "Co zepsuło imprezę w ostatniej chwili? ___",
            "Sekret, którego nikt nie powinien poznać: ___",
            "Co znalazło się w walizce po wakacjach? ___",
            "Powód, dla którego spóźniłem się do pracy: ___",
            "Co szef usłyszał na zebraniu i udał, że nie słyszał? ___",
            "Co było w SMS-ie wysłanym do złej osoby? ___",
            "Co stało się viralem w tym tygodniu? ___",
            "Co babcia powiedziała na Wigilii? ___",
            "Co zepsuło pierwszą randkę? ___",
            "Co znaleziono w szkolnej szafce? ___",
            "Czego nie można zrobić bez ___?",
            "Co było najdziwniejszym pytaniem na rozmowie o pracę? ___",
            "Co zostawiono w Uberze? ___",
            "Czym tłumaczył się sąsiad o 3 w nocy? ___"
    );

    private static final List<String> ANSWER_CARDS = List.of(
            "Niekończące się formularze urzędowe",
            "Mój były/moja była",
            "Tajna grupa na WhatsAppie",
            "Trzy kawy i żadnego planu",
            "Nieodebrane połączenie od mamy",
            "Podejrzanie dobra passa",
            "Cała szafa butów, w których nikt nie chodzi",
            "Instrukcja obsługi, której nikt nie czyta",
            "Chomik na sterydach",
            "Zapasowa kombinacja do sejfu",
            "Afera na grupie osiedlowej",
            "Kryzys wieku średniego w wieku 25 lat",
            "Niewyjaśniona plama na suficie",
            "Drugie śniadanie zjedzone o 16",
            "Playlista wstydu",
            "Fałszywy alarm pożarowy",
            "Cała paczka ciastek w jedną noc",
            "Niezapłacony mandat z 2019 roku",
            "Egzystencjalny kryzys przy kasie samoobsługowej",
            "Grupa wsparcia dla singli",
            "Zbyt szczera opinia cioci",
            "Trzy zakładki na jedną serię",
            "Awaria internetu w najgorszym momencie",
            "Nieplanowana drzemka na cztery godziny",
            "Legendarna awantura o pilota do telewizora"
    );

    private static final List<String> SPICY_QUESTION_CARDS = List.of(
            "Co zepsuło najlepszą randkę w życiu? ___",
            "Czego absolutnie nie wolno robić po pijaku? ___",
            "Co wydarzyło się na kawalerskim, o czym nikt nie mówi? ___",
            "Co znaleziono pod łóżkiem po imprezie? ___",
            "Co jest najgorszą rzeczą do powiedzenia w łóżku? ___",
            "Czym wytłumaczyłbyś/wytłumaczyłabyś zdradę? ___",
            "Co było najgłośniejszym odgłosem z sąsiedniego pokoju w hotelu? ___",
            "Czego nie można oglądać z rodzicami w pokoju? ___",
            "Co zepsuło nastrój w sypialni? ___",
            "Co kelner zobaczył pod stolikiem? ___",
            "Co trzeba wytłumaczyć lekarzowi na pogotowiu po imprezie? ___",
            "Co znalazło się w historii wyszukiwania, której nikt nie powinien zobaczyć? ___",
            "Co jest najgorszą nazwą użytkownika na Tinderze? ___",
            "Czego nigdy nie robi się na pierwszej randce? ___",
            "Co wypadło z kieszeni na lotniskowej kontroli? ___",
            "Co usłyszała cała klatka schodowa przez ściany? ___",
            "Co było powodem rozwodu po trzech tygodniach małżeństwa? ___",
            "Co zamówiono po dziesięciu kolejkach wódki? ___",
            "Czym straszy się dzieci, żeby nie weszły bez pytania? ___",
            "Co znalazła teściowa, sprawdzając szafkę nocną? ___"
    );

    private static final List<String> SPICY_ANSWER_CARDS = List.of(
            "Niekontrolowana erekcja w windzie",
            "Butelka wódki schowana w doniczce",
            "Seks o 3 w nocy z kimś, kogo imienia nie pamiętasz",
            "Niewypowiedziany kłębek fetyszy",
            "Trójkąt, który skończył się kłótnią o pilota",
            "Kac moralny trwający trzy dni",
            "Przypadkowy striptiz na stole kuchennym",
            "Zbyt entuzjastyczne jęki przez ścianę",
            "Wibrator w walizce na kontroli bezpieczeństwa",
            "Niewinne pytanie 'masz coś na dziś wieczór?'",
            "Żart o analnym seksie, który poszedł o krok za daleko",
            "Chlanie samotnie przed lustrem",
            "Niekontrolowane gazy w najgorszym możliwym momencie",
            "Nagie zdjęcie wysłane do złej osoby z kontaktów",
            "Pijacki SMS do byłego o 4 w nocy",
            "Orgazm udawany tak głośno, że sąsiedzi zaklaskali",
            "Striptizerka wynajęta przez pomyłkę na urodziny babci",
            "Trzy kieliszki wina i całkowita utrata zasad moralnych",
            "Fetysz, o którym nikt nie powinien wiedzieć",
            "Niekończąca się dyskusja o tym, kto był na górze",
            "Prezerwatywa znaleziona w kieszeni płaszcza na Wigilię",
            "Pijany striptiz przy ognisku",
            "Niespodziewana erekcja podczas prezentacji w pracy",
            "Zbyt szczegółowy opis nocy poślubnej",
            "Chwila ciszy po zbyt głośnym 'kocham cię' w łóżku",
            "Niekontrolowane wymioty w Uberze po seksie w toalecie klubu",
            "Zapomniana bielizna erotyczna w praniu u rodziców",
            "Trójkąt zakończony umową o podziale kanapy",
            "Nagi sąsiad podlewający kwiatki o świcie",
            "Butelka wina wypita samotnie przed śniadaniem",
            "Niekontrolowany orgazm podczas wideokonferencji",
            "Zdrada wyznana po dziesięciu shotach tequili",
            "Zbyt szczera opinia o rozmiarze",
            "Analny plug znaleziony przez dziecko w szufladzie",
            "Niewytłumaczalna wysypka po szalonej nocy",
            "Pijacki numer telefonu seksuolożki zapisany w kontaktach jako 'MAMA 2'",
            "Trzy godziny seksu i zero orgazmów",
            "Zestaw BDSM znaleziony przez listonosza",
            "Wymówka 'to nie to, na co wygląda' przy związanych rękach",
            "Kac tak silny, że modlitwa wydaje się logiczną opcją"
    );

    private static final List<String> QUESTION_CARDS_2 = List.of(
            "Co zepsuło grilla sąsiadów? ___",
            "Czego nie wolno robić na focie do CV? ___",
            "Co było powodem kłótni na wspólnym wyjeździe integracyjnym? ___",
            "Co znaleziono w szufladzie po przeprowadzce? ___",
            "Czym tłumaczył się kierowca autobusu za spóźnienie? ___",
            "Co sprawiło, że cała klasa wybuchła śmiechem na lekcji? ___",
            "Co zepsuło pierwszy dzień w nowej pracy? ___",
            "Czego nie powinno się mówić teściom przy pierwszym obiedzie? ___",
            "Co znalazło się w prezencie od Mikołajka w pracy? ___",
            "Co było najgorszym tematem do small talku w windzie? ___",
            "Czym wytłumaczyć spóźnienie na własny ślub? ___",
            "Co zrobiło z imprezy urodzinowej katastrofę? ___",
            "Co usłyszano przez słuchawki, które ktoś zapomniał wyłączyć? ___",
            "Co zepsuło zdjęcie klasowe? ___",
            "Czego nie wolno robić na basenie miejskim? ___",
            "Co było w paczce, która przyszła nie na ten adres? ___",
            "Co zrujnowało pierwszy występ na scenie? ___",
            "Czym wytłumaczyć nieobecność na własnym egzaminie? ___",
            "Co znaleziono w kieszeni kurtki po zimie? ___",
            "Co sprawiło, że sąsiedzi zaczęli się unikać? ___",
            "Co było powodem paniki na lotnisku? ___",
            "Czego nie wolno powiedzieć na pogrzebie? ___",
            "Co zepsuło romantyczną kolację? ___",
            "Co znalazł listonosz pod wycieraczką samochodu? ___",
            "Czym wytłumaczyć brak prezentu na imieninach? ___",
            "Co sprawiło, że cała rodzina przestała rozmawiać na Wigilii? ___",
            "Co było w plecaku, który zgubiono w pociągu? ___",
            "Czego nie wolno robić w kościele? ___",
            "Co zrujnowało wymarzony urlop? ___",
            "Co usłyszał cały autobus, gdy telefon zadzwonił w ciszy? ___",
            "Czym tłumaczyć się przed psem za spóźniony spacer? ___",
            "Co znaleziono w bucie po festiwalu? ___",
            "Co było powodem ewakuacji z biurowca? ___",
            "Czego nie wolno robić na zebraniu rodziców w szkole? ___",
            "Co zepsuło pierwszy dzień na diecie? ___"
    );

    private static final List<String> ANSWER_CARDS_2 = List.of(
            "Niewyjaśniony zapach w lodówce",
            "Awaria windy w bloku na dziesiątym piętrze",
            "Kolejka do kasy, która się nie rusza",
            "Zgubiony pilot od samochodu na dnie torby",
            "Nieskończona lista zakupów napisana na serwetce",
            "Przypadkowe polubienie zdjęcia z dwa lata temu",
            "Szafka z lekami, w której nic nie jest aktualne",
            "Niewyjaśniona dziura w skarpetce w ważnym dniu",
            "Zbyt długa kolejka do toalety na imprezie",
            "Kalendarz, w którym nikt nie zapisuje spotkań",
            "Grupa znajomych, która nigdy się nie zbiera w całości",
            "Nieodebrana paczka awizowana trzy razy",
            "Zapasowy klucz, którego nikt nie pamięta gdzie jest",
            "Rachunek za telefon wyższy niż za czynsz",
            "Nieplanowany remont, który trwa już rok",
            "Zgubiona skarpetka w pralce",
            "Niewyjaśniona plama na nowej kanapie",
            "Lista noworocznych postanowień z zeszłego roku",
            "Awaria ogrzewania w najmroźniejszy dzień roku",
            "Zbyt entuzjastyczny sąsiad z wiertarką w sobotę rano",
            "Niekończąca się rozmowa o pogodzie",
            "Zapas papieru toaletowego na wypadek końca świata",
            "Niewyjaśniony hałas na strychu",
            "Dramatyczne westchnienie przy odbieraniu wyniku ważenia",
            "Trzy nieodebrane przypomnienia o wizycie u dentysty",
            "Zapomniane hasło do własnej skrzynki mailowej",
            "Nieoczekiwana wizyta teściów bez uprzedzenia",
            "Zgubiony parasol w dniu największej ulewy",
            "Niewypite litry herbaty, która wystygła",
            "Zbyt szczegółowa opowieść o śnie",
            "Cała szuflada z kablami, które do niczego nie pasują",
            "Niewyjaśnione zniknięcie jednego widelca w kuchni",
            "Awaria budzika w dniu ważnego egzaminu",
            "Niekończący się spór o temperaturę w pokoju",
            "Zapomniane urodziny najlepszego przyjaciela",
            "Trzy nieodpowiedziane wiadomości od szefa",
            "Niewyjaśniona kolekcja gumek do włosów w aucie",
            "Zepsuty parasol w dniu rozmowy o pracę",
            "Zbyt długie przemówienie na weselu kuzyna",
            "Niekończące się szukanie okularów, które są na głowie",
            "Zapomniany termin płatności za prąd",
            "Awaria klimatyzacji w najgorętszy dzień lata",
            "Niewyjaśniona plama po winie na obrusie babci",
            "Zbyt entuzjastyczne odliczanie do Nowego Roku o 23:45",
            "Cała paczka chipsów zjedzona przed filmem",
            "Niewyjaśniony zapach spalenizny w kuchni",
            "Zgubiona karta do autobusu w najgorszym momencie",
            "Trzy alarmy, które nikogo nie budzą",
            "Niekończąca się dyskusja o tym, czyja kolej na zmywanie",
            "Zapomniany parasol w pociągu",
            "Awaria drukarki tuż przed ważnym wydrukiem",
            "Niewyjaśnione zniknięcie jednej skarpetki z całej pary",
            "Zbyt entuzjastyczny powitalny skok psa przy drzwiach",
            "Cała noc spędzona na układaniu puzzli",
            "Niewyjaśniona lista zakupów z rzeczami, których nikt nie pamięta",
            "Zgubiony bilet na pociąg minutę przed odjazdem",
            "Trzy nieudane próby upieczenia tego samego tortu",
            "Niekończące się szukanie wspólnego filmu na wieczór",
            "Zapomniana data rocznicy",
            "Awaria rowerowej lampki w środku nocy",
            "Niewyjaśniona ilość pustych kartonów po pizzy",
            "Zbyt entuzjastyczne odpowiadanie na pytanie \"jak się masz\"",
            "Cała szafka z przyprawami, które przeszły termin",
            "Niewyjaśnione zniknięcie pieniędzy ze wspólnej kasy na wycieczkę",
            "Zgubiona ładowarka w ostatniej chwili przed wyjazdem",
            "Trzy nieodebrane telefony od listonosza",
            "Niekończące się szukanie klucza w torbie przy drzwiach",
            "Zapomniane hasło do WiFi gości",
            "Awaria telewizora w dniu finału sezonu",
            "Niewyjaśniona ilość niedopasowanych pojemników bez pokrywek",
            "Zbyt entuzjastyczne machanie do kogoś, kto machał do innej osoby",
            "Cała grupa, która umawia się na \"coś w tym tygodniu\" od miesiąca",
            "Niewyjaśnione zniknięcie ulubionego długopisu",
            "Zgubiona lista prezentów świątecznych",
            "Trzy nieudane próby złożenia mebla z instrukcją w obrazkach",
            "Niekończąca się kolejka po szczepienie",
            "Zapomniana nazwa filmu, który \"koniecznie trzeba obejrzeć\"",
            "Awaria domofonu w dniu dostawy mebli",
            "Niewyjaśniona plama na ścianie korytarza",
            "Zbyt entuzjastyczne opowiadanie o treningu na siłowni",
            "Cała lista subskrypcji, o których zapomniano",
            "Niewyjaśnione zniknięcie jednej rękawiczki",
            "Zgubiona recepta w dniu wizyty w apteki",
            "Trzy nieodczytane powiadomienia o promocjach",
            "Niekończące się szukanie wolnego miejsca parkingowego",
            "Zapomniana kolejka do odbioru zamówienia",
            "Awaria windy w dniu przeprowadzki na szóste piętro",
            "Niewyjaśniona ilość reklamówek pod zlewem",
            "Zbyt entuzjastyczne machanie fajerwerkami",
            "Cała noc spędzona na przeglądaniu starych zdjęć",
            "Niewyjaśnione zniknięcie ulubionej przyprawy tuż przed gotowaniem",
            "Zgubiony numerek do szatni",
            "Trzy nieudane próby zaparkowania równolegle",
            "Niekończąca się dyskusja o tym, kto wyniesie śmieci",
            "Zapomniane hasło do konta, które ustawiono \"na później\"",
            "Awaria pralki w dniu, gdy trzeba spakować walizkę",
            "Niewyjaśniona ilość magnesów na lodówce bez żadnego powodu",
            "Zbyt entuzjastyczne świętowanie drobnego sukcesu",
            "Cała lista \"zrobię to w weekend\"",
            "Niewyjaśnione zniknięcie jednego kapcia",
            "Zgubiony bilet na koncert w dniu wyjścia",
            "Trzy nieodebrane rachunki leżące w szufladzie",
            "Niekończące się szukanie wspólnej daty na spotkanie",
            "Zapomniana nazwa piosenki, która \"chodzi po głowie\"",
            "Awaria żarówki w najmniej dogodnym momencie",
            "Niewyjaśniona ilość niedopitych butelek wody po całym domu",
            "Zbyt entuzjastyczne opowiadanie o dziecku znajomych",
            "Cała szafka pełna toreb na zapas",
            "Niewyjaśnione zniknięcie jednej baterii z pilota",
            "Zgubiony dowód osobisty w dniu odbioru paczki",
            "Trzy nieudane próby nauczenia się języka przez aplikację",
            "Niekończące się szukanie klucza do piwnicy",
            "Zapomniana data przeglądu samochodu",
            "Awaria routera w środku ważnego filmu",
            "Niewyjaśniona ilość długopisów, które nie piszą",
            "Zbyt entuzjastyczne odliczanie kalorii przy stole wigilijnym",
            "Cała noc spędzona na planowaniu wycieczki, która nigdy się nie odbyła",
            "Niewyjaśnione zniknięcie ulubionego kubka z biura",
            "Zgubiona kartka z listą haseł",
            "Trzy nieodebrane zaproszenia na wesele",
            "Niekończąca się kolejka do lekarza rodzinnego",
            "Zapomniana nazwa ulicy, na którą miało się skręcić",
            "Awaria zmywarki w dniu przyjęcia gości",
            "Niewyjaśniona ilość niesparowanych skarpetek w szafie",
            "Zbyt entuzjastyczne machanie do kamery na zdjęciu grupowym"
    );

    private static final List<String> OFFICE_QUESTION_CARDS = List.of(
            "Co zepsuło spotkanie integracyjne całego zespołu? ___",
            "Czego nie wolno mówić na rozmowie oceniającej? ___",
            "Co znalazło się w prezentacji wysłanej do klienta przez przypadek? ___",
            "Co było powodem awaryjnego restartu serwera w piątek o 17:00? ___",
            "Czym wytłumaczyć brak kamery na całej rozmowie wideo? ___",
            "Co usłyszano na spotkaniu, mimo że mikrofon miał być wyciszony? ___",
            "Co zepsuło premierę nowego systemu księgowego? ___",
            "Czego nie można napisać w odpowiedzi \"do wszystkich\"? ___",
            "Co było tematem plotek przy ekspresie do kawy? ___",
            "Co zrujnowało prezentację kwartalnych wyników? ___",
            "Czym tłumaczył się dział IT za trzeci dzień awarii? ___",
            "Co znalazło się w agendzie spotkania, które trwało trzy godziny bez powodu? ___"
    );

    private static final List<String> OFFICE_ANSWER_CARDS = List.of(
            "Kolejne spotkanie, które powinno być mailem",
            "Excel z formułami, których nikt już nie rozumie",
            "Nieskończona nitka maili w kopii do całej firmy",
            "Szkolenie z komunikacji prowadzone przez kogoś, kto nie słucha",
            "Kawa z automatu, która smakuje jak zupa",
            "Raport na \"jutro rano\", zlecony w piątek o 16:58",
            "Pracownik, który zawsze ma \"jedną szybką sprawę\" na koniec dnia",
            "Nowa strategia firmy ogłoszona przez komunikat na trzy slajdy",
            "Ergonomiczne krzesło zamówione pół roku temu, które nigdy nie przyszło",
            "Spotkanie o reorganizacji, po którym nic się nie zmienia",
            "Freelancer, który odpowiada po trzech tygodniach ciszy",
            "Firmowy event integracyjny z obowiązkową zabawą w kręgu",
            "Nowy system do raportowania godzin, wolniejszy od poprzedniego",
            "Prezentacja z trzydziestoma slajdami na pięciominutowe spotkanie",
            "Dział HR wysyłający ankietę satysfakcji po zwolnieniach",
            "Open space, w którym nie da się skupić ani na chwilę",
            "Status \"zaraz wracam\" trwający od trzech godzin",
            "Feedback 360 stopni, który nikogo nie zaskoczył",
            "Awaria VPN w dniu deadline'u",
            "Kolejna reorganizacja struktury, o której nikt nie wie, co zmienia",
            "Benefit w postaci karty na siłownię, z której nikt nie korzysta",
            "Spotkanie statusowe o statusie innego spotkania statusowego",
            "Pracownik miesiąca wybrany losowo z listy obecności",
            "Firmowy czat pełen emotek i żadnych odpowiedzi",
            "Target kwartalny ustalony bez pytania zespołu o zdanie"
    );

    private static final List<String> POPCULTURE_QUESTION_CARDS = List.of(
            "Co jest największym dramatem na szkolnej grupie klasowej w internecie? ___",
            "Czego nie wolno robić pod viralowym filmikiem w komentarzach? ___",
            "Co sprawiło, że serial, który obiecałeś dokończyć, leży odłożony od trzech lat? ___",
            "Co było w relacji, którą usunięto po dwóch minutach? ___",
            "Czym tłumaczy się influencer, gdy produkt, który reklamował, okazał się bezwartościowy? ___",
            "Co zepsuło finał serialu, na który czekano pięć sezonów? ___",
            "Co znalazło się w historii wyszukiwania po maratonie seriali o trzeciej w nocy? ___",
            "Czego nie wolno pisać pod zdjęciem znajomego, który właśnie się rozstał? ___",
            "Co stało się viralowym memem, którego nikt już nie pamięta skąd się wziął? ___",
            "Co sprawiło, że cała grupa na czacie przestała ze sobą rozmawiać po jednym komentarzu? ___",
            "Czym wytłumaczyć dwanaście godzin spędzonych na scrollowaniu bez celu? ___",
            "Co było powodem, dla którego algorytm zaczął pokazywać tylko jedną rzecz na feedzie? ___"
    );

    private static final List<String> POPCULTURE_ANSWER_CARDS = List.of(
            "Serial, który obiecałeś dokończyć od trzech lat",
            "Viralowy filmik, którego nikt nie pamięta nazwy",
            "Influencer sprzedający suplementy na odporność",
            "Podcast, który słuchasz tylko w tle, nie pamiętając o czym jest",
            "Teoria spiskowa wymyślona w komentarzach pod filmikiem",
            "Postać z serialu, którą wszyscy nienawidzą bardziej niż złoczyńcę",
            "Playlist algorytmu, który uparł się na jedną piosenkę z dzieciństwa",
            "Streamer grający w to samo od ośmiu godzin bez przerwy",
            "Quiz internetowy mówiący, jakim typem ciasta jesteś",
            "Grupa fanów kłócąca się o zakończenie, które twórcy już dawno zmienili",
            "Filtr na zdjęciu, który zmienia twarz w coś nierozpoznawalnego",
            "Trend taneczny, którego nikt już nie pamięta, skąd się wziął",
            "Komentarz pod zdjęciem, który wywołał wojnę na cały tydzień",
            "Serial kryminalny obejrzany w całości w jedną noc",
            "Aplikacja, która wie o tobie więcej niż najlepszy przyjaciel",
            "Recenzent oceniający wszystko jedną gwiazdką z zasady",
            "Bohater serialu fantasy, którego imienia nikt nie umie wymówić",
            "Dramat w komentarzach pod przepisem na sernik",
            "Celebryta, który zrobił coś kontrowersyjnego i zniknął na pół roku",
            "Nowy odcinek, który wyciekł dzień przed premierą",
            "Grupa dyskusyjna analizująca finał serialu klatka po klatce",
            "Challenge internetowy, który skończył się w szpitalu",
            "Playlist na siłownię, która nagle przeszła w smutne piosenki",
            "Spoiler wklejony w tytule filmiku bez ostrzeżenia",
            "Fanpage z cytatami, które nikt nigdy nie powiedział"
    );

    private static final List<String> KIDS_QUESTION_CARDS = List.of(
            "Co zepsuło urodzinową niespodziankę? ___",
            "Co zrobił piesek, kiedy nikt nie patrzył? ___",
            "Czego boi się potwór pod łóżkiem? ___",
            "Co znalazło się w kieszeni po praniu? ___",
            "Co powiedziała papuga sąsiadów? ___",
            "Co było najdziwniejsze w szkolnej jadłodajni? ___",
            "Czym tłumaczył się kotek za porysowaną kanapę? ___",
            "Co zepsuło wycieczkę klasową do zoo? ___",
            "Co znalazło się w prezencie od cioci? ___",
            "Czego nie wolno robić na basenie? ___"
    );

    private static final List<String> KIDS_ANSWER_CARDS = List.of(
            "Skarpetka, która zniknęła w pralce",
            "Dinozaur, który bał się burzy",
            "Balon, który uciekł do nieba",
            "Pingwin w za dużym kapturze",
            "Dwa gumisie skłócone o ostatni cukierek",
            "Zapach spalonych naleśników",
            "Kotek śpiący w pudełku zamiast w swoim łóżku",
            "Niewidzialny przyjaciel, który zjadł ostatnie ciastko",
            "Robot zrobiony z kartonowych pudełek",
            "Piesek, który ukradł kapcia",
            "Tęczowy jednorożec na rowerku",
            "Zbyt głośne kichnięcie podczas cichego czytania",
            "Zgubiona bajka na dobranoc",
            "Smok, który boi się własnego ognia",
            "Pizza z dodatkami, których nikt nie zamówił",
            "Zabawka, która piszczy w środku nocy",
            "Przedszkolak, który nie chce zjeść brokułów",
            "Śnieżna kula, która stopniała na parapecie",
            "Klaun z balonowym kapeluszem, który pękł",
            "Rybka, która nie chce wyjść z akwarium"
    );

    private static final List<String> NYE_QUESTION_CARDS = List.of(
            "Co zepsuło sylwestrową imprezę o północy? ___",
            "Co było w noworocznym postanowieniu, które złamano pierwszego dnia? ___",
            "Co wystrzeliło razem z fajerwerkami? ___",
            "Czego nie wolno mówić o północy przy toaście? ___",
            "Co zepsuło wspólne odliczanie do Nowego Roku? ___",
            "Co znalazło się w kotylionie zamiast confetti? ___",
            "Czym wytłumaczyć brak szampana o północy? ___",
            "Co było najgorszym prezentem sylwestrowym? ___",
            "Co usłyszano zamiast życzeń noworocznych? ___",
            "Co zrujnowało pierwszy dzień nowego roku? ___"
    );

    private static final List<String> NYE_ANSWER_CARDS = List.of(
            "Fajerwerk, który strzelił do wewnątrz",
            "Lista postanowień przepisywana z zeszłego roku",
            "Zgubiony but podczas tańca o północy",
            "Toast wzniesiony za coś, czego nikt nie pamięta",
            "Confetti sprzątane do lutego",
            "Zbyt entuzjastyczne odliczanie, które zaczęło się godzinę wcześniej",
            "Szampan otwarty niezgodnie z instrukcją",
            "Noworoczne postanowienie złamane przed śniadaniem",
            "Przebranie na bal, które się rozpadło przed północą",
            "Zasypianie dziesięć minut przed odliczaniem",
            "Petarda odpalona w niewłaściwym kierunku",
            "Kieliszek, który pękł podczas toastu",
            "Playlist sylwestrowa z jedną piosenką w kółko",
            "Fajerwerki sąsiadów trwające do piątej rano",
            "Zgubiony telefon w trakcie odliczania",
            "Trzy postanowienia noworoczne i zero realizacji",
            "Przypadkowy pocałunek o północy z niewłaściwą osobą",
            "Kotylion, który rozpadł się przed dwunastą",
            "Zapas szampana, który skończył się przed północą",
            "Pierwsza kłótnia nowego roku przed śniadaniem"
    );

    private final CardPackRepository cardPackRepository;
    private final CardRepository cardRepository;

    public CardSeeder(CardPackRepository cardPackRepository, CardRepository cardRepository) {
        this.cardPackRepository = cardPackRepository;
        this.cardRepository = cardRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cardRepository.count() > 0) {
            return;
        }

        CardPack genericPack = cardPackRepository.save(
                new CardPack("Generyczny", true, BigDecimal.ZERO, PackAudience.FAMILY));

        QUESTION_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, genericPack)));
        ANSWER_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, genericPack)));
        QUESTION_CARDS_2.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, genericPack)));
        ANSWER_CARDS_2.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, genericPack)));

        CardPack spicyPack = cardPackRepository.save(
                new CardPack("Pikantna", false, new BigDecimal("9.00"), PackAudience.ADULT));

        SPICY_QUESTION_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, spicyPack)));
        SPICY_ANSWER_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, spicyPack)));

        CardPack officePack = cardPackRepository.save(
                new CardPack("Biurowa", false, new BigDecimal("9.00"), PackAudience.FAMILY));

        OFFICE_QUESTION_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, officePack)));
        OFFICE_ANSWER_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, officePack)));

        CardPack popCulturePack = cardPackRepository.save(
                new CardPack("Pop-kultura", false, new BigDecimal("9.00"), PackAudience.FAMILY));

        POPCULTURE_QUESTION_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, popCulturePack)));
        POPCULTURE_ANSWER_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, popCulturePack)));

        // Free kids pack — the strict, always-clean option for "family mode" / young players.
        CardPack kidsPack = cardPackRepository.save(
                new CardPack("Dla dzieci", true, BigDecimal.ZERO, PackAudience.KIDS));

        KIDS_QUESTION_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, kidsPack)));
        KIDS_ANSWER_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, kidsPack)));

        // Seasonal/limited pack: only purchasable/usable Dec 15 - Jan 15 each year.
        LocalDate today = LocalDate.now();
        int seasonYear = today.getMonthValue() == 1 ? today.getYear() - 1 : today.getYear();
        CardPack nyePack = cardPackRepository.save(new CardPack(
                "Sylwestrowa", false, new BigDecimal("9.00"), PackAudience.FAMILY,
                LocalDate.of(seasonYear, 12, 15), LocalDate.of(seasonYear + 1, 1, 15)));

        NYE_QUESTION_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.QUESTION, nyePack)));
        NYE_ANSWER_CARDS.forEach(text -> cardRepository.save(new Card(text, CardType.ANSWER, nyePack)));
    }
}
