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
