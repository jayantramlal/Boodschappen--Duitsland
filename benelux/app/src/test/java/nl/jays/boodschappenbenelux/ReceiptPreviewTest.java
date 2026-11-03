package nl.jays.boodschappenbenelux;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import android.app.Application;
import android.graphics.*;
import android.view.View;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w320dp-h568dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReceiptPreviewTest {
 @Test public void smallScreenReviewInBothThemes()throws Exception {
  Application app=RuntimeEnvironment.getApplication();
  for(boolean dark:new boolean[]{false,true}){
   app.getSharedPreferences("appearance",0).edit().putBoolean("dark",dark).commit();
   ReceiptDraft d=new ReceiptDraft();d.shop="Lidl Duitsland";d.date="03-11-2026";d.totalText="4.23";
   ReceiptParser.Line a=new ReceiptParser.Line("Volle melk",119);d.lines.add(a);ReceiptParser.Line b=new ReceiptParser.Line("Pasta penne",149);b.discountCents=20;d.lines.add(b);ReceiptParser.Line p=new ReceiptParser.Line("Pfand",25);p.type=ReceiptParser.DEPOSIT;d.lines.add(p);
   ReceiptDraft.Page photo=new ReceiptDraft.Page();photo.file="00000000-0000-0000-0000-000000000000.jpg";photo.done=true;d.pages.add(photo);
   new ReceiptFiles(app).save(d);ActivityController<ReceiptActivity> controller=Robolectric.buildActivity(ReceiptActivity.class).setup();
   View root=controller.get().findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(568,View.MeasureSpec.EXACTLY));root.layout(0,0,320,568);
   View save=root.findViewWithTag("saveAll");int[] xy=new int[2];save.getLocationOnScreen(xy);int[] origin=new int[2];root.getLocationOnScreen(origin);assertTrue(save.getHeight()>=48);assertTrue(xy[1]+save.getHeight()-origin[1]<=568);
   Bitmap image=Bitmap.createBitmap(320,568,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));File directory=new File("build/receipt-qa");assertTrue(directory.isDirectory()||directory.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(directory,dark?"review-dark.png":"review-light.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();controller.pause().stop().destroy();
  }
 }
}
