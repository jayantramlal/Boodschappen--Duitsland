package nl.jays.boodschappenbenelux;

import java.math.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

/** Conservative receipt parser. Recognition is always a proposal, never a database write. */
final class ReceiptParser {
    static final String PRODUCT="PRODUCT", DISCOUNT="DISCOUNT", DEPOSIT="DEPOSIT", RETURN="RETURN";
    private static final Pattern MONEY=Pattern.compile("(?i)(-?\\d{1,6}[,.]\\d{2})(-?)\\s*(?:EUR|€)?\\s*(?:[A-D*])?\\s*$");
    private static final Pattern QUANTITY=Pattern.compile("(?i)(\\d{1,4}(?:[,.]\\d{1,3})?)\\s*(?:stk\\.?|st\\.?|kg|l)?\\s*[x×*]\\s*(\\d{1,5}[,.]\\d{2})");
    static final class Line {
        String id=UUID.randomUUID().toString(),label,barcode="",quantity="1",type=PRODUCT;
        int cents,unitCents,discountCents;
        boolean uncertain,edited;
        String reason="",unitText=null,discountText=null;
        Line(String label,int cents){this.label=label;this.cents=cents;this.unitCents=Math.abs(cents);}
        long net(){return (long)cents-discountCents;}
        void flag(String why){uncertain=true;reason=reason.isEmpty()?why:reason+" · "+why;}
        void setQuantityAndUnit(String q,int unit){
            BigDecimal n=new BigDecimal(q.replace(',','.'));
            if(n.signum()<=0||n.compareTo(new BigDecimal("10000"))>0||unit<0)throw new IllegalArgumentException("Aantal of prijs ongeldig");
            int total=n.multiply(BigDecimal.valueOf(unit)).setScale(0,RoundingMode.HALF_UP).intValueExact();
            if(total>100000000)throw new IllegalArgumentException("Bedrag te groot");
            quantity=n.stripTrailingZeros().toPlainString();unitCents=unit;cents=(type.equals(DISCOUNT)||type.equals(RETURN)||(type.equals(DEPOSIT)&&cents<0))?-total:total;
        }
    }
    static final class Document {
        String shop="",date="",raw="";
        Integer totalCents;
        final List<Line> lines=new ArrayList<>();
        final List<String> warnings=new ArrayList<>();
    }
    static Integer money(String value){
        try{
            String s=value.trim().replace("€","").replace("EUR","").replace(" ","");
            if(s.endsWith("-"))s="-"+s.substring(0,s.length()-1);
            if(s.contains(","))s=s.replace(".","").replace(',','.');
            int v=new BigDecimal(s).movePointRight(2).intValueExact();
            return Math.abs((long)v)>100000000?null:v;
        }catch(Exception e){return null;}
    }
    static Document document(String raw){
        Document d=new Document();d.raw=raw;d.shop=shop(raw);d.date=date(raw);
        String pending="";Line previous=null;
        for(String s:raw.split("\\r?\\n")){
            String line=s.trim().replaceAll("\\s+"," ");if(line.isEmpty())continue;
            String lower=line.toLowerCase(Locale.ROOT);
            if(lower.contains("ersparnis")||lower.contains("sie sparen"))continue;
            Matcher amount=MONEY.matcher(line);
            boolean has=amount.find();
            if(lower.matches("^(?:gesamt(?:betrag)?|gesamtsumme|summe|total|totaal|te betalen|zu zahlen|endbetrag|betrag eur)\\b.*")&&!lower.contains("erspar")){
                if(has){Integer v=money(amount.group(1)+amount.group(2));if(v!=null&&v>=0)d.totalCents=v;}
                pending="";previous=null;continue;
            }
            // Tax/payment/account numbers must never be offered as groceries.
            if(lower.matches("^(?:zwischen\\s*summe|subtotal|subtotaal|netto|brutto|mwst|ust|steuer|btw|bar(?:zahlung)?|gegeben|rückgeld|ruckgeld|wechselgeld|ec[- ]|kartenzahlung|visa|mastercard|girocard|pin\\b|zahlung|bezahlt|gezahlt|debit|karte|betaling|cash|saldo|ersparnis|sie sparen|datum|uhrzeit|kasse|kassa|bon[- ]?nr|beleg|transaktion|tel[.: ]|www\\.|https?://|\\d{5}\\s+).*")){
                pending="";continue;
            }
            if(lower.matches("^[a-d]?\\s*(?:7|19|9|21)[,.]\\d{2}\\s*%.*"))continue;
            Matcher quantity=QUANTITY.matcher(line);
            if(quantity.find()&&quantity.start()==0&&previous!=null){
                try{
                    String q=quantity.group(1).replace(',','.');Integer unit=money(quantity.group(2));
                    if(unit!=null){BigDecimal n=new BigDecimal(q);long calculated=n.multiply(BigDecimal.valueOf(unit)).setScale(0,RoundingMode.HALF_UP).longValueExact();
                        previous.quantity=n.stripTrailingZeros().toPlainString();previous.unitCents=unit;
                        if(Math.abs(calculated-previous.cents)>1)previous.flag("Aantal en regelbedrag controleren");}
                }catch(Exception e){previous.flag("Aantal controleren");}
                continue;
            }
            if(!has){
                // Only a short name directly adjacent to a price may be continued.
                if(line.matches(".*[\\p{L}].*")&&!line.matches(".*\\d{4,}.*")&&line.length()<65)pending=line;
                else pending="";
                continue;
            }
            String label=line.substring(0,amount.start()).replaceAll("[.*: ]+$","").trim();
            Integer cents=money(amount.group(1)+amount.group(2));if(cents==null)continue;
            if(label.isEmpty()&&!pending.isEmpty()){label=pending;pending="";}
            if(label.length()<2||!label.matches(".*[\\p{L}].*"))continue;
            boolean deposit=lower.contains("pfand")||lower.contains("statiegeld")||lower.contains("leergut");
            boolean discount=lower.matches(".*(rabatt|korting|coupon|gutschein|preisvorteil|aktionsvorteil|nachlass|lidl plus).*?");
            if(discount&&cents<0&&!deposit&&previous!=null&&!lower.matches(".*(coupon|gutschein|gesamt|einkauf).*")){
                if((long)previous.discountCents-cents<=Math.max(0,previous.cents)){previous.discountCents-=cents;pending="";continue;}
            }
            Line item=new Line(label,cents);
            if(deposit)item.type=DEPOSIT;
            else if(cents<0)item.type=discount?DISCOUNT:RETURN;
            else if(discount){item.type=DISCOUNT;item.cents=-cents;item.flag("Teken van korting controleren");}
            quantity=QUANTITY.matcher(label);
            if(quantity.find()){
                try{item.quantity=new BigDecimal(quantity.group(1).replace(',','.')).stripTrailingZeros().toPlainString();
                    Integer u=money(quantity.group(2));if(u!=null)item.unitCents=u;
                    String stripped=(label.substring(0,quantity.start())+label.substring(quantity.end())).trim();if(!stripped.isEmpty())item.label=stripped;
                    long sum=new BigDecimal(item.quantity).multiply(BigDecimal.valueOf(item.unitCents)).setScale(0,RoundingMode.HALF_UP).longValueExact();
                    if(Math.abs(sum-Math.abs((long)item.cents))>1)item.flag("Aantal en prijs controleren");
                }catch(Exception e){item.flag("Aantal controleren");}
            }
            if(!pending.isEmpty()&&!pending.equals(label)){
                // A name ending in '-' indicates a reliably split label. Other headers stay separate.
                if(pending.endsWith("-"))item.label=pending.substring(0,pending.length()-1)+item.label;
            }
            pending="";
            if(item.quantity.equals("1")&&item.type.equals(PRODUCT)){
                Matcher prefix=Pattern.compile("^(\\d{1,2})\\s+(?!g\\b|kg\\b|ml\\b|l\\b)([\\p{L}].*)",Pattern.CASE_INSENSITIVE).matcher(item.label);
                if(prefix.matches()&&Integer.parseInt(prefix.group(1))>1){
                    item.quantity=prefix.group(1);item.label=prefix.group(2);item.unitCents=BigDecimal.valueOf(item.cents).divide(new BigDecimal(item.quantity),0,RoundingMode.HALF_UP).intValue();item.flag("Aantal afgeleid: controleren");
                }
            }
            if(item.label.matches(".*[?~�].*")||item.label.length()<3)item.flag("Naam controleren");
            if(item.cents==0)item.flag("Prijs controleren");
            d.lines.add(item);previous=item;
        }
        if(d.date.isEmpty())d.warnings.add("Aankoopdatum invullen");
        if(d.shop.isEmpty())d.warnings.add("Winkel invullen");
        if(d.totalCents==null)d.warnings.add("Bontotaal niet herkend");
        return d;
    }
    static List<Line> parse(String raw){return document(raw).lines;}
    static String date(String raw){
        Matcher m=Pattern.compile("\\b([0-3]?\\d)[./-]([01]?\\d)[./-](20\\d{2}|\\d{2})\\b").matcher(raw);
        while(m.find()){
            String y=m.group(3);if(y.length()==2)y="20"+y;
            String s=String.format(Locale.ROOT,"%02d-%02d-%s",Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),y);
            if(dateMillis(s)!=null)return s;
        }return "";
    }
    static Long dateMillis(String value){
        if(!value.matches("\\d{2}-\\d{2}-20\\d{2}"))return null;
        SimpleDateFormat f=new SimpleDateFormat("dd-MM-yyyy",Locale.ROOT);f.setLenient(false);
        ParsePosition p=new ParsePosition(0);Date d=f.parse(value,p);
        return d==null||p.getIndex()!=value.length()||d.getTime()>System.currentTimeMillis()+86400000L?null:d.getTime();
    }
    static String shop(String raw){
        String top=raw.substring(0,Math.min(500,raw.length())).toLowerCase(Locale.ROOT);
        String[][] names={{"albert heijn","Albert Heijn"},{"jumbo","Jumbo"},{"dirk","Dirk"},{"lidl","Lidl"},{"plus","PLUS"},{"kruidvat","Kruidvat"},{"rewe","REWE"},{"schroff","EDEKA Schroff"},{"edeka","EDEKA"},{"aldi","ALDI SÜD"},{"kaufland","Kaufland"},{"dm-drogerie","dm"}};
        for(String[] item:names)if(top.contains(item[0])){
            if(item[0].equals("lidl")){if(top.matches("(?s).*(mwst|deutschland|ust-id|danke|kauf).*"))return "Lidl Duitsland";if(top.matches("(?s).*(btw|nederland|bedankt).*"))return "Lidl Nederland";}
            return item[1];
        }return "";
    }
    static String normalized(String s){return s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    static String mergePages(List<String> pages){
        List<String> merged=new ArrayList<>();Set<String> complete=new HashSet<>();
        for(String page:pages){
            String canonical=normalized(page);if(canonical.isEmpty()||!complete.add(canonical))continue;
            List<String> next=new ArrayList<>();for(String l:page.split("\\r?\\n"))if(!l.trim().isEmpty())next.add(l.trim());
            int overlap=0;
            for(int n=Math.min(merged.size(),next.size());n>=2;n--){
                boolean same=true;for(int j=0;j<n;j++)if(!normalized(merged.get(merged.size()-n+j)).equals(normalized(next.get(j)))){same=false;break;}
                if(same){overlap=n;break;}
            }
            for(int i=overlap;i<next.size();i++)merged.add(next.get(i));
        }StringBuilder result=new StringBuilder();for(String line:merged){if(result.length()>0)result.append('\n');result.append(line);}return result.toString();
    }
}
