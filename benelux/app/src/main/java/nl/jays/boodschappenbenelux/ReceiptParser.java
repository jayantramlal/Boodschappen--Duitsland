package nl.jays.boodschappenbenelux;

import java.util.*;
import java.util.regex.*;

final class ReceiptParser {
    static final class Line {
        final String label; final int cents;
        Line(String label,int cents){this.label=label;this.cents=cents;}
    }
    static List<Line> parse(String raw){
        List<Line> out=new ArrayList<>();
        Pattern amount=Pattern.compile("(?:€\\s*)?(\\d{1,4}[,.]\\d{2})\\s*(?:[A-Z])?$");
        for(String source:raw.split("\\r?\\n")){
            String line=source.trim().replaceAll("\\s+"," ");
            if(line.isEmpty()||line.matches("(?i).*(totaal|subtotal|subtotaal|btw|korting|betaling|pin|cash|wisselgeld|te betalen|retour|spaarkaart|datum).*"))continue;
            Matcher m=amount.matcher(line);
            if(!m.find())continue;
            String label=line.substring(0,m.start()).replaceAll("[.*\\-: ]+$","").trim();
            if(label.length()<2||!label.matches(".*[\\p{L}].*"))continue;
            try{
                int cents=new java.math.BigDecimal(m.group(1).replace(',','.')).movePointRight(2).intValueExact();
                if(cents>0&&cents<1000000)out.add(new Line(label,cents));
            }catch(Exception ignored){}
        }
        return out;
    }
    static String shop(String raw){
        String top=raw.toLowerCase(Locale.ROOT).substring(0,Math.min(180,raw.length()));
        String[][] names={{"albert heijn","Albert Heijn"},{"jumbo","Jumbo"},{"dirk","Dirk"},{"lidl","Lidl"},{"plus","PLUS"},{"kruidvat","Kruidvat"},{"rewe","REWE"},{"schroff","EDEKA Schroff"},{"aldi","ALDI SÜD"},{"kaufland","Kaufland"},{"dm-drogerie","dm"}};
        for(String[] item:names)if(top.contains(item[0]))return item[1];
        return "";
    }
}