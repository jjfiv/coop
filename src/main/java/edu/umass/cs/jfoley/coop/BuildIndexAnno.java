package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.IndexBuilder;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class BuildIndexAnno {
  public static void main(String[] args) throws IOException {
    File input = new File("bills-data/billfiles.standard.anno.gz");

    IndexConfiguration cfg = IndexConfiguration.create();

    Debouncer msg = new Debouncer(1000);

    try (IndexBuilder builder = new IndexBuilder(cfg, new Directory("bills.index"))) {
      try (LinesIterable lines = LinesIterable.fromFile(input)) {
        for (String line : lines) {
          String[] dat = line.split("\t");
          String name = dat[0];
          Parameters docInfo = Parameters.parseString(dat[1]);
          if (docInfo.keySet().size() != 1) {
            System.err.println(docInfo.keySet());
          }
          List<Parameters> info = docInfo.getList("sentences", Parameters.class);

          CoopDoc doc = new CoopDoc();
          doc.setName(name);

          List<String> pos = new ArrayList<>();
          List<String> lemmas = new ArrayList<>();
          List<String> tokens = new ArrayList<>();
          if (msg.ready()) {
            int n = lines.getLineNumber();
            System.out.println("#" + n + " " + name + " " + info.size());
            System.out.println(msg.estimate(n, 88232));
            if (n > 3000) break;
          }
          for (Parameters sentence : info) {
            List<String> spos = sentence.getAsList("pos", String.class);
            int start = pos.size();
            int end = start + spos.size();
            doc.addTag("sentence", start, end);
            pos.addAll(spos);
            lemmas.addAll(sentence.getAsList("lemmas", String.class));
            tokens.addAll(sentence.getAsList("tokens", String.class));
          }

          doc.setTerms("tokens", tokens);
          doc.setTerms("lemmas", lemmas);
          doc.setTerms("pos", pos);
          doc.setRawText(line);
          builder.addDocument(doc);
        }
      } // lines
      System.out.println("Done Reading!");
    } // builder

    System.out.println("Done Indexing!");
  }
}
