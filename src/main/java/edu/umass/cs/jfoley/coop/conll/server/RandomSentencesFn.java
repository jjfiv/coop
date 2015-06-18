package edu.umass.cs.jfoley.coop.conll.server;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
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

    List<List<Parameters>> sentences = new ArrayList<>();

    final List<List<SentenceIndexedToken>> data = new ArrayList<>();
    long time = Timing.milliseconds(() -> {
      try {
        data.addAll(index.pullSentences(ids));
      } catch (IOException e) {
        throw new ServerErr(e);
      }
    });

    for (List<SentenceIndexedToken> sentence : index.pullSentences(ids)) {
      List<Parameters> tokens = new ArrayList<>();
      for (SentenceIndexedToken stoken : sentence) {
        tokens.add(stoken.toJSON());
      }
      sentences.add(tokens);
    }
    Parameters response = Parameters.create();
    response.put("sentences", sentences);
    response.put("time", time);
    return response;
  }
}
