package edu.umass.cs.jfoley.coop.entityco;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.compress.LZFCoder;
import edu.umass.cs.ciir.waltz.coders.map.impl.WaltzDiskMapReader;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.IOException;

/**
 * @author jfoley
 */
public class ImportDbpediaCategories {

  public static final String subjectOf = "<http://purl.org/dc/terms/subject>";
  public static final String catPrefix = "/Category:";

  public static void main(String[] args) throws IOException {
    // categories stored as strings:
    Directory dbpediaDir = Directory.Read("dbpedia.ints");

    Debouncer msg = new Debouncer();
    TObjectIntHashMap<String> catCounts = new TObjectIntHashMap<>();
    /*try (
        IOMapWriter<String, String> catWriter = new SortingIOMapWriter<>(new WaltzDiskMapWriter<>(dbpediaDir, "category_by_article", CharsetCoders.utf8, new LZFCoder<>(CharsetCoders.utf8)));
        LinesIterable lines = LinesIterable.fromFile("/mnt/scratch/jfoley/article-categories_en.nt.gz")) {

      String lastPageId = null;
      List<String> categories = new ArrayList<>();

      for (String line : lines) {
        if(line.charAt(0) == '#') continue;
        int pos = line.indexOf(subjectOf);

        String lhs = line.substring(0, pos - 1);
        String rhs = line.substring(pos + subjectOf.length());

        String pageId = StrUtil.takeBefore(StrUtil.takeAfterLast(lhs, '/'), '>');

        if(!Objects.equals(lastPageId, pageId)) {
          if(!categories.isEmpty()) {
            catWriter.put(lastPageId, StrUtil.join(categories, "\t"));
          }
          categories.clear();
        }
        lastPageId = pageId;

        int cat_pos = rhs.lastIndexOf(catPrefix);
        String categoryName = StrUtil.takeBefore(rhs.substring(cat_pos + catPrefix.length()), '>');
        if(!categoryName.isEmpty()) {
          categories.add(categoryName);
          catCounts.adjustOrPutValue(categoryName, 1, 1);
          catCounts.adjustOrPutValue("__TOTAL__", 1, 1);
        }
        //System.err.println(pageId +"\t"+categoryName);
        //if(lines.getLineNumber()>1000) break;
        if(msg.ready()) {
          System.err.println("Lines/s: "+msg.estimate(lines.getLineNumber()));
        }
      }

      if (!categories.isEmpty()) {
        catWriter.put(lastPageId, StrUtil.join(categories, "\t"));
      }

    } // close input and output.

    try (IOMapWriter<String, Integer> catCountsWriter = new SortingIOMapWriter<>(new WaltzDiskMapWriter<>(dbpediaDir, "category_counts", CharsetCoders.utf8, FixedSize.ints))) {
      catCounts.forEachEntry((cat, freq) -> {
        try {
          catCountsWriter.put(cat, freq);
        } catch (IOException e) { throw new RuntimeException(e); }
        return true;
      });
    }*/

    try (WaltzDiskMapReader<String, String> catReader = new WaltzDiskMapReader<>(dbpediaDir, "category_by_article", CharsetCoders.utf8, new LZFCoder<>(CharsetCoders.utf8))) {
      System.out.println("Íris_Stefanelli: "+catReader.get("Íris_Stefanelli"));
      System.out.println("Albedo: "+catReader.get("Albedo"));
      System.out.println("Agent_Orange: "+catReader.get("Agent_Orange"));
      System.out.println("Agent_Orange_(cocktail): " + catReader.get("Agent_Orange_(cocktail)"));
    }
  }
}
