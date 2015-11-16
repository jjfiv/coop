package edu.umass.cs.jfoley.coop.experiments.rels;

import edu.washington.cs.knowitall.extractor.ReVerbExtractor;
import edu.washington.cs.knowitall.extractor.conf.ConfidenceFunction;
import edu.washington.cs.knowitall.extractor.conf.ReVerbOpenNlpConfFunction;
import edu.washington.cs.knowitall.nlp.ChunkedSentence;
import edu.washington.cs.knowitall.nlp.OpenNlpSentenceChunker;
import edu.washington.cs.knowitall.nlp.extraction.ChunkedBinaryExtraction;

/**
 * @author jfoley
 */
public class ReVerbExtract {
  public static void main(String[] args) throws Exception {

    String sentStr = "ReVerb is a relation-extraction java library.".toLowerCase();

    // Looks on the classpath for the default model files.
    OpenNlpSentenceChunker chunker = new OpenNlpSentenceChunker();
    ChunkedSentence sent = chunker.chunkSentence(sentStr);

    // Prints out the (token, tag, chunk-tag) for the sentence
    System.out.println(sentStr);
    for (int i = 0; i < sent.getLength(); i++) {
      String token = sent.getToken(i);
      String posTag = sent.getPosTag(i);
      String chunkTag = sent.getChunkTag(i);
      System.out.println(token + " " + posTag + " " + chunkTag);
    }

    // Prints out extractions from the sentence.
    ReVerbExtractor reverb = new ReVerbExtractor();

    ConfidenceFunction confFunc = //(ignored) -> 1.0;
        new ReVerbOpenNlpConfFunction();

    for (ChunkedBinaryExtraction extr : reverb.extract(sent)) {
      double conf = confFunc.getConf(extr);
      System.out.println("Arg1=" + extr.getArgument1());
      System.out.println("Rel=" + extr.getRelation());
      System.out.println("Arg2=" + extr.getArgument2());
      System.out.println("Conf=" + conf);
    }
  }
}
