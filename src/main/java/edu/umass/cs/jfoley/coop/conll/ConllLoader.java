package edu.umass.cs.jfoley.coop.conll;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.fn.SinkFn;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.lang.LazyPtr;
import ciir.jfoley.chai.string.StrUtil;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.crf.CRFDatum;
import edu.stanford.nlp.ie.crf.CRFLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.umass.cs.ciir.waltz.coders.files.RunWriter;
import edu.umass.cs.jfoley.coop.coders.KryoCoder;
import edu.umass.cs.jfoley.coop.document.CoopDoc;
import edu.umass.cs.jfoley.coop.tokenization.StanfordNLPTokenizer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class ConllLoader {

  public static class CoNLLToken {
    final String word;
    final String pos;
    final String chunk;
    final String ner;

    public CoNLLToken(String word, String pos, String chunk, String ner) {
      this.word = word;
      this.pos = pos;
      this.chunk = chunk;
      this.ner = ner;
    }
    public CoNLLToken(String[] row) {
      this(row[0], row[1], row[2], row[3]);
      assert(row.length == 4);
    }
  }

  /**
   * This class instantiates the CoreNLP feature factories by loading their default classifier.
   * Neat trick, courtesy of Jiepu Jiang.
   */
  static LazyPtr<CRFClassifier<CoreLabel>> NERClassifierHack = new LazyPtr<>(() -> {
    try {
      CRFClassifier<CoreLabel> clz = CRFClassifier.getClassifier(
          IO.resourceStream("/edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz"));
      clz.flags.useTags = true;
      clz.flags.useWordTag = true;
      return clz;
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  });


  public static LazyPtr<StanfordCoreNLP> nlp = new LazyPtr<>(() -> {
    //List<String> annotators = Arrays.asList("tokenize", "cleanxml", "ssplit", "pos", "lemma");
    List<String> annotators = Arrays.asList("tokenize", "ssplit", "pos", "lemma", "ner");
    Properties props = new Properties();
    props.put("annotators", StrUtil.join(annotators, ","));
    props.put("split.eolonly", "true");
    props.put("tokenize.whitespace", "true");
    return new StanfordCoreNLP(props);
  });

  public static void makeDocument(String name, List<String> lines, SinkFn<CoopDoc> output) {
    int linesIndex = 0;
    if(lines.isEmpty()) return;
    if(lines.get(0).isEmpty()) linesIndex = 1;

    StringBuilder fakeText = new StringBuilder();
    IntList sentenceBreaks = new IntList();
    sentenceBreaks.add(0);
    List<String> words = new ArrayList<>();
    List<String> pos = new ArrayList<>();
    List<String> chunks = new ArrayList<>();
    List<String> ner = new ArrayList<>();
    for (int i = linesIndex; i < lines.size(); i++) {
      String line = lines.get(i);
      if(line.isEmpty()) {
        fakeText.append('\n');
        if(Objects.requireNonNull(ListFns.getLast(sentenceBreaks)) != words.size()) {
          sentenceBreaks.add(words.size());
        }
        continue;
      }
      CoNLLToken tok = new CoNLLToken(line.split("\\s+"));
      fakeText.append(tok.word).append(' ');
      words.add(tok.word);
      pos.add(tok.pos);
      chunks.add(tok.chunk);
      ner.add(tok.ner);
    }
    if(Objects.requireNonNull(ListFns.getLast(sentenceBreaks)) != words.size()) {
      sentenceBreaks.add(words.size());
    }

    CoopDoc document = new CoopDoc();
    document.setName(name);
    for (List<Integer> kv : ListFns.sliding(sentenceBreaks, 2)) {
      int begin = kv.get(0);
      int end = kv.get(1);
      if(end <= begin) {
        System.out.println("END <= BEGIN: "+begin+" >= "+end);
        continue;
      }
      document.addTag("true_sentence", begin, end);
    }
    document.setTerms("true_terms", words);
    document.setTerms("true_pos", pos);
    document.setTerms("true_chunks", chunks);
    document.setTerms("true_ner", ner);
    document.setRawText(fakeText.toString());

    Annotation ann = new Annotation(document.getRawText());
    long time = Timing.milliseconds(() -> {
      nlp.get().annotate(ann);
    });
    System.out.println("Annotation in " + time + "ms.");

    document.setTerms("tokens", StanfordNLPTokenizer.collectTerms(ann));
    document.setTerms("lemmas", StanfordNLPTokenizer.collectLemmas(ann));

    // Since the stanford models are TRAINED on CoNLL, anything we get here is not scientific.
    document.setTerms("overfit_pos", StanfordNLPTokenizer.collectPOS(ann));
    document.setTerms("overfit_ner", StanfordNLPTokenizer.collectNER(ann));

    // Now collect the crf features:
    CRFClassifier<CoreLabel> classifier = NERClassifierHack.get();

    List<Set<String>> tokenLevelFeatures = new ArrayList<>();

    long feature_xms = Timing.milliseconds(() -> {
      List<CoreLabel> tokens = ann.get(CoreAnnotations.TokensAnnotation.class);
      for (int i = 0; i < tokens.size(); i++) {
        CRFDatum<List<String>, CRFLabel> datum = classifier.makeDatum(tokens, i, classifier.featureFactories);
        HashSet<String> features = new HashSet<>();
        for (List<String> fs : datum.asFeatures()) {
          features.addAll(fs);
        }
        tokenLevelFeatures.add(new TreeSet<>(features));
      }
    });
    System.out.println("Feature Extract in "+feature_xms+"ms.");
    document.setTermLevelIndicators(tokenLevelFeatures);

    assert(document.getTags().containsKey("true_sentence"));
    assert(!document.getTerms().isEmpty());
    output.process(document);

  }

  public static void main(String[] args) throws IOException {
    Directory cdir = Directory.Read("/media/jfoley/flash/raw/conll2003");

    List<String> interestingChildren = Arrays.asList(
        "CoNLL03.eng.train",
        "CoNLL03.eng.testa",
        "CoNLL03.eng.testb"
    );

    int i=0;
    for (String interestingChild : interestingChildren) {
      try (RunWriter<CoopDoc> writer = new RunWriter<>(new KryoCoder<>(CoopDoc.class), new File(interestingChild+".run"))) {
        try (LinesIterable iter = LinesIterable.fromFile(cdir.child(interestingChild))) {
          List<String> withinDocument = new ArrayList<>();
          for (String line : iter) {
            if (line.startsWith("-DOCSTART-")) {
              System.out.println(interestingChild+" "+i++);
              makeDocument(interestingChild, withinDocument, writer);
              withinDocument.clear();
              continue;
            }
            withinDocument.add(line.trim());
          }
          makeDocument(interestingChild, withinDocument, writer);
        }
      }
    }


  }



}
