package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.coders.kinds.DeltaIntListCoder;
import edu.umass.cs.ciir.waltz.coders.kinds.VarUInt;
import edu.umass.cs.ciir.waltz.coders.map.IOMapWriter;
import edu.umass.cs.ciir.waltz.galago.io.GalagoIO;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.document.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.NamespacedLabel;
import edu.umass.cs.jfoley.coop.index.SmartCollection;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jfoley
 */
public class DocumentLabelIndexWriter extends IndexItemWriter {
  private final IOMapWriter<NamespacedLabel, List<Integer>> ioMapWriter;
  private final HashMap<NamespacedLabel, SmartCollection<Integer>> tmpStorage;

  public DocumentLabelIndexWriter(Directory outputDir, CoopTokenizer tokenizer) throws IOException {
    super(outputDir, tokenizer);
    this.tmpStorage = new HashMap<>();
    this.ioMapWriter = GalagoIO.getIOMapWriter(
        NamespacedLabel.coder,
        new DeltaIntListCoder(),
        outputDir.childPath("doclabels")
    ).getSorting();
  }

  public void add(String namespace, String label, int docId) {
    NamespacedLabel key = new NamespacedLabel(namespace, label);
    SmartCollection<Integer> forLabel = tmpStorage.get(key);
    if (forLabel == null) {
      forLabel = new SmartCollection<>(VarUInt.instance);
      tmpStorage.put(key, forLabel);
    }
    forLabel.add(docId);
  }

  @Override
  public void process(CoopDoc document) {
    // collect variables:
    for (DocVar docVar : document.getVariables()) {
      if (docVar.getSchema() instanceof CategoricalVarSchema) {
        String field = docVar.getName();
        String label = (String) docVar.get();
        add(field, label, document.getIdentifier());
      }
    }
  }

  @Override
  public void close() throws IOException {
    for (Map.Entry<NamespacedLabel, SmartCollection<Integer>> kv : tmpStorage.entrySet()) {
      try (SmartCollection<Integer> idSet = kv.getValue()) {
        ioMapWriter.put(kv.getKey(), idSet.slurp());
      } // and delete any files used for that IdSet as we go.
    }
    tmpStorage.clear();
    ioMapWriter.close();
  }
}
