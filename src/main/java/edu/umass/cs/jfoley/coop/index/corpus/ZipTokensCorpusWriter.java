package edu.umass.cs.jfoley.coop.index.corpus;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipWriter;
import edu.umass.cs.ciir.waltz.coders.kinds.CharsetCoders;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.index.CoopTokenizer;
import edu.umass.cs.jfoley.coop.index.component.IndexItemWriter;

import java.io.IOException;

/**
 * So we don't have to pay tokenization time in our future life.
 * @author jfoley
 */
public class ZipTokensCorpusWriter extends IndexItemWriter {
  private final ZipWriter tokensCorpusWriter;
  private final ListCoder<String> tokensCodec;

  public ZipTokensCorpusWriter(Directory outputDir, CoopTokenizer tokenizer) throws IOException {
    super(outputDir, tokenizer);
    this.tokensCorpusWriter = new ZipWriter(outputDir.childPath("tokens.zip"));
    this.tokensCodec = new ListCoder<>(CharsetCoders.utf8LengthPrefixed);
  }

  @Override
  public void process(CoopDoc document) throws IOException {
    tokensCorpusWriter.write(Integer.toString(document.getIdentifier()), outputStream -> {
      tokensCodec.write(outputStream, document.getTerms(tokenizer.getDefaultTermSet()));
    });
  }

  @Override
  public void close() throws IOException {
    tokensCorpusWriter.close();
  }
}
