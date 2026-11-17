import SwiftUI

struct ProcessStubView: View {
    let store: SessionStore
    let sessionId: String
    let pageId: String
    let onFinished: () -> Void
    let onCancel: () -> Void

    @State private var current: PipelineStage = .crop
    @State private var metrics: PageMetrics?
    @State private var runner = OrtBackendFactory.makeRunner()

    var body: some View {
        ZStack {
            Color.black.opacity(0.92).ignoresSafeArea()
            VStack(spacing: 24) {
                Text("보정 중")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)
                Text("보통 10초 안팎")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.7))

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(PipelineStage.processingOrder, id: \.self) { stage in
                        HStack {
                            statusIcon(for: stage)
                            Text(stage.koreanLabel)
                                .foregroundStyle(.white)
                            Spacer()
                            if let ms = stageMs(stage) {
                                Text("\(ms)ms")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.white.opacity(0.55))
                            }
                        }
                        .accessibilityLabel("\(stage.koreanLabel) \(statusText(for: stage))")
                    }
                }
                .padding(20)
                .frame(maxWidth: 320)
                .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))

                if let metrics, let contract = PageMetricsPresentation.contractLine(metrics) {
                    Text(contract)
                        .font(.caption.monospaced())
                        .foregroundStyle(.white.opacity(0.65))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 320)
                        .accessibilityLabel(PageMetricsPresentation.accessibilitySummary(metrics))
                }

                if current == .done {
                    Text("펴진 페이지가 준비됐어요")
                        .foregroundStyle(.green)
                        .font(.subheadline.weight(.medium))
                }

                Button("취소", action: onCancel)
                    .foregroundStyle(.white.opacity(0.8))
            }
            .padding()
        }
        .onAppear {
            runner.run(store: store, sessionId: sessionId, pageId: pageId) { stage in
                current = stage
                metrics = (try? store.loadPage(sessionId: sessionId, pageId: pageId))?.metrics
                if stage == .done {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                        onFinished()
                    }
                }
            }
        }
    }

    private func stageMs(_ stage: PipelineStage) -> Int? {
        guard let metrics else { return nil }
        switch stage {
        case .crop: return metrics.cropMs
        case .geo: return metrics.geoMs
        case .finger: return metrics.fingerMs
        case .ill: return metrics.illMs
        default: return nil
        }
    }

    @ViewBuilder
    private func statusIcon(for stage: PipelineStage) -> some View {
        let index = PipelineStage.processingOrder.firstIndex(of: stage) ?? 0
        let currentIndex = PipelineStage.processingOrder.firstIndex(of: current) ?? 0
        if current == .done || index < currentIndex {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(.green)
        } else if stage == current {
            ProgressView()
                .tint(.white)
        } else {
            Image(systemName: "circle")
                .foregroundStyle(.white.opacity(0.35))
        }
    }

    private func statusText(for stage: PipelineStage) -> String {
        let index = PipelineStage.processingOrder.firstIndex(of: stage) ?? 0
        let currentIndex = PipelineStage.processingOrder.firstIndex(of: current) ?? 0
        if current == .done || index < currentIndex { return "완료" }
        if stage == current { return "진행 중" }
        return "대기"
    }
}
