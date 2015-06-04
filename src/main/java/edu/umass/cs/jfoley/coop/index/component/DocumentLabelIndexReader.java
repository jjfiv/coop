package edu.umass.cs.jfoley.coop.index.component;

import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.index.NamespacedLabel;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * @author jfoley
 */
public class DocumentLabelIndexReader implements Closeable {
  private final IOMap<NamespacedLabel, List<Integer>> ioMap;

  public DocumentLabelIndexReader(String path) throws IOException {
    this.ioMap = GalagoIO.openIOMap(NamespacedLabel.coder, new DeltaIntListCoder(), path);
  }

  public List<Integer> getMatchingDocs(String field, String label) throws IOException {
    NamespacedLabel key = new NamespacedLabel(field, label);
    return ioMap.get(key);
  }

  @Override
  public void close() throws IOException {
    ioMap.close();
  }
}
