package pl.gabrys.autorynek;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
public class Config {
 public String server = "anarchia.gg";
 // Tytul rynku nie jest uzywany do rozpoznania okna (rozmiar okna wystarcza),
 // ale zostaje jako pole informacyjne dla starego CFG.
 public String marketTitle = "Rynek (";
 // Cena calej oferty. Brak rozpoznania = brak zakupu.
 // Format serwera: "» Cena produktu: $1.65k ($1,650)" oraz zwykle "$1,150", "$1150".
 public String priceRegex = "(?iu)^\\s*(?:»\\s*)?(?:cena(?: produktu)?|koszt)(?: za (?:calosc|całość|ofertę|oferte))?\\s*:\\s*\\$?\\s*([0-9][0-9., ]*)(k)?\\s*(?:\\(\\s*\\$\\s*([0-9][0-9.,]*)\\s*\\))?\\s*$";
 public boolean dark = true;
 public double scale = 1.0;
 public int delayMs = 150;
 public int stableMs = 700;
 // Sortowanie: "Najwyzszej ceny" to 2. klik od "Brak motywu", wiec po nim lista
 // zwykle sie jeszcze zmienia. Zeby nie zakonczyc na zlym sortowaniu, mod klika
 // dalej, dopoki lista sie zmienia (maks. pageWarmup razy), potem kupuje.
 public int pageWarmup = 2;
 // Ile kolejnych klikniec bez zmiany listy wybaczamy, zanim uznamy sortowanie
 // za ustabilizowane. Podniesc do 2, gdy mod kupuje mimo zaznaczonego Najnowsze.
 public int pageQuietClicks = 1;
 public String pageItemNext = "minecraft:lime_dye";
 public String pageItemPrev = "minecraft:red_dye";
 public int pageSlotNext = 50;
 public int pageSlotPrev = 48;
 // Limit ceny: "unit" = za sztuke (cena calej oferty / liczba z ikony), "offer" = za cala oferte.
 public String priceMode = "unit";
 // Serce: ile najtanszych zakupow trzymac na liscie w panelu.
 public int historySize = 20;
 // Serwer: "Poprzednia strona" (red_dye) dziala LEWYM klikiem.
 // Przelaczenie na prawy klik zmienia sie tutaj, bez przebudowy.
 public boolean pageRightClick = false;
 // Ustawienia interfejsu.
 public String theme = "dark";
 public String accent = "teal";
 public boolean showFooter = true;
 // Serwer: sortowanie to minecraft:repeater w slocie 52, przelaczane PRAWYM klikiem.
 public String sortItem = "minecraft:repeater";
 public int sortSlot = 52;
 public int sortClicks = 3;
 public int reopenMs = 1000;
 public double maxSessionSpend = 10000;
 // Pozostawione dla zgodnosci ze starym CFG; okno zakupu rozpoznajemy po rozmiarze okna.
 public String confirmationTitle = "";
 public int confirmationSlot = -1;
 public String license = ""; // Tylko lokalne pole, bez walidatora licencji.
 public List<Rule> items = new ArrayList<>();
 public static class Rule {
  public String id, name;
  public double maxPrice;
  public Rule(String id, String name, double price) { this.id=id; this.name=name; this.maxPrice=price; }
 }
 public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 public static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("auto-rynek-skibidi-gabrys.json");
 public void validate() {
  Pattern.compile(priceRegex);
  if(server==null || server.isBlank() || marketTitle==null || marketTitle.isBlank() || items==null || items.size()>500) throw new IllegalArgumentException("Bledny CFG");
  delayMs=Math.max(100,Math.min(5000,delayMs));
  reopenMs=Math.max(1000,Math.min(10000,reopenMs));
  scale=Math.max(.7,Math.min(1.2,scale));
  if(!Double.isFinite(maxSessionSpend)||maxSessionSpend<=0) throw new IllegalArgumentException("Nieprawidlowy budzet");
  for(Rule r:items) if(r==null||r.id==null||r.name==null||!Double.isFinite(r.maxPrice)||r.maxPrice<=0) throw new IllegalArgumentException("Nieprawidlowa regula");
  if(!"unit".equals(priceMode)&&!"offer".equals(priceMode)) priceMode="unit";
  historySize=Math.max(5,Math.min(100,historySize));
  if(theme==null||(!theme.equals("dark")&&!theme.equals("light"))) theme="dark";
 }
 public static Config load() {
  try { Config c=GSON.fromJson(Files.readString(FILE),Config.class); c.validate(); return c; }
  catch(Exception e) { return new Config(); }
 }
 public void save() throws Exception { validate(); Files.createDirectories(FILE.getParent()); Files.writeString(FILE,GSON.toJson(this)); }
}
