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
import java.net.*;
import java.io.*;
import org.json.*;

public class MainActivity extends Activity {
    private final String[] shops={"Albert Heijn","Jumbo","Dirk","Lidl Nederland","PLUS","Kruidvat","REWE","EDEKA Schroff","Lidl Duitsland","ALDI SÜD","Kaufland","dm"};
    private Store db;
    private LinearLayout root,body;
    private int canvas,card,ink,muted,accent,soft,border,white=Color.WHITE;
    private boolean dark;
    private final ArrayDeque<Runnable> history=new ArrayDeque<>();
    private Runnable currentScreen;
    private String currentKey="";
    private boolean restoring;
    private long lastBack;
    private final int CAMERA_REQUEST=42, GALLERY_REQUEST=43;
    private final Set<Integer> savedReceiptLines=new HashSet<>();
    private String currentBarcode;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);db=new Store(this);
        dark=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("dark",false);
        palette();home();
    }
    private void palette(){
        canvas=Color.rgb(dark?15:250,dark?23:249,dark?20:244);
        card=Color.rgb(dark?28:255,dark?39:255,dark?35:253);
        ink=Color.rgb(dark?239:27,dark?246:41,dark?236:37);
        muted=Color.rgb(dark?170:101,dark?187:110,dark?175:104);
        accent=Color.rgb(dark?39:21,dark?126:91,dark?75:63);
        soft=Color.rgb(dark?34:232,dark?65:242,dark?53:231);
        border=Color.rgb(dark?54:224,dark?72:229,dark?61:222);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if(android.os.Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
    }
    private void screen(String key,String title,Runnable redraw){
        if(key.equals("home"))history.clear();
        else if(!restoring&&currentScreen!=null&&!key.equals(currentKey))history.push(currentScreen);
        currentKey=key;currentScreen=redraw;layout(title);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(16),dp(12),dp(16),dp(12));return t;}
    private GradientDrawable shape(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private void layout(String title){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(canvas);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12),dp(8),dp(12),dp(6));
        if(!"home".equals(currentKey)){
            TextView back=text("‹",32,ink);back.setGravity(Gravity.CENTER);back.setPadding(0,0,0,0);
            back.setContentDescription("Terug");header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
            back.setOnClickListener(v->onBackPressed());
        }
        TextView heading=text(title,20,ink);heading.setTypeface(null,1);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(dp(8),dp(8),dp(8),dp(8));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(!"settings".equals(currentKey)){
            TextView settings=text("⚙",23,ink);settings.setGravity(Gravity.CENTER);settings.setPadding(0,0,0,0);
            settings.setContentDescription("Instellingen");header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
            settings.setOnClickListener(v->settings());
        }
        root.addView(header);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(12),dp(20),dp(24));sc.addView(body);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(card);
        String[] labels={"Home","Lijst","Winkels","Bonnen"},symbols={"⌂","☷","▥","▤"};
        for(int i=0;i<labels.length;i++){final String label=labels[i];
            LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setGravity(Gravity.CENTER);
            int tint=currentKey.equals(label.toLowerCase(Locale.ROOT))||("lijst".equals(currentKey)&&label.equals("Lijst"))?accent:muted;
            TextView icon=text(symbols[i],23,tint);icon.setGravity(Gravity.CENTER);icon.setPadding(0,0,0,0);
            TextView caption=text(label,12,tint);caption.setGravity(Gravity.CENTER);caption.setPadding(0,0,0,0);
            item.addView(icon);item.addView(caption);nav.addView(item,new LinearLayout.LayoutParams(0,dp(64),1));
            item.setOnClickListener(v->{
                switch(label){case "Home":home();break;case "Lijst":list();break;case "Bonnen":receipt();break;default:compare();}
            });
        }root.addView(nav);
    }
    private void section(String label){TextView t=text(label,19,ink);t.setTypeface(null,1);t.setPadding(dp(2),dp(20),dp(2),dp(12));body.addView(t);}
    private void cardRow(String icon,String title,String detail,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(10),dp(10));row.setBackground(shape(card,18));
        TextView glyph=text(icon,24,accent);glyph.setGravity(Gravity.CENTER);glyph.setPadding(0,0,0,0);glyph.setBackground(shape(soft,14));row.addView(glyph,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(1);copy.setPadding(dp(14),0,dp(4),0);
        TextView main=text(title,17,ink);main.setTypeface(null,1);main.setPadding(0,0,0,0);copy.addView(main);
        if(detail!=null&&!detail.isEmpty()){TextView sub=text(detail,13,muted);sub.setPadding(0,dp(3),0,0);copy.addView(sub);}
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){TextView arrow=text("›",26,muted);arrow.setPadding(dp(6),0,dp(4),0);row.addView(arrow);row.setOnClickListener(v->action.run());}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);body.addView(row,p);
    }
    private void line(String s,Runnable action){TextView t=text(s,16,ink);t.setBackground(shape(card,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);body.addView(t,p);if(action!=null)t.setOnClickListener(v->action.run());}
    private void button(String s,Runnable r){TextView t=text(s,16,white);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);t.setBackground(shape(accent,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.bottomMargin=dp(12);body.addView(t,p);t.setOnClickListener(v->r.run());}
    private void secondary(String s,Runnable r){TextView t=text(s,15,accent);t.setGravity(Gravity.CENTER);t.setBackground(shape(soft,15));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(10);body.addView(t,p);t.setOnClickListener(v->r.run());}
    private void note(String s){body.addView(text(s,14,muted));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);e.setTextColor(ink);e.setHintTextColor(muted);e.setBackground(shape(card,14));e.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.bottomMargin=dp(10);body.addView(e,p);return e;}
    private void home(){
        screen("home","Boodschappen",this::home);
        TextView hello=text("Wat heb je nodig?",29,ink);hello.setTypeface(null,1);hello.setPadding(dp(2),dp(12),dp(2),dp(6));body.addView(hello);
        TextView intro=text("Houd je lijst en prijzen bij op één plek.",15,muted);intro.setPadding(dp(2),0,dp(2),dp(24));body.addView(intro);
        button("▣   Scan een product",this::scan);
        secondary("Barcode invoeren",this::manualBarcode);
        int count=0;try(Cursor c=db.list()){count=c.getCount();}
        final int items=count;
        section("Verder gaan");
        cardRow("☷","Mijn lijst",items==1?"1 product":items+" producten",this::list);
        cardRow("▥","Winkels","Vergelijk je lijst",this::compare);
        cardRow("▤","Bonnen","Voeg een betaalde prijs toe",this::receipt);
    }
    private void scan(){GmsBarcodeScanner scanner=GmsBarcodeScanning.getClient(this,new GmsBarcodeScannerOptions.Builder().enableAutoZoom().build());scanner.startScan()
        .addOnSuccessListener(result->{String code=result.getRawValue();if(code!=null&&!code.trim().isEmpty())lookupProduct(code.trim());else toast("Geen barcode gevonden");})
        .addOnFailureListener(e->{toast("Scanner niet beschikbaar. Voer de barcode handmatig in.");manualBarcode();});}
    private void manualBarcode(){screen("barcode","Barcode invoeren",this::manualBarcode);note("Typ de barcode of gebruik de camera.");EditText e=input("EAN / barcode","");button("Product openen",()->{if(e.getText().toString().trim().isEmpty())toast("Vul een barcode in");else lookupProduct(e.getText().toString().trim());});secondary("Camera openen",this::scan);}
    private void lookupProduct(String code){
        if(db.productName(code)!=null){product(code);return;}
        screen("lookup:"+code,"Product zoeken",()->lookupProduct(code));note("Zoeken naar barcode "+code+" in openbare productgegevens…");
        new Thread(()->{
            String[] domains={"world.openfoodfacts.org","world.openbeautyfacts.org","world.openpetfoodfacts.org","world.openproductsfacts.org"};
            String title=null,size="",imageUrl="";boolean error=false;
            for(String domain:domains){
                HttpURLConnection connection=null;
                try{
                    URL url=new URL("https://"+domain+"/api/v2/product/"+java.net.URLEncoder.encode(code,"UTF-8")+".json?fields=product_name,product_name_nl,quantity,image_front_url");
                    connection=(HttpURLConnection)url.openConnection();
                    connection.setConnectTimeout(4500);connection.setReadTimeout(4500);
                    connection.setRequestProperty("User-Agent","BoodschappenBeNeLuxDuitsland/1.0.0 (https://github.com/jayantramlal/Boodschappen--Duitsland)");
                    if(connection.getResponseCode()!=200){error=true;continue;}
                    try(InputStream stream=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[4096];int count;while((count=stream.read(buffer))!=-1&&bytes.size()<100000){bytes.write(buffer,0,count);}
                        JSONObject obj=new JSONObject(bytes.toString("UTF-8"));
                        if(obj.optInt("status",0)==1){
                            JSONObject item=obj.optJSONObject("product");
                            if(item!=null){title=item.optString("product_name_nl","").trim();if(title.isEmpty())title=item.optString("product_name","").trim();size=item.optString("quantity","").trim();imageUrl=item.optString("image_front_url","").trim();}
                            if(title!=null&&!title.isEmpty())break;
                        }
                    }
                }catch(Exception e){error=true;}finally{if(connection!=null)connection.disconnect();}
            }
            final String foundName=title,foundSize=size,foundImage=imageUrl;final boolean networkError=error;
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||!currentKey.equals("lookup:"+code))return;
                if(!history.isEmpty()){currentScreen=history.pop();currentKey="lookupComplete";}
                if(foundName!=null&&!foundName.isEmpty()){db.product(code,foundName,foundSize,foundImage);product(code);}
                else{if(networkError)toast("Product niet gevonden of verbinding ontbreekt. Voer het zelf in.");product(code);}
            });
        }).start();
    }
    private void product(String code){currentBarcode=code;String name=db.productName(code);if(name==null){screen("new:"+code,"Nieuw product",()->product(code));note("Barcode "+code+" is nog onbekend. Vul de gegevens zelf in.");EditText n=input("Productnaam","");EditText size=input("Inhoud, bijvoorbeeld 500 ml","");button("Product bewaren",()->{String title=n.getText().toString().trim();if(title.isEmpty()){toast("Naam is verplicht");return;}db.product(code,title,size.getText().toString().trim());if(!history.isEmpty()){currentScreen=history.pop();currentKey="newComplete";}product(code);});return;}
        screen("product:"+code,"Product",()->product(code));
        String imageUrl=db.productImage(code);
        if(imageUrl!=null&&!imageUrl.isEmpty())productPicture(imageUrl);
        else {TextView placeholder=text("▣",46,accent);placeholder.setGravity(Gravity.CENTER);placeholder.setBackground(shape(soft,20));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(154));ip.bottomMargin=dp(12);body.addView(placeholder,ip);}
        TextView productTitle=text(name,25,ink);productTitle.setTypeface(null,1);body.addView(productTitle);
        TextView codeLabel=text("Barcode  "+code,13,muted);codeLabel.setPadding(dp(16),0,dp(16),dp(12));body.addView(codeLabel);
        button("+  Voeg toe aan lijst",()->{db.addShopping(code);toast("Toegevoegd aan lijst");});
        secondary("Prijs vastleggen",()->priceForm(code,null,null));
        section("Prijzen");
        Map<String,PriceRow> best=new HashMap<>();long now=System.currentTimeMillis();
        try(Cursor c=db.prices(code)){while(c.moveToNext()){
            String shop=c.getString(0);int cents=c.getInt(1);String source=c.getString(2);long observed=c.getLong(3);
            Long until=c.isNull(4)?null:c.getLong(4);Integer original=c.isNull(5)?null:c.getInt(5);
            boolean active=source.equals("ACTIE")&&until!=null&&until>=now;
            boolean recent=source.equals("HANDMATIG")&&observed>=now-86400000L;
            int rank=active?0:recent?1:source.equals("BON")?2:3;
            PriceRow row=new PriceRow(shop,cents,source,observed,original,active,rank);
            PriceRow previous=best.get(shop);
            if(previous==null||rank<previous.rank||(rank==previous.rank&&observed>previous.observed))best.put(shop,row);
        }}
        List<PriceRow> rows=new ArrayList<>();
        for(String shop:shops)rows.add(best.containsKey(shop)?best.get(shop):new PriceRow(shop,0,"",0,null,false,4));
        rows.sort((a,b)->{int c=Integer.compare(a.rank>=2?1:0,b.rank>=2?1:0);if(c!=0)return c;c=Integer.compare(a.cents,b.cents);return c!=0?c:a.shop.compareTo(b.shop);});
        int known=0;for(PriceRow row:rows)if(row.rank<4){priceRow(row);known++;}
        if(known==0)note("Nog geen prijzen voor dit product.");
        final int missing=rows.size()-known;
        if(missing>0){final boolean[] shown={false};secondary("Toon "+missing+" winkels zonder prijs",()->{
            if(shown[0])return;shown[0]=true;
            for(PriceRow row:rows)if(row.rank>=4)priceRow(row);
        });}
    }
    private static class PriceRow {
        final String shop,source;final int cents,rank;final long observed;final Integer original;final boolean active;
        PriceRow(String shop,int cents,String source,long observed,Integer original,boolean active,int rank){
            this.shop=shop;this.cents=cents;this.source=source;this.observed=observed;this.original=original;this.active=active;this.rank=rank;
        }
    }
    private void productPicture(String address){
        android.widget.ImageView picture=new android.widget.ImageView(this);
        picture.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        picture.setPadding(dp(12),dp(12),dp(12),dp(12));
        picture.setBackground(shape(card,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(185));p.bottomMargin=dp(10);body.addView(picture,p);
        new Thread(()->{
            HttpURLConnection connection=null;
            try{
                URL url=new URL(address);
                String host=url.getHost().toLowerCase(Locale.ROOT);
                if(!url.getProtocol().equals("https")||!(host.equals("openfoodfacts.org")||host.endsWith(".openfoodfacts.org")||host.endsWith(".openbeautyfacts.org")||host.endsWith(".openpetfoodfacts.org")||host.endsWith(".openproductsfacts.org")))return;
                connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                if(connection.getContentLengthLong()>3000000)return;
                try(InputStream stream=connection.getInputStream()){
                    Bitmap bitmap=BitmapFactory.decodeStream(stream);
                    if(bitmap!=null)runOnUiThread(()->picture.setImageBitmap(bitmap));
                }
            }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        }).start();
    }
    private void priceRow(PriceRow row){
        String label;
        if(row.rank>=4)label=row.shop+" — prijs onbekend";
        else if(row.active)label=row.shop+"  "+(row.original!=null?euro(row.original)+"  ":"")+euro(row.cents)+"  ACTIE";
        else label=row.shop+"  "+euro(row.cents)+"  "+(row.source.equals("BON")?"bon van ":"waargenomen ")+date(row.observed)+(row.rank==2?" (historisch)":"");
        TextView t=text(label,16,ink);t.setBackground(shape(card,15));
        if(row.active&&row.original!=null){
            android.text.SpannableString span=new android.text.SpannableString(label);
            int from=label.indexOf(euro(row.original));
            span.setSpan(new android.text.style.StrikethroughSpan(),from,from+euro(row.original).length(),0);
            t.setText(span);
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);body.addView(t,p);
    }
    private String euro(int cents){return String.format(Locale.GERMANY,"€ %.2f",cents/100.0);}
    private String date(long ms){return new SimpleDateFormat("dd-MM-yyyy",Locale.getDefault()).format(new Date(ms));}
    private void list(){screen("lijst","Mijn lijst",this::list);int count=0;try(Cursor c=db.list()){while(c.moveToNext()){count++;String code=c.getString(0),name=c.getString(1);int qty=c.getInt(2);cardRow("✓",name,qty+" ×",()->product(code));}}if(count==0)note("Je lijst is leeg. Scan een product om te beginnen.");section("Toevoegen");button("▣   Scan product",this::scan);secondary("Barcode invoeren",this::manualBarcode);if(count>0)cardRow("▥","Vergelijk winkels","Bekijk prijzen voor je lijst",this::compare);}
    private void compare(){screen("winkels","Winkels",this::compare);int n=0;try(Cursor c=db.list()){n=c.getCount();}if(n==0){note("Voeg eerst producten toe aan je lijst.");button("Scan product",this::scan);return;}note("Prijsdekking voor je lijst van "+n+" producten");for(String shop:shops){int found=0;long total=0;try(Cursor c=db.allForShop(shop)){while(c.moveToNext()){if(!c.isNull(3)){found++;total+=(long)c.getInt(2)*c.getInt(3);}}}final int matched=found;final long sum=total;cardRow("▥",shop,matched==n?euro((int)Math.min(sum,Integer.MAX_VALUE))+" · alle prijzen bekend":matched+" van "+n+" prijzen bekend",()->shopDetails(shop));}}
    private void shopDetails(String shop){screen("shop:"+shop,shop,()->shopDetails(shop));int missing=0;long total=0;try(Cursor c=db.allForShop(shop)){while(c.moveToNext()){String name=c.getString(1);int qty=c.getInt(2);if(c.isNull(3)){missing++;line(qty+" × "+name+" — prijs onbekend",null);}else{int amount=c.getInt(3);total+=(long)qty*amount;line(qty+" × "+name+" — "+euro(amount)+" per stuk",null);}}}note(missing==0?"Volledig op basis van recent vastgelegde prijzen: "+euro((int)Math.min(total,Integer.MAX_VALUE)):"Totaal onvolledig: "+missing+" prijsregels ontbreken.");}
    private Integer parseCents(String s){String v=s.trim().replace("€","").replace(" ","").replace(",",".");try{java.math.BigDecimal n=new java.math.BigDecimal(v);int cents=n.movePointRight(2).intValueExact();return cents<0?null:cents;}catch(Exception ex){return null;}}
    private void priceForm(String code,String suggestedShop,String suggestedAmount){screen("price:"+code,"Prijs vastleggen",()->priceForm(code,suggestedShop,suggestedAmount));note("Product: "+db.productName(code));EditText shop=input("Winkel",suggestedShop==null?"":suggestedShop);EditText amount=input("Betaalde prijs in euro",suggestedAmount==null?"":suggestedAmount);
        button("Bewaar als bonprijs",()->{String s=shop.getText().toString().trim();Integer c=parseCents(amount.getText().toString());if(s.isEmpty()||c==null){toast("Controleer winkel en prijs");return;}db.recordPrice(code,s,c,"BON",null,null);product(code);});
        button("Bewaar als handmatig geobserveerde prijs",()->{String s=shop.getText().toString().trim();Integer c=parseCents(amount.getText().toString());if(s.isEmpty()||c==null){toast("Controleer winkel en prijs");return;}db.recordPrice(code,s,c,"HANDMATIG",null,null);product(code);});
        button("Actie vastleggen",()->{String s=shop.getText().toString().trim();Integer c=parseCents(amount.getText().toString());if(s.isEmpty()||c==null){toast("Controleer winkel en actieprijs");return;}actionForm(code,s,c);});}
    private void actionForm(String code,String shop,int cents){screen("action:"+code,"Actie controleren",()->actionForm(code,shop,cents));note(shop+" — "+euro(cents));EditText original=input("Normale prijs (€), optioneel","");EditText days=input("Nog geldig (aantal dagen)","7");button("Actie bewaren",()->{try{int d=Integer.parseInt(days.getText().toString().trim());Integer regular=original.getText().toString().trim().isEmpty()?null:parseCents(original.getText().toString());if(d<1||d>365||(!original.getText().toString().trim().isEmpty()&&regular==null)){toast("Controleer prijs en geldigheid");return;}db.recordPrice(code,shop,cents,"ACTIE",System.currentTimeMillis()+86400000L*d,regular);product(code);}catch(Exception e){toast("Vul een geldig aantal dagen in");}});}
    private void receipt(){screen("bonnen","Bonnen",this::receipt);note("Voeg een bon toe om betaalde prijzen te bewaren.");section("Bon toevoegen");button("Foto van bon maken",()->{try{Intent i=new Intent("android.media.action.IMAGE_CAPTURE");startActivityForResult(i,CAMERA_REQUEST);}catch(Exception e){toast("Geen camera-app beschikbaar");}});secondary("Foto kiezen",()->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,GALLERY_REQUEST);});cardRow("+","Prijs handmatig toevoegen","Zonder bonfoto",this::manualReceipt);}
    private void manualReceipt(){screen("manualReceipt","Bonprijs toevoegen",this::manualReceipt);EditText barcode=input("Barcode van gekocht product","");EditText shop=input("Winkel","");EditText name=input("Productnaam als nieuw product","");EditText amount=input("Prijs (€)","");button("Controleer en bewaar",()->{String b=barcode.getText().toString().trim(),s=shop.getText().toString().trim();Integer cents=parseCents(amount.getText().toString());if(b.isEmpty()||s.isEmpty()||cents==null){toast("Barcode, winkel en prijs zijn verplicht");return;}if(db.productName(b)==null){String n=name.getText().toString().trim();if(n.isEmpty()){toast("Vul ook de productnaam in");return;}db.product(b,n,"");}db.recordPrice(b,s,cents,"BON",null,null);product(b);});}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if((req!=CAMERA_REQUEST&&req!=GALLERY_REQUEST)||result!=RESULT_OK||data==null)return;
        try {
            InputImage image;
            if(req==GALLERY_REQUEST&&data.getData()!=null)image=InputImage.fromFilePath(this,data.getData());
            else {
                Object obj=data.getExtras()==null?null:data.getExtras().get("data");
                if(!(obj instanceof Bitmap)){toast("Camera leverde geen bruikbare foto. Kies een volledige bonfoto.");return;}
                image=InputImage.fromBitmap((Bitmap)obj,0);
            }
            screen("receiptLoading","Bon lezen",this::receipt);note("Tekstherkenning bezig…");
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener(txt->{savedReceiptLines.clear();receiptReview(txt.getText());})
                .addOnFailureListener(e->toast("Bonherkenning mislukt. Voeg de prijs handmatig toe."));
        }catch(Exception e){toast("Bonfoto kon niet worden geopend.");}
    }
    private void receiptReview(String raw){
        screen("receiptReview","Bon controleren",()->receiptReview(raw));
        String suggestedShop=ReceiptParser.shop(raw);
        note("Controleer elke regel en koppel die aan een barcode. De bonprijs wordt pas opgeslagen na jouw bevestiging.");
        java.util.List<ReceiptParser.Line> lines=ReceiptParser.parse(raw);
        if(lines.isEmpty())note("Geen afzonderlijke productregels gevonden. Je kunt de tekst hieronder gebruiken voor handmatige invoer.");
        for(int i=0;i<lines.size();i++){
            if(savedReceiptLines.contains(i))continue;
            final int index=i;ReceiptParser.Line line=lines.get(i);
            line(line.label+"  "+euro(line.cents),()->receiptLine(raw,index,suggestedShop));
        }
        button("Bonprijs handmatig toevoegen",this::manualReceipt);
        note("Herkende bontekst:");body.addView(text(raw.isEmpty()?"Geen tekst herkend":raw,13,white));
    }
    private void receiptLine(String raw,int index,String suggestedShop){
        java.util.List<ReceiptParser.Line> lines=ReceiptParser.parse(raw);
        if(index<0||index>=lines.size())return;
        ReceiptParser.Line item=lines.get(index);
        screen("receiptLine:"+index,"Bonregel koppelen",()->receiptLine(raw,index,suggestedShop));
        EditText name=input("Productnaam",item.label);
        EditText barcode=input("Barcode van exact dit product","");
        EditText shop=input("Winkel",suggestedShop);
        EditText amount=input("Betaalde prijs (€)",String.format(Locale.GERMANY,"%.2f",item.cents/100.0));
        EditText purchased=input("Aankoopdatum (dd-mm-jjjj)",ReceiptParser.date(raw));
        note("Selecteer een bekend product hieronder of voer de barcode in. Controleer ook verpakking en prijs.");
        String term=item.label.split(" ")[0];
        if(term.length()>=3)try(Cursor c=db.findProducts(term)){
            while(c.moveToNext()){String foundCode=c.getString(0),foundName=c.getString(1);
                line(foundName+" · "+foundCode,()->{barcode.setText(foundCode);name.setText(foundName);});
            }
        }
        button("Gekoppelde bonprijs bewaren",()->{
            String b=barcode.getText().toString().trim(),n=name.getText().toString().trim(),sh=shop.getText().toString().trim();
            Integer cents=parseCents(amount.getText().toString());
            if(b.isEmpty()||n.isEmpty()||sh.isEmpty()||cents==null){toast("Controleer barcode, product, winkel en prijs");return;}
            String old=db.productName(b);
            if(old==null)db.product(b,n,"");
            else if(!old.equals(n)){toast("Barcode hoort bij "+old+". Kies het juiste product.");return;}
            Long bought=parseReceiptDate(purchased.getText().toString());if(bought==null){toast("Controleer de aankoopdatum");return;}db.recordPriceAt(b,sh,cents,"BON",null,null,bought);savedReceiptLines.add(index);receiptReview(raw);
        });
        button("Deze regel overslaan",()->{savedReceiptLines.add(index);receiptReview(raw);});
    }
    private Long parseReceiptDate(String value){
        try{
            SimpleDateFormat fmt=new SimpleDateFormat("dd-MM-yyyy",Locale.getDefault());
            fmt.setLenient(false);Date d=fmt.parse(value.trim());
            if(d==null||d.getTime()>System.currentTimeMillis()+86400000L)return null;
            return d.getTime();
        }catch(Exception e){return null;}
    }
    private void settings(){
        screen("settings","Instellingen",this::settings);
        note("Weergave");
        Switch toggle=new Switch(this);toggle.setText("Donkere modus");toggle.setTextSize(17);toggle.setTextColor(ink);
        toggle.setPadding(dp(16),dp(12),dp(16),dp(12));toggle.setChecked(dark);
        body.addView(toggle,new LinearLayout.LayoutParams(-1,dp(62)));
        toggle.setOnCheckedChangeListener((view,checked)->{
            dark=checked;getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("dark",checked).apply();
            palette();settings();
        });
        note("Je keuze blijft bewaard na het sluiten van de app.");
        secondary("Terug",this::onBackPressed);
    }
    @Override public void onBackPressed(){
        if("home".equals(currentKey)){
            long now=System.currentTimeMillis();
            if(now-lastBack<=2000){finish();return;}
            lastBack=now;toast("Druk nog een keer op terug om af te sluiten");return;
        }
        if(!history.isEmpty()){
            Runnable previous=history.pop();
            restoring=true;
            try{previous.run();}finally{restoring=false;}
        }else home();
    }
}
