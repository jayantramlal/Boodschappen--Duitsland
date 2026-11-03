package nl.jays.boodschappenbenelux;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import android.app.Application;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.widget.*;
import static org.junit.Assert.*;
import java.util.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ReceiptStorageTest {
 private Application app;private Store store;
 @Before public void before()throws Exception {app=RuntimeEnvironment.getApplication();app.deleteDatabase("benelux.db");store=new Store(app);new ReceiptFiles(app).clear();}
 @After public void after(){store.close();}
 private ReceiptDraft sample(){ReceiptDraft d=new ReceiptDraft();d.shop="REWE";d.date="03-11-2026";d.totalText="2.05";ReceiptParser.Line a=new ReceiptParser.Line("COLA",200);a.discountCents=20;d.lines.add(a);ReceiptParser.Line p=new ReceiptParser.Line("PFAND",25);p.type=ReceiptParser.DEPOSIT;d.lines.add(p);return d;}
 private int count(String table){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
 @Test public void nothingStoredUntilSave(){ReceiptDraft d=sample();assertEquals(0,count("purchases"));assertEquals(0,count("prices"));d.lines.remove(1);assertEquals(0,count("products"));}
 @Test public void savesWholeReceiptOnce(){ReceiptDraft d=sample();assertTrue(store.savePurchase(d,"same"));assertFalse(store.savePurchase(d,"same"));assertFalse(store.savePurchase(sample(),"same"));assertEquals(1,count("purchases"));assertEquals(2,count("receipt_items"));assertEquals(1,count("prices"));}
 @Test public void noExistingProductOverwrite(){store.product("1234567890123","Original name","500ml","https://example.org/photo");store.addShopping("1234567890123");ReceiptDraft d=sample();d.lines.get(0).barcode="1234567890123";store.savePurchase(d,"f");assertEquals("Original name",store.productName("1234567890123"));assertEquals("https://example.org/photo",store.productImage("1234567890123"));try(Cursor c=store.list()){assertTrue(c.moveToFirst());assertEquals(1,c.getInt(2));}}
 @Test public void invalidReceiptLeavesNoPartialBooking(){ReceiptDraft d=sample();d.lines.get(1).label="";try{store.savePurchase(d,"bad");fail();}catch(IllegalArgumentException expected){}assertEquals(0,count("purchases"));assertEquals(0,count("prices"));assertEquals(0,count("products"));}
 @Test public void sqlFailureRollsBackEveryTable(){store.getWritableDatabase().execSQL("CREATE TRIGGER reject_prices BEFORE INSERT ON prices BEGIN SELECT RAISE(ABORT,'test failure'); END");try{store.savePurchase(sample(),"bad");fail();}catch(SQLiteException expected){}assertEquals(0,count("purchases"));assertEquals(0,count("products"));assertEquals(0,count("receipt_items"));}
 @Test public void draftSurvivesRestartAndStoresInvalidEdits()throws Exception{ReceiptFiles f=new ReceiptFiles(app);ReceiptDraft d=sample();d.lines.get(0).unitText="";f.save(d);ReceiptDraft restored=new ReceiptFiles(app).load();assertEquals(d.id,restored.id);assertEquals("",restored.lines.get(0).unitText);assertEquals(205,restored.total());try{restored.validate();fail();}catch(IllegalArgumentException expected){}}
 @Test public void archiveRetainsOriginalPageLinks()throws Exception{ReceiptDraft d=sample();ReceiptDraft.Page p=new ReceiptDraft.Page();p.file=UUID.randomUUID()+".jpg";p.done=true;p.text="Bontekst";d.pages.add(p);ReceiptFiles f=new ReceiptFiles(app);f.archive(d);assertEquals(p.file,f.purchase(d.id).pages.get(0).file);assertEquals("Bontekst",f.purchase(d.id).pages.get(0).text);}
 @Test public void migrationFromVersionTwoKeepsData(){store.close();app.deleteDatabase("benelux.db");SQLiteDatabase db=app.openOrCreateDatabase("benelux.db",0,null);db.execSQL("CREATE TABLE products(barcode TEXT PRIMARY KEY,name TEXT,size TEXT,image_url TEXT)");db.execSQL("CREATE TABLE shopping(barcode TEXT PRIMARY KEY,quantity INTEGER)");db.execSQL("CREATE TABLE prices(id INTEGER PRIMARY KEY,barcode TEXT,store TEXT,cents INTEGER,observed INTEGER,source TEXT,valid_until INTEGER,original_cents INTEGER)");db.execSQL("INSERT INTO products VALUES ('123','Melk','1L','image')");db.execSQL("INSERT INTO shopping VALUES ('123',2)");db.execSQL("INSERT INTO prices VALUES (1,'123','REWE',119,0,'BON',NULL,NULL)");db.setVersion(2);db.close();store=new Store(app);assertEquals("Melk",store.productName("123"));assertEquals(1,count("prices"));assertEquals(1,count("shopping"));assertEquals(0,count("purchases"));assertTrue(store.savePurchase(sample(),"upgraded"));}
 @Test public void backRetainsEditedDraft()throws Exception{ReceiptFiles f=new ReceiptFiles(app);ReceiptDraft d=sample();f.save(d);ActivityController<ReceiptActivity> c=Robolectric.buildActivity(ReceiptActivity.class).setup();ReceiptActivity a=c.get();EditText name=a.findViewById(android.R.id.content).findViewWithTag("name:"+d.lines.get(0).id);name.setText("Gecorrigeerde cola");a.onBackPressed();assertEquals("Gecorrigeerde cola",f.load().lines.get(0).label);assertEquals(0,count("purchases"));c.pause().stop().destroy();}
 @Test public void saveButtonCommitsAfterCorrection()throws Exception{ReceiptFiles f=new ReceiptFiles(app);ReceiptDraft d=sample();f.save(d);ActivityController<ReceiptActivity> c=Robolectric.buildActivity(ReceiptActivity.class).setup();Button b=c.get().findViewById(android.R.id.content).findViewWithTag("saveAll");assertEquals(0,count("purchases"));b.performClick();assertEquals(1,count("purchases"));assertNull(f.load());c.pause().stop().destroy();}
 @Test public void darkAndLightReviewOpenWithoutCrash()throws Exception{for(boolean dark:new boolean[]{false,true}){app.getSharedPreferences("appearance",0).edit().putBoolean("dark",dark).commit();new ReceiptFiles(app).save(sample());ActivityController<ReceiptActivity> c=Robolectric.buildActivity(ReceiptActivity.class).setup();assertNotNull(c.get().findViewById(android.R.id.content).findViewWithTag("saveAll"));c.pause().stop().destroy();}}
 @Test public void addedPagesDoNotOverwriteCorrections()throws Exception{ReceiptDraft d=new ReceiptDraft();ReceiptDraft.Page a=new ReceiptDraft.Page();a.text="BROT 1,00\nMILCH 2,00";a.done=true;d.pages.add(a);d.recognize();d.lines.get(0).label="Correctie";d.lines.get(0).edited=true;ReceiptDraft.Page b=new ReceiptDraft.Page();b.done=true;b.text="KAESE 3,00";d.pages.add(b);d.recognize();assertEquals("Correctie",d.lines.get(0).label);assertEquals(3,d.lines.size());}
 @Test public void deletedRowsDoNotReturnOnReprocessing(){ReceiptDraft d=new ReceiptDraft();ReceiptDraft.Page a=new ReceiptDraft.Page();a.text="BROT 1,00";a.done=true;d.pages.add(a);d.recognize();d.removed.add(d.lines.get(0).id);d.lines.clear();ReceiptDraft.Page b=new ReceiptDraft.Page();b.done=true;b.text="KAESE 3,00";d.pages.add(b);d.recognize();assertEquals(1,d.lines.size());assertEquals("KAESE",d.lines.get(0).label);}
}
