package pl.gabrys.autorynek;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
public class Config {
 public String server = "anarchia.gg";
 public String marketTitle = "Rynek (";
 // Cena calej oferty. Brak rozpoznania = brak zakupu.
 public String priceRegex = "(?iu)^\\s*(?:»\\s*)?Cena(?: za (?:calosc|całość|ofertę|oferte))?\\s*:\\s*([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:\\$|zł|zl)?\\s*$";
 public boolean dark = true;
 public double scale = 1.0;
 public int delayMs = 150;
 public int reopenMs = 1000;
 public double maxSessionSpend = 10000;
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
 }
 public static Config load() {
  try { Config c=GSON.fromJson(Files.readString(FILE),Config.class); c.validate(); return c; }
  catch(Exception e) { return new Config(); }
 }
 public void save() throws Exception { validate(); Files.createDirectories(FILE.getParent()); Files.writeString(FILE,GSON.toJson(this)); }
}
