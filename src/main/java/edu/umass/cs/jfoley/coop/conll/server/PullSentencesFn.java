package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author jfoley.
 */
public class PullSentencesFn extends IndexServerFn {
  public PullSentencesFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    IntList ids = new IntList();
    for (Long sid : input.getAsList("sentences", Long.class)) {
      ids.add(IntMath.fromLong(sid));
    }
    return Parameters.parseArray("sentences", this.pullSentenceJSON(ids));
  }
}
