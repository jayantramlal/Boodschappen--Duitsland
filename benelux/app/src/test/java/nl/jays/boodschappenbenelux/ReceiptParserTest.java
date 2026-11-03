package nl.jays.boodschappenbenelux;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ReceiptParserTest {
 @Test public void shortGermanReceipt(){ReceiptParser.Document d=ReceiptParser.document("REWE\n03.11.2026\nMILCH 1,19 A\nBROT 2,49 B\nSUMME EUR 3,68\nBAR 5,00\nRückgeld 1,32");assertEquals(2,d.lines.size());assertEquals("REWE",d.shop);assertEquals("03-11-2026",d.date);assertEquals(Integer.valueOf(368),d.totalCents);}
 @Test public void quantityInLabel(){ReceiptParser.Line l=ReceiptParser.parse("Cola 2 x 1,49 2,98 A").get(0);assertEquals("2",l.quantity);assertEquals(149,l.unitCents);assertEquals(298,l.cents);assertEquals("Cola",l.label);}
 @Test public void quantityOnNextLine(){ReceiptParser.Line l=ReceiptParser.parse("MINERALWASSER 2,58\n2 Stk x 1,29").get(0);assertEquals("2",l.quantity);assertEquals(129,l.unitCents);assertFalse(l.uncertain);}
 @Test public void weightedProduct(){ReceiptParser.Line l=ReceiptParser.parse("BANANEN 1,95\n0,652 kg x 2,99").get(0);assertEquals("0.652",l.quantity);assertEquals(299,l.unitCents);assertFalse(l.uncertain);}
 @Test public void discountAndPfand(){ReceiptParser.Document d=ReceiptParser.document("Lidl Deutschland\n03.11.2026\nCOLA 2,00\nRabatt -0,20\nPFAND 0,25\nSUMME 2,05");assertEquals(2,d.lines.size());assertEquals(20,d.lines.get(0).discountCents);assertEquals(ReceiptParser.DEPOSIT,d.lines.get(1).type);assertEquals(205,d.lines.stream().mapToLong(ReceiptParser.Line::net).sum());}
 @Test public void globalCouponAndReturnedDeposit(){List<ReceiptParser.Line> a=ReceiptParser.parse("BROT 2,00\nCoupon 0,50-\nLeergut -0,25");assertEquals(3,a.size());assertEquals(ReceiptParser.DISCOUNT,a.get(1).type);assertEquals(-50,a.get(1).cents);assertEquals(ReceiptParser.DEPOSIT,a.get(2).type);assertEquals(-25,a.get(2).cents);}
 @Test public void overlapAtPageBoundary(){String merged=ReceiptParser.mergePages(Arrays.asList("REWE\nBROT 1,00\nMILCH 2,00\nKAESE 3,00","MILCH 2,00\nKAESE 3,00\nCOLA 4,00\nSUMME 10,00"));assertEquals(4,ReceiptParser.parse(merged).size());}
 @Test public void samePageCapturedTwice(){String page="BROT 1,00\nMILCH 2,00\nSUMME 3,00";assertEquals(2,ReceiptParser.parse(ReceiptParser.mergePages(Arrays.asList(page,page))).size());}
 @Test public void realRepeatedProductsAreKept(){assertEquals(2,ReceiptParser.parse("MILCH 1,19\nMILCH 1,19").size());}
 @Test public void splitProductName(){List<ReceiptParser.Line> l=ReceiptParser.parse("TOMATEN-\nMARK 1,29");assertEquals("TOMATENMARK",l.get(0).label);}
 @Test public void priceOnSeparateLine(){assertEquals("GOUDA JUNG",ReceiptParser.parse("GOUDA JUNG\n2,49 A").get(0).label);}
 @Test public void impossibleDateIsNotAccepted(){assertEquals("",ReceiptParser.date("31.02.2026"));assertNull(ReceiptParser.dateMillis("03-11-2026rest"));}
 @Test public void moneyAndOverflow(){assertEquals(Integer.valueOf(123456),ReceiptParser.money("1.234,56"));assertEquals(Integer.valueOf(-25),ReceiptParser.money("0,25-"));assertNull(ReceiptParser.money("1,999"));assertNull(ReceiptParser.money("9999999999999"));}
 @Test public void missingDataNeedsCorrection(){ReceiptParser.Document d=ReceiptParser.document("XYZ??? 0,00");assertEquals(3,d.warnings.size());assertTrue(d.lines.get(0).uncertain);assertNull(d.totalCents);}
 @Test public void quantityMismatchFlagged(){ReceiptParser.Line l=ReceiptParser.parse("BROT 3,99\n2 x 1,99").get(0);assertFalse(l.uncertain);ReceiptParser.Line wrong=ReceiptParser.parse("BROT 5,99\n2 x 1,99").get(0);assertTrue(wrong.uncertain);}
 @Test public void changingAmountsUsesExactCents(){ReceiptParser.Line l=new ReceiptParser.Line("Melk",119);l.setQuantityAndUnit("3",119);l.discountCents=10;assertEquals(347,l.net());}
 @Test public void manyPartsRemainOrdered(){ArrayList<String> pages=new ArrayList<>();for(int n=0;n<20;n++)pages.add("Product"+n+" 1,00");assertEquals(20,ReceiptParser.parse(ReceiptParser.mergePages(pages)).size());}
 @Test public void taxLinesAreIgnored(){assertEquals(1,ReceiptParser.parse("BROT 2,00\nMwSt 0,13\nA 7,00% 0,13\nB 19,00% 0,00\nNETTO 1,87").size());}
 @Test public void leadingQuantityIsProposedWithWarning(){ReceiptParser.Line l=ReceiptParser.parse("2 COLA 2,98").get(0);assertEquals("2",l.quantity);assertEquals(149,l.unitCents);assertTrue(l.uncertain);}
 @Test public void packSizeIsNotQuantity(){ReceiptParser.Line l=ReceiptParser.parse("500 g GOUDA 2,99").get(0);assertEquals("1",l.quantity);}

}
