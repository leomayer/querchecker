package at.querchecker.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import at.querchecker.api.config.FreeLimitPeriod;
import at.querchecker.api.config.LimitUnit;
import at.querchecker.api.config.ProviderConfig;
import at.querchecker.api.config.ProviderProperties;
import at.querchecker.api.entity.Provider;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

  @Mock
  ApiUsageLogService usageLogService;

  @Mock
  ProviderProperties providerProperties;

  @InjectMocks
  QuotaService service;

  // --- Periodberechnungen ---

  @Test
  void getPeriodStart_daily_returnsTodayMidnight() {
    LocalDate periodStart = service.getPeriodStart(FreeLimitPeriod.DAILY, 1);
    assertThat(periodStart).isEqualTo(LocalDate.now());
  }

  @Test
  void getPeriodStart_monthly_returnsCurrentMonthStart_whenPeriodStartDay1() {
    LocalDate periodStart = service.getPeriodStart(FreeLimitPeriod.MONTHLY, 1);
    assertThat(periodStart).isEqualTo(LocalDate.now().withDayOfMonth(1));
  }

  @Test
  void getPeriodStart_monthly_returnsPreviousMonth_whenTodayBeforePeriodStartDay() {
    // Heute ist der 10., Periode startet am 15. → voriger Monat
    LocalDate today = LocalDate.of(2026, 3, 10);
    LocalDate expected = LocalDate.of(2026, 2, 15);
    assertThat(service.getPeriodStart(FreeLimitPeriod.MONTHLY, 15, today)).isEqualTo(expected);
  }

  @Test
  void getPeriodStart_monthly_returnsCurrentMonth_whenTodayIsPeriodStartDay() {
    LocalDate today = LocalDate.of(2026, 3, 15);
    LocalDate expected = LocalDate.of(2026, 3, 15);
    assertThat(service.getPeriodStart(FreeLimitPeriod.MONTHLY, 15, today)).isEqualTo(expected);
  }

  // --- checkQuota ---

  @Test
  void checkQuota_returnsOk_whenUnderLimit() {
    givenBraveConfig(1000, 80);
    when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any())).thenReturn(
      500L
    );

    assertThat(service.checkQuota(Provider.BRAVE)).isEqualTo(QuotaStatus.OK);
  }

  @Test
  void checkQuota_returnsQuotaExceeded_whenAtLimit() {
    givenBraveConfig(1000, 80);
    when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any())).thenReturn(
      1000L
    );

    assertThat(service.checkQuota(Provider.BRAVE)).isEqualTo(QuotaStatus.QUOTA_EXCEEDED);
  }

  @Test
  void checkQuota_returnsQuotaExceeded_whenOverLimit() {
    givenBraveConfig(1000, 80);
    when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any())).thenReturn(
      1050L
    );

    assertThat(service.checkQuota(Provider.BRAVE)).isEqualTo(QuotaStatus.QUOTA_EXCEEDED);
  }

  // --- isWarningThreshold ---

  @Test
  void isWarningThreshold_returnsFalse_whenBelow80Percent() {
    givenBraveConfig(1000, 80);
    when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any())).thenReturn(
      799L
    );

    assertThat(service.isWarningThreshold(Provider.BRAVE)).isFalse();
  }

  @Test
  void isWarningThreshold_returnsTrue_whenAtExact80Percent() {
    givenBraveConfig(1000, 80);
    when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any())).thenReturn(
      800L
    );

    assertThat(service.isWarningThreshold(Provider.BRAVE)).isTrue();
  }

  @Test
  void isWarningThreshold_returnsTrue_whenAbove80Percent() {
    givenBraveConfig(1000, 80);
    when(usageLogService.countByProviderAndPeriod(eq(Provider.BRAVE), any(), any())).thenReturn(
      950L
    );

    assertThat(service.isWarningThreshold(Provider.BRAVE)).isTrue();
  }

  // --- ICECAT (kein Kontingent) ---

  @Test
  void checkQuota_returnsOk_forIcecat() {
    assertThat(service.checkQuota(Provider.ICECAT)).isEqualTo(QuotaStatus.OK);
  }

  @Test
  void isWarningThreshold_returnsFalse_forIcecat() {
    assertThat(service.isWarningThreshold(Provider.ICECAT)).isFalse();
  }

  private void givenBraveConfig(int freeLimit, int alertAtPercent) {
    ProviderConfig config = new ProviderConfig();
    config.setFreeLimit(freeLimit);
    config.setFreeLimitPeriod(FreeLimitPeriod.MONTHLY);
    config.setPeriodStartDay(1);
    config.setLimitUnit(LimitUnit.REQUESTS);
    config.setAlertAtPercent(alertAtPercent);
    when(providerProperties.getProvider(Provider.BRAVE)).thenReturn(config);
  }
}
