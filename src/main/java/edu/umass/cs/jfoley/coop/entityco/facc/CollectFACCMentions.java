package edu.umass.cs.jfoley.coop.entityco.facc;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import gnu.trove.map.hash.TObjectIntHashMap;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jfoley
 */
public class CollectFACCMentions {
  public static class FACCMention implements Comparable<FACCMention> {
    final String rawText;
    final String freebaseId;

    public FACCMention(String rawText, String freebaseId) {
      this.rawText = rawText;
      this.freebaseId = freebaseId;
    }

    @Override
    public int hashCode() {
      return rawText.hashCode() ^ freebaseId.hashCode();
    }
    @Override
    public boolean equals(Object other) {
      if(!(other instanceof FACCMention)) {
        return false;
      }
      FACCMention rhs = (FACCMention) other;
      return rawText.equals(rhs.rawText) && freebaseId.equals(rhs.freebaseId);
    }

    @Override
    public int compareTo(@Nonnull FACCMention o) {
      int cmp = freebaseId.compareTo(o.freebaseId);
      if(cmp != 0) return cmp;
      return rawText.compareTo(rawText);
    }
  }
  public static void main(String[] args) throws IOException {
    String input = args[0]; // "ClueWeb09_English_1/en0000/00.anns.tsv.gz";
    int index = Integer.parseInt(args[1]); // 0

    TObjectIntHashMap<FACCMention> entityCounts = new TObjectIntHashMap<>();
    try (LinesIterable lines = LinesIterable.fromFile(input)) {
      for (String line : lines) {
        String[] row = line.split("\t");
        String text = row[2];
        String fb = row[7];

        entityCounts.adjustOrPutValue(new FACCMention(text, fb), 1, 1);
      }
    }

    List<FACCMention> mentions = new ArrayList<>(entityCounts.keySet());
    Collections.sort(mentions);

    try(PrintWriter output = IO.openPrintWriter("facc_"+index+"_counts.tsv.gz")) {
      for (FACCMention mention : mentions) {
        int count = entityCounts.get(mention);
        output.println(mention.freebaseId + "\t" + mention.rawText + "\t" + count);
      }
    }
  }
}
