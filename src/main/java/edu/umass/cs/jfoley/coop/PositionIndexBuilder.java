package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.lang.Builder;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.dociter.movement.PostingMover;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.galago.io.RawGalagoDiskMapWriter;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.SimplePostingListFormat;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley.
 */
public class PositionIndexBuilder implements Closeable, Builder<IOMap<String, PostingMover<PositionsList>>> {
  private final StreamingPostingBuilder<String, PositionsList> builder;
  private final String outputPath;

  public PositionIndexBuilder(String outputPath) throws IOException {
    this.outputPath = outputPath;
    this.builder = new StreamingPostingBuilder<>(
        CharsetCoders.utf8LengthPrefixed,
        new PositionsListCoder(),
        new RawGalagoDiskMapWriter(outputPath)
    );
  }

  public void process(String term, int document, IntList positions) {
    builder.add(term, document, new SimplePositionsList(positions));
  }

  @Override
  public void close() throws IOException {
    builder.close(); // close the posting builder
  }

  @Override
  public IOMap<String, PostingMover<PositionsList>> getOutput() {
    return openExisting(outputPath);
  }

  public static IOMap<String, PostingMover<PositionsList>> openExisting(String path) {
    try {
      return GalagoIO.openIOMap(
          CharsetCoders.utf8LengthPrefixed,
          new SimplePostingListFormat.PostingCoder<>(new PositionsListCoder()),
          path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}



