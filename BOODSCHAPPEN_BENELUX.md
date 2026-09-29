# Boodschappen BeNeLux-Duitsland

Nieuwe Android-boodschappenapp, vanaf nul ontworpen. De bestaande repository blijft behouden; de oude Boodschappen Duitsland-broncode is geen functionele basis voor de nieuwe app.

## Afgesproken uitgangspunten

- Zichtbare appnaam: **Boodschappen BeNeLux-Duitsland**.
- Een centrale productcatalogus herkent producten op barcode en toont productgegevens.
- Een boodschappenlijst gebruikt dezelfde producten als de scanner en prijsvergelijking.
- Een bonscanner leest winkel, datum, productregels en betaalde bedragen. De gebruiker kan herkende producten en bedragen controleren en corrigeren voordat deze aan de prijsgeschiedenis worden gekoppeld.
- Prijsgeschiedenis bewaart product, winkel, datum, bron en hoeveelheid/eenheid. Bonprijzen zijn historische aankoopprijzen en worden niet automatisch als huidige winkelprijs gepresenteerd.
- Na het scannen toont een productscherm een rij van de bekende winkelprijzen. Een geldige aanbieding toont de gewone prijs doorgestreept met de actieprijs ernaast. Ontbrekende en verlopen prijzen blijven zichtbaar als onbekend.
- De eerder besproken twaalf winkels blijven binnen scope: Albert Heijn, Jumbo, Dirk, Lidl Nederland, PLUS, Kruidvat, REWE, EDEKA Schroff, Lidl Duitsland, ALDI SÜD, Kaufland en dm.
- Een vergelijking van een volledige boodschappenlijst vereist prijzen voor alle regels bij de betreffende winkel; een onvolledig totaal krijgt geen rang als goedkoopste winkel.

## Nog vast te leggen tijdens de bouw

Exacte schermen, barcodegegevensbron en toestemming/licentie voor gebruik, verwerking van bonregels zonder barcode, prijsregels bij verschillende verpakkingsgroottes, lokale opslag en back-up, en welke aanbodbronnen werkelijk beschikbaar zijn. Deze punten worden geïmplementeerd en getest voordat een functie als klaar wordt aangemerkt.
