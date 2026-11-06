package pl.gabrys.autorynek;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Uklad liczony od rozmiaru okna: nic nie nachodzi na siebie przy zadnej skali.
public class MarketScreen extends Screen {
 private int tab=1, page=0, x,y,w,h, pad, rowH;
 private ItemStack adding;
 private TextFieldWidget cfgText,license,profileText;
 private String message="";
 private final List<String> rowsCache=new ArrayList<>();
 private static final String[] TABS={"Rynek","Wyglad","Czas","Zakupy","O modzie"};

 public MarketScreen(ItemStack adding) { super(Text.literal("Auto Rynek Skibidi Gabrys 2")); this.adding=adding; }

 private ButtonWidget button(String text,int bx,int by,int bw,Runnable action) {
  return addDrawableChild(ButtonWidget.builder(Text.literal(text),b->action.run()).dimensions(bx,by,bw,20).build());
 }
 private TextFieldWidget field(int fx,int fy,int fw,String value) {
  var f=new TextFieldWidget(textRenderer,fx,fy,fw,20,Text.literal("Pole")); f.setMaxLength(1000000); f.setText(value); addDrawableChild(f); return f;
 }
 private boolean light() { return "light".equals(AutoRynek.cfg.theme)||!AutoRynek.cfg.dark; }

 @Override protected void init() {
  rowsCache.clear();
  double s=AutoRynek.cfg.scale;
  w=Math.min((int)(width*0.92), (int)(860*s));
  w=Math.max(360,Math.min(width-16,w));
  h=Math.min((int)(height*0.90), (int)(420*s));
  h=Math.max(220,Math.min(height-16,h));
  x=(width-w)/2; y=(height-h)/2; pad=12; rowH=22;

  // Zakladki: siatka, ktora sama sie dzieli na dostepna szerokosc.
  int n=TABS.length, gap=6;
  int tw=(w-2*pad-(n-1)*gap)/n;
  for(int i=0;i<n;i++) { final int t=i; button(TABS[i], x+pad+i*(tw+gap), y+30, tw, ()->{tab=t;adding=null;clearAndInit();}); }

  if(adding!=null) { addForm(); }
  else if(tab==1) { addMarket(); }
  else if(tab==2) { addTime(); }
  else if(tab==3) { addBuys(); }
  else if(tab==4) { addAbout(); }
  else { addLook(); }

  int cw=90;
  button("Zamknij", x+w-pad-cw, y+h-pad-20, cw, ()->close());
 }

 // ---------- RYNEK: lista ze slotami 1,2,3... ----------
 private void addMarket() {
  int top=y+62, bottom=y+h-pad-56;
  int rows=Math.max(1,(bottom-top)/rowH), start=page*rows;
  var items=AutoRynek.cfg.items;
  for(int j=0;j<rows&&start+j<items.size();j++) {
   var rule=items.get(start+j); int rowY=top+j*rowH;
   int numW=34, nameW=Math.max(60,w-2*pad-numW-150);
   rowsCache.add((start+j+1)+".");
   var name=field(x+pad+numW,rowY,nameW,rule.name); name.setEditable(false);
   var price=field(x+pad+numW+nameW+6,rowY,84,trim(rule.maxPrice));
   button("Zapisz", x+w-pad-58,rowY,58,()->{
    try { double v=Double.parseDouble(price.getText().replace(',','.')); if(!Double.isFinite(v)||v<=0) throw new Exception(); rule.maxPrice=v; save(); message="Zapisano cene"; }
    catch(Exception e) { message="Wpisz dodatnia cene"; }
   });
   button("X", x+w-pad-58-40,rowY,34,()->{AutoRynek.cfg.items.remove(rule);save();clearAndInit();});
  }
  if(items.isEmpty()) message="Lista pusta - wgraj profil albo dodaj przedmiot klawiszem Z";

  int y1=y+h-pad-48;
  button("<", x+pad,y1,30,()->{page=Math.max(0,page-1);clearAndInit();});
  button(">", x+pad+34,y1,30,()->{if((page+1)*rows<items.size())page++;clearAndInit();});
  button(AutoRynek.running?"Stop":"Start", x+pad+72,y1,96,()->{close();AutoRynek.toggle();});

  int y2=y+h-pad-22;
  profileText=field(x+pad,y2,Math.max(80,w-2*pad-230),"");
  profileText.setPlaceholder(Text.literal("wklej linie profilu: id;cena;wlaczone;enchant;..."));
  button("Wgraj profil", x+w-pad-224,y2,100,()->{
   int[] r=AutoRynek.importProfiles(profileText.getText());
   save(); message="Profil: dodano "+r[0]+", pominieto "+r[1]; clearAndInit();
  });
  button("Ze schowka", x+w-pad-120,y2,108,()->{
   int[] r=AutoRynek.importProfiles(client.keyboard.getClipboard());
   save(); message="Ze schowka: dodano "+r[0]+", pominieto "+r[1]; clearAndInit();
  });
  // Folder na plik profilu: podmieniasz plik, klikasz "Z folderu".
  button("Folder CFG", x+pad,y1-26,110,()->{
   try { java.nio.file.Path dir=AutoRynek.profileDir(); Util.getOperatingSystem().open(dir.toFile()); message="Folder: "+dir; }
   catch(Exception e) { message="Nie udalo sie otworzyc folderu"; }
  });
  button("Z folderu", x+pad+114,y1-26,110,()->{
   int[] r=AutoRynek.importProfileFile();
   save(); message="Plik: dodano "+r[0]+", pominieto "+r[1]; clearAndInit();
  });
 }

 // ---------- CZAS: tempo klikania ----------
 private void addTime() {
  int top=y+62;
  addDrawableChild(new SliderWidget(x+pad,top,Math.max(120,w-2*pad),20,Text.literal("Czas"),(AutoRynek.cfg.delayMs-AutoRynek.cfg.minDelayMs)/Math.max(1.0,(AutoRynek.cfg.maxDelayMs-AutoRynek.cfg.minDelayMs))) {
   protected void updateMessage() { setMessage(Text.literal("Opoznienie: "+AutoRynek.cfg.delayMs+" ms")); }
   protected void applyValue() { AutoRynek.cfg.delayMs=(int)(AutoRynek.cfg.minDelayMs+value*(AutoRynek.cfg.maxDelayMs-AutoRynek.cfg.minDelayMs)); }
  });
  button("Najszybciej ("+AutoRynek.cfg.minDelayMs+" ms)", x+pad,top+30,180,()->{AutoRynek.cfg.fastMode=true;AutoRynek.cfg.delayMs=AutoRynek.cfg.minDelayMs;save();clearAndInit();});
  button(AutoRynek.cfg.fastMode?"Auto-szybki: wlaczony":"Auto-szybki: wylaczony", x+pad+188,top+30,200,()->{AutoRynek.cfg.fastMode=!AutoRynek.cfg.fastMode;save();clearAndInit();});
  button("+50 ms", x+pad,top+58,90,()->{AutoRynek.cfg.delayMs=Math.min(AutoRynek.cfg.maxDelayMs,AutoRynek.cfg.delayMs+50);save();clearAndInit();});
  button("-50 ms", x+pad+98,top+58,90,()->{AutoRynek.cfg.delayMs=Math.max(AutoRynek.cfg.minDelayMs,AutoRynek.cfg.delayMs-50);save();clearAndInit();});
  message="Klient i tak wykonuje akcje raz na tick (20/s), wiec 50 ms to praktyczna granica.";
 }

 // ---------- ZAKUPY ----------
 private void addBuys() {
  int top=y+62;
  button(AutoRynek.cfg.priceMode.equals("unit")?"Tryb ceny: za sztuke":"Tryb ceny: za cala oferte", x+pad,top,200,()->{
   AutoRynek.cfg.priceMode=AutoRynek.cfg.priceMode.equals("unit")?"offer":"unit"; save(); clearAndInit(); });
  button("Wyczysc liste zakupow", x+pad+208,top,200,()->{AutoRynek.history.clear();AutoRynek.spent=0;AutoRynek.checked=0;AutoRynek.skipped=0;save();clearAndInit();});
 }

 // ---------- WYGLAD ----------
 private void addLook() {
  int top=y+62;
  button("Bialy", x+pad,top,120,()->{AutoRynek.cfg.dark=false;AutoRynek.cfg.theme="light";save();clearAndInit();});
  button("Czarny", x+pad+128,top,120,()->{AutoRynek.cfg.dark=true;AutoRynek.cfg.theme="dark";save();clearAndInit();});
  button("Motyw: "+(light()?"jasny":"ciemny"), x+pad+256,top,140,()->{AutoRynek.cfg.theme=light()?"dark":"light";AutoRynek.cfg.dark=!light();save();clearAndInit();});
  addDrawableChild(new SliderWidget(x+pad,top+30,Math.max(140,w-2*pad),20,Text.literal("Skala"),(AutoRynek.cfg.scale-0.5)/1.1) {
   protected void updateMessage() { setMessage(Text.literal("Skala panelu: "+Math.round((0.5+value*1.1)*100)+"%")); }
   protected void applyValue() { AutoRynek.cfg.scale=0.5+value*1.1; }
  });
  button("Zastosuj skale", x+pad,top+60,160,()->{save();clearAndInit();});
  button("Akcent: "+AutoRynek.cfg.accent, x+pad+168,top+60,160,()->{
   AutoRynek.cfg.accent=AutoRynek.cfg.accent.equals("teal")?"purple":(AutoRynek.cfg.accent.equals("purple")?"amber":"teal"); save(); clearAndInit(); });
  button(AutoRynek.cfg.showFooter?"Stopka: wlaczona":"Stopka: wylaczona", x+pad,top+88,180,()->{AutoRynek.cfg.showFooter=!AutoRynek.cfg.showFooter;save();clearAndInit();});
 }

 // ---------- O MODZIE ----------
 private void addAbout() {
  int top=y+62;
  license=field(x+pad,top,Math.max(100,w-2*pad),"");
  license.setText(AutoRynek.cfg.license);
  button("Zapisz lokalnie", x+pad,top+30,160,()->{AutoRynek.cfg.license=license.getText();save();clearAndInit();});
 }

 // ---------- DODAWANIE PRZEDMIOTU (klawisz Z) ----------
 private void addForm() {
  int top=y+62;
  var name=field(x+pad,top+(int)(rowH*0.6),Math.max(80,w-2*pad),adding.getName().getString()); name.setEditable(false);
  var amount=field(x+pad,top+rowH*2,120,"0");
  button("Dodaj przedmiot", x+pad+128,top+rowH*2,150,()->{
   try { double v=Double.parseDouble(amount.getText().replace(',','.')); if(!Double.isFinite(v)||v<=0) throw new Exception();
    String id=AutoRynek.id(adding), label=adding.getName().getString();
    AutoRynek.cfg.items.removeIf(r->r.id.equals(id)&&r.name.equals(label));
    AutoRynek.cfg.items.add(new Config.Rule(id,label,v)); save(); adding=null; clearAndInit();
   } catch(Exception e) { message="Wpisz dodatnia cene"; }
  });
  button("Kopiuj opis", x+pad+286,top+rowH*2,140,()->{
   StringBuilder sb=new StringBuilder(AutoRynek.id(adding)+"\n"+adding.getName().getString()+"\n");
   for(Text t:AutoRynek.lore(adding)) sb.append(t.getString()).append('\n');
   client.keyboard.setClipboard(sb.toString()); message="Opis w schowku";
  });
 }

 private static String trim(double v) { return v==Math.floor(v)?String.valueOf((long)v):String.format(Locale.ROOT,"%.2f",v); }
 private void save() { try { AutoRynek.cfg.save(); } catch(Exception e) { message="Blad zapisu"; } }

 @Override public void render(DrawContext d,int mx,int my,float dt) {
  boolean li=light();
  int bg=li?0xFFF2F4F8:0xFF20232D, fg=li?0xFF252936:0xFFF2F5FF;
  int accent="purple".equals(AutoRynek.cfg.accent)?0xFF9B7BFF:("amber".equals(AutoRynek.cfg.accent)?0xFFFFB84D:0xFF53CFC4);
  d.fill(0,0,width,height,li?0x66000000:0x99000000);
  d.fill(x-1,y-1,x+w+1,y+h+1,accent);
  d.fill(x,y,x+w,y+h,bg);
  d.fill(x,y,x+w,y+26,accent);
  d.drawTextWithShadow(textRenderer,title,x+pad,y+9,0xFF10131A);

  d.fill(x+pad-2,y+56,x+w-pad+2,y+h-pad-54,li?0xFFE7EBF3:0xFF171A22);

  if(adding!=null) {
   d.drawTextWithShadow(textRenderer,"Nowy przedmiot",x+pad,y+56+6,fg);
  } else if(tab==1) {
   for(int j=0;j<rowsCache.size();j++) d.drawTextWithShadow(textRenderer,rowsCache.get(j),x+pad+4,y+62+j*rowH+6,fg);
  } else if(tab==3) {
   d.drawTextWithShadow(textRenderer,"Najtansze za sztuke u gory",x+pad,y+90,fg);
   d.drawTextWithShadow(textRenderer,"Sprawdzone: "+AutoRynek.checked+"   pominięte: "+AutoRynek.skipped+"   wydane: "+String.format(Locale.ROOT,"%,.0f",AutoRynek.spent),x+pad,y+104,fg);
   var hist=AutoRynek.history;
   if(hist.isEmpty()) d.drawTextWithShadow(textRenderer,"Brak zakupow - lista wypelni sie przy skupie",x+pad,y+124,fg);
   for(int j=0;j<hist.size()&&j<14;j++) d.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(hist.get(j),w-2*pad-8),x+pad,y+124+j*14,fg);
  } else if(tab==4) {
   d.drawTextWithShadow(textRenderer,"Licencja (pole lokalne, bez aktywacji)",x+pad,y+56+6,fg);
  }

  super.render(d,mx,my,dt);
  if(AutoRynek.cfg.showFooter) d.drawTextWithShadow(textRenderer,"Z dodaj przedmiot | C start/stop | Prawy Shift: panel",x+pad,y+h-pad-14,fg);
  String line=message.isEmpty()?AutoRynek.status:message;
  d.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(line,w-2*pad-100),x+pad,y+h-pad-14-(AutoRynek.cfg.showFooter?10:0),0xFFFFB84D);
 }
 @Override public boolean shouldPause() { return false; }
 @Override public void close() { save(); super.close(); }
}
