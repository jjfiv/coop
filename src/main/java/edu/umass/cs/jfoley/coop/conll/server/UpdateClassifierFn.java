package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.classifier.LabeledToken;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class UpdateClassifierFn extends IndexServerFn {

  public UpdateClassifierFn(TermBasedIndexReader index) throws IOException {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    long requestTime = System.currentTimeMillis();

    String classifier = input.getString("classifier");
    List<Parameters> labels = input.getAsList("labels", Parameters.class);

    List<LabeledToken> ltok = new ArrayList<>(labels.size());
    for (Parameters label : labels) {
      int id = label.get("tokenId", -1);
      if(id < 0) continue;
      boolean positive = label.get("positive", true);
      long time = Math.min(requestTime, label.get("time", requestTime)); // don't allow times being set in the future from JS
      ltok.add(new LabeledToken(time, id, positive));
    }

    index.classifiers.addLabels(classifier, ltok);
    index.classifiers.train(classifier);
    return index.classifiers.getInfo(classifier);
  }
}
