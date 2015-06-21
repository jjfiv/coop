package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.classifier.ClassifiedToken;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley.
 */
public class ClassifyTokensFn extends IndexServerFn {
  public ClassifyTokensFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    String classifier = input.getString("classifier");
    List<Integer> tokenIds = input.getAsList("tokens", Integer.class);

    List<Parameters> output = new ArrayList<>();
    for (ClassifiedToken classifiedToken : index.classifiers.classifyTokens(classifier, tokenIds)) {
      Parameters ctoken = Parameters.create();
      ctoken.put("token", classifiedToken.token.toJSON());
      ctoken.put("positive", classifiedToken.positive);
      ctoken.put("score", classifiedToken.score);
      output.add(ctoken);
    }

    return Parameters.parseArray("results", output);
  }
}
