package nl.jays.boodschappenbenelux;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

final class Store extends SQLiteOpenHelper {
    Store(Context c) { super(c, "benelux.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE products(barcode TEXT PRIMARY KEY,name TEXT NOT NULL,size TEXT DEFAULT '',image_url TEXT DEFAULT '')");
        d.execSQL("CREATE TABLE shopping(barcode TEXT PRIMARY KEY,quantity INTEGER NOT NULL DEFAULT 1,FOREIGN KEY(barcode) REFERENCES products(barcode))");
        d.execSQL("CREATE TABLE prices(id INTEGER PRIMARY KEY AUTOINCREMENT,barcode TEXT NOT NULL,store TEXT NOT NULL,cents INTEGER NOT NULL CHECK(cents>=0),observed INTEGER NOT NULL,source TEXT NOT NULL CHECK(source IN ('BON','HANDMATIG','ACTIE')),valid_until INTEGER,original_cents INTEGER,FOREIGN KEY(barcode) REFERENCES products(barcode))");
        receiptTables(d);
        d.execSQL("ALTER TABLE prices ADD COLUMN purchase_id TEXT");
    }
    private void receiptTables(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE IF NOT EXISTS purchases(id TEXT PRIMARY KEY,fingerprint TEXT NOT NULL UNIQUE,store TEXT NOT NULL,bought INTEGER NOT NULL,total_cents INTEGER,calculated_cents INTEGER NOT NULL,created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE IF NOT EXISTS receipt_items(receipt_id TEXT NOT NULL,line_id TEXT NOT NULL,label TEXT NOT NULL,quantity TEXT NOT NULL,unit_cents INTEGER NOT NULL,line_cents INTEGER NOT NULL,discount_cents INTEGER NOT NULL,type TEXT NOT NULL,barcode TEXT,PRIMARY KEY(receipt_id,line_id),FOREIGN KEY(receipt_id) REFERENCES purchases(id))");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int next) {
        if(old<2)d.execSQL("ALTER TABLE products ADD COLUMN image_url TEXT DEFAULT ''");
        if(old<3){receiptTables(d);d.execSQL("ALTER TABLE prices ADD COLUMN purchase_id TEXT");}
    }
    void product(String barcode,String name,String size) {
        product(barcode,name,size,"");
    }
    void product(String barcode,String name,String size,String imageUrl) {
        ContentValues v=new ContentValues(); v.put("barcode",barcode); v.put("name",name); v.put("size",size);v.put("image_url",imageUrl);
        getWritableDatabase().insertWithOnConflict("products",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    String productImage(String barcode) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT image_url FROM products WHERE barcode=?",new String[]{barcode})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    String productName(String barcode) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM products WHERE barcode=?",new String[]{barcode})) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }
    void addShopping(String barcode) {
        SQLiteDatabase d=getWritableDatabase();
        d.beginTransaction();
        try { ContentValues increment=new ContentValues();
            try(Cursor c=d.rawQuery("SELECT quantity FROM shopping WHERE barcode=?",new String[]{barcode})) {
                if(c.moveToFirst()) { increment.put("quantity",c.getInt(0)+1);d.update("shopping",increment,"barcode=?",new String[]{barcode}); }
                else { increment.put("barcode",barcode);increment.put("quantity",1);d.insertOrThrow("shopping",null,increment); }
            } d.setTransactionSuccessful();
        } finally { d.endTransaction(); }
    }
    void removeShopping(String barcode) {
        getWritableDatabase().delete("shopping","barcode=?",new String[]{barcode});
    }
    void recordPrice(String barcode,String shop,int cents,String source,Long until,Integer original) { recordPriceAt(barcode,shop,cents,source,until,original,System.currentTimeMillis()); }
    void recordPriceAt(String barcode,String shop,int cents,String source,Long until,Integer original,long observed) {
        ContentValues v=new ContentValues();
        v.put("barcode",barcode); v.put("store",shop); v.put("cents",cents); v.put("source",source); v.put("observed",observed);
        if(until!=null)v.put("valid_until",until);
        if(original!=null)v.put("original_cents",original);
        getWritableDatabase().insertOrThrow("prices",null,v);
    }
    Cursor findProducts(String query) { return getReadableDatabase().rawQuery("SELECT barcode,name FROM products WHERE name LIKE ? ORDER BY name LIMIT 20",new String[]{"%"+query.replace("%","").replace("_","")+"%"}); }
    Cursor list() { return getReadableDatabase().rawQuery("SELECT shopping.barcode,products.name,shopping.quantity FROM shopping JOIN products USING(barcode) ORDER BY products.name",null); }
    Cursor prices(String barcode) { return getReadableDatabase().rawQuery("SELECT store,cents,source,observed,valid_until,original_cents FROM prices WHERE barcode=? ORDER BY store,observed DESC",new String[]{barcode}); }
    Cursor shops() { return getReadableDatabase().rawQuery("SELECT DISTINCT store FROM prices ORDER BY store",null); }
    Cursor allForShop(String shop) { return getReadableDatabase().rawQuery("SELECT shopping.barcode,products.name,shopping.quantity,(SELECT cents FROM prices p WHERE p.barcode=shopping.barcode AND p.store=? AND ((p.source='ACTIE' AND p.valid_until>=?) OR (p.source='HANDMATIG' AND p.observed>=?)) ORDER BY CASE WHEN p.source='ACTIE' THEN 0 ELSE 1 END,p.observed DESC LIMIT 1) AS cents FROM shopping JOIN products USING(barcode)",new String[]{shop,Long.toString(System.currentTimeMillis()),Long.toString(System.currentTimeMillis()-86400000L)}); }
    /** All receipt rows and their historic prices commit together, exactly once. */
    boolean savePurchase(ReceiptDraft draft,String fingerprint) {
        draft.validate();
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try {
            try(Cursor c=d.rawQuery("SELECT id FROM purchases WHERE id=? OR fingerprint=?",new String[]{draft.id,fingerprint})){
                if(c.moveToFirst()){d.setTransactionSuccessful();return false;}
            }
            ContentValues p=new ContentValues();p.put("id",draft.id);p.put("fingerprint",fingerprint);p.put("store",draft.shop.trim());p.put("bought",ReceiptParser.dateMillis(draft.date));
            Integer receiptTotal=ReceiptParser.money(draft.totalText);if(receiptTotal!=null)p.put("total_cents",receiptTotal);p.put("calculated_cents",draft.total());p.put("created",System.currentTimeMillis());d.insertOrThrow("purchases",null,p);
            Map<String,java.math.BigDecimal> quantities=new LinkedHashMap<>();Map<String,Long> amounts=new LinkedHashMap<>();
            for(ReceiptParser.Line l:draft.lines){
                String barcode=l.barcode.trim();
                if(l.type.equals(ReceiptParser.PRODUCT)&&barcode.isEmpty())barcode="receipt:"+draft.id+":"+UUID.nameUUIDFromBytes(ReceiptParser.normalized(l.label).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                ContentValues v=new ContentValues();v.put("receipt_id",draft.id);v.put("line_id",l.id);v.put("label",l.label.trim());v.put("quantity",l.quantity);v.put("unit_cents",l.unitCents);v.put("line_cents",l.cents);v.put("discount_cents",l.discountCents);v.put("type",l.type);v.put("barcode",barcode);d.insertOrThrow("receipt_items",null,v);
                if(l.type.equals(ReceiptParser.PRODUCT)){
                    ContentValues product=new ContentValues();product.put("barcode",barcode);product.put("name",l.label.trim());product.put("size","");product.put("image_url","");
                    // Never replace an existing product's name, size, image, or shopping list relation.
                    d.insertWithOnConflict("products",null,product,SQLiteDatabase.CONFLICT_IGNORE);
                    quantities.put(barcode,quantities.getOrDefault(barcode,java.math.BigDecimal.ZERO).add(new java.math.BigDecimal(l.quantity)));
                    amounts.put(barcode,amounts.getOrDefault(barcode,0L)+l.net());
                }
            }
            for(String barcode:quantities.keySet()){
                int unitNet=java.math.BigDecimal.valueOf(amounts.get(barcode)).divide(quantities.get(barcode),0,java.math.RoundingMode.HALF_UP).intValueExact();
                ContentValues price=new ContentValues();price.put("barcode",barcode);price.put("store",draft.shop.trim());price.put("cents",unitNet);price.put("source","BON");price.put("observed",ReceiptParser.dateMillis(draft.date));price.put("purchase_id",draft.id);d.insertOrThrow("prices",null,price);
            }
            d.setTransactionSuccessful();return true;
        }finally{d.endTransaction();}
    }
    boolean hasPurchase(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM purchases WHERE id=?",new String[]{id})){return c.moveToFirst();}}
    Cursor purchases(){return getReadableDatabase().rawQuery("SELECT id,store,bought,calculated_cents,total_cents FROM purchases ORDER BY created DESC",null);}

}

