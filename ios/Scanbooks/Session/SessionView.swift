import SwiftUI

struct SessionPageRow: Identifiable, Equatable {
    let id: String
    let status: PipelineStage
}

/// Session grid + reorder/delete + PDF export (docs/UIUX.md §4.4 M4).
struct SessionView: View {
    @Binding var title: String
    let pages: [SessionPageRow]
    let cleanedPageCount: Int
    let onBack: () -> Void
    let onAddScan: () -> Void
    let onMove: (String, Int) -> Void
    let onDelete: (String) -> Void
    let onExportPdf: () throws -> String

    @State private var exportMessage: String?
    @State private var exportFailed = false
    @State private var selectedIds: Set<String> = []

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                TextField("세션 제목", text: $title)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3.weight(.semibold))

                Text("\(pages.count)장 · 완료 \(cleanedPageCount)장")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                if pages.isEmpty {
                    Text("촬영한 페이지가 없습니다")
                        .foregroundStyle(.secondary)
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 12) {
                        ForEach(Array(pages.enumerated()), id: \.element.id) { index, page in
                            pageCell(index: index, page: page)
                        }
                    }
                }

                if !selectedIds.isEmpty {
                    Button(role: .destructive) {
                        for id in selectedIds {
                            onDelete(id)
                        }
                        selectedIds.removeAll()
                    } label: {
                        Label("선택 \(selectedIds.count)장 삭제", systemImage: "trash")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityLabel("선택 삭제")
                }

                Spacer()

                Button(action: onAddScan) {
                    Label("추가 스캔", systemImage: "camera.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("추가 스캔")

                Button {
                    do {
                        let uri = try onExportPdf()
                        exportFailed = false
                        exportMessage = "저장됨: \(uri)"
                    } catch {
                        exportFailed = true
                        exportMessage = "완료된(cleanedUri) 페이지가 없어 PDF를 만들지 못했습니다."
                    }
                } label: {
                    Label("PDF로 내보내기", systemImage: "doc.richtext")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(cleanedPageCount == 0)
                .accessibilityLabel("PDF로 내보내기")
            }
            .padding()
            .navigationTitle("세션")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("카메라", action: onBack)
                }
            }
            .alert(exportFailed ? "PDF 내보내기 실패" : "PDF 내보내기", isPresented: Binding(
                get: { exportMessage != nil },
                set: { if !$0 { exportMessage = nil } }
            )) {
                Button("확인", role: .cancel) { exportMessage = nil }
            } message: {
                Text(exportMessage ?? "")
            }
        }
    }

    @ViewBuilder
    private func pageCell(index: Int, page: SessionPageRow) -> some View {
        let selected = selectedIds.contains(page.id)
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.25))
                .aspectRatio(3 / 4, contentMode: .fit)
                .overlay {
                    Text("\(index + 1)")
                        .font(.headline)
                }
                .overlay(alignment: .topTrailing) {
                    Text(statusBadge(page.status))
                        .font(.caption2.weight(.semibold))
                        .padding(4)
                        .background(statusColor(page.status).opacity(0.85), in: Capsule())
                        .padding(6)
                }
                .overlay {
                    if selected {
                        RoundedRectangle(cornerRadius: 8)
                            .strokeBorder(Color.accentColor, lineWidth: 3)
                    }
                }
                .onTapGesture {
                    if selected {
                        selectedIds.remove(page.id)
                    } else {
                        selectedIds.insert(page.id)
                    }
                }

            HStack(spacing: 8) {
                Button {
                    onMove(page.id, index - 1)
                } label: {
                    Image(systemName: "chevron.up")
                }
                .disabled(index == 0)
                .accessibilityLabel("앞으로 이동")

                Button {
                    onMove(page.id, index + 1)
                } label: {
                    Image(systemName: "chevron.down")
                }
                .disabled(index >= pages.count - 1)
                .accessibilityLabel("뒤로 이동")
            }
            .font(.caption)
        }
    }

    private func statusBadge(_ status: PipelineStage) -> String {
        switch status {
        case .done: return "완료"
        case .failed: return "실패"
        default: return "처리중"
        }
    }

    private func statusColor(_ status: PipelineStage) -> Color {
        switch status {
        case .done: return .green
        case .failed: return .orange
        default: return .blue
        }
    }
}

#Preview {
    SessionView(
        title: .constant("테스트"),
        pages: [
            SessionPageRow(id: "a", status: .done),
            SessionPageRow(id: "b", status: .crop),
        ],
        cleanedPageCount: 1,
        onBack: {},
        onAddScan: {},
        onMove: { _, _ in },
        onDelete: { _ in },
        onExportPdf: { "export.pdf" }
    )
}
