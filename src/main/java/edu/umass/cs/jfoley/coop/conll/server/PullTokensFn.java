package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.collections.list.IntList;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.document.CoopToken;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley.
 */
public class PullTokensFn extends IndexServerFn {
  public PullTokensFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    System.err.println(input);
    IntList request = new IntList();
    for (Long token : input.getAsList("tokens", Long.class)) {
      request.add(IntMath.fromLong(token.longValue()));
    }

    List<Parameters> tokens = new ArrayList<>();
    for (Pair<Integer, CoopToken> kv : index.tokenCorpus.getInBulk(request)) {
      tokens.add(kv.getValue().toJSON());
    }

    return Parameters.parseArray("tokens", tokens);
  }
}
