package uk.gov.nationalarchives.signcookies

import java.time.Instant

class TestTimeUtils extends TimeUtils {
  override def now(): Instant = Instant.ofEpochSecond(1632480554)
}
