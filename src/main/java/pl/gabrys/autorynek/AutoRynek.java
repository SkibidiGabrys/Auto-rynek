package pl.gabrys.autorynek;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
 private static boolean resetRequested=false;
 private boolean shift,z,c;
 private long next=0,waitSince=0;
 private String waiting=null;
 private int sortAttempts=0;
 private boolean sorted=false,forward=true,needReopen=false;
 private double pendingPrice=0;
 private String pendingName="",pendingId="";
 public static void stop(String why) { running=false; status=why; }
 public static void toggle() {
  if(running) { stop("Wylaczony"); return; }
  if(cfg.items.isEmpty()) { status="Najpierw dodaj przedmiot"; return; }
  resetRequested=true; running=true; status="Start";
 }
 @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
 private boolean key(MinecraftClient mc,int key) { return GLFW.glfwGetKey(mc.getWindow().getHandle(),key)==GLFW.GLFW_PRESS; }
 private void tick(MinecraftClient mc) {
  boolean s=key(mc,GLFW.GLFW_KEY_RIGHT_SHIFT), zz=key(mc,GLFW.GLFW_KEY_Z), cc=key(mc,GLFW.GLFW_KEY_C);
  if(s&&!shift&&mc.player!=null) { stop("Panel otwarty - automat zatrzymany"); mc.setScreen(new MarketScreen(null)); }
  if(zz&&!z&&mc.player!=null&&(mc.currentScreen==null||mc.currentScreen instanceof HandledScreen<?>)) {
   ItemStack stack=mc.player.getMainHandStack();
   if(mc.currentScreen instanceof HandledScreen<?> hs) { Slot slot=((HandledAccessor)hs).autorynek$focusedSlot(); stack=slot==null?ItemStack.EMPTY:slot.getStack(); }
   if(!stack.isEmpty()) { stop("Dodawanie przedmiotu"); mc.setScreen(new MarketScreen(stack.copy())); }
  }
  if(cc&&!c&&(mc.currentScreen==null||mc.currentScreen instanceof HandledScreen<?>)) { waiting=null; sorted=false; sortAttempts=0; pendingPrice=0; needReopen=true; toggle(); }
  shift=s; z=zz; c=cc;
  if(!running) return;
  if(resetRequested) { waiting=null; sorted=false; sortAttempts=0; pendingPrice=0; needReopen=true; next=0; resetRequested=false; }
  if(mc.player==null||mc.world==null||mc.getCurrentServerEntry()==null) { stop("Brak polaczenia z serwerem"); return; }
  String address=mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT).split(":")[0];
  if(!(address.equals(cfg.server)||address.endsWith("."+cfg.server))) { stop("Inny serwer"); return; }
  long now=System.currentTimeMillis(); if(now<next) return;
  if(mc.currentScreen==null) {
   if(needReopen||pendingPrice>0) { mc.getNetworkHandler().sendChatCommand("ah"); next=now+cfg.reopenMs; needReopen=false; waiting=null; sorted=false; sortAttempts=0;
    // Konserwatywne rozliczenie limitu po wyslaniu zakupu, nie deklaracja sukcesu.
    pendingPrice=0;
   } else { stop("Rynek zamkniety poza zakupem"); }
   return;
  }
  if(!(mc.currentScreen instanceof HandledScreen<?> screen)) { stop("Nieznany ekran"); return; }
  String title=screen.getTitle().getString();
  if(!title.startsWith(cfg.marketTitle)) {
   if(pendingPrice>0&&!cfg.confirmationTitle.isBlank()&&title.equals(cfg.confirmationTitle)&&cfg.confirmationSlot>=0) {
    int slot=cfg.confirmationSlot;
    if(slot>=screen.getScreenHandler().slots.size()-36) { stop("Bledny slot potwierdzenia"); return; }
    // Przed potwierdzeniem wymagamy tej samej oferty i ceny na tym ekranie.
    boolean match=false;
    for(Slot sl:screen.getScreenHandler().slots) {
     ItemStack st=sl.getStack(); Double p=price(st);
     if(id(st).equals(pendingId)&&st.getName().getString().equals(pendingName)&&p!=null&&Double.compare(p,pendingPrice)==0) match=true;
    }
    if(!match) { stop("Nie potwierdzono ceny/przedmiotu w oknie zakupu"); return; }
    click(mc,screen,slot,now); pendingPrice=0; needReopen=true; return;
   }
   stop("Nieznane okno zakupu: "+title+". Wymagana kalibracja CFG."); return;
  }
  if(!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)||handler.getRows()!=6) { stop("Nieznany uklad rynku"); return; }
  String fingerprint=fingerprint(screen);
  if(waiting!=null) {
   if(waiting.equals(fingerprint)) { if(now-waitSince>3000) stop("Brak aktualizacji rynku po kliknieciu"); return; }
   waiting=null; pendingPrice=0;
  }
  ItemStack sort=handler.getSlot(52).getStack();
  if(isNewest(sort)) { sorted=true; sortAttempts=0; }
  if(!sorted) {
   if(sortAttempts>=5 || !sort.getName().getString().toLowerCase(Locale.ROOT).contains("sort")) { stop("Nie rozpoznano sortowania Najnowsze"); return; }
   sortAttempts++; click(mc,screen,52,now); return;
  }
  for(int i=0;i<45;i++) {
   ItemStack st=handler.getSlot(i).getStack(); if(st.isEmpty()) continue;
   Double amount=price(st); if(amount==null||amount<=0) continue;
   for(Config.Rule r:cfg.items) if(id(st).equals(r.id)&&st.getName().getString().equals(r.name)&&amount<=r.maxPrice) {
    if(spent+amount>cfg.maxSessionSpend) { stop("Osiagnieto limit wydatkow sesji"); return; }
    pendingPrice=amount; pendingName=st.getName().getString(); pendingId=id(st); spent+=amount;
    status="Wyslano zakup: "+pendingName+" / "+amount; needReopen=true;
    click(mc,screen,i,now); return;
   }
  }
  int page=forward?50:48;
  String itemId=id(handler.getSlot(page).getStack());
  if(!itemId.equals(forward?"minecraft:lime_dye":"minecraft:red_dye")&&!itemId.equals(forward?"minecraft:green_dye":"minecraft:red_dye")) { stop("Nie rozpoznano przycisku strony"); return; }
  forward=!forward; sorted=false; click(mc,screen,page,now);
 }
 private void click(MinecraftClient mc,HandledScreen<?> s,int slot,long now) {
  waiting=fingerprint(s); waitSince=now; next=now+cfg.delayMs;
  mc.interactionManager.clickSlot(s.getScreenHandler().syncId,slot,0,SlotActionType.PICKUP,mc.player);
 }
 public static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).toString(); }
 public static List<Text> lore(ItemStack s) { var l=s.get(DataComponentTypes.LORE); return l==null?List.of():l.lines(); }
 public static Double price(ItemStack s) {
  Pattern p=Pattern.compile(cfg.priceRegex); Double result=null;
  for(Text line:lore(s)) { Matcher m=p.matcher(line.getString().replaceAll("§.",""));
   if(m.matches()) { try { double val=Double.parseDouble(m.group(1).replace(',','.')); if(!Double.isFinite(val)||val<=0||result!=null) return null; result=val; } catch(Exception e) { return null; } }
  }
  return result;
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
 private static String fingerprint(HandledScreen<?> s) {
  StringBuilder b=new StringBuilder(s.getTitle().getString());
  int size=s.getScreenHandler().slots.size()-36;
  for(int i=0;i<size;i++) { ItemStack st=s.getScreenHandler().getSlot(i).getStack(); b.append('|').append(id(st)).append(':').append(st.getCount()).append(':').append(st.getName().getString()); for(Text t:lore(st)) b.append(t.toString()); }
  return b.toString();
 }
}
