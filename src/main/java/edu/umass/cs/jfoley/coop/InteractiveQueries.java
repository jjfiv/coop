package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.FS;
import org.lemurproject.galago.utility.Parameters;
import org.lemurproject.galago.utility.tools.AppFunction;

import java.io.PrintStream;

/**
 * @author jfoley.
 */
public class InteractiveQueries extends AppFunction {
  @Override
  public String getName() {
    return "interactive-queries";
  }

  @Override
  public String getHelpString() {
    return makeHelpStr(
        "index", "path to this collection's index"
    );
  }

  @Override
  public void run(Parameters p, PrintStream output) throws Exception {
    String indexPath = p.getString("index");
    if(!FS.isDirectory(indexPath)) {
      throw new IllegalArgumentException("Couldn't make \"index\" into a folder.");
    }
    output.println(indexPath);
  }
}
