package edu.umass.cs.jfoley.coop.conll.classifier;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import edu.umass.cs.jfoley.coop.conll.server.IndexServerFn;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author jfoley.
 */
public class ListClassifiersFn extends IndexServerFn {
  public ListClassifiersFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    if(input.isString("id")) {
      return index.classifiers.getInfo(input.getInt("id"));
    }
    return Parameters.parseArray("classifiers", index.classifiers.getAllInfo());
  }
}
