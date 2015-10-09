package edu.umass.cs.jfoley.coop.entityco.facc;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.string.StrUtil;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class ProcessFACCStats {
  public static void main(String[] args) throws IOException {


    long totalMentions = 0;
    TObjectIntHashMap<String> entityCount = new TObjectIntHashMap<>();
    Map<String, TObjectIntHashMap<String>> mentionCounts = new HashMap<>();

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    try (LinesIterable lines = LinesIterable.fromFile("facc_09_dbpedia_mentions.tsv.gz")) {
      for (String line : lines) {
        String[] row = line.split("\t");
        String mention = row[0];
        String entity = row[1];
        int count = Integer.parseInt(row[2]);

        List<String> terms = tokenizer.tokenize(mention).terms;
        if (terms.isEmpty()) continue;
        String mtext = StrUtil.join(terms);

        // calculate naive bayes probability of this mention mapping to a given entity:
        mentionCounts
            .computeIfAbsent(mtext, (ignored) -> new TObjectIntHashMap<>())
            .adjustOrPutValue(entity, count, count);

        entityCount.adjustOrPutValue(entity, count, count);
        totalMentions += count;
      }
    }
    try (PrintWriter output = IO.openPrintWriter("facc_09_mcounts.tsv.gz")) {
      for (Map.Entry<String, TObjectIntHashMap<String>> kv : mentionCounts.entrySet()) {
        String mtext = kv.getKey();
        TObjectIntHashMap<String> conditionalEcounts = kv.getValue();

        output.print(mtext);
        conditionalEcounts.forEachEntry((e, count) -> {
          output.print("\t"+e+" "+count);
          return true;
        });
        output.println();
      }
    }
    try (PrintWriter output = IO.openPrintWriter("facc_09_ecounts.tsv.gz")) {
      final double chances = totalMentions;
      entityCount.forEachEntry((e, count) -> {
        output.println(e + "\t" + Math.log(count / chances));
        return true;
      });
    }

    System.out.println(totalMentions);
  }
}
