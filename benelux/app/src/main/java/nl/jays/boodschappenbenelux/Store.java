package nl.jays.boodschappenbenelux;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

final class Store extends SQLiteOpenHelper {
    Store(Context c) { super(c, "benelux.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE products(barcode TEXT PRIMARY KEY,name TEXT NOT NULL,size TEXT DEFAULT '')");
        d.execSQL("CREATE TABLE shopping(barcode TEXT PRIMARY KEY,quantity INTEGER NOT NULL DEFAULT 1,FOREIGN KEY(barcode) REFERENCES products(barcode))");
        d.execSQL("CREATE TABLE prices(id INTEGER PRIMARY KEY AUTOINCREMENT,barcode TEXT NOT NULL,store TEXT NOT NULL,cents INTEGER NOT NULL CHECK(cents>=0),observed INTEGER NOT NULL,source TEXT NOT NULL CHECK(source IN ('BON','HANDMATIG','ACTIE')),valid_until INTEGER,original_cents INTEGER,FOREIGN KEY(barcode) REFERENCES products(barcode))");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int next) { throw new IllegalStateException("Migratie vereist"); }
    void product(String barcode,String name,String size) {
        ContentValues v=new ContentValues(); v.put("barcode",barcode); v.put("name",name); v.put("size",size);
        getWritableDatabase().insertWithOnConflict("products",null,v,SQLiteDatabase.CONFLICT_REPLACE);
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
    void recordPrice(String barcode,String shop,int cents,String source,Long until,Integer original) {
        ContentValues v=new ContentValues();
        v.put("barcode",barcode); v.put("store",shop); v.put("cents",cents); v.put("source",source); v.put("observed",System.currentTimeMillis());
        if(until!=null)v.put("valid_until",until);
        if(original!=null)v.put("original_cents",original);
        getWritableDatabase().insertOrThrow("prices",null,v);
    }
    Cursor list() { return getReadableDatabase().rawQuery("SELECT shopping.barcode,products.name,shopping.quantity FROM shopping JOIN products USING(barcode) ORDER BY products.name",null); }
    Cursor prices(String barcode) { return getReadableDatabase().rawQuery("SELECT store,cents,source,observed,valid_until,original_cents FROM prices WHERE barcode=? ORDER BY store,observed DESC",new String[]{barcode}); }
    Cursor shops() { return getReadableDatabase().rawQuery("SELECT DISTINCT store FROM prices ORDER BY store",null); }
    Cursor allForShop(String shop) { return getReadableDatabase().rawQuery("SELECT shopping.barcode,products.name,shopping.quantity,(SELECT cents FROM prices p WHERE p.barcode=shopping.barcode AND p.store=? AND ((p.source='ACTIE' AND p.valid_until>=?) OR (p.source='HANDMATIG' AND p.observed>=?)) ORDER BY CASE WHEN p.source='ACTIE' THEN 0 ELSE 1 END,p.observed DESC LIMIT 1) AS cents FROM shopping JOIN products USING(barcode)",new String[]{shop,Long.toString(System.currentTimeMillis()),Long.toString(System.currentTimeMillis()-86400000L)}); }
}
