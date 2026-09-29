package com.eh8s.eh8s.service;

import com.eh8s.eh8s.database.jooq.eh8s.tables.pojos.SppMemberScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Off-chain SPP share math for Noche Oscura Q2.
 */
class BandServiceTest {

  @Test
  void nocheOscuraSharesSumTo10000Bps() {
    List<SppMemberScore> scores = new ArrayList<>();
    scores.add(score(95));
    scores.add(score(88));
    scores.add(score(50));
    scores.add(score(17));
    BandService.applyShareBps(scores);
    assertEquals(3800, scores.get(0).getShareBps());
    assertEquals(3520, scores.get(1).getShareBps());
    assertEquals(2000, scores.get(2).getShareBps());
    assertEquals(680, scores.get(3).getShareBps());
    assertEquals(10_000, scores.stream().mapToInt(SppMemberScore::getShareBps).sum());
  }

  private static SppMemberScore score(int total) {
    SppMemberScore row = new SppMemberScore();
    row.setTotalPoints(total);
    return row;
  }
}
