import net.dankito.readability4j.Readability4J;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class Compare {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]); String[] names={"twz","9to5google"};
  String[] urls={"https://www.twz.com/air/full-f-47-production-line-not-too-far-away-usaf-general","https://9to5google.com/2026/09/30/facer-wear-os-library-update-timex/"};
  StringBuilder report=new StringBuilder("fixture\trun\tparse_ms\tcharacters\n");
  for(int i=0;i<names.length;i++) {
   String html=Files.readString(root.resolve("android/app/src/main/assets/"+names[i]+".html"));
   for(int run=0;run<11;run++) {
    long start=System.nanoTime(); var article=new Readability4J(urls[i],html).parse(); double ms=(System.nanoTime()-start)/1e6;
    String text=article.getTextContent(); if(text==null || text.trim().length()<100) throw new IllegalStateException("Empty extraction");
    Files.writeString(root.resolve("results/"+names[i]+"-readability4j-host.txt"),text,StandardCharsets.UTF_8);
    report.append(names[i]+"\t"+run+"\t"+ms+"\t"+text.length()+"\n");
   }
  }
  Files.writeString(root.resolve("results/readability4j-host.tsv"),report.toString());
  System.out.println("Readability4J host baseline: 22 runs");
 }
}
