package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.Mover;
import edu.umass.cs.ciir.waltz.postings.docset.DocumentSetReader;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley
 */
public class DocumentLabelIndexReader implements Closeable {
  private final IOMap<NamespacedLabel, Mover> ioMap;

  public DocumentLabelIndexReader(Directory dir) throws IOException {
    this.ioMap = new DocumentSetReader<>(NamespacedLabel.coder, dir, "doclabels");
  }

  public Mover getMatchingDocs(String field, String label) throws IOException {
    NamespacedLabel key = new NamespacedLabel(field, label);
    return ioMap.get(key);
  }

  @Override
  public void close() throws IOException {
    ioMap.close();
  }
}
