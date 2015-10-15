package edu.umass.cs.jfoley.coop.experiments;

import ciir.jfoley.chai.lang.ThreadsafeLazyPtr;
import ciir.jfoley.chai.math.StreamingStats;
import ciir.jfoley.chai.string.StrUtil;
import ciir.jfoley.chai.time.Debouncer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.DocumentSource;
import org.lemurproject.galago.core.parse.DocumentStreamParser;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class NewswireTiming {
  public static ThreadsafeLazyPtr<StanfordCoreNLP> nlpSingleton = new ThreadsafeLazyPtr<>(() -> {
    //List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit", "pos", "lemma");
    List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit", "pos", "lemma", "ner");
    Properties props = new Properties();
    props.put("annotators", StrUtil.join(annotators, ","));
    return new StanfordCoreNLP(props);
  });
  public static void main(String[] args) throws IOException {
    Parameters argp = Parameters.parseArgs(args);
    StanfordCoreNLP nlp = nlpSingleton.get();

    Annotation test = new Annotation("The quick brown fox jumped over the lazy dog.");
    nlp.annotate(test);

    // warmup stanford-nlp.

    List<DocumentSplit> documentSplits = DocumentSource.processDirectory(new File(argp.get("input", "/mnt/scratch/jfoley/robust04raw")), argp);

    StreamingStats parsingTime = new StreamingStats();
    StreamingStats stanfordTime = new StreamingStats();
    long numQueries = 0;

    int N = 500;
    int count = 0;
    //StreamingStats galagoTime = new StreamingStats();
    for (DocumentSplit documentSplit : documentSplits) {
      DocumentStreamParser parser = DocumentStreamParser.create(documentSplit, argp);

      Debouncer msg = new Debouncer(1000);
      long st, et;
      while (true) {
        st = System.nanoTime();
        Document doc = parser.nextDocument();
        if (doc == null) break;
        et = System.nanoTime();
        parsingTime.push((et - st) / 1e9);

        st = System.nanoTime();
        Annotation ann = new Annotation(doc.text);
        nlp.annotate(ann);

        List<CoreLabel> coreLabels = ann.get(CoreAnnotations.TokensAnnotation.class);
        for (int i = 0; i < coreLabels.size(); i++) {
          List<String> terms = new ArrayList<>();
          CoreLabel coreLabel = coreLabels.get(i);
          String nerTag = coreLabel.get(CoreAnnotations.NamedEntityTagAnnotation.class);
          if(Objects.equals(nerTag, "O")) continue;
          terms.add(coreLabel.get(CoreAnnotations.TextAnnotation.class));

          while(i+1 < coreLabels.size()) {
            if(Objects.equals(coreLabels.get(i + 1).get(CoreAnnotations.NamedEntityTagAnnotation.class), nerTag)) {
              terms.add(coreLabels.get(i+1).get(CoreAnnotations.TextAnnotation.class));
              i++;
            } else break;
          }

          if(!terms.isEmpty()) {
            numQueries++;
          }
        }
        et = System.nanoTime();
        stanfordTime.push((et - st) / 1e9);

        if(msg.ready()) {
          System.err.println(msg.estimate(count, N));
        }

        if(count++ >= N) break;
      }

      if(count >= N) break;
    }

    System.err.println("parsingTime: "+parsingTime);
    System.err.println("StanfordNLP.ner: "+stanfordTime);
    System.err.println("numQueries: "+numQueries);
  }
}
