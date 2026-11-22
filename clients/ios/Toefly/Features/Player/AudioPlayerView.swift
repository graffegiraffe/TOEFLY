import AVFoundation
import Observation
import SwiftUI

/// Playback of one clip with `AVPlayer`: progress, the end of the first play, replays and the speed.
@MainActor
@Observable
final class AudioController {
    enum Phase: Equatable { case idle, loading, playing, paused, finished, failed }

    private(set) var phase: Phase = .idle
    private(set) var position: Double = 0
    private(set) var duration: Double = 0
    /// Replays, speed and seeking open after the first full play (docs/07-ux-ui.md §3.7, Practice).
    private(set) var heardOnce = false
    var rate: Float = 1 {
        didSet {
            if phase == .playing {
                player?.rate = rate
            }
        }
    }

    var onFinished: () -> Void = {}
    var onPlayingChange: (Bool) -> Void = { _ in }

    private var player: AVPlayer?
    private var url: URL?
    private var observers: [NSObjectProtocol] = []
    private var timeObserver: Any?
    private var statusObservation: NSKeyValueObservation?

    func load(_ url: URL?, heard: Bool) {
        guard url != self.url else { return }
        stop()
        self.url = url
        heardOnce = heard
        position = 0
        duration = 0
        guard let url else {
            phase = .failed
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        self.player = player
        phase = .loading
        timeObserver = player
            .addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 4), queue: .main) { [weak self] time in
                MainActor.assumeIsolated {
                    self?.position = time.seconds
                    if let seconds = player.currentItem?.duration.seconds, seconds.isFinite {
                        self?.duration = seconds
                    }
                }
            }
        observers.append(NotificationCenter.default.addObserver(
            forName: AVPlayerItem.didPlayToEndTimeNotification,
            object: item,
            queue: .main,
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.finished() }
        })
        statusObservation = item.observe(\.status) { [weak self] item, _ in
            let failed = item.status == .failed
            let seconds = item.duration.seconds
            Task { @MainActor in
                if seconds.isFinite, seconds > 0 {
                    self?.duration = seconds
                }
                if failed {
                    self?.phase = .failed
                    self?.onPlayingChange(false)
                }
            }
        }
    }

    func play() {
        guard let player else { return }
        if phase == .finished {
            player.seek(to: .zero)
        }
        try? AVAudioSession.sharedInstance().setActive(true)
        player.playImmediately(atRate: rate)
        phase = .playing
        onPlayingChange(true)
    }

    func pause() {
        player?.pause()
        if phase == .playing {
            phase = .paused
            onPlayingChange(false)
        }
    }

    func seek(to seconds: Double) {
        player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
        position = seconds
    }

    private func finished() {
        phase = .finished
        position = duration
        onPlayingChange(false)
        if !heardOnce {
            heardOnce = true
            onFinished()
        }
    }

    func stop() {
        player?.pause()
        if let timeObserver {
            player?.removeTimeObserver(timeObserver)
        }
        observers.forEach(NotificationCenter.default.removeObserver)
        observers = []
        timeObserver = nil
        statusObservation = nil
        player = nil
        url = nil
        if phase == .playing {
            onPlayingChange(false)
        }
        phase = .idle
    }
}

/// `AudioPlayer` (docs/07-ux-ui.md §3.7) on `surface-task`: the first play starts by itself and cannot be paused or
/// sought, as on the exam; then Replay, seeking and the speed open, and every replay is counted.
struct AudioPlayerView: View {
    let url: URL?
    let autoplay: Bool
    let paused: Bool
    let onFinished: () -> Void
    let onReplay: () -> Void
    let onPlayingChange: (Bool) -> Void

    @State private var audio = AudioController()

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            if audio.phase == .failed {
                Label("Audio is unavailable right now. Try again later.", systemImage: "speaker.slash")
                    .font(.toeflyBody)
                    .foregroundStyle(Color.toefly.danger)
                    .accessibilityIdentifier("audio-unavailable")
            } else {
                HStack(spacing: Space.s3) {
                    playButton
                    VStack(alignment: .leading, spacing: Space.s1) {
                        if audio.heardOnce {
                            Slider(
                                value: Binding(get: { audio.position }, set: { audio.seek(to: $0) }),
                                in: 0 ... max(audio.duration, 1),
                            )
                            .accessibilityLabel("Audio position")
                        } else {
                            ProgressView(
                                value: min(audio.position, max(audio.duration, 1)),
                                total: max(audio.duration, 1),
                            )
                            .tint(Color.toefly.primary)
                            .accessibilityLabel("Audio progress")
                        }
                        Text(timeText)
                            .font(.toeflyCaption.monospacedDigit())
                            .foregroundStyle(Color.toefly.inkMuted)
                    }
                }
                if audio.heardOnce {
                    Picker("Speed", selection: $audio.rate) {
                        Text("0.75×").accessibilityLabel("Slower").tag(Float(0.75))
                        Text("1×").accessibilityLabel("Normal speed").tag(Float(1))
                        Text("1.25×").accessibilityLabel("Faster").tag(Float(1.25))
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("audio-speed")
                }
            }
        }
        .padding(Space.s4)
        .background(Color.toefly.surfaceTask, in: .rect(cornerRadius: Radius.base))
        .overlay { RoundedRectangle(cornerRadius: Radius.base).strokeBorder(Color.toefly.border) }
        .onAppear { start() }
        .onChange(of: url) { start() }
        .onChange(of: paused) { _, isPaused in
            if isPaused {
                audio.pause()
            }
        }
        .onDisappear { audio.stop() }
    }

    private func start() {
        audio.onFinished = onFinished
        audio.onPlayingChange = onPlayingChange
        audio.load(url, heard: !autoplay)
        if autoplay, !paused {
            audio.play()
        }
    }

    @ViewBuilder private var playButton: some View {
        let playing = audio.phase == .playing
        Button {
            if playing {
                audio.pause()
            } else {
                if audio.heardOnce, audio.phase == .finished || audio.phase == .idle || audio.position == 0 {
                    onReplay()
                }
                audio.play()
            }
        } label: {
            Image(systemName: playing ? "pause.fill" : (audio.heardOnce ? "arrow.counterclockwise" : "play.fill"))
                .font(.title3.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Color.toefly.primary, in: .circle)
        }
        .buttonStyle(.plain)
        // The first play is like the exam: no pause, no stop (§3.7).
        .disabled(!audio.heardOnce && playing)
        .accessibilityLabel(playing ? "Pause audio" : (audio.heardOnce ? "Replay" : "Play audio"))
        .accessibilityIdentifier("audio-play")
    }

    private var timeText: String {
        let format = { (seconds: Double) in
            let value = Int(seconds.isFinite ? max(seconds, 0) : 0)
            return "\(value / 60):\(String(format: "%02d", value % 60))"
        }
        if audio.phase == .playing, !audio.heardOnce {
            return "Listening… \(format(audio.position)) / \(format(audio.duration))"
        }
        return "\(format(audio.position)) / \(format(audio.duration))"
    }
}
