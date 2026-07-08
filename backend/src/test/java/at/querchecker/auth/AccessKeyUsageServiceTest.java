package at.querchecker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessKeyUsageServiceTest {

  @Mock
  AccessKeyUsageRepository repo;

  @InjectMocks
  AccessKeyUsageService service;

  // --- checkQuota ---

  @Test
  void checkQuota_underLimit_doesNotThrow() {
    when(repo.findRemainingToday(1L)).thenReturn(3);
    assertThatCode(() -> service.checkQuota(1L)).doesNotThrowAnyException();
  }

  @Test
  void checkQuota_atLimit_throws() {
    when(repo.findRemainingToday(1L)).thenReturn(0);
    assertThatThrownBy(() -> service.checkQuota(1L)).isInstanceOf(QuotaExceededException.class);
  }

  @Test
  void checkQuota_overLimit_throws() {
    when(repo.findRemainingToday(1L)).thenReturn(-2);
    assertThatThrownBy(() -> service.checkQuota(1L)).isInstanceOf(QuotaExceededException.class);
  }

  @Test
  void checkQuota_nullAccessKeyId_skipsQuery() {
    service.checkQuota(null); // SUPERUSER/GUEST
    verifyNoInteractions(repo);
  }

  @Test
  void checkQuota_unknownKey_nullRemaining_doesNotThrow() {
    when(repo.findRemainingToday(99L)).thenReturn(null);
    assertThatCode(() -> service.checkQuota(99L)).doesNotThrowAnyException();
  }

  // --- consume ---

  @Test
  void consume_incrementsUsage() {
    service.consume(1L);
    verify(repo).incrementToday(1L);
  }

  @Test
  void consume_nullAccessKeyId_skips() {
    service.consume(null); // SUPERUSER/GUEST
    verifyNoInteractions(repo);
  }

  // --- remainingToday ---

  @Test
  void remainingToday_returnsQueryValue() {
    when(repo.findRemainingToday(1L)).thenReturn(7);
    assertThat(service.remainingToday(1L)).isEqualTo(7);
  }

  @Test
  void remainingToday_nullAccessKeyId_returnsNull() {
    assertThat(service.remainingToday(null)).isNull();
    verifyNoInteractions(repo);
  }
}
