package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.FixedSize;
import edu.umass.cs.ciir.waltz.coders.map.IOMap;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.IOException;

/**
 * @author jfoley
 */
public class KryoCoopDocCorpusWriter extends IndexItemWriter {
  private final IOMapWriter<Integer, CoopDoc> writer;

  public KryoCoopDocCorpusWriter(Directory outputDir, IndexConfiguration cfg) throws IOException {
    super(outputDir, cfg);
    this.writer = GalagoIO.getIOMapWriter(
        FixedSize.ints,
        new KryoCoder<>(CoopDoc.class),
        outputDir.childPath("kryo.corpus")
    ).getSorting();
  }

  public static IOMap<Integer, CoopDoc> getReader(Directory inputDir) throws IOException {
    return GalagoIO.openIOMap(
        FixedSize.ints,
        new KryoCoder<>(CoopDoc.class),
        inputDir.childPath("kryo.corpus")
    );
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    writer.put(document.getIdentifier(), document);
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
