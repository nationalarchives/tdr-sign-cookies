package uk.gov.nationalarchives.signcookies

import java.time.Instant

class TimeUtilsImpl extends TimeUtils {
  override def now(): Instant = Instant.now()
}
