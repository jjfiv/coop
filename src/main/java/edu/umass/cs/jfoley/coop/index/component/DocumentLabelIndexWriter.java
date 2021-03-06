package edu.umass.cs.jfoley.coop.index.component;

import ciir.jfoley.chai.io.Directory;
import edu.umass.cs.ciir.waltz.postings.docset.DocumentSetWriter;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.document.DocVar;
import edu.umass.cs.jfoley.coop.index.general.IndexItemWriter;
import edu.umass.cs.jfoley.coop.index.general.NamespacedLabel;
import edu.umass.cs.jfoley.coop.schema.CategoricalVarSchema;
import edu.umass.cs.jfoley.coop.schema.IndexConfiguration;

import java.io.IOException;

/**
 * @author jfoley
 */
public class DocumentLabelIndexWriter extends IndexItemWriter {
  DocumentSetWriter<NamespacedLabel> writer;

  public DocumentLabelIndexWriter(Directory outputDir, IndexConfiguration cfg) throws IOException {
    super(outputDir, cfg);
    this.writer = new DocumentSetWriter<>(
        NamespacedLabel.coder,
        outputDir, "doclabels"
    );
  }

  public void add(String namespace, String label, int docId) {
    NamespacedLabel key = new NamespacedLabel(namespace, label);
    try {
      writer.process(key, docId);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
    writer.close();
  }
}
