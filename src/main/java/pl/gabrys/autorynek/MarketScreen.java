package pl.gabrys.autorynek;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.Locale;
public class MarketScreen extends Screen {
 private int tab=1, page=0, x,y,w,h;
 private ItemStack adding;
 private TextFieldWidget cfgText,license,profileText;
 private String message="";
 private final java.util.List<String> d0=new java.util.ArrayList<>();
 public MarketScreen(ItemStack adding) { super(Text.literal("Auto Rynek Skibidi Gabrys")); this.adding=adding; }
 private ButtonWidget button(String text,int bx,int by,int bw,Runnable action) {
  return addDrawableChild(ButtonWidget.builder(Text.literal(text),b->action.run()).dimensions(bx,by,bw,20).build());
 }
 private TextFieldWidget field(int fx,int fy,int fw,String value) {
  var f=new TextFieldWidget(textRenderer,fx,fy,fw,20,Text.literal("Pole")); f.setMaxLength(32000); f.setText(value); addDrawableChild(f); return f;
 }
 @Override protected void init() {
  d0.clear();
  w=Math.min(width-24,(int)(720*AutoRynek.cfg.scale)); h=Math.min(height-24,350); x=(width-w)/2; y=(height-h)/2;
  int tw=(w-40)/4;
  button("Rynek",x+16,y+32,tw,()->{tab=1;adding=null;clearAndInit();});
  button("Wyglad",x+20+tw,y+32,tw,()->{tab=0;adding=null;clearAndInit();});
  button("Zakupy",x+24+2*tw,y+32,tw,()->{tab=3;adding=null;clearAndInit();});
  button("O modzie",x+28+3*tw,y+32,tw,()->{tab=2;adding=null;clearAndInit();});
  if(adding!=null) { addForm(); }
  else if(tab==0) {
   button("Bialy",x+20,y+85,110,()->{AutoRynek.cfg.dark=false;save();});
   button("Czarny",x+140,y+85,110,()->{AutoRynek.cfg.dark=true;save();});
   addDrawableChild(new SliderWidget(x+20,y+130,Math.min(320,w-40),20,Text.literal("Skala"),(AutoRynek.cfg.scale-.7)/.5) {
    protected void updateMessage() { setMessage(Text.literal("Skala panelu: "+Math.round((.7+value*.5)*100)+"%")); }
    protected void applyValue() { AutoRynek.cfg.scale=.7+value*.5; }
   });
   button("Zastosuj skale",x+20,y+160,160,()->{save();clearAndInit();});
   button(AutoRynek.cfg.accent.equals("teal")?"Akcent: morski":(AutoRynek.cfg.accent.equals("purple")?"Akcent: fiolet":"Akcent: bursztyn"),x+200,y+160,170,()->{
    AutoRynek.cfg.accent=AutoRynek.cfg.accent.equals("teal")?"purple":(AutoRynek.cfg.accent.equals("purple")?"amber":"teal"); save(); clearAndInit(); });
   if(AutoRynek.cfg.theme.equals("light")) button("Motyw: jasny",x+200,y+85,110,()->{AutoRynek.cfg.theme="dark";save();clearAndInit();});
   else button("Motyw: ciemny",x+200,y+85,110,()->{AutoRynek.cfg.theme="light";save();clearAndInit();});
   button(AutoRynek.cfg.showFooter?"Stopka: wlaczona":"Stopka: wylaczona",x+20,y+184,170,()->{AutoRynek.cfg.showFooter=!AutoRynek.cfg.showFooter;save();clearAndInit();});
  } else if(tab==1) {
   int rows=Math.max(1,(h-170)/26),start=page*rows;
   for(int j=0;j<rows&&start+j<AutoRynek.cfg.items.size();j++) {
    var rule=AutoRynek.cfg.items.get(start+j); int rowY=y+70+j*26;
    var name=field(x+16,rowY,Math.max(70,w-240),rule.name); name.setEditable(false);
    var price=field(x+w-216,rowY,90,Double.toString(rule.maxPrice));
    button("Zapisz",x+w-122,rowY,64,()->{
     try { double val=Double.parseDouble(price.getText().replace(',','.')); if(!Double.isFinite(val)||val<=0) throw new Exception(); rule.maxPrice=val; save(); }
     catch(Exception e) { message="Wpisz dodatnia cene"; }
    });
    button("X",x+w-54,rowY,36,()->{AutoRynek.cfg.items.remove(rule);save();clearAndInit();});
   }
   button("<",x+16,y+h-91,28,()->{page=Math.max(0,page-1);clearAndInit();});
   button(">",x+48,y+h-91,28,()->{if((page+1)*rows<AutoRynek.cfg.items.size())page++;clearAndInit();});
   button(AutoRynek.running?"Stop":"Start",x+84,y+h-91,100,()->{close();AutoRynek.toggle();});
   cfgText=field(x+16,y+h-64,Math.max(80,w-208),"");
   button("Wczytaj CFG",x+w-186,y+h-64,100,()->{
    try { Config c=Config.GSON.fromJson(cfgText.getText(),Config.class); c.validate(); AutoRynek.stop("Wczytano CFG"); AutoRynek.cfg=c; save();clearAndInit(); }
    catch(Exception e) { message="Bledny kod CFG"; }
   });
   button("Kopiuj",x+w-82,y+h-64,66,()->{client.keyboard.setClipboard(Config.GSON.toJson(AutoRynek.cfg));message="CFG w schowku";});
   // Import profilu: "id;cena;wlaczone;enchant;procent;lore;nazwa;ogon" - po jednej linii na regule.
   profileText=field(x+16,y+h-37,Math.max(80,w-208),"");
   profileText.setPlaceholder(Text.literal("wklej linie profilu (Ctrl+V)"));
   button("Wgraj profil",x+w-186,y+h-37,100,()->{
    int[] r=AutoRynek.importProfiles(profileText.getText());
    try { AutoRynek.cfg.save(); } catch(Exception e) {}
    message="Profil: dodano "+r[0]+", pominieto "+r[1];
    clearAndInit();
   });
   button("Wklej+Wgraj",x+w-82,y+h-37,66,()->{
    int[] r=AutoRynek.importProfiles(client.keyboard.getClipboard());
    try { AutoRynek.cfg.save(); } catch(Exception e) {}
    message="Ze schowka: dodano "+r[0]+", pominieto "+r[1];
    clearAndInit();
   });
  } else if(tab==3) {
   // Lista najlepszych (najtanszych) zakupow + przelacznik trybu ceny.
   button(AutoRynek.cfg.priceMode.equals("unit")?"Tryb: za sztuke":"Tryb: za cala oferte",x+16,y+h-91,170,()->{
    AutoRynek.cfg.priceMode=AutoRynek.cfg.priceMode.equals("unit")?"offer":"unit"; save(); clearAndInit(); });
   button("Wyczysc liste",x+194,y+h-91,130,()->{AutoRynek.history.clear();AutoRynek.spent=0;AutoRynek.checked=0;AutoRynek.skipped=0;save();clearAndInit();});
   int rowsH=Math.max(1,(h-190)/14);
   for(int j=0;j<rowsH&&j<AutoRynek.history.size();j++) {
    d0.add(AutoRynek.history.get(j));
   }
  } else {
   license=field(x+20,y+100,Math.max(100,w-40),AutoRynek.cfg.license);
   button("Zapisz lokalnie",x+20,y+132,150,()->{AutoRynek.cfg.license=license.getText();save();});
  }
  button("Zamknij",x+w-96,y+h-29,80,()->close());
 }
 private void addForm() {
  var name=field(x+20,y+88,w-40,adding.getName().getString()); name.setEditable(false);
  var amount=field(x+20,y+135,140,"0");
  button("Dodaj przedmiot",x+172,y+135,150,()->{
   try { double price=Double.parseDouble(amount.getText().replace(',','.')); if(!Double.isFinite(price)||price<=0)throw new Exception();
    String id=AutoRynek.id(adding),label=adding.getName().getString();
    AutoRynek.cfg.items.removeIf(r->r.id.equals(id)&&r.name.equals(label));
    AutoRynek.cfg.items.add(new Config.Rule(id,label,price));save();adding=null;clearAndInit();
   } catch(Exception e) { message="Wpisz dodatnia cene calej oferty"; }
  });
  button("Kopiuj opis do kalibracji",x+20,y+178,220,()->{
   StringBuilder s=new StringBuilder(AutoRynek.id(adding)+"\n"+adding.getName().getString()+"\n");
   for(Text t:AutoRynek.lore(adding))s.append(t.getString()).append('\n'); client.keyboard.setClipboard(s.toString());message="Opis w schowku";
  });
 }
 private void save() { try { AutoRynek.cfg.save();message="Zapisano"; }catch(Exception e) {message="Blad zapisu: "+e.getMessage();} }
 @Override public void render(DrawContext d,int mx,int my,float dt) {
  d.fill(0,0,width,height,0x99000000);
  boolean dark=!"light".equals(AutoRynek.cfg.theme)&&AutoRynek.cfg.dark;
  int bg=dark?0xFF20232D:0xFFF1F4FA, fg=dark?0xFFF2F5FF:0xFF252936;
  int accent="purple".equals(AutoRynek.cfg.accent)?0xFF9B7BFF:("amber".equals(AutoRynek.cfg.accent)?0xFFFFB84D:0xFF53CFC4);
  d.fill(x-1,y-1,x+w+1,y+h+1,accent);d.fill(x,y,x+w,y+h,bg);
  // Naglowek: pasek w kolorze akcentu i wyrazny tytul.
  d.fill(x,y,x+w,y+26,accent);
  d.fill(x+10,y+64,x+w-10,y+h-100,dark?0xFF171A22:0xFFE3E8F2);
  d.enableScissor(x,y+26,x+w,y+h);
  d.drawTextWithShadow(textRenderer,title,x+14,y+9,0xFF10131A);
  super.render(d,mx,my,dt);
  d.disableScissor();
  if(adding!=null) { d.drawTextWithShadow(textRenderer,"Nazwa przedmiotu",x+20,y+73,fg); d.drawTextWithShadow(textRenderer,"Maks. cena CALEJ oferty",x+20,y+120,fg); }
  else if(tab==0) {d.drawTextWithShadow(textRenderer,"Motyw i rozmiar panelu",x+20,y+68,fg);}
  else if(tab==2) {d.drawTextWithShadow(textRenderer,"Licencja (pole lokalne, bez aktywacji)",x+20,y+80,fg);}
  else if(tab==3) {
   d.drawTextWithShadow(textRenderer,"Najlepsze zakupy (najtansze za sztuke u gory)",x+16,y+66,fg);
   d.drawTextWithShadow(textRenderer,"Sprawdzone oferty: "+AutoRynek.checked+"   pominięte: "+AutoRynek.skipped+"   wydane: "+String.format(Locale.ROOT,"%,.0f",AutoRynek.spent),x+16,y+82,fg);
   if(d0.isEmpty()) d.drawTextWithShadow(textRenderer,"Brak zakupow - lista wypelni sie przy skupie",x+16,y+104,fg);
   for(int j=0;j<d0.size();j++) d.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(d0.get(j),w-40),x+16,y+104+j*14,fg);
  }
  if(AutoRynek.cfg.showFooter) d.drawTextWithShadow(textRenderer,"Z dodaj przedmiot | C start/stop | Prawy Shift: panel",x+12,y+h-20,fg);
  String line=message.isEmpty()?AutoRynek.status:message;
  d.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(line,w-30),x+12,y+h-40,0xFFFFB84D);
 }
 @Override public boolean shouldPause() {return false;}
 @Override public void close() {save();super.close();}
}
