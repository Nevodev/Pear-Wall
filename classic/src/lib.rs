use jni::objects::{JByteArray, JClass};
use jni::sys::{jfloat, jlong};
use jni::JNIEnv;
use biquad::{Biquad, Coefficients, DirectForm1, Hertz, Type, Q_BUTTERWORTH_F32};

const SILENCE_DB: f32 = -72.0;
const DEFAULT_REPORT_INTERVAL_SECONDS: f32 = 0.05;
const MIN_REPORT_INTERVAL_SECONDS: f32 = 0.001;
const MAX_REPORT_INTERVAL_SECONDS: f32 = 0.25;

type LowPass = DirectForm1<f32>;

fn low_pass(cutoff_hz: f32, sample_rate_hz: f32) -> LowPass {
    let coefficients = Coefficients::<f32>::from_params(
        Type::LowPass,
        Hertz::from_hz(sample_rate_hz).expect("valid sample rate"),
        Hertz::from_hz(cutoff_hz).expect("valid cutoff"),
        Q_BUTTERWORTH_F32,
    )
    .expect("valid low-pass coefficients");
    DirectForm1::new(coefficients)
}

struct TransientDetector {
    sample_rate: f32,
    low: LowPass,
    bass_top: LowPass,
    reference_top: LowPass,
    bass_power: f64,
    reference_power: f64,
    previous_db: f32,
}

impl Default for TransientDetector {
    fn default() -> Self {
        Self {
            sample_rate: 0.0,
            low: low_pass(25.0, 48_000.0),
            bass_top: low_pass(190.0, 48_000.0),
            reference_top: low_pass(760.0, 48_000.0),
            bass_power: 0.0,
            reference_power: 0.0,
            previous_db: 0.0,
        }
    }
}

impl TransientDetector {
    fn process(&mut self, waveform: &[i8], sample_rate: f32) -> f32 {
        if sample_rate != self.sample_rate {
            self.sample_rate = sample_rate;
            self.low = low_pass(25.0, sample_rate);
            self.bass_top = low_pass(190.0, sample_rate);
            self.reference_top = low_pass(760.0, sample_rate);
            self.bass_power = 0.0;
            self.reference_power = 0.0;
        }

        let mut strongest: f32 = 0.0;
        for raw in waveform {
            let sample = (*raw as f32 + 128.0) / 128.0 - 1.0;
            let low = self.low.run(sample);
            let bass_top = self.bass_top.run(sample);
            let bass = bass_top - low;
            let reference = self.reference_top.run(sample) - bass_top;
            self.bass_power = envelope(self.bass_power, (bass * bass) as f64, 0.006, 0.045, sample_rate);
            self.reference_power = envelope(
                self.reference_power,
                (reference * reference) as f64,
                0.006,
                0.045,
                sample_rate,
            );
            let bass_db = power_to_db(self.bass_power as f32);
            let reference_db = power_to_db(self.reference_power as f32);
            let rise = (bass_db - self.previous_db).max(0.0);
            self.previous_db = bass_db;
            let response = smooth_range(bass_db - reference_db, 0.0, 8.0);
            let attack = smooth_range(rise, 7.0, 14.0);
            strongest = strongest.max(response * attack);
        }
        strongest * 0.82
    }

}

struct Analyzer {
    recent: [f32; 4],
    recent_write: usize,
    target: f32,
    power: f32,
    bass_baseline_db: f32,
    reference_baseline_db: f32,
    previous_bass_db: f32,
    sharp_attack: f32,
    response_history: [f32; 3],
    history_write: usize,
    history_count: usize,
    initialized: bool,
    transient_detector: TransientDetector,
    transient_response: f32,
    previous_waveform_ns: jlong,
    previous_fft_ns: jlong,
}

impl Default for Analyzer {
    fn default() -> Self {
        Self {
            recent: [0.0; 4],
            recent_write: 0,
            target: 0.0,
            power: 0.0,
            bass_baseline_db: SILENCE_DB,
            reference_baseline_db: SILENCE_DB,
            previous_bass_db: SILENCE_DB,
            sharp_attack: 0.0,
            response_history: [0.0; 3],
            history_write: 0,
            history_count: 0,
            initialized: false,
            transient_detector: TransientDetector::default(),
            transient_response: 0.0,
            previous_waveform_ns: 0,
            previous_fft_ns: 0,
        }
    }
}

impl Analyzer {
    fn process_waveform(&mut self, waveform: &[i8], sample_rate: f32, timestamp_ns: jlong) {
        if waveform.is_empty() || sample_rate <= 0.0 {
            return;
        }
        let elapsed = elapsed_seconds(self.previous_waveform_ns, timestamp_ns);
        self.previous_waveform_ns = timestamp_ns;
        self.transient_response = self
            .transient_detector
            .process(waveform, sample_rate)
            .max(self.transient_response * decay(elapsed, 0.063));
    }

    fn process_fft(&mut self, fft: &[i8], sample_rate: f32, timestamp_ns: jlong) -> f32 {
        if fft.len() < 8 || sample_rate <= 0.0 {
            return 0.0;
        }
        let elapsed = elapsed_seconds(self.previous_fft_ns, timestamp_ns);
        self.previous_fft_ns = timestamp_ns;

        let low_bass = band_power(fft, sample_rate, 30.0, 105.0);
        let bass_note = band_power(fft, sample_rate, 75.0, 155.0);
        let upper_bass = band_power(fft, sample_rate, 145.0, 210.0);
        let low_mid = band_power(fft, sample_rate, 155.0, 380.0);
        let mid = band_power(fft, sample_rate, 380.0, 760.0);
        let core_bass = low_bass.max(bass_note * 0.9);
        let supported_upper = upper_bass.min(core_bass * 1.35);
        let bass_db = power_to_db(core_bass + supported_upper * 0.2);
        let reference_db = power_to_db((low_mid * 2.3).max(mid * 1.6));

        if !self.initialized {
            self.bass_baseline_db = SILENCE_DB.max(bass_db - 7.0);
            self.reference_baseline_db = reference_db;
            self.previous_bass_db = bass_db;
            self.initialized = true;
            return 0.0;
        }

        let frame_rise = (bass_db - self.previous_bass_db).max(0.0);
        self.previous_bass_db = bass_db;
        let rise = (bass_db - self.bass_baseline_db).max(0.0);
        let reference_rise = (reference_db - self.reference_baseline_db).max(0.0);
        let dominance = smooth_range(bass_db - reference_db, 0.0, 8.0);
        let sharp_target = if bass_db >= -45.0 { smooth_range(frame_rise, 7.0, 14.0) } else { 0.0 };
        self.sharp_attack = sharp_target.max(self.sharp_attack * decay(elapsed, 0.09));

        let harmonic_confidence = smooth_range(dominance, 0.12, 0.3) * self.sharp_attack * 0.9;
        let bass_confidence = dominance.max(harmonic_confidence);
        let rejection = 0.7 - dominance * 0.35;
        let bass_only_rise = rise - reference_rise * rejection;

        self.bass_baseline_db = follow_baseline(self.bass_baseline_db, bass_db, 1.1, 0.16, elapsed);
        self.reference_baseline_db = follow_baseline(self.reference_baseline_db, reference_db, 1.1, 0.16, elapsed);

        let level = smooth_range(bass_db, -50.0, -18.0);
        let transient = smooth_range(bass_only_rise, 1.2, 7.0);
        let unprocessed = level * bass_confidence * (0.1 + 0.9 * transient);
        let immediate = self.sharp_attack >= 0.72 && transient >= 0.32;
        let confirmed = self.confirm_response(unprocessed, immediate);

        self.recent[self.recent_write] = confirmed;
        self.recent_write = (self.recent_write + 1) % self.recent.len();
        let ramp = [0.1, 0.2, 0.3, 0.4];
        let weighted: f32 = (0..4)
            .map(|i| self.recent[(self.recent_write + i) % 4] * ramp[i])
            .sum();
        self.target = weighted.max(self.target * decay(elapsed, 1.0));
        self.power += (self.target - self.power) * (1.0 - decay(elapsed, 0.07));
        self.power.max(self.transient_response * level).clamp(0.0, 1.0)
    }

    fn confirm_response(&mut self, value: f32, immediate: bool) -> f32 {
        self.response_history[self.history_write] = value;
        self.history_write = (self.history_write + 1) % 3;
        self.history_count = (self.history_count + 1).min(3);
        if immediate {
            return value;
        }
        if self.history_count == 1 {
            return 0.0;
        }
        if self.history_count == 2 {
            return self.response_history[0].min(self.response_history[1]);
        }
        let a = self.response_history[0];
        let b = self.response_history[1];
        let c = self.response_history[2];
        a + b + c - a.min(b).min(c) - a.max(b).max(c)
    }
}

fn band_power(fft: &[i8], sample_rate: f32, minimum: f32, maximum: f32) -> f32 {
    let first = 1.max((minimum * fft.len() as f32 / sample_rate).ceil() as usize);
    let last = (fft.len() / 2 - 1).min((maximum * fft.len() as f32 / sample_rate).floor() as usize);
    if last < first {
        return 0.0;
    }
    let mut power = 0.0;
    for bin in first..=last {
        let real = fft[bin * 2] as f32 / 128.0;
        let imaginary = fft[bin * 2 + 1] as f32 / 128.0;
        power += real * real + imaginary * imaginary;
    }
    power / (last - first + 1) as f32
}

fn follow_baseline(current: f32, target: f32, attack: f32, release: f32, elapsed: f32) -> f32 {
    let time_constant = if target > current { attack } else { release };
    (current + (target - current) * (1.0 - decay(elapsed, time_constant))).max(SILENCE_DB)
}

fn elapsed_seconds(previous: jlong, current: jlong) -> f32 {
    if previous <= 0 || current <= previous {
        return DEFAULT_REPORT_INTERVAL_SECONDS;
    }
    ((current - previous) as f32 / 1_000_000_000.0).clamp(MIN_REPORT_INTERVAL_SECONDS, MAX_REPORT_INTERVAL_SECONDS)
}

fn decay(elapsed: f32, time_constant: f32) -> f32 {
    (-elapsed / time_constant).exp()
}

fn envelope(current: f64, target: f64, attack: f64, release: f64, sample_rate: f32) -> f64 {
    let seconds = if target > current { attack } else { release };
    let mix = 1.0 - (-1.0 / (sample_rate as f64 * seconds)).exp();
    current + (target - current) * mix
}

fn power_to_db(power: f32) -> f32 {
    10.0 * power.max(1e-12).log10()
}

fn smooth_range(value: f32, floor: f32, ceiling: f32) -> f32 {
    let x = ((value - floor) / (ceiling - floor)).clamp(0.0, 1.0);
    x * x * (3.0 - 2.0 * x)
}

fn analyzer_from_ptr<'a>(ptr: jlong) -> Option<&'a mut Analyzer> {
    if ptr == 0 {
        None
    } else {
        Some(unsafe { &mut *(ptr as *mut Analyzer) })
    }
}

#[no_mangle]
pub extern "system" fn Java_com_nevoit_pearwall_audio_ClassicAudioAnalyzer_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    Box::into_raw(Box::new(Analyzer::default())) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_nevoit_pearwall_audio_ClassicAudioAnalyzer_nativeProcessWaveform(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    waveform: JByteArray,
    sample_rate_hz: jfloat,
    timestamp_ns: jlong,
) {
    let Some(analyzer) = analyzer_from_ptr(handle) else { return };
    let Ok(bytes) = env.convert_byte_array(waveform) else { return };
    let samples: Vec<i8> = bytes.into_iter().map(|value| value as i8).collect();
    analyzer.process_waveform(&samples, sample_rate_hz, timestamp_ns);
}

#[no_mangle]
pub extern "system" fn Java_com_nevoit_pearwall_audio_ClassicAudioAnalyzer_nativeProcessFft(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    fft: JByteArray,
    sample_rate_hz: jfloat,
    timestamp_ns: jlong,
) -> jfloat {
    let Some(analyzer) = analyzer_from_ptr(handle) else { return 0.0 };
    let Ok(bytes) = env.convert_byte_array(fft) else { return 0.0 };
    let samples: Vec<i8> = bytes.into_iter().map(|value| value as i8).collect();
    analyzer.process_fft(&samples, sample_rate_hz, timestamp_ns)
}

#[no_mangle]
pub extern "system" fn Java_com_nevoit_pearwall_audio_ClassicAudioAnalyzer_nativeReset(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(analyzer) = analyzer_from_ptr(handle) {
        *analyzer = Analyzer::default();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_nevoit_pearwall_audio_ClassicAudioAnalyzer_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { drop(Box::from_raw(handle as *mut Analyzer)); }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_input_is_safe() {
        let mut analyzer = Analyzer::default();
        analyzer.process_waveform(&[], 48_000.0, 1);
        assert_eq!(analyzer.process_fft(&[], 48_000.0, 2), 0.0);
    }

    #[test]
    fn decay_is_time_based() {
        assert!(decay(0.1, 0.1) < 0.4);
    }
}
