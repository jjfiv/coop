package edu.umass.cs.jfoley.coop.phrases;

import ciir.jfoley.chai.collections.list.IntList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author jfoley
 */
public class PhraseDetectorTest {
  @Test
  public void testSimple() {
    PhraseDetector detector = new PhraseDetector(10);
    detector.addPattern(Arrays.asList(1, 2, 3));
    detector.addPattern(Arrays.asList(2, 2, 2));
    detector.addPattern(Arrays.asList(2, 2, 3));
    detector.addPattern(Arrays.asList(0, 0));

    int[] document = new int[] {
        1,2,3, // hit 0
        0,0, // hit 3
        2,2,3, // hit 5
        2,2,2,2,3, // overlapping hits 8,9,10
        2, // dead
        1,2,3 // hit 14
    };

    List<List<Integer>> matches = new ArrayList<>();
    List<Integer> positions = new ArrayList<>();

    detector.match(document, (pos, size) -> {
      IntList match = IntList.clone(document, pos, size);
      assertTrue(detector.matches(match));
      positions.add(pos);
      matches.add(match);
    });

    assertEquals(Arrays.asList(0,3,5,8,9,10,14), positions);

    assertEquals(Arrays.asList(
        Arrays.asList(1,2,3),
        Arrays.asList(0,0),
        Arrays.asList(2,2,3),
        Arrays.asList(2,2,2),
        Arrays.asList(2,2,2),
        Arrays.asList(2,2,3),
        Arrays.asList(1,2,3)
    ), matches);
  }

}