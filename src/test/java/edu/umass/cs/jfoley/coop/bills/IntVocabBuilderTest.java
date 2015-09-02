package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.collections.list.IntList;
import ciir.jfoley.chai.collections.util.ListFns;
import ciir.jfoley.chai.io.TemporaryDirectory;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class IntVocabBuilderTest {

  @Test
  public void pullDocuments() throws IOException {
    List<String> documents = new ArrayList<>();
    documents.add("this is the time for all good men to come to the party");
    documents.add("the quick brown fox jumped over the lazy dog");
    documents.add("any dog would want to go to a party");

    List<List<String>> docTerms = ListFns.map(documents,
        (doc) -> Arrays.asList(doc.split("\\s+")));

    List<String> corpus = new ArrayList<>();
    for (List<String> docTerm : docTerms) {
      corpus.addAll(docTerm);
    }

    Set<String> uniqueTerms = new TreeSet<>(corpus);

    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      try (IntVocabBuilder.IntVocabWriter writer = new IntVocabBuilder.IntVocabWriter(tmpdir)) {

        for (int i = 0; i < docTerms.size(); i++) {
          List<String> doc = docTerms.get(i);
          writer.process("doc"+i, doc);
        }
      }

      try (IntCoopIndex reader = new IntCoopIndex(tmpdir)) {
        assertEquals(uniqueTerms.size(), reader.getCorpus().getNumTerms());

        for (int i = 0; i < docTerms.size(); i++) {
          List<String> doc1t = docTerms.get(i);

          IntList doc1ids = new IntList();
          for (String term : doc1t) {
            doc1ids.add(reader.getTermId(term));
          }

          assertEquals(doc1ids, new IntList(reader.getCorpus().getDocument(reader.names.getReverse("doc"+i))));
          assertEquals(doc1t, reader.translateToTerms(doc1ids));
        }

        assertEquals(Arrays.asList("time", "for", "all", "good", "men"), reader.translateToTerms(reader.getCorpus().getSlice(0, 3, 5)));
        assertEquals("any", reader.vocab.getForward(reader.getCorpus().getTerm(2, 0)));
        assertEquals("dog", reader.vocab.getForward(reader.getCorpus().getTerm(2, 1)));
      }
    }
  }


}
