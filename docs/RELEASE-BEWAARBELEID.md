# Beleid: drie nieuwste goedgekeurde app-versies

- Bewaar uitsluitend de **drie nieuwste succesvol gebouwde én handmatig gecontroleerde** releases in de beheerde `verified-v...`-reeks.
- Gebruik **Actions > Bewaar gecontroleerde app-versie (laatste 3) > Run workflow** en voer het werkelijke app-versienummer, de succesvolle Actions-run-ID en de expliciete goedkeuring in.
- De workflow controleert de buildstatus, downloadt de ondertekende APK, verifieert de APK-handtekening en bewaart een exacte broncode-snapshot, commit-SHA, run-ID en checksums in een GitHub Release.
- Pas **na** succesvolle publicatie en controle van het nieuwe archief verwijdert de workflow oudere *beheerde releases* boven de drie. Bestaande releases buiten deze reeks en de Git-geschiedenis worden nooit automatisch verwijderd.
- **Keystores, secrets, API-tokens, certificaten en wachtwoorden worden NIET in releases opgeslagen.** Bewaar hun oorspronkelijke waarden en eventuele oude versies in een afzonderlijke, versleutelde herstelback-up met toegangsbeheer. GitHub Secrets alleen vormen geen exporteerbare back-up.
- Controleer vóór het verwijderen van oude gegevens dat benodigde signing keys en integraties voor alle drie versies beschikbaar zijn. De workflow kan aanwezigheid van externe secrets/back-ups niet zelfstandig bewijzen.
- Deze workflow verklaart een versie niet automatisch functioneel getest: goedkeuring is een expliciete menselijke stap.
- GitHub Actions-artifacts zijn tijdelijk; GitHub Releases vormen het duurzame APK- en broncodearchief. De standaardbuilds blijven ongewijzigd.
- Een nieuwe release creëert geen garantie voor een volledig reproduceerbare build bij gewijzigde of verwijderde externe dependencies. Periodieke test-herbouw blijft noodzakelijk.

## Veiligheidsslot voor opruimen

Het opruimen van overtollige beheerde releases is **standaard uitgeschakeld**. Het wordt pas actief wanneer repositoryvariabele `RELEASE_BACKUP_VERIFIED` exact `true` is, nadat de signing-key-, secret- en herstelback-ups afzonderlijk aantoonbaar zijn gecontroleerd. Alleen de drie nieuwste goedgekeurde releases tellen; een succesvolle build alleen is geen functionele goedkeuring. De workflow kan deze back-ups niet zelf uitlezen of testen. Zonder deze bevestiging blijven alle beheerde releases staan.

## Verplicht herstelbeleid bij mislukte installatie of test

- **Een geslaagde GitHub-build of een downloadbare APK/installer is nog geen gebruikersgoedkeuring.** Goedkeuring volgt pas nadat de gebruiker het bestand daadwerkelijk kan installeren, starten en voldoende heeft getest.
- Bewaar nieuwe niet-geteste builds desgewenst tijdelijk als kandidaat; verwijder of overschrijf nooit de laatst geteste, werkende basisversie door het verschijnen van een kandidaat.
- Bij een mislukte installatie, opstartfout of afkeuring is de **laatst daadwerkelijk geïnstalleerde en goedgekeurde versie** de verplichte bronbasis voor het volgende herstel. Gebruik de mislukte build niet als nieuwe basis.
- Inventariseer de wijzigingen tussen de goede basisversie en de afgekeurde build(s), en pas **alle gewenste wijzigingen opnieuw toe** op de goede basis. Behoud alleen expliciet gewenste verbeteringen; neem defecte of ongewenste effecten niet over.
- Bouw opnieuw, controleer handtekening/updatecompatibiliteit en lever opnieuw ter installatie en gebruikerstest. Een buildworkflow kan dit ontwikkelwerk niet zelfstandig garanderen: de wijzigingstoepassing en functionele goedkeuring vereisen afzonderlijke controles.
- De beheerde duurzaam **goedgekeurde** geschiedenis bevat de huidige goede basisversie en de vorige goedgekeurde edities (maximaal **3** voor gewone repositories; **10** voor Jay's Kilometerregistratie). Alleen goedgekeurde, volledige nieuwe archieven tellen mee voor rotatie.
- **Nooit** een oudste goedgekeurde versie verwijderen doordat alleen een nieuwe kandidaat succesvol bouwt. Opschonen pas na bevestigde goedkeuring, verificatie van de nieuwe release, en het bestaande veiligheidsslot `RELEASE_BACKUP_VERIFIED=true`.
