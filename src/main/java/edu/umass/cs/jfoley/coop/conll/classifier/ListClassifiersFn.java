package edu.umass.cs.jfoley.coop.conll.classifier;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.server.IndexServerFn;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;

/**
 * @author jfoley.
 */
public class ListClassifiersFn extends IndexServerFn {
  public ListClassifiersFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException {
    Parameters output = Parameters.create();
    for (String classifierName : index.classifiers.dataByClassifier.keySet()) {
      output.put(classifierName, index.classifiers.getInfo(classifierName));
    }
    return Parameters.parseArray("classifiers", output);
  }
}
