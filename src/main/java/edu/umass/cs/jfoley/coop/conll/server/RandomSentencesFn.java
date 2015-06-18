package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class RandomSentencesFn extends IndexServerFn {
  public RandomSentencesFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    int count = input.get("count", 10);
    List<Integer> ids = Sample.randomIntegers(count, index.getSentenceCount());

    Parameters response = Parameters.create();
    response.put("sentences", pullSentenceJSON(ids));
    return response;
  }
}
