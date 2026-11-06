package pl.gabrys.autorynek;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import pl.gabrys.autorynek.mixin.HandledAccessor;
import java.util.*;
import java.util.regex.*;
public class AutoRynek implements ClientModInitializer {
 public static Config cfg=Config.load();
 public static boolean running=false;
 public static String status="Wylaczony";
 public static double spent=0;
 public static int checked=0,skipped=0;
 // Napis na srodku ekranu (snajpa-on / snajpa-off) przez cfg.hudSeconds.
 public static String hudText="";
 public static long hudUntil=0;
 public static void flash(String text) { hudText=text; hudUntil=System.currentTimeMillis()+(long)(cfg.hudSeconds*1000); }
 // Lista najlepszych (najtanszych) zakupow: "nazwa", cena za sztuke, sztuki, kiedy.
 public static final java.util.List<String> history=new java.util.ArrayList<>();
 private static boolean resetRequested=false;
 private boolean shift,z,c;
 private long next=0,waitSince=0;
 private String waiting=null;
 private int sortAttempts=0;
 private boolean sorted=false,forward=true,needReopen=false;
 private int pageClicks=0,quietClicks=0,sortBlind=0;
 private boolean startSorting=false,prevSortWasNewest=false;
 private int direction=-1;
 private String lastList="",sortListBefore="";
 private long stableSince=0;
 private double pendingPrice=0;
 private String pendingName="",pendingId="";
 public static void stop(String why) { running=false; status=why; if(!why.startsWith("Panel")) flash(cfg.hudOff); }
 public static void toggle() {
  if(running) { stop("Wylaczony"); return; }
  if(cfg.items.isEmpty()) { status="Najpierw dodaj przedmiot"; return; }
  resetRequested=true; running=true; status="Start"; flash(cfg.hudOn);
 }
 @Override public void onInitializeClient() {
 ClientTickEvents.END_CLIENT_TICK.register(this::tick);
 // Napis na srodku ekranu: snajpa-on / snajpa-off przez cfg.hudSeconds.
 HudRenderCallback.EVENT.register((ctx,tickDelta)->{
  if(hudText.isEmpty()||System.currentTimeMillis()>hudUntil) return;
  var tr=MinecraftClient.getInstance().textRenderer;
  int cx=(ctx.getScaledWindowWidth()-tr.getWidth(hudText))/2, cy=ctx.getScaledWindowHeight()/2;
  ctx.drawTextWithShadow(tr,hudText,cx,cy,0xFF55FF55);
 });
}
 private boolean key(MinecraftClient mc,int key) { return GLFW.glfwGetKey(mc.getWindow().getHandle(),key)==GLFW.GLFW_PRESS; }
 private void tick(MinecraftClient mc) {
  boolean s=key(mc,GLFW.GLFW_KEY_RIGHT_SHIFT), zz=key(mc,GLFW.GLFW_KEY_Z), cc=key(mc,GLFW.GLFW_KEY_C);
  if(s&&!shift&&mc.player!=null) { stop("Panel otwarty - automat zatrzymany"); mc.setScreen(new MarketScreen(null)); }
  if(zz&&!z&&mc.player!=null&&(mc.currentScreen==null||mc.currentScreen instanceof HandledScreen<?>)) {
   ItemStack stack=mc.player.getMainHandStack();
   if(mc.currentScreen instanceof HandledScreen<?> hs) { Slot slot=((HandledAccessor)hs).autorynek$focusedSlot(); stack=slot==null?ItemStack.EMPTY:slot.getStack(); }
   if(!stack.isEmpty()) { stop("Dodawanie przedmiotu"); mc.setScreen(new MarketScreen(stack.copy())); }
  }
  // C: wlacz/wylacz automat. Dziala takze bez otwartego rynku - mod sam wpisze /ah.
  if(cc&&!c&&mc.player!=null&&!(mc.currentScreen instanceof MarketScreen)) {
   waiting=null; sorted=false; sortAttempts=0; pageClicks=0; quietClicks=0; pendingPrice=0;
   if(running) { toggle(); } else { needReopen=true; toggle(); }
  }
  shift=s; z=zz; c=cc;
  if(!running) return;
  if(resetRequested) { waiting=null; sorted=false; sortAttempts=0; pageClicks=0; quietClicks=0; pendingPrice=0; needReopen=true; next=0; resetRequested=false; }
  if(mc.player==null||mc.world==null||mc.getCurrentServerEntry()==null) { stop("Brak polaczenia z serwerem"); return; }
  String address=mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT).split(":")[0];
  if(!(address.equals(cfg.server)||address.endsWith("."+cfg.server))) { stop("Inny serwer"); return; }
  long now=System.currentTimeMillis(); if(now<next) return;
  if(mc.currentScreen==null) {
   if(needReopen||pendingPrice>0) { mc.getNetworkHandler().sendChatCommand("ah"); next=now+cfg.reopenMs; needReopen=false; waiting=null; sorted=false; sortAttempts=0; pageClicks=0; quietClicks=0;
    // Konserwatywne rozliczenie limitu po wyslaniu zakupu, nie deklaracja sukcesu.
    pendingPrice=0;
   } else { stop("Rynek zamkniety poza zakupem"); }
   return;
  }
  if(!(mc.currentScreen instanceof HandledScreen<?> screen)) { stop("Nieznany ekran"); return; }
  boolean bigMarket=screen.getScreenHandler() instanceof GenericContainerScreenHandler g&&g.getRows()==6;
  // Okno zakupu tez nazywa sie "Rynek", dlatego rozrozniamy je po rozmiarze: 6 rzedow = lista ofert.
  if(!bigMarket) {
   if(pendingPrice<=0) { stop("Okno zakupu bez wyslanego zakupu"); return; }
   int slot=-1;
   for(Slot sl:screen.getScreenHandler().slots) {
    if(slot>=0) break;
    if(id(sl.getStack()).equals("minecraft:lime_dye")&&sl.getStack().getName().getString().toLowerCase(Locale.ROOT).contains("zakup")) slot=sl.id;
   }
   if(slot<0) { stop("Nie znaleziono zielonego potwierdzenia zakupu"); return; }
   Double shown=null;
   for(Slot sl:screen.getScreenHandler().slots) { Double p=price(sl.getStack()); if(p!=null) shown=p; }
   if(shown==null) { stop("Okno zakupu bez widocznej ceny - brak potwierdzenia"); return; }
   if(Double.compare(shown,pendingPrice)!=0) { stop("Cena w oknie zakupu rozni sie od oferty: "+shown+" vs "+pendingPrice); return; }
   status="Potwierdzam zakup za "+pendingPrice+" (slot "+slot+")";
   click(mc,screen,slot,now); pendingPrice=0; needReopen=true; return;
  }
  GenericContainerScreenHandler handler=(GenericContainerScreenHandler)screen.getScreenHandler();
  String fingerprint=fingerprint(screen);
  if(waiting!=null) {
   if(waiting.equals(fingerprint)) { if(now-waitSince>3000) stop("Brak aktualizacji rynku po kliknieciu"); return; }
   waiting=null; pendingPrice=0;
  }
  // Sortowanie: przycisk minecraft:repeater, przelaczany PRAWYM klikiem.
  ItemStack sort=handler.getSlot(cfg.sortSlot).getStack();
  if(!id(sort).equals(cfg.sortItem)) {
   stop("Slot "+cfg.sortSlot+" to nie sortowanie: "+id(sort)+" ("+sort.getName().getString()+")");
   return;
  }
  // Czytamy z opisu, ktora pozycja jest zaznaczona (serwer podswietla ja po wybraniu).
  String sel=selectedSort(sort);
  if(sel!=null) {
   sorted=sel.contains("najnowsze");
   startSorting=!sorted;
   if(!sorted) {
    // Klikamy prawym, az do skutku. Liczymy proby.
    sortAttempts++;
    if(sortAttempts>cfg.sortMaxClicks) {
     // Ostatnia deska ratunku: 3 kliknięcia od "Brak motywu" dają "Najnowsze".
     if(sortBlind<cfg.sortBlindClicks) { sortBlind++; status="Sortowanie: klik na slepo "+sortBlind+"/"+cfg.sortBlindClicks+" (opis: "+sel+")"; click(mc,screen,cfg.sortSlot,now,true); return; }
     stop("Sortowanie: nie udalo sie wybrac Najnowsze (opis: "+sel+")");
     return;
    }
    status="Sortowanie: "+sortAttempts+"/"+cfg.sortMaxClicks+" (teraz: "+sel+")";
    stableSince=now; lastList=fingerprint(screen);
    click(mc,screen,cfg.sortSlot,now,true); return;
   }
   if(!prevSortWasNewest) { status="Sortowanie: Najnowsze wybrane"; stableSince=now; lastList=fingerprint(screen); }
  } else if(!sorted) {
   // Opisu nie da sie odczytac - klikamy 3 razy prawym (Brak motywu -> ... -> Najnowsze).
   if(sortBlind>=cfg.sortBlindClicks) { stop("Sortowanie: brak opisu, "+cfg.sortBlindClicks+" klikow bez efektu"); return; }
   sortBlind++; status="Sortowanie: klik "+sortBlind+"/"+cfg.sortBlindClicks+" (bez opisu)";
   stableSince=now; lastList=fingerprint(screen);
   click(mc,screen,cfg.sortSlot,now,true); return;
  }
  prevSortWasNewest=sorted;
  // Krotka chwila na ustabilizowanie listy po zmianie sortowania.
  if(now-stableSince<cfg.stableMs) return;
  for(int i=0;i<45;i++) {
   ItemStack st=handler.getSlot(i).getStack(); if(st.isEmpty()) continue;
   Double amount=price(st); if(amount==null||amount<=0) { skipped++; continue; }
   checked++;
   // Cena za sztuke: liczba z ikony to liczba sztuk w ofercie.
   int units=st.getCount();
   Double unit=amount;
   if(cfg.priceMode.equals("unit")) {
    if(units<=0) { skipped++; continue; }
    unit=amount/units;
   }
   String loreText=loose(String.join(" ",lore(st).stream().map(Text::getString).toList()));
   for(Config.Rule r:cfg.items) if(id(st).equals(r.id)&&unit<=r.maxPrice
     &&(r.enchants.isBlank()||(!loreText.isEmpty()&&loreText.contains(loose(r.enchants))))) {
    if(spent+amount>cfg.maxSessionSpend) { stop("Osiagnieto limit wydatkow sesji"); return; }
    pendingPrice=amount; pendingName=st.getName().getString(); pendingId=id(st); spent+=amount;
    status="Wyslano zakup: "+pendingName+" / "+amount+(cfg.priceMode.equals("unit")?" ("+String.format(Locale.ROOT,"%.1f",unit)+"/szt, "+units+" szt)":"");
    addHistory(pendingName,unit,units,amount); needReopen=true;
    click(mc,screen,i,now); return;
   }
  }
  // Strony. Czerwony = "Poprzednia strona" (LEWY klik), zielony = "Nastepna strona".
  // Sprawdzamy NAZWE, zeby nie kliknac w barwnik od serca.
  if(!sorted) return;
  int page=forward?cfg.pageSlotNext:cfg.pageSlotPrev;
  ItemStack pageStack=handler.getSlot(page).getStack();
  String itemId=id(pageStack), pageName=pageStack.getName().getString().toLowerCase(Locale.ROOT);
  String wanted=forward?cfg.pageItemNext:cfg.pageItemPrev;
  if(!itemId.equals(wanted)) { stop("Slot "+page+" to nie przycisk strony: "+itemId); return; }
  if(!pageName.contains("następna")&&!pageName.contains("nastepna")&&!pageName.contains("poprzednia")) {
   stop("Slot "+page+" nie jest strona, tylko: "+pageStack.getName().getString()); return;
  }
  forward=!forward; click(mc,screen,page,now,cfg.pageRightClick);
 }
 private void click(MinecraftClient mc,HandledScreen<?> s,int slot,long now) { click(mc,s,slot,now,false); }
 // Prawy klik = przycisk 1 w clickSlot (sortowanie i strony rynku), lewy = 0 (zakup, potwierdzenie).
 private void click(MinecraftClient mc,HandledScreen<?> s,int slot,long now,boolean rightClick) {
  waiting=fingerprint(s); waitSince=now; next=now+cfg.delayMs;
  mc.interactionManager.clickSlot(s.getScreenHandler().syncId,slot,rightClick?1:0,SlotActionType.PICKUP,mc.player);
 }
 public static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).toString(); }
 public static List<Text> lore(ItemStack s) { var l=s.get(DataComponentTypes.LORE); return l==null?List.of():l.lines(); }
 public static Double price(ItemStack s) {
  Pattern p=Pattern.compile(cfg.priceRegex); Double result=null;
  for(Text line:lore(s)) { Matcher m=p.matcher(line.getString().replaceAll("§.",""));
   if(m.matches()) {
    try {
     double val=parseAmount(m.group(1),m.group(2));
     // Nawias z dokladna kwota jest sprawdzeniem spojnosci: sprzeczny = brak zakupu.
     if(m.group(3)!=null) { double exact=parseAmount(m.group(3),null); if(Double.compare(val,exact)!=0) return null; }
     if(!Double.isFinite(val)||val<=0||result!=null) return null;
     result=val;
    } catch(Exception e) { return null; }
   }
  }
  return result;
 }
 // "1.65"+"k" -> 1650, "1,150" -> 1150, "1 150" -> 1150.
 public static double parseAmount(String number,String suffix) {
  String n=number.trim().replace(" ","");
  double out;
  if(n.contains(",")) {
   if(n.contains(".")) { n=n.replace(",",""); out=Double.parseDouble(n); }
   else {
    String[] parts=n.split(",");
    out=(parts.length==2&&parts[1].length()==3)?Double.parseDouble(n.replace(",","")):Double.parseDouble(n.replace(',','.'));
   }
  } else if(n.chars().filter(ch->ch=='.').count()>1) { out=Double.parseDouble(n.replace(".","")); }
  else {
   int dot=n.indexOf('.');
   out=(dot>=0&&n.length()-dot-1==3)?Double.parseDouble(n.replace(".","")):Double.parseDouble(n);
  }
  if(suffix!=null&&suffix.equalsIgnoreCase("k")) out*=1000;
  return out;
 }
 // Zwraca nazwe zaznaczonej pozycji sortowania (np. "Najnowsze") albo null, gdy brak podswietlenia.
 public static String selectedSort(ItemStack s) {
  String[] names={"brak motywu","najwyzszej ceny","najwyższej ceny","najnizszej ceny","najniższej ceny","najnowsze","najstarsze"};
  for(Text t:lore(s)) {
   final String[] found={null};
   t.visit((style,part)->{
    if(style.getColor()!=null) {
     String low=part.toLowerCase(Locale.ROOT);
     for(String n:names) if(low.contains(n)) found[0]=n;
    }
    return Optional.empty();
   },net.minecraft.text.Style.EMPTY);
   if(found[0]!=null) return found[0];
  }
  // Serwer moze tez oznaczac pozycje znakiem strzalki.
  for(Text t:lore(s)) { String low=t.getString().toLowerCase(Locale.ROOT);
   if(low.contains("➤")||low.contains(">")) for(String n:names) if(low.contains(n)) return n; }
  return null;
 }
 private static boolean isNewest(ItemStack s) {
  for(Text t:lore(s)) {
   Optional<Boolean> found=t.visit((style,part)->{
    var color=style.getColor();
    if(part.toLowerCase(Locale.ROOT).contains("najnowsze")&&color!=null&&color.getRgb()==0xFFFF55) return Optional.of(true);
    return Optional.empty();
   },net.minecraft.text.Style.EMPTY);
   if(found.orElse(false)) return true;
  }
  return false;
 }
 private static String marketItems(HandledScreen<?> s) {
  StringBuilder b=new StringBuilder();
  for(int i=0;i<45;i++) { ItemStack st=s.getScreenHandler().getSlot(i).getStack(); b.append('|').append(id(st)).append(':').append(st.getName().getString()); }
  return b.toString();
 }
 // Lista najlepszych zakupow: najtansze na gorze, bez duplikatow tego samego przedmiotu.
 // Import profilu: "id;cena;wlaczone;enchant;procent;lore;nazwa;ogon" - jeden wiersz = jedna regula.
 // Folder z plikiem profilu: podmieniasz plik, klikasz "Z folderu".
 public static java.nio.file.Path profileDir() throws Exception {
  java.nio.file.Path d=FabricLoader.getInstance().getConfigDir().resolve("autorynek");
  java.nio.file.Files.createDirectories(d);
  return d;
 }
 public static final String PROFILE_FILE="profile.txt";
 // Wczytuje caly plik z folderu (bez limitu dlugosci pola tekstowego).
 public static int[] importProfileFile() {
  try {
   java.nio.file.Path f=profileDir().resolve(PROFILE_FILE);
   if(!java.nio.file.Files.exists(f)) { java.nio.file.Files.writeString(f,""); return new int[]{0,0}; }
   return importProfiles(java.nio.file.Files.readString(f));
  } catch(Exception e) { return new int[]{0,0}; }
 }
 public static int[] importProfiles(String text) {
  int added=0,skipped=0;
  for(String rawLine:text.replace("\r\n","\n").split("\n")) {
   String line=rawLine.trim(); if(line.isEmpty()) continue;
   String[] p=line.split(";",-1);
   if(p.length<8) { skipped++; continue; }
   String id=p[0].trim(), price=p[1].trim(), on=p[2].trim().toLowerCase(Locale.ROOT);
   String ench=p[3].trim();
   String name=p[6].trim();
   if(!id.startsWith("minecraft:")) { skipped++; continue; }
   if(!on.equals("true")&&!on.equals("1")) { skipped++; continue; }
   double max;
   try { max=Double.parseDouble(price.replace(',','.')); } catch(Exception e) { skipped++; continue; }
   if(!Double.isFinite(max)||max<=0) { skipped++; continue; }
   Config.Rule rule=new Config.Rule(id,name,max,ench);
   cfg.items.removeIf(r->r.id.equals(id)&&r.name.equals(name)&&loose(r.enchants).equals(loose(ench)));
   cfg.items.add(rule); added++;
  }
  return new int[]{added,skipped};
 }
 // "ochrona vi, regeneracji i," == "ochrona VI, regeneracji i" (bez wielkosci i przecinka na koncu).
 public static String loose(String s) {
  if(s==null) return "";
  String out=s.toLowerCase(Locale.ROOT).replace(","," ").replaceAll("\\s+"," ").trim();
  return out;
 }
 private static void addHistory(String name,double unit,int units,double total) {
  String line=String.format(Locale.ROOT,"%s x%d - %,.0f/szt (razem %,.0f)",name,units,unit,total);
  history.removeIf(h->h.startsWith(name+" x"));
  history.add(line);
  history.sort(java.util.Comparator.comparingDouble(AutoRynek::historyUnit));
  while(history.size()>Math.max(5,Math.min(100,cfg.historySize))) history.remove(history.size()-1);
 }
 private static double historyUnit(String line) {
  try { int a=line.lastIndexOf(" - "),b=line.indexOf("/szt"); return Double.parseDouble(line.substring(a+3,b).replace(",","").replace(" ","")); }
  catch(Exception e) { return Double.MAX_VALUE; }
 }
 private static boolean textContains(ItemStack s,String needle) {
  String n=needle.toLowerCase(Locale.ROOT);
  for(Text t:lore(s)) if(t.getString().toLowerCase(Locale.ROOT).contains(n)) return true;
  return s.getName().getString().toLowerCase(Locale.ROOT).contains(n);
 }
 private static String fingerprint(HandledScreen<?> s) {
  StringBuilder b=new StringBuilder(s.getTitle().getString());
  int size=s.getScreenHandler().slots.size()-36;
  for(int i=0;i<size;i++) { ItemStack st=s.getScreenHandler().getSlot(i).getStack(); b.append('|').append(id(st)).append(':').append(st.getCount()).append(':').append(st.getName().getString()); for(Text t:lore(st)) b.append(t.toString()); }
  return b.toString();
 }
}
