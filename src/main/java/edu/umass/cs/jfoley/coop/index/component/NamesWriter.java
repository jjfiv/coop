package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.IdMaps;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.IOException;

/**
 * @author jfoley
 */
public class NamesWriter extends IndexItemWriter {
  private final IdMaps.Writer<String> names;
  public NamesWriter(Directory outputDir, IndexConfiguration cfg) throws IOException {
    super(outputDir, cfg);
    this.names = GalagoIO.openIdMapsWriter(
        outputDir.childPath("names"),
        VarUInt.instance,
        CharsetCoders.utf8);
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    names.put(document.getIdentifier(), document.getName());
  }

  @Override
  public void close() throws IOException {
    names.close();
  }
}
