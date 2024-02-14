# Noviaal Planning and Notes: NoPaN

## Functioneel Ontwerp NoPaN

NoPaN is een taak- en notitie registratie en beheer systeem:

* Taken worden overzichtelijk weergegeven en zijn eenvoudig van status te veranderen.
* Notities worden in een nette lijst, gepagineerd weergegeven.

NB: hoe kom ik aan 50 functionele en niet-functionele eisen?

### Functionele Eisen

* Als gebruiker wil ik me kunnen registreren zodat ik van de faciliteiten van NoPaN gebruik kan maken
  * registratie met email adres, password en naam
  * email adres moet uniek zijn
  * naam mag niet leeg zijn
  * password moet minimaal 7 karakters lang zijn
* Als gebruiker wil ik me kunnen aanmelden, zodat ik mijn taken en notities kan beheren
* Als aangemelde gebruiker wil ik me kunnen afmelden zodat mijn device door iemand anders gebruikt kan worden
* Als geregistreerde gebruiker wil ik mijn Gravatar dat bij mijn email adres hoort zien zodat de boel wat vrolijker wordt

* Als gebruiker wil ik een nieuwe taak aanmaken, zodat ik deze kan onthouden
  * een taak heeft een titel, vrije tekst en een deadline datum + tijd
  * de titel en vrije tekst mogen niet leeg zijn
  * de datum + tijd moeten in de toekomst liggen
* Als gebruiker wil ik een bestaande taak een andere status kunnen geven zodat ik de voortgang kan (laten) zien
  * door middel van drag & drop kan de status aangepast worden
* Als gebruiker wil ik een bestaande taak inhoudelijk wijzigen zodat deze weer de werkelijke bedoeling weergeven
  * titel en vrije tekst mogen wederom niet leeg zijn
* Als gebruiker wil ik een bestaande taak kunnen verwijderen zodra deze niet meer relevant is

* Als gebruiker wil ik een nieuwe notitie aanmaken zodat ik mijn aantekeningen kwijt kan
  * een notitie heeft een titel en vrije tekst
  * titel en vrije tekst mogen niet leeg zijn
  * voor een nieuwe notitie wordt een 'slug' gegenereerd op basis van de titel
* Als gebruiker wil ik een bestaande notitie kunnen wijzigen zodat de notitie weer de gewijzigde realiteit weergeeft
  * titel en vrije tekst mogen wederom niet leeg zijn
* Als gebruiker wil ik een bestaande notitie verwijderen omdat deze geen relevante inhoud meer heeft

### Niet-functionele Eisen

* eenvoudige installatie, zonder overmatige technische kennis, liefst een één-knops intallatie!
  * script, maar voor Windows is dat moeilijk omdat ik geen beschikking over een Windows machine beschik
  * andere optie(s) onderzoeken
* moet uitgevoerd kunnen worden op Windows 10 of 11, macOS of Linux
* moet uitgevoerd kunnen worden met Firefox, Chrome (incl. Edge, Arc, Brave, etc. etc.) en Safari
* installatie en uitvoering van deze frontend applicatie hebben een internet verbinding nodig
* testen op Chrome, Firefox en Safari, maar alleen op macOS, er vanuit gaand dat die browsers genoeg cross-platform zijn.
* security is geen harde eis voor deze frontend: de bedoeling is dat de app alleen lokaal op de machine van een gebruiker draait en niet op een server in het grote,
  boze internet.

### Use-Cases

ToDo:
nieuwe gebruiker registreren,
aanmelden geregistreerde gebruiker,
nieuwe taak aanmaken,
taak-status veranderen door drag & drop en
nieuwe notitie aanmaken.

### Inspiratie Bronnen

* Trello
* Jira
* Basecamp

## Verantwoording

### Technische Keuzes

Alle technische keuzes zijn gemaakt door Novi. Alleen JavaScript zou ik nu niet meer kiezen.

#### JavaScript

Door Novi opgelegd. Zou ik zelf nooit meer voor kiezen: TypeScript of liever nog Elm, PureScript of een andere hoger niveau taal en omgeving.
Blijft een vreemde programmeertaal ten opzichte van de bij mij meer bekende talen als Java, Kotlin, Python en Scala. Een programmeertaal zonder
een type-system dat me helpt om tijdens het bouwen en uitvoeren van een applicatie veelgemaakte fouten te voorkomen is echt wennen.

#### React

Is best een fijn en veelzijdig framework. Vlot nieuwe applicaties bouwen en bestaande applicaties uitbreiden zijn na enig oefenen goed te doen.

#### Axios

Maakt communiceren met mijn eigen en andere backends/APIs overzichtelijk en beheersbaar.

#### html

Niet aan te ontkomen en dat zou ik ook niet willen. Ik vind, na 25 jaar regelmatig ermee werken, dat html niet voor niets nergens door vervangen is. Soms wat veel
tekst nodig om iets te definieren, met name tabellen, maar over het algemeen begrijpelijk en structureel goed doordacht.

#### css

Voor layout, kleuren en typografie is css onmisbaar. Ondanks veel bezwaren vanuit ontwikkelaar-vriendelijkheid is css onlosmakelijk verbonden met het ontwikkelen van
mooie en aansprekende websites en webapplicaties.

### Limitaties

#### Algemeen

Werkt alleen met Internet verbinding, aangezien backend daar ergens draait; geen offline mogelijkheid.

#### Functioneel

1. Een gebruiker heeft erg weinig informatie: naam, email adres en password. Een profiel met (veel) meer data zou aantrekkelijk kunnen zijn
2. Een taak is opzettelijk inhoudelijk beperkt opgezet. Dit is in lijn met het BaseCamp gedachtengoed: zorg dat de tool geen doel op zich wordt, waarmee ook gezorgd
   wordt dat de functioneliteit niet ondergesneeud wordt door vele velden.
3. Een notitie is om dezelfde reden bescheiden gehouden. Het risico dat veel verschillende invoer/data om steeds meer functionaliteit vraagt is te groot (zie Jira)
4. Gebruikers leven nu in het backend systeem van NoPaN. Gegeven de grote hoeveelheden Google, HitHub, Microsoft, etc. bestaande accounts zou OAuth als autenticatie
   mechanisme wellicht makkelijker en zelfs drempelverlagend kunnen zijn.

## enige opmerkingen achteraf

Het deel Figma-prototype heb ik expres overgeslagen: kost veel tijd en levert minder dan 5% van het eind-cijfer op, aangezien wireframes + figma in totaal 5%
oplevert. Wel geprobeerd om er een design systeem in te krijgen zodat in ieder geval kleuren, typografie en elementaire elementen enigszins voorgekookt zijn, maar dat
had ik nog van een aparte cursus Figma van Novi overgehouden. Daarbij komt: ik ben geen grafische vormgever of designer. Dat is een vak apart.
