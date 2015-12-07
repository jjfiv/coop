package edu.umass.cs.jfoley.coop.experiments.deepscore;

import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.core.index.disk.DiskIndex;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.iterator.DataIterator;
import org.lemurproject.galago.core.retrieval.iterator.ScoreIterator;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author jfoley
 */
public class RobustFDM {
  public static void main(String[] args) throws Exception {
    LocalRetrieval ret = new LocalRetrieval("/mnt/scratch3/jfoley/robust.galago");

    Map<String, String> qidToDesc = new TreeMap<>();
    for (String line : LinesIterable.fromFile("/home/jfoley/code/queries/robust04/rob04.descs.tsv").slurp()) {
      String[] data = line.split("\t");
      qidToDesc.put(data[0].trim(), data[1].trim());
    }

    String model = "fdm";

    long maxDocument = ret.getIndex().getLengthsIterator().totalEntries();

    TagTokenizer tok = new TagTokenizer();
    StringPooler.disable();

    qidToDesc.entrySet().parallelStream().forEach((kv -> {
      String qid = kv.getKey();
      System.err.println(qid);
      try (PrintWriter scores = IO.openPrintWriter("robust."+qid+"."+model+".tsv")) {
        String query = kv.getValue();
        List<String> tokens = tok.tokenize(query).terms;
        Node qNode = new Node(model);
        qNode.addTerms(tokens);

        Parameters qp = Parameters.create();
        Node xqNode = ret.transformQuery(qNode, qp);

        ScoreIterator iterator = (ScoreIterator) ret.createIterator(qp, xqNode);
        Debouncer msg = new Debouncer();
        iterator.forEach(ctx -> {
          if(msg.ready()) {
            System.err.println("\t"+msg.estimate(ctx.document, maxDocument));
          }
          scores.println(ctx.document+"\t"+iterator.score(ctx));
        });
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }));
  }

  public static class DumpNameIds {
    public static void main(String[] args) throws IOException {
      DiskIndex ret = new DiskIndex("/mnt/scratch3/jfoley/robust.galago");

      try (PrintWriter scores = IO.openPrintWriter("robust.ids.tsv")) {
        DataIterator<String> namesIterator = ret.getNamesIterator();
        namesIterator.forEach(ctx -> {
          scores.println(ctx.document+"\t"+namesIterator.data(ctx));
        });
      }
    }
  }
}
