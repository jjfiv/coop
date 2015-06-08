package edu.umass.cs.jfoley.coop.bdaat;

import edu.umass.cs.jfoley.coop.index.Gettysburg;
import org.junit.Test;
import org.lemurproject.galago.core.index.mem.MemoryIndex;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.core.retrieval.LocalRetrieval;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.processing.RankedDocumentModel;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.tokenize.Tokenizer;
import org.lemurproject.galago.utility.Parameters;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class BDAATProcessingModelTest {

  @Test
  public void testNotLossy() throws Exception {
    Tokenizer tok = new TagTokenizer();
    MemoryIndex index = new MemoryIndex();
    List<String> paragraphs = Gettysburg.getParagraphs();
    for (int i = 0; i < paragraphs.size(); i++) {
      String s = paragraphs.get(i);
      Document doc = new Document();
      doc.text = s;
      doc.name = "p"+i;
      tok.tokenize(doc);
      index.process(doc);
    }

    Node testQuery = new Node("combine");
    testQuery.addChild(Node.Text("nation"));
    testQuery.addChild(Node.Text("liberty"));
    testQuery.addChild(Node.Text("of"));
    testQuery.addChild(Node.Text("nobly"));
    testQuery.addChild(Node.Text("government"));

    LocalRetrieval ret = new LocalRetrieval(index);

    Results daatResults = ret.transformAndExecuteQuery(testQuery, Parameters.parseArray(
        "processingModel", RankedDocumentModel.class.getName()
    ));
    Results bdaatResults = ret.transformAndExecuteQuery(testQuery, Parameters.parseArray(
        "processingModel", BDAATProcessingModel.class.getName(),
        "batchSize", 2 // score 2 terms separately
    ));

    assertEquals(daatResults.scoredDocuments, bdaatResults.scoredDocuments);

  }

}