package edu.umass.cs.jfoley.coop.conll.classifier;

import ciir.jfoley.chai.Timing;
import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.errors.FatalError;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.jfoley.coop.conll.SentenceIndexedToken;
import edu.umass.cs.jfoley.coop.conll.TermBasedIndexReader;

import java.io.IOException;
import java.util.*;

/**
 * @author jfoley
 */
public class RandomlyInitClassifier  {

  public static void main(String[] args) throws IOException {

    List<SparseBooleanFeatures> testa = new ArrayList<>();
    List<SparseBooleanFeatures> testb = new ArrayList<>();
    try (TermBasedIndexReader index = new TermBasedIndexReader(Directory.Read("./CoNLL03.eng.testa.run.stoken.index"))) {
      List<Integer> allTokens = IterableFns.intoList(index.tokenCorpus.keys());
      long pullTime = Timing.milliseconds(() -> {
        try {
          testa.addAll(index.pullFeatures(allTokens));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      System.out.println("TestA pull time: "+pullTime);
    }
    try (TermBasedIndexReader index = new TermBasedIndexReader(Directory.Read("./CoNLL03.eng.testb.run.stoken.index"))) {
      List<Integer> allTokens = IterableFns.intoList(index.tokenCorpus.keys());
      long pullTime = Timing.milliseconds(() -> {
        try {
          testb.addAll(index.pullFeatures(allTokens));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      System.out.println("TestB pull time: "+pullTime);
    }

    try (TermBasedIndexReader index = new TermBasedIndexReader(Directory.Read("./CoNLL03.eng.train.run.stoken.index"))) {

      IntList counts = new IntList(Arrays.asList(1,10,20,50,100,1000,2000));
      List<String> kinds = Arrays.asList("I-PER", "I-LOC", "I-ORG", "I-MISC");
      Random rand = new Random(13);

      for(String kind : kinds) {
        for (int count : counts) {

          // Prepare data
          List<Integer> positive = new IntList();
          List<Integer> negative = new IntList();

          while(positive.size() < count) {
            List<Integer> sentenceIds = Sample.randomIntegers(rand, 200, index.getSentenceCount());
            for (List<SentenceIndexedToken> tokens : index.pullSentences(sentenceIds)) {
              for (SentenceIndexedToken token : tokens) {
                Map<String, String> terms = token.getTerms();
                String ner = terms.get("true_ner");
                if (ner.equals(kind)) {
                  positive.add(token.tokenId);
                } else if(negative.size() < count*4) {
                  negative.add(token.tokenId);
                }
                if(positive.size() == count) break;
              }
              if(positive.size() == count) break;
            }
          }

          // Print info about prepared data:
          System.out.printf("%s\t%4d\t%5d\n", kind, positive.size(), negative.size());

          List<SparseBooleanFeatures> posF = new ArrayList<>();
          List<SparseBooleanFeatures> negF = new ArrayList<>();
          long pullPos = Timing.milliseconds(() -> {
            try {
              posF.addAll(index.pullFeatures(positive));
            } catch (IOException e) {
              throw new FatalError(e);
            }
          });
          long pullNeg = Timing.milliseconds(() -> {
            try {
              negF.addAll(index.pullFeatures(negative));
            } catch (IOException e) {
              throw new FatalError(e);
            }
          });
          System.out.println("Pull pos: " + pullPos + " pull neg: " + pullNeg);

          // train classifier:
          Classifier classifier = new PerceptronClassifier(index.classifiers.featuresAboveThreshold.size());
          double accuracy = classifier.train(posF, negF);
          System.out.printf("Training Accuracy: %3.1f%%\n", 100.0 * accuracy);
          System.out.printf("Number of features: %d\n", classifier.getComplexity());

        }
      }


    }
  }
}
