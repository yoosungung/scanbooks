import SwiftUI

/// Session grid + PDF export from `cleanedUri` pages (PDFKit).
struct SessionView: View {
    @Binding var title: String
    let pageCount: Int
    let cleanedPageCount: Int
    let onBack: () -> Void
    let onExportPdf: () throws -> String

    @State private var exportMessage: String?
    @State private var exportFailed = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                TextField("세션 제목", text: $title)
                    .textFieldStyle(.roundedBorder)
                    .font(.title3.weight(.semibold))

                Text("\(pageCount)장 · 완료 \(cleanedPageCount)장")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 12) {
                    if pageCount == 0 {
                        Text("촬영한 페이지가 없습니다")
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        ForEach(0..<pageCount, id: \.self) { index in
                            RoundedRectangle(cornerRadius: 8)
                                .fill(Color.secondary.opacity(0.25))
                                .aspectRatio(3 / 4, contentMode: .fit)
                                .overlay {
                                    Text("\(index + 1)")
                                        .font(.headline)
                                }
                                .overlay(alignment: .topTrailing) {
                                    Text(index < cleanedPageCount ? "완료" : "대기")
                                        .font(.caption2.weight(.semibold))
                                        .padding(4)
                                        .background(
                                            (index < cleanedPageCount ? Color.green : Color.orange)
                                                .opacity(0.85),
                                            in: Capsule()
                                        )
                                        .padding(6)
                                }
                        }
                    }
                }

                Spacer()

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
}

#Preview {
    SessionView(
        title: .constant("테스트"),
        pageCount: 2,
        cleanedPageCount: 2,
        onBack: {},
        onExportPdf: { "export.pdf" }
    )
}
