package edu.umass.cs.jfoley.coop.experiments.rels;

import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.bills.IntCoopIndex;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * Look through parentheses in titles to see how many categories there are and if it makes sense to build classifiers for them.
 *
 * Not sure, actually: 100k pages that are ambig., 60k categories involved.
 * @author jfoley
 */
public class DbpediaCategoryStats {
  public static void main(String[] args) throws IOException {
    // Extract from index:
    IntCoopIndex dbpedia = new IntCoopIndex(Directory.Read("/mnt/scratch3/jfoley/dbpedia.ints"));

    TObjectIntHashMap<String> categories = new TObjectIntHashMap<>();
    HashMap<String, List<String>> pages = new HashMap<>();

    Predicate<String> onCategory = (cat) -> {
      if(categories.contains(cat)) {
        categories.adjustOrPutValue(cat, 1, 1);
        return false;
      } else {
        categories.adjustOrPutValue(cat, 1, 1);
        return true;
      }
    };

    final String defaultCategory = "default";

    int i=0;
    Debouncer msg = new Debouncer();
    dbpedia.getNames().values().forEach((name) -> {
      int pos = name.indexOf("_(");
      if(pos >= 0) {
        String cat = StrUtil.collapseSpecialMarks(StrUtil.compactSpaces(name.substring(pos + 2).replaceAll("(\\(|_|\\))", " "))).intern();
        String title = StrUtil.collapseSpecialMarks(name.substring(0, pos).replace('_', ' '));

        if(cat.contains(",")) {
          for (String subcat : cat.split(",")) {
            if(onCategory.test(subcat.trim().toLowerCase())) {
              //System.err.println(name+" -> ``"+title+"`` , ``"+subcat+"``");
            }
          }
        } else {
          if(onCategory.test(cat.toLowerCase())) {
            //System.err.println(name+" -> ``"+title+"`` , ``"+cat+"``");
          }
        }
        MapFns.extendListInMap(pages, title, cat);
      } else {
        categories.adjustOrPutValue(defaultCategory, 1, 1);
        MapFns.extendListInMap(pages, name, defaultCategory);
      }
      if(msg.ready()) {
        System.err.println("# "+name);
      }
    });

    int ambiguousPages = 0;

    TObjectIntHashMap<String> disambiguatingCategories = new TObjectIntHashMap<>();
    for (List<String> cats : pages.values()) {
      if(cats.size() <= 1) continue;
      ambiguousPages++;
      for (String cat : cats) {
        disambiguatingCategories.adjustOrPutValue(cat, 1, 1);
      }
      if(cats.size() > 4) {
        System.err.println(cats);
      }
    }

    System.err.println(ambiguousPages);
    System.err.println(disambiguatingCategories.size());
    System.err.println(categories.size());

    /*
    final int K = 40;
    List<TopKHeap.Weighted<String>> catsWithMoreThanK = new ArrayList<>();
    categories.forEachEntry((cat, count) -> {
      if(count >= K) {
        catsWithMoreThanK.add(new TopKHeap.Weighted<>(count, cat));
      }
      return true;
    });
    System.err.println(catsWithMoreThanK.size());
    catsWithMoreThanK.sort(Comparator.reverseOrder());
    for (TopKHeap.Weighted<String> stringWeighted : catsWithMoreThanK) {
      System.err.println("\t"+stringWeighted);
    }
    */
  }
}
