package edu.umass.cs.jfoley.coop.phrases;

import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley
 */
public class AccumulatingHitListWriter implements Closeable {
  int lastDoc = -1;
  PhraseHitList docHits = new PhraseHitList();

  public final IOMapWriter<Integer, PhraseHitList> output;

  public AccumulatingHitListWriter(IOMapWriter<Integer, PhraseHitList> output) {
    this.output = output;
  }

  public void add(int doc, int pos, int size, int id) {
    if (lastDoc != doc) {
      flush();
    }
    lastDoc = doc;
    docHits.add(pos, size, id);
  }

  private void flush() {
    if (lastDoc != -1) {
      try {
        output.put(lastDoc, docHits);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      docHits = new PhraseHitList();
    }
  }

  @Override
  public void close() throws IOException {
    flush();
    output.close();
  }
}
