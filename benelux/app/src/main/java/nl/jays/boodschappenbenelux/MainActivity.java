package nl.jays.boodschappenbenelux;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.codescanner.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private final String[] shops={"Albert Heijn","Jumbo","Dirk","Lidl Nederland","PLUS","Kruidvat","REWE","EDEKA Schroff","Lidl Duitsland","ALDI SÜD","Kaufland","dm"};
    private Store db;
    private LinearLayout root,body;
    private int navy=Color.rgb(13,24,42),card=Color.rgb(25,39,61),yellow=Color.rgb(255,199,51),white=Color.WHITE;
    private final int CAMERA_REQUEST=42;
    private String currentBarcode;
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Store(this);getWindow().setStatusBarColor(navy);getWindow().setNavigationBarColor(Color.BLACK);home();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(14),dp(12),dp(14),dp(12));return t;}
    private void layout(String title){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(navy);setContentView(root);
        root.addView(text(title,23,yellow));
        ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(12),0,dp(12),dp(12));sc.addView(body);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);for(String label:new String[]{"Home","Lijst","Scan","Bon","Winkels"}){
            TextView t=text(label,12,white);t.setGravity(Gravity.CENTER);nav.addView(t,new LinearLayout.LayoutParams(0,dp(54),1));t.setOnClickListener(v->{
                switch(label){case "Home":home();break;case "Lijst":list();break;case "Scan":scan();break;case "Bon":receipt();break;default:compare();}
            });
        }root.addView(nav);
    }
    private void line(String s,Runnable action){TextView t=text(s,17,white);GradientDrawable bg=new GradientDrawable();bg.setColor(card);bg.setCornerRadius(dp(14));t.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);body.addView(t,p);if(action!=null)t.setOnClickListener(v->action.run());}
    private void button(String s,Runnable r){TextView t=text(s,16,navy);t.setGravity(Gravity.CENTER);t.setBackgroundColor(yellow);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);body.addView(t,p);t.setOnClickListener(v->r.run());}
    private void note(String s){body.addView(text(s,14,white));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);e.setTextColor(white);e.setHintTextColor(Color.LTGRAY);body.addView(e);return e;}
    private void home(){layout("Boodschappen BeNeLux-Duitsland");note("Scan producten, stel je lijst samen en bewaar prijzen van bonnetjes.");button("Scan een barcode",this::scan);button("Boodschappenlijst",this::list);button("Bon vastleggen",this::receipt);button("Vergelijk winkels",this::compare);note("Bonprijzen zijn eerdere aankopen. Onbekende of verlopen prijzen worden niet als actuele prijzen getoond.");}
    private void scan(){GmsBarcodeScanner scanner=GmsBarcodeScanning.getClient(this,new GmsBarcodeScannerOptions.Builder().enableAutoZoom().build());scanner.startScan()
        .addOnSuccessListener(result->{String code=result.getRawValue();if(code!=null&&!code.trim().isEmpty())product(code.trim());else toast("Geen barcode gevonden");})
        .addOnFailureListener(e->{toast("Scanner niet beschikbaar. Voer de barcode handmatig in.");manualBarcode();});}
    private void manualBarcode(){layout("Barcode invoeren");EditText e=input("EAN / barcode","");button("Product openen",()->{if(e.getText().toString().trim().isEmpty())toast("Vul een barcode in");else product(e.getText().toString().trim());});}
    private void product(String code){currentBarcode=code;String name=db.productName(code);if(name==null){layout("Nieuw product");note("Barcode "+code+" is nog onbekend. Vul de gegevens zelf in.");EditText n=input("Productnaam","");EditText size=input("Inhoud, bijvoorbeeld 500 ml","");button("Product bewaren",()->{String title=n.getText().toString().trim();if(title.isEmpty()){toast("Naam is verplicht");return;}db.product(code,title,size.getText().toString().trim());product(code);});return;}
        layout(name);note("Barcode: "+code);button("Toevoegen aan boodschappenlijst",()->{db.addShopping(code);toast("Toegevoegd aan lijst");});button("Prijs vastleggen",()->priceForm(code,null,null));line("Prijsgegevens per winkel",null);
        Set<String> shown=new HashSet<>();long now=System.currentTimeMillis();
        try(Cursor c=db.prices(code)){while(c.moveToNext()){String shop=c.getString(0);if(!shown.add(shop))continue;int cents=c.getInt(1);String source=c.getString(2);long observed=c.getLong(3);Long until=c.isNull(4)?null:c.getLong(4);Integer original=c.isNull(5)?null:c.getInt(5);
            boolean active=source.equals("ACTIE")&&until!=null&&until>=now;
            String label=shop+"  "+(active&&original!=null?euro(original)+" → ":"")+euro(cents)+"  "+(active?"ACTIE":source.equals("BON")?"bonprijs van "+date(observed):source.equals("ACTIE")?"actie verlopen":"handmatig van "+date(observed));line(label,null);
        }}if(shown.isEmpty())note("Nog geen winkelprijzen voor dit product.");
    }
    private String euro(int cents){return String.format(Locale.GERMANY,"€ %.2f",cents/100.0);}
    private String date(long ms){return new SimpleDateFormat("dd-MM-yyyy",Locale.getDefault()).format(new Date(ms));}
    private void list(){layout("Boodschappenlijst");int count=0;try(Cursor c=db.list()){while(c.moveToNext()){count++;String code=c.getString(0),name=c.getString(1);int qty=c.getInt(2);line(qty+" × "+name+"\n"+code,()->product(code));}}if(count==0)note("Je lijst is nog leeg.");button("Barcode scannen",this::scan);button("Winkels vergelijken",this::compare);}
    private void compare(){layout("Winkelvergelijking");int n=0;try(Cursor c=db.list()){n=c.getCount();}if(n==0){note("Voeg eerst producten toe aan je lijst.");return;}for(String shop:shops){int found=0;long total=0;try(Cursor c=db.allForShop(shop)){while(c.moveToNext()){if(!c.isNull(3)){found++;total+=(long)c.getInt(2)*c.getInt(3);}}}final int matched=found;final long sum=total;line(shop+" — "+matched+"/"+n+" prijzen"+(matched==n?" · totaal "+euro((int)Math.min(sum,Integer.MAX_VALUE)):" · totaal onvolledig"),()->shopDetails(shop));}}
    private void shopDetails(String shop){layout(shop);int missing=0;long total=0;try(Cursor c=db.allForShop(shop)){while(c.moveToNext()){String name=c.getString(1);int qty=c.getInt(2);if(c.isNull(3)){missing++;line(qty+" × "+name+" — prijs onbekend",null);}else{int amount=c.getInt(3);total+=(long)qty*amount;line(qty+" × "+name+" — "+euro(amount)+" per stuk",null);}}}note(missing==0?"Volledig op basis van recent vastgelegde prijzen: "+euro((int)Math.min(total,Integer.MAX_VALUE)):"Totaal onvolledig: "+missing+" prijsregels ontbreken.");}
    private Integer parseCents(String s){String v=s.trim().replace("€","").replace(" ","").replace(",",".");try{java.math.BigDecimal n=new java.math.BigDecimal(v);int cents=n.movePointRight(2).intValueExact();return cents<0?null:cents;}catch(Exception ex){return null;}}
    private void priceForm(String code,String suggestedShop,String suggestedAmount){layout("Prijs vastleggen");note("Product: "+db.productName(code));EditText shop=input("Winkel",suggestedShop==null?"":suggestedShop);EditText amount=input("Betaalde prijs in euro",suggestedAmount==null?"":suggestedAmount);
        button("Bewaar als bonprijs",()->{String s=shop.getText().toString().trim();Integer c=parseCents(amount.getText().toString());if(s.isEmpty()||c==null){toast("Controleer winkel en prijs");return;}db.recordPrice(code,s,c,"BON",null,null);product(code);});
        button("Bewaar als handmatig geobserveerde prijs",()->{String s=shop.getText().toString().trim();Integer c=parseCents(amount.getText().toString());if(s.isEmpty()||c==null){toast("Controleer winkel en prijs");return;}db.recordPrice(code,s,c,"HANDMATIG",null,null);product(code);});
        button("Actie vastleggen",()->{String s=shop.getText().toString().trim();Integer c=parseCents(amount.getText().toString());if(s.isEmpty()||c==null){toast("Controleer winkel en actieprijs");return;}actionForm(code,s,c);});}
    private void actionForm(String code,String shop,int cents){layout("Actie controleren");note(shop+" — "+euro(cents));EditText original=input("Normale prijs (€), optioneel","");EditText days=input("Nog geldig (aantal dagen)","7");button("Actie bewaren",()->{try{int d=Integer.parseInt(days.getText().toString().trim());Integer regular=original.getText().toString().trim().isEmpty()?null:parseCents(original.getText().toString());if(d<1||d>365||(!original.getText().toString().trim().isEmpty()&&regular==null)){toast("Controleer prijs en geldigheid");return;}db.recordPrice(code,shop,cents,"ACTIE",System.currentTimeMillis()+86400000L*d,regular);product(code);}catch(Exception e){toast("Vul een geldig aantal dagen in");}});}
    private void receipt(){layout("Bon scannen");note("Maak een foto en controleer de herkende tekst. Bonregels hebben meestal geen barcode; koppel elke prijs zelf aan het juiste product.");button("Foto van bon maken",()->{try{Intent i=new Intent("android.media.action.IMAGE_CAPTURE");startActivityForResult(i,CAMERA_REQUEST);}catch(Exception e){toast("Geen camera-app beschikbaar");}});button("Bonprijs handmatig toevoegen",this::manualReceipt);}
    private void manualReceipt(){layout("Bonprijs toevoegen");EditText barcode=input("Barcode van gekocht product","");EditText shop=input("Winkel","");EditText name=input("Productnaam als nieuw product","");EditText amount=input("Prijs (€)","");button("Controleer en bewaar",()->{String b=barcode.getText().toString().trim(),s=shop.getText().toString().trim();Integer cents=parseCents(amount.getText().toString());if(b.isEmpty()||s.isEmpty()||cents==null){toast("Barcode, winkel en prijs zijn verplicht");return;}if(db.productName(b)==null){String n=name.getText().toString().trim();if(n.isEmpty()){toast("Vul ook de productnaam in");return;}db.product(b,n,"");}db.recordPrice(b,s,cents,"BON",null,null);product(b);});}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=CAMERA_REQUEST||result!=RESULT_OK||data==null){if(req==CAMERA_REQUEST)toast("Geen bonfoto ontvangen");return;}
        Object obj=data.getExtras()==null?null:data.getExtras().get("data");if(!(obj instanceof Bitmap)){toast("Camera leverde geen foto. Voeg de prijs handmatig toe.");return;}
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap((Bitmap)obj,0))
            .addOnSuccessListener(txt->receiptReview(txt.getText())).addOnFailureListener(e->toast("Bonherkenning mislukt. Voeg de prijs handmatig toe."));}
    private void receiptReview(String raw){layout("Bon controleren");note("Herkende tekst (controleer de bedragen):");TextView content=text(raw.isEmpty()?"Geen tekst gevonden":raw,14,white);body.addView(content);button("Product en prijs koppelen",this::manualReceipt);}
    @Override public void onBackPressed(){home();}
}
