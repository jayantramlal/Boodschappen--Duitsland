package nl.jays.boodschappenbenelux;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.documentscanner.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persistent review screen. OCR/camera callbacks never write purchases or prices. */
public class ReceiptActivity extends Activity {
    private static final int DOCUMENT=201,PHOTO=202,GALLERY=203;
    private static final AtomicBoolean WORKING=new AtomicBoolean(false);
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private ReceiptFiles files;private Store db;private ReceiptDraft draft;
    private AppPalette colors;private LinearLayout root,body,footer;
    private TextView totals,warning;private Button saveButton;private CheckBox mismatch;
    private boolean busy,readOnly,initializing,acknowledged,draftFailed,scannerOpening;
    private final HashMap<String,TextView> rowTotals=new HashMap<>();
    private final HashSet<String> invalid=new HashSet<>();
    @Override public void onCreate(Bundle state){
        boolean dark=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("dark",false);
        setTheme(dark?R.style.BeneluxDarkTheme:R.style.BeneluxTheme);super.onCreate(state);
        colors=AppPalette.forDarkMode(dark);db=new Store(this);files=new ReceiptFiles(getApplicationContext());
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        if(getIntent().hasExtra("purchaseId")){
            try{draft=files.purchase(getIntent().getStringExtra("purchaseId"));readOnly=true;render();}
            catch(Exception e){toast("Bon kon niet worden geopend");finish();}return;
        }
        try{draft=files.load();}catch(Exception e){draftFailed=true;showRecoveryError();return;}
        if(draft!=null&&db.hasPurchase(draft.id)){files.clear();draft=null;}
        boolean existing=draft!=null;if(draft==null)draft=new ReceiptDraft();
        if(!WORKING.get()&&!persist())return;
        render();
        if(WORKING.get()){busy=true;render();waitForWork();}
        else if(hasPendingWork())process();
        else if(!existing&&getIntent().getBooleanExtra("capture",false)&&state==null)openScanner();
        else if(!existing&&getIntent().getBooleanExtra("gallery",false)&&state==null)gallery();
    }
    private void showRecoveryError(){
        new AlertDialog.Builder(this).setTitle("Concept kon niet worden geopend")
            .setMessage("Je opgeslagen gegevens blijven bewaard. Sluit dit scherm en probeer opnieuw.")
            .setPositiveButton("Terug",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }
    private boolean hasPendingWork(){if(!draft.pending.isEmpty())return true;for(ReceiptDraft.Page p:draft.pages)if(!p.done)return true;return false;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int fill){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(14));d.setStroke(dp(1),colors.border);return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private void note(String value){TextView t=text(value,14,colors.muted);t.setPadding(dp(2),dp(6),dp(2),dp(8));body.addView(t);}
    private void section(String value){TextView t=text(value,19,colors.ink);t.setTypeface(null,1);t.setPadding(0,dp(16),0,dp(8));body.addView(t);}
    private Button button(String label,Runnable action,boolean primary){
        Button b=new Button(this);b.setText(label);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(primary?Color.WHITE:colors.accent);b.setBackgroundTintList(null);b.setBackground(shape(primary?colors.button:colors.soft));b.setOnClickListener(v->action.run());return b;
    }
    private void addButton(String label,Runnable action){Button b=button(label,action,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);body.addView(b,p);}
    private EditText field(LinearLayout parent,String label,String value,boolean numeric){
        LinearLayout group=new LinearLayout(this);group.setOrientation(1);group.setPadding(dp(3),dp(4),dp(3),dp(6));
        TextView title=text(label,12,colors.muted);title.setPadding(dp(4),0,0,dp(3));group.addView(title);
        EditText e=new EditText(this);e.setTextSize(15);e.setTextColor(colors.ink);e.setHintTextColor(colors.muted);e.setSingleLine(true);e.setHint(label);e.setContentDescription(label);e.setText(value);e.setPadding(dp(10),dp(7),dp(10),dp(7));e.setBackground(shape(colors.card));e.setMinimumHeight(dp(48));
        if(numeric)e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        group.addView(e,new LinearLayout.LayoutParams(-1,-2));
        parent.addView(group,new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?0:-1,-2,parent.getOrientation()==LinearLayout.HORIZONTAL?1:0));
        e.setEnabled(!readOnly&&!busy);return e;
    }
    private void changed(EditText e,Runnable action){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!initializing&&!readOnly){action.run();acknowledged=false;persist();updateTotals();}}public void afterTextChanged(Editable e){}});}
    private void render(){
        initializing=true;rowTotals.clear();invalid.clear();
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(colors.canvas);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(4),dp(12),dp(4));
        TextView back=text("‹",32,colors.ink);back.setGravity(Gravity.CENTER);back.setContentDescription("Terug");header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->onBackPressed());
        TextView title=text(readOnly?"Kassabon":"Bon controleren",21,colors.ink);title.setTypeface(null,1);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(12),0,dp(12),dp(20));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        if(busy){ProgressBar progress=new ProgressBar(this);body.addView(progress);note("Bon lezen… Je concept blijft bewaard.");}
        EditText shop=field(body,"Winkel",draft.shop,false);shop.setTag("receiptShop");changed(shop,()->{draft.shop=shop.getText().toString();draft.metadataEdited=true;});
        EditText date=field(body,"Aankoopdatum (dd-mm-jjjj)",draft.date,false);date.setTag("receiptDate");changed(date,()->{draft.date=date.getText().toString();draft.metadataEdited=true;});
        if(!draft.error.isEmpty())note(draft.error);
        if(!readOnly){
            if(!draft.pages.isEmpty())addButton("Bon bekijken · "+draft.pages.size()+" foto’s",()->viewPage(0));
            if(!busy){
                LinearLayout capture=new LinearLayout(this);capture.setPadding(0,dp(8),0,dp(8));
                Button more=button(draft.pages.isEmpty()?"Bon scannen":"Volgend bondeel",this::openScanner,false);capture.addView(more,new LinearLayout.LayoutParams(0,-2,1));
                Button gallery=button("Foto kiezen",this::gallery,false);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,-2,1);gp.leftMargin=dp(6);capture.addView(gallery,gp);body.addView(capture);
                addButton("Handmatig fotograferen",this::manualPhoto);
            }
        }else if(!draft.pages.isEmpty())addButton("Originele bon bekijken",()->viewPage(0));
        if(!readOnly&&!busy&&hasPendingWork()){
            addButton("Opnieuw lezen",this::process);
            if(!draft.pending.isEmpty())addButton("Onleesbare fotoselectie overslaan",()->new AlertDialog.Builder(this).setTitle("Selectie overslaan?").setMessage("Eerder gelezen foto’s en regels blijven bewaard.").setNegativeButton("Behouden",null).setPositiveButton("Overslaan",(d,w)->{draft.pending.clear();persist();render();}).show());
        }
        boolean quality=false;for(ReceiptDraft.Page p:draft.pages)quality|=p.qualityWarning;
        if(quality)note("Foto mogelijk onscherp of slecht belicht. Controleer de regels of maak een nieuwe foto.");
        section("Producten");
        if(draft.lines.isEmpty())note("Nog geen producten. Scan een bon of voeg een regel toe.");
        for(ReceiptParser.Line l:new ArrayList<>(draft.lines))renderLine(l);
        if(!readOnly&&!busy)addButton("+ Product toevoegen",()->{draft.lines.add(new ReceiptParser.Line("",0));persist();render();body.getChildAt(body.getChildCount()-2).requestFocus();});
        if(!readOnly&&!draft.raw.isEmpty())addButton("Herkende tekst",()->new AlertDialog.Builder(this).setTitle("Bontekst").setMessage(draft.raw).setPositiveButton("Sluiten",null).show());
        if(!readOnly&&!busy)addButton("Concept verwijderen",()->new AlertDialog.Builder(this).setTitle("Concept verwijderen?").setMessage("De nog niet opgeslagen regels worden verwijderd.").setNegativeButton("Behouden",null).setPositiveButton("Verwijderen",(x,w)->{files.discard(draft);finish();}).show());
        footer=new LinearLayout(this);footer.setOrientation(1);footer.setPadding(dp(14),dp(6),dp(14),dp(8));footer.setBackgroundColor(colors.card);
        EditText receiptTotal=field(footer,"Totaal op kassabon (€)",draft.totalText,true);receiptTotal.setTag("receiptTotal");changed(receiptTotal,()->{draft.totalText=receiptTotal.getText().toString();draft.metadataEdited=true;});
        totals=text("",16,colors.ink);totals.setTypeface(null,1);footer.addView(totals);
        warning=text("",13,colors.muted);footer.addView(warning);
        mismatch=new CheckBox(this);mismatch.setText("Verschil / ontbrekend bontotaal gecontroleerd");mismatch.setTextSize(13);mismatch.setTextColor(colors.ink);mismatch.setButtonTintList(ColorStateList.valueOf(colors.accent));mismatch.setMinHeight(dp(48));mismatch.setOnCheckedChangeListener((b,checked)->acknowledged=checked);footer.addView(mismatch);
        saveButton=button("Alles opslaan",this::saveAll,true);saveButton.setTag("saveAll");footer.addView(saveButton,new LinearLayout.LayoutParams(-1,dp(50)));root.addView(footer);
        if(readOnly){receiptTotal.setEnabled(false);saveButton.setVisibility(View.GONE);mismatch.setVisibility(View.GONE);}
        initializing=false;updateTotals();if(busy)enable(body,false);
    }
    private void renderLine(ReceiptParser.Line l){
        LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(8),dp(8),dp(8),dp(10));row.setBackground(shape(colors.card));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(10);body.addView(row,rp);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(typeLabel(l.type),13,colors.muted);label.setPadding(dp(4),0,0,0);top.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(!readOnly){Button remove=button("×",()->{draft.removed.add(l.id);draft.lines.remove(l);persist();acknowledged=false;render();},false);remove.setContentDescription("Verwijder "+l.label);top.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));label.setOnClickListener(v->chooseType(l));}
        row.addView(top);
        EditText name=field(row,"Productnaam",l.label,false);name.setTag("name:"+l.id);changed(name,()->{l.label=name.getText().toString();l.edited=true;});
        LinearLayout values=new LinearLayout(this);values.setOrientation(0);row.addView(values);
        EditText quantity=field(values,"Aantal",l.quantity,true),unit=field(values,"Stukprijs €",l.unitText==null?number(l.unitCents):l.unitText,true),discount=field(values,"Korting €",l.discountText==null?number(l.discountCents):l.discountText,true);
        quantity.setTag("quantity:"+l.id);unit.setTag("unit:"+l.id);discount.setTag("discount:"+l.id);
        TextView net=text("Regel: "+euro(l.net()),15,colors.ink);net.setPadding(dp(4),dp(5),0,0);row.addView(net);rowTotals.put(l.id,net);
        Runnable amounts=()->{
            l.edited=true;l.quantity=quantity.getText().toString();l.unitText=unit.getText().toString();l.discountText=discount.getText().toString();
            Integer price=ReceiptParser.money(l.unitText),off=ReceiptParser.money(l.discountText);
            try{if(price==null||off==null||off<0)throw new Exception();l.setQuantityAndUnit(l.quantity,price);l.discountCents=off;if(off>Math.max(0,l.cents))throw new Exception();invalid.remove(l.id);net.setText("Regel: "+euro(l.net()));}
            catch(Exception e){invalid.add(l.id);net.setText("Controleer aantal, prijs en korting");}
        };
        changed(quantity,amounts);changed(unit,amounts);changed(discount,amounts);
        if(l.uncertain){TextView check=text("◦ "+l.reason,13,colors.muted);check.setPadding(dp(4),dp(8),dp(4),dp(8));row.addView(check);if(!readOnly)check.setOnClickListener(v->{l.uncertain=false;l.edited=true;persist();check.setVisibility(View.GONE);});}
        if(!readOnly){TextView link=text(l.barcode.isEmpty()?"Product koppelen (optioneel)":"Gekoppeld: "+l.barcode,13,colors.accent);link.setMinHeight(dp(48));link.setGravity(Gravity.CENTER_VERTICAL);link.setPadding(dp(4),0,0,0);row.addView(link);link.setOnClickListener(v->linkProduct(l));}
    }
    private String typeLabel(String t){return t.equals(ReceiptParser.DEPOSIT)?"Statiegeld / Pfand":t.equals(ReceiptParser.DISCOUNT)?"Korting":t.equals(ReceiptParser.RETURN)?"Retour":"Product";}
    private void chooseType(ReceiptParser.Line l){
        String[] types={ReceiptParser.PRODUCT,ReceiptParser.DEPOSIT,ReceiptParser.DISCOUNT,ReceiptParser.RETURN};
        new AlertDialog.Builder(this).setTitle("Soort regel").setItems(new String[]{"Product","Statiegeld / Pfand","Korting","Retour"},(d,i)->{l.edited=true;l.type=types[i];l.cents=(i>=2?-1:1)*Math.abs(l.cents);l.discountCents=0;l.discountText=null;persist();acknowledged=false;render();}).show();
    }
    private void linkProduct(ReceiptParser.Line l){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(16),dp(6),dp(16),dp(6));EditText code=field(box,"Barcode (optioneel)",l.barcode,false);
        AutoCompleteTextView search=new AutoCompleteTextView(this);search.setHint("Zoek bestaand product");search.setTextColor(colors.ink);search.setHintTextColor(colors.muted);search.setMinimumHeight(dp(48));box.addView(search);
        ArrayList<String> names=new ArrayList<>(),codes=new ArrayList<>();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int before,int count){if(s.length()<2)return;names.clear();codes.clear();try(Cursor c=db.findProducts(s.toString())){while(c.moveToNext()){codes.add(c.getString(0));names.add(c.getString(1)+" · "+c.getString(0));}}search.setAdapter(new ArrayAdapter<>(ReceiptActivity.this,android.R.layout.simple_dropdown_item_1line,new ArrayList<>(names)));}});
        search.setOnItemClickListener((p,v,pos,id)->{String selected=String.valueOf(p.getItemAtPosition(pos));int delimiter=selected.lastIndexOf(" · ");if(delimiter>=0)code.setText(selected.substring(delimiter+3));});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Product koppelen").setView(box).setNegativeButton("Annuleren",null).setPositiveButton("Toepassen",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String b=code.getText().toString().trim();if(!b.isEmpty()&&!b.matches("[A-Za-z0-9:_-]{1,160}")){code.setError("Controleer barcode");return;}l.edited=true;l.barcode=b;persist();dialog.dismiss();render();}));dialog.show();
    }
    private void enable(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof android.view.ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)enable(g.getChildAt(i),enabled);}}
    private void updateTotals(){
        if(totals==null)return;long total=draft.total();totals.setText("Berekend: "+euro(total));Integer target=ReceiptParser.money(draft.totalText);
        boolean differs=target==null||target<0||total!=target;
        warning.setText(!invalid.isEmpty()?"Controleer de ongeldige invoer":target==null?"Bontotaal ontbreekt":total==target?"Totaal komt overeen":"Verschil: "+euro(total-target));
        if(!readOnly){mismatch.setVisibility(differs?View.VISIBLE:View.GONE);if(!acknowledged&&mismatch.isChecked())mismatch.setChecked(false);saveButton.setEnabled(!busy&&!draftFailed&&invalid.isEmpty());}
    }
    private boolean persist(){
        if(readOnly||draft==null)return true;
        try{files.save(draft);draftFailed=false;return true;}catch(Exception e){draftFailed=true;toast("Concept kon niet worden bewaard. Controleer vrije opslagruimte.");if(saveButton!=null)saveButton.setEnabled(false);return false;}
    }
    @Override protected void onPause(){if(!busy)persist();super.onPause();}
    @Override public void onBackPressed(){if(readOnly){finish();return;}if(busy){finish();return;}if(persist())finish();}
    private void gallery(){if(busy||scannerOpening)return;persist();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,GALLERY);}catch(Exception e){toast("Geen fotokiezer beschikbaar");}}
    private void manualPhoto(){
        if(busy||scannerOpening)return;if(!persist())return;
        try{File directory=new File(getCacheDir(),"receipt-camera");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Geen opslagruimte");File f=new File(directory,"receipt-camera.jpg");Uri u=FileProvider.getUriForFile(this,getPackageName()+".receiptfiles",f);
            Intent i=new Intent("android.media.action.IMAGE_CAPTURE");i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,u);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Bonfoto",u));startActivityForResult(i,PHOTO);
        }catch(Exception e){toast("Camera niet beschikbaar. Kies een bestaande foto.");}
    }
    private void openScanner(){
        if(busy||scannerOpening)return;if(!persist())return;scannerOpening=true;
        try{GmsDocumentScannerOptions options=new GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(30).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();
            GmsDocumentScanning.getClient(options).getStartScanIntent(this).addOnSuccessListener(sender->{
                scannerOpening=false;if(isFinishing()||isDestroyed())return;
                try{startIntentSenderForResult(sender,DOCUMENT,null,0,0,0);}catch(Exception e){toast("Automatische scanner niet beschikbaar");manualPhoto();}
            }).addOnFailureListener(e->{scannerOpening=false;if(!isFinishing()){toast("Automatische scanner niet beschikbaar. Handmatige camera wordt geopend.");manualPhoto();}});
        }catch(Exception e){scannerOpening=false;toast("Automatische scanner niet beschikbaar");manualPhoto();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        ArrayList<Uri> uris=new ArrayList<>();
        try{
            if(request==DOCUMENT){GmsDocumentScanningResult r=GmsDocumentScanningResult.fromActivityResultIntent(data);if(r!=null&&r.getPages()!=null)for(GmsDocumentScanningResult.Page p:r.getPages())uris.add(p.getImageUri());}
            else if(request==PHOTO){File directory=new File(getCacheDir(),"receipt-camera");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Geen opslagruimte");File f=new File(directory,"receipt-camera.jpg");if(f.isFile()&&f.length()>0)uris.add(Uri.fromFile(f));}
            else if(request==GALLERY&&data!=null){if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());}
            if(uris.isEmpty()){toast("Geen bonfoto ontvangen");return;}
            if(draft.pages.size()+uris.size()>30){toast("Maximaal 30 bonfoto’s per aankoop");return;}
            for(Uri u:uris){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(!draft.pending.contains(u.toString()))draft.pending.add(u.toString());}
            if(persist())process();
        }catch(Exception e){toast("Bonfoto kon niet worden geopend");}
    }
    private void process(){
        if(!WORKING.compareAndSet(false,true)){busy=true;render();waitForWork();return;}
        busy=true;render();final ReceiptDraft work=draft;final ReceiptFiles storage=files;
        WORK.execute(()->{
            TextRecognizer recognizer=null;
            try{
                recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                work.error="";
                while(!work.pending.isEmpty()){
                    String uri=work.pending.get(0);
                    ReceiptDraft.Page p=storage.importImage(work,Uri.parse(uri));
                    if(p!=null)work.pages.add(p);
                    work.pending.remove(0);storage.save(work);
                }
                for(ReceiptDraft.Page p:work.pages)if(!p.done){
                    Text result=Tasks.await(recognizer.process(InputImage.fromFilePath(getApplicationContext(),Uri.fromFile(storage.page(work,p)))),45,TimeUnit.SECONDS);
                    p.text=orderedText(result);p.done=true;
                    // Repeated auto captures must not create extra receipt lines.
                    work.recognize();storage.save(work);
                }
                if(work.lines.isEmpty())work.error="Geen productregels herkend. Maak een scherpere foto of voeg regels toe.";
                ArrayList<ReceiptDraft.Page> duplicates=new ArrayList<>();HashSet<String> seen=new HashSet<>();
                for(ReceiptDraft.Page page:work.pages)if(page.done&&!page.text.trim().isEmpty()&&!seen.add(ReceiptParser.normalized(page.text)))duplicates.add(page);
                for(ReceiptDraft.Page page:duplicates){work.pages.remove(page);storage.page(work,page).delete();}
                storage.save(work);
            }catch(Exception e){work.error="Niet alle foto’s konden worden gelezen. Je concept blijft bewaard. Probeer opnieuw of voeg regels toe.";try{storage.save(work);}catch(Exception ignored){}}
            finally{if(recognizer!=null)recognizer.close();WORKING.set(false);MAIN.post(()->{if(!isFinishing()&&!isDestroyed()){busy=false;draft=work;render();if(hasPendingWork())addButton("Opnieuw proberen",this::process);}});}
        });
    }
    private void waitForWork(){MAIN.postDelayed(()->{if(isFinishing()||isDestroyed())return;if(WORKING.get()){waitForWork();return;}try{ReceiptDraft loaded=files.load();if(loaded!=null)draft=loaded;busy=false;render();if(hasPendingWork())addButton("Verder lezen",this::process);}catch(Exception e){busy=false;showRecoveryError();}},350);}
    static String orderedText(Text result){
        ArrayList<Text.Line> lines=new ArrayList<>();for(Text.TextBlock block:result.getTextBlocks())lines.addAll(block.getLines());
        lines.sort((a,b)->{android.graphics.Rect x=a.getBoundingBox(),y=b.getBoundingBox();if(x==null||y==null)return 0;return Integer.compare(x.centerY(),y.centerY());});
        StringBuilder raw=new StringBuilder();ArrayList<Text.Line> row=new ArrayList<>();int center=0,height=0;
        for(Text.Line l:lines){Rect r=l.getBoundingBox();if(r==null)continue;if(!row.isEmpty()&&Math.abs(r.centerY()-center)>Math.max(height,r.height())*.55){appendRow(raw,row);row.clear();}row.add(l);center=r.centerY();height=r.height();}
        appendRow(raw,row);return raw.toString();
    }
    private static void appendRow(StringBuilder out,ArrayList<Text.Line> row){row.sort((a,b)->Integer.compare(a.getBoundingBox().left,b.getBoundingBox().left));if(row.isEmpty())return;for(Text.Line l:row){if(out.length()>0&&out.charAt(out.length()-1)!='\n')out.append(' ');out.append(l.getText());}out.append('\n');}
    private void saveAll(){
        if(busy||readOnly||WORKING.get())return;
        try{
            if(hasPendingWork())throw new IllegalArgumentException("Lees eerst de resterende foto’s of verwijder het ongelezen bondeel");
            draft.validate();Integer target=ReceiptParser.money(draft.totalText);
            if((target==null||draft.total()!=target)&&!acknowledged){toast("Controleer het verschil met het bontotaal en vink dit aan");return;}
            if(!invalid.isEmpty())throw new IllegalArgumentException("Controleer de invoer");
            saveButton.setEnabled(false);if(!persist())return;
            files.archive(draft);String fingerprint=draft.raw.trim().isEmpty()?"manual:"+draft.id:ReceiptFiles.fingerprint(draft.raw);
            boolean saved=db.savePurchase(draft,fingerprint);if(saved||db.hasPurchase(draft.id))files.clear();else files.discard(draft);readOnly=true;toast(saved?"Aankoop opgeslagen":"Deze bon is al opgeslagen; niets dubbel geboekt");finish();
        }catch(Exception e){toast(e.getMessage()==null?"Opslaan mislukt; je concept blijft bewaard":e.getMessage());if(saveButton!=null)saveButton.setEnabled(true);}
    }
    private void viewPage(int index){
        if(index<0||index>=draft.pages.size())return;
        ReceiptDraft.Page p=draft.pages.get(index);ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;Bitmap b=BitmapFactory.decodeFile(files.page(draft,p).getAbsolutePath(),o);if(b==null){toast("Bonfoto niet beschikbaar");return;}image.setImageBitmap(b);
        ScrollView scroll=new ScrollView(this);scroll.addView(image);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("Bon · "+(index+1)+" / "+draft.pages.size()).setView(scroll).setPositiveButton(index+1<draft.pages.size()?"Volgende":"Sluiten",(d,w)->{if(index+1<draft.pages.size())viewPage(index+1);});
        if(index>0)builder.setNeutralButton("Vorige",(d,w)->viewPage(index-1));
        if(!readOnly&&!busy)builder.setNegativeButton("Foto verwijderen",(d,w)->new AlertDialog.Builder(this).setTitle("Foto verwijderen?").setMessage("De herkende regels blijven staan en zijn apart te wijzigen.").setNegativeButton("Behouden",null).setPositiveButton("Verwijderen",(x,y)->{draft.pages.remove(index);persist();render();}).show());
        AlertDialog dialog=builder.create();dialog.setOnDismissListener(d->b.recycle());dialog.show();
    }
    private static String number(long cents){return String.format(Locale.ROOT,"%.2f",cents/100.0);}
    private static String euro(long cents){return String.format(Locale.GERMANY,"€ %.2f",cents/100.0);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
