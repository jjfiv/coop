package edu.umass.cs.jfoley.coop.conll.server;

import org.lemurproject.galago.utility.Parameters;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * @author jfoley
 */
public interface ServerFn {
  Logger logger = Logger.getLogger(ServerFn.class.getName());
  @Nonnull
  Parameters handleRequest(Parameters input) throws IOException, SQLException;
}
