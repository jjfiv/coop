package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.VarInt;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.schema.IntegerVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;

import java.io.IOException;

/**
 * @author jfoley
 */
public class NumericalVarWriter extends IndexItemWriter {
  StreamingPostingBuilder<String, Integer> countPostingsBuilder;

  public NumericalVarWriter(Directory outputDir, CoopTokenizer tokenizer) throws IOException {
    super(outputDir, tokenizer);
    countPostingsBuilder = new StreamingPostingBuilder<>(
        CharsetCoders.utf8Raw,
        VarInt.instance,
        GalagoIO.getRawIOMapWriter(outputDir.childPath("numbers"))
    );
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    for (DocVar docVar : document.getVariables()) {
      if(docVar.getSchema() instanceof IntegerVarSchema) {
        String name = docVar.getName();
        int value = (Integer) docVar.get();
        countPostingsBuilder.add(name, document.getIdentifier(), value);
      }
    }
  }

  @Override
  public void close() throws IOException {
    countPostingsBuilder.close();
  }
}
