package nl.jays.boodschappenbenelux;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptImageTest {
 @Test public void darkImageNeedsCorrection(){Bitmap b=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLACK);assertTrue(ReceiptFiles.qualityWarning(b));b.recycle();}
 @Test public void washedOutImageNeedsCorrection(){Bitmap b=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);assertTrue(ReceiptFiles.qualityWarning(b));b.recycle();}
 @Test public void lowResolutionThumbnailNeedsCorrection(){Bitmap b=Bitmap.createBitmap(200,300,Bitmap.Config.ARGB_8888);assertTrue(ReceiptFiles.qualityWarning(b));b.recycle();}
 @Test public void sharpHighContrastImagePassesQualityHeuristic(){Bitmap b=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888);int[] data=new int[800*1200];for(int y=0;y<1200;y++)for(int x=0;x<800;x++)data[y*800+x]=((x/3+y/3)%2==0)?Color.WHITE:Color.BLACK;b.setPixels(data,0,800,0,0,800,1200);assertFalse(ReceiptFiles.qualityWarning(b));b.recycle();}
 @Test public void originalFileCopiedAndExactDuplicateIgnored()throws Exception {
  android.app.Application app=RuntimeEnvironment.getApplication();ReceiptFiles f=new ReceiptFiles(app);ReceiptDraft d=new ReceiptDraft();File image=new File(app.getCacheDir(),"receipt-input.jpg");
  Bitmap bitmap=Bitmap.createBitmap(1200,2200,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);Canvas c=new Canvas(bitmap);Paint ink=new Paint();ink.setColor(Color.BLACK);ink.setTextSize(42);for(int y=80;y<2100;y+=65)c.drawText("REWE  MILCH 1,19  PFAND 0,25",20,y,ink);try(FileOutputStream out=new FileOutputStream(image)){assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,93,out));}bitmap.recycle();
  ReceiptDraft.Page first=f.importImage(d,Uri.fromFile(image));assertNotNull(first);assertTrue(f.page(d,first).isFile());d.pages.add(first);assertNull(f.importImage(d,Uri.fromFile(image)));assertTrue(image.isFile());
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.page(d,first).getAbsolutePath(),options);assertTrue(Math.max(options.outWidth,options.outHeight)<=2600);assertTrue(options.outWidth>=600);
 }
}
