package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.files.RunReader;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;

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

        Directory output = here.childDir(file.getName()+".stoken.index");

        try (TermBasedIndexWriter writer = new TermBasedIndexWriter(output)) {
          for (CoopDoc coopDoc : collection) {
            if (coopDoc.getTags().isEmpty()) continue;
            writer.addDocument(coopDoc);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      };

    }
  }
}
