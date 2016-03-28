package edu.umass.cs.jfoley.coop.util;

import ciir.jfoley.chai.collections.util.IterableFns;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.time.Debouncer;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author jfoley
 */
public class DownloadTwitter {
  public static void main(String[] args) throws IOException, TwitterException, InterruptedException {
    ConfigurationBuilder builder = new ConfigurationBuilder();
    builder.setJSONStoreEnabled(true);
    TwitterFactory factory = new TwitterFactory(builder.build());
    Twitter twitter = factory.getInstance();

    long total = 3274089;
    long completed = 0;
    try (PrintWriter output = IO.openPrintWriter("/mnt/scratch3/jfoley/signal-news/tweets.jsonl.gz")) {
      Debouncer msg = new Debouncer();
      long[] ids = new long[100];
      try (LinesIterable tweets = LinesIterable.fromFile("/mnt/scratch3/jfoley/signal-news/tweet_data.txt.gz")) {
        for (List<String> t100 : IterableFns.batches(tweets, 100)) {
          if(ids.length != t100.size()) {
            ids = new long[t100.size()];
          }
          for (int i = 0; i < t100.size(); i++) {
            String s = t100.get(i);
            ids[i] = Long.parseLong(s);
          }

          while(true) {
            try {
              // fetch
              ResponseList<Status> lookup = twitter.lookup(ids);
              for (Status status : lookup) {
                String rawJSON = TwitterObjectFactory.getRawJSON(status);
                output.println(rawJSON);
              }
              RateLimitStatus rateLimitStatus = lookup.getRateLimitStatus();
              if (rateLimitStatus.getRemaining() == 0) {
                try {
                  System.err.println("Rate Limit: Sleeping for: " + rateLimitStatus.getSecondsUntilReset()+" seconds");
                  Thread.sleep(1000 * (rateLimitStatus.getSecondsUntilReset()+1));
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }
              break; // done if we get here, happily.

            } catch (TwitterException twe) {
              if (twe.getErrorCode() == 88) { // rate limit exceeded
                int sleepTime = twe.getRateLimitStatus().getResetTimeInSeconds()+1;
                System.err.println("# Rate Limit Exceeded: Sleeping for: "+sleepTime+" seconds");
                Thread.sleep(sleepTime*1000);
                continue;
              } else {
                throw new RuntimeException(twe);
              }
            }
          }

          completed += ids.length;
          if(msg.ready()) {
            System.err.println(msg.estimate(completed, total));
          }
        }

      }
    }

  }
}
