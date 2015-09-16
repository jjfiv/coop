package edu.umass.cs.jfoley.coop.bills;

import ciir.jfoley.chai.IntMath;
import ciir.jfoley.chai.io.Directory;
import ciir.jfoley.chai.time.Debouncer;
import org.lemurproject.galago.core.parse.TagTokenizer;
import org.lemurproject.galago.utility.StringPooler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jfoley
 */
public class ExtractNames {
  public static void main(String[] args) throws IOException {
    IntCoopIndex index = new IntCoopIndex(new Directory("dbpedia.ints"));

    TagTokenizer tokenizer = new TagTokenizer();
    StringPooler.disable();

    System.err.println("Total: "+index.names.size());
    int count = IntMath.fromLong(index.names.size());

    Debouncer msg = new Debouncer(3000);
    int i = 0;
    List<List<String>> tokenizedNames = new ArrayList<>(count);
    for (String name : index.names.values()) {
      i++;
      String text = name.replace('_', ' ');
      tokenizedNames.add(tokenizer.tokenize(text).terms);
      if(msg.ready()) {
        System.out.println(msg.estimate(i, count));
      }
    }


  }
}
