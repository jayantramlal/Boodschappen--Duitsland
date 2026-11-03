package nl.jays.boodschappenbenelux;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Private, compressed receipt images and crash-safe draft storage. */
final class ReceiptFiles {
    private final Context context;
    private final File root;
    ReceiptFiles(Context c){context=c;root=new File(c.getFilesDir(),"receipts");if(!root.isDirectory()&&!root.mkdirs())throw new IllegalStateException("Geen opslagruimte");}
    File folder(ReceiptDraft d){File f=new File(root,d.id);if(!f.isDirectory()&&!f.mkdirs())throw new IllegalStateException("Geen opslagruimte");return f;}
    File page(ReceiptDraft d,ReceiptDraft.Page p){return new File(folder(d),p.file);}
    private AtomicFile active(){return new AtomicFile(new File(root,"draft.json"));}
    ReceiptDraft load() throws Exception {if(!active().getBaseFile().exists()&&!new File(root,"draft.json.bak").exists())return null;return ReceiptDraft.from(new JSONObject(new String(active().readFully(),"UTF-8")));}
    void save(ReceiptDraft d) throws Exception {write(active(),d.json().toString());}
    void archive(ReceiptDraft d) throws Exception {write(new AtomicFile(new File(folder(d),"receipt.json")),d.json().toString());}
    ReceiptDraft purchase(String id)throws Exception{if(!id.matches("[a-fA-F0-9-]{36}"))throw new IOException("Ongeldige bon");return ReceiptDraft.from(new JSONObject(new String(new AtomicFile(new File(new File(root,id),"receipt.json")).readFully(),"UTF-8")));}
    void clear(){active().delete();}
    void discard(ReceiptDraft d){clear();File directory=new File(root,d.id);if(d.id.matches("[a-fA-F0-9-]{36}")){File[] all=directory.listFiles();if(all!=null)for(File f:all)if(f.isFile())f.delete();directory.delete();}}

    private void write(AtomicFile target,String s)throws Exception {
        FileOutputStream out=null;try{out=target.startWrite();out.write(s.getBytes("UTF-8"));target.finishWrite(out);}catch(Exception e){if(out!=null)target.failWrite(out);throw e;}
    }
    ReceiptDraft.Page importImage(ReceiptDraft d,Uri uri)throws Exception {
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
        if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Geen bruikbare afbeelding");
        int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>3200)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;
        Bitmap b;try(InputStream in=context.getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,o);}
        if(b==null)throw new IOException("Foto kon niet worden gelezen");
        try{
            int orientation=ExifInterface.ORIENTATION_NORMAL;
            try(InputStream in=context.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(Exception ignored){}
            Matrix m=new Matrix();
            switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
            if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b){b.recycle();b=rotated;}}
            int longest=Math.max(b.getWidth(),b.getHeight());if(longest>2600){double scale=2600.0/longest;Bitmap scaled=Bitmap.createScaledBitmap(b,Math.max(1,(int)(b.getWidth()*scale)),Math.max(1,(int)(b.getHeight()*scale)),true);if(scaled!=b){b.recycle();b=scaled;}}
            ReceiptDraft.Page p=new ReceiptDraft.Page();p.file=UUID.randomUUID()+".jpg";p.qualityWarning=qualityWarning(b);
            File f=page(d,p);AtomicFile atomic=new AtomicFile(f);FileOutputStream out=null;
            try{out=atomic.startWrite();if(!b.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Foto opslaan mislukt");atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
            try(InputStream in=new FileInputStream(f)){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)md.update(bytes,0,n);p.hash=hex(md.digest());}
            for(ReceiptDraft.Page old:d.pages)if(old.hash.equals(p.hash)){f.delete();return null;}
            return p;
        }finally{b.recycle();}
    }
    static boolean qualityWarning(Bitmap b){
        if(b.getWidth()<600||b.getHeight()<600)return true;
        double sum=0,edge=0;int n=0;
        for(int y=b.getHeight()/10;y<b.getHeight()*9/10;y+=Math.max(1,b.getHeight()/100))for(int x=b.getWidth()/10;x<b.getWidth()*9/10;x+=Math.max(1,b.getWidth()/100)){
            int a=gray(b.getPixel(x,y)),next=gray(b.getPixel(Math.min(x+2,b.getWidth()-1),y));sum+=a;edge+=Math.abs(a-next);n++;
        }double mean=sum/Math.max(1,n);return mean<45||mean>251||edge/Math.max(1,n)<1.2;
    }
    private static int gray(int c){return (Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100;}
    static String fingerprint(String raw)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(ReceiptParser.normalized(raw).getBytes("UTF-8")));}
    private static String hex(byte[] a){StringBuilder s=new StringBuilder();for(byte b:a)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
