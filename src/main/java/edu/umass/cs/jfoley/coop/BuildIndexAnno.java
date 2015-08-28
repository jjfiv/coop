package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.io.inputs.InputContainer;
import ciir.jfoley.chai.io.inputs.InputFinder;
import ciir.jfoley.chai.io.inputs.InputStreamable;
import ciir.jfoley.chai.string.StrUtil;
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
    Parameters argp = Parameters.create();
    InputFinder inputFinder = InputFinder.Default();
    List<? extends InputContainer> inputs = inputFinder.findAllInputs(argp.get("input", "bills-data/billsanno.zip"));

    IndexConfiguration cfg = IndexConfiguration.create();

    Debouncer msg = new Debouncer(1000);

    int N = inputs.size();
    try (IndexBuilder builder = new IndexBuilder(cfg, new Directory("bills-complete.index"))) {
      for (int i = 0; i < inputs.size(); i++) {
        InputContainer ic = inputs.get(i);
        for (InputStreamable in : ic.getInputs()) {
          try (LinesIterable lines = LinesIterable.of(in.getReader())) {
            for (String line : lines) {
              CoopDoc doc = processAnnoLine(in.getName(), line);
              if (msg.ready()) {
                int numProcessed = builder.count();
                System.out.println("# "+doc.getName()+" "+numProcessed+" " +msg.estimate(numProcessed));
              }
              if(doc.getName().startsWith("bill")) {
                doc.setIdentifier(Integer.parseInt(StrUtil.takeAfter(doc.getName(), "bill")));
              }
              builder.addDocument(doc);
            } // lines
          } // reader
        } // files
      }
      System.out.println("Done Reading!");
    } // builder

    System.out.println("Done Indexing!");
  }

  private static CoopDoc processAnnoLine(String fileName, String line) throws IOException {
    String[] dat = line.split("\t");

    String name;
    Parameters docInfo;
    if(dat.length == 1) {
      docInfo = Parameters.parseString(line);
      name = StrUtil.removeBack((new File(fileName)).getName(), ".anno");
    } else if(dat.length == 2) {
      name = dat[0];
      docInfo = Parameters.parseString(dat[1]);
    } else throw new RuntimeException(line);
    if (docInfo.keySet().size() != 1) {
      System.err.println(docInfo.keySet());
    }
    List<Parameters> info = docInfo.getList("sentences", Parameters.class);

    CoopDoc doc = new CoopDoc();
    doc.setName(name);

    List<String> pos = new ArrayList<>();
    List<String> lemmas = new ArrayList<>();
    List<String> tokens = new ArrayList<>();
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
    return doc;
  }
}
