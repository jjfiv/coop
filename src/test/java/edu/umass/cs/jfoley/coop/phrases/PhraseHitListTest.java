package edu.umass.cs.jfoley.coop.phrases;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class PhraseHitListTest {

  @Test
  public void testFind() {
    PhraseHitList db = new PhraseHitList();
    db.add(0, 7, 1);
    db.add(10, 14, 2);
    db.add(0, 1, 3);

    assertEquals((List) Collections.emptyList(), db.find(7, 1));
    assertEquals((List) Collections.singletonList(1), db.find(6,1));
  }


}