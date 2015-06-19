package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
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
    String classifier = input.getString("classifier");
    List<String> positives = input.getAsList("positives", String.class);
    List<String> negatives = input.getAsList("positives", String.class);

    index.classifiers.addLabels(classifier, positives, negatives);
    return index.classifiers.getInfo(classifier);
  }
}
