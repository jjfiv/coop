package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import edu.umass.cs.jfoley.coop.querying.LocatePhrase;
import edu.umass.cs.jfoley.coop.querying.eval.DocumentResult;
import edu.umass.cs.jfoley.coop.tokenization.CoopTokenizer;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class FindPhrase extends CoopIndexServerFn {
  protected FindPhrase(IndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters p) throws IOException, SQLException {
    Parameters output = Parameters.create();
    int offset = p.get("offset", 0);
    int count = p.get("count", 200);
    assert(offset >= 0);
    assert(count > 0);

    String termKind = p.get("termKind", "lemmas");

    CoopTokenizer tokenizer = index.getTokenizer();
    List<String> query = tokenizer.createDocument("tmp", p.getString("query")).getTerms(termKind);

    output.put("queryTerms", query);


    Pair<Long, List<DocumentResult<Integer>>> hits = Timing.milliseconds(() -> LocatePhrase.find(index, query));
    output.put("queryFrequency", hits.right.size());
    output.put("queryTime", hits.left);

    TIntObjectHashMap<Parameters> hitInfos = new TIntObjectHashMap<>();
    // build slices from the results, based on arguments to this file:
    for (DocumentResult<Integer> hit : ListFns.slice(hits.right, offset, offset + count)) {
      Parameters doc = Parameters.create();
      doc.put("id", hit.document);
      doc.put("loc", hit.value);
      hitInfos.put(hit.document, doc);
    }

    for (Pair<Integer, String> kv : index.lookupNames(new IntList(hitInfos.keys()))) {
      hitInfos.get(kv.left).put("name", kv.right);
    }

    output.put("results", new ArrayList<>(hitInfos.valueCollection()));
    return output;
  }
}
