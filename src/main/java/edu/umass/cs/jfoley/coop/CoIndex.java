package edu.umass.cs.jfoley.coop;

import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.archive.ZipWriter;
import ciir.jfoley.chai.string.StrUtil;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author jfoley.
 */
public class CoIndex {
  public static class Builder implements Closeable {
    private final Directory outputDir;
    private final ZipWriter rawCorpusWriter;

    public Builder(Directory outputDir) throws IOException {
      this.outputDir = outputDir;
      this.rawCorpusWriter = new ZipWriter(outputDir.childPath("raw.zip"));
    }

    public void addDocument(String name, String text) throws IOException {
      System.out.println(name+"\t"+ StrUtil.preview(text, 60));
      rawCorpusWriter.writeUTF8(name, text);
    }

    @Override
    public void close() throws IOException {
      rawCorpusWriter.close();
    }
  }
}
