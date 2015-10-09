package edu.umass.cs.jfoley.coop.entityco.facc;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author jfoley
 */
public class ReduceFACCMentions {

  public static void main(String[] args) throws IOException {
    Directory input = Directory.Read("/mnt/scratch/jfoley/facc_09_counts/");

    List<File> inputs = input.children();

    TObjectIntHashMap<CollectFACCMentions.FACCMention> mentionFrequencies = new TObjectIntHashMap<>();

    Debouncer msg = new Debouncer();
    for (int i = 0; i < inputs.size(); i++) {
      File file = inputs.get(i);
      if(msg.ready()) {
        System.err.println("File " + i + "/" + inputs.size() + " " + msg.estimate(i, inputs.size()));
      }

      try (LinesIterable lines = LinesIterable.fromFile(file)) {
        for (String line : lines) {
          String[] data = line.split("\t");
          CollectFACCMentions.FACCMention m = new CollectFACCMentions.FACCMention(data[0], data[1]);
          int count = Integer.parseInt(data[2]);
          mentionFrequencies.adjustOrPutValue(m, count, count);
        }
      }
    }


    try (PrintWriter output = IO.openPrintWriter("facc_09_mentions.tsv.gz")) {
      mentionFrequencies.forEachEntry((m, count) -> {
        output.println(m.freebaseId+"\t"+m.rawText+"\t"+count);
        return true;
      });
    }
  }
}
