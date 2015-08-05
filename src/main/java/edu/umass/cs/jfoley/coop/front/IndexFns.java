package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.errors.FatalError;
import edu.umass.cs.jfoley.coop.conll.server.ServerFn;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.TermSlice;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class IndexFns {
  public static void setup(IndexReader coopIndex, Map<String, ServerFn> methods) {

    methods.put("indexMeta", (p) -> coopIndex.getMetadata());
    methods.put("findKWIC", new FindKWIC(coopIndex));
  }

  public static class FindKWIC implements ServerFn {
    public final IndexReader index;

    public FindKWIC(IndexReader index) {
      this.index = index;
    }

    @Override
    public Parameters handleRequest(Parameters p) throws IOException, SQLException {
      Parameters output = Parameters.create();
      int width = p.get("width", 5);
      int limit = p.get("limit", 100);

      Tokenizer tokenizer = new TagTokenizer();
      List<String> query = tokenizer.tokenize(p.getString("query")).terms;

      Pair<Long, List<DocumentResult<Integer>>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, query));
      output.put("queryTime", hits.left);
      output.put("queryHits", hits.right.size());

      List<TermSlice> slices = new ArrayList<>();
      for (DocumentResult<Integer> hit : ListFns.take(hits.right, limit)) {
        slices.add(new TermSlice(
            hit.document,
            hit.value - width,
            hit.value + query.size() + width));
      }

      Pair<Long, List<Pair<TermSlice, List<String>>>> kwic = Timing.milliseconds(() -> {
        try {
          return index.getCorpus().pullTermSlices(slices);
        } catch (IOException e) {
          throw new FatalError(e);
        }
      });

      output.put("pullResultTime", kwic.left);

      List<Parameters> results = new ArrayList<>();
      for (Pair<TermSlice, List<String>> data : kwic.right) {
        Parameters hitP = Parameters.create();
        TermSlice info = data.left;
        hitP.put("document", info.document);
        hitP.put("start", info.start);
        hitP.put("end", info.end);
        hitP.put("terms", data.right);
        results.add(hitP);
      }

      output.put("results", results);
      return output;
    }
  }
}
