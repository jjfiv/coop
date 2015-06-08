package edu.umass.cs.jfoley.coop.bdaat;

import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.processing.ProcessingModel;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.utility.Parameters;

/**
 * @author jfoley.
 */
public class BDAATProcessingModel extends ProcessingModel {
  @Override
  public ScoredDocument[] execute(Node queryTree, Parameters queryParams) throws Exception {
    return new ScoredDocument[0];
  }
}
