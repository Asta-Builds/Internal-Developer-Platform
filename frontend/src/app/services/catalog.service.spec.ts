import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogService, FeatureFlag } from './catalog.service';

describe('CatalogService', () => {
  let service: CatalogService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CatalogService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CatalogService);
  });

  it('initialises the telemetry signal with cluster defaults', () => {
    expect(service.telemetrySignal().totalPods).toBe(16);
    expect(service.telemetrySignal().healthyPods).toBe(16);
  });

  it('exposes the default traffic mode as NORMAL', () => {
    expect(service.trafficModeSignal()).toBe('NORMAL');
  });

  describe('evaluateBatchCanary', () => {
    it('evaluates every user against the flag threshold', () => {
      service.featureFlagsSignal.set([
        { key: 'PAY_V2', enabled: true, rolloutPercent: 50 } as FeatureFlag,
      ]);

      const result = service.evaluateBatchCanary('PAY_V2', 100);

      expect(result.key).toBe('PAY_V2');
      expect(result.threshold).toBe(50);
      expect(result.totalUsers).toBe(100);
      expect(result.enabledCount + result.disabledCount).toBe(100);
      expect(result.enabledPercentage).toBe(Math.round((result.enabledCount / 100) * 100));
    });

    it('samples the first ten users with deterministic buckets', () => {
      const result = service.evaluateBatchCanary('PAY_V2', 100);

      expect(result.samples).toHaveLength(10);
      const again = service.evaluateBatchCanary('PAY_V2', 100);
      expect(again.samples).toEqual(result.samples);
      expect(again.enabledCount).toBe(result.enabledCount);
    });

    it('falls back to a 50% disabled default flag when the key is unknown', () => {
      const result = service.evaluateBatchCanary('UNKNOWN_KEY', 40);

      expect(result.threshold).toBe(50);
      expect(result.totalUsers).toBe(40);
    });

    it('forces the threshold to zero when the flag is disabled', () => {
      service.featureFlagsSignal.set([
        { key: 'OFF_V1', enabled: false, rolloutPercent: 90 } as FeatureFlag,
      ]);

      const result = service.evaluateBatchCanary('OFF_V1', 50);

      expect(result.threshold).toBe(0);
      expect(result.enabledCount).toBe(0);
      expect(result.enabledPercentage).toBe(0);
    });
  });

  it('setTrafficMode flips the generator profile', () => {
    service.setTrafficMode('CHAOS_LATENCY');

    expect(service.trafficModeSignal()).toBe('CHAOS_LATENCY');
  });
});
