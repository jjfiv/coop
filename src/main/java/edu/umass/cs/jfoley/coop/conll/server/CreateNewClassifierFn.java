package edu.umass.cs.jfoley.coop.conll.server;

import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;
import org.lemurproject.galago.utility.Parameters;

import java.io.IOException;
import java.sql.SQLException;

/**
 * @author jfoley
 */
public class CreateNewClassifierFn extends IndexServerFn {
  public CreateNewClassifierFn(TermBasedIndexReader index) {
    super(index);
  }

  @Override
  public Parameters handleRequest(Parameters input) throws IOException, SQLException {
    String name = input.getString("name");
    String description = input.get("description", (String) null);
    int id = this.index.classifiers.create(name, description);
    return this.index.classifiers.getInfo(id);
  }
}
