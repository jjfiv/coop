package edu.umass.cs.jfoley.coop.index.corpus;

import ciir.jfoley.chai.io.archive.ZipArchive;
import ciir.jfoley.chai.io.archive.ZipArchiveEntry;
import edu.umass.cs.ciir.waltz.coders.kinds.ListCoder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author jfoley
 */
public class ZipTokensCorpusReader extends AbstractCorpusReader {
  final ZipArchive tokensCorpus;
  final ListCoder<String> tokensCodec;

  public ZipTokensCorpusReader(ZipArchive tokensCorpus, ListCoder<String> tokensCodec) {
    this.tokensCorpus = tokensCorpus;
    this.tokensCodec = tokensCodec;
  }

  @Override
  public void close() throws IOException {
    tokensCorpus.close();
  }

  @Override
  public List<String> pullTokens(int document) {
    ZipArchiveEntry entry = tokensCorpus.getByName(Integer.toString(document));
    if(entry == null) return null;
    try (InputStream is = entry.getInputStream()) {
      return tokensCodec.readImpl(is);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
