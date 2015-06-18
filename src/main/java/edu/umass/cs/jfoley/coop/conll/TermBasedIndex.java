package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.errors.FatalError;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.math.StreamingStats;
import edu.umass.cs.ciir.waltz.coders.files.RunReader;
import edu.umass.cs.ciir.waltz.postings.extents.Span;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.CoopToken;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class TermBasedIndex {

  public static void main(String[] args) throws IOException {
    Directory here = Directory.Read(".");
    for (File file : here.children()) {
      if(file.getName().endsWith(".run")) {
        System.out.println(file);
        List<CoopDoc> collection = new ArrayList<>();
        try (RunReader<CoopDoc> reader = new RunReader<>(new KryoCoder<>(CoopDoc.class), file)) {
          long ms = Timing.milliseconds(() -> {
            IterableFns.intoSink(reader, collection::add);
          });
          System.out.println("Read "+reader.getCount()+" entries in "+ms+ "ms");
        }

        if(file.getName().contains("test")) continue;

        Directory output = here.childDir(file.getName()+".stoken.index");

        try (TermBasedIndexWriter writer = new TermBasedIndexWriter(output)) {
          StreamingStats stats = new StreamingStats();
          for (int i = 0; i < collection.size(); i++) {
            CoopDoc coopDoc = collection.get(i);
            List<CoopToken> tokens = coopDoc.tokens();
            if (coopDoc.getTags().isEmpty()) continue;
            for (Span stag : coopDoc.getTags().get("true_sentence")) {
              stats.push(Timing.milliseconds(() -> {
                try {
                  writer.addSentence(ListFns.slice(tokens, stag.begin, stag.end));
                } catch (IOException e) {
                  throw new FatalError(e);
                }
              }));
            }

            if(i++ % 100 == 0) {
              System.out.printf("%d: %s\n", i, stats);
            }
          }
          System.out.println(stats);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      };

    }
  }
}
