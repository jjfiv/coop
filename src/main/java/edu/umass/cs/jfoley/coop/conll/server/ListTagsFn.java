package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.collections.util.IterableFns;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author jfoley
 */
public class ListTagsFn extends IndexServerFn {
  public ListTagsFn(TermBasedIndexReader index) throws IOException {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    Parameters output = Parameters.create();
    output.put("tags", IterableFns.intoList(this.index.tokensByTags.keys()));
    return output;
  }
}
