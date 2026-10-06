package dev.yomu.app.sync
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
class SyncFailureTest {
 @Test fun permanentErrorsPauseAutomaticRetries() {
  for(code in listOf(401,403,409,413)) assertFalse(classifySyncFailure(SyncHttpException(code,"Failed")).retryable)
  assertTrue(classifySyncFailure(SyncHttpException(401,"Failed")).message.contains("reconnect"))
  assertFalse(SyncUiState("Failed",123,456,"Rejected",false,false).canRetryAutomatically())
 }
 @Test fun temporaryFailuresKeepRetrying() {
  for(code in listOf(408,429,500,503)) assertTrue(classifySyncFailure(SyncHttpException(code,"Failed")).retryable)
  assertTrue(classifySyncFailure(IOException("Offline")).retryable)
  assertTrue(SyncUiState("Failed",123,456,"Offline",true,false).canRetryAutomatically())
 }
}
