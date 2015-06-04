package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.MapFns;
import ciir.jfoley.chai.fn.GenerateFn;
import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.ciir.waltz.io.postings.PositionsListCoder;
import edu.umass.cs.ciir.waltz.io.postings.StreamingPostingBuilder;
import edu.umass.cs.ciir.waltz.postings.positions.PositionsList;
import edu.umass.cs.ciir.waltz.postings.positions.SimplePositionsList;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class PositionsSetWriter extends IndexItemWriter {
  private final HashMap<String, StreamingPostingBuilder<String, PositionsList>> positionsBuilders;

  public PositionsSetWriter(Directory outputDir, CoopTokenizer tokenizer) throws IOException {
    super(outputDir, tokenizer);

    this.positionsBuilders = new HashMap<>();
    for (String tokenSet : this.tokenizer.getTermSets()) {
      positionsBuilders.put(
          tokenSet,
          new StreamingPostingBuilder<>(
              CharsetCoders.utf8Raw,
              new PositionsListCoder(),
              GalagoIO.getRawIOMapWriter(outputDir.childPath(tokenSet + ".positions")))
      );
    }
  }

  @Override
  public void process(CoopDoc document) {
    for (Map.Entry<String, List<String>> kv : document.getTerms().entrySet()) {
      addToPositionsBuilder(document.getIdentifier(), kv.getKey(), kv.getValue());
    }
  }

  public void addToPositionsBuilder(int currentId, String tokenSet, List<String> terms) {
    StreamingPostingBuilder<String, PositionsList> positionsBuilder = this.positionsBuilders.get(tokenSet);

    // collection position vectors:
    Map<String, IntList> data = new HashMap<>();
    for (int i = 0, termsSize = terms.size(); i < termsSize; i++) {
      String term = terms.get(i);
      MapFns.extendCollectionInMap(data, term, i, (GenerateFn<IntList>) IntList::new);
    }
    // Add position vectors to builder:
    for (Map.Entry<String, IntList> kv : data.entrySet()) {
      positionsBuilder.add(
          kv.getKey(),
          currentId,
          new SimplePositionsList(kv.getValue()));
    }
  }

  @Override
  public void close() throws IOException {
    for (Closeable builder : positionsBuilders.values()) {
      builder.close();
    }
  }
}
