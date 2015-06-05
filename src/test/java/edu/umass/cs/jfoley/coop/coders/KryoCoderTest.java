package edu.umass.cs.jfoley.coop.coders;

import ciir.jfoley.chai.random.Sample;
import edu.umass.cs.ciir.waltz.coders.Coder;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author jfoley
 */
public class KryoCoderTest {

  @Test
  public void testSimpleKryoCoder() {
    Coder<String> coder = new KryoCoder<>(String.class);
    List<String> data = Sample.strings(new Random(), 1000);
    for (String testStr : data) {
      assertEquals(testStr, coder.read(coder.write(testStr)));
    }
  }

}