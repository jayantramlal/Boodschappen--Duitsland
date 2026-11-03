package nl.jays.boodschappenbenelux;

import org.json.*;
import java.util.*;

final class ReceiptDraft {
    String id=UUID.randomUUID().toString(),shop="",date="",totalText="",raw="",error="";
    boolean metadataEdited,needsTotalReview;
    final ArrayList<ReceiptParser.Line> lines=new ArrayList<>();
    final ArrayList<Page> pages=new ArrayList<>();
    final ArrayList<String> pending=new ArrayList<>();
    final Set<String> removed=new HashSet<>();
    static final class Page {
        String file="",hash="",text="";boolean done,qualityWarning;
    }
    long total(){long n=0;for(ReceiptParser.Line line:lines)n=Math.addExact(n,line.net());return n;}
    void recognize(){
        ArrayList<String> parts=new ArrayList<>();for(Page p:pages)if(p.done)parts.add(p.text);
        String merged=ReceiptParser.mergePages(parts);if(merged.equals(raw))return;
        ReceiptParser.Document parsed=ReceiptParser.document(merged);raw=merged;
        if(!metadataEdited){shop=parsed.shop;date=parsed.date;totalText=parsed.totalCents==null?"":String.format(Locale.ROOT,"%.2f",parsed.totalCents/100.0);}
        Map<String,ReceiptParser.Line> existing=new HashMap<>();for(ReceiptParser.Line l:lines)existing.put(l.id,l);
        for(int i=0;i<parsed.lines.size();i++){
            String key="ocr-"+i;ReceiptParser.Line proposed=parsed.lines.get(i);proposed.id=key;
            if(removed.contains(key))continue;
            ReceiptParser.Line old=existing.get(key);
            if(old==null){lines.add(proposed);}
            else if(!old.edited){int at=lines.indexOf(old);lines.set(at,proposed);}
            // Do not overwrite corrected items, even after adding more photos.
        }
        // A single matching boundary line is ambiguous. Mark it instead of deleting a real purchase.
        for(int i=1;i<lines.size();i++){
            ReceiptParser.Line a=lines.get(i-1),b=lines.get(i);
            if(a.label.equals(b.label)&&a.cents==b.cents&&pages.size()>1)b.flag("Mogelijke overlap: controleer dubbele regel");
        }
    }
    void validate(){
        if(shop.trim().isEmpty())throw new IllegalArgumentException("Vul de winkel in");
        if(ReceiptParser.dateMillis(date)==null)throw new IllegalArgumentException("Controleer de aankoopdatum (dd-mm-jjjj)");
        if(lines.isEmpty())throw new IllegalArgumentException("Voeg eerst een product toe");
        if(lines.size()>1500)throw new IllegalArgumentException("Te veel regels");
        if(!totalText.trim().isEmpty()&&(ReceiptParser.money(totalText)==null||ReceiptParser.money(totalText)<0))throw new IllegalArgumentException("Controleer het bontotaal");
        for(ReceiptParser.Line l:lines){
            if(l.label.trim().isEmpty())throw new IllegalArgumentException("Vul alle productnamen in");
            if(!Arrays.asList(ReceiptParser.PRODUCT,ReceiptParser.DISCOUNT,ReceiptParser.DEPOSIT,ReceiptParser.RETURN).contains(l.type))throw new IllegalArgumentException("Ongeldig regeltype");
            try {java.math.BigDecimal q=new java.math.BigDecimal(l.quantity.replace(',','.'));
                if(q.signum()<=0||q.compareTo(new java.math.BigDecimal("10000"))>0)throw new Exception();
                if(l.unitText!=null&&(ReceiptParser.money(l.unitText)==null||ReceiptParser.money(l.unitText)<0))throw new Exception();
                if(l.discountText!=null&&(ReceiptParser.money(l.discountText)==null||ReceiptParser.money(l.discountText)<0))throw new Exception();
                if(l.discountCents<0||l.discountCents>Math.max(0,l.cents)||Math.abs((long)l.cents)>100000000)throw new Exception();
                if(l.type.equals(ReceiptParser.PRODUCT)&&l.net()<0)throw new Exception();
                if(!l.barcode.isEmpty()&&!l.barcode.matches("[A-Za-z0-9:_-]{1,160}"))throw new Exception();
            }catch(Exception e){throw new IllegalArgumentException("Controleer aantal, prijs of korting bij "+l.label);}
        }
        total();
    }
    JSONObject json() throws JSONException {
        JSONObject j=new JSONObject();j.put("id",id);j.put("shop",shop);j.put("date",date);j.put("totalText",totalText);j.put("raw",raw);j.put("error",error);j.put("metadataEdited",metadataEdited);j.put("needsTotalReview",needsTotalReview);
        JSONArray a=new JSONArray();for(ReceiptParser.Line l:lines){JSONObject o=new JSONObject();o.put("id",l.id);o.put("label",l.label);o.put("barcode",l.barcode);o.put("quantity",l.quantity);o.put("type",l.type);o.put("cents",l.cents);o.put("unitCents",l.unitCents);o.put("discountCents",l.discountCents);o.put("uncertain",l.uncertain);o.put("edited",l.edited);o.put("reason",l.reason);o.put("unitText",l.unitText==null?JSONObject.NULL:l.unitText);o.put("discountText",l.discountText==null?JSONObject.NULL:l.discountText);a.put(o);}j.put("lines",a);
        a=new JSONArray();for(Page p:pages){JSONObject o=new JSONObject();o.put("file",p.file);o.put("hash",p.hash);o.put("text",p.text);o.put("done",p.done);o.put("qualityWarning",p.qualityWarning);a.put(o);}j.put("pages",a);
        a=new JSONArray();for(String key:removed)a.put(key);j.put("removed",a);a=new JSONArray();for(String uri:pending)a.put(uri);j.put("pending",a);return j;
    }
    static ReceiptDraft from(JSONObject j) throws JSONException {
        ReceiptDraft d=new ReceiptDraft();d.id=j.getString("id");if(!d.id.matches("[a-fA-F0-9-]{36}"))throw new JSONException("Ongeldig concept");
        d.shop=j.optString("shop");d.date=j.optString("date");d.totalText=j.optString("totalText");d.raw=j.optString("raw");d.error=j.optString("error");d.metadataEdited=j.optBoolean("metadataEdited");d.needsTotalReview=j.optBoolean("needsTotalReview");
        JSONArray a=j.optJSONArray("lines");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);ReceiptParser.Line l=new ReceiptParser.Line(o.getString("label"),o.getInt("cents"));l.id=o.getString("id");l.barcode=o.optString("barcode");l.quantity=o.optString("quantity","1");l.type=o.optString("type",ReceiptParser.PRODUCT);l.unitCents=o.optInt("unitCents",Math.abs(l.cents));l.discountCents=o.optInt("discountCents");l.uncertain=o.optBoolean("uncertain");l.edited=o.optBoolean("edited");l.reason=o.optString("reason");l.unitText=o.isNull("unitText")?null:o.getString("unitText");l.discountText=o.isNull("discountText")?null:o.getString("discountText");d.lines.add(l);}
        a=j.optJSONArray("pages");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Page p=new Page();p.file=o.getString("file");if(!p.file.matches("[a-fA-F0-9-]+\\.jpg"))throw new JSONException("Ongeldige foto");p.hash=o.optString("hash");p.text=o.optString("text");p.done=o.optBoolean("done");p.qualityWarning=o.optBoolean("qualityWarning");d.pages.add(p);}
        a=j.optJSONArray("removed");if(a!=null)for(int i=0;i<a.length();i++)d.removed.add(a.getString(i));a=j.optJSONArray("pending");if(a!=null)for(int i=0;i<a.length();i++)d.pending.add(a.getString(i));return d;
    }
}
