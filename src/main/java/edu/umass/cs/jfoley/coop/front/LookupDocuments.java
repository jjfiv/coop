package edu.umass.cs.jfoley.coop.front;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.jfoley.coop.index.IndexReader;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * @author jfoley
 */
public class LookupDocuments extends CoopIndexServerFn {
  protected LookupDocuments(IndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    Parameters output = Parameters.create();

    IntList ids = new IntList();
    for (long id : input.getAsList("ids", Long.class)) {
      ids.add(IntMath.fromLong(id));
    }

    TIntObjectHashMap<Parameters> data = new TIntObjectHashMap<>();
    for (Pair<Integer, String> kv : index.lookupNames(ids)) {
      Parameters doc = Parameters.create();
      doc.put("id", kv.left);
      doc.put("name", kv.right);
      data.put(kv.left, doc);
    }

    output.put("results", new ArrayList<>(data.valueCollection()));
    return output;
  }
}
